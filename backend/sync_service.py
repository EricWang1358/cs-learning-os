"""Scoped pull queries for the personal sync protocol (Phase 2).

The manifest endpoint serves scoped deltas from ``sync_changes``; the pull
endpoint returns full typed records for requested IDs. Scope is a set of
area labels (the desktop has no Area entity), optionally extended by due
reviews and pinned nodes. Push endpoints (Phase 3) accept append-only
learning events with client-stable IDs and per-record receipts.
"""

from __future__ import annotations

import json
import sqlite3
from dataclasses import dataclass, field
from pathlib import Path

try:
    from .learning_service import parse_time, record_quiz_attempt
    from .node_lifecycle_service import (
        markdown_frontmatter,
        parse_frontmatter_block,
        restore_file_on_failure,
        slugify,
        split_markdown_frontmatter,
        upsert_node_file_in_conn,
        yaml_scalar,
    )
    from .sync_envelope import (
        ENTITY_CAPTURE_SLIP,
        ENTITY_QUIZ,
        ENTITY_READER_QUESTION,
        bump_revision_and_log,
        content_hash,
        record_change,
        utc_now,
    )
except ImportError:  # pragma: no cover - script execution
    from learning_service import parse_time, record_quiz_attempt
    from node_lifecycle_service import (
        markdown_frontmatter,
        parse_frontmatter_block,
        restore_file_on_failure,
        slugify,
        split_markdown_frontmatter,
        upsert_node_file_in_conn,
        yaml_scalar,
    )
    from sync_envelope import (
        ENTITY_CAPTURE_SLIP,
        ENTITY_QUIZ,
        ENTITY_READER_QUESTION,
        bump_revision_and_log,
        content_hash,
        record_change,
        utc_now,
    )

MANIFEST_PAGE_LIMIT = 500
PULL_ID_LIMIT = 200

ENTITY_TYPES = {"node", "quiz", "capture_slip", "reader_question", "review_attempt"}


@dataclass
class SyncScope:
    areas: list[str] = field(default_factory=list)
    include_due_reviews: bool = False
    pinned_node_ids: list[str] = field(default_factory=list)

    @classmethod
    def from_payload(cls, payload: dict | None) -> "SyncScope":
        payload = payload or {}
        areas = payload.get("areas") or []
        pinned = payload.get("pinnedNodeIds") or []
        return cls(
            areas=[str(area).strip() for area in areas if str(area).strip()],
            include_due_reviews=bool(payload.get("includeDueReviews")),
            pinned_node_ids=[str(node).strip() for node in pinned if str(node).strip()],
        )


def _due_quiz_ids(conn: sqlite3.Connection, now: str) -> set[str]:
    rows = conn.execute(
        "SELECT target_id FROM review_queue WHERE target_type = 'quiz' AND due_at <= ?",
        (now,),
    ).fetchall()
    return {row["target_id"] for row in rows}


def _due_node_slugs(conn: sqlite3.Connection, now: str) -> set[str]:
    rows = conn.execute(
        "SELECT target_id FROM review_queue WHERE target_type = 'node' AND due_at <= ?",
        (now,),
    ).fetchall()
    return {row["target_id"] for row in rows}


def entity_in_scope(
    conn: sqlite3.Connection,
    entity_type: str,
    entity_id: str,
    scope: SyncScope,
    due_quizzes: set[str],
    due_nodes: set[str],
) -> tuple[bool, str | None]:
    """Return (in_scope, current_area_label). Tombstoned entities (row gone)
    are always in scope so deletions can propagate; their area is None."""
    if entity_type == "node":
        row = conn.execute("SELECT area FROM nodes WHERE slug = ?", (entity_id,)).fetchone()
        if not row:
            return True, None
        area = row["area"]
        in_scope = area in scope.areas or entity_id in scope.pinned_node_ids
        if not in_scope and scope.include_due_reviews:
            in_scope = entity_id in due_nodes
        return in_scope, area
    if entity_type == "quiz":
        row = conn.execute("SELECT area FROM quizzes WHERE id = ?", (entity_id,)).fetchone()
        if not row:
            return True, None
        area = row["area"]
        in_scope = area in scope.areas
        if not in_scope and scope.include_due_reviews:
            in_scope = entity_id in due_quizzes
        return in_scope, area
    if entity_type == "capture_slip":
        return True, None
    if entity_type == "reader_question":
        row = conn.execute(
            "SELECT target_type, target_id FROM reader_questions WHERE client_id = ?",
            (entity_id,),
        ).fetchone()
        if not row:
            row = conn.execute(
                "SELECT target_type, target_id FROM reader_questions WHERE ? = ('db-' || id)",
                (entity_id,),
            ).fetchone()
        if not row:
            return True, None
        return entity_in_scope(conn, row["target_type"], row["target_id"], scope, due_quizzes, due_nodes)
    if entity_type == "review_attempt":
        row = conn.execute(
            "SELECT quiz_id FROM quiz_attempts WHERE client_attempt_id = ?",
            (entity_id,),
        ).fetchone()
        if not row:
            return True, None
        return entity_in_scope(conn, "quiz", row["quiz_id"], scope, due_quizzes, due_nodes)
    return False, None


