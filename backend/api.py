from __future__ import annotations

import sqlite3
import re
import os
import json
import logging
import shutil
import subprocess
import threading
import time
import urllib.request
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

from fastapi import BackgroundTasks, FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

try:
    from . import ai_job_service
    from . import ai_revision_service
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
    )
    from . import content_write_service
    from .content_write_service import body_hash
    from .db import connect, initialize
    from . import graph_service
    from . import learning_service
    from . import maintenance_service
    from . import node_lifecycle_service
    from . import reader_question_service
    from . import search_service
except ImportError:
    import ai_job_service
    import ai_revision_service
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
    )
    import content_write_service
    from content_write_service import body_hash
    from db import connect, initialize
    import graph_service
    import learning_service
    import maintenance_service
    import node_lifecycle_service
    import reader_question_service
    import search_service


ROOT = Path(__file__).resolve().parents[1]
CONTENT_ROOT = Path(os.environ.get("CS_LEARNING_CONTENT", ROOT / "content-demo")).resolve()
CONTENT_ASSETS_ROOT = (CONTENT_ROOT / "assets").resolve()
DB_PATH = Path(os.environ.get("CS_LEARNING_DB", ROOT / "var" / "knowledge.db")).resolve()
logger = logging.getLogger("cs_learning.api")
SYSTEM_METRICS_CACHE_TTL_SECONDS = 600
SYSTEM_METRICS_GITHUB_TTL_SECONDS = 3600
SYSTEM_METRICS_CACHE_MAX_STALE_SECONDS = 86400
SYSTEM_METRICS_CACHE_PATH = ROOT / "generated" / "health-metrics-cache.json"
SYSTEM_METRICS_CACHE: dict[str, object] = {"payload": None, "collected_at": 0.0}
SYSTEM_METRICS_GITHUB_CACHE: dict[str, object] = {"payload": None, "collected_at": 0.0}
SYSTEM_METRICS_LOCK = threading.Lock()
SYSTEM_METRICS_GITHUB_LOCK = threading.Lock()
SYSTEM_METRICS_REFRESH_IN_PROGRESS = False
SYSTEM_METRICS_STARTUP_REFRESH_STARTED = False
SAFE_SLUG_RE = re.compile(r"[^a-z0-9-]+")
app = FastAPI(title="CS Learning OS API")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

ai_provider_name = ai_revision_service.ai_provider_name
openai_is_configured = ai_revision_service.openai_is_configured
openai_model_name = ai_revision_service.openai_model_name


@app.on_event("startup")
def on_startup() -> None:
    start_system_metrics_background_refresh()


@app.get("/content-assets/{asset_path:path}")
def get_content_asset(asset_path: str):
    if not asset_path or "\x00" in asset_path:
        raise HTTPException(status_code=404, detail="Asset not found")

    asset = (CONTENT_ASSETS_ROOT / asset_path).resolve()
    try:
        asset.relative_to(CONTENT_ASSETS_ROOT)
    except ValueError:
        raise HTTPException(status_code=404, detail="Asset not found") from None

    if not asset.is_file():
        raise HTTPException(status_code=404, detail="Asset not found")

    return FileResponse(asset)


class ReaderQuestionCreate(BaseModel):
    target_type: str = Field(pattern="^(node|quiz)$")
    target_id: str = Field(min_length=1)
    question: str = Field(min_length=1)


class ReaderQuestionResolve(BaseModel):
    resolution_note: str = ""


class BodyUpdate(BaseModel):
    body: str
    base_body_hash: str = ""


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


class QuizAttemptCreate(BaseModel):
    grade: str = Field(pattern="^(again|hard|good|easy)$")
    elapsed_ms: int = Field(default=0, ge=0)
    note: str = ""


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


def unique_directory_size(paths: list[Path]) -> int:
    resolved_paths = []
    for path in paths:
        try:
            resolved_paths.append(path.resolve())
        except OSError:
            continue
    roots: list[Path] = []
    for path in sorted(set(resolved_paths), key=lambda item: len(item.parts)):
        if not any(path == root or root in path.parents for root in roots):
            roots.append(path)
    return sum(directory_size(path) for path in roots)


