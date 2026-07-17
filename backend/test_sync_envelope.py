"""Phase 0 acceptance tests for the sync change envelope.

Proves the desktop schema is syncable: revisions, client-stable IDs,
tombstones, and exactly-one-change-row-per-mutation, all inside the
mutation's own transaction.
"""

from __future__ import annotations

import sqlite3
from pathlib import Path

from backend.content_write_service import update_target_body
from backend.db import connect, initialize
from backend.ingest import ingest
from backend.learning_service import record_quiz_attempt
from backend.node_lifecycle_service import (
    create_node,
    move_node_to_trash,
    permanently_delete_node,
    restore_node,
)
from backend.reader_question_service import (
    create_reader_question,
    delete_reader_question,
    set_reader_question_status,
)


def fresh_conn(tmp_path: Path) -> sqlite3.Connection:
    conn = connect(tmp_path / "knowledge.db")
    initialize(conn)
    return conn


def sync_rows(conn: sqlite3.Connection, entity_type: str | None = None) -> list[sqlite3.Row]:
    query = "SELECT * FROM sync_changes"
    params: tuple = ()
    if entity_type:
        query += " WHERE entity_type = ?"
        params = (entity_type,)
    return conn.execute(query + " ORDER BY seq", params).fetchall()


def create_demo_node(conn: sqlite3.Connection, content_root: Path) -> str:
    return create_node(
        conn,
        content_root,
        title="Sync Envelope Demo",
        area="algorithms",
        track="general",
        summary="demo",
        tags=[],
        visibility="core",
        status="draft",
        order=1000,
    )


def test_fresh_schema_has_sync_columns_and_tables(tmp_path: Path) -> None:
    conn = fresh_conn(tmp_path)
    node_columns = {row["name"] for row in conn.execute("PRAGMA table_info(nodes)")}
    quiz_columns = {row["name"] for row in conn.execute("PRAGMA table_info(quizzes)")}
    attempt_columns = {row["name"] for row in conn.execute("PRAGMA table_info(quiz_attempts)")}
    question_columns = {row["name"] for row in conn.execute("PRAGMA table_info(reader_questions)")}
    tables = {row["name"] for row in conn.execute("SELECT name FROM sqlite_master WHERE type = 'table'")}

    assert "revision" in node_columns
    assert {"revision", "deleted_at"} <= quiz_columns
    assert "client_attempt_id" in attempt_columns
    assert "client_id" in question_columns
    assert {"capture_slips", "sync_changes"} <= tables

    version = conn.execute("SELECT value FROM schema_meta WHERE key = 'schema_version'").fetchone()
    assert version["value"] == "5"


def test_migration_upgrades_legacy_database_idempotently(tmp_path: Path) -> None:
    db_path = tmp_path / "legacy.db"
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    conn.executescript(
        """
        CREATE TABLE nodes (
            slug TEXT PRIMARY KEY, title TEXT NOT NULL, area TEXT NOT NULL,
            track TEXT NOT NULL DEFAULT 'general', display_order INTEGER NOT NULL DEFAULT 1000,
            status TEXT NOT NULL, visibility TEXT NOT NULL, summary TEXT NOT NULL,
            body TEXT NOT NULL, path TEXT NOT NULL, updated_at TEXT NOT NULL
        );
        CREATE TABLE quizzes (
            id TEXT PRIMARY KEY, title TEXT NOT NULL, area TEXT NOT NULL,
            display_order INTEGER NOT NULL DEFAULT 1000, status TEXT NOT NULL,
            visibility TEXT NOT NULL, difficulty TEXT NOT NULL, summary TEXT NOT NULL,
            body TEXT NOT NULL, path TEXT NOT NULL, weight INTEGER NOT NULL DEFAULT 1,
            updated_at TEXT NOT NULL
        );
        CREATE TABLE quiz_attempts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            quiz_id TEXT NOT NULL, grade TEXT NOT NULL,
            answered_at TEXT NOT NULL, elapsed_ms INTEGER NOT NULL DEFAULT 0,
            note TEXT NOT NULL DEFAULT ''
        );
        CREATE TABLE reader_questions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            target_type TEXT NOT NULL, target_id TEXT NOT NULL,
            question TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'open',
            created_at TEXT NOT NULL, resolved_at TEXT NOT NULL DEFAULT '',
            resolution_note TEXT NOT NULL DEFAULT ''
        );
        INSERT INTO nodes VALUES ('legacy-node', 'Legacy', 'algorithms', 'general', 1000, 'seed', 'core', '', 'body', 'nodes/algorithms/legacy.md', 'now');
        """
    )
    conn.commit()

    initialize(conn)
    initialize(conn)

    node_columns = {row["name"] for row in conn.execute("PRAGMA table_info(nodes)")}
    quiz_columns = {row["name"] for row in conn.execute("PRAGMA table_info(quizzes)")}
    attempt_columns = {row["name"] for row in conn.execute("PRAGMA table_info(quiz_attempts)")}
    question_columns = {row["name"] for row in conn.execute("PRAGMA table_info(reader_questions)")}
    assert "revision" in node_columns
    assert {"revision", "deleted_at"} <= quiz_columns
    assert "client_attempt_id" in attempt_columns
    assert "client_id" in question_columns
    row = conn.execute("SELECT revision FROM nodes WHERE slug = 'legacy-node'").fetchone()
    assert row["revision"] == 0


