from __future__ import annotations

import re
import shutil
import sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path

from fastapi import HTTPException

try:
    from .reader_question_service import delete_reader_questions_for_target
    from .sync_envelope import (
        ENTITY_NODE,
        bump_revision_and_log,
        log_permanent_delete,
    )
except ImportError:  # pragma: no cover - script execution
    from reader_question_service import delete_reader_questions_for_target
    from sync_envelope import (
        ENTITY_NODE,
        bump_revision_and_log,
        log_permanent_delete,
    )


SAFE_SLUG_RE = re.compile(r"[^a-z0-9-]+")


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def slugify(value: str) -> str:
    slug = SAFE_SLUG_RE.sub("-", value.strip().lower().replace("_", "-")).strip("-")
    return re.sub(r"-+", "-", slug) or "untitled-node"


def strip_utf8_bom(text: str) -> str:
    return text[1:] if text.startswith("\ufeff") else text


def split_markdown_frontmatter(text: str) -> tuple[str, str]:
    text = strip_utf8_bom(text)
    match = re.match(r"^(---\s*\n.*?\n---\s*\n)(.*)$", text, flags=re.DOTALL)
    if not match:
        return "", text
    return match.group(1), match.group(2)


def parse_frontmatter_block(frontmatter: str) -> dict[str, str]:
    meta: dict[str, str] = {}
    if not frontmatter:
        return meta
    for line in frontmatter.splitlines():
        if not line or line == "---" or line.startswith("  - "):
            continue
        if ":" in line:
            key, value = line.split(":", 1)
            meta[key.strip()] = value.strip().strip("\"'")
    return meta


def yaml_scalar(value: object) -> str:
    text = str(value).replace('"', '\\"')
    return f'"{text}"'


def markdown_frontmatter(payload: dict[str, object]) -> str:
    lines = ["---"]
    for key, value in payload.items():
        if isinstance(value, list):
            lines.append(f"{key}: [{', '.join(yaml_scalar(item) for item in value)}]")
        else:
            lines.append(f"{key}: {yaml_scalar(value)}")
    lines.append("---")
    return "\n".join(lines) + "\n\n"


def node_markdown_template(
    slug: str,
    title: str,
    area: str,
    track: str,
    summary: str,
    tags: list[str],
    visibility: str,
    status: str,
    order: int,
) -> str:
    body = f"""# {title}

## Why It Matters

Explain why this node deserves to exist in your learning map.

## Core Idea

Write the smallest useful version of the concept here.

## Example

```text
Replace this demo block with a concrete example.
```

## Common Mistakes

- Add the first mistake or confusion you want future-you to avoid.

## Suggested Next

- Link related nodes after this note becomes stable.
"""
    frontmatter = markdown_frontmatter(
        {
            "slug": slug,
            "title": title,
            "area": area,
            "track": track,
            "order": order,
            "status": status,
            "visibility": visibility,
            "summary": summary or "Draft node. Replace this summary after editing.",
            "tags": tags,
        }
    )
    return frontmatter + body


def update_markdown_frontmatter_value(path: Path, key: str, value: str) -> None:
    text = path.read_text(encoding="utf-8")
    frontmatter, body = split_markdown_frontmatter(text)
    if not frontmatter:
        raise HTTPException(status_code=400, detail="Node source file has no frontmatter")
    lines = frontmatter.strip().splitlines()
    updated = False
    for index, line in enumerate(lines):
        if line.startswith(f"{key}:"):
            lines[index] = f"{key}: {yaml_scalar(value)}"
            updated = True
            break
    if not updated:
        lines.insert(-1, f"{key}: {yaml_scalar(value)}")
    path.write_text("\n".join(lines) + "\n" + body, encoding="utf-8")


def remove_markdown_frontmatter_key(path: Path, key: str) -> None:
    text = path.read_text(encoding="utf-8")
    frontmatter, body = split_markdown_frontmatter(text)
    if not frontmatter:
        raise HTTPException(status_code=400, detail="Node source file has no frontmatter")
    lines = [
        line
        for line in frontmatter.strip().splitlines()
        if not line.startswith(f"{key}:")
    ]
    path.write_text("\n".join(lines) + "\n" + body, encoding="utf-8")


