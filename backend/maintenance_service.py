from __future__ import annotations

import hashlib
import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path


PACKAGE_FORMAT_VERSION = "1"


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
