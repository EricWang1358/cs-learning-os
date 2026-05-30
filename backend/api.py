from __future__ import annotations

import sqlite3
import re
import os
import json
import logging
import hashlib
import shutil
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

from fastapi import BackgroundTasks, FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

try:
    from .ai_job_service import (
        add_ai_job_event as service_add_ai_job_event,
        classify_ai_error,
        ensure_job_can_write as service_ensure_job_can_write,
        get_ai_job_or_404,
        recover_stale_ai_jobs as service_recover_stale_ai_jobs,
        row_to_ai_job,
        summarize_ai_error,
        update_ai_job as service_update_ai_job,
        utc_now,
    )
    from .codex_service import (
        codex_base_url,
        codex_cli_path,
        codex_is_configured,
        codex_job_home,
        codex_model_name,
        codex_model_provider_name,
        codex_preflight,
        run_codex_json,
    )
    from .db import connect, initialize
    from .patch_policy import apply_patch_ops
except ImportError:
    from ai_job_service import (
        add_ai_job_event as service_add_ai_job_event,
        classify_ai_error,
        ensure_job_can_write as service_ensure_job_can_write,
        get_ai_job_or_404,
        recover_stale_ai_jobs as service_recover_stale_ai_jobs,
        row_to_ai_job,
        summarize_ai_error,
        update_ai_job as service_update_ai_job,
        utc_now,
    )
    from codex_service import (
        codex_base_url,
        codex_cli_path,
        codex_is_configured,
        codex_job_home,
        codex_model_name,
        codex_model_provider_name,
        codex_preflight,
        run_codex_json,
    )
    from db import connect, initialize
    from patch_policy import apply_patch_ops


ROOT = Path(__file__).resolve().parents[1]
CONTENT_ROOT = Path(os.environ.get("CS_LEARNING_CONTENT", ROOT / "content-demo")).resolve()
DB_PATH = Path(os.environ.get("CS_LEARNING_DB", ROOT / "var" / "knowledge.db")).resolve()
logger = logging.getLogger("cs_learning.api")
GRAPH_PAGE_SIZE = 12
STABLE_AREAS = ["abilities", "algorithms", "cs-fundamentals", "projects", "tools", "questions"]
AREA_LABELS = {
    "abilities": "Abilities",
    "algorithms": "Algorithms",
    "cs-fundamentals": "CS fundamentals",
    "projects": "Projects",
    "tools": "Tools",
    "questions": "Questions",
    "archive": "Archive",
    "trash": "Trashbin",
}
TRACK_LABELS = {
    "general": "General",
    "c-and-memory": "C and memory",
    "gdb-debugging": "GDB debugging",
    "x86-64-assembly": "x86-64 assembly",
    "bomb-lab": "Bomb Lab",
    "networking": "Networking",
}
HEADING_RE = re.compile(r"^(#{1,3})\s+(.+?)\s*$", re.MULTILINE)
SAFE_SLUG_RE = re.compile(r"[^a-z0-9-]+")

app = FastAPI(title="CS Learning OS API")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class ReaderQuestionCreate(BaseModel):
    target_type: str = Field(pattern="^(node|quiz)$")
    target_id: str = Field(min_length=1)
    question: str = Field(min_length=1)


class ReaderQuestionResolve(BaseModel):
    resolution_note: str = ""


class BodyUpdate(BaseModel):
    body: str


class NodeCreate(BaseModel):
    title: str = Field(min_length=1)
    area: str = Field(default="questions", pattern="^[a-z0-9-]+$")
    track: str = Field(default="general", pattern="^[a-z0-9-]+$")
    summary: str = ""
    tags: list[str] = []
    visibility: str = Field(default="support", pattern="^(core|support|draft|archive)$")
    status: str = "draft"
    order: int = 1000


class NodeReadMark(BaseModel):
    read_at: Optional[str] = None
    min_interval_seconds: int = Field(default=60, ge=0, le=86400)


class AiReviseRequest(BaseModel):
    target_type: str = Field(pattern="^(node|quiz)$")
    target_id: str = Field(min_length=1)
    question_ids: list[int] = []
    instruction: str = ""
    draft_body: str = ""


class AiJobCreate(BaseModel):
    target_type: str = Field(pattern="^(node|quiz)$")
    target_id: str = Field(min_length=1)
    question_ids: list[int] = []
    question: str = ""
    instruction: str = ""
    draft_body: str = ""


class AiJobApply(BaseModel):
    body: str = Field(min_length=1)


class AiJobReject(BaseModel):
    reason: str = ""


def get_conn() -> sqlite3.Connection:
    conn = connect(DB_PATH)
    initialize(conn)
    return conn


def directory_size(path: Path) -> int:
    if not path.exists():
        return 0
    if path.is_file():
        return path.stat().st_size
    total = 0
    for item in path.rglob("*"):
        if item.is_file():
            try:
                total += item.stat().st_size
            except OSError:
                continue
    return total


def plain_markdown_text(text: str) -> str:
    return re.sub(r"\*\*([^*]+?)\*\*", r"\1", re.sub(r"`([^`]+)`", r"\1", text)).strip()


def heading_id(text: str, index: int) -> str:
    base = plain_markdown_text(text).lower()
    base = re.sub(r"[^a-z0-9\u4e00-\u9fff]+", "-", base).strip("-")
    return f"section-{base or 'heading'}-{index}"


def markdown_headings(body: str) -> list[dict]:
    headings: list[dict] = []
    for index, match in enumerate(HEADING_RE.finditer(body)):
        text = plain_markdown_text(match.group(2))
        headings.append(
            {
                "type": "heading",
                "id": heading_id(text, index),
                "label": text,
                "meta": f"h{len(match.group(1))}",
                "hint": "Open this section in focus reading.",
                "level": len(match.group(1)),
                "href": "",
                "child_count": 0,
                "has_children": False,
            }
        )
    return headings


