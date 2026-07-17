from __future__ import annotations

import sqlite3
from datetime import datetime, timedelta, timezone

from fastapi import HTTPException

try:
    from .sync_envelope import ENTITY_REVIEW_ATTEMPT, new_client_id, record_change
except ImportError:  # pragma: no cover - script execution
    from sync_envelope import ENTITY_REVIEW_ATTEMPT, new_client_id, record_change


GRADE_INTERVALS = {
    "again": 0.01,
    "hard": 1.0,
    "good": 2.5,
    "easy": 4.0,
}


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def parse_time(value: str) -> datetime:
    if not value:
        return datetime.now(timezone.utc)
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def get_quiz_or_404(conn: sqlite3.Connection, quiz_id: str) -> sqlite3.Row:
    row = conn.execute("SELECT * FROM quizzes WHERE id = ?", (quiz_id,)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="Quiz not found")
    return row


def next_review_state(existing: sqlite3.Row | None, grade: str, answered_at: str) -> dict:
    if grade not in GRADE_INTERVALS:
        raise HTTPException(status_code=400, detail="grade must be again, hard, good, or easy")

    previous_interval = float(existing["interval_days"]) if existing else 0.0
    ease = float(existing["ease_factor"]) if existing else 2.5
    reps = int(existing["reps"]) if existing else 0
    lapses = int(existing["lapses"]) if existing else 0

    if grade == "again":
        interval = GRADE_INTERVALS[grade]
        ease = max(1.3, ease - 0.2)
        reps = 0
        lapses += 1
    elif grade == "hard":
        interval = max(1.0, previous_interval * 1.2 if previous_interval else GRADE_INTERVALS[grade])
        ease = max(1.3, ease - 0.15)
        reps += 1
    elif grade == "good":
        interval = max(GRADE_INTERVALS[grade], previous_interval * ease if previous_interval else GRADE_INTERVALS[grade])
        reps += 1
    else:
        ease = min(3.0, ease + 0.15)
        interval = max(GRADE_INTERVALS[grade], previous_interval * ease * 1.3 if previous_interval else GRADE_INTERVALS[grade])
        reps += 1

    due_at = parse_time(answered_at) + timedelta(days=interval)
    return {
        "due_at": due_at.isoformat(),
        "interval_days": interval,
        "ease_factor": ease,
        "reps": reps,
        "lapses": lapses,
    }


def record_quiz_attempt(
    conn: sqlite3.Connection,
    quiz_id: str,
    grade: str,
    elapsed_ms: int = 0,
    note: str = "",
    client_attempt_id: str | None = None,
    answered_at: str | None = None,
) -> dict:
    get_quiz_or_404(conn, quiz_id)
    if answered_at:
        # Preserve the client timestamp: scheduling order across devices
        # depends on when the attempt actually happened.
        parse_time(answered_at)
        answered_at = answered_at.replace("Z", "+00:00")
    else:
        answered_at = utc_now()
    client_attempt_id = client_attempt_id or new_client_id()
    existing = conn.execute(
        "SELECT * FROM review_queue WHERE target_type = 'quiz' AND target_id = ?",
        (quiz_id,),
    ).fetchone()
    review = next_review_state(existing, grade, answered_at)
    cursor = conn.execute(
        """
        INSERT INTO quiz_attempts (client_attempt_id, quiz_id, grade, answered_at, elapsed_ms, note)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        (client_attempt_id, quiz_id, grade, answered_at, max(0, elapsed_ms), note.strip()),
    )
    record_change(conn, ENTITY_REVIEW_ATTEMPT, client_attempt_id)
    conn.execute(
        """
        INSERT INTO review_queue (
            target_type, target_id, due_at, interval_days, ease_factor, reps, lapses, updated_at
        ) VALUES ('quiz', ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(target_type, target_id) DO UPDATE SET
            due_at = excluded.due_at,
            interval_days = excluded.interval_days,
            ease_factor = excluded.ease_factor,
            reps = excluded.reps,
            lapses = excluded.lapses,
            updated_at = excluded.updated_at
        """,
        (
            quiz_id,
            review["due_at"],
            review["interval_days"],
            review["ease_factor"],
            review["reps"],
            review["lapses"],
            answered_at,
        ),
    )
    conn.commit()
    queue_row = conn.execute(
        "SELECT * FROM review_queue WHERE target_type = 'quiz' AND target_id = ?",
        (quiz_id,),
    ).fetchone()
    return {"attempt_id": cursor.lastrowid, "client_attempt_id": client_attempt_id, "review": dict(queue_row)}


def due_reviews(conn: sqlite3.Connection, limit: int = 50) -> list[dict]:
    now = utc_now()
    rows = conn.execute(
        """
        SELECT rq.*, q.title, q.area, q.difficulty, q.summary
        FROM review_queue rq
        LEFT JOIN quizzes q ON rq.target_type = 'quiz' AND rq.target_id = q.id
        WHERE rq.due_at <= ?
        ORDER BY rq.due_at, rq.target_type, rq.target_id
        LIMIT ?
        """,
        (now, max(1, min(limit, 200))),
    ).fetchall()
    reviews = [dict(row) for row in rows]
    remaining = max(0, max(1, min(limit, 200)) - len(reviews))
    if remaining:
        fresh_rows = conn.execute(
            """
            SELECT
                'quiz' AS target_type,
                q.id AS target_id,
                '' AS due_at,
                0.0 AS interval_days,
                2.5 AS ease_factor,
                0 AS reps,
                0 AS lapses,
                '' AS updated_at,
                q.title,
                q.area,
                q.difficulty,
                q.summary
            FROM quizzes q
            LEFT JOIN review_queue rq ON rq.target_type = 'quiz' AND rq.target_id = q.id
            WHERE rq.target_id IS NULL
              AND q.visibility NOT IN ('archive', 'trash')
            ORDER BY q.display_order, q.title
            LIMIT ?
            """,
            (remaining,),
        ).fetchall()
        reviews.extend(dict(row) for row in fresh_rows)
    return reviews
