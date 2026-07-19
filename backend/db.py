from __future__ import annotations

import sqlite3
from datetime import datetime, timezone
from pathlib import Path


SCHEMA_VERSION = "5"
PACKAGE_FORMAT_VERSION = "1"


SCHEMA = """
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS nodes (
    slug TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    area TEXT NOT NULL,
    track TEXT NOT NULL DEFAULT 'general',
    display_order INTEGER NOT NULL DEFAULT 1000,
    status TEXT NOT NULL,
    visibility TEXT NOT NULL,
    summary TEXT NOT NULL,
    body TEXT NOT NULL,
    path TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    revision INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tags (
    name TEXT PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS node_tags (
    node_slug TEXT NOT NULL REFERENCES nodes(slug) ON DELETE CASCADE,
    tag_name TEXT NOT NULL REFERENCES tags(name) ON DELETE CASCADE,
    PRIMARY KEY (node_slug, tag_name)
);

CREATE TABLE IF NOT EXISTS links (
    source_slug TEXT NOT NULL REFERENCES nodes(slug) ON DELETE CASCADE,
    target_slug TEXT NOT NULL,
    kind TEXT NOT NULL,
    PRIMARY KEY (source_slug, target_slug, kind)
);

CREATE TABLE IF NOT EXISTS sources (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    node_slug TEXT NOT NULL REFERENCES nodes(slug) ON DELETE CASCADE,
    source TEXT NOT NULL,
    source_type TEXT NOT NULL DEFAULT 'url',
    note TEXT NOT NULL DEFAULT ''
);

CREATE VIRTUAL TABLE IF NOT EXISTS node_fts USING fts5(
    slug UNINDEXED,
    title,
    summary,
    body,
    tags
);

CREATE TABLE IF NOT EXISTS quizzes (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    area TEXT NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 1000,
    status TEXT NOT NULL,
    visibility TEXT NOT NULL,
    difficulty TEXT NOT NULL,
    summary TEXT NOT NULL,
    body TEXT NOT NULL,
    path TEXT NOT NULL,
    weight INTEGER NOT NULL DEFAULT 1,
    updated_at TEXT NOT NULL,
    revision INTEGER NOT NULL DEFAULT 0,
    deleted_at TEXT
);

CREATE TABLE IF NOT EXISTS quiz_tags (
    quiz_id TEXT NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    tag_name TEXT NOT NULL REFERENCES tags(name),
    PRIMARY KEY (quiz_id, tag_name)
);

CREATE TABLE IF NOT EXISTS quiz_links (
    quiz_id TEXT NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    node_slug TEXT NOT NULL,
    kind TEXT NOT NULL,
    PRIMARY KEY (quiz_id, node_slug, kind)
);

CREATE TABLE IF NOT EXISTS quiz_sources (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    quiz_id TEXT NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    source TEXT NOT NULL,
    source_type TEXT NOT NULL DEFAULT 'local',
    note TEXT NOT NULL DEFAULT ''
);

CREATE VIRTUAL TABLE IF NOT EXISTS quiz_fts USING fts5(
    id UNINDEXED,
    title,
    summary,
    body,
    tags
);

CREATE TABLE IF NOT EXISTS reader_questions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    client_id TEXT UNIQUE,
    target_type TEXT NOT NULL CHECK(target_type IN ('node', 'quiz')),
    target_id TEXT NOT NULL,
    question TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'open',
    created_at TEXT NOT NULL,
    resolved_at TEXT NOT NULL DEFAULT '',
    resolution_note TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS ai_jobs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    target_type TEXT NOT NULL CHECK(target_type IN ('node', 'quiz')),
    target_id TEXT NOT NULL,
    question_ids TEXT NOT NULL DEFAULT '[]',
    provider TEXT NOT NULL DEFAULT '',
    model TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'queued',
    stage TEXT NOT NULL DEFAULT 'created',
    instruction TEXT NOT NULL DEFAULT '',
    draft_body TEXT NOT NULL DEFAULT '',
    result_json TEXT NOT NULL DEFAULT '',
    error TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    completed_at TEXT NOT NULL DEFAULT '',
    started_at TEXT NOT NULL DEFAULT '',
    retry_of INTEGER,
    attempt INTEGER NOT NULL DEFAULT 1,
    base_body_hash TEXT NOT NULL DEFAULT '',
    error_code TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS ai_job_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL REFERENCES ai_jobs(id) ON DELETE CASCADE,
    level TEXT NOT NULL DEFAULT 'info',
    stage TEXT NOT NULL,
    message TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS graph_cache (
    cache_key TEXT PRIMARY KEY,
    payload_json TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS node_activity (
    node_slug TEXT PRIMARY KEY REFERENCES nodes(slug) ON DELETE CASCADE,
    last_read_at TEXT NOT NULL DEFAULT '',
    read_count INTEGER NOT NULL DEFAULT 0,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS schema_meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS content_files (
    path TEXT PRIMARY KEY,
    target_type TEXT NOT NULL DEFAULT '',
    target_id TEXT NOT NULL DEFAULT '',
    mtime_ns INTEGER NOT NULL DEFAULT 0,
    size_bytes INTEGER NOT NULL DEFAULT 0,
    sha256 TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'indexed',
    last_ingested_at TEXT NOT NULL DEFAULT '',
    error TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS review_queue (
    target_type TEXT NOT NULL CHECK(target_type IN ('node', 'quiz')),
    target_id TEXT NOT NULL,
    due_at TEXT NOT NULL,
    interval_days REAL NOT NULL DEFAULT 0,
    ease_factor REAL NOT NULL DEFAULT 2.5,
    reps INTEGER NOT NULL DEFAULT 0,
    lapses INTEGER NOT NULL DEFAULT 0,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (target_type, target_id)
);

CREATE TABLE IF NOT EXISTS quiz_attempts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    client_attempt_id TEXT UNIQUE,
    quiz_id TEXT NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    grade TEXT NOT NULL CHECK(grade IN ('again', 'hard', 'good', 'easy')),
    answered_at TEXT NOT NULL,
    elapsed_ms INTEGER NOT NULL DEFAULT 0,
    note TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS capture_slips (
    id TEXT PRIMARY KEY,
    body TEXT NOT NULL,
    type TEXT NOT NULL,
    topic_hint TEXT NOT NULL DEFAULT '',
    source_label TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'inbox',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    revision INTEGER NOT NULL DEFAULT 0,
    deleted_at TEXT
);

CREATE TABLE IF NOT EXISTS sync_changes (
    seq INTEGER PRIMARY KEY AUTOINCREMENT,
    entity_type TEXT NOT NULL CHECK(entity_type IN ('node', 'quiz', 'capture_slip', 'reader_question', 'review_attempt', 'bite_card')),
    entity_id TEXT NOT NULL,
    revision INTEGER,
    content_hash TEXT,
    tombstone INTEGER NOT NULL DEFAULT 0,
    changed_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS bite_cards (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_type TEXT NOT NULL CHECK(source_type IN ('node', 'quiz')),
    source_id TEXT NOT NULL,
    title TEXT NOT NULL,
    area TEXT NOT NULL DEFAULT '',
    difficulty TEXT NOT NULL DEFAULT '',
    question_type TEXT NOT NULL DEFAULT 'blank' CHECK(question_type IN ('blank', 'multiple_choice')),
    prompt TEXT NOT NULL,
    answer TEXT NOT NULL,
    options_json TEXT NOT NULL DEFAULT '[]',
    hint TEXT NOT NULL DEFAULT '',
    explanation_json TEXT NOT NULL DEFAULT '[]',
    status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active', 'archive')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_reader_questions_status_created
ON reader_questions(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_jobs_status_updated
ON ai_jobs(status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_job_events_job_id
ON ai_job_events(job_id, id);

CREATE INDEX IF NOT EXISTS idx_review_queue_due
ON review_queue(due_at, target_type, target_id);

CREATE INDEX IF NOT EXISTS idx_quiz_attempts_quiz_answered
ON quiz_attempts(quiz_id, answered_at DESC);

CREATE INDEX IF NOT EXISTS idx_bite_cards_status_updated
ON bite_cards(status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_sync_changes_entity
ON sync_changes(entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_sync_changes_seq
ON sync_changes(seq);
"""


