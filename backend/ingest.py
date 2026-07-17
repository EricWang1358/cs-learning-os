from __future__ import annotations

import argparse
import hashlib
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

try:
    from .db import connect, initialize
    from .sync_envelope import (
        ENTITY_NODE,
        ENTITY_QUIZ,
        content_hash,
        record_change,
    )
except ImportError:
    from db import connect, initialize
    from sync_envelope import (
        ENTITY_NODE,
        ENTITY_QUIZ,
        content_hash,
        record_change,
    )


FRONTMATTER_RE = re.compile(r"^---\s*\n(.*?)\n---\s*\n", re.DOTALL)


@dataclass
class Node:
    slug: str
    title: str
    area: str
    track: str
    display_order: int
    status: str
    visibility: str
    summary: str
    body: str
    path: str
    updated_at: str
    tags: list[str]
    prerequisites: list[str]
    related: list[str]
    sources: list[str]


@dataclass
class Quiz:
    id: str
    title: str
    area: str
    display_order: int
    status: str
    visibility: str
    difficulty: str
    summary: str
    body: str
    path: str
    updated_at: str
    weight: int
    tags: list[str]
    linked_nodes: list[str]
    sources: list[str]


def strip_utf8_bom(text: str) -> str:
    return text[1:] if text.startswith("\ufeff") else text


def file_updated_at(path: Path) -> str:
    return datetime.fromtimestamp(path.stat().st_mtime, timezone.utc).isoformat()


def parse_scalar(value: str) -> Any:
    value = value.strip()
    if not value:
        return []
    if value.startswith("[") and value.endswith("]"):
        inner = value[1:-1].strip()
        if not inner:
            return []
        return [item.strip().strip("\"'") for item in inner.split(",")]
    return value.strip("\"'")


def parse_frontmatter(text: str) -> tuple[dict[str, Any], str]:
    text = strip_utf8_bom(text)
    match = FRONTMATTER_RE.match(text)
    if not match:
        return {}, text

    meta: dict[str, Any] = {}
    current_key: str | None = None
    for raw_line in match.group(1).splitlines():
        line = raw_line.rstrip()
        if not line.strip():
            continue
        if line.startswith("  - ") and current_key:
            if not isinstance(meta.get(current_key), list):
                meta[current_key] = []
            meta[current_key].append(line[4:].strip().strip("\"'"))
            continue
        if ":" in line:
            key, value = line.split(":", 1)
            current_key = key.strip()
            meta[current_key] = parse_scalar(value)

    return meta, text[match.end() :]


def as_list(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, list):
        return [str(item) for item in value if str(item).strip()]
    if isinstance(value, str) and value.strip():
        return [value.strip()]
    return []


def slug_from_path(path: Path) -> str:
    return path.stem.lower().replace(" ", "-")


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def record_content_file(
    conn,
    content_root: Path,
    path: Path,
    target_type: str,
    target_id: str,
    status: str = "indexed",
    error: str = "",
) -> None:
    stat = path.stat()
    rel_path = path.relative_to(content_root).as_posix()
    now = datetime.now(timezone.utc).isoformat()
    conn.execute(
        """
        INSERT INTO content_files (
            path, target_type, target_id, mtime_ns, size_bytes, sha256, status, last_ingested_at, error
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(path) DO UPDATE SET
            target_type = excluded.target_type,
            target_id = excluded.target_id,
            mtime_ns = excluded.mtime_ns,
            size_bytes = excluded.size_bytes,
            sha256 = excluded.sha256,
            status = excluded.status,
            last_ingested_at = excluded.last_ingested_at,
            error = excluded.error
        """,
        (
            rel_path,
            target_type,
            target_id,
            stat.st_mtime_ns,
            stat.st_size,
            file_sha256(path),
            status,
            now,
            error,
        ),
    )


