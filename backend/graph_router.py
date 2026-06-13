from __future__ import annotations

import sqlite3
from typing import Callable

from fastapi import APIRouter, Query

try:
    from . import graph_service
except ImportError:
    import graph_service


ConnectionFactory = Callable[[], sqlite3.Connection]


def create_graph_router(get_conn: ConnectionFactory) -> APIRouter:
    router = APIRouter(prefix="/api", tags=["graph"])

    @router.get("/graph")
    def get_graph_root(page: int = Query(default=1, ge=1)) -> dict:
        with get_conn() as conn:
            payload = graph_service.root_graph_payload(conn, page)
            conn.commit()
            return payload

    @router.get("/graph/area/{area}")
    def get_graph_area(area: str, page: int = Query(default=1, ge=1)) -> dict:
        with get_conn() as conn:
            payload = graph_service.area_graph_payload(conn, area, page)
            conn.commit()
            return payload

    @router.get("/graph/track/{area}/{track}")
    def get_graph_track(area: str, track: str, page: int = Query(default=1, ge=1)) -> dict:
        with get_conn() as conn:
            payload = graph_service.track_graph_payload(conn, area, track, page)
            conn.commit()
            return payload

    @router.get("/graph/node/{slug}")
    def get_graph_node(slug: str, page: int = Query(default=1, ge=1)) -> dict:
        with get_conn() as conn:
            payload = graph_service.node_graph_payload(conn, slug, page)
            conn.commit()
            return payload

    return router
