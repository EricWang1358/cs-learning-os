from __future__ import annotations

import json
from datetime import datetime, timezone

from fastapi import HTTPException


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def summarize_ai_error(error: str | None) -> str:
    if not error:
        return ""

    text = error.strip()
    lower_text = text.lower()
    if "high demand" in lower_text:
        return (
            "Codex CLI failed because the model provider reported high demand. "
            "The job is safe to retry; it did not change your Markdown."
        )
    if "timed out" in lower_text:
        return "Codex CLI timed out before returning a draft. The job is safe to retry."
    if "openai_api_key" in lower_text or "api key" in lower_text:
        return "AI provider is not configured with the required API key."
    if "returned non-json" in lower_text:
        return "Codex CLI returned output that was not valid JSON, so the draft could not be parsed."

    for marker in ["\n--------\nuser", "\nuser\n", "Original Markdown body:"]:
        marker_index = text.find(marker)
        if marker_index != -1:
            text = text[:marker_index].strip()

    lines = []
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        if "WARN codex_core" in stripped or "ERROR: Reconnecting" in stripped:
            continue
        lines.append(stripped)
        if len(" ".join(lines)) > 700:
            break

    summary = " ".join(lines).strip()
    return summary[:900] + ("..." if len(summary) > 900 else "")


def classify_ai_error(error: str | None) -> str:
    text = (error or "").lower()
    if "high demand" in text:
        return "high_demand"
    if "timed out" in text:
        return "timeout"
    if "non-json" in text or "not valid json" in text:
        return "non_json"
    if "api key" in text or "unauthorized" in text or "not configured" in text:
        return "auth_or_config"
    if "cancel" in text:
        return "cancelled"
    return "unknown"


def row_to_ai_job(row) -> dict:
    result = json.loads(row["result_json"] or "{}")
    error = row["error"] or ""
    return {
        "id": row["id"],
        "target_type": row["target_type"],
        "target_id": row["target_id"],
        "question_ids": json.loads(row["question_ids"] or "[]"),
        "provider": row["provider"],
        "model": row["model"],
        "status": row["status"],
        "stage": row["stage"],
        "instruction": row["instruction"],
        "error": error,
        "error_summary": summarize_ai_error(error),
        "error_code": row["error_code"],
        "retry_of": row["retry_of"],
        "attempt": row["attempt"],
        "base_body_hash": row["base_body_hash"],
        "created_at": row["created_at"],
        "updated_at": row["updated_at"],
        "completed_at": row["completed_at"],
        "started_at": row["started_at"],
        "revision": result.get("revision"),
    }


def update_ai_job(conn_factory, job_id: int, **fields: object) -> None:
    if not fields:
        return
    fields["updated_at"] = utc_now()
    assignments = ", ".join(f"{name} = ?" for name in fields)
    values = list(fields.values())
    values.append(job_id)
    with conn_factory() as conn:
        conn.execute(f"UPDATE ai_jobs SET {assignments} WHERE id = ?", values)
        conn.commit()


def add_ai_job_event(conn_factory, job_id: int, stage: str, message: str, level: str = "info") -> None:
    with conn_factory() as conn:
        conn.execute(
            """
            INSERT INTO ai_job_events (job_id, level, stage, message, created_at)
            VALUES (?, ?, ?, ?, ?)
            """,
            (job_id, level, stage, message, utc_now()),
        )
        conn.commit()


def get_ai_job_or_404(conn, job_id: int):
    row = conn.execute("SELECT * FROM ai_jobs WHERE id = ?", (job_id,)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="AI job not found")
    return row


def ensure_job_can_write(conn_factory, job_id: int):
    with conn_factory() as conn:
        row = get_ai_job_or_404(conn, job_id)
    if row["status"] not in {"queued", "solving"}:
        raise HTTPException(status_code=409, detail=f"AI job is already {row['status']}")
    return row


def recover_stale_ai_jobs(conn_factory, cutoff_seconds: int) -> None:
    cutoff = datetime.now(timezone.utc).timestamp() - cutoff_seconds
    now = utc_now()
    with conn_factory() as conn:
        rows = conn.execute(
            """
            SELECT id, updated_at
            FROM ai_jobs
            WHERE status IN ('queued', 'solving')
            """
        ).fetchall()
        stale_ids = []
        for row in rows:
            try:
                updated = datetime.fromisoformat(row["updated_at"]).timestamp()
            except ValueError:
                updated = 0
            if updated < cutoff:
                stale_ids.append(row["id"])
        for job_id in stale_ids:
            conn.execute(
                """
                UPDATE ai_jobs
                SET status = 'failed',
                    stage = 'stale_failed',
                    error = ?,
                    error_code = 'stale_worker',
                    updated_at = ?,
                    completed_at = ?
                WHERE id = ?
                """,
                ("AI job was left queued/solving after the worker stopped or the API restarted.", now, now, job_id),
            )
        conn.commit()
    for job_id in stale_ids:
        add_ai_job_event(conn_factory, job_id, "stale_failed", "Marked stale after local worker recovery.", "warning")