def read_nodes(content_root: Path) -> list[Node]:
    nodes_root = content_root / "nodes"
    nodes: list[Node] = []
    for path in sorted(nodes_root.rglob("*.md")):
        if path.name == "index.md":
            continue
        text = path.read_text(encoding="utf-8")
        meta, body = parse_frontmatter(text)
        rel_path = path.relative_to(content_root).as_posix()
        slug = str(meta.get("slug") or slug_from_path(path))
        title = str(meta.get("title") or path.stem.replace("-", " ").title())
        area = str(meta.get("area") or path.parent.name)
        nodes.append(
            Node(
                slug=slug,
                title=title,
                area=area,
                track=str(meta.get("track") or "general"),
                display_order=int(meta.get("order") or meta.get("display_order") or 1000),
                status=str(meta.get("status") or "seed"),
                visibility=str(meta.get("visibility") or "support"),
                summary=str(meta.get("summary") or ""),
                body=body.strip(),
                path=rel_path,
                updated_at=file_updated_at(path),
                tags=as_list(meta.get("tags")),
                prerequisites=as_list(meta.get("prerequisites")),
                related=as_list(meta.get("related")),
                sources=as_list(meta.get("sources")),
            )
        )
    return nodes


def read_quizzes(content_root: Path) -> list[Quiz]:
    quizzes_root = content_root / "quizzes"
    if not quizzes_root.exists():
        return []

    quizzes: list[Quiz] = []
    for path in sorted(quizzes_root.rglob("*.md")):
        if path.name == "index.md":
            continue
        text = path.read_text(encoding="utf-8")
        meta, body = parse_frontmatter(text)
        rel_path = path.relative_to(content_root).as_posix()
        quiz_id = str(meta.get("id") or slug_from_path(path))
        quizzes.append(
            Quiz(
                id=quiz_id,
                title=str(meta.get("title") or path.stem.replace("-", " ").title()),
                area=str(meta.get("area") or path.parent.name),
                display_order=int(meta.get("display_order") or meta.get("order") or 1000),
                status=str(meta.get("status") or "seed"),
                visibility=str(meta.get("visibility") or "practice"),
                difficulty=str(meta.get("difficulty") or "medium"),
                summary=str(meta.get("summary") or ""),
                body=body.strip(),
                path=rel_path,
                updated_at=file_updated_at(path),
                weight=int(meta.get("weight") or 1),
                tags=as_list(meta.get("tags")),
                linked_nodes=as_list(meta.get("linked_nodes")),
                sources=as_list(meta.get("sources")),
            )
        )
    return quizzes


