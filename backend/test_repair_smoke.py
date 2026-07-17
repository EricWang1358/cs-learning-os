"""Quick smoke test for POST /api/system/repair"""
from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI
from fastapi.testclient import TestClient

from backend.db import connect, initialize
from backend.ingest import ingest
from backend.productization_router import create_productization_router


def build_client(tmp_path: Path) -> TestClient:
    db_path = tmp_path / "knowledge.db"
    content_root = tmp_path / "content"
    export_root = tmp_path / "generated"

    def get_conn():
        conn = connect(db_path)
        initialize(conn)
        return conn

    app = FastAPI()
    app.include_router(create_productization_router(get_conn, content_root, export_root))
    return TestClient(app)


def test_repair_creates_stubs_for_broken_links(tmp_path: Path) -> None:
    client = build_client(tmp_path)
    db_path = tmp_path / "knowledge.db"
    content_root = tmp_path / "content"

    # Seed a node that links to a missing target
    (content_root / "nodes" / "algorithms").mkdir(parents=True)
    (content_root / "nodes" / "algorithms" / "source-node.md").write_text(
        """---
slug: "source-node"
title: "Source"
area: "algorithms"
status: "seed"
visibility: "core"
summary: ""
prerequisites:
  - "missing-target"
related:
  - "also-missing"
---

Body.
""",
        encoding="utf-8",
    )

    ingest(content_root, db_path)

    report = client.get("/api/system/repair").json()
    assert report["issue_count"] == 2
    assert all(i["kind"] == "broken_node_link" for i in report["issues"])

    result = client.post("/api/system/repair", json={}).json()
    assert result["report"]["broken_node_link"]["created_count"] == 2

    remaining = result["remaining"]
    assert remaining["issue_count"] == 0
    assert remaining["ok"] is True

    # Verify stub file was created
    stub_path = content_root / "nodes" / "stubs" / "missing-target.md"
    assert stub_path.is_file()
    text = stub_path.read_text(encoding="utf-8")
    assert 'visibility: "support"' in text
    assert 'status: "stub"' in text


if __name__ == "__main__":
    import tempfile

    with tempfile.TemporaryDirectory() as tmp:
        test_repair_creates_stubs_for_broken_links(Path(tmp))
    print("OK")