def read_markdown_frontmatter_value(path: Path, key: str) -> str:
    text = path.read_text(encoding="utf-8")
    frontmatter, _ = split_markdown_frontmatter(text)
    return parse_frontmatter_block(frontmatter).get(key, "")


@contextmanager
def restore_file_on_failure(path: Path):
    existed = path.exists()
    original = path.read_text(encoding="utf-8") if existed else ""
    try:
        yield
    except Exception:
        if existed:
            path.write_text(original, encoding="utf-8")
        elif path.exists():
            path.unlink()
        raise


@contextmanager
def stage_file_delete(content_root: Path, path: Path):
    if not path.exists():
        yield
        return
    trash_dir = content_root / ".trash"
    trash_dir.mkdir(parents=True, exist_ok=True)
    staged_path = trash_dir / f"{utc_now().replace(':', '-').replace('.', '-')}-{path.name}"
    shutil.move(str(path), str(staged_path))
    try:
        yield
    except Exception:
        path.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(staged_path), str(path))
        raise
    staged_path.unlink(missing_ok=True)


def upsert_node_file_in_conn(conn: sqlite3.Connection, content_root: Path, path: Path) -> sqlite3.Row:
    try:
        from .ingest import as_list, parse_frontmatter, record_content_file, slug_from_path
    except ImportError:
        from ingest import as_list, parse_frontmatter, record_content_file, slug_from_path

    text = path.read_text(encoding="utf-8")
    meta, body = parse_frontmatter(text)
    rel_path = path.relative_to(content_root).as_posix()
    slug = str(meta.get("slug") or slug_from_path(path))
    title = str(meta.get("title") or path.stem.replace("-", " ").title())
    area = str(meta.get("area") or path.parent.name)
    track = str(meta.get("track") or "general")
    display_order = int(meta.get("order") or meta.get("display_order") or 1000)
    status = str(meta.get("status") or "draft")
    visibility = str(meta.get("visibility") or "support")
    summary = str(meta.get("summary") or "")
    tags = as_list(meta.get("tags"))
    prerequisites = as_list(meta.get("prerequisites"))
    related = as_list(meta.get("related"))
    sources = as_list(meta.get("sources"))
    normalized_body = body.strip()
    now = utc_now()

    conn.execute(
        """
        INSERT INTO nodes (
            slug, title, area, track, display_order, status, visibility, summary, body, path, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(slug) DO UPDATE SET
            title = excluded.title,
            area = excluded.area,
            track = excluded.track,
            display_order = excluded.display_order,
            status = excluded.status,
            visibility = excluded.visibility,
            summary = excluded.summary,
            body = excluded.body,
            path = excluded.path,
            updated_at = excluded.updated_at
        """,
        (
            slug,
            title,
            area,
            track,
            display_order,
            status,
            visibility,
            summary,
            normalized_body,
            rel_path,
            now,
        ),
    )
    bump_revision_and_log(
        conn,
        "nodes",
        "slug",
        ENTITY_NODE,
        slug,
        normalized_body,
        trashed=visibility == "trash",
    )
    conn.execute("DELETE FROM node_tags WHERE node_slug = ?", (slug,))
    conn.execute("DELETE FROM links WHERE source_slug = ?", (slug,))
    conn.execute("DELETE FROM sources WHERE node_slug = ?", (slug,))
    conn.execute("DELETE FROM node_fts WHERE slug = ?", (slug,))
    conn.execute("DELETE FROM graph_cache")

    for tag in tags:
        conn.execute("INSERT OR IGNORE INTO tags (name) VALUES (?)", (tag,))
        conn.execute("INSERT INTO node_tags (node_slug, tag_name) VALUES (?, ?)", (slug, tag))
    for target in prerequisites:
        conn.execute(
            "INSERT INTO links (source_slug, target_slug, kind) VALUES (?, ?, ?)",
            (slug, target, "prerequisite"),
        )
    for target in related:
        conn.execute(
            "INSERT INTO links (source_slug, target_slug, kind) VALUES (?, ?, ?)",
            (slug, target, "related"),
        )
    for source in sources:
        source_type = "url" if source.startswith(("http://", "https://")) else "local"
        conn.execute(
            "INSERT INTO sources (node_slug, source, source_type) VALUES (?, ?, ?)",
            (slug, source, source_type),
        )
    conn.execute(
        """
        INSERT INTO node_fts (slug, title, summary, body, tags)
        VALUES (?, ?, ?, ?, ?)
        """,
        (slug, title, summary, normalized_body, " ".join(tags)),
    )
    record_content_file(conn, content_root, path, "node", slug)
    row = conn.execute("SELECT * FROM nodes WHERE slug = ?", (slug,)).fetchone()
    if not row:
        raise HTTPException(status_code=500, detail="Node upsert failed")
    return row


