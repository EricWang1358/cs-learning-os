from __future__ import annotations

import json
import sqlite3
import os
import signal
import sys
from pathlib import Path
from threading import Lock
from time import monotonic
from typing import Callable, List, Optional

from fastapi import APIRouter, BackgroundTasks, Header, HTTPException, Query

try:
    from . import maintenance_service
    from .ai_job_service import utc_now
except ImportError:
    import maintenance_service
    from ai_job_service import utc_now


ConnectionFactory = Callable[[], sqlite3.Connection]
MODEL_PREFLIGHT_COOLDOWN_SECONDS = 5.0


# ---------------------------------------------------------------------------
# AI provider config — stored in a JSON file next to the database
# ---------------------------------------------------------------------------

def _ai_config_file() -> Path:
    db_path = os.environ.get("CS_LEARNING_DB", "")
    if db_path:
        return Path(db_path).resolve().parent / ".ai-providers.json"
    return Path.cwd() / "data" / ".ai-providers.json"


def _load_ai_providers() -> list[dict]:
    path = _ai_config_file()
    if path.exists():
        try:
            return json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            return []
    return []


def _save_ai_providers(providers: list[dict]) -> None:
    path = _ai_config_file()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(providers, indent=2, ensure_ascii=False), encoding="utf-8")


# ---------------------------------------------------------------------------
# Router factory
# ---------------------------------------------------------------------------

