from __future__ import annotations

import sqlite3
from pathlib import Path


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
    updated_at TEXT NOT NULL
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
    updated_at TEXT NOT NULL
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

CREATE INDEX IF NOT EXISTS idx_reader_questions_status_created
ON reader_questions(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_jobs_status_updated
ON ai_jobs(status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_job_events_job_id
ON ai_job_events(job_id, id);
"""


def connect(db_path: Path) -> sqlite3.Connection:
    db_path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def initialize(conn: sqlite3.Connection) -> None:
    conn.executescript(SCHEMA)
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
    conn.commit()