def connect(db_path: Path) -> sqlite3.Connection:
    db_path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def initialize(conn: sqlite3.Connection) -> None:
    conn.executescript(SCHEMA)
    _ensure_sync_changes_accepts_bite_card(conn)
    existing_columns = {
        row["name"] for row in conn.execute("PRAGMA table_info(nodes)").fetchall()
    }
    if "track" not in existing_columns:
        conn.execute("ALTER TABLE nodes ADD COLUMN track TEXT NOT NULL DEFAULT 'general'")
    if "display_order" not in existing_columns:
        conn.execute("ALTER TABLE nodes ADD COLUMN display_order INTEGER NOT NULL DEFAULT 1000")

    quiz_columns = {
        row["name"] for row in conn.execute("PRAGMA table_info(quizzes)").fetchall()
    }
    if "display_order" not in quiz_columns:
        conn.execute("ALTER TABLE quizzes ADD COLUMN display_order INTEGER NOT NULL DEFAULT 1000")
    if "revision" not in quiz_columns:
        conn.execute("ALTER TABLE quizzes ADD COLUMN revision INTEGER NOT NULL DEFAULT 0")
    if "deleted_at" not in quiz_columns:
        conn.execute("ALTER TABLE quizzes ADD COLUMN deleted_at TEXT")

    if "revision" not in existing_columns:
        conn.execute("ALTER TABLE nodes ADD COLUMN revision INTEGER NOT NULL DEFAULT 0")

    attempt_columns = {
        row["name"] for row in conn.execute("PRAGMA table_info(quiz_attempts)").fetchall()
    }
    if "client_attempt_id" not in attempt_columns:
        conn.execute("ALTER TABLE quiz_attempts ADD COLUMN client_attempt_id TEXT")
        conn.execute(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_quiz_attempts_client_id "
            "ON quiz_attempts(client_attempt_id) WHERE client_attempt_id IS NOT NULL"
        )

    question_columns = {
        row["name"] for row in conn.execute("PRAGMA table_info(reader_questions)").fetchall()
    }
    if "client_id" not in question_columns:
        conn.execute("ALTER TABLE reader_questions ADD COLUMN client_id TEXT")
        conn.execute(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_reader_questions_client_id "
            "ON reader_questions(client_id) WHERE client_id IS NOT NULL"
        )

    bite_columns = {
        row["name"] for row in conn.execute("PRAGMA table_info(bite_cards)").fetchall()
    }
    if "question_type" not in bite_columns:
        conn.execute("ALTER TABLE bite_cards ADD COLUMN question_type TEXT NOT NULL DEFAULT 'blank'")
    if "options_json" not in bite_columns:
        conn.execute("ALTER TABLE bite_cards ADD COLUMN options_json TEXT NOT NULL DEFAULT '[]'")

    ai_job_columns = {
        row["name"] for row in conn.execute("PRAGMA table_info(ai_jobs)").fetchall()
    }
    migrations = {
        "started_at": "ALTER TABLE ai_jobs ADD COLUMN started_at TEXT NOT NULL DEFAULT ''",
        "retry_of": "ALTER TABLE ai_jobs ADD COLUMN retry_of INTEGER",
        "attempt": "ALTER TABLE ai_jobs ADD COLUMN attempt INTEGER NOT NULL DEFAULT 1",
        "base_body_hash": "ALTER TABLE ai_jobs ADD COLUMN base_body_hash TEXT NOT NULL DEFAULT ''",
        "error_code": "ALTER TABLE ai_jobs ADD COLUMN error_code TEXT NOT NULL DEFAULT ''",
    }
    for column, statement in migrations.items():
        if column not in ai_job_columns:
            conn.execute(statement)

    now = datetime.now(timezone.utc).isoformat()
    for key, value in {
        "schema_version": SCHEMA_VERSION,
        "package_format_version": PACKAGE_FORMAT_VERSION,
    }.items():
        conn.execute(
            """
            INSERT INTO schema_meta (key, value, updated_at)
            VALUES (?, ?, ?)
            ON CONFLICT(key) DO UPDATE SET
                value = excluded.value,
                updated_at = excluded.updated_at
            """,
            (key, value, now),
        )
    conn.commit()