def ingest(content_root: Path, db_path: Path) -> int:
    conn = connect(db_path)
    initialize(conn)
    nodes = read_nodes(content_root)
    quizzes = read_quizzes(content_root)

    with conn:
        preserved_node_activity = {
            row["node_slug"]: {
                "last_read_at": row["last_read_at"],
                "read_count": row["read_count"],
                "updated_at": row["updated_at"],
            }
            for row in conn.execute(
                """
                SELECT node_slug, last_read_at, read_count, updated_at
                FROM node_activity
                """
            )
        }
        # Sync lineage: keep revisions stable across a full rebuild so the
        # change cursor stays meaningful, and preserve the append-only
        # attempt log that the quizzes cascade would otherwise wipe.
        preserved_node_revisions = {
            row["slug"]: {"revision": row["revision"], "body": row["body"]}
            for row in conn.execute("SELECT slug, revision, body FROM nodes")
        }
        preserved_quiz_revisions = {
            row["id"]: {"revision": row["revision"], "body": row["body"]}
            for row in conn.execute("SELECT id, revision, body FROM quizzes")
        }
        preserved_attempts = [
            dict(row)
            for row in conn.execute(
                """
                SELECT id, client_attempt_id, quiz_id, grade, answered_at, elapsed_ms, note
                FROM quiz_attempts
                """
            )
        ]

        conn.execute("DELETE FROM quiz_fts")
        conn.execute("DELETE FROM quiz_sources")
        conn.execute("DELETE FROM quiz_links")
        conn.execute("DELETE FROM quiz_tags")
        conn.execute("DELETE FROM quizzes")
        conn.execute("DELETE FROM graph_cache")
        conn.execute("DELETE FROM node_fts")
        conn.execute("DELETE FROM sources")
        conn.execute("DELETE FROM links")
        conn.execute("DELETE FROM node_tags")
        conn.execute("DELETE FROM tags")
        conn.execute("DELETE FROM nodes")
        conn.execute("UPDATE content_files SET status = 'missing'")

        for node in nodes:
            conn.execute(
                """
                INSERT INTO nodes (
                    slug, title, area, track, display_order, status, visibility, summary, body, path, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    node.slug,
                    node.title,
                    node.area,
                    node.track,
                    node.display_order,
                    node.status,
                    node.visibility,
                    node.summary,
                    node.body,
                    node.path,
                    node.updated_at,
                ),
            )

            for tag in node.tags:
                conn.execute("INSERT OR IGNORE INTO tags (name) VALUES (?)", (tag,))
                conn.execute(
                    "INSERT INTO node_tags (node_slug, tag_name) VALUES (?, ?)",
                    (node.slug, tag),
                )

            for target in node.prerequisites:
                conn.execute(
                    "INSERT INTO links (source_slug, target_slug, kind) VALUES (?, ?, ?)",
                    (node.slug, target, "prerequisite"),
                )
            for target in node.related:
                conn.execute(
                    "INSERT INTO links (source_slug, target_slug, kind) VALUES (?, ?, ?)",
                    (node.slug, target, "related"),
                )

            for source in node.sources:
                source_type = "url" if source.startswith(("http://", "https://")) else "local"
                conn.execute(
                    """
                    INSERT INTO sources (node_slug, source, source_type)
                    VALUES (?, ?, ?)
                    """,
                    (node.slug, source, source_type),
                )

            conn.execute(
                """
                INSERT INTO node_fts (slug, title, summary, body, tags)
                VALUES (?, ?, ?, ?, ?)
                """,
                (node.slug, node.title, node.summary, node.body, " ".join(node.tags)),
            )
            record_content_file(conn, content_root, content_root / node.path, "node", node.slug)

        node_slugs = {node.slug for node in nodes}
        for node_slug, activity in preserved_node_activity.items():
            if node_slug not in node_slugs:
                continue
            conn.execute(
                """
                INSERT INTO node_activity (node_slug, last_read_at, read_count, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(node_slug) DO UPDATE SET
                    last_read_at = excluded.last_read_at,
                    read_count = excluded.read_count,
                    updated_at = excluded.updated_at
                """,
                (
                    node_slug,
                    activity["last_read_at"],
                    activity["read_count"],
                    activity["updated_at"],
                ),
            )

        for quiz in quizzes:
            conn.execute(
                """
                INSERT INTO quizzes (
                    id, title, area, display_order, status, visibility, difficulty, summary, body, path, weight, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    quiz.id,
                    quiz.title,
                    quiz.area,
                    quiz.display_order,
                    quiz.status,
                    quiz.visibility,
                    quiz.difficulty,
                    quiz.summary,
                    quiz.body,
                    quiz.path,
                    quiz.weight,
                    quiz.updated_at,
                ),
            )

            for tag in quiz.tags:
                conn.execute("INSERT OR IGNORE INTO tags (name) VALUES (?)", (tag,))
                conn.execute(
                    "INSERT INTO quiz_tags (quiz_id, tag_name) VALUES (?, ?)",
                    (quiz.id, tag),
                )

            for node_slug in quiz.linked_nodes:
                conn.execute(
                    "INSERT INTO quiz_links (quiz_id, node_slug, kind) VALUES (?, ?, ?)",
                    (quiz.id, node_slug, "tests"),
                )

            for source in quiz.sources:
                source_type = "url" if source.startswith(("http://", "https://")) else "local"
                conn.execute(
                    """
                    INSERT INTO quiz_sources (quiz_id, source, source_type)
                    VALUES (?, ?, ?)
                    """,
                    (quiz.id, source, source_type),
                )

            conn.execute(
                """
                INSERT INTO quiz_fts (id, title, summary, body, tags)
                VALUES (?, ?, ?, ?, ?)
                """,
                (quiz.id, quiz.title, quiz.summary, quiz.body, " ".join(quiz.tags)),
            )
            record_content_file(conn, content_root, content_root / quiz.path, "quiz", quiz.id)

        current_node_slugs = set()
        for node in nodes:
            current_node_slugs.add(node.slug)
            previous = preserved_node_revisions.get(node.slug)
            if previous is None:
                conn.execute("UPDATE nodes SET revision = 1 WHERE slug = ?", (node.slug,))
                record_change(conn, ENTITY_NODE, node.slug, 1, content_hash(node.body))
            elif content_hash(previous["body"]) == content_hash(node.body):
                conn.execute(
                    "UPDATE nodes SET revision = ? WHERE slug = ?",
                    (previous["revision"], node.slug),
                )
            else:
                revision = int(previous["revision"] or 0) + 1
                conn.execute("UPDATE nodes SET revision = ? WHERE slug = ?", (revision, node.slug))
                record_change(conn, ENTITY_NODE, node.slug, revision, content_hash(node.body))
        for slug, previous in preserved_node_revisions.items():
            if slug not in current_node_slugs:
                record_change(
                    conn,
                    ENTITY_NODE,
                    slug,
                    int(previous["revision"] or 0) + 1,
                    None,
                    tombstone=True,
                )

        current_quiz_ids = set()
        for quiz in quizzes:
            current_quiz_ids.add(quiz.id)
            previous = preserved_quiz_revisions.get(quiz.id)
            if previous is None:
                conn.execute("UPDATE quizzes SET revision = 1 WHERE id = ?", (quiz.id,))
                record_change(conn, ENTITY_QUIZ, quiz.id, 1, content_hash(quiz.body))
            elif content_hash(previous["body"]) == content_hash(quiz.body):
                conn.execute(
                    "UPDATE quizzes SET revision = ? WHERE id = ?",
                    (previous["revision"], quiz.id),
                )
            else:
                revision = int(previous["revision"] or 0) + 1
                conn.execute("UPDATE quizzes SET revision = ? WHERE id = ?", (revision, quiz.id))
                record_change(conn, ENTITY_QUIZ, quiz.id, revision, content_hash(quiz.body))
        for quiz_id, previous in preserved_quiz_revisions.items():
            if quiz_id not in current_quiz_ids:
                record_change(
                    conn,
                    ENTITY_QUIZ,
                    quiz_id,
                    int(previous["revision"] or 0) + 1,
                    None,
                    tombstone=True,
                )

        for attempt in preserved_attempts:
            if attempt["quiz_id"] not in current_quiz_ids:
                continue
            conn.execute(
                """
                INSERT INTO quiz_attempts (id, client_attempt_id, quiz_id, grade, answered_at, elapsed_ms, note)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    attempt["id"],
                    attempt["client_attempt_id"],
                    attempt["quiz_id"],
                    attempt["grade"],
                    attempt["answered_at"],
                    attempt["elapsed_ms"],
                    attempt["note"],
                ),
            )

        now = datetime.now(timezone.utc).isoformat()
        for key, value in {
            "last_full_ingest_at": now,
            "last_ingest_content_root": str(content_root),
        }.items():
            conn.execute(
                """
                INSERT INTO schema_meta (key, value, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(key) DO UPDATE SET
                    value = excluded.value,
                    updated_at = excluded.updated_at
                """,
                (key, value, now),
            )

    conn.close()
    return len(nodes)


def main() -> int:
    parser = argparse.ArgumentParser(description="Ingest Markdown knowledge nodes into SQLite.")
    parser.add_argument("--content", default="content-demo", help="Path to the content directory.")
    parser.add_argument("--db", default="var/knowledge.db", help="Path to the SQLite database.")
    args = parser.parse_args()

    content_path = Path(args.content).resolve()
    db_path = Path(args.db).resolve()
    count = ingest(content_path, db_path)
    quiz_count = len(read_quizzes(content_path))
    print(f"Ingested {count} nodes and {quiz_count} quizzes into {db_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