def _current_area(conn: sqlite3.Connection, entity_type: str, entity_id: str) -> str | None:
    if entity_type == "node":
        row = conn.execute("SELECT area FROM nodes WHERE slug = ?", (entity_id,)).fetchone()
        return row["area"] if row else None
    if entity_type == "quiz":
        row = conn.execute("SELECT area FROM quizzes WHERE id = ?", (entity_id,)).fetchone()
        return row["area"] if row else None
    return None


def manifest_changes(
    conn: sqlite3.Connection,
    cursor: int,
    scope: SyncScope,
    now: str,
    limit: int = MANIFEST_PAGE_LIMIT,
) -> dict:
    """Change rows with seq > cursor, newest-first applied by the client.

    Delta rows are NOT scope-filtered: area labels travel with each row so
    the client can apply in-scope changes and drop local copies of entities
    that moved out of its subset. Scope is still enforced where content is
    served (baseline synthesis and the pull endpoint)."""
    due_quizzes = _due_quiz_ids(conn, now) if scope.include_due_reviews else set()
    due_nodes = _due_node_slugs(conn, now) if scope.include_due_reviews else set()

    high_water = conn.execute(
        "SELECT COALESCE(MAX(seq), 0) AS hw FROM sync_changes"
    ).fetchone()["hw"]
    rows = conn.execute(
        "SELECT * FROM sync_changes WHERE seq > ? AND seq <= ? ORDER BY seq LIMIT ?",
        (max(0, cursor), high_water, limit + 1),
    ).fetchall()
    has_more = len(rows) > limit
    rows = rows[:limit]

    changes: list[dict] = []
    seen: set[tuple[str, str]] = set()
    for row in rows:
        seen.add((row["entity_type"], row["entity_id"]))
        changes.append(
            {
                "type": row["entity_type"],
                "id": row["entity_id"],
                "revision": row["revision"],
                "hash": row["content_hash"],
                "tombstone": bool(row["tombstone"]),
                "area": _current_area(conn, row["entity_type"], row["entity_id"]),
            }
        )

    if cursor <= 0:
        baseline = _baseline_changes(conn, scope, due_quizzes, due_nodes, seen)
        changes = baseline + changes

    next_cursor = rows[-1]["seq"] if rows else high_water
    return {
        "cursor": int(next_cursor),
        "changes": changes,
        "hasMore": has_more,
    }


def _baseline_changes(
    conn: sqlite3.Connection,
    scope: SyncScope,
    due_quizzes: set[str],
    due_nodes: set[str],
    seen: set[tuple[str, str]],
) -> list[dict]:
    baseline: list[dict] = []
    node_rows = conn.execute(
        "SELECT slug, area, body, revision, visibility FROM nodes"
    ).fetchall()
    for row in node_rows:
        if ("node", row["slug"]) in seen:
            continue
        in_scope = row["area"] in scope.areas or row["slug"] in scope.pinned_node_ids
        if not in_scope and scope.include_due_reviews:
            in_scope = row["slug"] in due_nodes
        if not in_scope:
            continue
        baseline.append(
            {
                "type": "node",
                "id": row["slug"],
                "revision": row["revision"],
                "hash": content_hash(row["body"]),
                "tombstone": row["visibility"] == "trash",
                "area": row["area"],
            }
        )
    quiz_rows = conn.execute(
        "SELECT id, area, body, revision, visibility FROM quizzes"
    ).fetchall()
    for row in quiz_rows:
        if ("quiz", row["id"]) in seen:
            continue
        in_scope = row["area"] in scope.areas
        if not in_scope and scope.include_due_reviews:
            in_scope = row["id"] in due_quizzes
        if not in_scope:
            continue
        baseline.append(
            {
                "type": "quiz",
                "id": row["id"],
                "revision": row["revision"],
                "hash": content_hash(row["body"]),
                "tombstone": row["visibility"] == "trash",
                "area": row["area"],
            }
        )
    return baseline


