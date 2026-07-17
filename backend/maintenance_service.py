from __future__ import annotations

import hashlib
import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path


PACKAGE_FORMAT_VERSION = "1"
LLMWIKI_FORMAT_VERSION = "1"


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def schema_meta(conn: sqlite3.Connection) -> dict:
    rows = conn.execute("SELECT key, value, updated_at FROM schema_meta ORDER BY key").fetchall()
    return {row["key"]: {"value": row["value"], "updated_at": row["updated_at"]} for row in rows}


def content_manifest(conn: sqlite3.Connection, content_root: Path) -> dict:
    files = []
    for root_name in ["nodes", "quizzes", "assets"]:
        root = content_root / root_name
        if not root.exists():
            continue
        for path in sorted(root.rglob("*")):
            if not path.is_file():
                continue
            stat = path.stat()
            files.append(
                {
                    "path": path.relative_to(content_root).as_posix(),
                    "size_bytes": stat.st_size,
                    "mtime_ns": stat.st_mtime_ns,
                    "sha256": sha256_file(path),
                }
            )
    counts = {
        "nodes": conn.execute("SELECT COUNT(*) FROM nodes").fetchone()[0],
        "quizzes": conn.execute("SELECT COUNT(*) FROM quizzes").fetchone()[0],
        "assets": sum(1 for item in files if item["path"].startswith("assets/")),
        "files": len(files),
    }
    return {
        "package_format_version": PACKAGE_FORMAT_VERSION,
        "generated_at": utc_now(),
        "content_root": str(content_root),
        "counts": counts,
        "files": files,
    }


def _rows_to_list(rows: list[sqlite3.Row]) -> list[dict]:
    return [dict(row) for row in rows]


def _group_values(rows: list[sqlite3.Row], key_column: str, value_column: str) -> dict[str, list[str]]:
    grouped: dict[str, list[str]] = {}
    for row in rows:
        grouped.setdefault(row[key_column], []).append(row[value_column])
    return grouped