def create_node(
    conn: sqlite3.Connection,
    content_root: Path,
    title: str,
    area: str,
    track: str,
    summary: str,
    tags: list[str],
    visibility: str,
    status: str,
    order: int,
) -> str:
    title = title.strip()
    if not title:
        raise HTTPException(status_code=400, detail="title cannot be empty")
    area = slugify(area)
    track = slugify(track)
    slug_base = slugify(title)
    tags = [slugify(tag) for tag in tags if tag.strip()]
    summary = summary.strip()

    slug = slug_base
    counter = 2
    while conn.execute("SELECT 1 FROM nodes WHERE slug = ?", (slug,)).fetchone() or (content_root / "nodes" / area / f"{slug}.md").exists():
        slug = f"{slug_base}-{counter}"
        counter += 1

    node_dir = content_root / "nodes" / area
    node_dir.mkdir(parents=True, exist_ok=True)
    node_path = node_dir / f"{slug}.md"
    with restore_file_on_failure(node_path):
        node_path.write_text(
            node_markdown_template(
                slug,
                title,
                area,
                track,
                summary,
                tags,
                visibility,
                status.strip() or "draft",
                order,
            ),
            encoding="utf-8",
        )
        upsert_node_file_in_conn(conn, content_root, node_path)
        conn.commit()
    return slug


def node_source_or_404(conn: sqlite3.Connection, content_root: Path, slug: str) -> tuple[sqlite3.Row, Path]:
    row = conn.execute("SELECT * FROM nodes WHERE slug = ?", (slug,)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="Node not found")
    content_path = content_root / row["path"]
    if not content_path.is_file():
        raise HTTPException(status_code=404, detail="Node source file not found")
    return row, content_path


def update_node_display_order(
    conn: sqlite3.Connection,
    content_root: Path,
    slug: str,
    display_order: int,
    expected_updated_at: str,
) -> sqlite3.Row:
    """Update a node's order through the same file/DB lifecycle as ingest.

    Validation happens before touching the source file. ``restore_file_on_failure``
    then gives the filesystem the same rollback guarantee as the SQLite
    transaction if ingestion or revision logging fails.
    """
    if display_order <= 0:
        raise HTTPException(status_code=422, detail="display_order must be a positive integer")

    # Serialize the compare-and-write sequence. Without an immediate write
    # lock, two desktop clients could both pass the version check and then
    # overwrite one another's order.
    conn.execute("BEGIN IMMEDIATE")
    row, content_path = node_source_or_404(conn, content_root, slug)
    if row["updated_at"] != expected_updated_at:
        raise HTTPException(
            status_code=409,
            detail={
                "code": "node_version_conflict",
                "current_updated_at": row["updated_at"],
            },
        )

    conflict = conn.execute(
        """
        SELECT slug, title
        FROM nodes
        WHERE area = ?
          AND track = ?
          AND display_order = ?
          AND slug != ?
        ORDER BY slug
        LIMIT 1
        """,
        (row["area"], row["track"], display_order, slug),
    ).fetchone()
    if conflict:
        raise HTTPException(
            status_code=409,
            detail={
                "code": "display_order_conflict",
                "display_order": display_order,
                "conflicting_node": {
                    "slug": conflict["slug"],
                    "title": conflict["title"],
                },
            },
        )

    # Preserve the existing frontmatter key when possible. Older content may
    # use ``display_order`` while current nodes use ``order``.
    frontmatter_key = "order"
    if not read_markdown_frontmatter_value(content_path, "order") and read_markdown_frontmatter_value(
        content_path, "display_order"
    ):
        frontmatter_key = "display_order"

    with restore_file_on_failure(content_path):
        update_markdown_frontmatter_value(content_path, frontmatter_key, str(display_order))
        updated = upsert_node_file_in_conn(conn, content_root, content_path)
        conn.commit()
    return updated