def git_tracked_size(repo_root: Path) -> int:
    try:
        result = subprocess.run(
            ["git", "ls-files", "-z"],
            cwd=repo_root,
            check=True,
            capture_output=True,
            text=False,
            timeout=5,
        )
    except (OSError, subprocess.SubprocessError):
        return 0
    total = 0
    for raw_name in result.stdout.split(b"\0"):
        if not raw_name:
            continue
        path = repo_root / raw_name.decode("utf-8", errors="ignore")
        if path.is_file():
            try:
                total += path.stat().st_size
            except OSError:
                continue
    return total


def github_repo_size(owner: str = "EricWang1358", repo: str = "cs-learning-os") -> dict:
    fallback_bytes = git_tracked_size(ROOT)
    if os.environ.get("CS_LEARNING_GITHUB_REMOTE_SIZE", "").lower() not in {"1", "true", "yes"}:
        return {
            "bytes": fallback_bytes,
            "source": "git-tracked-local",
            "url": f"https://github.com/{owner}/{repo}",
            "message": "Showing local tracked-file estimate. Set CS_LEARNING_GITHUB_REMOTE_SIZE=1 to query GitHub.",
            "fallback_tracked_bytes": fallback_bytes,
            "cached": True,
        }
    now = time.monotonic()
    with SYSTEM_METRICS_GITHUB_LOCK:
        cached = SYSTEM_METRICS_GITHUB_CACHE.get("payload")
        collected_at = float(SYSTEM_METRICS_GITHUB_CACHE.get("collected_at") or 0)
        if cached and now - collected_at < SYSTEM_METRICS_GITHUB_TTL_SECONDS:
            return {**cached, "cached": True}
    url = f"https://api.github.com/repos/{owner}/{repo}"
    try:
        request = urllib.request.Request(
            url,
            headers={
                "Accept": "application/vnd.github+json",
                "User-Agent": "cs-learning-os-local-health",
            },
        )
        with urllib.request.urlopen(request, timeout=3) as response:
            payload = json.loads(response.read().decode("utf-8"))
        size_kb = int(payload.get("size") or 0)
        result = {
            "bytes": size_kb * 1024,
            "source": "github-api",
            "url": payload.get("html_url") or f"https://github.com/{owner}/{repo}",
            "message": "GitHub reports repository size in KiB.",
            "fallback_tracked_bytes": fallback_bytes,
            "cached": False,
        }
    except Exception as error:
        result = {
            "bytes": fallback_bytes,
            "source": "git-tracked-fallback",
            "url": f"https://github.com/{owner}/{repo}",
            "message": f"GitHub API unavailable; showing local tracked-file estimate. {error}",
            "fallback_tracked_bytes": fallback_bytes,
            "cached": False,
        }
    with SYSTEM_METRICS_GITHUB_LOCK:
        SYSTEM_METRICS_GITHUB_CACHE["payload"] = result
        SYSTEM_METRICS_GITHUB_CACHE["collected_at"] = time.monotonic()
    return result


def storage_partition(key: str, label: str, path: Path, summary: str, kind: str) -> dict:
    return {
        "key": key,
        "label": label,
        "bytes": directory_size(path),
        "path": str(path),
        "summary": summary,
        "kind": kind,
    }


def read_system_metrics_snapshot() -> Optional[dict]:
    if not SYSTEM_METRICS_CACHE_PATH.exists():
        return None
    try:
        with SYSTEM_METRICS_CACHE_PATH.open("r", encoding="utf-8") as handle:
            payload = json.load(handle)
    except (OSError, json.JSONDecodeError):
        return None
    collected_at = payload.get("collected_at")
    if not isinstance(collected_at, str):
        return None
    try:
        age = (datetime.now(timezone.utc) - datetime.fromisoformat(collected_at)).total_seconds()
    except ValueError:
        return None
    if age > SYSTEM_METRICS_CACHE_MAX_STALE_SECONDS:
        return None
    return payload


def write_system_metrics_snapshot(payload: dict) -> None:
    try:
        SYSTEM_METRICS_CACHE_PATH.parent.mkdir(parents=True, exist_ok=True)
        with SYSTEM_METRICS_CACHE_PATH.open("w", encoding="utf-8") as handle:
            json.dump(payload, handle, ensure_ascii=False, indent=2)
    except OSError as error:
        logger.warning("Failed to write system metrics cache: %s", error)