def pull_records(
    conn: sqlite3.Connection,
    entity_type: str,
    ids: list[str],
    scope: SyncScope,
    now: str,
) -> list[dict]:
    if entity_type not in ENTITY_TYPES:
        raise ValueError(f"unsupported entity type: {entity_type}")
    due_quizzes = _due_quiz_ids(conn, now) if scope.include_due_reviews else set()
    due_nodes = _due_node_slugs(conn, now) if scope.include_due_reviews else set()
    records: list[dict] = []
    for entity_id in ids[:PULL_ID_LIMIT]:
        in_scope, _area = entity_in_scope(conn, entity_type, entity_id, scope, due_quizzes, due_nodes)
        if not in_scope:
            continue
        record = _record_for(conn, entity_type, entity_id)
        if record is not None:
            records.append(record)
    return records


def _record_for(conn: sqlite3.Connection, entity_type: str, entity_id: str) -> dict | None:
    if entity_type == "node":
        row = conn.execute("SELECT * FROM nodes WHERE slug = ?", (entity_id,)).fetchone()
        if not row:
            return None
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
    if entity_type == "quiz":
        row = conn.execute("SELECT * FROM quizzes WHERE id = ?", (entity_id,)).fetchone()
        if not row:
            return None
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
    if entity_type == "review_attempt":
        row = conn.execute(
            "SELECT * FROM quiz_attempts WHERE client_attempt_id = ?",
            (entity_id,),
        ).fetchone()
        if not row:
            return None
        return {
            "type": "review_attempt",
            "id": row["client_attempt_id"],
            "quizId": row["quiz_id"],
            "grade": row["grade"],
            "answeredAt": row["answered_at"],
            "elapsedMs": row["elapsed_ms"],
            "note": row["note"],
        }
    if entity_type == "reader_question":
        row = conn.execute(
            "SELECT * FROM reader_questions WHERE client_id = ? OR ('db-' || id) = ?",
            (entity_id, entity_id),
        ).fetchone()
        if not row:
            return None
        return {
            "type": "reader_question",
            "id": row["client_id"] or f"db-{row['id']}",
            "targetType": row["target_type"],
            "targetId": row["target_id"],
            "question": row["question"],
            "status": row["status"],
            "createdAt": row["created_at"],
            "resolvedAt": row["resolved_at"],
            "resolutionNote": row["resolution_note"],
        }
    if entity_type == "capture_slip":
        row = conn.execute("SELECT * FROM capture_slips WHERE id = ?", (entity_id,)).fetchone()
        if not row:
            return None
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
    return None


# ---------------------------------------------------------------------------
# Push (Phase 3): append-only learning events with per-record receipts.
# ---------------------------------------------------------------------------

RECEIPT_ACCEPTED = "accepted"
RECEIPT_DUPLICATE = "duplicate"
RECEIPT_REJECTED = "rejected"

CAPTURE_SLIP_TYPES = {"unclear", "mistake", "video_note", "concept_seed", "question"}


def _receipt(record_id: str, status: str, reason: str = "") -> dict:
    receipt = {"id": record_id, "status": status}
    if reason:
        receipt["reason"] = reason
    return receipt


