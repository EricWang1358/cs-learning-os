from __future__ import annotations

import os
import shutil
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

beta_root = ROOT / "generated" / "beta-smoke"
if beta_root.exists():
    shutil.rmtree(beta_root, ignore_errors=True)
beta_root.mkdir(parents=True, exist_ok=True)
try:
    content_root = beta_root / "content"
    db_path = beta_root / "knowledge.db"
    shutil.copytree(ROOT / "content-demo", content_root)

    os.environ["CS_LEARNING_PROFILE"] = "friend-beta"
    os.environ["CS_LEARNING_BETA"] = "true"
    os.environ["CS_LEARNING_AI_ENABLED"] = "false"
    os.environ["CS_LEARNING_CONTENT"] = str(content_root)
    os.environ["CS_LEARNING_DB"] = str(db_path)
    os.environ["CS_LEARNING_DATA_ROOT"] = str(beta_root)
    os.environ["CS_LEARNING_EXPORT_ROOT"] = str(beta_root / "exports")
    os.environ["CS_LEARNING_GENERATED_ROOT"] = str(beta_root / "generated")
    os.environ["CS_LEARNING_AI_PROVIDER"] = "openai-api"
    os.environ.pop("OPENAI_API_KEY", None)

    from fastapi.testclient import TestClient

    from api import app
    from db import SCHEMA_VERSION, connect, initialize
    from ingest import ingest
    import maintenance_service

    ingest(content_root, db_path)

    with sqlite3.connect(db_path) as raw_conn:
        raw_conn.row_factory = sqlite3.Row
        schema = maintenance_service.schema_meta(raw_conn)
        assert schema["schema_version"]["value"] == SCHEMA_VERSION
        assert raw_conn.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
        assert raw_conn.execute("SELECT COUNT(*) FROM nodes").fetchone()[0] > 0
        assert raw_conn.execute("SELECT COUNT(*) FROM quizzes").fetchone()[0] > 0

    legacy_db_path = beta_root / "legacy-knowledge.db"
    with sqlite3.connect(legacy_db_path) as legacy_conn:
        legacy_conn.execute(
            """
            CREATE TABLE nodes (
                slug TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                area TEXT NOT NULL,
                status TEXT NOT NULL,
                visibility TEXT NOT NULL,
                summary TEXT NOT NULL,
                body TEXT NOT NULL,
                path TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """
        )
        legacy_conn.execute(
            """
            INSERT INTO nodes (slug, title, area, status, visibility, summary, body, path, updated_at)
            VALUES ('legacy-node', 'Legacy Node', 'projects', 'active', 'core', 'legacy summary', 'legacy body', 'nodes/legacy-node.md', '2026-01-01T00:00:00+00:00')
            """
        )
        legacy_conn.execute(
            """
            CREATE TABLE ai_jobs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                target_type TEXT NOT NULL,
                target_id TEXT NOT NULL,
                question_ids TEXT NOT NULL DEFAULT '[]',
                provider TEXT NOT NULL DEFAULT '',
                model TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'failed',
                stage TEXT NOT NULL DEFAULT 'failed',
                instruction TEXT NOT NULL DEFAULT '',
                draft_body TEXT NOT NULL DEFAULT '',
                result_json TEXT NOT NULL DEFAULT '',
                error TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                completed_at TEXT NOT NULL DEFAULT ''
            )
            """
        )
        legacy_conn.execute(
            """
            INSERT INTO ai_jobs (target_type, target_id, created_at, updated_at)
            VALUES ('node', 'legacy-node', '2026-01-01T00:00:00+00:00', '2026-01-01T00:00:00+00:00')
            """
        )
        legacy_conn.commit()

    with connect(legacy_db_path) as legacy_conn:
        initialize(legacy_conn)
        legacy_tables = {
            row["name"]
            for row in legacy_conn.execute("SELECT name FROM sqlite_master WHERE type = 'table'").fetchall()
        }
        assert {"schema_meta", "content_files", "review_queue", "quiz_attempts"}.issubset(legacy_tables)
        assert legacy_conn.execute("SELECT track FROM nodes WHERE slug = 'legacy-node'").fetchone()[0] == "general"
        assert legacy_conn.execute("SELECT COUNT(*) FROM ai_jobs").fetchone()[0] == 1
        assert legacy_conn.execute("SELECT started_at FROM ai_jobs WHERE id = 1").fetchone()[0] == ""
        assert maintenance_service.schema_meta(legacy_conn)["schema_version"]["value"] == SCHEMA_VERSION

    with TestClient(app, raise_server_exceptions=False) as client:
        health = client.get("/api/health")
        assert health.status_code == 200, health.text
        health_payload = health.json()
        assert health_payload["profile"] == "friend-beta"
        assert health_payload["beta"] is True
        assert health_payload["ai"]["enabled"] is False
        assert health_payload["ai"]["configured"] is False

        repair = client.get("/api/system/repair")
        assert repair.status_code == 200, repair.text
        assert repair.json()["ok"] is True, repair.json()

        package = client.get("/api/package/export")
        assert package.status_code == 200, package.text
        assert package.json()["manifest"]["counts"]["nodes"] > 0

        package_write = client.get("/api/package/export", params={"write": "true"})
        assert package_write.status_code == 200, package_write.text
        written_manifest = Path(package_write.json()["manifest"]["written_to"])
        assert written_manifest.is_file()
        assert beta_root in written_manifest.parents

        preflight = client.get("/api/ai/preflight")
        assert preflight.status_code == 200, preflight.text
        preflight_payload = preflight.json()
        assert preflight_payload["enabled"] is False
        assert preflight_payload["checks"]["ai_enabled"] is False

        blocked = client.post(
            "/api/ai/jobs",
            json={
                "target_type": "node",
                "target_id": "project-crud-app",
                "question_ids": [],
                "question": "Should be blocked in beta.",
                "instruction": "Do not run.",
            },
        )
        assert blocked.status_code == 403, f"{blocked.status_code}: {blocked.text}"

    print("Beta migration smoke passed")
finally:
    shutil.rmtree(beta_root, ignore_errors=True)