def cached_system_metrics_payload() -> Optional[dict]:
    now = time.monotonic()
    with SYSTEM_METRICS_LOCK:
        cached = SYSTEM_METRICS_CACHE.get("payload")
        collected_at = float(SYSTEM_METRICS_CACHE.get("collected_at") or 0)
        if cached and now - collected_at < SYSTEM_METRICS_CACHE_TTL_SECONDS:
            return {**cached, "cached": True, "refreshing": SYSTEM_METRICS_REFRESH_IN_PROGRESS}
    snapshot = read_system_metrics_snapshot()
    if snapshot:
        with SYSTEM_METRICS_LOCK:
            SYSTEM_METRICS_CACHE["payload"] = snapshot
            SYSTEM_METRICS_CACHE["collected_at"] = now
        return {**snapshot, "cached": True, "refreshing": False}
    return None


def load_system_metrics_snapshot_into_memory() -> Optional[dict]:
    snapshot = read_system_metrics_snapshot()
    if snapshot:
        with SYSTEM_METRICS_LOCK:
            SYSTEM_METRICS_CACHE["payload"] = snapshot
            SYSTEM_METRICS_CACHE["collected_at"] = time.monotonic()
    return snapshot


def build_heavy_system_metrics() -> dict:
    started = time.perf_counter()
    generated_dir = ROOT / "generated"
    github_size = github_repo_size()
    db_bytes = DB_PATH.stat().st_size if DB_PATH.exists() else 0
    project_related_bytes = unique_directory_size([ROOT, CONTENT_ROOT, DB_PATH.parent, generated_dir])
    exclusive_partitions = exclusive_storage_partitions()
    explained_project_bytes = sum(partition["bytes"] for partition in exclusive_partitions)
    if project_related_bytes > explained_project_bytes:
        exclusive_partitions.append(
            {
                "key": "unaccounted",
                "label": "Other related files",
                "bytes": project_related_bytes - explained_project_bytes,
                "path": "multiple roots",
                "summary": "Files counted in the total but not assigned to a named partition. This usually means an external path or overlap needs a clearer label.",
                "kind": "other",
            }
        )
    partitions = [
        {
            "key": "project-related",
            "label": "Project related files",
            "bytes": project_related_bytes,
            "path": str(ROOT),
            "summary": "Total local footprint across the app repository, external content data, SQLite storage, and generated artifacts.",
            "kind": "total",
        },
        {
            "key": "github-upload",
            "label": "GitHub upload size",
            "bytes": github_size["bytes"],
            "path": github_size["url"],
            "summary": "Remote repository size if GitHub API is reachable; otherwise a local tracked-file estimate.",
            "kind": "remote",
        },
        {
            "key": "git-tracked",
            "label": "Git tracked files",
            "bytes": github_size["fallback_tracked_bytes"],
            "path": str(ROOT),
            "summary": "Local files currently tracked by Git. This approximates what is uploaded, excluding ignored private data.",
            "kind": "source",
        },
        storage_partition(
            "app-repo",
            "App repository",
            ROOT,
            "React app, FastAPI backend, docs, scripts, demo content, and Git metadata for the public project.",
            "code",
        ),
        storage_partition(
            "content",
            "Content data",
            CONTENT_ROOT,
            "Private or demo Markdown knowledge files that back nodes, quizzes, links, and sources.",
            "knowledge",
        ),
        {
            "key": "sqlite-db",
            "label": "SQLite DB",
            "bytes": db_bytes,
            "path": str(DB_PATH),
            "summary": "Local query/index database with nodes, quizzes, FTS search tables, questions, jobs, graph cache, and reading activity.",
            "kind": "database",
        },
        storage_partition(
            "generated",
            "Generated artifacts",
            generated_dir,
            "Development logs, screenshots, smoke-test evidence, temporary Codex home, and other rebuildable artifacts.",
            "generated",
        ),
    ]
    return {
        "storage": {
            "content_bytes": directory_size(CONTENT_ROOT),
            "db_bytes": db_bytes,
            "generated_bytes": directory_size(generated_dir),
            "project_related_bytes": project_related_bytes,
            "github_repo_bytes": github_size["bytes"],
            "github_repo_fallback_tracked_bytes": github_size["fallback_tracked_bytes"],
            "partitions": partitions,
            "exclusive_partitions": exclusive_partitions,
            "explained_project_bytes": sum(partition["bytes"] for partition in exclusive_partitions),
        },
        "paths": {
            "project": str(ROOT),
            "content": str(CONTENT_ROOT),
            "db": str(DB_PATH),
            "generated": str(generated_dir),
        },
        "github": github_size,
        "collected_at": datetime.now(timezone.utc).isoformat(),
        "collection_ms": round((time.perf_counter() - started) * 1000),
    }