def push_attempts(conn: sqlite3.Connection, items: list[dict]) -> list[dict]:
    """Accept review attempts by client ID. Duplicates and unknown quizzes
    are reported per record; accepted attempts flow through the desktop
    scheduler exactly like locally recorded ones."""
    receipts: list[dict] = []
    for item in items:
        client_id = item["clientAttemptId"]
        existing = conn.execute(
            "SELECT 1 FROM quiz_attempts WHERE client_attempt_id = ?",
            (client_id,),
        ).fetchone()
        if existing:
            receipts.append(_receipt(client_id, RECEIPT_DUPLICATE))
            continue
        quiz = conn.execute(
            "SELECT 1 FROM quizzes WHERE id = ? AND deleted_at IS NULL",
            (item["quizId"],),
        ).fetchone()
        if not quiz:
            receipts.append(_receipt(client_id, RECEIPT_REJECTED, "unknown_quiz"))
            continue
        answered_at = (item.get("answeredAt") or "").strip() or None
        if answered_at:
            try:
                parse_time(answered_at.replace("Z", "+00:00"))
            except (TypeError, ValueError):
                receipts.append(_receipt(client_id, RECEIPT_REJECTED, "invalid_answered_at"))
                continue
        record_quiz_attempt(
            conn,
            item["quizId"],
            item["grade"],
            elapsed_ms=item.get("elapsedMs", 0),
            note=item.get("note", ""),
            client_attempt_id=client_id,
            answered_at=answered_at,
        )
        receipts.append(_receipt(client_id, RECEIPT_ACCEPTED))
    return receipts


def push_captures(conn: sqlite3.Connection, items: list[dict]) -> list[dict]:
    receipts: list[dict] = []
    for item in items:
        slip_id = item["id"]
        existing = conn.execute(
            "SELECT 1 FROM capture_slips WHERE id = ?",
            (slip_id,),
        ).fetchone()
        if existing:
            receipts.append(_receipt(slip_id, RECEIPT_DUPLICATE))
            continue
        slip_type = item.get("type") or "concept_seed"
        if slip_type not in CAPTURE_SLIP_TYPES:
            receipts.append(_receipt(slip_id, RECEIPT_REJECTED, "unknown_type"))
            continue
        now = utc_now()
        created_at = (item.get("createdAt") or "").strip() or now
        conn.execute(
            """
            INSERT INTO capture_slips (id, body, type, topic_hint, source_label, status, created_at, updated_at, revision)
            VALUES (?, ?, ?, ?, ?, 'inbox', ?, ?, 1)
            """,
            (
                slip_id,
                item["body"],
                slip_type,
                (item.get("topicHint") or "").strip(),
                (item.get("sourceLabel") or "").strip(),
                created_at,
                now,
            ),
        )
        record_change(conn, ENTITY_CAPTURE_SLIP, slip_id, 1, content_hash(item["body"]))
        receipts.append(_receipt(slip_id, RECEIPT_ACCEPTED))
    conn.commit()
    return receipts


def push_reader_questions(conn: sqlite3.Connection, items: list[dict]) -> list[dict]:
    receipts: list[dict] = []
    for item in items:
        client_id = item["clientId"]
        existing = conn.execute(
            "SELECT 1 FROM reader_questions WHERE client_id = ?",
            (client_id,),
        ).fetchone()
        if existing:
            receipts.append(_receipt(client_id, RECEIPT_DUPLICATE))
            continue
        target = conn.execute(
            "SELECT 1 FROM nodes WHERE slug = ?",
            (item["nodeId"],),
        ).fetchone()
        if not target:
            receipts.append(_receipt(client_id, RECEIPT_REJECTED, "unknown_target"))
            continue
        now = utc_now()
        conn.execute(
            """
            INSERT INTO reader_questions (client_id, target_type, target_id, question, status, created_at)
            VALUES (?, 'node', ?, ?, 'open', ?)
            """,
            (client_id, item["nodeId"], item["question"], (item.get("createdAt") or "").strip() or now),
        )
        record_change(conn, ENTITY_READER_QUESTION, client_id)
        receipts.append(_receipt(client_id, RECEIPT_ACCEPTED))
    conn.commit()
    return receipts