def graph_page(items: list[dict], page: int) -> tuple[list[dict], dict]:
    safe_page = max(page, 1)
    total = len(items)
    total_pages = max(1, (total + GRAPH_PAGE_SIZE - 1) // GRAPH_PAGE_SIZE)
    current_page = min(safe_page, total_pages)
    start = (current_page - 1) * GRAPH_PAGE_SIZE
    return items[start : start + GRAPH_PAGE_SIZE], {
        "page": current_page,
        "page_size": GRAPH_PAGE_SIZE,
        "total": total,
        "total_pages": total_pages,
        "has_prev": current_page > 1,
        "has_next": current_page < total_pages,
    }


def graph_cache_key(level: str, parts: list[str], page: int) -> str:
    return ":".join([level, *parts, f"page={max(page, 1)}"])


def get_cached_graph(conn: sqlite3.Connection, cache_key: str) -> dict | None:
    row = conn.execute("SELECT payload_json FROM graph_cache WHERE cache_key = ?", (cache_key,)).fetchone()
    if not row:
        return None
    return json.loads(row["payload_json"])


def set_cached_graph(conn: sqlite3.Connection, cache_key: str, payload: dict) -> None:
    conn.execute(
        """
        INSERT INTO graph_cache (cache_key, payload_json, updated_at)
        VALUES (?, ?, ?)
        ON CONFLICT(cache_key) DO UPDATE SET
            payload_json = excluded.payload_json,
            updated_at = excluded.updated_at
        """,
        (cache_key, json.dumps(payload, ensure_ascii=False), utc_now()),
    )


def graph_node(
    node_type: str,
    node_id: str,
    label: str,
    meta: str = "",
    hint: str = "",
    child_count: int = 0,
    href: str = "",
    level: int = 0,
) -> dict:
    return {
        "type": node_type,
        "id": node_id,
        "label": label,
        "meta": meta,
        "hint": hint,
        "child_count": child_count,
        "has_children": child_count > 0,
        "href": href,
        "level": level,
    }


def graph_response(
    conn: sqlite3.Connection,
    cache_key: str,
    center: dict,
    path: list[dict],
    children: list[dict],
    page: int = 1,
    actions: list[dict] | None = None,
) -> dict:
    paged_children, pagination = graph_page(children, page)
    payload = {
        "center": center,
        "path": path,
        "children": paged_children,
        "pagination": pagination,
        "actions": actions or [],
    }
    set_cached_graph(conn, cache_key, payload)
    return payload


def root_graph_payload(conn: sqlite3.Connection, page: int) -> dict:
    cache_key = graph_cache_key("root", ["workbench"], page)
    cached = get_cached_graph(conn, cache_key)
    if cached:
        return cached

    rows = conn.execute(
        """
        SELECT area, COUNT(*) AS count, MIN(display_order) AS first_order
        FROM nodes
        WHERE visibility NOT IN ('archive', 'trash')
        GROUP BY area
        """
    ).fetchall()
    counts = {row["area"]: row["count"] for row in rows}
    areas = [area for area in STABLE_AREAS if counts.get(area, 0) > 0]
    extras = sorted(area for area in counts if area not in STABLE_AREAS)
    children = [
        graph_node(
            "area",
            area,
            AREA_LABELS.get(area, area.replace("-", " ").title()),
            f"area · {counts[area]} nodes",
            "Open this knowledge domain.",
            counts[area],
            f"/graph/area/{area}",
        )
        for area in [*areas, *extras]
    ]
    center = graph_node("root", "workbench", "Workbench", "root · learning map", "Choose a knowledge domain.", len(children))
    return graph_response(conn, cache_key, center, [center], children, page)


def area_graph_payload(conn: sqlite3.Connection, area: str, page: int) -> dict:
    cache_key = graph_cache_key("area", [area], page)
    cached = get_cached_graph(conn, cache_key)
    if cached:
        return cached

    rows = conn.execute(
        """
        SELECT track, COUNT(*) AS count, MIN(display_order) AS first_order
        FROM nodes
        WHERE area = ? AND visibility NOT IN ('archive', 'trash')
        GROUP BY track
        ORDER BY first_order, track
        """,
        (area,),
    ).fetchall()
    if not rows:
        raise HTTPException(status_code=404, detail="Graph area not found")
    label = AREA_LABELS.get(area, area.replace("-", " ").title())
    children = [
        graph_node(
            "track",
            row["track"],
            TRACK_LABELS.get(row["track"], row["track"].replace("-", " ").title()),
            f"track · {row['count']} nodes",
            f"Continue through {label}.",
            row["count"],
            f"/graph/track/{area}/{row['track']}",
        )
        for row in rows
    ]
    root = graph_node("root", "workbench", "Workbench", "root", "Back to all domains.", len(children), "/graph")
    center = graph_node("area", area, label, f"area · {sum(row['count'] for row in rows)} nodes", "Choose a track.", len(children))
    return graph_response(conn, cache_key, center, [root, center], children, page)


def track_graph_payload(conn: sqlite3.Connection, area: str, track: str, page: int) -> dict:
    cache_key = graph_cache_key("track", [area, track], page)
    cached = get_cached_graph(conn, cache_key)
    if cached:
        return cached

    rows = conn.execute(
        """
        SELECT slug, title, summary, display_order, updated_at, body
        FROM nodes
        WHERE area = ? AND track = ? AND visibility NOT IN ('archive', 'trash')
        ORDER BY display_order, updated_at DESC, title
        """,
        (area, track),
    ).fetchall()
    if not rows:
        raise HTTPException(status_code=404, detail="Graph track not found")
    area_label = AREA_LABELS.get(area, area.replace("-", " ").title())
    track_label = TRACK_LABELS.get(track, track.replace("-", " ").title())
    children = [
        graph_node(
            "node",
            row["slug"],
            row["title"],
            f"node · #{row['display_order']} · {len(markdown_headings(row['body']))} headings",
            row["summary"] or "Open headings or read this note.",
            len(markdown_headings(row["body"])),
            f"/graph/node/{row['slug']}",
        )
        for row in rows
    ]
    path = [
        graph_node("root", "workbench", "Workbench", "root", "Back to all domains.", 0, "/graph"),
        graph_node("area", area, area_label, "area", "Back to this domain.", len(rows), f"/graph/area/{area}"),
        graph_node("track", track, track_label, f"track · {len(rows)} nodes", "Choose a node.", len(rows)),
    ]
    return graph_response(conn, cache_key, path[-1], path, children, page)


def node_graph_payload(conn: sqlite3.Connection, slug: str, page: int) -> dict:
    cache_key = graph_cache_key("node", [slug], page)
    cached = get_cached_graph(conn, cache_key)
    if cached:
        return cached

    row = conn.execute("SELECT * FROM nodes WHERE slug = ?", (slug,)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="Graph node not found")
    headings = markdown_headings(row["body"])
    for heading in headings:
        heading["href"] = f"/nodes/{slug}?focus=1#{heading['id']}"
    area_label = AREA_LABELS.get(row["area"], row["area"].replace("-", " ").title())
    track_label = TRACK_LABELS.get(row["track"], row["track"].replace("-", " ").title())
    path = [
        graph_node("root", "workbench", "Workbench", "root", "Back to all domains.", 0, "/graph"),
        graph_node("area", row["area"], area_label, "area", "Back to this domain.", 0, f"/graph/area/{row['area']}"),
        graph_node(
            "track",
            row["track"],
            track_label,
            "track",
            "Back to this track.",
            0,
            f"/graph/track/{row['area']}/{row['track']}",
        ),
        graph_node("node", slug, row["title"], f"node · {len(headings)} headings", row["summary"], len(headings)),
    ]
    actions = [
        {"kind": "open_reading", "label": "Open reading", "href": f"/nodes/{slug}"},
        {"kind": "focus_reading", "label": "Focus reading", "href": f"/nodes/{slug}?focus=1"},
    ]
    return graph_response(conn, cache_key, path[-1], path, headings, page, actions)


def update_ai_job(job_id: int, **fields: object) -> None:
    service_update_ai_job(get_conn, job_id, **fields)


def add_ai_job_event(job_id: int, stage: str, message: str, level: str = "info") -> None:
    service_add_ai_job_event(get_conn, job_id, stage, message, level)


def ensure_job_can_write(job_id: int) -> sqlite3.Row:
    return service_ensure_job_can_write(get_conn, job_id)


def recover_stale_ai_jobs() -> None:
    service_recover_stale_ai_jobs(get_conn, int(os.environ.get("CS_LEARNING_STALE_JOB_SECONDS", "900")))


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


def slugify(value: str) -> str:
    slug = SAFE_SLUG_RE.sub("-", value.strip().lower().replace("_", "-")).strip("-")
    return re.sub(r"-+", "-", slug) or "untitled-node"


def split_markdown_frontmatter(text: str) -> tuple[str, str]:
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


def node_markdown_template(slug: str, title: str, area: str, track: str, summary: str, tags: list[str], visibility: str, status: str, order: int) -> str:
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


def write_markdown_body(path: Path, body: str) -> None:
    text = path.read_text(encoding="utf-8")
    frontmatter, _ = split_markdown_frontmatter(text)
    normalized_body = body.strip() + "\n"
    path.write_text(frontmatter + normalized_body, encoding="utf-8")


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
def stage_file_delete(path: Path):
    if not path.exists():
        yield
        return
    trash_dir = CONTENT_ROOT / ".trash"
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


def body_hash(body: str) -> str:
    return hashlib.sha256(body.strip().encode("utf-8")).hexdigest()


def build_fts_query(term: str) -> str:
    tokens = re.findall(r"[\w-]+", term, flags=re.UNICODE)
    return " ".join(token.replace('"', "") for token in tokens)


def update_node_body_in_conn(conn: sqlite3.Connection, row: sqlite3.Row, body: str) -> None:
    now = datetime.now(timezone.utc).isoformat()
    slug = row["slug"]
    tags = [
        item["tag_name"]
        for item in conn.execute(
            "SELECT tag_name FROM node_tags WHERE node_slug = ? ORDER BY tag_name",
            (slug,),
        ).fetchall()
    ]
    conn.execute("UPDATE nodes SET body = ?, updated_at = ? WHERE slug = ?", (body, now, slug))
    conn.execute("DELETE FROM node_fts WHERE slug = ?", (slug,))
    conn.execute(
        """
        INSERT INTO node_fts (slug, title, summary, body, tags)
        VALUES (?, ?, ?, ?, ?)
        """,
        (slug, row["title"], row["summary"], body, " ".join(tags)),
    )


def upsert_node_file_in_conn(conn: sqlite3.Connection, path: Path) -> sqlite3.Row:
    try:
        from .ingest import parse_frontmatter, as_list, slug_from_path
    except ImportError:
        from ingest import parse_frontmatter, as_list, slug_from_path

    text = path.read_text(encoding="utf-8")
    meta, body = parse_frontmatter(text)
    rel_path = path.relative_to(CONTENT_ROOT).as_posix()
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
    row = conn.execute("SELECT * FROM nodes WHERE slug = ?", (slug,)).fetchone()
    if not row:
        raise HTTPException(status_code=500, detail="Node upsert failed")
    return row


def update_quiz_body_in_conn(conn: sqlite3.Connection, row: sqlite3.Row, body: str) -> None:
    now = datetime.now(timezone.utc).isoformat()
    quiz_id = row["id"]
    tags = [
        item["tag_name"]
        for item in conn.execute(
            "SELECT tag_name FROM quiz_tags WHERE quiz_id = ? ORDER BY tag_name",
            (quiz_id,),
        ).fetchall()
    ]
    conn.execute("UPDATE quizzes SET body = ?, updated_at = ? WHERE id = ?", (body, now, quiz_id))
    conn.execute("DELETE FROM quiz_fts WHERE id = ?", (quiz_id,))
    conn.execute(
        """
        INSERT INTO quiz_fts (id, title, summary, body, tags)
        VALUES (?, ?, ?, ?, ?)
        """,
        (quiz_id, row["title"], row["summary"], body, " ".join(tags)),
    )


def like_term(term: str) -> str:
    escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return f"%{escaped}%"


def openai_model_name() -> str:
    return os.environ.get("CS_LEARNING_OPENAI_MODEL", "gpt-5.4-mini")


def codex_fake_mode() -> str:
    return os.environ.get("CS_LEARNING_CODEX_FAKE", "").strip().lower()


def ai_provider_name() -> str:
    return os.environ.get("CS_LEARNING_AI_PROVIDER", "codex-cli").strip().lower()


def openai_is_configured() -> bool:
    return bool(os.environ.get("OPENAI_API_KEY"))


def extract_response_text(response: object) -> str:
    output_text = getattr(response, "output_text", "")
    if output_text:
        return output_text
    output = getattr(response, "output", [])
    chunks: list[str] = []
    for item in output or []:
        for content in getattr(item, "content", []) or []:
            text = getattr(content, "text", "")
            if text:
                chunks.append(text)
    return "\n".join(chunks)


def ai_revision_schema() -> dict:
    return {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "revised_body": {
                "type": "string",
                "description": "Full replacement Markdown body only. Leave empty only when patch_ops can build the final body.",
            },
            "patch_ops": {
                "type": "array",
                "description": "Preferred compact Markdown patch operations. Use exact find text from the original body.",
                "items": {
                    "type": "object",
                    "additionalProperties": False,
                    "properties": {
                        "op": {
                            "type": "string",
                            "enum": ["replace", "append_after", "append_end"],
                        },
                        "section": {
                            "type": "string",
                            "description": "Nearby Markdown heading or human-readable location.",
                        },
                        "find": {
                            "type": "string",
                            "description": "Exact existing Markdown to replace or append after. Empty only for append_end.",
                        },
                        "replace": {
                            "type": "string",
                            "description": "New Markdown fragment.",
                        },
                    },
                    "required": ["op", "section", "find", "replace"],
                },
            },
            "summary": {
                "type": "string",
                "description": "One short sentence explaining the main improvement.",
            },
            "rationale": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Why these changes answer the learner's confusion.",
            },
            "changed_sections": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Markdown section headings or areas that changed.",
            },
            "resolved_question_ids": {
                "type": "array",
                "items": {"type": "integer"},
                "description": "Reader question IDs that the revised body directly answers.",
            },
            "suggested_new_nodes": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Reusable prerequisite ideas that deserve separate future nodes.",
            },
        },
        "required": [
            "revised_body",
            "patch_ops",
            "summary",
            "rationale",
            "changed_sections",
            "resolved_question_ids",
            "suggested_new_nodes",
        ],
    }


