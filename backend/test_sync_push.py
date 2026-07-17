"""Phase 3a contract tests for append-only push endpoints."""

from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI
from fastapi.testclient import TestClient

from backend import sync_auth
from backend.db import connect, initialize
from backend import sync_service
from backend.sync_router import create_sync_router


def build_client(db_path: Path, content_root: Path | None = None) -> TestClient:
    def get_conn():
        conn = connect(db_path)
        initialize(conn)
        return conn

    app = FastAPI()
    app.include_router(
        create_sync_router(
            get_conn,
            is_loopback=lambda request: True,
            content_root=content_root,
        )
    )
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


def test_push_node_applies_matching_revision_once_and_rejects_stale_change(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    content_root = tmp_path / "content"
    seed_library(db_path)
    with connect(db_path) as conn:
        initialize(conn)
        conn.execute("UPDATE nodes SET revision = 1 WHERE slug = 'n1'")
        conn.commit()
    item = {
        "changeId": "node-change-1",
        "id": "n1",
        "title": "Updated Node",
        "area": "algorithms",
        "track": "general",
        "summary": "updated summary",
        "body": "# Updated Node\n\nPhone edit.",
        "visibility": "core",
        "baseRevision": 1,
        "revision": 2,
        "tombstone": False,
    }
    handler = getattr(sync_service, "push_nodes", None)
    assert handler is not None

    with connect(db_path) as conn:
        initialize(conn)
        first = handler(conn, content_root, "phone-a", [item])
        replay = handler(conn, content_root, "phone-a", [item])
        stale = handler(
            conn,
            content_root,
            "phone-a",
            [item | {"changeId": "node-change-2", "body": "# Stale", "revision": 2}],
        )
        row = conn.execute("SELECT title, body, revision FROM nodes WHERE slug = 'n1'").fetchone()

    assert first == [{"id": "n1", "status": "accepted", "revision": 2}]
    assert replay == first
    assert stale == [{"id": "n1", "status": "rejected", "reason": "stale_revision", "revision": 2}]
    assert dict(row) == {
        "title": "Updated Node",
        "body": "# Updated Node\n\nPhone edit.",
        "revision": 2,
    }
    assert (content_root / "nodes" / "algorithms" / "n1.md").read_text(encoding="utf-8").endswith("# Updated Node\n\nPhone edit.\n")


def test_push_node_preserves_desktop_only_markdown_metadata(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    content_root = tmp_path / "content"
    seed_library(db_path)
    node_path = content_root / "nodes" / "algorithms" / "n1.md"
    node_path.parent.mkdir(parents=True)
    node_path.write_text(
        """---
slug: \"n1\"
title: \"Node\"
area: \"algorithms\"
track: \"general\"
order: \"42\"
status: \"seed\"
visibility: \"core\"
summary: \"desktop summary\"
tags: [\"graphs\", \"invariants\"]
prerequisites: [\"setup\"]
source_url: \"https://example.test/notes\"
---

Desktop body.
""",
        encoding="utf-8",
    )
    with connect(db_path) as conn:
        initialize(conn)
        conn.execute("UPDATE nodes SET revision = 1 WHERE slug = 'n1'")
        conn.commit()

    with connect(db_path) as conn:
        initialize(conn)
        receipts = sync_service.push_nodes(
            conn,
            content_root,
            "phone-a",
            [
                {
                    "changeId": "keep-metadata",
                    "id": "n1",
                    "title": "Phone title",
                    "area": "algorithms",
                    "track": "general",
                    "summary": "phone summary",
                    "body": "Phone body.",
                    "visibility": "support",
                    "baseRevision": 1,
                    "revision": 2,
                    "tombstone": False,
                }
            ],
        )

    assert receipts == [{"id": "n1", "status": "accepted", "revision": 2}]
    source = node_path.read_text(encoding="utf-8")
    assert 'tags: ["graphs", "invariants"]' in source
    assert 'prerequisites: ["setup"]' in source
    assert 'source_url: "https://example.test/notes"' in source
    assert 'order: "42"' in source
    assert 'title: "Phone title"' in source
    assert source.endswith("Phone body.\n")


def test_push_node_tombstone_permanently_deletes_and_logs_sync_change(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    content_root = tmp_path / "content"
    seed_library(db_path)
    node_path = content_root / "nodes" / "algorithms" / "n1.md"
    node_path.parent.mkdir(parents=True, exist_ok=True)
    node_path.write_text(
        "---\nslug: \"n1\"\ntitle: \"Node\"\narea: \"algorithms\"\ntrack: \"general\"\nvisibility: \"trash\"\n---\n\nDeleted on phone.\n",
        encoding="utf-8",
    )
    with connect(db_path) as conn:
        initialize(conn)
        conn.execute("UPDATE nodes SET revision = 1, visibility = 'trash' WHERE slug = 'n1'")
        conn.commit()

    with connect(db_path) as conn:
        initialize(conn)
        receipts = sync_service.push_nodes(
            conn,
            content_root,
            "phone-a",
            [
                {
                    "changeId": "node-delete-1",
                    "id": "n1",
                    "title": "Node",
                    "area": "algorithms",
                    "track": "general",
                    "summary": "",
                    "body": "# Node\n\nDeleted on phone.",
                    "visibility": "trash",
                    "baseRevision": 1,
                    "revision": 2,
                    "tombstone": True,
                }
            ],
        )
        row = conn.execute("SELECT 1 FROM nodes WHERE slug = 'n1'").fetchone()
        changes = conn.execute(
            "SELECT revision, tombstone FROM sync_changes WHERE entity_type = 'node' AND entity_id = 'n1' ORDER BY seq"
        ).fetchall()

    assert receipts == [{"id": "n1", "status": "accepted", "revision": 2}]
    assert row is None
    assert not node_path.exists()
    assert [dict(change) for change in changes][-1] == {"revision": 2, "tombstone": 1}


def test_push_node_tombstone_also_deletes_linked_reader_questions(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    content_root = tmp_path / "content"
    seed_library(db_path)
    node_path = content_root / "nodes" / "algorithms" / "n1.md"
    node_path.parent.mkdir(parents=True, exist_ok=True)
    node_path.write_text(
        "---\nslug: \"n1\"\ntitle: \"Node\"\narea: \"algorithms\"\ntrack: \"general\"\nvisibility: \"trash\"\n---\n\nDeleted on phone.\n",
        encoding="utf-8",
    )
    with connect(db_path) as conn:
        initialize(conn)
        conn.execute("UPDATE nodes SET revision = 1, visibility = 'trash' WHERE slug = 'n1'")
        conn.execute(
            """
            INSERT INTO reader_questions (client_id, target_type, target_id, question, status, created_at)
            VALUES ('rq-node-1', 'node', 'n1', 'Still attached?', 'open', '2026-07-17T10:00:00+00:00')
            """
        )
        conn.commit()

    with connect(db_path) as conn:
        initialize(conn)
        receipts = sync_service.push_nodes(
            conn,
            content_root,
            "phone-a",
            [
                {
                    "changeId": "node-delete-rq-1",
                    "id": "n1",
                    "title": "Node",
                    "area": "algorithms",
                    "track": "general",
                    "summary": "",
                    "body": "# Node\n\nDeleted on phone.",
                    "visibility": "trash",
                    "baseRevision": 1,
                    "revision": 2,
                    "tombstone": True,
                }
            ],
        )
        question = conn.execute("SELECT 1 FROM reader_questions WHERE client_id = 'rq-node-1'").fetchone()
        question_changes = conn.execute(
            "SELECT tombstone FROM sync_changes WHERE entity_type = 'reader_question' AND entity_id = 'rq-node-1' ORDER BY seq"
        ).fetchall()

    assert receipts == [{"id": "n1", "status": "accepted", "revision": 2}]
    assert question is None
    assert [row["tombstone"] for row in question_changes][-1] == 1


def test_push_nodes_endpoint_applies_an_authenticated_mobile_edit(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    content_root = tmp_path / "content"
    client = build_client(db_path, content_root)
    headers = {"Authorization": f"Bearer {credential(client)}"}
    seed_library(db_path)
    with connect(db_path) as conn:
        initialize(conn)
        conn.execute("UPDATE nodes SET revision = 1 WHERE slug = 'n1'")
        conn.commit()

    receipts = push(
        client,
        headers,
        "nodes",
        [
            {
                "changeId": "http-node-1",
                "id": "n1",
                "title": "HTTP Node",
                "area": "algorithms",
                "track": "general",
                "summary": "saved through the route",
                "body": "# HTTP Node\n\nSaved from Android.",
                "visibility": "core",
                "baseRevision": 1,
                "revision": 2,
                "tombstone": False,
            }
        ],
    )

    assert receipts == [{"id": "n1", "status": "accepted", "revision": 2}]
    with connect(db_path) as conn:
        initialize(conn)
        row = conn.execute("SELECT title, body, revision FROM nodes WHERE slug = 'n1'").fetchone()
    assert dict(row) == {
        "title": "HTTP Node",
        "body": "# HTTP Node\n\nSaved from Android.",
        "revision": 2,
    }


def test_push_nodes_endpoint_rejects_malformed_desktop_source_per_record(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    content_root = tmp_path / "content"
    client = build_client(db_path, content_root)
    headers = {"Authorization": f"Bearer {credential(client)}"}
    seed_library(db_path)
    node_path = content_root / "nodes" / "algorithms" / "n1.md"
    node_path.parent.mkdir(parents=True)
    node_path.write_text("Malformed desktop file.", encoding="utf-8")
    with connect(db_path) as conn:
        initialize(conn)
        conn.execute("UPDATE nodes SET revision = 1 WHERE slug = 'n1'")
        conn.commit()

    receipts = push(
        client,
        headers,
        "nodes",
        [
            {
                "changeId": "bad-source-node",
                "id": "n1",
                "title": "Phone title",
                "area": "algorithms",
                "track": "general",
                "summary": "phone summary",
                "body": "Phone body.",
                "visibility": "core",
                "baseRevision": 1,
                "revision": 2,
                "tombstone": False,
            }
        ],
    )

    assert receipts == [{"id": "n1", "status": "rejected", "reason": "invalid_source", "revision": 1}]
    assert node_path.read_text(encoding="utf-8") == "Malformed desktop file."


def test_push_quizzes_endpoint_updates_once_and_preserves_desktop_metadata(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    content_root = tmp_path / "content"
    client = build_client(db_path, content_root)
    headers = {"Authorization": f"Bearer {credential(client)}"}
    seed_library(db_path)
    quiz_path = content_root / "quizzes" / "algorithms" / "q1.md"
    quiz_path.parent.mkdir(parents=True)
    quiz_path.write_text(
        """---
id: \"q1\"
title: \"Old Quiz\"
area: \"algorithms\"
order: \"42\"
status: \"seed\"
visibility: \"practice\"
difficulty: \"easy\"
summary: \"desktop summary\"
tags: [\"memory\"]
source_url: \"https://example.test/quiz\"
---

## Prompt

Old prompt.

## Answer

Old answer.
""",
        encoding="utf-8",
    )
    with connect(db_path) as conn:
        initialize(conn)
        conn.execute("UPDATE quizzes SET revision = 1 WHERE id = 'q1'")
        conn.commit()
    item = {
        "changeId": "quiz-change-1",
        "id": "q1",
        "area": "algorithms",
        "body": "## Prompt\n\nPhone prompt.\n\n## Answer\n\nPhone answer.",
        "visibility": "practice",
        "baseRevision": 1,
        "revision": 2,
        "tombstone": False,
    }

    first = push(client, headers, "quizzes", [item])
    replay = push(client, headers, "quizzes", [item])
    stale = push(client, headers, "quizzes", [item | {"changeId": "quiz-change-2"}])

    assert first == [{"id": "q1", "status": "accepted", "revision": 2}]
    assert replay == first
    assert stale == [{"id": "q1", "status": "rejected", "reason": "stale_revision", "revision": 2}]
    source = quiz_path.read_text(encoding="utf-8")
    assert 'tags: ["memory"]' in source
    assert 'source_url: "https://example.test/quiz"' in source
    assert 'order: "42"' in source
    assert 'title: "Old Quiz"' in source
    assert 'difficulty: "easy"' in source
    assert 'summary: "desktop summary"' in source
    assert source.endswith("## Prompt\n\nPhone prompt.\n\n## Answer\n\nPhone answer.\n")
    with connect(db_path) as conn:
        initialize(conn)
        row = conn.execute("SELECT title, difficulty, summary, body, revision FROM quizzes WHERE id = 'q1'").fetchone()
    assert dict(row) == {
        "title": "Old Quiz",
        "difficulty": "easy",
        "summary": "desktop summary",
        "body": "## Prompt\n\nPhone prompt.\n\n## Answer\n\nPhone answer.",
        "revision": 2,
    }


def test_push_quiz_tombstone_permanently_deletes_and_logs_sync_change(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    content_root = tmp_path / "content"
    seed_library(db_path)
    quiz_path = content_root / "quizzes" / "algorithms" / "q1.md"
    quiz_path.parent.mkdir(parents=True, exist_ok=True)
    quiz_path.write_text(
        "---\nid: \"q1\"\ntitle: \"Quiz\"\narea: \"algorithms\"\nvisibility: \"trash\"\n---\n\n## Prompt\n\nQ\n\n## Answer\n\nA\n",
        encoding="utf-8",
    )
    with connect(db_path) as conn:
        initialize(conn)
        conn.execute("UPDATE quizzes SET revision = 1, visibility = 'trash' WHERE id = 'q1'")
        conn.commit()

    with connect(db_path) as conn:
        initialize(conn)
        receipts = sync_service.push_quizzes(
            conn,
            content_root,
            "phone-a",
            [
                {
                    "changeId": "quiz-delete-1",
                    "id": "q1",
                    "area": "algorithms",
                    "body": "## Prompt\n\nQ\n\n## Answer\n\nA",
                    "visibility": "trash",
                    "baseRevision": 1,
                    "revision": 2,
                    "tombstone": True,
                }
            ],
        )
        row = conn.execute("SELECT 1 FROM quizzes WHERE id = 'q1'").fetchone()
        changes = conn.execute(
            "SELECT revision, tombstone FROM sync_changes WHERE entity_type = 'quiz' AND entity_id = 'q1' ORDER BY seq"
        ).fetchall()

    assert receipts == [{"id": "q1", "status": "accepted", "revision": 2}]
    assert row is None
    assert not quiz_path.exists()
    assert [dict(change) for change in changes][-1] == {"revision": 2, "tombstone": 1}


def test_push_quiz_tombstone_also_deletes_linked_reader_questions(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    content_root = tmp_path / "content"
    seed_library(db_path)
    quiz_path = content_root / "quizzes" / "algorithms" / "q1.md"
    quiz_path.parent.mkdir(parents=True, exist_ok=True)
    quiz_path.write_text(
        "---\nid: \"q1\"\ntitle: \"Quiz\"\narea: \"algorithms\"\nvisibility: \"trash\"\n---\n\n## Prompt\n\nQ\n\n## Answer\n\nA\n",
        encoding="utf-8",
    )
    with connect(db_path) as conn:
        initialize(conn)
        conn.execute("UPDATE quizzes SET revision = 1, visibility = 'trash' WHERE id = 'q1'")
        conn.execute(
            """
            INSERT INTO reader_questions (client_id, target_type, target_id, question, status, created_at)
            VALUES ('rq-quiz-1', 'quiz', 'q1', 'Still attached?', 'open', '2026-07-17T10:00:00+00:00')
            """
        )
        conn.commit()

    with connect(db_path) as conn:
        initialize(conn)
        receipts = sync_service.push_quizzes(
            conn,
            content_root,
            "phone-a",
            [
                {
                    "changeId": "quiz-delete-rq-1",
                    "id": "q1",
                    "area": "algorithms",
                    "body": "## Prompt\n\nQ\n\n## Answer\n\nA",
                    "visibility": "trash",
                    "baseRevision": 1,
                    "revision": 2,
                    "tombstone": True,
                }
            ],
        )
        question = conn.execute("SELECT 1 FROM reader_questions WHERE client_id = 'rq-quiz-1'").fetchone()
        question_changes = conn.execute(
            "SELECT tombstone FROM sync_changes WHERE entity_type = 'reader_question' AND entity_id = 'rq-quiz-1' ORDER BY seq"
        ).fetchall()

    assert receipts == [{"id": "q1", "status": "accepted", "revision": 2}]
    assert question is None
    assert [row["tombstone"] for row in question_changes][-1] == 1


def test_push_quizzes_endpoint_creates_a_new_mobile_quiz(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    content_root = tmp_path / "content"
    client = build_client(db_path, content_root)
    headers = {"Authorization": f"Bearer {credential(client)}"}

    receipts = push(
        client,
        headers,
        "quizzes",
        [
            {
                "changeId": "new-quiz-change",
                "id": "mobile-quiz-1",
                "area": "algorithms",
                "body": "## Prompt\n\nWhat is a TLB?\n\n## Answer\n\nA translation cache.",
                "visibility": "practice",
                "baseRevision": None,
                "revision": 1,
                "tombstone": False,
            }
        ],
    )

    assert receipts == [{"id": "mobile-quiz-1", "status": "accepted", "revision": 1}]
    source = content_root / "quizzes" / "algorithms" / "mobile-quiz-1.md"
    assert source.is_file()
    assert 'difficulty: "medium"' in source.read_text(encoding="utf-8")
    assert 'title: "What is a TLB?"' in source.read_text(encoding="utf-8")
    with connect(db_path) as conn:
        initialize(conn)
        row = conn.execute("SELECT difficulty, revision FROM quizzes WHERE id = 'mobile-quiz-1'").fetchone()
        changes = conn.execute(
            "SELECT revision FROM sync_changes WHERE entity_type = 'quiz' AND entity_id = 'mobile-quiz-1'"
        ).fetchall()
    assert dict(row) == {"difficulty": "medium", "revision": 1}
    assert [change["revision"] for change in changes] == [1]


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