def llmwiki_pack(conn: sqlite3.Connection, content_root: Path) -> dict:
    manifest = content_manifest(conn, content_root)
    nodes = _rows_to_list(
        conn.execute(
            """
            SELECT slug, title, area, track, display_order, status, visibility, summary, path, updated_at
            FROM nodes
            ORDER BY area, track, display_order, title
            """
        ).fetchall()
    )
    quizzes = _rows_to_list(
        conn.execute(
            """
            SELECT id, title, area, display_order, status, visibility, difficulty, summary, path, weight, updated_at
            FROM quizzes
            ORDER BY area, display_order, title
            """
        ).fetchall()
    )

    node_tags = _group_values(
        conn.execute(
            """
            SELECT node_slug AS slug, tag_name AS tag
            FROM node_tags
            ORDER BY node_slug, tag_name
            """
        ).fetchall(),
        "slug",
        "tag",
    )
    quiz_tags = _group_values(
        conn.execute(
            """
            SELECT quiz_id AS id, tag_name AS tag
            FROM quiz_tags
            ORDER BY quiz_id, tag_name
            """
        ).fetchall(),
        "id",
        "tag",
    )
    node_links = _group_values(
        conn.execute(
            """
            SELECT source_slug AS slug, kind || ':' || target_slug AS link
            FROM links
            ORDER BY source_slug, kind, target_slug
            """
        ).fetchall(),
        "slug",
        "link",
    )
    quiz_links = _group_values(
        conn.execute(
            """
            SELECT quiz_id AS id, kind || ':' || node_slug AS link
            FROM quiz_links
            ORDER BY quiz_id, kind, node_slug
            """
        ).fetchall(),
        "id",
        "link",
    )
    source_rows = conn.execute(
        """
        SELECT 'node' AS target_type, node_slug AS target_id, source, source_type, note
        FROM sources
        UNION ALL
        SELECT 'quiz' AS target_type, quiz_id AS target_id, source, source_type, note
        FROM quiz_sources
        ORDER BY target_type, target_id, source
        """
    ).fetchall()
    sources: dict[tuple[str, str], list[dict]] = {}
    for row in source_rows:
        sources.setdefault((row["target_type"], row["target_id"]), []).append(
            {"source": row["source"], "source_type": row["source_type"], "note": row["note"]}
        )

    file_hashes = {item["path"]: item["sha256"] for item in manifest["files"]}
    node_items = [
        {
            **node,
            "type": "node",
            "id": node["slug"],
            "tags": node_tags.get(node["slug"], []),
            "links": node_links.get(node["slug"], []),
            "sources": sources.get(("node", node["slug"]), []),
            "sha256": file_hashes.get(node["path"], ""),
        }
        for node in nodes
    ]
    quiz_items = [
        {
            **quiz,
            "type": "quiz",
            "id": quiz["id"],
            "tags": quiz_tags.get(quiz["id"], []),
            "links": quiz_links.get(quiz["id"], []),
            "sources": sources.get(("quiz", quiz["id"]), []),
            "sha256": file_hashes.get(quiz["path"], ""),
        }
        for quiz in quizzes
    ]

    asset_files = [item for item in manifest["files"] if item["path"].startswith("assets/")]
    markdown_files = [item for item in manifest["files"] if item["path"].endswith(".md")]

    return {
        "llmwiki_format_version": LLMWIKI_FORMAT_VERSION,
        "package_format_version": PACKAGE_FORMAT_VERSION,
        "generated_at": utc_now(),
        "profile": "local-llmwiki-pack",
        "content_root": str(content_root),
        "output": {
            "default_path": "generated/exports/llmwiki-pack.json",
            "write_trigger": "GET /api/llmwiki/export?write=true",
            "preview_trigger": "GET /api/llmwiki/export",
        },
        "usage": {
            "entrypoint": "System health > LLM Wiki pack > Export LLM Wiki pack",
            "purpose": "On-demand LLM-readable index of Markdown package items, hashes, links, sources, and asset references.",
            "write_policy": "Read-only export. Accepted imports or repairs must flow through ContentWriteService.",
        },
        "memory_policy": {
            "includes_full_body": False,
            "asset_policy": "Assets are referenced by path and hash; binary/image bytes are never inlined.",
            "loading": "Generated on demand from SQLite rows and file hashes; no resident LLMwiki index is kept in memory.",
        },
        "counts": {
            **manifest["counts"],
            "items": len(node_items) + len(quiz_items),
            "markdown_files": len(markdown_files),
            "asset_references": len(asset_files),
        },
        "report": {
            "added": 0,
            "updated": 0,
            "skipped": 0,
            "failed": 0,
            "stale": 0,
            "repaired": 0,
            "exported_items": len(node_items) + len(quiz_items),
            "exported_files": len(manifest["files"]),
            "body_fields_omitted": len(node_items) + len(quiz_items),
            "warnings": [
                "This is a read-only LLMwiki projection. Import and repair workflows are intentionally not enabled yet.",
                "Use file paths and hashes to request selected Markdown bodies instead of loading the full corpus into an LLM context.",
            ],
        },
        "items": [*node_items, *quiz_items],
        "files": manifest["files"],
    }