def refresh_system_metrics_cache() -> None:
    global SYSTEM_METRICS_REFRESH_IN_PROGRESS
    with SYSTEM_METRICS_LOCK:
        if SYSTEM_METRICS_REFRESH_IN_PROGRESS:
            return
        SYSTEM_METRICS_REFRESH_IN_PROGRESS = True
    try:
        payload = build_heavy_system_metrics()
        write_system_metrics_snapshot(payload)
        with SYSTEM_METRICS_LOCK:
            SYSTEM_METRICS_CACHE["payload"] = payload
            SYSTEM_METRICS_CACHE["collected_at"] = time.monotonic()
    except Exception:
        logger.exception("Failed to refresh system metrics cache")
    finally:
        with SYSTEM_METRICS_LOCK:
            SYSTEM_METRICS_REFRESH_IN_PROGRESS = False


def start_system_metrics_background_refresh() -> None:
    global SYSTEM_METRICS_STARTUP_REFRESH_STARTED
    with SYSTEM_METRICS_LOCK:
        if SYSTEM_METRICS_STARTUP_REFRESH_STARTED:
            return
        SYSTEM_METRICS_STARTUP_REFRESH_STARTED = True
    load_system_metrics_snapshot_into_memory()
    thread = threading.Thread(
        target=refresh_system_metrics_cache,
        name="system-metrics-refresh",
        daemon=True,
    )
    thread.start()


def fallback_system_metrics_payload() -> dict:
    generated_dir = ROOT / "generated"
    db_bytes = DB_PATH.stat().st_size if DB_PATH.exists() else 0
    content_bytes = 0
    generated_bytes = 0
    project_related_bytes = content_bytes + db_bytes + generated_bytes
    partitions = [
        {
            "key": "content",
            "label": "Content data",
            "bytes": content_bytes,
            "path": str(CONTENT_ROOT),
            "summary": "Pending heavy scan: private or demo Markdown knowledge files.",
            "kind": "knowledge",
        },
        {
            "key": "sqlite-db",
            "label": "SQLite DB",
            "bytes": db_bytes,
            "path": str(DB_PATH),
            "summary": "Fast fallback: local SQLite database file.",
            "kind": "database",
        },
        {
            "key": "generated",
            "label": "Generated artifacts",
            "bytes": generated_bytes,
            "path": str(generated_dir),
            "summary": "Pending heavy scan: rebuildable local generated artifacts.",
            "kind": "generated",
        },
    ]
    return {
        "storage": {
            "content_bytes": content_bytes,
            "db_bytes": db_bytes,
            "generated_bytes": generated_bytes,
            "project_related_bytes": project_related_bytes,
            "github_repo_bytes": 0,
            "github_repo_fallback_tracked_bytes": 0,
            "partitions": partitions,
            "exclusive_partitions": partitions,
            "explained_project_bytes": project_related_bytes,
        },
        "paths": {
            "project": str(ROOT),
            "content": str(CONTENT_ROOT),
            "db": str(DB_PATH),
            "generated": str(generated_dir),
        },
        "github": {
            "bytes": 0,
            "source": "pending-cache",
            "url": "https://github.com/EricWang1358/cs-learning-os",
            "message": "Local tracked-file GitHub estimate is pending; cached metrics will replace this fallback.",
            "fallback_tracked_bytes": 0,
            "cached": False,
        },
        "collected_at": datetime.now(timezone.utc).isoformat(),
        "collection_ms": 0,
    }


