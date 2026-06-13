from __future__ import annotations

import json
import logging
import sqlite3
from pathlib import Path
from typing import Callable, Optional

from fastapi import APIRouter, BackgroundTasks, HTTPException

try:
    from . import ai_job_service
    from . import ai_revision_service
    from . import content_write_service
    from .ai_job_service import (
        add_ai_job_event as service_add_ai_job_event,
        classify_ai_error,
        ensure_job_can_write as service_ensure_job_can_write,
        get_ai_job_or_404,
        row_to_ai_job,
        summarize_ai_error,
        update_ai_job as service_update_ai_job,
        utc_now,
    )
    from .api_models import AiJobApply, AiJobCreate, AiJobReject, AiReviseRequest
    from .api_serializers import row_to_node, row_to_quiz, row_to_reader_question
    from .content_write_service import body_hash
except ImportError:
    import ai_job_service
    import ai_revision_service
    import content_write_service
    from ai_job_service import (
        add_ai_job_event as service_add_ai_job_event,
        classify_ai_error,
        ensure_job_can_write as service_ensure_job_can_write,
        get_ai_job_or_404,
        row_to_ai_job,
        summarize_ai_error,
        update_ai_job as service_update_ai_job,
        utc_now,
    )
    from api_models import AiJobApply, AiJobCreate, AiJobReject, AiReviseRequest
    from api_serializers import row_to_node, row_to_quiz, row_to_reader_question
    from content_write_service import body_hash


ConnectionFactory = Callable[[], sqlite3.Connection]