def repair_report(conn: sqlite3.Connection, content_root: Path) -> dict:
    issues: list[dict] = []
    for table, id_column, label in [("nodes", "slug", "node"), ("quizzes", "id", "quiz")]:
        rows = conn.execute(f"SELECT {id_column} AS id, path FROM {table}").fetchall()
        for row in rows:
            path = content_root / row["path"]
            if not path.is_file():
                issues.append(
                    {
                        "severity": "error",
                        "kind": "missing_source_file",
                        "target_type": label,
                        "target_id": row["id"],
                        "path": row["path"],
                    }
                )

    tracked_paths = {
        row["path"]
        for row in conn.execute(
            """
            SELECT path FROM nodes
            UNION
            SELECT path FROM quizzes
            """
        ).fetchall()
    }
    for row in conn.execute("SELECT path, target_type, target_id, sha256 FROM content_files WHERE status = 'indexed'").fetchall():
        path = content_root / row["path"]
        if row["path"] not in tracked_paths:
            issues.append(
                {
                    "severity": "warning",
                    "kind": "stale_content_file_record",
                    "target_type": row["target_type"],
                    "target_id": row["target_id"],
                    "path": row["path"],
                }
            )
        elif path.is_file() and row["sha256"] and sha256_file(path) != row["sha256"]:
            issues.append(
                {
                    "severity": "warning",
                    "kind": "content_hash_mismatch",
                    "target_type": row["target_type"],
                    "target_id": row["target_id"],
                    "path": row["path"],
                }
            )

    broken_links = conn.execute(
        """
        SELECT source_slug, target_slug, kind
        FROM links
        WHERE target_slug NOT IN (SELECT slug FROM nodes)
        """
    ).fetchall()
    for row in broken_links:
        issues.append(
            {
                "severity": "warning",
                "kind": "broken_node_link",
                "source_slug": row["source_slug"],
                "target_slug": row["target_slug"],
                "link_kind": row["kind"],
            }
        )

    node_count = conn.execute("SELECT COUNT(*) FROM nodes").fetchone()[0]
    node_fts_count = conn.execute("SELECT COUNT(*) FROM node_fts").fetchone()[0]
    quiz_count = conn.execute("SELECT COUNT(*) FROM quizzes").fetchone()[0]
    quiz_fts_count = conn.execute("SELECT COUNT(*) FROM quiz_fts").fetchone()[0]
    if node_count != node_fts_count:
        issues.append({"severity": "warning", "kind": "node_fts_count_mismatch", "nodes": node_count, "node_fts": node_fts_count})
    if quiz_count != quiz_fts_count:
        issues.append({"severity": "warning", "kind": "quiz_fts_count_mismatch", "quizzes": quiz_count, "quiz_fts": quiz_fts_count})

    return {
        "ok": not any(issue["severity"] == "error" for issue in issues),
        "generated_at": utc_now(),
        "issue_count": len(issues),
        "issues": issues,
    }


def write_manifest(path: Path, manifest: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")


def _slug_to_title(slug: str) -> str:
    return " ".join(part.capitalize() for part in slug.split("-"))


def repair_broken_links(conn: sqlite3.Connection, content_root: Path) -> dict:
    """Create stub nodes for every link target that does not exist yet."""
    rows = conn.execute(
        """
        SELECT DISTINCT target_slug
        FROM links
        WHERE target_slug NOT IN (SELECT slug FROM nodes)
        ORDER BY target_slug
        """
    ).fetchall()

    stub_area = "stubs"
    stub_track = "general"
    stub_status = "stub"
    stub_visibility = "support"
    stub_dir = content_root / "nodes" / stub_area
    stub_dir.mkdir(parents=True, exist_ok=True)

    created: list[str] = []
    errors: list[str] = []
    now = utc_now()

    for row in rows:
        slug = row["target_slug"]
        title = _slug_to_title(slug)
        path = stub_dir / f"{slug}.md"
        relative_path = path.relative_to(content_root).as_posix()
        summary = "Stub node auto-created to repair a broken link. Expand this note when you are ready to write it."
        body = ""

        frontmatter = f"""---
slug: "{slug}"
title: "{title}"
area: "{stub_area}"
track: "{stub_track}"
status: "{stub_status}"
visibility: "{stub_visibility}"
summary: "{summary}"
---
"""
        try:
            path.write_text(frontmatter + body, encoding="utf-8")
            conn.execute(
                """
                INSERT INTO nodes (
                    slug, title, area, track, display_order, status, visibility, summary, body, path, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    slug,
                    title,
                    stub_area,
                    stub_track,
                    1000,
                    stub_status,
                    stub_visibility,
                    summary,
                    body,
                    relative_path,
                    now,
                ),
            )
            conn.execute(
                "INSERT INTO node_fts (slug, title, summary, body, tags) VALUES (?, ?, ?, ?, ?)",
                (slug, title, summary, body, ""),
            )
            created.append(slug)
        except Exception as exc:  # pragma: no cover - filesystem edge cases
            errors.append(f"{slug}: {exc}")

    return {
        "repaired_kind": "broken_node_link",
        "created": created,
        "created_count": len(created),
        "error_count": len(errors),
        "errors": errors,
    }


def run_repair(conn: sqlite3.Connection, content_root: Path) -> dict:
    """Run the full repair workflow and return a report."""
    report: dict = {}
    with conn:
        report["broken_node_link"] = repair_broken_links(conn, content_root)
    return report