def move_node_to_trash(conn: sqlite3.Connection, content_root: Path, slug: str) -> None:
    _, content_path = node_source_or_404(conn, content_root, slug)
    with restore_file_on_failure(content_path):
        previous_visibility = read_markdown_frontmatter_value(content_path, "visibility")
        if previous_visibility and previous_visibility != "trash":
            update_markdown_frontmatter_value(content_path, "previous_visibility", previous_visibility)
        update_markdown_frontmatter_value(content_path, "visibility", "trash")
        upsert_node_file_in_conn(conn, content_root, content_path)
        conn.commit()


def restore_node(conn: sqlite3.Connection, content_root: Path, slug: str) -> None:
    _, content_path = node_source_or_404(conn, content_root, slug)
    with restore_file_on_failure(content_path):
        previous_visibility = read_markdown_frontmatter_value(content_path, "previous_visibility") or "support"
        update_markdown_frontmatter_value(content_path, "visibility", previous_visibility)
        remove_markdown_frontmatter_key(content_path, "previous_visibility")
        upsert_node_file_in_conn(conn, content_root, content_path)
        conn.commit()


def archive_node(conn: sqlite3.Connection, content_root: Path, slug: str) -> None:
    row, content_path = node_source_or_404(conn, content_root, slug)
    if row["visibility"] == "trash":
        raise HTTPException(status_code=400, detail="Restore node from Trashbin before archiving")
    with restore_file_on_failure(content_path):
        previous_visibility = read_markdown_frontmatter_value(content_path, "visibility")
        if previous_visibility and previous_visibility != "archive":
            update_markdown_frontmatter_value(content_path, "previous_visibility", previous_visibility)
        update_markdown_frontmatter_value(content_path, "visibility", "archive")
        upsert_node_file_in_conn(conn, content_root, content_path)
        conn.commit()


def unarchive_node(conn: sqlite3.Connection, content_root: Path, slug: str) -> None:
    _, content_path = node_source_or_404(conn, content_root, slug)
    with restore_file_on_failure(content_path):
        previous_visibility = read_markdown_frontmatter_value(content_path, "previous_visibility") or "support"
        if previous_visibility in {"archive", "trash"}:
            previous_visibility = "support"
        update_markdown_frontmatter_value(content_path, "visibility", previous_visibility)
        remove_markdown_frontmatter_key(content_path, "previous_visibility")
        upsert_node_file_in_conn(conn, content_root, content_path)
        conn.commit()


def permanently_delete_node(conn: sqlite3.Connection, content_root: Path, slug: str) -> None:
    row = conn.execute("SELECT * FROM nodes WHERE slug = ?", (slug,)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="Node not found")
    if row["visibility"] != "trash":
        raise HTTPException(status_code=400, detail="Move node to Trashbin before permanent delete")
    content_path = content_root / row["path"]
    with stage_file_delete(content_root, content_path):
        delete_reader_questions_for_target(conn, "node", slug)
        conn.execute("DELETE FROM links WHERE target_slug = ?", (slug,))
        conn.execute("DELETE FROM nodes WHERE slug = ?", (slug,))
        log_permanent_delete(conn, ENTITY_NODE, slug, row["revision"])
        conn.execute("DELETE FROM node_fts WHERE slug = ?", (slug,))
        conn.execute("DELETE FROM content_files WHERE path = ?", (row["path"],))
        conn.execute("DELETE FROM graph_cache")
        conn.commit()
