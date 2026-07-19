from __future__ import annotations

import sqlite3
from typing import Callable, Optional

from fastapi import APIRouter, HTTPException, Query

try:
    from . import bite_service
    from .api_models import BiteCardCreate, BiteCardUpdate
except ImportError:
    import bite_service
    from api_models import BiteCardCreate, BiteCardUpdate


ConnectionFactory = Callable[[], sqlite3.Connection]


def create_bite_router(get_conn: ConnectionFactory) -> APIRouter:
    router = APIRouter(prefix="/api", tags=["daily-bite"])

    @router.get("/bites")
    def list_bites(status: str = Query(default="active", pattern="^(active|archive)$")) -> dict:
        with get_conn() as conn:
            return {"bites": bite_service.list_bite_cards(conn, status)}

    @router.post("/bites")
    def create_bite(payload: BiteCardCreate) -> dict:
        with get_conn() as conn:
            return {"bite": bite_service.create_bite_card(conn, payload)}

    @router.get("/bites/{card_id}")
    def get_bite(card_id: int) -> dict:
        try:
            with get_conn() as conn:
                return {"bite": bite_service.get_bite_card(conn, card_id)}
        except ValueError as error:
            raise HTTPException(status_code=404, detail=str(error)) from error

    @router.put("/bites/{card_id}")
    def update_bite(card_id: int, payload: BiteCardUpdate) -> dict:
        try:
            with get_conn() as conn:
                return {"bite": bite_service.update_bite_card(conn, card_id, payload)}
        except ValueError as error:
            raise HTTPException(status_code=404, detail=str(error)) from error

    @router.delete("/bites/{card_id}")
    def delete_bite(card_id: int) -> dict:
        try:
            with get_conn() as conn:
                return {"bite": bite_service.archive_bite_card(conn, card_id)}
        except ValueError as error:
            raise HTTPException(status_code=404, detail=str(error)) from error

    @router.get("/bite/daily")
    def get_daily_bite(day: Optional[str] = Query(default=None, pattern=r"^\d{4}-\d{2}-\d{2}$")) -> dict:
        try:
            with get_conn() as conn:
                return bite_service.daily_bite(conn, day)
        except ValueError as error:
            raise HTTPException(status_code=404, detail=str(error)) from error

    @router.get("/bite/sources")
    def list_sources(area: str = Query(default="")) -> dict:
        with get_conn() as conn:
            return {"sources": bite_service.list_sources(conn, area)}

    @router.post("/bite/extract")
    def extract_cards(source_type: str = Query(pattern="^(quiz|node)$"),
                      source_id: str = Query(min_length=1)) -> dict:
        with get_conn() as conn:
            cards = bite_service.extract_from_source(conn, source_type, source_id)
            return {"cards": cards, "count": len(cards)}

    @router.post("/bite/extract-and-save")
    def extract_and_save(source_type: str = Query(pattern="^(quiz|node)$"),
                         source_id: str = Query(min_length=1)) -> dict:
        with get_conn() as conn:
            cards = bite_service.extract_from_source(conn, source_type, source_id)
            saved = []
            for c in cards:
                saved.append(bite_service.create_bite_card(conn, c))
            return {"cards": saved, "count": len(saved)}

    @router.get("/bite/next")
    def get_next_bite(cursor: str = "") -> dict:
        try:
            with get_conn() as conn:
                return bite_service.next_bite(conn, cursor)
        except ValueError as error:
            raise HTTPException(status_code=404, detail=str(error)) from error

    return router
