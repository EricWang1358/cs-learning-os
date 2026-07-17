"""Phase 2a contract tests for the scoped manifest/pull endpoints."""

from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI
from fastapi.testclient import TestClient

from backend import sync_envelope
from backend.content_write_service import update_target_body
from backend.db import connect, initialize
from backend.learning_service import record_quiz_attempt
from backend.node_lifecycle_service import create_node
from backend.reader_question_service import create_reader_question
from backend.sync_router import create_sync_router

SCOPE_ALGORITHMS = {"areas": ["algorithms"], "includeDueReviews": False, "pinnedNodeIds": []}


def build_client(db_path: Path) -> TestClient:
    def get_conn():
        conn = connect(db_path)
        initialize(conn)
        return conn

    app = FastAPI()
    app.include_router(create_sync_router(get_conn, is_loopback=lambda request: True))
    return TestClient(app)


def credential(client: TestClient) -> str:
    token = client.post("/api/sync/v1/pairing-tokens").json()["token"]
    return client.post("/api/sync/v1/pair", json={"token": token, "device_name": "phone"}).json()["credential"]


def auth_headers(client: TestClient) -> dict:
    return {"Authorization": f"Bearer {credential(client)}"}


def seed_two_area_library(db_path: Path, content_root: Path) -> tuple[str, str]:
    with connect(db_path) as conn:
        initialize(conn)
        alg_slug = create_node(conn, content_root, "Alg Node", "algorithms", "general", "", [], "core", "seed", 1000)
        sys_slug = create_node(conn, content_root, "Sys Node", "systems", "general", "", [], "core", "seed", 1000)
        conn.execute(
            """
            INSERT INTO quizzes (id, title, area, status, visibility, difficulty, summary, body, path, updated_at)
            VALUES ('quiz-alg', 'Alg Quiz', 'algorithms', 'seed', 'practice', 'easy', '', 'quiz body', 'quizzes/algorithms/q.md', 'now')
            """
        )
        conn.commit()
    return alg_slug, sys_slug


def manifest(client: TestClient, headers: dict, cursor: int = 0, scope: dict | None = None, server_id: str = "") -> dict:
    response = client.post(
        "/api/sync/v1/manifest",
        json={"cursor": cursor, "serverId": server_id, "scope": scope or SCOPE_ALGORITHMS},
        headers=headers,
    )
    assert response.status_code == 200, response.text
    return response.json()


