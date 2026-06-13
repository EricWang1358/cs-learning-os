from __future__ import annotations

import sqlite3
from typing import Callable, Optional

from fastapi import APIRouter

try:
    from . import reader_question_service
    from .api_models import ReaderQuestionCreate, ReaderQuestionResolve
    from .api_serializers import row_to_reader_question
except ImportError:
    import reader_question_service
    from api_models import ReaderQuestionCreate, ReaderQuestionResolve
    from api_serializers import row_to_reader_question


ConnectionFactory = Callable[[], sqlite3.Connection]


def create_reader_question_router(get_conn: ConnectionFactory) -> APIRouter:
    router = APIRouter(prefix="/api", tags=["reader-questions"])

    @router.get("/reader-questions")
    def list_reader_questions(
        target_type: Optional[str] = None,
        target_id: Optional[str] = None,
        status: str = "open",
    ) -> dict:
        with get_conn() as conn:
            rows = reader_question_service.list_reader_questions(conn, target_type, target_id, status)
        return {"questions": [row_to_reader_question(row) for row in rows]}

    @router.post("/reader-questions")
    def create_reader_question(payload: ReaderQuestionCreate) -> dict:
        with get_conn() as conn:
            row = reader_question_service.create_reader_question(
                conn,
                payload.target_type,
                payload.target_id,
                payload.question,
            )

        return {"question": row_to_reader_question(row)}

    @router.post("/reader-questions/{question_id}/resolve")
    def resolve_reader_question(question_id: int, payload: ReaderQuestionResolve) -> dict:
        with get_conn() as conn:
            updated = reader_question_service.set_reader_question_status(
                conn,
                question_id,
                "resolved",
                payload.resolution_note,
            )

        return {"question": row_to_reader_question(updated)}

    @router.post("/reader-questions/{question_id}/dismiss")
    def dismiss_reader_question(question_id: int, payload: ReaderQuestionResolve) -> dict:
        with get_conn() as conn:
            updated = reader_question_service.set_reader_question_status(
                conn,
                question_id,
                "dismissed",
                payload.resolution_note,
            )

        return {"question": row_to_reader_question(updated)}

    @router.delete("/reader-questions/{question_id}")
    def delete_reader_question(question_id: int) -> dict:
        with get_conn() as conn:
            reader_question_service.delete_reader_question(conn, question_id)
        return {"ok": True}

    return router
