from __future__ import annotations

import threading
from pathlib import Path

from fastapi import FastAPI
from fastapi.testclient import TestClient

from backend import codex_service
from backend.system_router import create_system_router


ROOT = Path(__file__).resolve().parents[1]


def build_client(codex_preflight, codex_configuration_probe=None):
    app = FastAPI()
    app.include_router(
        create_system_router(
            get_conn=lambda: None,
            metrics_service=None,
            recover_stale_ai_jobs=lambda: None,
            ai_provider_name=lambda: "codex-cli",
            openai_is_configured=lambda: False,
            openai_model_name=lambda: "unused",
            codex_is_configured=lambda: True,
            codex_model_name=lambda: "test-model",
            codex_model_provider_name=lambda: "OpenAI",
            codex_base_url=lambda: "https://api.openai.com/v1",
            codex_cli_path=lambda: "codex",
            codex_job_home=lambda: Path("generated/codex-home"),
            codex_configuration_probe=codex_configuration_probe
            or (
                lambda: {
                    "ok": True,
                    "checks": {"cli": True, "auth_file": True, "config_file": True},
                    "ran_model": False,
                }
            ),
            codex_preflight=codex_preflight,
            ai_enabled=lambda: True,
            app_profile=lambda: "local",
            beta_mode=lambda: True,
        )
    )
    return TestClient(app, raise_server_exceptions=False)


def test_get_preflight_ignores_legacy_run_model_without_calling_codex() -> None:
    calls: list[bool] = []

    def codex_preflight(*, run_model: bool) -> dict:
        calls.append(run_model)
        return {"ok": True, "ran_model": run_model}

    with build_client(codex_preflight) as client:
        response = client.get("/api/ai/preflight?run_model=true")

    assert response.status_code == 200
    assert response.json()["ran_model"] is False
    assert calls == []


def test_get_preflight_uses_the_pure_configuration_probe_for_a_fresh_codex_install() -> None:
    calls: list[bool] = []
    probe_calls = []

    def codex_preflight(*, run_model: bool) -> dict:
        calls.append(run_model)
        return {"ok": False, "ran_model": run_model}

    def codex_configuration_probe() -> dict:
        probe_calls.append(True)
        return {
            "ok": True,
            "checks": {"cli": True, "auth_file": True, "config_file": True},
            "ran_model": False,
            "message": "Codex CLI metadata checks passed.",
        }

    with build_client(codex_preflight, codex_configuration_probe) as client:
        response = client.get("/api/ai/preflight")

    assert response.status_code == 200
    assert response.json()["ok"] is True
    assert response.json()["checks"]["config_file"] is True
    assert probe_calls == [True]
    assert calls == []


def test_pure_configuration_probe_reports_a_fresh_valid_install_without_creating_files(tmp_path, monkeypatch) -> None:
    auth_file = tmp_path / "source" / "auth.json"
    auth_file.parent.mkdir()
    auth_file.write_text("{}", encoding="utf-8")
    job_home = tmp_path / "generated" / "codex-home"
    monkeypatch.setattr(codex_service, "codex_cli_path", lambda: "codex")
    monkeypatch.setattr(codex_service, "codex_auth_file", lambda: auth_file)
    monkeypatch.setattr(codex_service, "codex_job_home", lambda: job_home)
    monkeypatch.setattr(codex_service, "codex_model_name", lambda: "test-model")
    monkeypatch.setattr(codex_service, "codex_model_provider_name", lambda: "OpenAI")
    monkeypatch.setattr(codex_service, "codex_base_url", lambda: "https://api.openai.com/v1")

    payload = codex_service.codex_configuration_probe()

    assert payload["ok"] is True
    assert payload["checks"] == {"cli": True, "auth_file": True, "config_file": True}
    assert payload["codex_home"] == str(job_home)
    assert not job_home.exists()


def test_model_preflight_requires_exact_local_action_header() -> None:
    calls: list[bool] = []

    def codex_preflight(*, run_model: bool) -> dict:
        calls.append(run_model)
        return {"ok": True, "ran_model": run_model}

    with build_client(codex_preflight) as client:
        missing = client.post("/api/ai/model-preflight")
        incorrect = client.post("/api/ai/model-preflight", headers={"X-CS-Local-Action": "true"})
        duplicated = client.post(
            "/api/ai/model-preflight",
            headers=[("X-CS-Local-Action", "1"), ("X-CS-Local-Action", "invalid")],
        )

    assert missing.status_code == 403
    assert incorrect.status_code == 403
    assert duplicated.status_code == 403
    assert calls == []


def test_model_preflight_uses_codex_once_then_applies_cooldown() -> None:
    calls: list[bool] = []

    def codex_preflight(*, run_model: bool) -> dict:
        calls.append(run_model)
        return {"ok": True, "ran_model": run_model}

    with build_client(codex_preflight) as client:
        first = client.post("/api/ai/model-preflight", headers={"X-CS-Local-Action": "1"})
        repeated = client.post("/api/ai/model-preflight", headers={"X-CS-Local-Action": "1"})

    assert first.status_code == 200
    assert repeated.status_code == 429
    assert calls == [True]


def test_model_preflight_rejects_a_concurrent_request_before_calling_codex() -> None:
    started = threading.Event()
    release = threading.Event()
    calls: list[bool] = []

    def codex_preflight(*, run_model: bool) -> dict:
        calls.append(run_model)
        started.set()
        assert release.wait(timeout=2)
        return {"ok": True, "ran_model": run_model}

    with build_client(codex_preflight) as client:
        first_response = []

        def make_first_request() -> None:
            first_response.append(client.post("/api/ai/model-preflight", headers={"X-CS-Local-Action": "1"}))

        request_thread = threading.Thread(target=make_first_request)
        request_thread.start()
        assert started.wait(timeout=2)
        concurrent = client.post("/api/ai/model-preflight", headers={"X-CS-Local-Action": "1"})
        release.set()
        request_thread.join(timeout=2)

    assert not request_thread.is_alive()
    assert first_response[0].status_code == 200
    assert concurrent.status_code == 429
    assert calls == [True]


def test_development_launcher_keeps_the_api_bound_to_loopback() -> None:
    launcher = (ROOT / "scripts" / "dev.ps1").read_text(encoding="utf-8")

    assert '$ApiHost = "127.0.0.1"' in launcher
    assert '"--host", "$ApiHost"' in launcher
    assert "0.0.0.0" not in launcher