def test_node_create_and_body_update_log_exactly_one_change_each(tmp_path: Path) -> None:
    conn = fresh_conn(tmp_path)
    content_root = tmp_path / "content"
    slug = create_demo_node(conn, content_root)

    rows = sync_rows(conn, "node")
    assert len(rows) == 1
    assert rows[0]["entity_id"] == slug
    assert rows[0]["revision"] == 1
    assert rows[0]["tombstone"] == 0

    update_target_body(conn, content_root, "node", slug, "# Sync Envelope Demo\n\nUpdated body.")

    rows = sync_rows(conn, "node")
    assert len(rows) == 2
    assert rows[1]["revision"] == 2
    assert rows[1]["content_hash"] != rows[0]["content_hash"]
    node = conn.execute("SELECT revision FROM nodes WHERE slug = ?", (slug,)).fetchone()
    assert node["revision"] == 2
    assert rows[0]["seq"] < rows[1]["seq"]


def test_trash_then_restore_logs_tombstone_then_live_change(tmp_path: Path) -> None:
    conn = fresh_conn(tmp_path)
    content_root = tmp_path / "content"
    slug = create_demo_node(conn, content_root)

    move_node_to_trash(conn, content_root, slug)
    restore_node(conn, content_root, slug)

    rows = sync_rows(conn, "node")
    assert [row["tombstone"] for row in rows] == [0, 1, 0]
    assert [row["revision"] for row in rows] == [1, 2, 3]
    seqs = [row["seq"] for row in rows]
    assert seqs == sorted(seqs)


def test_permanent_delete_logs_tombstone_with_bumped_revision(tmp_path: Path) -> None:
    conn = fresh_conn(tmp_path)
    content_root = tmp_path / "content"
    slug = create_demo_node(conn, content_root)
    move_node_to_trash(conn, content_root, slug)

    permanently_delete_node(conn, content_root, slug)

    assert conn.execute("SELECT 1 FROM nodes WHERE slug = ?", (slug,)).fetchone() is None
    rows = sync_rows(conn, "node")
    assert rows[-1]["tombstone"] == 1
    assert rows[-1]["revision"] == 3
    assert rows[-1]["content_hash"] is None


def test_permanent_delete_node_also_tombstones_linked_reader_questions(tmp_path: Path) -> None:
    conn = fresh_conn(tmp_path)
    content_root = tmp_path / "content"
    slug = create_demo_node(conn, content_root)
    question = create_reader_question(conn, "node", slug, "Will this become orphaned?")
    move_node_to_trash(conn, content_root, slug)

    permanently_delete_node(conn, content_root, slug)

    assert conn.execute("SELECT 1 FROM reader_questions WHERE id = ?", (question["id"],)).fetchone() is None
    rows = sync_rows(conn, "reader_question")
    assert rows[-1]["entity_id"] == question["client_id"]
    assert rows[-1]["tombstone"] == 1