def push_nodes(
    conn: sqlite3.Connection,
    content_root: Path,
    device_id: str,
    items: list[dict],
) -> list[dict]:
    """Apply idempotent mobile updates to existing desktop Nodes.

    The desktop content file remains the durable authoring representation, so
    every accepted update writes a complete frontmatter/body document and
    reuses the normal ingest path to refresh SQLite, FTS, and sync_changes.
    """
    _ensure_push_receipt_schema(conn)
    receipts: list[dict] = []
    for item in items:
        change_id = str(item["changeId"])
        replay = _load_push_receipt(conn, device_id, change_id)
        if replay is not None:
            receipts.append(replay)
            continue

        node_id = str(item["id"])
        existing = conn.execute("SELECT * FROM nodes WHERE slug = ?", (node_id,)).fetchone()
        if existing is None:
            if item.get("baseRevision") is not None or int(item.get("revision", -1)) != 1:
                receipts.append(_content_receipt(node_id, RECEIPT_REJECTED, "missing_node"))
                continue
        current_revision = int(existing["revision"]) if existing is not None else 0
        if bool(item.get("tombstone")):
            receipts.append(_content_receipt(node_id, RECEIPT_REJECTED, "tombstone_unsupported", current_revision))
            continue
        if existing is not None and int(item.get("baseRevision", -1)) != current_revision:
            receipts.append(_content_receipt(node_id, RECEIPT_REJECTED, "stale_revision", current_revision))
            continue
        if int(item.get("revision", -1)) != current_revision + 1:
            receipts.append(_content_receipt(node_id, RECEIPT_REJECTED, "invalid_revision", current_revision))
            continue
        if slugify(node_id) != node_id:
            receipts.append(_content_receipt(node_id, RECEIPT_REJECTED, "invalid_node_id", current_revision))
            continue

        body = str(item.get("body") or "").strip()
        title = str(item.get("title") or "").strip()
        area = slugify(str(item.get("area") or ""))
        track = slugify(str(item.get("track") or ""))
        visibility = str(item.get("visibility") or "").strip()
        if not body or not title or not area or not track or visibility not in {"core", "support", "draft", "archive"}:
            receipts.append(_content_receipt(node_id, RECEIPT_REJECTED, "invalid_node", current_revision))
            continue

        source_path = content_root / (existing["path"] if existing is not None else f"nodes/{area}/{node_id}.md")
        source_path.parent.mkdir(parents=True, exist_ok=True)
        synced_metadata = {
            "title": title,
            "area": area,
            "track": track,
            "visibility": visibility,
            "summary": str(item.get("summary") or "").strip(),
        }
        if existing is None or not source_path.exists():
            document = markdown_frontmatter(
                {
                    "slug": node_id,
                    **synced_metadata,
                    "order": existing["display_order"] if existing is not None else 1000,
                    "status": existing["status"] if existing is not None else "draft",
                    "tags": [],
                }
            ) + body + "\n"
        else:
            try:
                document = _merged_frontmatter_document(source_path, synced_metadata, body)
            except ValueError:
                receipts.append(_content_receipt(node_id, RECEIPT_REJECTED, "invalid_source", current_revision))
                continue
        with restore_file_on_failure(source_path):
            source_path.write_text(document, encoding="utf-8")
            updated = upsert_node_file_in_conn(conn, content_root, source_path)
            receipt = _content_receipt(node_id, RECEIPT_ACCEPTED, revision=int(updated["revision"]))
            _save_push_receipt(conn, device_id, change_id, receipt)
            conn.commit()
        receipts.append(receipt)
    return receipts


def _merged_frontmatter_document(path: Path, fields: dict[str, str], body: str) -> str:
    """Update synced fields while retaining unowned desktop frontmatter."""
    frontmatter, _ = split_markdown_frontmatter(path.read_text(encoding="utf-8"))
    if not frontmatter:
        raise ValueError("Existing node source has no frontmatter")

    lines = frontmatter.strip().splitlines()
    seen: set[str] = set()
    for index, line in enumerate(lines):
        key, separator, _ = line.partition(":")
        if separator and key in fields:
            lines[index] = f"{key}: {yaml_scalar(fields[key])}"
            seen.add(key)
    for key, value in fields.items():
        if key not in seen:
            lines.insert(-1, f"{key}: {yaml_scalar(value)}")
    return "\n".join(lines) + "\n\n" + body + "\n"


