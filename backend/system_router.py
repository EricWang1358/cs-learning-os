from __future__ import annotations

import sqlite3
import os
import signal
import sys
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

    @router.get("/health")
    def health() -> dict:
        recover_stale_ai_jobs()
        enabled = ai_enabled()
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
            },
        }

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
                "message": "AI features are disabled for this beta profile. Configure CS_LEARNING_AI_ENABLED=true only on a trusted local machine.",
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
                else "OPENAI_API_KEY is not configured for the local API process."
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
                return {
                    "provider": provider,
                    "ok": False,
                    "enabled": False,
                    "checks": {"ai_enabled": False},
                    "model": codex_model_name() if provider == "codex-cli" else openai_model_name(),
                    "ran_model": False,
                    "message": "AI features are disabled for this beta profile. Configure CS_LEARNING_AI_ENABLED=true only on a trusted local machine.",
                }
            if provider != "codex-cli":
                raise HTTPException(status_code=409, detail="Model preflight is available only for the Codex CLI provider.")
            return {"provider": provider, "enabled": True, **codex_preflight(run_model=True)}
        finally:
            with model_preflight_lock:
                model_preflight_in_flight = False
                model_preflight_available_at = monotonic() + MODEL_PREFLIGHT_COOLDOWN_SECONDS

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
            ai_payload = {
                "ok": False,
                "enabled": False,
                "provider": ai_provider_name(),
                "message": "AI features are disabled for this beta profile.",
            }
        else:
            ai_payload = codex_preflight(run_model=False) if ai_provider_name() == "codex-cli" else {
                "ok": openai_is_configured(),
                "enabled": True,
                "message": "OpenAI API key is configured." if openai_is_configured() else "OPENAI_API_KEY is not configured.",
            }
        return {
            "counts": counts,
            **heavy_metrics,
            "cache": {
                "cached": bool(heavy_metrics.get("cached")),
                "refreshing": bool(heavy_metrics.get("refreshing")),
                "ttl_seconds": metrics_service.cache_ttl_seconds,
                "refresh_after": "Manual refresh or cache expiry; heavy scans run in the background.",
            },
            "ai": ai_payload,
            "schema": meta,
        }

    @router.post("/system/restart")
    def system_restart(background_tasks: BackgroundTasks) -> dict:
        """Re-ingest all content, then restart the backend server."""
        def _shutdown():
            try:
                from pathlib import Path
                from . import ingest as ingest_mod
                root = Path(os.environ.get("CS_LEARNING_CONTENT", str(Path.cwd() / "data" / "content"))).resolve()
                db = Path(os.environ.get("CS_LEARNING_DB", str(Path.cwd() / "data" / "knowledge.db"))).resolve()
                ingest_mod.ingest(root, db)
            except Exception as exc:
                print(f"[restart] ingest failed (continuing with shutdown): {exc}")
            import time
            time.sleep(1)
            os.kill(os.getpid(), signal.SIGTERM)
        background_tasks.add_task(_shutdown)
        return {"ok": True, "message": "Re-ingesting content then restarting server."}

    return router