def test_quiz_attempt_gets_client_id_and_logs_review_attempt(tmp_path: Path) -> None:
    conn = fresh_conn(tmp_path)
    conn.execute(
        """
        INSERT INTO quizzes (id, title, area, status, visibility, difficulty, summary, body, path, updated_at)
        VALUES ('q1', 'Quiz One', 'algorithms', 'seed', 'practice', 'easy', '', 'body', 'quizzes/algorithms/q1.md', 'now')
        """
    )
    conn.commit()

    result = record_quiz_attempt(conn, "q1", "good", elapsed_ms=1200)

    assert result["client_attempt_id"]
    attempt = conn.execute("SELECT * FROM quiz_attempts WHERE id = ?", (result["attempt_id"],)).fetchone()
    assert attempt["client_attempt_id"] == result["client_attempt_id"]
    rows = sync_rows(conn, "review_attempt")
    assert len(rows) == 1
    assert rows[0]["entity_id"] == result["client_attempt_id"]
    assert rows[0]["tombstone"] == 0


def test_reader_question_lifecycle_logs_changes(tmp_path: Path) -> None:
    conn = fresh_conn(tmp_path)
    content_root = tmp_path / "content"
    slug = create_demo_node(conn, content_root)
    before = len(sync_rows(conn))

    question = create_reader_question(conn, "node", slug, "What does this node claim?")
    assert question["client_id"]
    set_reader_question_status(conn, question["id"], "resolved", "answered")
    delete_reader_question(conn, question["id"])

    rows = sync_rows(conn, "reader_question")
    assert len(rows) == 3
    assert len(sync_rows(conn)) == before + 3
    assert {row["entity_id"] for row in rows} == {question["client_id"]}
    assert rows[-1]["tombstone"] == 1


def write_node_file(content_root: Path, slug: str, body: str) -> None:
    path = content_root / "nodes" / "algorithms" / f"{slug}.md"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        "---\ntitle: \"Node\"\narea: algorithms\n---\n\n# Node\n\n" + body + "\n",
        encoding="utf-8",
    )


def write_quiz_file(content_root: Path, quiz_id: str, body: str) -> None:
    path = content_root / "quizzes" / "algorithms" / f"{quiz_id}.md"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        "---\ntitle: \"Quiz\"\narea: algorithms\n---\n\n## Prompt\n\nQ\n\n## Answer\n\n" + body + "\n",
        encoding="utf-8",
    )


def test_ingest_preserves_revisions_attempts_and_logs_only_deltas(tmp_path: Path) -> None:
    content_root = tmp_path / "content"
    db_path = tmp_path / "knowledge.db"
    write_node_file(content_root, "n1", "Body v1.")
    write_quiz_file(content_root, "q1", "A1")

    ingest(content_root, db_path)
    conn = connect(db_path)
    baseline_changes = len(sync_rows(conn))
    assert baseline_changes == 2
    assert conn.execute("SELECT revision FROM nodes WHERE slug = 'n1'").fetchone()["revision"] == 1

    record_quiz_attempt(conn, "q1", "good")
    conn.close()

    ingest(content_root, db_path)
    conn = connect(db_path)
    assert len(sync_rows(conn)) == baseline_changes + 1  # only the attempt row
    assert conn.execute("SELECT revision FROM nodes WHERE slug = 'n1'").fetchone()["revision"] == 1
    assert conn.execute("SELECT revision FROM quizzes WHERE id = 'q1'").fetchone()["revision"] == 1
    assert conn.execute("SELECT COUNT(*) AS c FROM quiz_attempts").fetchone()["c"] == 1
    conn.close()

    write_node_file(content_root, "n1", "Body v2 with more detail.")
    ingest(content_root, db_path)
    conn = connect(db_path)
    node_rows = [row for row in sync_rows(conn, "node")]
    assert node_rows[-1]["revision"] == 2
    assert conn.execute("SELECT revision FROM nodes WHERE slug = 'n1'").fetchone()["revision"] == 2
    assert conn.execute("SELECT revision FROM quizzes WHERE id = 'q1'").fetchone()["revision"] == 1
    conn.close()

    (content_root / "quizzes" / "algorithms" / "q1.md").unlink()
    ingest(content_root, db_path)
    conn = connect(db_path)
    quiz_rows = [row for row in sync_rows(conn, "quiz")]
    assert quiz_rows[-1]["tombstone"] == 1
    assert quiz_rows[-1]["entity_id"] == "q1"
    conn.close()