def direct_child_partitions(root: Path, reserved_paths: list[Path], key_prefix: str) -> list[dict]:
    partitions = []
    reserved = []
    for path in reserved_paths:
        try:
            reserved.append(path.resolve())
        except OSError:
            continue
    if not root.exists() or not root.is_dir():
        return partitions
    for child in sorted(root.iterdir(), key=lambda item: item.name.lower()):
        try:
            resolved_child = child.resolve()
        except OSError:
            continue
        if any(reserved_path == resolved_child or resolved_child in reserved_path.parents for reserved_path in reserved):
            continue
        if child.name in {".git", "node_modules", ".venv", "__pycache__"}:
            summary = "Tooling/cache footprint. Usually useful locally, but not part of authored learning content."
            kind = "tooling"
        elif child.name in {"app", "backend", "scripts", "docs", "skill", "content-demo"}:
            summary = "Project source, docs, scripts, skills, or committed demo material."
            kind = "source"
        elif child.name == "content":
            summary = "Knowledge Markdown content directory for nodes, quizzes, links, and sources."
            kind = "knowledge"
        elif child.suffix == ".db":
            summary = "SQLite database file with local search indexes, jobs, graph cache, and reading activity."
            kind = "database"
        elif child.name == "generated":
            summary = "Development logs, screenshots, smoke-test evidence, Codex home, and rebuildable artifacts."
            kind = "generated"
        else:
            summary = "Project-adjacent file or folder under the app repository root."
            kind = "other"
        partitions.append(
            {
                "key": f"{key_prefix}-{child.name.lower().replace(' ', '-')}",
                "label": child.name,
                "bytes": directory_size(child),
                "path": str(child),
                "summary": summary,
                "kind": kind,
            }
        )
    return partitions


