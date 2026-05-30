from __future__ import annotations

import json
import logging
import os
import shutil
import subprocess
import tempfile
from pathlib import Path

from fastapi import HTTPException

try:
    import tomllib
except ModuleNotFoundError:  # Python < 3.11
    try:
        import tomli as tomllib
    except ModuleNotFoundError:
        tomllib = None


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CODEX_HOME = Path.home() / ".codex"
logger = logging.getLogger("cs_learning.codex")


def codex_model_name() -> str:
    return os.environ.get("CS_LEARNING_CODEX_MODEL", "gpt-5.4-mini")


def codex_source_home() -> Path:
    return Path(os.environ.get("CS_LEARNING_CODEX_SOURCE_HOME", DEFAULT_CODEX_HOME)).resolve()


def load_codex_source_config() -> dict:
    config_path = codex_source_home() / "config.toml"
    if not config_path.is_file():
        return {}
    if tomllib is None:
        logger.warning("Python tomllib is unavailable; set CS_LEARNING_CODEX_BASE_URL explicitly for dynamic Codex config.")
        return {}
    try:
        with config_path.open("rb") as config_file:
            return tomllib.load(config_file)
    except Exception as exc:
        logger.warning("Could not read Codex source config %s: %s", config_path, exc)
        return {}


def codex_model_provider_name() -> str:
    return os.environ.get("CS_LEARNING_CODEX_MODEL_PROVIDER") or load_codex_source_config().get("model_provider", "OpenAI")


def codex_base_url() -> str:
    configured = os.environ.get("CS_LEARNING_CODEX_BASE_URL")
    if configured:
        return configured.strip()
    config = load_codex_source_config()
    provider_name = codex_model_provider_name()
    provider = config.get("model_providers", {}).get(provider_name, {})
    return str(provider.get("base_url") or "https://api.openai.com/v1").strip()


def codex_auth_mode() -> str:
    configured = os.environ.get("CS_LEARNING_CODEX_AUTH_MODE")
    if configured:
        return configured.strip()
    config = load_codex_source_config()
    provider_name = codex_model_provider_name()
    provider = config.get("model_providers", {}).get(provider_name, {})
    return "openai" if provider.get("requires_openai_auth", True) else "none"


def codex_auth_file() -> Path:
    return Path(os.environ.get("CS_LEARNING_CODEX_AUTH_FILE", codex_source_home() / "auth.json")).resolve()


def codex_job_home() -> Path:
    return Path(os.environ.get("CS_LEARNING_CODEX_HOME", ROOT / "generated" / "codex-home")).resolve()


def shell_quote_toml(value: str) -> str:
    return json.dumps(value)


def ensure_codex_job_home() -> Path:
    home = codex_job_home()
    home.mkdir(parents=True, exist_ok=True)
    auth_file = codex_auth_file()
    if auth_file.is_file():
        shutil.copy2(auth_file, home / "auth.json")
    config_text = "\n".join(
        [
            f"model_provider = {shell_quote_toml(codex_model_provider_name())}",
            f"model = {shell_quote_toml(codex_model_name())}",
            'model_reasoning_effort = "none"',
            "disable_response_storage = true",
            "",
            f'[model_providers.{shell_quote_toml(codex_model_provider_name())}]',
            f"name = {shell_quote_toml(codex_model_provider_name())}",
            f"base_url = {shell_quote_toml(codex_base_url())}",
            'wire_api = "responses"',
            f"requires_openai_auth = {str(codex_auth_mode() == 'openai').lower()}",
            "",
            "[features]",
            "js_repl = false",
        ]
    )
    (home / "config.toml").write_text(config_text, encoding="utf-8")
    return home


def codex_cli_path() -> str:
    configured = os.environ.get("CS_LEARNING_CODEX_CLI")
    if configured:
        return configured
    found = shutil.which("codex.cmd") or shutil.which("codex")
    return found or ""


def codex_is_configured() -> bool:
    return bool(codex_cli_path())