def push_quizzes(
    conn: sqlite3.Connection,
    content_root: Path,
    device_id: str,
    items: list[dict],
) -> list[dict]:
    """Apply idempotent mobile Quiz changes to desktop Markdown and SQLite."""
    _ensure_push_receipt_schema(conn)
    receipts: list[dict] = []
    for item in items:
        change_id = str(item["changeId"])
        replay = _load_push_receipt(conn, device_id, change_id)
        if replay is not None:
            receipts.append(replay)
            continue

        quiz_id = str(item["id"])
        existing = conn.execute("SELECT * FROM quizzes WHERE id = ?", (quiz_id,)).fetchone()
        if existing is None and (item.get("baseRevision") is not None or int(item.get("revision", -1)) != 1):
            receipts.append(_content_receipt(quiz_id, RECEIPT_REJECTED, "missing_quiz"))
            continue
        current_revision = int(existing["revision"]) if existing is not None else 0
        if bool(item.get("tombstone")):
            receipts.append(_content_receipt(quiz_id, RECEIPT_REJECTED, "tombstone_unsupported", current_revision))
            continue
        if existing is not None and int(item.get("baseRevision", -1)) != current_revision:
            receipts.append(_content_receipt(quiz_id, RECEIPT_REJECTED, "stale_revision", current_revision))
            continue
        if int(item.get("revision", -1)) != current_revision + 1:
            receipts.append(_content_receipt(quiz_id, RECEIPT_REJECTED, "invalid_revision", current_revision))
            continue
        if slugify(quiz_id) != quiz_id:
            receipts.append(_content_receipt(quiz_id, RECEIPT_REJECTED, "invalid_quiz_id", current_revision))
            continue

        body = str(item.get("body") or "").strip()
        requested_title = str(item.get("title") or "").strip()
        title = requested_title or (str(existing["title"]) if existing is not None else _quiz_title_from_body(body))
        area = slugify(str(item.get("area") or ""))
        requested_difficulty = str(item.get("difficulty") or "").strip()
        difficulty = requested_difficulty or (str(existing["difficulty"]) if existing is not None else "medium")
        requested_summary = item.get("summary")
        summary = str(requested_summary).strip() if requested_summary is not None else (
            str(existing["summary"]) if existing is not None else ""
        )
        visibility = str(item.get("visibility") or "").strip()
        if not body or not title or not area or visibility not in {"practice", "support", "draft", "archive"}:
            receipts.append(_content_receipt(quiz_id, RECEIPT_REJECTED, "invalid_quiz", current_revision))
            continue

        source_path = content_root / (existing["path"] if existing is not None else f"quizzes/{area}/{quiz_id}.md")
        source_path.parent.mkdir(parents=True, exist_ok=True)
        synced_metadata = {
            "area": area,
            "visibility": visibility,
        }
        if requested_title or existing is None:
            synced_metadata["title"] = title
        if requested_summary is not None or existing is None:
            synced_metadata["summary"] = summary
        if requested_difficulty or existing is None:
            synced_metadata["difficulty"] = difficulty
        if existing is None or not source_path.exists():
            document = markdown_frontmatter(
                {
                    "id": quiz_id,
                    **synced_metadata,
                    "order": 1000,
                    "status": "draft",
                    "tags": [],
                }
            ) + body + "\n"
        else:
            try:
                document = _merged_frontmatter_document(source_path, synced_metadata, body)
            except ValueError:
                receipts.append(_content_receipt(quiz_id, RECEIPT_REJECTED, "invalid_source", current_revision))
                continue

        with restore_file_on_failure(source_path):
            source_path.write_text(document, encoding="utf-8")
            _upsert_quiz_change_in_conn(
                conn,
                existing,
                quiz_id,
                title,
                area,
                difficulty,
                summary,
                visibility,
                body,
                source_path.relative_to(content_root).as_posix(),
                _frontmatter_metadata(document),
            )
            updated = conn.execute("SELECT * FROM quizzes WHERE id = ?", (quiz_id,)).fetchone()
            if updated is None:
                raise RuntimeError("Quiz upsert failed")
            receipt = _content_receipt(quiz_id, RECEIPT_ACCEPTED, revision=int(updated["revision"]))
            _save_push_receipt(conn, device_id, change_id, receipt)
            conn.commit()
        receipts.append(receipt)
    return receipts