def exclusive_storage_partitions() -> list[dict]:
    roots = [ROOT]
    candidates = [CONTENT_ROOT, DB_PATH.parent]
    for candidate in candidates:
        try:
            resolved = candidate.resolve()
        except OSError:
            continue
        if any(resolved == root or root in resolved.parents for root in roots):
            continue
        roots = [root for root in roots if not (root == resolved or resolved in root.parents)]
        roots.append(resolved)

    partitions: list[dict] = []
    for root in roots:
        try:
            key_prefix = "repo" if root.resolve() == ROOT.resolve() else f"external-{root.name.lower().replace(' ', '-')}"
        except OSError:
            key_prefix = f"external-{root.name.lower().replace(' ', '-')}"
        partitions.extend(direct_child_partitions(root, [], key_prefix))
    return partitions


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
            context_json = ai_revision_service.build_ai_revision_prompt(
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
        response = ai_revision_service.run_revision(context_json, reader_questions)

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
def system_metrics(background_tasks: BackgroundTasks, refresh: bool = Query(False)) -> dict:
    recover_stale_ai_jobs()
    with get_conn() as conn:
        counts = {
            "nodes": conn.execute("SELECT COUNT(*) FROM nodes").fetchone()[0],
            "quizzes": conn.execute("SELECT COUNT(*) FROM quizzes").fetchone()[0],
            "open_questions": conn.execute("SELECT COUNT(*) FROM reader_questions WHERE status = 'open'").fetchone()[0],
            "active_ai_jobs": conn.execute("SELECT COUNT(*) FROM ai_jobs WHERE status IN ('queued', 'solving', 'draft_ready', 'failed')").fetchone()[0],
            "failed_ai_jobs": conn.execute("SELECT COUNT(*) FROM ai_jobs WHERE status = 'failed'").fetchone()[0],
            "due_reviews": conn.execute("SELECT COUNT(*) FROM review_queue WHERE due_at <= ?", (utc_now(),)).fetchone()[0],
        }
        meta = maintenance_service.schema_meta(conn)
    heavy_metrics = None if refresh else cached_system_metrics_payload()
    if heavy_metrics is None:
        heavy_metrics = fallback_system_metrics_payload()
        if refresh:
            background_tasks.add_task(refresh_system_metrics_cache)
        heavy_metrics["cached"] = False
        heavy_metrics["refreshing"] = SYSTEM_METRICS_REFRESH_IN_PROGRESS
    elif refresh:
        background_tasks.add_task(refresh_system_metrics_cache)
        heavy_metrics["refreshing"] = True
    ai_payload = codex_preflight(run_model=False) if ai_provider_name() == "codex-cli" else {
        "ok": openai_is_configured(),
        "message": "OpenAI API key is configured." if openai_is_configured() else "OPENAI_API_KEY is not configured.",
    }
    return {
        "counts": counts,
        **heavy_metrics,
        "cache": {
            "cached": bool(heavy_metrics.get("cached")),
            "refreshing": bool(heavy_metrics.get("refreshing")),
            "ttl_seconds": SYSTEM_METRICS_CACHE_TTL_SECONDS,
            "refresh_after": "Manual refresh or cache expiry; heavy scans run in the background.",
        },
        "ai": ai_payload,
        "schema": meta,
    }


@app.get("/api/system/schema")
def system_schema() -> dict:
    with get_conn() as conn:
        return {"schema": maintenance_service.schema_meta(conn)}


@app.get("/api/system/repair")
def system_repair_report() -> dict:
    with get_conn() as conn:
        return maintenance_service.repair_report(conn, CONTENT_ROOT)


@app.get("/api/package/export")
def package_export_manifest(write: bool = Query(False)) -> dict:
    with get_conn() as conn:
        manifest = maintenance_service.content_manifest(conn, CONTENT_ROOT)
    if write:
        path = ROOT / "generated" / "exports" / "learning-package-manifest.json"
        maintenance_service.write_manifest(path, manifest)
        manifest["written_to"] = str(path)
    return {"manifest": manifest}


@app.get("/api/review/due")
def due_reviews(limit: int = Query(default=50, ge=1, le=200)) -> dict:
    with get_conn() as conn:
        return {"reviews": learning_service.due_reviews(conn, limit=limit)}


@app.post("/api/quizzes/{quiz_id}/attempts")
def record_quiz_attempt(quiz_id: str, payload: QuizAttemptCreate) -> dict:
    with get_conn() as conn:
        return learning_service.record_quiz_attempt(
            conn,
            quiz_id,
            payload.grade,
            elapsed_ms=payload.elapsed_ms,
            note=payload.note,
        )


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

    context_json = ai_revision_service.build_ai_revision_prompt(
        target,
        payload.target_type,
        reader_questions,
        payload.instruction,
        payload.draft_body,
    )
    response = ai_revision_service.run_revision(context_json, reader_questions)

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
    with get_conn() as conn:
        base_body = payload.draft_body.strip()
        if not base_body:
            if payload.target_type == "node":
                body_row = conn.execute("SELECT body FROM nodes WHERE slug = ?", (payload.target_id,)).fetchone()
            else:
                body_row = conn.execute("SELECT body FROM quizzes WHERE id = ?", (payload.target_id,)).fetchone()
            base_body = body_row["body"] if body_row else ""
        row = ai_job_service.create_ai_job(
            conn,
            payload.target_type,
            payload.target_id,
            payload.question_ids,
            payload.question,
            payload.instruction,
            payload.draft_body,
            ai_provider_name(),
            codex_model_name() if ai_provider_name() == "codex-cli" else openai_model_name(),
            body_hash(base_body),
        )

    add_ai_job_event(row["id"], "queued", "AI job created. Reader questions remain open until draft is applied.")
    background_tasks.add_task(run_ai_job, row["id"])
    return {"job": row_to_ai_job(row)}


@app.get("/api/ai/jobs")
def list_ai_jobs(
    target_type: Optional[str] = None,
    target_id: Optional[str] = None,
    status: Optional[str] = None,
) -> dict:
    with get_conn() as conn:
        rows = ai_job_service.list_ai_jobs(conn, target_type, target_id, status)
    return {"jobs": [row_to_ai_job(row) for row in rows]}


@app.get("/api/ai/jobs/{job_id}")
def get_ai_job(job_id: int) -> dict:
    with get_conn() as conn:
        row = get_ai_job_or_404(conn, job_id)
    return {"job": row_to_ai_job(row)}


@app.get("/api/ai/jobs/{job_id}/events")
def list_ai_job_events(job_id: int) -> dict:
    with get_conn() as conn:
        rows = ai_job_service.list_ai_job_events(conn, job_id)
    return {"events": [dict(row) for row in rows]}


@app.post("/api/ai/jobs/{job_id}/apply")
def apply_ai_job(job_id: int, payload: AiJobApply) -> dict:
    with get_conn() as conn:
        row = get_ai_job_or_404(conn, job_id)
        updated = content_write_service.apply_ai_job_body(conn, CONTENT_ROOT, row, payload.body)
    add_ai_job_event(job_id, "applied", "Human applied the draft and resolved linked questions.")
    return {"job": row_to_ai_job(updated)}


@app.post("/api/ai/jobs/{job_id}/cancel")
def cancel_ai_job(job_id: int) -> dict:
    with get_conn() as conn:
        updated = ai_job_service.cancel_ai_job(conn, job_id)
    add_ai_job_event(job_id, "cancelled", "Human cancelled the job. Linked questions remain open.", "warning")
    return {"job": row_to_ai_job(updated)}


@app.post("/api/ai/jobs/{job_id}/reject")
def reject_ai_job(job_id: int, payload: AiJobReject) -> dict:
    with get_conn() as conn:
        updated = ai_job_service.reject_ai_job(conn, job_id, payload.reason)
    add_ai_job_event(job_id, "rejected", "Human rejected the draft. Linked questions remain open.", "warning")
    return {"job": row_to_ai_job(updated)}


@app.post("/api/ai/jobs/{job_id}/retry")
def retry_ai_job(job_id: int, background_tasks: BackgroundTasks) -> dict:
    with get_conn() as conn:
        new_row = ai_job_service.retry_ai_job(
            conn,
            job_id,
            ai_provider_name(),
            codex_model_name() if ai_provider_name() == "codex-cli" else openai_model_name(),
        )

    add_ai_job_event(new_row["id"], "queued", f"Retry created from job #{job_id}.")
    background_tasks.add_task(run_ai_job, new_row["id"])
    return {"job": row_to_ai_job(new_row)}


@app.get("/api/nodes")
def list_nodes(
    area: Optional[str] = None,
    visibility: Optional[str] = None,
    sort: str = Query(default="order"),
) -> dict:
    with get_conn() as conn:
        rows = search_service.list_nodes(conn, area=area, visibility=visibility, sort=sort)
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
    with get_conn() as conn:
        slug = node_lifecycle_service.create_node(
            conn,
            CONTENT_ROOT,
            payload.title,
            payload.area,
            payload.track,
            payload.summary,
            payload.tags,
            payload.visibility,
            payload.status,
            payload.order,
        )

    return get_node(slug)


@app.get("/api/graph")
def get_graph_root(page: int = Query(default=1, ge=1)) -> dict:
    with get_conn() as conn:
        payload = graph_service.root_graph_payload(conn, page)
        conn.commit()
        return payload


@app.get("/api/graph/area/{area}")
def get_graph_area(area: str, page: int = Query(default=1, ge=1)) -> dict:
    with get_conn() as conn:
        payload = graph_service.area_graph_payload(conn, area, page)
        conn.commit()
        return payload


@app.get("/api/graph/track/{area}/{track}")
def get_graph_track(area: str, track: str, page: int = Query(default=1, ge=1)) -> dict:
    with get_conn() as conn:
        payload = graph_service.track_graph_payload(conn, area, track, page)
        conn.commit()
        return payload


@app.get("/api/graph/node/{slug}")
def get_graph_node(slug: str, page: int = Query(default=1, ge=1)) -> dict:
    with get_conn() as conn:
        payload = graph_service.node_graph_payload(conn, slug, page)
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
    node["body_hash"] = body_hash(row["body"])
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
                if elapsed_seconds < payload.min_interval_seconds:
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
    with get_conn() as conn:
        content_write_service.update_target_body(
            conn,
            CONTENT_ROOT,
            "node",
            slug,
            payload.body,
            payload.base_body_hash,
        )

    return get_node(slug)


@app.post("/api/nodes/{slug}/trash")
def trash_node(slug: str) -> dict:
    with get_conn() as conn:
        node_lifecycle_service.move_node_to_trash(conn, CONTENT_ROOT, slug)
    return get_node(slug)


@app.post("/api/nodes/{slug}/restore")
def restore_node(slug: str) -> dict:
    with get_conn() as conn:
        node_lifecycle_service.restore_node(conn, CONTENT_ROOT, slug)
    return get_node(slug)


@app.post("/api/nodes/{slug}/archive")
def archive_node(slug: str) -> dict:
    with get_conn() as conn:
        node_lifecycle_service.archive_node(conn, CONTENT_ROOT, slug)
    return get_node(slug)


@app.post("/api/nodes/{slug}/unarchive")
def unarchive_node(slug: str) -> dict:
    with get_conn() as conn:
        node_lifecycle_service.unarchive_node(conn, CONTENT_ROOT, slug)
    return get_node(slug)


@app.delete("/api/nodes/{slug}")
def permanently_delete_node(slug: str) -> dict:
    with get_conn() as conn:
        node_lifecycle_service.permanently_delete_node(conn, CONTENT_ROOT, slug)
    return {"ok": True}


@app.get("/api/search")
def search(q: str = Query(default="", min_length=0), sort: str = Query(default="relevance")) -> dict:
    with get_conn() as conn:
        rows = search_service.search_nodes(conn, q, sort=sort)

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
    sort: str = Query(default="order"),
) -> dict:
    with get_conn() as conn:
        rows = search_service.list_quizzes(conn, area=area, visibility=visibility, sort=sort)
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
    quiz["body_hash"] = body_hash(row["body"])
    quiz["tags"] = tags
    quiz["linked_nodes"] = linked_nodes
    quiz["sources"] = sources
    quiz["open_question_count"] = open_question_count
    return {"quiz": quiz}