def test_baseline_manifest_scoped_and_idle_delta_empty(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    client = build_client(db_path)
    headers = auth_headers(client)
    alg_slug, sys_slug = seed_two_area_library(db_path, tmp_path / "content")

    body = manifest(client, headers, cursor=0)
    assert body["reset"] is False
    assert body["hasMore"] is False
    assert body["cursor"] > 0
    changes = {(change["type"], change["id"]): change for change in body["changes"]}
    assert ("quiz", "quiz-alg") in changes
    assert ("node", alg_slug) in changes
    # Deltas are unfiltered but carry area labels so the client can drop
    # out-of-scope entities itself (move-out detection).
    assert changes[("node", sys_slug)]["area"] == "systems"

    idle = manifest(client, headers, cursor=body["cursor"])
    assert idle["changes"] == []
    assert idle["cursor"] == body["cursor"]


def test_incremental_manifest_reports_only_new_changes(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    client = build_client(db_path)
    headers = auth_headers(client)
    alg_slug, _sys_slug = seed_two_area_library(db_path, tmp_path / "content")
    baseline = manifest(client, headers, cursor=0)

    with connect(db_path) as conn:
        initialize(conn)
        update_target_body(conn, tmp_path / "content", "node", alg_slug, "# Alg Node\n\nRevised body.")

    delta = manifest(client, headers, cursor=baseline["cursor"])
    assert len(delta["changes"]) == 1
    change = delta["changes"][0]
    assert change["type"] == "node"
    assert change["id"] == alg_slug
    assert change["revision"] == 2
    assert change["tombstone"] is False
    assert delta["cursor"] > baseline["cursor"]


def test_paged_manifest_cursor_advances_only_through_returned_rows(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    client = build_client(db_path)
    headers = auth_headers(client)
    with connect(db_path) as conn:
        initialize(conn)
        for index in range(501):
            conn.execute(
                """
                INSERT INTO sync_changes (entity_type, entity_id, revision, content_hash, tombstone, changed_at)
                VALUES ('capture_slip', ?, 1, 'hash', 0, '2026-07-17T00:00:00+00:00')
                """,
                (f"slip-{index}",),
            )
        conn.commit()

    first = manifest(client, headers, cursor=0)
    assert first["hasMore"] is True
    assert len(first["changes"]) == 500
    assert first["cursor"] == 500

    second = manifest(client, headers, cursor=first["cursor"])
    assert second["hasMore"] is False
    assert [change["id"] for change in second["changes"]] == ["slip-500"]


def test_manifest_rows_carry_area_for_move_out_detection(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    client = build_client(db_path)
    headers = auth_headers(client)
    alg_slug, _sys_slug = seed_two_area_library(db_path, tmp_path / "content")
    baseline = manifest(client, headers, cursor=0)

    with connect(db_path) as conn:
        initialize(conn)
        conn.execute("UPDATE nodes SET area = 'systems' WHERE slug = ?", (alg_slug,))
        sync_envelope.bump_revision_and_log(
            conn, "nodes", "slug", sync_envelope.ENTITY_NODE, alg_slug, "body"
        )
        conn.commit()

    delta = manifest(client, headers, cursor=baseline["cursor"])
    moved = [change for change in delta["changes"] if change["id"] == alg_slug]
    assert len(moved) == 1
    assert moved[0]["area"] == "systems"


def test_server_id_mismatch_requests_reset(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    client = build_client(db_path)
    headers = auth_headers(client)
    body = manifest(client, headers, cursor=12, server_id="some-other-server")
    assert body["reset"] is True
    assert body["changes"] == []
    assert body["cursor"] == 0

    healthy = manifest(client, headers, cursor=12, server_id=body["serverId"])
    assert healthy["reset"] is False


def test_pull_returns_records_and_enforces_scope(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    client = build_client(db_path)
    headers = auth_headers(client)
    alg_slug, sys_slug = seed_two_area_library(db_path, tmp_path / "content")

    def pull(entity_type: str, ids: list[str], scope: dict | None = None) -> dict:
        response = client.post(
            "/api/sync/v1/pull",
            json={"entityType": entity_type, "ids": ids, "scope": scope or SCOPE_ALGORITHMS},
            headers=headers,
        )
        assert response.status_code == 200, response.text
        return response.json()

    node_records = pull("node", [alg_slug])["records"]
    assert len(node_records) == 1
    record = node_records[0]
    assert record["id"] == alg_slug
    assert record["revision"] == 1
    assert record["hash"]
    assert "Alg Node" in record["body"]

    assert pull("node", [sys_slug])["records"] == []
    assert pull("node", [alg_slug], scope={"areas": ["systems"]})["records"] == []

    quiz_records = pull("quiz", ["quiz-alg"])["records"]
    assert len(quiz_records) == 1
    assert quiz_records[0]["body"] == "quiz body"

    bad = client.post(
        "/api/sync/v1/pull",
        json={"entityType": "bogus", "ids": ["x"], "scope": SCOPE_ALGORITHMS},
        headers=headers,
    )
    assert bad.status_code == 400


def test_pull_attempts_and_reader_questions(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    client = build_client(db_path)
    headers = auth_headers(client)
    alg_slug, _sys_slug = seed_two_area_library(db_path, tmp_path / "content")

    with connect(db_path) as conn:
        initialize(conn)
        attempt = record_quiz_attempt(conn, "quiz-alg", "good", elapsed_ms=500)
        question = create_reader_question(conn, "node", alg_slug, "Why does this matter?")

    def pull(entity_type: str, ids: list[str], scope: dict | None = None) -> list[dict]:
        response = client.post(
            "/api/sync/v1/pull",
            json={"entityType": entity_type, "ids": ids, "scope": scope or SCOPE_ALGORITHMS},
            headers=headers,
        )
        assert response.status_code == 200, response.text
        return response.json()["records"]

    attempts = pull("review_attempt", [attempt["client_attempt_id"]])
    assert len(attempts) == 1
    assert attempts[0]["grade"] == "good"
    assert attempts[0]["quizId"] == "quiz-alg"

    questions = pull("reader_question", [question["client_id"]])
    assert len(questions) == 1
    assert questions[0]["question"] == "Why does this matter?"
    assert questions[0]["status"] == "open"
    assert pull("reader_question", [question["client_id"]], scope={"areas": ["systems"]}) == []


def test_manifest_and_pull_require_credentials(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    client = build_client(db_path)
    seed_two_area_library(db_path, tmp_path / "content")
    response = client.post("/api/sync/v1/manifest", json={"cursor": 0, "scope": SCOPE_ALGORITHMS})
    assert response.status_code == 401
    response = client.post("/api/sync/v1/pull", json={"entityType": "node", "ids": [], "scope": SCOPE_ALGORITHMS})
    assert response.status_code == 401
