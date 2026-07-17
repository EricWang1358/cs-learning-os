"""Offline sync package export (Phase 5).

The package is a ZIP containing a manifest plus typed JSON records that
share the pull-endpoint record schema, so the Android importer reuses the
sync DTOs. Markdown projections are included for readability; media stays
out of v1 packages.
"""

from __future__ import annotations

import json
import sqlite3
import zipfile
from datetime import datetime, timezone
from pathlib import Path

try:
    from .sync_auth import SYNC_PROTOCOL_VERSION, server_id
    from .sync_envelope import content_hash
except ImportError:  # pragma: no cover - script execution
    from sync_auth import SYNC_PROTOCOL_VERSION, server_id
    from sync_envelope import content_hash

FORMAT_MARKER = "cs-learning-os-package"
FORMAT_VERSION = 1


def _node_record(row: sqlite3.Row) -> dict:
    return {
        "type": "node",
        "id": row["slug"],
        "title": row["title"],
        "area": row["area"],
        "track": row["track"],
        "summary": row["summary"],
        "body": row["body"],
        "visibility": row["visibility"],
        "revision": row["revision"],
        "updatedAt": row["updated_at"],
        "hash": content_hash(row["body"]),
    }


def _quiz_record(row: sqlite3.Row) -> dict:
    return {
        "type": "quiz",
        "id": row["id"],
        "title": row["title"],
        "area": row["area"],
        "difficulty": row["difficulty"],
        "summary": row["summary"],
        "body": row["body"],
        "visibility": row["visibility"],
        "revision": row["revision"],
        "updatedAt": row["updated_at"],
        "hash": content_hash(row["body"]),
    }


def _capture_record(row: sqlite3.Row) -> dict:
    return {
        "type": "capture_slip",
        "id": row["id"],
        "body": row["body"],
        "slipType": row["type"],
        "topicHint": row["topic_hint"],
        "sourceLabel": row["source_label"],
        "status": row["status"],
        "revision": row["revision"],
        "createdAt": row["created_at"],
        "updatedAt": row["updated_at"],
    }


def build_sync_package(conn: sqlite3.Connection, out_path: Path) -> dict:
    nodes = conn.execute("SELECT * FROM nodes ORDER BY area, slug").fetchall()
    quizzes = conn.execute("SELECT * FROM quizzes ORDER BY area, id").fetchall()
    slips = conn.execute("SELECT * FROM capture_slips ORDER BY created_at, id").fetchall()

    manifest = {
        "format": FORMAT_MARKER,
        "formatVersion": FORMAT_VERSION,
        "protocolVersion": SYNC_PROTOCOL_VERSION,
        "serverId": server_id(conn),
        "exportedAt": datetime.now(timezone.utc).isoformat(),
        "counts": {
            "nodes": len(nodes),
            "quizzes": len(quizzes),
            "capture_slips": len(slips),
        },
    }

    out_path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2))
        archive.writestr(
            "records/nodes.json",
            json.dumps([_node_record(row) for row in nodes], ensure_ascii=False, indent=2),
        )
        archive.writestr(
            "records/quizzes.json",
            json.dumps([_quiz_record(row) for row in quizzes], ensure_ascii=False, indent=2),
        )
        archive.writestr(
            "records/capture_slips.json",
            json.dumps([_capture_record(row) for row in slips], ensure_ascii=False, indent=2),
        )
        for row in nodes:
            archive.writestr(f"markdown/nodes/{row['area']}/{row['slug']}.md", row["body"] + "\n")
        for row in quizzes:
            archive.writestr(f"markdown/quizzes/{row['area']}/{row['id']}.md", row["body"] + "\n")
    return manifest
