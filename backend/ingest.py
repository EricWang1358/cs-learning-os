from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

try:
    from .db import connect, initialize
except ImportError:
    from db import connect, initialize


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
    tags: list[str]
    prerequisites: list[str]
    related: list[str]
    sources: list[str]


@dataclass
class Quiz:
    id: str
    title: str
    area: str
    status: str
    visibility: str
    difficulty: str
    summary: str
    body: str
    path: str
    weight: int
    tags: list[str]
    linked_nodes: list[str]
    sources: list[str]


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
                status=str(meta.get("status") or "seed"),
                visibility=str(meta.get("visibility") or "practice"),
                difficulty=str(meta.get("difficulty") or "medium"),
                summary=str(meta.get("summary") or ""),
                body=body.strip(),
                path=rel_path,
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
    now = datetime.now(timezone.utc).isoformat()

    with conn:
        conn.execute("DELETE FROM quiz_fts")
        conn.execute("DELETE FROM quiz_sources")
        conn.execute("DELETE FROM quiz_links")
        conn.execute("DELETE FROM quiz_tags")
        conn.execute("DELETE FROM quizzes")
        conn.execute("DELETE FROM node_fts")
        conn.execute("DELETE FROM sources")
        conn.execute("DELETE FROM links")
        conn.execute("DELETE FROM node_tags")
        conn.execute("DELETE FROM tags")
        conn.execute("DELETE FROM nodes")

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
                    now,
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

        for quiz in quizzes:
            conn.execute(
                """
                INSERT INTO quizzes (
                    id, title, area, status, visibility, difficulty, summary, body, path, weight, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    quiz.id,
                    quiz.title,
                    quiz.area,
                    quiz.status,
                    quiz.visibility,
                    quiz.difficulty,
                    quiz.summary,
                    quiz.body,
                    quiz.path,
                    quiz.weight,
                    now,
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

    conn.close()
    return len(nodes)


def main() -> int:
    parser = argparse.ArgumentParser(description="Ingest Markdown knowledge nodes into SQLite.")
    parser.add_argument("--content", default="content", help="Path to the content directory.")
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
