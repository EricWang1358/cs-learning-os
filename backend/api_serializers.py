from __future__ import annotations

import sqlite3


def row_to_node(row: sqlite3.Row) -> dict:
    node = {
        "slug": row["slug"],
        "title": row["title"],
        "area": row["area"],
        "track": row["track"],
        "display_order": row["display_order"],
        "status": row["status"],
        "visibility": row["visibility"],
        "summary": row["summary"],
        "path": row["path"],
        "updated_at": row["updated_at"],
    }
    if "last_read_at" in row.keys():
        node["last_read_at"] = row["last_read_at"] or ""
    if "read_count" in row.keys():
        node["read_count"] = row["read_count"] or 0
    return node


def row_to_quiz(row: sqlite3.Row) -> dict:
    return {
        "id": row["id"],
        "title": row["title"],
        "area": row["area"],
        "display_order": row["display_order"],
        "status": row["status"],
        "visibility": row["visibility"],
        "difficulty": row["difficulty"],
        "summary": row["summary"],
        "path": row["path"],
        "weight": row["weight"],
        "updated_at": row["updated_at"],
    }


def row_to_reader_question(row: sqlite3.Row) -> dict:
    return {
        "id": row["id"],
        "target_type": row["target_type"],
        "target_id": row["target_id"],
        "question": row["question"],
        "status": row["status"],
        "created_at": row["created_at"],
        "resolved_at": row["resolved_at"],
        "resolution_note": row["resolution_note"],
    }


def slug_title(value: str) -> str:
    return " ".join(part.capitalize() for part in value.replace("_", "-").split("-"))