def create_system_router(
    get_conn: ConnectionFactory,
    metrics_service,
    recover_stale_ai_jobs: Callable[[], None],
    ai_provider_name: Callable[[], str],
    openai_is_configured: Callable[[], bool],
    openai_model_name: Callable[[], str],
    codex_is_configured: Callable[[], bool],
    codex_model_name: Callable[[], str],
    codex_model_provider_name: Callable[[], str],
    codex_base_url: Callable[[], str],
    codex_cli_path: Callable[[], str],
    codex_job_home: Callable[[], object],
    codex_configuration_probe: Callable[[], dict],
    codex_preflight: Callable[..., dict],
    ai_enabled: Callable[[], bool],
    app_profile: Callable[[], str],
    beta_mode: Callable[[], bool],
) -> APIRouter:
    router = APIRouter(prefix="/api", tags=["system"])
    model_preflight_lock = Lock()
    model_preflight_in_flight = False
    model_preflight_available_at = 0.0

    # -- health -----------------------------------------------------------------

    @router.get("/health")
    def health() -> dict:
        recover_stale_ai_jobs()
        enabled = ai_enabled()
        providers = _load_ai_providers()
        active = os.environ.get("CS_LEARNING_AI_PROVIDER", "codex-cli")
        return {
            "ok": True,
            "profile": app_profile(),
            "beta": beta_mode(),
            "ai": {
                "provider": ai_provider_name(),
                "enabled": enabled,
                "configured": enabled and (codex_is_configured() if ai_provider_name() == "codex-cli" else openai_is_configured()),
                "model": codex_model_name() if ai_provider_name() == "codex-cli" else openai_model_name(),
                "codex_cli": codex_cli_path() if enabled else "",
                "codex_model_provider": codex_model_provider_name() if enabled else "",
                "codex_base_url": codex_base_url() if enabled and ai_provider_name() == "codex-cli" else "",
                "codex_home": str(codex_job_home()) if enabled and ai_provider_name() == "codex-cli" else "",
                "savedProviders": len(providers),
                "activeConfig": active,
            },
        }

    # -- preflight --------------------------------------------------------------

    @router.get("/ai/preflight")
    def ai_preflight() -> dict:
        provider = ai_provider_name()
        if not ai_enabled():
            return {
                "provider": provider,
                "ok": False,
                "enabled": False,
                "checks": {"ai_enabled": False},
                "model": codex_model_name() if provider == "codex-cli" else openai_model_name(),
                "ran_model": False,
                "message": "AI features are disabled for this beta profile.",
            }
        if provider == "codex-cli":
            return {"provider": provider, "enabled": True, **codex_configuration_probe()}
        return {
            "provider": provider,
            "enabled": True,
            "ok": openai_is_configured(),
            "checks": {"openai_api_key": openai_is_configured()},
            "model": openai_model_name(),
            "ran_model": False,
            "message": (
                "OpenAI API key is configured."
                if openai_is_configured()
                else "OPENAI_API_KEY is not configured."
            ),
        }

    @router.post("/ai/model-preflight")
    def ai_model_preflight(
        x_cs_local_action: Optional[List[str]] = Header(default=None, alias="X-CS-Local-Action"),
    ) -> dict:
        nonlocal model_preflight_in_flight, model_preflight_available_at
        if x_cs_local_action != ["1"]:
            raise HTTPException(status_code=403, detail="Model preflight requires X-CS-Local-Action: 1.")
        with model_preflight_lock:
            now = monotonic()
            if model_preflight_in_flight or now < model_preflight_available_at:
                raise HTTPException(status_code=429, detail="Model preflight is already running or cooling down.")
            model_preflight_in_flight = True
        try:
            provider = ai_provider_name()
            if not ai_enabled():
                return {"provider": provider, "ok": False, "enabled": False, "checks": {"ai_enabled": False}, "ran_model": False}
            if provider != "codex-cli":
                raise HTTPException(status_code=409, detail="Model preflight is available only for the Codex CLI provider.")
            return {"provider": provider, "enabled": True, **codex_preflight(run_model=True)}
        finally:
            with model_preflight_lock:
                model_preflight_in_flight = False
                model_preflight_available_at = monotonic() + MODEL_PREFLIGHT_COOLDOWN_SECONDS

    # -- metrics ----------------------------------------------------------------

    @router.get("/system/metrics")
    def system_metrics(background_tasks: BackgroundTasks, refresh: bool = Query(False)) -> dict:
        recover_stale_ai_jobs()
        with get_conn() as conn:
            counts = {
                "nodes": conn.execute("SELECT COUNT(*) FROM nodes").fetchone()[0],
                "quizzes": conn.execute("SELECT COUNT(*) FROM quizzes").fetchone()[0],
                "open_questions": conn.execute("SELECT COUNT(*) FROM reader_questions WHERE status = 'open'").fetchone()[0],
                "active_ai_jobs": conn.execute("SELECT COUNT(*) FROM ai_jobs WHERE status IN ('queued', 'solving', 'draft_ready', 'failed')").fetchone()[0],
                "failed_ai_jobs": conn.execute("SELECT COUNT(*) FROM ai_jobs WHERE status = 'failed'").fetchone()[0],
                "due_reviews": conn.execute("SELECT COUNT(*) FROM review_queue WHERE due_at <= ?", (utc_now(),)).fetchone()[0],
            }
            meta = maintenance_service.schema_meta(conn)
        heavy_metrics = None if refresh else metrics_service.cached_payload()
        if heavy_metrics is None:
            heavy_metrics = metrics_service.fallback_payload()
            if refresh:
                background_tasks.add_task(metrics_service.refresh_cache)
            heavy_metrics["cached"] = False
            heavy_metrics["refreshing"] = metrics_service.refresh_in_progress
        elif refresh:
            background_tasks.add_task(metrics_service.refresh_cache)
            heavy_metrics["refreshing"] = True
        if not ai_enabled():
            ai_payload = {"ok": False, "enabled": False, "provider": ai_provider_name(), "message": "AI features are disabled."}
        else:
            ai_payload = codex_preflight(run_model=False) if ai_provider_name() == "codex-cli" else {
                "ok": openai_is_configured(), "enabled": True,
                "message": "OpenAI API key is configured." if openai_is_configured() else "OPENAI_API_KEY is not configured.",
            }
        return {
            "counts": counts, **heavy_metrics,
            "cache": {"cached": bool(heavy_metrics.get("cached")), "refreshing": bool(heavy_metrics.get("refreshing")),
                       "ttl_seconds": metrics_service.cache_ttl_seconds,
                       "refresh_after": "Manual refresh or cache expiry; heavy scans run in the background."},
            "ai": ai_payload, "schema": meta,
        }

    # -- AI provider config ------------------------------------------------------

    @router.get("/system/ai-config")
    def ai_config() -> dict:
        """Return saved AI providers and the active provider."""
        providers = _load_ai_providers()
        built_in_codex = {
            "id": "codex-cli", "label": "Codex CLI (default)", "provider": "codex-cli",
            "apiKey": "", "baseUrl": "", "model": codex_model_name(), "isBuiltIn": True,
        }
        has_codex = any(p.get("id") == "codex-cli" for p in providers)
        all_providers = providers if has_codex else [built_in_codex] + providers
        active = os.environ.get("CS_LEARNING_AI_PROVIDER", "codex-cli")
        active_info = next((p for p in all_providers if p.get("id") == active), built_in_codex)
        return {
            "active": active, "activeModel": active_info.get("model", ""),
            "providers": all_providers, "aiEnabled": ai_enabled(),
        }

    @router.post("/system/ai-config")
    def save_ai_config(payload: dict) -> dict:
        """Save or update an AI provider configuration."""
        pid = payload.get("id", "").strip()
        if not pid:
            raise HTTPException(status_code=400, detail="Provider id is required.")
        if pid == "codex-cli":
            raise HTTPException(status_code=400, detail="Cannot overwrite the built-in Codex CLI provider.")
        entry = {
            "id": pid, "label": payload.get("label", pid), "provider": "openai-compatible",
            "apiKey": payload.get("apiKey", ""), "baseUrl": payload.get("baseUrl", ""),
            "model": payload.get("model", ""), "isBuiltIn": False,
        }
        providers = _load_ai_providers()
        providers = [p for p in providers if p.get("id") != pid]
        providers.append(entry)
        _save_ai_providers(providers)
        return {"ok": True, "provider": entry, "providers": providers}

    @router.post("/system/ai-config/activate")
    def activate_ai_provider(payload: dict) -> dict:
        """Switch the active AI provider (takes effect on restart or same-session env set)."""
        pid = payload.get("id", "").strip()
        if not pid:
            raise HTTPException(status_code=400, detail="Provider id is required.")
        if pid != "codex-cli":
            providers = _load_ai_providers()
            match = next((p for p in providers if p.get("id") == pid), None)
            if not match:
                raise HTTPException(status_code=404, detail="Provider not found.")
            if not match.get("apiKey"):
                raise HTTPException(status_code=400, detail="Provider has no API key configured.")
        # Set env vars for current process (restart will pick up from config file)
        os.environ["CS_LEARNING_AI_PROVIDER"] = pid
        if pid != "codex-cli":
            prov = next((p for p in _load_ai_providers() if p.get("id") == pid), {})
            if prov.get("apiKey"): os.environ["OPENAI_API_KEY"] = prov["apiKey"]
            if prov.get("baseUrl"): os.environ["CS_LEARNING_AI_BASE_URL"] = prov["baseUrl"]
            if prov.get("model"): os.environ["CS_LEARNING_AI_MODEL"] = prov["model"]
        return {"ok": True, "active": pid, "message": f"Activated '{pid}'. Restart for full effect."}

    @router.delete("/system/ai-config/{provider_id}")
    def delete_ai_provider(provider_id: str) -> dict:
        """Remove a saved AI provider configuration."""
        if provider_id == "codex-cli":
            raise HTTPException(status_code=400, detail="Cannot remove the built-in Codex CLI provider.")
        providers = _load_ai_providers()
        providers = [p for p in providers if p.get("id") != provider_id]
        _save_ai_providers(providers)
        return {"ok": True, "providers": providers}

    # -- AI provider test & models ------------------------------------------------

    @router.post("/system/ai-config/test")
    def test_ai_provider(payload: dict) -> dict:
        """Test connection to an AI provider by calling its /models endpoint."""
        import ssl, urllib.request
        base = payload.get("baseUrl", "").strip().rstrip("/")
        key = payload.get("apiKey", "").strip()
        if not base or not key:
            raise HTTPException(status_code=400, detail="Base URL and API Key are required.")
        url = f"{base}/models"
        try:
            req = urllib.request.Request(url, headers={
                "Authorization": f"Bearer {key}",
                "Content-Type": "application/json",
            })
            ctx = ssl.create_default_context()
            with urllib.request.urlopen(req, timeout=10, context=ctx) as resp:
                data = json.loads(resp.read().decode())
                ids = [m.get("id", "") for m in data.get("data", []) if m.get("id")]
                return {
                    "ok": True,
                    "status": resp.status,
                    "modelCount": len(ids),
                    "models": ids[:20],
                    "message": f"Connected. Found {len(ids)} models.",
                }
        except urllib.error.HTTPError as e:
            body = e.read().decode()[:500] if e.fp else ""
            return {"ok": False, "status": e.code, "message": f"HTTP {e.code}: {body}"}
        except Exception as e:
            return {"ok": False, "status": 0, "message": str(e)}

    @router.post("/system/ai-config/models")
    def pull_ai_models(payload: dict) -> dict:
        """Pull the model list from an AI provider."""
        import ssl, urllib.request
        base = payload.get("baseUrl", "").strip().rstrip("/")
        key = payload.get("apiKey", "").strip()
        if not base or not key:
            raise HTTPException(status_code=400, detail="Base URL and API Key are required.")
        url = f"{base}/models"
        try:
            req = urllib.request.Request(url, headers={
                "Authorization": f"Bearer {key}",
                "Content-Type": "application/json",
            })
            ctx = ssl.create_default_context()
            with urllib.request.urlopen(req, timeout=15, context=ctx) as resp:
                data = json.loads(resp.read().decode())
                ids = sorted([m.get("id", "") for m in data.get("data", []) if m.get("id")])
                return {"ok": True, "models": ids, "count": len(ids)}
        except Exception as e:
            return {"ok": False, "models": [], "count": 0, "message": str(e)}

    # -- restart ----------------------------------------------------------------

    @router.post("/system/restart")
    def system_restart(background_tasks: BackgroundTasks) -> dict:
        """Re-ingest all content, then restart the backend server."""
        def _shutdown():
            try:
                root = Path(os.environ.get("CS_LEARNING_CONTENT", str(Path.cwd() / "data" / "content"))).resolve()
                db = Path(os.environ.get("CS_LEARNING_DB", str(Path.cwd() / "data" / "knowledge.db"))).resolve()
                sys.path.insert(0, str(Path(__file__).resolve().parent))
                from ingest import ingest as ingest_content
                ingest_content(root, db)
            except Exception as exc:
                print(f"[restart] ingest failed (continuing): {exc}")
            import time
            time.sleep(1)
            os.kill(os.getpid(), signal.SIGTERM)
        background_tasks.add_task(_shutdown)
        return {"ok": True, "message": "Re-ingesting content then restarting server."}

    return router