def revision_response(
    result: dict,
    reader_questions: list[dict],
    model: str,
    provider: str,
    original_body: str = "",
) -> dict:
    patch_ops = result.get("patch_ops") or []
    revised_body = result.get("revised_body", "").strip()
    if not revised_body and patch_ops and original_body:
        revised_body = apply_patch_ops(original_body, patch_ops)
    if not revised_body:
        logger.error("AI revision returned an empty body: %s", result)
        raise HTTPException(
            status_code=502,
            detail=(
                "AI revision returned neither a full body nor applicable patch ops. "
                f"Raw result: {json.dumps(result, ensure_ascii=False)[:1000]}"
            ),
        )

    known_question_ids = {item["id"] for item in reader_questions}
    resolved_question_ids = [
        question_id
        for question_id in result.get("resolved_question_ids", [])
        if question_id in known_question_ids
    ]

    return {
        "revision": {
            "revised_body": revised_body,
            "patch_ops": patch_ops,
            "summary": result.get("summary", ""),
            "rationale": result.get("rationale", []),
            "changed_sections": result.get("changed_sections", []),
            "resolved_question_ids": resolved_question_ids,
            "suggested_new_nodes": result.get("suggested_new_nodes", []),
            "model": model,
            "provider": provider,
        }
    }


def load_ai_target(
    conn: sqlite3.Connection,
    target_type: str,
    target_id: str,
    question_ids: list[int],
) -> tuple[dict, list[dict]]:
    if target_type == "node":
        row = conn.execute("SELECT * FROM nodes WHERE slug = ?", (target_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Node not found")
        tags = [
            item["tag_name"]
            for item in conn.execute(
                "SELECT tag_name FROM node_tags WHERE node_slug = ? ORDER BY tag_name",
                (target_id,),
            ).fetchall()
        ]
        links = [
            {"target": item["target_slug"], "kind": item["kind"]}
            for item in conn.execute(
                "SELECT target_slug, kind FROM links WHERE source_slug = ? ORDER BY kind, target_slug",
                (target_id,),
            ).fetchall()
        ]
        target = row_to_node(row) | {"body": row["body"], "tags": tags, "links": links}
    else:
        row = conn.execute("SELECT * FROM quizzes WHERE id = ?", (target_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Quiz not found")
        tags = [
            item["tag_name"]
            for item in conn.execute(
                "SELECT tag_name FROM quiz_tags WHERE quiz_id = ? ORDER BY tag_name",
                (target_id,),
            ).fetchall()
        ]
        linked_nodes = [
            {"slug": item["node_slug"], "kind": item["kind"], "title": item["title"]}
            for item in conn.execute(
                """
                SELECT ql.node_slug, ql.kind, COALESCE(n.title, ql.node_slug) AS title
                FROM quiz_links ql
                LEFT JOIN nodes n ON n.slug = ql.node_slug
                WHERE ql.quiz_id = ?
                ORDER BY ql.kind, ql.node_slug
                """,
                (target_id,),
            ).fetchall()
        ]
        target = row_to_quiz(row) | {"body": row["body"], "tags": tags, "linked_nodes": linked_nodes}

    params: list[object] = [target_type, target_id]
    query = """
        SELECT *
        FROM reader_questions
        WHERE target_type = ?
          AND target_id = ?
    """
    if question_ids:
        placeholders = ",".join("?" for _ in question_ids)
        query += f" AND id IN ({placeholders})"
        params.extend(question_ids)
    else:
        query += " AND status IN ('open', 'queued', 'solving', 'draft_ready')"
    query += " ORDER BY created_at DESC, id DESC"
    rows = conn.execute(query, params).fetchall()
    return target, [row_to_reader_question(item) for item in rows]


def build_ai_revision_prompt(
    target: dict,
    target_type: str,
    reader_questions: list[dict],
    instruction: str,
    draft_body: str,
) -> str:
    content_standard = """
Standard A: bilingual practical exam note. Use a patient tutorial tone, aligned English and Chinese,
concrete command/code examples, plain explanation, common mistakes, quick recall, and deliberate links.
Quality bar: match the depth of "Shark Tank Passcode: process_code and is_valid_code". Do not skip
prerequisite vocabulary, command effects, operand/register roles, state changes, branch decisions, or
arithmetic. For C/GDB/assembly, include tiny examples and define terms a first-pass learner would ask about.
When a reader question is local to the current node, fold the answer into this body. If it reveals a
reusable prerequisite, mention it in suggested_new_nodes instead of bloating this body.
Placement gate: cs-fundamentals is only for intro-level prerequisites or foundational bridges such as
intro C, GDB, x86-64, binary representation, memory, CSAPP/Bomb Lab basics. Do not place advanced,
project-specific, tool-only, or rare-trick material there by default.

Standard Q: quiz-bank item. Keep prompt, answer, explanation, plain explanation, what this tests, and
linked review. Do not skip reasoning steps; show line-by-line state changes and arithmetic when relevant.
Include "How To Think" when the solution depends on recognizing noise, calling convention, state tracing,
pointer/memory layout, or a non-obvious instruction pattern. Explain tempting wrong answers when useful.
"""
    payload = {
        "target_type": target_type,
        "target": target,
        "reader_questions": reader_questions,
        "extra_instruction": instruction.strip(),
        "draft_body_override": draft_body.strip(),
        "content_standard": content_standard.strip(),
    }
    return json.dumps(payload, ensure_ascii=False, indent=2)


def build_ai_revision_instruction(context_json: str) -> str:
    context = json.loads(context_json)
    target = context["target"]
    reader_questions = context["reader_questions"]
    original_body = context["draft_body_override"] or target["body"]
    questions_text = "\n".join(
        f"- #{item['id']}: {item['question']}" for item in reader_questions
    ) or "- No saved reader questions; use the extra instruction."
    return f"""
You are revising a personal CS learning knowledge base from a local web app.

Return a JSON object matching the provided schema. Do not wrap it in Markdown.
Rules:
- Prefer patch_ops over rewriting the whole file. Use small exact-find patches when the answer can be localized.
- For replace patch_ops, find must contain the complete old block being replaced, not just a heading or first line.
- A replace patch_op must not keep a duplicate copy of the old block after the new block. If you add bilingual lines, replace the full original section with the bilingual section.
- If patch_ops can build the final Markdown, revised_body may be an empty string.
- If no safe exact patch is possible, revised_body must be the complete replacement Markdown body only, without YAML frontmatter.
- revised_body and patch_ops must not both be empty. If no useful edit is needed, return the original body unchanged as revised_body.
- Preserve the useful structure of the original body.
- Improve clarity, fill missing reasoning steps, and keep explanations tutorial-like.
- If target_type is node, prefer Standard A.
- If target_type is quiz, prefer Standard Q.
- Treat Shark Tank Passcode as the minimum quality bar for quiz and low-level systems explanations.
- Do not create shallow prerequisite nodes. If a concept deserves a node, make it tutorial-grade; otherwise fold it into the current body.
- Keep cs-fundamentals intro-level only. Suggest a different area/track for advanced, project-specific, tool-only, or rare material.
- Do not invent external sources.
- resolved_question_ids may include only reader questions directly answered in revised_body.

Target:
- type: {context["target_type"]}
- id: {target.get("slug") or target.get("id")}
- title: {target.get("title")}
- summary: {target.get("summary")}
- tags: {", ".join(target.get("tags", []))}

Extra instruction:
{context["extra_instruction"] or "Improve clarity for the saved reader questions."}

Reader questions:
{questions_text}

Content standard:
{context["content_standard"]}

Original Markdown body:
```markdown
{original_body}
```
""".strip()


def run_codex_revision(context_json: str, reader_questions: list[dict]) -> dict:
    fake_mode = codex_fake_mode()
    if fake_mode:
        if fake_mode == "success":
            context = json.loads(context_json)
            target = context["target"]
            original_body = (context["draft_body_override"] or target["body"]).strip()
            questions = context["reader_questions"]
            return revision_response(
                {
                    "revised_body": "",
                    "patch_ops": [
                        {
                            "op": "append_end",
                            "section": "end",
                            "find": "",
                            "replace": "## AI Draft Smoke Note\nThis fake Codex draft answers the selected reader question for the CRUD demo flow.",
                        }
                    ],
                    "summary": "Fake Codex draft added a small clarification.",
                    "rationale": ["Fake response used for deterministic smoke coverage."],
                    "changed_sections": ["AI Draft Smoke Note"],
                    "resolved_question_ids": [item["id"] for item in questions],
                    "suggested_new_nodes": [],
                },
                reader_questions,
                "fake-codex",
                "codex-cli",
                original_body,
            )
        if fake_mode == "non_json":
            raise HTTPException(status_code=502, detail="Codex CLI returned non-JSON output")
        if fake_mode == "timeout":
            raise HTTPException(status_code=504, detail="Codex CLI revision timed out")
        raise HTTPException(status_code=502, detail=f"Fake Codex failure: {fake_mode}")

    try:
        result = run_codex_json(build_ai_revision_instruction(context_json), ai_revision_schema())
    except HTTPException as exc:
        if exc.status_code == 502 and isinstance(exc.detail, str):
            raise HTTPException(status_code=502, detail=summarize_ai_error(exc.detail)) from exc
        raise

    logger.info("Codex CLI revision finished")
    context = json.loads(context_json)
    original_body = (context["draft_body_override"] or context["target"]["body"]).strip()
    return revision_response(result, reader_questions, codex_model_name(), "codex-cli", original_body)


def run_openai_revision(context_json: str, reader_questions: list[dict]) -> dict:
    if not openai_is_configured():
        logger.warning("AI revision rejected because OPENAI_API_KEY is not configured")
        raise HTTPException(
            status_code=503,
            detail="OPENAI_API_KEY is not configured for the local API process",
        )

    try:
        from openai import OpenAI
    except ImportError as exc:
        logger.exception("AI revision failed because the OpenAI package is missing")
        raise HTTPException(
            status_code=503,
            detail="OpenAI Python package is not installed. Run pip install -r backend/requirements.txt",
        ) from exc

    client = OpenAI()
    system_prompt = """
You revise a personal CS learning knowledge base. Return only valid JSON matching the schema.
Do not invent external sources. Preserve Markdown. Preserve the learner's useful structure.
Improve clarity, fill missing reasoning steps, and keep the body suitable for direct saving.
Use Shark Tank Passcode as the quality bar: detailed reasoning, prerequisite vocabulary, examples, common mistakes, and linked review.
Prefer compact patch_ops with exact find text; use revised_body only when a patch would be unsafe.
For replace patch_ops, match and replace the full old block. Do not use a heading-only find string to insert a new section above the old section.
If the target is a node, prefer Standard A. If the target is a quiz, prefer Standard Q.
Keep cs-fundamentals intro-level only; do not dump advanced or project-specific topics there.
Resolve only question IDs that are directly answered in the revised body.
"""

    try:
        response = client.responses.create(
            model=openai_model_name(),
            input=[
                {"role": "system", "content": system_prompt.strip()},
                {"role": "user", "content": context_json},
            ],
            text={
                "format": {
                    "type": "json_schema",
                    "name": "cs_learning_revision",
                    "schema": ai_revision_schema(),
                    "strict": True,
                }
            },
        )
        raw_text = extract_response_text(response)
        result = json.loads(raw_text)
    except Exception as exc:
        logger.exception("OpenAI API revision failed during model call or response parsing")
        raise HTTPException(status_code=502, detail=f"OpenAI API revision failed: {exc}") from exc

    context = json.loads(context_json)
    original_body = (context["draft_body_override"] or context["target"]["body"]).strip()
    return revision_response(result, reader_questions, openai_model_name(), "openai-api", original_body)


def run_ai_job(job_id: int) -> None:
    try:
        add_ai_job_event(job_id, "queued", "AI job picked up by local worker.")
        update_ai_job(job_id, status="solving", stage="context_built", started_at=utc_now())
        with get_conn() as conn:
            row = get_ai_job_or_404(conn, job_id)
            if row["status"] not in {"queued", "solving"}:
                add_ai_job_event(job_id, row["status"], "AI job stopped before context build.", "warning")
                return
            question_ids = json.loads(row["question_ids"] or "[]")
            target, reader_questions = load_ai_target(
                conn,
                row["target_type"],
                row["target_id"],
                question_ids,
            )
            if row["draft_body"].strip():
                target["body"] = row["draft_body"].strip()
            context_json = build_ai_revision_prompt(
                target,
                row["target_type"],
                reader_questions,
                row["instruction"],
                row["draft_body"],
            )
        add_ai_job_event(job_id, "context_built", f"Built prompt with {len(reader_questions)} reader question(s).")

        provider = ai_provider_name()
        update_ai_job(job_id, provider=provider, stage="codex_running" if provider == "codex-cli" else "model_running")
        add_ai_job_event(job_id, "codex_running" if provider == "codex-cli" else "model_running", f"Starting {provider}.")
        if provider == "codex-cli":
            response = run_codex_revision(context_json, reader_questions)
        elif provider == "openai-api":
            response = run_openai_revision(context_json, reader_questions)
        else:
            raise HTTPException(
                status_code=400,
                detail="CS_LEARNING_AI_PROVIDER must be codex-cli or openai-api",
            )

        ensure_job_can_write(job_id)
        revision = response["revision"]
        update_ai_job(
            job_id,
            status="draft_ready",
            stage="draft_ready",
            provider=revision["provider"],
            model=revision["model"],
            result_json=json.dumps(response, ensure_ascii=False),
            completed_at=utc_now(),
        )
        add_ai_job_event(job_id, "draft_ready", "AI draft is ready for human review.")
    except Exception as exc:
        detail = exc.detail if isinstance(exc, HTTPException) else str(exc)
        logger.exception("AI job %s failed", job_id)
        error_text = str(detail)
        error_code = classify_ai_error(error_text)
        try:
            ensure_job_can_write(job_id)
        except HTTPException:
            add_ai_job_event(job_id, "stopped", "AI worker stopped after job state changed.", "warning")
            return
        update_ai_job(job_id, status="failed", stage="failed", error=error_text, error_code=error_code, completed_at=utc_now())
        add_ai_job_event(job_id, "failed", summarize_ai_error(error_text), "error")


@app.get("/api/health")
def health() -> dict:
    recover_stale_ai_jobs()
    return {
        "ok": True,
        "ai": {
            "provider": ai_provider_name(),
            "configured": codex_is_configured() if ai_provider_name() == "codex-cli" else openai_is_configured(),
            "model": codex_model_name() if ai_provider_name() == "codex-cli" else openai_model_name(),
            "codex_cli": codex_cli_path(),
            "codex_model_provider": codex_model_provider_name(),
            "codex_base_url": codex_base_url() if ai_provider_name() == "codex-cli" else "",
            "codex_home": str(codex_job_home()) if ai_provider_name() == "codex-cli" else "",
        },
    }


@app.get("/api/ai/preflight")
def ai_preflight(run_model: bool = False) -> dict:
    provider = ai_provider_name()
    if provider == "codex-cli":
        return {"provider": provider, **codex_preflight(run_model=run_model)}
    return {
        "provider": provider,
        "ok": openai_is_configured(),
        "checks": {"openai_api_key": openai_is_configured()},
        "model": openai_model_name(),
        "ran_model": False,
        "message": (
            "OpenAI API key is configured."
            if openai_is_configured()
            else "OPENAI_API_KEY is not configured for the local API process."
        ),
    }


@app.get("/api/system/metrics")
def system_metrics() -> dict:
    recover_stale_ai_jobs()
    with get_conn() as conn:
        counts = {
            "nodes": conn.execute("SELECT COUNT(*) FROM nodes").fetchone()[0],
            "quizzes": conn.execute("SELECT COUNT(*) FROM quizzes").fetchone()[0],
            "open_questions": conn.execute("SELECT COUNT(*) FROM reader_questions WHERE status = 'open'").fetchone()[0],
            "active_ai_jobs": conn.execute("SELECT COUNT(*) FROM ai_jobs WHERE status IN ('queued', 'solving', 'draft_ready', 'failed')").fetchone()[0],
            "failed_ai_jobs": conn.execute("SELECT COUNT(*) FROM ai_jobs WHERE status = 'failed'").fetchone()[0],
        }
    generated_dir = ROOT / "generated"
    return {
        "counts": counts,
        "storage": {
            "content_bytes": directory_size(CONTENT_ROOT),
            "db_bytes": DB_PATH.stat().st_size if DB_PATH.exists() else 0,
            "generated_bytes": directory_size(generated_dir),
        },
        "paths": {
            "content": str(CONTENT_ROOT),
            "db": str(DB_PATH),
            "generated": str(generated_dir),
        },
        "ai": codex_preflight(run_model=False) if ai_provider_name() == "codex-cli" else {
            "ok": openai_is_configured(),
            "message": "OpenAI API key is configured." if openai_is_configured() else "OPENAI_API_KEY is not configured.",
        },
    }


@app.post("/api/ai/revise")
def revise_with_ai(payload: AiReviseRequest) -> dict:
    provider = ai_provider_name()
    logger.info(
        "AI revision requested provider=%s target_type=%s target_id=%s question_ids=%s",
        provider,
        payload.target_type,
        payload.target_id,
        payload.question_ids,
    )

    with get_conn() as conn:
        target, reader_questions = load_ai_target(
            conn,
            payload.target_type,
            payload.target_id,
            payload.question_ids,
        )
    logger.info("AI revision context loaded with %s open reader questions", len(reader_questions))

    if payload.draft_body.strip():
        target["body"] = payload.draft_body.strip()

    context_json = build_ai_revision_prompt(
        target,
        payload.target_type,
        reader_questions,
        payload.instruction,
        payload.draft_body,
    )
    if provider == "codex-cli":
        response = run_codex_revision(context_json, reader_questions)
    elif provider == "openai-api":
        response = run_openai_revision(context_json, reader_questions)
    else:
        raise HTTPException(
            status_code=400,
            detail="CS_LEARNING_AI_PROVIDER must be codex-cli or openai-api",
        )

    resolved_question_ids = response["revision"]["resolved_question_ids"]
    logger.info(
        "AI revision succeeded provider=%s target_type=%s target_id=%s resolved_question_ids=%s",
        provider,
        payload.target_type,
        payload.target_id,
        resolved_question_ids,
    )
    return response


@app.post("/api/ai/jobs")
def create_ai_job(payload: AiJobCreate, background_tasks: BackgroundTasks) -> dict:
    question_ids = list(dict.fromkeys(payload.question_ids))
    now = utc_now()

    with get_conn() as conn:
        if payload.target_type == "node":
            exists = conn.execute("SELECT 1 FROM nodes WHERE slug = ?", (payload.target_id,)).fetchone()
        else:
            exists = conn.execute("SELECT 1 FROM quizzes WHERE id = ?", (payload.target_id,)).fetchone()
        if not exists:
            raise HTTPException(status_code=404, detail="target not found")

        question = payload.question.strip()
        if question:
            cursor = conn.execute(
                """
                INSERT INTO reader_questions (target_type, target_id, question, status, created_at)
                VALUES (?, ?, ?, 'queued', ?)
                """,
                (payload.target_type, payload.target_id, question, now),
            )
            question_ids.append(cursor.lastrowid)

        base_body = payload.draft_body.strip()
        if not base_body:
            if payload.target_type == "node":
                body_row = conn.execute("SELECT body FROM nodes WHERE slug = ?", (payload.target_id,)).fetchone()
            else:
                body_row = conn.execute("SELECT body FROM quizzes WHERE id = ?", (payload.target_id,)).fetchone()
            base_body = body_row["body"] if body_row else ""

        cursor = conn.execute(
            """
            INSERT INTO ai_jobs (
                target_type, target_id, question_ids, provider, model, status, stage,
                instruction, draft_body, created_at, updated_at, base_body_hash
            )
            VALUES (?, ?, ?, ?, ?, 'queued', 'queued', ?, ?, ?, ?, ?)
            """,
            (
                payload.target_type,
                payload.target_id,
                json.dumps(question_ids),
                ai_provider_name(),
                codex_model_name() if ai_provider_name() == "codex-cli" else openai_model_name(),
                payload.instruction,
                payload.draft_body,
                now,
                now,
                body_hash(base_body),
            ),
        )
        conn.commit()
        row = conn.execute("SELECT * FROM ai_jobs WHERE id = ?", (cursor.lastrowid,)).fetchone()

    add_ai_job_event(row["id"], "queued", "AI job created. Reader questions remain open until draft is applied.")
    background_tasks.add_task(run_ai_job, row["id"])
    return {"job": row_to_ai_job(row)}


@app.get("/api/ai/jobs")
def list_ai_jobs(
    target_type: Optional[str] = None,
    target_id: Optional[str] = None,
    status: Optional[str] = None,
) -> dict:
    query = "SELECT * FROM ai_jobs WHERE 1 = 1"
    params: list[str] = []
    if target_type:
        if target_type not in {"node", "quiz"}:
            raise HTTPException(status_code=400, detail="target_type must be node or quiz")
        query += " AND target_type = ?"
        params.append(target_type)
    if target_id:
        query += " AND target_id = ?"
        params.append(target_id)
    if status == "active":
        query += " AND status IN ('queued', 'solving', 'draft_ready', 'failed')"
    elif status:
        query += " AND status = ?"
        params.append(status)
    query += " ORDER BY updated_at DESC, id DESC LIMIT 50"
    with get_conn() as conn:
        rows = conn.execute(query, params).fetchall()
    return {"jobs": [row_to_ai_job(row) for row in rows]}


@app.get("/api/ai/jobs/{job_id}")
def get_ai_job(job_id: int) -> dict:
    with get_conn() as conn:
        row = get_ai_job_or_404(conn, job_id)
    return {"job": row_to_ai_job(row)}


@app.get("/api/ai/jobs/{job_id}/events")
def list_ai_job_events(job_id: int) -> dict:
    with get_conn() as conn:
        get_ai_job_or_404(conn, job_id)
        rows = conn.execute(
            """
            SELECT id, job_id, level, stage, message, created_at
            FROM ai_job_events
            WHERE job_id = ?
            ORDER BY id
            """,
            (job_id,),
        ).fetchall()
    return {"events": [dict(row) for row in rows]}


@app.post("/api/ai/jobs/{job_id}/apply")
def apply_ai_job(job_id: int, payload: AiJobApply) -> dict:
    now = utc_now()
    with get_conn() as conn:
        row = get_ai_job_or_404(conn, job_id)
        if row["status"] != "draft_ready":
            raise HTTPException(status_code=400, detail="AI job is not draft_ready")
        if row["target_type"] == "node":
            target_row = conn.execute("SELECT * FROM nodes WHERE slug = ?", (row["target_id"],)).fetchone()
        else:
            target_row = conn.execute("SELECT * FROM quizzes WHERE id = ?", (row["target_id"],)).fetchone()
        if not target_row:
            raise HTTPException(status_code=404, detail="AI job target not found")
        if row["base_body_hash"] and body_hash(target_row["body"]) != row["base_body_hash"]:
            raise HTTPException(status_code=409, detail="Target Markdown changed after this draft was created")

        content_path = CONTENT_ROOT / target_row["path"]
        with restore_file_on_failure(content_path):
            write_markdown_body(content_path, payload.body)
            if row["target_type"] == "node":
                update_node_body_in_conn(conn, target_row, payload.body.strip())
            else:
                update_quiz_body_in_conn(conn, target_row, payload.body.strip())
            question_ids = json.loads(row["question_ids"] or "[]")
            if question_ids:
                placeholders = ",".join("?" for _ in question_ids)
                conn.execute(
                    f"""
                    UPDATE reader_questions
                    SET status = 'resolved',
                        resolved_at = ?,
                        resolution_note = ?
                    WHERE id IN ({placeholders})
                    """,
                    [now, "Resolved by applied AI job", *question_ids],
                )
            conn.execute(
                """
                UPDATE ai_jobs
                SET status = 'applied',
                    stage = 'applied',
                    updated_at = ?,
                    completed_at = ?
                WHERE id = ?
                """,
                (now, now, job_id),
            )
            conn.commit()
        updated = conn.execute("SELECT * FROM ai_jobs WHERE id = ?", (job_id,)).fetchone()
    add_ai_job_event(job_id, "applied", "Human applied the draft and resolved linked questions.")
    return {"job": row_to_ai_job(updated)}


@app.post("/api/ai/jobs/{job_id}/cancel")
def cancel_ai_job(job_id: int) -> dict:
    now = utc_now()
    with get_conn() as conn:
        row = get_ai_job_or_404(conn, job_id)
        if row["status"] in {"draft_ready", "failed", "cancelled", "applied", "rejected", "retried"}:
            raise HTTPException(status_code=400, detail=f"cannot cancel {row['status']} job")
        conn.execute(
            """
            UPDATE ai_jobs
            SET status = 'cancelled',
                stage = 'cancelled',
                updated_at = ?,
                completed_at = ?
            WHERE id = ?
            """,
            (now, now, job_id),
        )
        conn.commit()
        updated = conn.execute("SELECT * FROM ai_jobs WHERE id = ?", (job_id,)).fetchone()
    add_ai_job_event(job_id, "cancelled", "Human cancelled the job. Linked questions remain open.", "warning")
    return {"job": row_to_ai_job(updated)}


@app.post("/api/ai/jobs/{job_id}/reject")
def reject_ai_job(job_id: int, payload: AiJobReject) -> dict:
    now = utc_now()
    with get_conn() as conn:
        row = get_ai_job_or_404(conn, job_id)
        if row["status"] != "draft_ready":
            raise HTTPException(status_code=400, detail=f"cannot reject {row['status']} job")
        note = payload.reason.strip() or "Draft rejected by human review"
        conn.execute(
            """
            UPDATE ai_jobs
            SET status = 'rejected',
                stage = 'rejected',
                error = ?,
                updated_at = ?,
                completed_at = ?
            WHERE id = ?
            """,
            (note, now, now, job_id),
        )
        conn.commit()
        updated = conn.execute("SELECT * FROM ai_jobs WHERE id = ?", (job_id,)).fetchone()
    add_ai_job_event(job_id, "rejected", "Human rejected the draft. Linked questions remain open.", "warning")
    return {"job": row_to_ai_job(updated)}


@app.post("/api/ai/jobs/{job_id}/retry")
def retry_ai_job(job_id: int, background_tasks: BackgroundTasks) -> dict:
    now = utc_now()
    with get_conn() as conn:
        row = get_ai_job_or_404(conn, job_id)
        if row["status"] != "failed":
            raise HTTPException(status_code=400, detail=f"cannot retry {row['status']} job")

        conn.execute(
            """
            UPDATE ai_jobs
            SET status = 'retried',
                stage = 'retried',
                updated_at = ?,
                completed_at = ?
            WHERE id = ?
            """,
            (now, now, job_id),
        )

        cursor = conn.execute(
            """
            INSERT INTO ai_jobs (
                target_type, target_id, question_ids, provider, model, status, stage,
                instruction, draft_body, created_at, updated_at, retry_of, attempt, base_body_hash
            )
            VALUES (?, ?, ?, ?, ?, 'queued', 'queued', ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                row["target_type"],
                row["target_id"],
                row["question_ids"],
                ai_provider_name(),
                codex_model_name() if ai_provider_name() == "codex-cli" else openai_model_name(),
                row["instruction"],
                row["draft_body"],
                now,
                now,
                row["id"],
                int(row["attempt"] or 1) + 1,
                row["base_body_hash"],
            ),
        )
        conn.commit()
        new_row = conn.execute("SELECT * FROM ai_jobs WHERE id = ?", (cursor.lastrowid,)).fetchone()

    add_ai_job_event(new_row["id"], "queued", f"Retry created from job #{job_id}.")
    background_tasks.add_task(run_ai_job, new_row["id"])
    return {"job": row_to_ai_job(new_row)}


@app.get("/api/nodes")
def list_nodes(
    area: Optional[str] = None,
    visibility: Optional[str] = None,
) -> dict:
    query = "SELECT * FROM nodes WHERE 1 = 1"
    params: list[str] = []
    if area:
        query += " AND area = ?"
        params.append(area)
    if visibility:
        query += " AND visibility = ?"
        params.append(visibility)
    query += " ORDER BY area, track, display_order, title"

    with get_conn() as conn:
        rows = conn.execute(query, params).fetchall()
    return {"nodes": [row_to_node(row) for row in rows]}


@app.get("/api/areas")
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


@app.post("/api/nodes")
def create_node(payload: NodeCreate) -> dict:
    title = payload.title.strip()
    if not title:
        raise HTTPException(status_code=400, detail="title cannot be empty")
    area = slugify(payload.area)
    track = slugify(payload.track)
    slug_base = slugify(title)
    tags = [slugify(tag) for tag in payload.tags if tag.strip()]
    summary = payload.summary.strip()

    with get_conn() as conn:
        slug = slug_base
        counter = 2
        while conn.execute("SELECT 1 FROM nodes WHERE slug = ?", (slug,)).fetchone() or (CONTENT_ROOT / "nodes" / area / f"{slug}.md").exists():
            slug = f"{slug_base}-{counter}"
            counter += 1

        node_dir = CONTENT_ROOT / "nodes" / area
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
                    payload.visibility,
                    payload.status.strip() or "draft",
                    payload.order,
                ),
                encoding="utf-8",
            )
            upsert_node_file_in_conn(conn, node_path)
            conn.commit()

    return get_node(slug)


@app.get("/api/graph")
def get_graph_root(page: int = Query(default=1, ge=1)) -> dict:
    with get_conn() as conn:
        payload = root_graph_payload(conn, page)
        conn.commit()
        return payload


@app.get("/api/graph/area/{area}")
def get_graph_area(area: str, page: int = Query(default=1, ge=1)) -> dict:
    with get_conn() as conn:
        payload = area_graph_payload(conn, area, page)
        conn.commit()
        return payload


@app.get("/api/graph/track/{area}/{track}")
def get_graph_track(area: str, track: str, page: int = Query(default=1, ge=1)) -> dict:
    with get_conn() as conn:
        payload = track_graph_payload(conn, area, track, page)
        conn.commit()
        return payload


@app.get("/api/graph/node/{slug}")
def get_graph_node(slug: str, page: int = Query(default=1, ge=1)) -> dict:
    with get_conn() as conn:
        payload = node_graph_payload(conn, slug, page)
        conn.commit()
        return payload


@app.get("/api/nodes/{slug}")
def get_node(slug: str) -> dict:
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
    node["tags"] = tags
    node["links"] = links
    node["sources"] = sources
    node["open_question_count"] = open_question_count
    return {"node": node}


@app.post("/api/nodes/{slug}/read")
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
                if elapsed_seconds < 0:
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
    return get_node(slug)


@app.put("/api/nodes/{slug}/body")
def update_node_body(slug: str, payload: BodyUpdate) -> dict:
    body = payload.body.strip()
    if not body:
        raise HTTPException(status_code=400, detail="body cannot be empty")

    with get_conn() as conn:
        row = conn.execute("SELECT * FROM nodes WHERE slug = ?", (slug,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Node not found")

        content_path = CONTENT_ROOT / row["path"]
        if not content_path.is_file():
            raise HTTPException(status_code=404, detail="Node source file not found")

        with restore_file_on_failure(content_path):
            write_markdown_body(content_path, body)
            update_node_body_in_conn(conn, row, body)
            conn.commit()

    return get_node(slug)


@app.post("/api/nodes/{slug}/trash")
def trash_node(slug: str) -> dict:
    with get_conn() as conn:
        row = conn.execute("SELECT * FROM nodes WHERE slug = ?", (slug,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Node not found")
        content_path = CONTENT_ROOT / row["path"]
        if not content_path.is_file():
            raise HTTPException(status_code=404, detail="Node source file not found")
        with restore_file_on_failure(content_path):
            previous_visibility = read_markdown_frontmatter_value(content_path, "visibility")
            if previous_visibility and previous_visibility != "trash":
                update_markdown_frontmatter_value(content_path, "previous_visibility", previous_visibility)
            update_markdown_frontmatter_value(content_path, "visibility", "trash")
            upsert_node_file_in_conn(conn, content_path)
            conn.commit()
    return get_node(slug)


@app.post("/api/nodes/{slug}/restore")
def restore_node(slug: str) -> dict:
    with get_conn() as conn:
        row = conn.execute("SELECT * FROM nodes WHERE slug = ?", (slug,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Node not found")
        content_path = CONTENT_ROOT / row["path"]
        if not content_path.is_file():
            raise HTTPException(status_code=404, detail="Node source file not found")
        with restore_file_on_failure(content_path):
            previous_visibility = read_markdown_frontmatter_value(content_path, "previous_visibility") or "support"
            update_markdown_frontmatter_value(content_path, "visibility", previous_visibility)
            remove_markdown_frontmatter_key(content_path, "previous_visibility")
            upsert_node_file_in_conn(conn, content_path)
            conn.commit()
    return get_node(slug)


@app.delete("/api/nodes/{slug}")
def permanently_delete_node(slug: str) -> dict:
    with get_conn() as conn:
        row = conn.execute("SELECT * FROM nodes WHERE slug = ?", (slug,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Node not found")
        if row["visibility"] != "trash":
            raise HTTPException(status_code=400, detail="Move node to Trashbin before permanent delete")
        content_path = CONTENT_ROOT / row["path"]
        with stage_file_delete(content_path):
            conn.execute("DELETE FROM links WHERE target_slug = ?", (slug,))
            conn.execute("DELETE FROM nodes WHERE slug = ?", (slug,))
            conn.execute("DELETE FROM node_fts WHERE slug = ?", (slug,))
            conn.execute("DELETE FROM graph_cache")
            conn.commit()
    return {"ok": True}


@app.get("/api/search")
def search(q: str = Query(default="", min_length=0)) -> dict:
    term = q.strip()
    with get_conn() as conn:
        if term:
            fts_query = build_fts_query(term)
            rows = []
            if fts_query:
                try:
                    rows = conn.execute(
                        """
                        SELECT n.*, bm25(node_fts) AS rank
                        FROM node_fts
                        JOIN nodes n ON n.slug = node_fts.slug
                        WHERE node_fts MATCH ?
                          AND n.visibility != 'trash'
                        ORDER BY rank, n.title
                        LIMIT 50
                        """,
                        (fts_query,),
                    ).fetchall()
                except sqlite3.OperationalError:
                    rows = []
            if not rows:
                pattern = like_term(term)
                rows = conn.execute(
                    """
                    SELECT DISTINCT n.*
                    FROM nodes n
                    LEFT JOIN node_tags nt ON nt.node_slug = n.slug
                    WHERE n.visibility != 'trash'
                      AND (
                        n.title LIKE ? ESCAPE '\\'
                        OR n.summary LIKE ? ESCAPE '\\'
                        OR n.body LIKE ? ESCAPE '\\'
                        OR nt.tag_name LIKE ? ESCAPE '\\'
                      )
                    ORDER BY n.area, n.title
                    LIMIT 50
                    """,
                    (pattern, pattern, pattern, pattern),
                ).fetchall()
        else:
            rows = conn.execute(
                "SELECT * FROM nodes WHERE visibility != 'trash' ORDER BY area, track, display_order, title LIMIT 50"
            ).fetchall()

    return {"nodes": [row_to_node(row) for row in rows]}


@app.get("/api/areas/{area}/tracks")
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


@app.get("/api/quizzes")
def list_quizzes(
    area: Optional[str] = None,
    visibility: Optional[str] = None,
) -> dict:
    query = "SELECT * FROM quizzes WHERE 1 = 1"
    params: list[str] = []
    if area:
        query += " AND area = ?"
        params.append(area)
    if visibility:
        query += " AND visibility = ?"
        params.append(visibility)
    query += " ORDER BY area, difficulty, title"

    with get_conn() as conn:
        rows = conn.execute(query, params).fetchall()
    return {"quizzes": [row_to_quiz(row) for row in rows]}


@app.get("/api/quizzes/{quiz_id}")
def get_quiz(quiz_id: str) -> dict:
    with get_conn() as conn:
        row = conn.execute("SELECT * FROM quizzes WHERE id = ?", (quiz_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Quiz not found")
        tags = [
            item["tag_name"]
            for item in conn.execute(
                "SELECT tag_name FROM quiz_tags WHERE quiz_id = ? ORDER BY tag_name",
                (quiz_id,),
            ).fetchall()
        ]
        linked_nodes = [
            {"slug": item["node_slug"], "kind": item["kind"], "title": item["title"]}
            for item in conn.execute(
                """
                SELECT ql.node_slug, ql.kind, COALESCE(n.title, ql.node_slug) AS title
                FROM quiz_links ql
                LEFT JOIN nodes n ON n.slug = ql.node_slug
                WHERE ql.quiz_id = ?
                ORDER BY ql.kind, ql.node_slug
                """,
                (quiz_id,),
            ).fetchall()
        ]
        sources = [
            {
                "source": item["source"],
                "source_type": item["source_type"],
                "note": item["note"],
            }
            for item in conn.execute(
                "SELECT source, source_type, note FROM quiz_sources WHERE quiz_id = ? ORDER BY id",
                (quiz_id,),
            ).fetchall()
        ]
        open_question_count = conn.execute(
            """
            SELECT COUNT(*) AS count
            FROM reader_questions
            WHERE target_type = 'quiz'
              AND target_id = ?
              AND status = 'open'
            """,
            (quiz_id,),
        ).fetchone()["count"]

    quiz = row_to_quiz(row)
    quiz["body"] = row["body"]
    quiz["tags"] = tags
    quiz["linked_nodes"] = linked_nodes
    quiz["sources"] = sources
    quiz["open_question_count"] = open_question_count
    return {"quiz": quiz}


@app.put("/api/quizzes/{quiz_id}/body")
def update_quiz_body(quiz_id: str, payload: BodyUpdate) -> dict:
    body = payload.body.strip()
    if not body:
        raise HTTPException(status_code=400, detail="body cannot be empty")

    with get_conn() as conn:
        row = conn.execute("SELECT * FROM quizzes WHERE id = ?", (quiz_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Quiz not found")

        content_path = CONTENT_ROOT / row["path"]
        if not content_path.is_file():
            raise HTTPException(status_code=404, detail="Quiz source file not found")

        with restore_file_on_failure(content_path):
            write_markdown_body(content_path, body)
            update_quiz_body_in_conn(conn, row, body)
            conn.commit()

    return get_quiz(quiz_id)


@app.get("/api/quiz-search")
def search_quizzes(q: str = Query(default="", min_length=0)) -> dict:
    term = q.strip()
    with get_conn() as conn:
        if term:
            fts_query = build_fts_query(term)
            rows = []
            if fts_query:
                try:
                    rows = conn.execute(
                        """
                        SELECT q.*, bm25(quiz_fts) AS rank
                        FROM quiz_fts
                        JOIN quizzes q ON q.id = quiz_fts.id
                        WHERE quiz_fts MATCH ?
                        ORDER BY rank, q.title
                        LIMIT 50
                        """,
                        (fts_query,),
                    ).fetchall()
                except sqlite3.OperationalError:
                    rows = []
            if not rows:
                pattern = like_term(term)
                rows = conn.execute(
                    """
                    SELECT DISTINCT q.*
                    FROM quizzes q
                    LEFT JOIN quiz_tags qt ON qt.quiz_id = q.id
                    WHERE q.title LIKE ? ESCAPE '\\'
                       OR q.summary LIKE ? ESCAPE '\\'
                       OR q.body LIKE ? ESCAPE '\\'
                       OR qt.tag_name LIKE ? ESCAPE '\\'
                    ORDER BY q.area, q.title
                    LIMIT 50
                    """,
                    (pattern, pattern, pattern, pattern),
                ).fetchall()
        else:
            rows = conn.execute(
                "SELECT * FROM quizzes ORDER BY area, difficulty, title LIMIT 50"
            ).fetchall()

    return {"quizzes": [row_to_quiz(row) for row in rows]}


@app.get("/api/reader-questions")
def list_reader_questions(
    target_type: Optional[str] = None,
    target_id: Optional[str] = None,
    status: str = "open",
) -> dict:
    query = "SELECT * FROM reader_questions WHERE 1 = 1"
    params: list[str] = []
    if target_type:
        if target_type not in {"node", "quiz"}:
            raise HTTPException(status_code=400, detail="target_type must be node or quiz")
        query += " AND target_type = ?"
        params.append(target_type)
    if target_id:
        query += " AND target_id = ?"
        params.append(target_id)
    if status == "active":
        query += " AND status IN ('open', 'queued', 'solving', 'draft_ready', 'failed')"
    elif status:
        query += " AND status = ?"
        params.append(status)
    query += " ORDER BY created_at DESC, id DESC"

    with get_conn() as conn:
        rows = conn.execute(query, params).fetchall()
    return {"questions": [row_to_reader_question(row) for row in rows]}


@app.post("/api/reader-questions")
def create_reader_question(payload: ReaderQuestionCreate) -> dict:
    question = payload.question.strip()
    if not question:
        raise HTTPException(status_code=400, detail="question cannot be empty")

    now = datetime.now(timezone.utc).isoformat()
    with get_conn() as conn:
        if payload.target_type == "node":
            exists = conn.execute(
                "SELECT 1 FROM nodes WHERE slug = ?", (payload.target_id,)
            ).fetchone()
        else:
            exists = conn.execute(
                "SELECT 1 FROM quizzes WHERE id = ?", (payload.target_id,)
            ).fetchone()
        if not exists:
            raise HTTPException(status_code=404, detail="target not found")

        cursor = conn.execute(
            """
            INSERT INTO reader_questions (target_type, target_id, question, status, created_at)
            VALUES (?, ?, ?, 'open', ?)
            """,
            (payload.target_type, payload.target_id, question, now),
        )
        conn.commit()
        row = conn.execute(
            "SELECT * FROM reader_questions WHERE id = ?", (cursor.lastrowid,)
        ).fetchone()

    return {"question": row_to_reader_question(row)}


@app.post("/api/reader-questions/{question_id}/resolve")
def resolve_reader_question(question_id: int, payload: ReaderQuestionResolve) -> dict:
    now = datetime.now(timezone.utc).isoformat()
    with get_conn() as conn:
        row = conn.execute(
            "SELECT * FROM reader_questions WHERE id = ?", (question_id,)
        ).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="reader question not found")

        conn.execute(
            """
            UPDATE reader_questions
            SET status = 'resolved',
                resolved_at = ?,
                resolution_note = ?
            WHERE id = ?
            """,
            (now, payload.resolution_note, question_id),
        )
        conn.commit()
        updated = conn.execute(
            "SELECT * FROM reader_questions WHERE id = ?", (question_id,)
        ).fetchone()

    return {"question": row_to_reader_question(updated)}


@app.post("/api/reader-questions/{question_id}/dismiss")
def dismiss_reader_question(question_id: int, payload: ReaderQuestionResolve) -> dict:
    now = utc_now()
    with get_conn() as conn:
        row = conn.execute(
            "SELECT * FROM reader_questions WHERE id = ?", (question_id,)
        ).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="reader question not found")

        conn.execute(
            """
            UPDATE reader_questions
            SET status = 'dismissed',
                resolved_at = ?,
                resolution_note = ?
            WHERE id = ?
            """,
            (now, payload.resolution_note, question_id),
        )
        conn.commit()
        updated = conn.execute(
            "SELECT * FROM reader_questions WHERE id = ?", (question_id,)
        ).fetchone()

    return {"question": row_to_reader_question(updated)}


@app.delete("/api/reader-questions/{question_id}")
def delete_reader_question(question_id: int) -> dict:
    with get_conn() as conn:
        row = conn.execute(
            "SELECT * FROM reader_questions WHERE id = ?", (question_id,)
        ).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="reader question not found")
        conn.execute("DELETE FROM reader_questions WHERE id = ?", (question_id,))
        conn.commit()
    return {"ok": True}
