"""Phase 5a tests for the offline sync package export."""

from __future__ import annotations

import json
import zipfile
from pathlib import Path

from fastapi import FastAPI
from fastapi.testclient import TestClient

from backend.db import connect, initialize
from backend.node_lifecycle_service import create_node
from backend.sync_package import FORMAT_MARKER, FORMAT_VERSION
from backend.sync_router import create_sync_router


def build_client(db_path: Path, export_root: Path) -> TestClient:
    def get_conn():
        conn = connect(db_path)
        initialize(conn)
        return conn

    app = FastAPI()
    app.include_router(create_sync_router(get_conn, is_loopback=lambda request: True, export_root=export_root))
    return TestClient(app)


def credential(client: TestClient) -> str:
    token = client.post("/api/sync/v1/pairing-tokens").json()["token"]
    return client.post("/api/sync/v1/pair", json={"token": token, "device_name": "phone"}).json()["credential"]


def seed(db_path: Path, content_root: Path) -> str:
    with connect(db_path) as conn:
        initialize(conn)
        slug = create_node(conn, content_root, "Pkg Node", "algorithms", "general", "s", [], "core", "seed", 1000)
        conn.execute(
            """
            INSERT INTO quizzes (id, title, area, status, visibility, difficulty, summary, body, path, updated_at)
            VALUES ('q1', 'Pkg Quiz', 'algorithms', 'seed', 'practice', 'easy', '', 'quiz body', 'quizzes/algorithms/q1.md', 'now')
            """
        )
        conn.execute(
            """
            INSERT INTO capture_slips (id, body, type, topic_hint, source_label, status, created_at, updated_at, revision)
            VALUES ('s1', 'TLB 是页表缓存', 'concept_seed', 'TLB', 'phone', 'inbox', 'now', 'now', 1)
            """
        )
        conn.commit()
    return slug


def test_package_export_writes_readable_zip(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    export_root = tmp_path / "exports"
    client = build_client(db_path, export_root)
    headers = {"Authorization": f"Bearer {credential(client)}"}
    slug = seed(db_path, tmp_path / "content")

    response = client.post("/api/sync/v1/export/package", headers=headers)
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["fileName"].startswith("cs-learning-os-package-")
    assert body["counts"] == {"nodes": 1, "quizzes": 1, "capture_slips": 1}

    package_path = Path(body["path"])
    assert package_path.is_file()
    with zipfile.ZipFile(package_path) as archive:
        names = set(archive.namelist())
        assert "manifest.json" in names
        assert "records/nodes.json" in names
        assert "records/quizzes.json" in names
        assert "records/capture_slips.json" in names
        assert f"markdown/nodes/algorithms/{slug}.md" in names

        manifest = json.loads(archive.read("manifest.json"))
        assert manifest["format"] == FORMAT_MARKER
        assert manifest["formatVersion"] == FORMAT_VERSION
        assert manifest["serverId"]

        nodes = json.loads(archive.read("records/nodes.json"))
        assert len(nodes) == 1
        assert nodes[0]["type"] == "node"
        assert nodes[0]["id"] == slug
        assert nodes[0]["revision"] == 1
        assert nodes[0]["hash"]
        quizzes = json.loads(archive.read("records/quizzes.json"))
        assert quizzes[0]["body"] == "quiz body"
        slips = json.loads(archive.read("records/capture_slips.json"))
        assert slips[0]["slipType"] == "concept_seed"


def test_package_export_requires_credential(tmp_path: Path) -> None:
    client = build_client(tmp_path / "knowledge.db", tmp_path / "exports")
    response = client.post("/api/sync/v1/export/package")
    assert response.status_code == 401
