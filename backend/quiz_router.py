from __future__ import annotations

import sqlite3
from pathlib import Path
from typing import Callable, Optional

from fastapi import APIRouter, HTTPException, Query

try:
    from . import content_write_service
    from . import learning_service
    from . import search_service
    from .api_models import BodyUpdate, QuizAttemptCreate
    from .api_serializers import row_to_quiz
    from .content_write_service import body_hash
except ImportError:
    import content_write_service
    import learning_service
    import search_service
    from api_models import BodyUpdate, QuizAttemptCreate
    from api_serializers import row_to_quiz
    from content_write_service import body_hash


ConnectionFactory = Callable[[], sqlite3.Connection]


def create_quiz_router(get_conn: ConnectionFactory, content_root: Path) -> APIRouter:
    router = APIRouter(prefix="/api", tags=["quizzes"])

    def get_quiz_payload(quiz_id: str) -> dict:
        with get_conn() as conn:
            row = conn.execute("SELECT * FROM quizzes WHERE id = ?", (quiz_id,)).fetchone()
            if not row:
                raise HTTPException(status_code=404, detail="Quiz not found")
            tags = [
                item["tag_name"]
                for item in conn.execute(
                    "SELECT tag_name FROM quiz_tags WHERE quiz_id = ? ORDER BY tag_name",
                    (quiz_id,),
                ).fetchall()
            ]
            linked_nodes = [
                {"slug": item["node_slug"], "kind": item["kind"], "title": item["title"]}
                for item in conn.execute(
                    """
                    SELECT ql.node_slug, ql.kind, COALESCE(n.title, ql.node_slug) AS title
                    FROM quiz_links ql
                    LEFT JOIN nodes n ON n.slug = ql.node_slug
                    WHERE ql.quiz_id = ?
                    ORDER BY ql.kind, ql.node_slug
                    """,
                    (quiz_id,),
                ).fetchall()
            ]
            sources = [
                {
                    "source": item["source"],
                    "source_type": item["source_type"],
                    "note": item["note"],
                }
                for item in conn.execute(
                    "SELECT source, source_type, note FROM quiz_sources WHERE quiz_id = ? ORDER BY id",
                    (quiz_id,),
                ).fetchall()
            ]
            open_question_count = conn.execute(
                """
                SELECT COUNT(*) AS count
                FROM reader_questions
                WHERE target_type = 'quiz'
                  AND target_id = ?
                  AND status = 'open'
                """,
                (quiz_id,),
            ).fetchone()["count"]

        quiz = row_to_quiz(row)
        quiz["body"] = row["body"]
        quiz["body_hash"] = body_hash(row["body"])
        quiz["tags"] = tags
        quiz["linked_nodes"] = linked_nodes
        quiz["sources"] = sources
        quiz["open_question_count"] = open_question_count
        return {"quiz": quiz}

    @router.get("/review/due")
    def due_reviews(limit: int = Query(default=50, ge=1, le=200)) -> dict:
        with get_conn() as conn:
            return {"reviews": learning_service.due_reviews(conn, limit=limit)}

    @router.post("/quizzes/{quiz_id}/attempts")
    def record_quiz_attempt(quiz_id: str, payload: QuizAttemptCreate) -> dict:
        with get_conn() as conn:
            return learning_service.record_quiz_attempt(
                conn,
                quiz_id,
                payload.grade,
                elapsed_ms=payload.elapsed_ms,
                note=payload.note,
            )

    @router.get("/quizzes")
    def list_quizzes(
        area: Optional[str] = None,
        visibility: Optional[str] = None,
        sort: str = Query(default="order"),
    ) -> dict:
        with get_conn() as conn:
            rows = search_service.list_quizzes(conn, area=area, visibility=visibility, sort=sort)
        return {"quizzes": [row_to_quiz(row) for row in rows]}

    @router.get("/quizzes/{quiz_id}")
    def get_quiz(quiz_id: str) -> dict:
        return get_quiz_payload(quiz_id)

    @router.put("/quizzes/{quiz_id}/body")
    def update_quiz_body(quiz_id: str, payload: BodyUpdate) -> dict:
        with get_conn() as conn:
            content_write_service.update_target_body(
                conn,
                content_root,
                "quiz",
                quiz_id,
                payload.body,
                payload.base_body_hash,
            )

        return get_quiz_payload(quiz_id)

    @router.get("/quiz-search")
    def search_quizzes(
        q: str = Query(default="", min_length=0),
        sort: str = Query(default="relevance"),
        area: Optional[str] = None,
        visibility: Optional[str] = None,
        difficulty: Optional[str] = None,
    ) -> dict:
        with get_conn() as conn:
            rows = search_service.search_quizzes(
                conn,
                q,
                sort=sort,
                area=area,
                visibility=visibility,
                difficulty=difficulty,
            )

        return {"quizzes": [row_to_quiz(row) for row in rows]}

    return router