def create_ai_job(
    conn,
    target_type: str,
    target_id: str,
    question_ids: list[int],
    question: str,
    instruction: str,
    draft_body: str,
    provider: str,
    model: str,
    base_body_hash: str,
):
    if target_type == "node":
        exists = conn.execute("SELECT 1 FROM nodes WHERE slug = ?", (target_id,)).fetchone()
    else:
        exists = conn.execute("SELECT 1 FROM quizzes WHERE id = ?", (target_id,)).fetchone()
    if not exists:
        raise HTTPException(status_code=404, detail="target not found")

    now = utc_now()
    persisted_question_ids = list(dict.fromkeys(question_ids))
    clean_question = question.strip()
    if clean_question:
        cursor = conn.execute(
            """
            INSERT INTO reader_questions (target_type, target_id, question, status, created_at)
            VALUES (?, ?, ?, 'queued', ?)
            """,
            (target_type, target_id, clean_question, now),
        )
        persisted_question_ids.append(cursor.lastrowid)

    cursor = conn.execute(
        """
        INSERT INTO ai_jobs (
            target_type, target_id, question_ids, provider, model, status, stage,
            instruction, draft_body, created_at, updated_at, base_body_hash
        )
        VALUES (?, ?, ?, ?, ?, 'queued', 'queued', ?, ?, ?, ?, ?)
        """,
        (
            target_type,
            target_id,
            json.dumps(persisted_question_ids),
            provider,
            model,
            instruction,
            draft_body,
            now,
            now,
            base_body_hash,
        ),
    )
    conn.commit()
    return conn.execute("SELECT * FROM ai_jobs WHERE id = ?", (cursor.lastrowid,)).fetchone()


def list_ai_jobs(conn, target_type: str | None = None, target_id: str | None = None, status: str | None = None):
    query = "SELECT * FROM ai_jobs WHERE 1 = 1"
    params: list[str] = []
    if target_type:
        if target_type not in {"node", "quiz"}:
            raise HTTPException(status_code=400, detail="target_type must be node or quiz")
        query += " AND target_type = ?"
        params.append(target_type)
    if target_id:
        query += " AND target_id = ?"
        params.append(target_id)
    if status == "active":
        query += " AND status IN ('queued', 'solving', 'draft_ready', 'failed')"
    elif status:
        query += " AND status = ?"
        params.append(status)
    query += " ORDER BY updated_at DESC, id DESC LIMIT 50"
    return conn.execute(query, params).fetchall()


def list_ai_job_events(conn, job_id: int):
    get_ai_job_or_404(conn, job_id)
    return conn.execute(
        """
        SELECT id, job_id, level, stage, message, created_at
        FROM ai_job_events
        WHERE job_id = ?
        ORDER BY id
        """,
        (job_id,),
    ).fetchall()


def cancel_ai_job(conn, job_id: int):
    now = utc_now()
    row = get_ai_job_or_404(conn, job_id)
    if row["status"] in {"draft_ready", "failed", "cancelled", "applied", "rejected", "retried"}:
        raise HTTPException(status_code=400, detail=f"cannot cancel {row['status']} job")
    conn.execute(
        """
        UPDATE ai_jobs
        SET status = 'cancelled',
            stage = 'cancelled',
            updated_at = ?,
            completed_at = ?
        WHERE id = ?
        """,
        (now, now, job_id),
    )
    conn.commit()
    return conn.execute("SELECT * FROM ai_jobs WHERE id = ?", (job_id,)).fetchone()


def reject_ai_job(conn, job_id: int, reason: str = ""):
    now = utc_now()
    row = get_ai_job_or_404(conn, job_id)
    if row["status"] != "draft_ready":
        raise HTTPException(status_code=400, detail=f"cannot reject {row['status']} job")
    note = reason.strip() or "Draft rejected by human review"
    conn.execute(
        """
        UPDATE ai_jobs
        SET status = 'rejected',
            stage = 'rejected',
            error = ?,
            updated_at = ?,
            completed_at = ?
        WHERE id = ?
        """,
        (note, now, now, job_id),
    )
    conn.commit()
    return conn.execute("SELECT * FROM ai_jobs WHERE id = ?", (job_id,)).fetchone()


def retry_ai_job(conn, job_id: int, provider: str, model: str):
    now = utc_now()
    row = get_ai_job_or_404(conn, job_id)
    if row["status"] != "failed":
        raise HTTPException(status_code=400, detail=f"cannot retry {row['status']} job")

    conn.execute(
        """
        UPDATE ai_jobs
        SET status = 'retried',
            stage = 'retried',
            updated_at = ?,
            completed_at = ?
        WHERE id = ?
        """,
        (now, now, job_id),
    )

    cursor = conn.execute(
        """
        INSERT INTO ai_jobs (
            target_type, target_id, question_ids, provider, model, status, stage,
            instruction, draft_body, created_at, updated_at, retry_of, attempt, base_body_hash
        )
        VALUES (?, ?, ?, ?, ?, 'queued', 'queued', ?, ?, ?, ?, ?, ?, ?)
        """,
        (
            row["target_type"],
            row["target_id"],
            row["question_ids"],
            provider,
            model,
            row["instruction"],
            row["draft_body"],
            now,
            now,
            row["id"],
            int(row["attempt"] or 1) + 1,
            row["base_body_hash"],
        ),
    )
    conn.commit()
    return conn.execute("SELECT * FROM ai_jobs WHERE id = ?", (cursor.lastrowid,)).fetchone()
