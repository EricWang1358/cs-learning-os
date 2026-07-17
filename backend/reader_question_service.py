from __future__ import annotations

import sqlite3
from datetime import datetime, timezone

from fastapi import HTTPException

try:
    from .sync_envelope import (
        ENTITY_READER_QUESTION,
        log_permanent_delete,
        new_client_id,
        record_change,
    )
except ImportError:  # pragma: no cover - script execution
    from sync_envelope import (
        ENTITY_READER_QUESTION,
        log_permanent_delete,
        new_client_id,
        record_change,
    )


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def list_reader_questions(
    conn: sqlite3.Connection,
    target_type: str | None = None,
    target_id: str | None = None,
    status: str = "open",
) -> list[sqlite3.Row]:
    query = "SELECT * FROM reader_questions WHERE 1 = 1"
    params: list[str | int] = []
    if target_type:
        if target_type not in {"node", "quiz"}:
            raise HTTPException(status_code=400, detail="target_type must be node or quiz")
        query += " AND target_type = ?"
        params.append(target_type)
    if target_id:
        query += " AND target_id = ?"
        params.append(target_id)
    if status == "active":
        query += " AND status IN ('open', 'queued', 'solving', 'draft_ready', 'failed')"
    elif status:
        query += " AND status = ?"
        params.append(status)
    query += " ORDER BY created_at DESC, id DESC"
    return conn.execute(query, params).fetchall()


def ensure_target_exists(conn: sqlite3.Connection, target_type: str, target_id: str) -> None:
    if target_type == "node":
        exists = conn.execute("SELECT 1 FROM nodes WHERE slug = ?", (target_id,)).fetchone()
    elif target_type == "quiz":
        exists = conn.execute("SELECT 1 FROM quizzes WHERE id = ?", (target_id,)).fetchone()
    else:
        raise HTTPException(status_code=400, detail="target_type must be node or quiz")
    if not exists:
        raise HTTPException(status_code=404, detail="target not found")


def create_reader_question(
    conn: sqlite3.Connection,
    target_type: str,
    target_id: str,
    question: str,
    client_id: str | None = None,
) -> sqlite3.Row:
    question = question.strip()
    if not question:
        raise HTTPException(status_code=400, detail="question cannot be empty")
    ensure_target_exists(conn, target_type, target_id)
    now = utc_now()
    client_id = client_id or new_client_id()
    cursor = conn.execute(
        """
        INSERT INTO reader_questions (client_id, target_type, target_id, question, status, created_at)
        VALUES (?, ?, ?, ?, 'open', ?)
        """,
        (client_id, target_type, target_id, question, now),
    )
    record_change(conn, ENTITY_READER_QUESTION, client_id)
    conn.commit()
    return conn.execute("SELECT * FROM reader_questions WHERE id = ?", (cursor.lastrowid,)).fetchone()


def get_reader_question_or_404(conn: sqlite3.Connection, question_id: int) -> sqlite3.Row:
    row = conn.execute("SELECT * FROM reader_questions WHERE id = ?", (question_id,)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="reader question not found")
    return row


def set_reader_question_status(
    conn: sqlite3.Connection,
    question_id: int,
    status: str,
    resolution_note: str,
) -> sqlite3.Row:
    existing = get_reader_question_or_404(conn, question_id)
    now = utc_now()
    conn.execute(
        """
        UPDATE reader_questions
        SET status = ?,
            resolved_at = ?,
            resolution_note = ?
        WHERE id = ?
        """,
        (status, now, resolution_note, question_id),
    )
    record_change(conn, ENTITY_READER_QUESTION, existing["client_id"] or f"db-{question_id}")
    conn.commit()
    return get_reader_question_or_404(conn, question_id)


def delete_reader_question(conn: sqlite3.Connection, question_id: int) -> None:
    existing = get_reader_question_or_404(conn, question_id)
    conn.execute("DELETE FROM reader_questions WHERE id = ?", (question_id,))
    log_permanent_delete(conn, ENTITY_READER_QUESTION, existing["client_id"] or f"db-{question_id}", None)
    conn.commit()