def codex_preflight(run_model: bool = False) -> dict:
    executable = codex_cli_path()
    home = ensure_codex_job_home() if executable else codex_job_home()
    checks = {
        "cli": bool(executable),
        "auth_file": (home / "auth.json").is_file(),
        "config_file": (home / "config.toml").is_file(),
    }
    payload = {
        "ok": all(checks.values()),
        "checks": checks,
        "codex_cli": executable,
        "model": codex_model_name(),
        "model_provider": codex_model_provider_name(),
        "base_url": codex_base_url(),
        "codex_home": str(home),
        "ran_model": False,
        "message": "Codex CLI metadata checks passed." if all(checks.values()) else "Codex CLI is not ready.",
    }
    if not run_model or not payload["ok"]:
        return payload

    try:
        result = run_codex_json(
            'Return JSON with ok true and message "preflight".',
            {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "ok": {"type": "boolean"},
                    "message": {"type": "string"},
                },
                "required": ["ok", "message"],
            },
        )
        payload["ran_model"] = True
        payload["model_result"] = result
        payload["ok"] = bool(result.get("ok"))
        payload["message"] = "Codex CLI model preflight passed." if payload["ok"] else "Codex CLI model preflight returned an unexpected result."
    except HTTPException as exc:
        payload["ran_model"] = True
        payload["ok"] = False
        payload["message"] = str(exc.detail)
    return payload


def run_codex_json(prompt: str, schema: dict) -> dict:
    executable = codex_cli_path()
    if not executable:
        raise HTTPException(
            status_code=503,
            detail="Codex CLI is not configured. Set CS_LEARNING_CODEX_CLI or install @openai/codex.",
        )

    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".json", delete=False) as schema_file:
        json.dump(schema, schema_file, ensure_ascii=False)
        schema_path = schema_file.name
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".json", delete=False) as output_file:
        output_path = output_file.name

    try:
        model = codex_model_name()
        codex_home = ensure_codex_job_home()
        command = [
            executable,
            "--ask-for-approval",
            "never",
            "-m",
            model,
            "exec",
            "--ephemeral",
            "--ignore-rules",
            "-C",
            str(ROOT),
            "--sandbox",
            "read-only",
            "--output-schema",
            schema_path,
            "--output-last-message",
            output_path,
            "-",
        ]
        logger.info("Starting Codex CLI with executable=%s model=%s", executable, model)
        env = os.environ.copy()
        env["CODEX_HOME"] = str(codex_home)
        completed = subprocess.run(
            command,
            cwd=ROOT,
            input=prompt,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=int(os.environ.get("CS_LEARNING_CODEX_TIMEOUT", "180")),
            check=False,
            env=env,
        )
    except subprocess.TimeoutExpired as exc:
        logger.exception("Codex CLI timed out")
        raise HTTPException(status_code=504, detail="Codex CLI revision timed out") from exc
    finally:
        try:
            Path(schema_path).unlink(missing_ok=True)
        except OSError:
            logger.warning("Could not delete temporary Codex schema file: %s", schema_path)

    output = ""
    output_file_path = Path(output_path)
    if output_file_path.is_file():
        output = output_file_path.read_text(encoding="utf-8").strip()
    if not output:
        output = (completed.stdout or "").strip()
    try:
        output_file_path.unlink(missing_ok=True)
    except OSError:
        logger.warning("Could not delete temporary Codex output file: %s", output_path)

    diagnostics = (completed.stderr or "").strip()
    if completed.returncode != 0:
        logger.error(
            "Codex CLI failed returncode=%s stderr=%s stdout=%s",
            completed.returncode,
            diagnostics[-2000:],
            output[-2000:],
        )
        raise HTTPException(
            status_code=502,
            detail=f"Codex CLI revision failed: {diagnostics or output or completed.returncode}",
        )

    try:
        return json.loads(output)
    except json.JSONDecodeError as exc:
        logger.error("Codex CLI returned non-JSON output: %s", output[-2000:])
        raise HTTPException(status_code=502, detail="Codex CLI returned non-JSON output") from exc
