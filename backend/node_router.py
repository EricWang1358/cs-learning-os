from __future__ import annotations

import sqlite3
from datetime import datetime
from pathlib import Path
from typing import Callable, Optional

from fastapi import APIRouter, HTTPException, Query

try:
    from . import content_write_service
    from . import node_lifecycle_service
    from . import search_service
    from .ai_job_service import utc_now
    from .api_models import BodyUpdate, NodeCreate, NodeDisplayOrderUpdate, NodeReadMark
    from .api_serializers import row_to_node, slug_title
    from .content_write_service import body_hash
except ImportError:
    import content_write_service
    import node_lifecycle_service
    import search_service
    from ai_job_service import utc_now
    from api_models import BodyUpdate, NodeCreate, NodeDisplayOrderUpdate, NodeReadMark
    from api_serializers import row_to_node, slug_title
    from content_write_service import body_hash


ConnectionFactory = Callable[[], sqlite3.Connection]


def create_node_router(get_conn: ConnectionFactory, content_root: Path) -> APIRouter:
    router = APIRouter(prefix="/api", tags=["nodes"])

    def get_node_payload(slug: str) -> dict:
        with get_conn() as conn:
            row = conn.execute(
                """
                SELECT n.*, COALESCE(a.last_read_at, '') AS last_read_at, COALESCE(a.read_count, 0) AS read_count
                FROM nodes n
                LEFT JOIN node_activity a ON a.node_slug = n.slug
                WHERE n.slug = ?
                """,
                (slug,),
            ).fetchone()
            if not row:
                raise HTTPException(status_code=404, detail="Node not found")
            tags = [
                item["tag_name"]
                for item in conn.execute(
                    "SELECT tag_name FROM node_tags WHERE node_slug = ? ORDER BY tag_name",
                    (slug,),
                ).fetchall()
            ]
            links = [
                {"target": item["target_slug"], "kind": item["kind"]}
                for item in conn.execute(
                    "SELECT target_slug, kind FROM links WHERE source_slug = ? ORDER BY kind, target_slug",
                    (slug,),
                ).fetchall()
            ]
            sources = [
                {
                    "source": item["source"],
                    "source_type": item["source_type"],
                    "note": item["note"],
                }
                for item in conn.execute(
                    "SELECT source, source_type, note FROM sources WHERE node_slug = ? ORDER BY id",
                    (slug,),
                ).fetchall()
            ]
            open_question_count = conn.execute(
                """
                SELECT COUNT(*) AS count
                FROM reader_questions
                WHERE target_type = 'node'
                  AND target_id = ?
                  AND status = 'open'
                """,
                (slug,),
            ).fetchone()["count"]

        node = row_to_node(row)
        node["body"] = row["body"]
        node["body_hash"] = body_hash(row["body"])
        node["tags"] = tags
        node["links"] = links
        node["sources"] = sources
        node["open_question_count"] = open_question_count
        return {"node": node}

    @router.get("/nodes")
    def list_nodes(
        area: Optional[str] = None,
        visibility: Optional[str] = None,
        sort: str = Query(default="order"),
    ) -> dict:
        with get_conn() as conn:
            rows = search_service.list_nodes(conn, area=area, visibility=visibility, sort=sort)
        return {"nodes": [row_to_node(row) for row in rows]}

    @router.get("/areas")
    def list_areas() -> dict:
        with get_conn() as conn:
            rows = conn.execute(
                """
                SELECT
                    area,
                    COUNT(*) AS node_count,
                    MIN(display_order) AS first_order
                FROM nodes
                WHERE visibility NOT IN ('archive', 'trash')
                GROUP BY area
                ORDER BY first_order, area
                """
            ).fetchall()
            archive_count = conn.execute(
                "SELECT COUNT(*) AS count FROM nodes WHERE visibility = 'archive'"
            ).fetchone()["count"]
            trash_count = conn.execute(
                "SELECT COUNT(*) AS count FROM nodes WHERE visibility = 'trash'"
            ).fetchone()["count"]
            total_count = sum(row["node_count"] for row in rows)

        return {
            "areas": [
                {
                    "area": row["area"],
                    "label": slug_title(row["area"]),
                    "node_count": row["node_count"],
                    "first_order": row["first_order"],
                }
                for row in rows
            ],
            "system": {
                "all": total_count,
                "archive": archive_count,
                "trash": trash_count,
            },
        }

    @router.post("/nodes")
    def create_node(payload: NodeCreate) -> dict:
        with get_conn() as conn:
            slug = node_lifecycle_service.create_node(
                conn,
                content_root,
                payload.title,
                payload.area,
                payload.track,
                payload.summary,
                payload.tags,
                payload.visibility,
                payload.status,
                payload.order,
            )

        return get_node_payload(slug)

    @router.get("/nodes/{slug}")
    def get_node(slug: str) -> dict:
        return get_node_payload(slug)

    @router.post("/nodes/{slug}/read")
    def mark_node_read(slug: str, payload: NodeReadMark) -> dict:
        with get_conn() as conn:
            row = conn.execute("SELECT slug FROM nodes WHERE slug = ?", (slug,)).fetchone()
            if not row:
                raise HTTPException(status_code=404, detail="Node not found")
            now = payload.read_at or utc_now()
            existing = conn.execute(
                "SELECT last_read_at FROM node_activity WHERE node_slug = ?",
                (slug,),
            ).fetchone()
            should_increment = True
            write_time = now
            if existing and existing["last_read_at"] and payload.min_interval_seconds > 0:
                try:
                    previous = datetime.fromisoformat(str(existing["last_read_at"]).replace("Z", "+00:00"))
                    current = datetime.fromisoformat(str(now).replace("Z", "+00:00"))
                    elapsed_seconds = (current - previous).total_seconds()
                    should_increment = elapsed_seconds >= payload.min_interval_seconds
                    if elapsed_seconds < payload.min_interval_seconds:
                        write_time = existing["last_read_at"]
                        should_increment = False
                except ValueError:
                    should_increment = True
            conn.execute(
                """
                INSERT INTO node_activity (node_slug, last_read_at, read_count, updated_at)
                VALUES (?, ?, 1, ?)
                ON CONFLICT(node_slug) DO UPDATE SET
                    last_read_at = excluded.last_read_at,
                    read_count = node_activity.read_count + ?,
                    updated_at = excluded.updated_at
                """,
                (slug, write_time, utc_now(), 1 if should_increment else 0),
            )
            conn.commit()
        return get_node_payload(slug)

    @router.patch("/nodes/{slug}/display-order")
    def update_node_display_order(slug: str, payload: NodeDisplayOrderUpdate) -> dict:
        with get_conn() as conn:
            node_lifecycle_service.update_node_display_order(
                conn,
                content_root,
                slug,
                payload.display_order,
                payload.expected_updated_at,
            )
        return get_node_payload(slug)

    @router.put("/nodes/{slug}/body")
    def update_node_body(slug: str, payload: BodyUpdate) -> dict:
        with get_conn() as conn:
            content_write_service.update_target_body(
                conn,
                content_root,
                "node",
                slug,
                payload.body,
                payload.base_body_hash,
            )

        return get_node_payload(slug)

    @router.post("/nodes/{slug}/trash")
    def trash_node(slug: str) -> dict:
        with get_conn() as conn:
            node_lifecycle_service.move_node_to_trash(conn, content_root, slug)
        return get_node_payload(slug)

    @router.post("/nodes/{slug}/restore")
    def restore_node(slug: str) -> dict:
        with get_conn() as conn:
            node_lifecycle_service.restore_node(conn, content_root, slug)
        return get_node_payload(slug)

    @router.post("/nodes/{slug}/archive")
    def archive_node(slug: str) -> dict:
        with get_conn() as conn:
            node_lifecycle_service.archive_node(conn, content_root, slug)
        return get_node_payload(slug)

    @router.post("/nodes/{slug}/unarchive")
    def unarchive_node(slug: str) -> dict:
        with get_conn() as conn:
            node_lifecycle_service.unarchive_node(conn, content_root, slug)
        return get_node_payload(slug)

    @router.delete("/nodes/{slug}")
    def permanently_delete_node(slug: str) -> dict:
        with get_conn() as conn:
            node_lifecycle_service.permanently_delete_node(conn, content_root, slug)
        return {"ok": True}

    @router.get("/search")
    def search(q: str = Query(default="", min_length=0), sort: str = Query(default="relevance")) -> dict:
        with get_conn() as conn:
            rows = search_service.search_nodes(conn, q, sort=sort)

        return {"nodes": [row_to_node(row) for row in rows]}

    @router.get("/areas/{area}/tracks")
    def list_area_tracks(area: str) -> dict:
        with get_conn() as conn:
            rows = conn.execute(
                """
                SELECT
                    track,
                    COUNT(*) AS node_count,
                    MIN(display_order) AS first_order
                FROM nodes
                WHERE area = ?
                  AND visibility NOT IN ('archive', 'trash')
                GROUP BY track
                ORDER BY first_order, track
                """,
                (area,),
            ).fetchall()

        return {
            "area": area,
            "tracks": [
                {
                    "track": row["track"],
                    "label": slug_title(row["track"]),
                    "node_count": row["node_count"],
                    "first_order": row["first_order"],
                }
                for row in rows
            ],
        }

    return router
