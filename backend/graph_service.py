from __future__ import annotations

import json
import re
import sqlite3
from datetime import datetime, timezone

from fastapi import HTTPException


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


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def label_for_area(area: str) -> str:
    return AREA_LABELS.get(area, area.replace("-", " ").title())


def label_for_track(track: str) -> str:
    return TRACK_LABELS.get(track, track.replace("-", " ").title())


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
            label_for_area(area),
            f"area - {counts[area]} nodes",
            "Open this knowledge domain.",
            counts[area],
            f"/graph/area/{area}",
        )
        for area in [*areas, *extras]
    ]
    center = graph_node("root", "workbench", "Workbench", "root - learning map", "Choose a knowledge domain.", len(children))
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
    label = label_for_area(area)
    children = [
        graph_node(
            "track",
            row["track"],
            label_for_track(row["track"]),
            f"track - {row['count']} nodes",
            f"Continue through {label}.",
            row["count"],
            f"/graph/track/{area}/{row['track']}",
        )
        for row in rows
    ]
    root = graph_node("root", "workbench", "Workbench", "root", "Back to all domains.", len(children), "/graph")
    center = graph_node("area", area, label, f"area - {sum(row['count'] for row in rows)} nodes", "Choose a track.", len(children))
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
    area_label = label_for_area(area)
    track_label = label_for_track(track)
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
        graph_node("track", track, track_label, f"track - {len(rows)} nodes", "Choose a node.", len(rows)),
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
    area_label = label_for_area(row["area"])
    track_label = label_for_track(row["track"])
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
