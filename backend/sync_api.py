"""LAN-facing sync gateway.

Only pairing and authenticated sync routes are served here.  The desktop API
remains loopback-only, so exposing this process on a Wi-Fi interface does not
publish the editor, AI, or maintenance endpoints.
"""

from __future__ import annotations

import os
import sqlite3
from pathlib import Path

from fastapi import FastAPI

from .db import connect, initialize
from .runtime_config import sync_advertised_base_url
from .sync_router import create_sync_router


ROOT = Path(__file__).resolve().parents[1]
DB_PATH = Path(os.environ.get("CS_LEARNING_DB", ROOT / "var" / "knowledge.db")).resolve()
CONTENT_ROOT = Path(os.environ.get("CS_LEARNING_CONTENT", ROOT / "content-demo")).resolve()
EXPORT_ROOT = Path(os.environ.get("CS_LEARNING_EXPORT_ROOT", ROOT / "generated" / "exports")).resolve()


def get_conn() -> sqlite3.Connection:
    conn = connect(DB_PATH)
    initialize(conn)
    return conn


app = FastAPI(title="CS Learning OS Sync Gateway")
app.include_router(
    create_sync_router(
        get_conn,
        export_root=EXPORT_ROOT,
        content_root=CONTENT_ROOT,
        advertised_base_url=sync_advertised_base_url(),
    )
)