def create_ai_router(
    get_conn: ConnectionFactory,
    content_root: Path,
    logger: logging.Logger,
    ai_provider_name: Callable[[], str],
    openai_model_name: Callable[[], str],
    codex_model_name: Callable[[], str],
) -> APIRouter:
    router = APIRouter(prefix="/api", tags=["ai"])

    def update_ai_job(job_id: int, **fields: object) -> None:
        service_update_ai_job(get_conn, job_id, **fields)

    def add_ai_job_event(job_id: int, stage: str, message: str, level: str = "info") -> None:
        service_add_ai_job_event(get_conn, job_id, stage, message, level)

    def ensure_job_can_write(job_id: int) -> sqlite3.Row:
        return service_ensure_job_can_write(get_conn, job_id)

    def load_ai_target(
        conn: sqlite3.Connection,
        target_type: str,
        target_id: str,
        question_ids: list[int],
    ) -> tuple[dict, list[dict]]:
        if target_type == "node":
            row = conn.execute("SELECT * FROM nodes WHERE slug = ?", (target_id,)).fetchone()
            if not row:
                raise HTTPException(status_code=404, detail="Node not found")
            tags = [
                item["tag_name"]
                for item in conn.execute(
                    "SELECT tag_name FROM node_tags WHERE node_slug = ? ORDER BY tag_name",
                    (target_id,),
                ).fetchall()
            ]
            links = [
                {"target": item["target_slug"], "kind": item["kind"]}
                for item in conn.execute(
                    "SELECT target_slug, kind FROM links WHERE source_slug = ? ORDER BY kind, target_slug",
                    (target_id,),
                ).fetchall()
            ]
            target = row_to_node(row) | {"body": row["body"], "tags": tags, "links": links}
        else:
            row = conn.execute("SELECT * FROM quizzes WHERE id = ?", (target_id,)).fetchone()
            if not row:
                raise HTTPException(status_code=404, detail="Quiz not found")
            tags = [
                item["tag_name"]
                for item in conn.execute(
                    "SELECT tag_name FROM quiz_tags WHERE quiz_id = ? ORDER BY tag_name",
                    (target_id,),
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
                    (target_id,),
                ).fetchall()
            ]
            target = row_to_quiz(row) | {"body": row["body"], "tags": tags, "linked_nodes": linked_nodes}

        params: list[object] = [target_type, target_id]
        query = """
            SELECT *
            FROM reader_questions
            WHERE target_type = ?
              AND target_id = ?
        """
        if question_ids:
            placeholders = ",".join("?" for _ in question_ids)
            query += f" AND id IN ({placeholders})"
            params.extend(question_ids)
        else:
            query += " AND status IN ('open', 'queued', 'solving', 'draft_ready')"
        query += " ORDER BY created_at DESC, id DESC"
        rows = conn.execute(query, params).fetchall()
        return target, [row_to_reader_question(item) for item in rows]

    def run_ai_job(job_id: int) -> None:
        try:
            add_ai_job_event(job_id, "queued", "AI job picked up by local worker.")
            update_ai_job(job_id, status="solving", stage="context_built", started_at=utc_now())
            with get_conn() as conn:
                row = get_ai_job_or_404(conn, job_id)
                if row["status"] not in {"queued", "solving"}:
                    add_ai_job_event(job_id, row["status"], "AI job stopped before context build.", "warning")
                    return
                question_ids = json.loads(row["question_ids"] or "[]")
                target, reader_questions = load_ai_target(
                    conn,
                    row["target_type"],
                    row["target_id"],
                    question_ids,
                )
                if row["draft_body"].strip():
                    target["body"] = row["draft_body"].strip()
                context_json = ai_revision_service.build_ai_revision_prompt(
                    target,
                    row["target_type"],
                    reader_questions,
                    row["instruction"],
                    row["draft_body"],
                )
            add_ai_job_event(job_id, "context_built", f"Built prompt with {len(reader_questions)} reader question(s).")

            provider = ai_provider_name()
            update_ai_job(job_id, provider=provider, stage="codex_running" if provider == "codex-cli" else "model_running")
            add_ai_job_event(job_id, "codex_running" if provider == "codex-cli" else "model_running", f"Starting {provider}.")
            response = ai_revision_service.run_revision(context_json, reader_questions)

            ensure_job_can_write(job_id)
            revision = response["revision"]
            update_ai_job(
                job_id,
                status="draft_ready",
                stage="draft_ready",
                provider=revision["provider"],
                model=revision["model"],
                result_json=json.dumps(response, ensure_ascii=False),
                completed_at=utc_now(),
            )
            add_ai_job_event(job_id, "draft_ready", "AI draft is ready for human review.")
        except Exception as exc:
            detail = exc.detail if isinstance(exc, HTTPException) else str(exc)
            logger.exception("AI job %s failed", job_id)
            error_text = str(detail)
            error_code = classify_ai_error(error_text)
            try:
                ensure_job_can_write(job_id)
            except HTTPException:
                add_ai_job_event(job_id, "stopped", "AI worker stopped after job state changed.", "warning")
                return
            update_ai_job(job_id, status="failed", stage="failed", error=error_text, error_code=error_code, completed_at=utc_now())
            add_ai_job_event(job_id, "failed", summarize_ai_error(error_text), "error")

    @router.post("/ai/revise")
    def revise_with_ai(payload: AiReviseRequest) -> dict:
        provider = ai_provider_name()
        logger.info(
            "AI revision requested provider=%s target_type=%s target_id=%s question_ids=%s",
            provider,
            payload.target_type,
            payload.target_id,
            payload.question_ids,
        )

        with get_conn() as conn:
            target, reader_questions = load_ai_target(
                conn,
                payload.target_type,
                payload.target_id,
                payload.question_ids,
            )
        logger.info("AI revision context loaded with %s open reader questions", len(reader_questions))

        if payload.draft_body.strip():
            target["body"] = payload.draft_body.strip()

        context_json = ai_revision_service.build_ai_revision_prompt(
            target,
            payload.target_type,
            reader_questions,
            payload.instruction,
            payload.draft_body,
        )
        response = ai_revision_service.run_revision(context_json, reader_questions)

        resolved_question_ids = response["revision"]["resolved_question_ids"]
        logger.info(
            "AI revision succeeded provider=%s target_type=%s target_id=%s resolved_question_ids=%s",
            provider,
            payload.target_type,
            payload.target_id,
            resolved_question_ids,
        )
        return response

    @router.post("/ai/jobs")
    def create_ai_job(payload: AiJobCreate, background_tasks: BackgroundTasks) -> dict:
        with get_conn() as conn:
            base_body = payload.draft_body.strip()
            if not base_body:
                if payload.target_type == "node":
                    body_row = conn.execute("SELECT body FROM nodes WHERE slug = ?", (payload.target_id,)).fetchone()
                else:
                    body_row = conn.execute("SELECT body FROM quizzes WHERE id = ?", (payload.target_id,)).fetchone()
                base_body = body_row["body"] if body_row else ""
            row = ai_job_service.create_ai_job(
                conn,
                payload.target_type,
                payload.target_id,
                payload.question_ids,
                payload.question,
                payload.instruction,
                payload.draft_body,
                ai_provider_name(),
                codex_model_name() if ai_provider_name() == "codex-cli" else openai_model_name(),
                body_hash(base_body),
            )

        add_ai_job_event(row["id"], "queued", "AI job created. Reader questions remain open until draft is applied.")
        background_tasks.add_task(run_ai_job, row["id"])
        return {"job": row_to_ai_job(row)}

    @router.get("/ai/jobs")
    def list_ai_jobs(
        target_type: Optional[str] = None,
        target_id: Optional[str] = None,
        status: Optional[str] = None,
    ) -> dict:
        with get_conn() as conn:
            rows = ai_job_service.list_ai_jobs(conn, target_type, target_id, status)
        return {"jobs": [row_to_ai_job(row) for row in rows]}

    @router.get("/ai/jobs/{job_id}")
    def get_ai_job(job_id: int) -> dict:
        with get_conn() as conn:
            row = get_ai_job_or_404(conn, job_id)
        return {"job": row_to_ai_job(row)}

    @router.get("/ai/jobs/{job_id}/events")
    def list_ai_job_events(job_id: int) -> dict:
        with get_conn() as conn:
            rows = ai_job_service.list_ai_job_events(conn, job_id)
        return {"events": [dict(row) for row in rows]}

    @router.post("/ai/jobs/{job_id}/apply")
    def apply_ai_job(job_id: int, payload: AiJobApply) -> dict:
        with get_conn() as conn:
            row = get_ai_job_or_404(conn, job_id)
            updated = content_write_service.apply_ai_job_body(conn, content_root, row, payload.body)
        add_ai_job_event(job_id, "applied", "Human applied the draft and resolved linked questions.")
        return {"job": row_to_ai_job(updated)}

    @router.post("/ai/jobs/{job_id}/cancel")
    def cancel_ai_job(job_id: int) -> dict:
        with get_conn() as conn:
            updated = ai_job_service.cancel_ai_job(conn, job_id)
        add_ai_job_event(job_id, "cancelled", "Human cancelled the job. Linked questions remain open.", "warning")
        return {"job": row_to_ai_job(updated)}

    @router.post("/ai/jobs/{job_id}/reject")
    def reject_ai_job(job_id: int, payload: AiJobReject) -> dict:
        with get_conn() as conn:
            updated = ai_job_service.reject_ai_job(conn, job_id, payload.reason)
        add_ai_job_event(job_id, "rejected", "Human rejected the draft. Linked questions remain open.", "warning")
        return {"job": row_to_ai_job(updated)}

    @router.post("/ai/jobs/{job_id}/retry")
    def retry_ai_job(job_id: int, background_tasks: BackgroundTasks) -> dict:
        with get_conn() as conn:
            new_row = ai_job_service.retry_ai_job(
                conn,
                job_id,
                ai_provider_name(),
                codex_model_name() if ai_provider_name() == "codex-cli" else openai_model_name(),
            )

        add_ai_job_event(new_row["id"], "queued", f"Retry created from job #{job_id}.")
        background_tasks.add_task(run_ai_job, new_row["id"])
        return {"job": row_to_ai_job(new_row)}

    return router
