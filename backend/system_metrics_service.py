from __future__ import annotations

import json
import logging
import os
import subprocess
import threading
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional


class SystemMetricsService:
    cache_ttl_seconds = 600
    github_ttl_seconds = 3600
    cache_max_stale_seconds = 86400

    def __init__(
        self,
        project_root: Path,
        content_root: Path,
        db_path: Path,
        logger: logging.Logger,
        generated_root: Path | None = None,
    ) -> None:
        self.project_root = project_root.resolve()
        self.content_root = content_root.resolve()
        self.db_path = db_path.resolve()
        self.generated_dir = (generated_root or self.project_root / "generated").resolve()
        self.cache_path = self.generated_dir / "health-metrics-cache.json"
        self.logger = logger
        self.cache: dict[str, object] = {"payload": None, "collected_at": 0.0}
        self.github_cache: dict[str, object] = {"payload": None, "collected_at": 0.0}
        self.lock = threading.Lock()
        self.github_lock = threading.Lock()
        self.refresh_in_progress = False
        self.startup_refresh_started = False

    def directory_size(self, path: Path) -> int:
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

    def unique_directory_size(self, paths: list[Path]) -> int:
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
        return sum(self.directory_size(path) for path in roots)

    def git_tracked_size(self) -> int:
        try:
            result = subprocess.run(
                ["git", "ls-files", "-z"],
                cwd=self.project_root,
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
            path = self.project_root / raw_name.decode("utf-8", errors="ignore")
            if path.is_file():
                try:
                    total += path.stat().st_size
                except OSError:
                    continue
        return total

    def github_repo_size(self, owner: str = "EricWang1358", repo: str = "cs-learning-os") -> dict:
        fallback_bytes = self.git_tracked_size()
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
        with self.github_lock:
            cached = self.github_cache.get("payload")
            collected_at = float(self.github_cache.get("collected_at") or 0)
            if cached and now - collected_at < self.github_ttl_seconds:
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
        with self.github_lock:
            self.github_cache["payload"] = result
            self.github_cache["collected_at"] = time.monotonic()
        return result

    def storage_partition(self, key: str, label: str, path: Path, summary: str, kind: str) -> dict:
        return {
            "key": key,
            "label": label,
            "bytes": self.directory_size(path),
            "path": str(path),
            "summary": summary,
            "kind": kind,
        }

    def read_snapshot(self) -> Optional[dict]:
        if not self.cache_path.exists():
            return None
        try:
            with self.cache_path.open("r", encoding="utf-8") as handle:
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
        if age > self.cache_max_stale_seconds:
            return None
        return payload

    def write_snapshot(self, payload: dict) -> None:
        try:
            self.cache_path.parent.mkdir(parents=True, exist_ok=True)
            with self.cache_path.open("w", encoding="utf-8") as handle:
                json.dump(payload, handle, ensure_ascii=False, indent=2)
        except OSError as error:
            self.logger.warning("Failed to write system metrics cache: %s", error)

    def cached_payload(self) -> Optional[dict]:
        now = time.monotonic()
        with self.lock:
            cached = self.cache.get("payload")
            collected_at = float(self.cache.get("collected_at") or 0)
            if cached and now - collected_at < self.cache_ttl_seconds:
                return {**cached, "cached": True, "refreshing": self.refresh_in_progress}
        snapshot = self.read_snapshot()
        if snapshot:
            with self.lock:
                self.cache["payload"] = snapshot
                self.cache["collected_at"] = now
            return {**snapshot, "cached": True, "refreshing": False}
        return None

    def load_snapshot_into_memory(self) -> Optional[dict]:
        snapshot = self.read_snapshot()
        if snapshot:
            with self.lock:
                self.cache["payload"] = snapshot
                self.cache["collected_at"] = time.monotonic()
        return snapshot

    def build_heavy_metrics(self) -> dict:
        started = time.perf_counter()
        github_size = self.github_repo_size()
        db_bytes = self.db_path.stat().st_size if self.db_path.exists() else 0
        project_related_bytes = self.unique_directory_size([self.project_root, self.content_root, self.db_path.parent, self.generated_dir])
        exclusive_partitions = self.exclusive_storage_partitions()
        explained_project_bytes = sum(partition["bytes"] for partition in exclusive_partitions)
        if project_related_bytes > explained_project_bytes:
            exclusive_partitions.append(
                {
                    "key": "unaccounted",
                    "label": "Other related files",
                    "bytes": project_related_bytes - explained_project_bytes,
                    "path": "multiple roots",
                    "summary": "Files counted in the total but not assigned to a named partition.",
                    "kind": "other",
                }
            )
        partitions = [
            {
                "key": "project-related",
                "label": "Project related files",
                "bytes": project_related_bytes,
                "path": str(self.project_root),
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
                "path": str(self.project_root),
                "summary": "Local files currently tracked by Git. This approximates what is uploaded, excluding ignored private data.",
                "kind": "source",
            },
            self.storage_partition("app-repo", "App repository", self.project_root, "React app, FastAPI backend, docs, scripts, demo content, and Git metadata.", "code"),
            self.storage_partition("content", "Content data", self.content_root, "Private or demo Markdown knowledge files that back nodes, quizzes, links, and sources.", "knowledge"),
            {
                "key": "sqlite-db",
                "label": "SQLite DB",
                "bytes": db_bytes,
                "path": str(self.db_path),
                "summary": "Local query/index database with nodes, quizzes, FTS search tables, questions, jobs, graph cache, and reading activity.",
                "kind": "database",
            },
            self.storage_partition("generated", "Generated artifacts", self.generated_dir, "Development logs, screenshots, smoke-test evidence, temporary Codex home, and other rebuildable artifacts.", "generated"),
        ]
        return {
            "storage": {
                "content_bytes": self.directory_size(self.content_root),
                "db_bytes": db_bytes,
                "generated_bytes": self.directory_size(self.generated_dir),
                "project_related_bytes": project_related_bytes,
                "github_repo_bytes": github_size["bytes"],
                "github_repo_fallback_tracked_bytes": github_size["fallback_tracked_bytes"],
                "partitions": partitions,
                "exclusive_partitions": exclusive_partitions,
                "explained_project_bytes": sum(partition["bytes"] for partition in exclusive_partitions),
            },
            "paths": {
                "project": str(self.project_root),
                "content": str(self.content_root),
                "db": str(self.db_path),
                "generated": str(self.generated_dir),
            },
            "github": github_size,
            "collected_at": datetime.now(timezone.utc).isoformat(),
            "collection_ms": round((time.perf_counter() - started) * 1000),
        }

    def refresh_cache(self) -> None:
        with self.lock:
            if self.refresh_in_progress:
                return
            self.refresh_in_progress = True
        try:
            payload = self.build_heavy_metrics()
            self.write_snapshot(payload)
            with self.lock:
                self.cache["payload"] = payload
                self.cache["collected_at"] = time.monotonic()
        except Exception:
            self.logger.exception("Failed to refresh system metrics cache")
        finally:
            with self.lock:
                self.refresh_in_progress = False

    def start_background_refresh(self) -> None:
        with self.lock:
            if self.startup_refresh_started:
                return
            self.startup_refresh_started = True
        self.load_snapshot_into_memory()
        thread = threading.Thread(
            target=self.refresh_cache,
            name="system-metrics-refresh",
            daemon=True,
        )
        thread.start()

    def fallback_payload(self) -> dict:
        db_bytes = self.db_path.stat().st_size if self.db_path.exists() else 0
        partitions = [
            {
                "key": "content",
                "label": "Content data",
                "bytes": 0,
                "path": str(self.content_root),
                "summary": "Pending heavy scan: private or demo Markdown knowledge files.",
                "kind": "knowledge",
            },
            {
                "key": "sqlite-db",
                "label": "SQLite DB",
                "bytes": db_bytes,
                "path": str(self.db_path),
                "summary": "Fast fallback: local SQLite database file.",
                "kind": "database",
            },
            {
                "key": "generated",
                "label": "Generated artifacts",
                "bytes": 0,
                "path": str(self.generated_dir),
                "summary": "Pending heavy scan: rebuildable local generated artifacts.",
                "kind": "generated",
            },
        ]
        return {
            "storage": {
                "content_bytes": 0,
                "db_bytes": db_bytes,
                "generated_bytes": 0,
                "project_related_bytes": db_bytes,
                "github_repo_bytes": 0,
                "github_repo_fallback_tracked_bytes": 0,
                "partitions": partitions,
                "exclusive_partitions": partitions,
                "explained_project_bytes": db_bytes,
            },
            "paths": {
                "project": str(self.project_root),
                "content": str(self.content_root),
                "db": str(self.db_path),
                "generated": str(self.generated_dir),
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

    def direct_child_partitions(self, root: Path, reserved_paths: list[Path], key_prefix: str) -> list[dict]:
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
                    "bytes": self.directory_size(child),
                    "path": str(child),
                    "summary": summary,
                    "kind": kind,
                }
            )
        return partitions

    def exclusive_storage_partitions(self) -> list[dict]:
        roots = [self.project_root]
        candidates = [self.content_root, self.db_path.parent]
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
                key_prefix = "repo" if root.resolve() == self.project_root.resolve() else f"external-{root.name.lower().replace(' ', '-')}"
            except OSError:
                key_prefix = f"external-{root.name.lower().replace(' ', '-')}"
            partitions.extend(self.direct_child_partitions(root, [], key_prefix))
        return partitions
