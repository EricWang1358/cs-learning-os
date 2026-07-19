"""Change-envelope helpers for the personal sync protocol (Phase 0).

Every desktop mutation of a syncable entity must append exactly one row to
``sync_changes`` inside the same SQLite transaction as the mutation itself.
The manifest endpoint (Phase 2) serves deltas by reading rows with
``seq > cursor``; clients treat ``seq`` as an opaque, strictly increasing
change cursor.
"""

from __future__ import annotations

import hashlib
import sqlite3
import uuid
from datetime import datetime, timezone

ENTITY_NODE = "node"
ENTITY_QUIZ = "quiz"
ENTITY_CAPTURE_SLIP = "capture_slip"
ENTITY_READER_QUESTION = "reader_question"
ENTITY_REVIEW_ATTEMPT = "review_attempt"
ENTITY_BITE_CARD = "bite_card"


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def new_client_id() -> str:
    """Client-stable identifier shared with mobile uploads (UUID hex)."""
    return uuid.uuid4().hex


def content_hash(text: str) -> str:
    return hashlib.sha256(text.strip().encode("utf-8")).hexdigest()


def record_change(
    conn: sqlite3.Connection,
    entity_type: str,
    entity_id: str,
    revision: int | None = None,
    hash_value: str | None = None,
    tombstone: bool = False,
) -> None:
    conn.execute(
        """
        INSERT INTO sync_changes (entity_type, entity_id, revision, content_hash, tombstone, changed_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        (entity_type, entity_id, revision, hash_value, 1 if tombstone else 0, utc_now()),
    )


def bump_revision_and_log(
    conn: sqlite3.Connection,
    table: str,
    id_column: str,
    entity_type: str,
    entity_id: str,
    body_text: str,
    trashed: bool = False,
) -> int:
    """Increment the entity revision and append one sync_changes row.

    Call inside the same transaction as the entity mutation, after the
    mutation. ``trashed`` marks soft-delete (trash visibility) changes.
    Returns the new revision.
    """
    row = conn.execute(
        f"SELECT revision FROM {table} WHERE {id_column} = ?",
        (entity_id,),
    ).fetchone()
    revision = (int(row["revision"]) if row and row["revision"] is not None else 0) + 1
    conn.execute(
        f"UPDATE {table} SET revision = ? WHERE {id_column} = ?",
        (revision, entity_id),
    )
    record_change(
        conn,
        entity_type,
        entity_id,
        revision,
        content_hash(body_text),
        tombstone=trashed,
    )
    return revision


def log_permanent_delete(
    conn: sqlite3.Connection,
    entity_type: str,
    entity_id: str,
    last_revision: int | None,
) -> None:
    """Append a tombstone row for an entity whose row no longer exists."""
    revision = (last_revision or 0) + 1
    record_change(conn, entity_type, entity_id, revision, None, tombstone=True)
