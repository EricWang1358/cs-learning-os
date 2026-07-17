"""Phase 3a contract tests for append-only push endpoints."""

from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI
from fastapi.testclient import TestClient

from backend import sync_auth
from backend.db import connect, initialize
from backend.sync_router import create_sync_router


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


def seed_library(db_path: Path) -> None:
    with connect(db_path) as conn:
        initialize(conn)
        conn.execute(
            """
            INSERT INTO nodes (slug, title, area, status, visibility, summary, body, path, updated_at)
            VALUES ('n1', 'Node', 'algorithms', 'seed', 'core', '', 'body', 'nodes/algorithms/n1.md', 'now')
            """
        )
        conn.execute(
            """
            INSERT INTO quizzes (id, title, area, status, visibility, difficulty, summary, body, path, updated_at)
            VALUES ('q1', 'Quiz', 'algorithms', 'seed', 'practice', 'easy', '', 'body', 'quizzes/algorithms/q1.md', 'now')
            """
        )
        conn.commit()


def push(client: TestClient, headers: dict, path: str, items: list[dict]) -> list[dict]:
    response = client.post(f"/api/sync/v1/push/{path}", json={"items": items}, headers=headers)
    assert response.status_code == 200, response.text
    return response.json()["receipts"]


def test_push_attempts_accept_dedupe_and_reject_unknown(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    client = build_client(db_path)
    headers = {"Authorization": f"Bearer {credential(client)}"}
    seed_library(db_path)

    item = {
        "clientAttemptId": "att-1",
        "quizId": "q1",
        "grade": "good",
        "answeredAt": "2026-07-16T09:30:00+00:00",
        "elapsedMs": 800,
        "note": "",
    }
    receipts = push(client, headers, "attempts", [item])
    assert receipts == [{"id": "att-1", "status": "accepted"}]

    receipts = push(client, headers, "attempts", [item])
    assert receipts == [{"id": "att-1", "status": "duplicate"}]

    receipts = push(
        client,
        headers,
        "attempts",
        [{"clientAttemptId": "att-2", "quizId": "ghost", "grade": "good"}],
    )
    assert receipts == [{"id": "att-2", "status": "rejected", "reason": "unknown_quiz"}]

    with connect(db_path) as conn:
        initialize(conn)
        attempt = conn.execute(
            "SELECT * FROM quiz_attempts WHERE client_attempt_id = 'att-1'"
        ).fetchone()
        assert attempt["answered_at"] == "2026-07-16T09:30:00+00:00"
        queue = conn.execute(
            "SELECT * FROM review_queue WHERE target_type = 'quiz' AND target_id = 'q1'"
        ).fetchone()
        assert queue is not None
        assert int(queue["reps"]) == 1
        changes = conn.execute(
            "SELECT * FROM sync_changes WHERE entity_type = 'review_attempt' AND entity_id = 'att-1'"
        ).fetchall()
        assert len(changes) == 1


def test_push_attempts_rejects_invalid_grade_and_time(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    client = build_client(db_path)
    headers = {"Authorization": f"Bearer {credential(client)}"}
    seed_library(db_path)

    bad_grade = client.post(
        "/api/sync/v1/push/attempts",
        json={"items": [{"clientAttemptId": "a1", "quizId": "q1", "grade": "perfect"}]},
        headers=headers,
    )
    assert bad_grade.status_code == 422

    receipts = push(
        client,
        headers,
        "attempts",
        [{"clientAttemptId": "a2", "quizId": "q1", "grade": "good", "answeredAt": "not-a-time"}],
    )
    assert receipts == [{"id": "a2", "status": "rejected", "reason": "invalid_answered_at"}]


def test_push_captures_dedupes_and_validates_type(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    client = build_client(db_path)
    headers = {"Authorization": f"Bearer {credential(client)}"}
    seed_library(db_path)

    item = {
        "id": "slip-1",
        "body": "TLB 是页表缓存",
        "type": "concept_seed",
        "topicHint": "TLB",
        "sourceLabel": "phone",
        "createdAt": "2026-07-16T09:30:00+00:00",
    }
    receipts = push(client, headers, "captures", [item])
    assert receipts == [{"id": "slip-1", "status": "accepted"}]
    receipts = push(client, headers, "captures", [item])
    assert receipts == [{"id": "slip-1", "status": "duplicate"}]
    receipts = push(client, headers, "captures", [{"id": "slip-2", "body": "x", "type": "weird"}])
    assert receipts == [{"id": "slip-2", "status": "rejected", "reason": "unknown_type"}]

    with connect(db_path) as conn:
        initialize(conn)
        slip = conn.execute("SELECT * FROM capture_slips WHERE id = 'slip-1'").fetchone()
        assert slip["body"] == "TLB 是页表缓存"
        assert slip["status"] == "inbox"
        changes = conn.execute(
            "SELECT * FROM sync_changes WHERE entity_type = 'capture_slip' AND entity_id = 'slip-1'"
        ).fetchall()
        assert len(changes) == 1


def test_push_reader_questions_dedupes_and_rejects_unknown_target(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    client = build_client(db_path)
    headers = {"Authorization": f"Bearer {credential(client)}"}
    seed_library(db_path)

    item = {"clientId": "rq-1", "nodeId": "n1", "question": "为什么 TLB 会 miss？"}
    receipts = push(client, headers, "reader-questions", [item])
    assert receipts == [{"id": "rq-1", "status": "accepted"}]
    receipts = push(client, headers, "reader-questions", [item])
    assert receipts == [{"id": "rq-1", "status": "duplicate"}]
    receipts = push(client, headers, "reader-questions", [{"clientId": "rq-2", "nodeId": "ghost", "question": "x"}])
    assert receipts == [{"id": "rq-2", "status": "rejected", "reason": "unknown_target"}]

    with connect(db_path) as conn:
        initialize(conn)
        question = conn.execute("SELECT * FROM reader_questions WHERE client_id = 'rq-1'").fetchone()
        assert question["status"] == "open"
        assert question["target_type"] == "node"
        assert question["target_id"] == "n1"


def test_push_requires_push_scope(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    client = build_client(db_path)
    with connect(db_path) as conn:
        initialize(conn)
        sync_auth.ensure_sync_auth_schema(conn)
        read_only = "css_readonly"
        conn.execute(
            """
            INSERT INTO sync_devices (id, name, credential_hash, scopes, created_at)
            VALUES ('dev-ro', 'read-only', ?, 'sync:read', 'now')
            """,
            (sync_auth.hash_secret(read_only),),
        )
        conn.commit()

    response = client.post(
        "/api/sync/v1/push/attempts",
        json={"items": []},
        headers={"Authorization": f"Bearer {read_only}"},
    )
    assert response.status_code == 401

    response = client.post(
        "/api/sync/v1/push/attempts",
        json={"items": []},
    )
    assert response.status_code == 401