def _quiz_title_from_body(body: str) -> str:
    lines = body.splitlines()
    for index, line in enumerate(lines):
        if line.strip().lower() != "## prompt":
            continue
        for candidate in lines[index + 1:]:
            title = candidate.strip()
            if title:
                return title[:300]
        break
    return "Untitled Quiz"


def _frontmatter_metadata(document: str) -> dict[str, str]:
    frontmatter, _ = split_markdown_frontmatter(document)
    return parse_frontmatter_block(frontmatter)


def _upsert_quiz_change_in_conn(
    conn: sqlite3.Connection,
    existing: sqlite3.Row | None,
    quiz_id: str,
    title: str,
    area: str,
    difficulty: str,
    summary: str,
    visibility: str,
    body: str,
    relative_path: str,
    metadata: dict[str, str],
) -> None:
    now = utc_now()
    source_title = metadata.get("title") or title
    source_area = metadata.get("area") or area
    source_difficulty = metadata.get("difficulty") or difficulty
    source_summary = metadata["summary"] if "summary" in metadata else summary
    source_visibility = metadata.get("visibility") or visibility
    source_order = int(metadata.get("order") or (existing["display_order"] if existing is not None else 1000))
    source_status = metadata.get("status") or (existing["status"] if existing is not None else "draft")
    if existing is None:
        conn.execute(
            """
            INSERT INTO quizzes (
                id, title, area, display_order, status, visibility, difficulty,
                summary, body, path, weight, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                quiz_id,
                source_title,
                source_area,
                source_order,
                source_status,
                source_visibility,
                source_difficulty,
                source_summary,
                body,
                relative_path,
                1,
                now,
            ),
        )
    else:
        conn.execute(
            """
            UPDATE quizzes
            SET title = ?, area = ?, display_order = ?, status = ?, visibility = ?,
                difficulty = ?, summary = ?, body = ?, path = ?, updated_at = ?
            WHERE id = ?
            """,
            (
                source_title,
                source_area,
                source_order,
                source_status,
                source_visibility,
                source_difficulty,
                source_summary,
                body,
                relative_path,
                now,
                quiz_id,
            ),
        )
    bump_revision_and_log(conn, "quizzes", "id", ENTITY_QUIZ, quiz_id, body)
    tags = [
        row["tag_name"]
        for row in conn.execute(
            "SELECT tag_name FROM quiz_tags WHERE quiz_id = ? ORDER BY tag_name",
            (quiz_id,),
        ).fetchall()
    ]
    conn.execute("DELETE FROM quiz_fts WHERE id = ?", (quiz_id,))
    conn.execute("DELETE FROM graph_cache")
    conn.execute(
        "INSERT INTO quiz_fts (id, title, summary, body, tags) VALUES (?, ?, ?, ?, ?)",
        (quiz_id, source_title, source_summary, body, " ".join(tags)),
    )


def _ensure_push_receipt_schema(conn: sqlite3.Connection) -> None:
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS sync_push_receipts (
            device_id TEXT NOT NULL,
            change_id TEXT NOT NULL,
            receipt_json TEXT NOT NULL,
            created_at TEXT NOT NULL,
            PRIMARY KEY (device_id, change_id)
        )
        """
    )


def _load_push_receipt(conn: sqlite3.Connection, device_id: str, change_id: str) -> dict | None:
    row = conn.execute(
        "SELECT receipt_json FROM sync_push_receipts WHERE device_id = ? AND change_id = ?",
        (device_id, change_id),
    ).fetchone()
    return json.loads(row["receipt_json"]) if row else None


def _save_push_receipt(conn: sqlite3.Connection, device_id: str, change_id: str, receipt: dict) -> None:
    conn.execute(
        """
        INSERT INTO sync_push_receipts (device_id, change_id, receipt_json, created_at)
        VALUES (?, ?, ?, ?)
        """,
        (device_id, change_id, json.dumps(receipt, sort_keys=True), utc_now()),
    )


def _content_receipt(record_id: str, status: str, reason: str = "", revision: int | None = None) -> dict:
    receipt = _receipt(record_id, status, reason)
    if revision is not None:
        receipt["revision"] = revision
    return receipt
