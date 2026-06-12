from __future__ import annotations

import hashlib
import json
import re
import sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path

from fastapi import HTTPException


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def strip_utf8_bom(text: str) -> str:
    return text[1:] if text.startswith("\ufeff") else text


def split_markdown_frontmatter(text: str) -> tuple[str, str]:
    text = strip_utf8_bom(text)
    match = re.match(r"^(---\s*\n.*?\n---\s*\n)(.*)$", text, flags=re.DOTALL)
    if not match:
        return "", text
    return match.group(1), match.group(2)


def body_hash(body: str) -> str:
    return hashlib.sha256(body.strip().encode("utf-8")).hexdigest()


def normalized_body(body: str) -> str:
    value = body.strip()
    if not value:
        raise HTTPException(status_code=400, detail="body cannot be empty")
    return value


def write_markdown_body(path: Path, body: str) -> None:
    text = path.read_text(encoding="utf-8")
    frontmatter, _ = split_markdown_frontmatter(text)
    path.write_text(frontmatter + normalized_body(body) + "\n", encoding="utf-8")


@contextmanager
def restore_file_on_failure(path: Path):
    existed = path.exists()
    original = path.read_text(encoding="utf-8") if existed else ""
    try:
        yield
    except Exception:
        if existed:
            path.write_text(original, encoding="utf-8")
        elif path.exists():
            path.unlink()
        raise


def update_node_body_in_conn(conn: sqlite3.Connection, row: sqlite3.Row, body: str) -> None:
    now = utc_now()
    slug = row["slug"]
    tags = [
        item["tag_name"]
        for item in conn.execute(
            "SELECT tag_name FROM node_tags WHERE node_slug = ? ORDER BY tag_name",
            (slug,),
        ).fetchall()
    ]
    conn.execute("UPDATE nodes SET body = ?, updated_at = ? WHERE slug = ?", (body, now, slug))
    conn.execute("DELETE FROM node_fts WHERE slug = ?", (slug,))
    conn.execute("DELETE FROM graph_cache")
    conn.execute(
        """
        INSERT INTO node_fts (slug, title, summary, body, tags)
        VALUES (?, ?, ?, ?, ?)
        """,
        (slug, row["title"], row["summary"], body, " ".join(tags)),
    )


def update_quiz_body_in_conn(conn: sqlite3.Connection, row: sqlite3.Row, body: str) -> None:
    now = utc_now()
    quiz_id = row["id"]
    tags = [
        item["tag_name"]
        for item in conn.execute(
            "SELECT tag_name FROM quiz_tags WHERE quiz_id = ? ORDER BY tag_name",
            (quiz_id,),
        ).fetchall()
    ]
    conn.execute("UPDATE quizzes SET body = ?, updated_at = ? WHERE id = ?", (body, now, quiz_id))
    conn.execute("DELETE FROM quiz_fts WHERE id = ?", (quiz_id,))
    conn.execute("DELETE FROM graph_cache")
    conn.execute(
        """
        INSERT INTO quiz_fts (id, title, summary, body, tags)
        VALUES (?, ?, ?, ?, ?)
        """,
        (quiz_id, row["title"], row["summary"], body, " ".join(tags)),
    )


def get_target_or_404(conn: sqlite3.Connection, target_type: str, target_id: str) -> sqlite3.Row:
    if target_type == "node":
        row = conn.execute("SELECT * FROM nodes WHERE slug = ?", (target_id,)).fetchone()
        detail = "Node not found"
    elif target_type == "quiz":
        row = conn.execute("SELECT * FROM quizzes WHERE id = ?", (target_id,)).fetchone()
        detail = "Quiz not found"
    else:
        raise HTTPException(status_code=400, detail="target_type must be node or quiz")
    if not row:
        raise HTTPException(status_code=404, detail=detail)
    return row


def source_file_or_404(content_root: Path, row: sqlite3.Row, label: str) -> Path:
    content_path = content_root / row["path"]
    if not content_path.is_file():
        raise HTTPException(status_code=404, detail=f"{label} source file not found")
    return content_path


def write_target_body_in_conn(
    conn: sqlite3.Connection,
    content_root: Path,
    target_type: str,
    target_row: sqlite3.Row,
    body: str,
    expected_body_hash: str = "",
) -> None:
    body = normalized_body(body)
    if expected_body_hash and body_hash(target_row["body"]) != expected_body_hash:
        raise HTTPException(status_code=409, detail="Target Markdown changed after this draft was created")

    label = "Node" if target_type == "node" else "Quiz"
    content_path = source_file_or_404(content_root, target_row, label)
    write_markdown_body(content_path, body)
    if target_type == "node":
        update_node_body_in_conn(conn, target_row, body)
    else:
        update_quiz_body_in_conn(conn, target_row, body)


def update_target_body(
    conn: sqlite3.Connection,
    content_root: Path,
    target_type: str,
    target_id: str,
    body: str,
    expected_body_hash: str = "",
) -> sqlite3.Row:
    target_row = get_target_or_404(conn, target_type, target_id)
    content_path = source_file_or_404(content_root, target_row, "Node" if target_type == "node" else "Quiz")
    with restore_file_on_failure(content_path):
        write_target_body_in_conn(conn, content_root, target_type, target_row, body, expected_body_hash)
        conn.commit()
    return get_target_or_404(conn, target_type, target_id)


def apply_ai_job_body(
    conn: sqlite3.Connection,
    content_root: Path,
    job_row: sqlite3.Row,
    body: str,
) -> sqlite3.Row:
    if job_row["status"] != "draft_ready":
        raise HTTPException(status_code=400, detail="AI job is not draft_ready")

    target_row = get_target_or_404(conn, job_row["target_type"], job_row["target_id"])
    content_path = source_file_or_404(
        content_root,
        target_row,
        "Node" if job_row["target_type"] == "node" else "Quiz",
    )
    now = utc_now()
    with restore_file_on_failure(content_path):
        write_target_body_in_conn(
            conn,
            content_root,
            job_row["target_type"],
            target_row,
            body,
            job_row["base_body_hash"],
        )
        question_ids = json.loads(job_row["question_ids"] or "[]")
        if question_ids:
            placeholders = ",".join("?" for _ in question_ids)
            conn.execute(
                f"""
                UPDATE reader_questions
                SET status = 'resolved',
                    resolved_at = ?,
                    resolution_note = ?
                WHERE id IN ({placeholders})
                """,
                [now, "Resolved by applied AI job", *question_ids],
            )
        conn.execute(
            """
            UPDATE ai_jobs
            SET status = 'applied',
                stage = 'applied',
                updated_at = ?,
                completed_at = ?
            WHERE id = ?
            """,
            (now, now, job_row["id"]),
        )
        conn.commit()
    updated = conn.execute("SELECT * FROM ai_jobs WHERE id = ?", (job_row["id"],)).fetchone()
    if not updated:
        raise HTTPException(status_code=500, detail="AI job apply failed")
    return updated