@app.put("/api/quizzes/{quiz_id}/body")
def update_quiz_body(quiz_id: str, payload: BodyUpdate) -> dict:
    with get_conn() as conn:
        content_write_service.update_target_body(
            conn,
            CONTENT_ROOT,
            "quiz",
            quiz_id,
            payload.body,
            payload.base_body_hash,
        )

    return get_quiz(quiz_id)


@app.get("/api/quiz-search")
def search_quizzes(
    q: str = Query(default="", min_length=0),
    sort: str = Query(default="relevance"),
    area: Optional[str] = None,
    visibility: Optional[str] = None,
    difficulty: Optional[str] = None,
) -> dict:
    with get_conn() as conn:
        rows = search_service.search_quizzes(
            conn,
            q,
            sort=sort,
            area=area,
            visibility=visibility,
            difficulty=difficulty,
        )

    return {"quizzes": [row_to_quiz(row) for row in rows]}


@app.get("/api/reader-questions")
def list_reader_questions(
    target_type: Optional[str] = None,
    target_id: Optional[str] = None,
    status: str = "open",
) -> dict:
    with get_conn() as conn:
        rows = reader_question_service.list_reader_questions(conn, target_type, target_id, status)
    return {"questions": [row_to_reader_question(row) for row in rows]}


@app.post("/api/reader-questions")
def create_reader_question(payload: ReaderQuestionCreate) -> dict:
    with get_conn() as conn:
        row = reader_question_service.create_reader_question(
            conn,
            payload.target_type,
            payload.target_id,
            payload.question,
        )

    return {"question": row_to_reader_question(row)}


@app.post("/api/reader-questions/{question_id}/resolve")
def resolve_reader_question(question_id: int, payload: ReaderQuestionResolve) -> dict:
    with get_conn() as conn:
        updated = reader_question_service.set_reader_question_status(
            conn,
            question_id,
            "resolved",
            payload.resolution_note,
        )

    return {"question": row_to_reader_question(updated)}


@app.post("/api/reader-questions/{question_id}/dismiss")
def dismiss_reader_question(question_id: int, payload: ReaderQuestionResolve) -> dict:
    with get_conn() as conn:
        updated = reader_question_service.set_reader_question_status(
            conn,
            question_id,
            "dismissed",
            payload.resolution_note,
        )

    return {"question": row_to_reader_question(updated)}


@app.delete("/api/reader-questions/{question_id}")
def delete_reader_question(question_id: int) -> dict:
    with get_conn() as conn:
        reader_question_service.delete_reader_question(conn, question_id)
    return {"ok": True}
