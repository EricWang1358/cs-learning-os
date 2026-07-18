from __future__ import annotations

import sqlite3
from pathlib import Path

from fastapi import FastAPI
from fastapi.testclient import TestClient

from backend.db import connect, initialize
from backend.node_lifecycle_service import create_node
from backend.node_router import create_node_router


def build_client(tmp_path: Path) -> tuple[TestClient, Path, Path]:
    db_path = tmp_path / "knowledge.db"
    content_root = tmp_path / "content"
    content_root.mkdir()
    conn = connect(db_path)
    initialize(conn)
    create_node(conn, content_root, "First Node", "algorithms", "graphs", "first", [], "core", "seed", 1)
    create_node(conn, content_root, "Second Node", "algorithms", "graphs", "second", [], "core", "seed", 2)
    conn.close()

    def get_conn() -> sqlite3.Connection:
        conn = connect(db_path)
        initialize(conn)
        return conn

    app = FastAPI()
    app.include_router(create_node_router(get_conn, content_root))
    return TestClient(app, raise_server_exceptions=False), db_path, content_root


def node_row(db_path: Path, slug: str) -> sqlite3.Row:
    conn = connect(db_path)
    initialize(conn)
    row = conn.execute("SELECT * FROM nodes WHERE slug = ?", (slug,)).fetchone()
    conn.close()
    assert row is not None
    return row


def test_display_order_success_updates_source_indexes_and_revision(tmp_path: Path):
    client, db_path, content_root = build_client(tmp_path)
    before = node_row(db_path, "second-node")
    source = content_root / before["path"]

    conn = connect(db_path)
    initialize(conn)
    conn.execute(
        "INSERT INTO graph_cache (cache_key, payload_json, updated_at) VALUES ('test', '{}', 'now')"
    )
    conn.commit()
    conn.close()

    response = client.patch(
        "/api/nodes/second-node/display-order",
        json={"display_order": 3, "expected_updated_at": before["updated_at"]},
    )
    assert response.status_code == 200, response.text
    node = response.json()["node"]
    assert node["display_order"] == 3
    after = node_row(db_path, "second-node")
    assert after["display_order"] == 3
    assert after["revision"] == before["revision"] + 1
    assert 'order: "3"' in source.read_text(encoding="utf-8")

    conn = connect(db_path)
    initialize(conn)
    assert conn.execute("SELECT COUNT(*) FROM node_fts WHERE slug = 'second-node'").fetchone()[0] == 1
    assert conn.execute("SELECT COUNT(*) FROM graph_cache").fetchone()[0] == 0
    change = conn.execute(
        "SELECT entity_type, entity_id, revision FROM sync_changes WHERE entity_type = 'node' AND entity_id = 'second-node' ORDER BY seq DESC LIMIT 1"
    ).fetchone()
    assert tuple(change) == ("node", "second-node", after["revision"])
    conn.close()


def test_display_order_rejects_duplicate_without_mutation(tmp_path: Path):
    client, db_path, content_root = build_client(tmp_path)
    before = node_row(db_path, "second-node")
    source = content_root / before["path"]
    original_text = source.read_text(encoding="utf-8")

    response = client.patch(
        "/api/nodes/second-node/display-order",
        json={"display_order": 1, "expected_updated_at": before["updated_at"]},
    )
    assert response.status_code == 409
    assert response.json()["detail"]["code"] == "display_order_conflict"
    assert source.read_text(encoding="utf-8") == original_text
    after = node_row(db_path, "second-node")
    assert after["display_order"] == before["display_order"]
    assert after["revision"] == before["revision"]


def test_display_order_rejects_stale_invalid_and_missing_requests(tmp_path: Path):
    client, db_path, content_root = build_client(tmp_path)
    before = node_row(db_path, "second-node")
    source = content_root / before["path"]
    original_text = source.read_text(encoding="utf-8")

    stale = client.patch(
        "/api/nodes/second-node/display-order",
        json={"display_order": 4, "expected_updated_at": "stale"},
    )
    assert stale.status_code == 409
    assert stale.json()["detail"]["code"] == "node_version_conflict"
    assert stale.json()["detail"]["current_updated_at"] == before["updated_at"]
    invalid = client.patch(
        "/api/nodes/second-node/display-order",
        json={"display_order": 0, "expected_updated_at": before["updated_at"]},
    )
    assert invalid.status_code == 422
    missing = client.patch(
        "/api/nodes/missing-node/display-order",
        json={"display_order": 4, "expected_updated_at": before["updated_at"]},
    )
    assert missing.status_code == 404
    assert source.read_text(encoding="utf-8") == original_text
    after = node_row(db_path, "second-node")
    assert after["display_order"] == before["display_order"]
    assert after["revision"] == before["revision"]


def test_display_order_updates_legacy_frontmatter_key(tmp_path: Path):
    client, db_path, content_root = build_client(tmp_path)
    before = node_row(db_path, "second-node")
    source = content_root / before["path"]
    source.write_text(
        source.read_text(encoding="utf-8").replace('order: "2"', 'display_order: "2"'),
        encoding="utf-8",
    )

    response = client.patch(
        "/api/nodes/second-node/display-order",
        json={"display_order": 3, "expected_updated_at": before["updated_at"]},
    )
    assert response.status_code == 200, response.text
    text = source.read_text(encoding="utf-8")
    assert 'display_order: "3"' in text
    assert '\norder: "3"' not in text


def test_display_order_rolls_back_file_and_database_on_lifecycle_failure(tmp_path: Path, monkeypatch):
    client, db_path, content_root = build_client(tmp_path)
    before = node_row(db_path, "second-node")
    source = content_root / before["path"]
    original_text = source.read_text(encoding="utf-8")

    from backend import node_lifecycle_service

    def fail_upsert(*_args, **_kwargs):
        raise RuntimeError("simulated lifecycle failure")

    monkeypatch.setattr(node_lifecycle_service, "upsert_node_file_in_conn", fail_upsert)
    response = client.patch(
        "/api/nodes/second-node/display-order",
        json={"display_order": 4, "expected_updated_at": before["updated_at"]},
    )
    assert response.status_code == 500
    assert source.read_text(encoding="utf-8") == original_text
    after = node_row(db_path, "second-node")
    assert after["display_order"] == before["display_order"]
    assert after["revision"] == before["revision"]