def _ensure_sync_changes_accepts_bite_card(conn: sqlite3.Connection) -> None:
    row = conn.execute(
        "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'sync_changes'"
    ).fetchone()
    table_sql = row["sql"] if row else ""
    if "bite_card" in table_sql:
        return
    conn.execute("DROP INDEX IF EXISTS idx_sync_changes_entity")
    conn.execute("DROP INDEX IF EXISTS idx_sync_changes_seq")
    conn.execute("ALTER TABLE sync_changes RENAME TO sync_changes_legacy")
    conn.execute(
        """
        CREATE TABLE sync_changes (
            seq INTEGER PRIMARY KEY AUTOINCREMENT,
            entity_type TEXT NOT NULL CHECK(entity_type IN ('node', 'quiz', 'capture_slip', 'reader_question', 'review_attempt', 'bite_card')),
            entity_id TEXT NOT NULL,
            revision INTEGER,
            content_hash TEXT,
            tombstone INTEGER NOT NULL DEFAULT 0,
            changed_at TEXT NOT NULL
        )
        """
    )
    conn.execute(
        """
        INSERT INTO sync_changes (seq, entity_type, entity_id, revision, content_hash, tombstone, changed_at)
        SELECT seq, entity_type, entity_id, revision, content_hash, tombstone, changed_at
        FROM sync_changes_legacy
        """
    )
    conn.execute("DROP TABLE sync_changes_legacy")
    conn.execute(
        "CREATE INDEX IF NOT EXISTS idx_sync_changes_entity ON sync_changes(entity_type, entity_id)"
    )
    conn.execute("CREATE INDEX IF NOT EXISTS idx_sync_changes_seq ON sync_changes(seq)")
