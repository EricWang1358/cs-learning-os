#!/usr/bin/env python3
"""Validate Markdown link metadata before it reaches the content index.

This checks the deterministic content-navigation graph. KnowledgeGraph table
invariants are enforced by the backend API, but exact slugs and prerequisite
cycles can be caught before a write or ingest.
"""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path

from build_index import parse_frontmatter


def as_list(value) -> list[str]:
    if value is None:
        return []
    if isinstance(value, list):
        return [str(item).strip() for item in value if str(item).strip()]
    if isinstance(value, str) and value.strip():
        return [value.strip()]
    return []


MARKDOWN_LINK_RE = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")


def validate_body_links(path: Path, root: Path, body: str) -> list[str]:
    """Validate reader-facing relative Markdown links without blocking URLs."""
    errors: list[str] = []
    for match in MARKDOWN_LINK_RE.finditer(body):
        href = match.group(1).strip().split("#", 1)[0].split("?", 1)[0]
        if not href or href.startswith("/") or re.match(r"^(?:https?:|mailto:|tel:|data:|javascript:)", href, re.I):
            continue
        if not href.lower().endswith(".md"):
            continue
        target = (path.parent / href).resolve()
        try:
            target.relative_to(root.resolve())
        except ValueError:
            errors.append(f"{path.relative_to(root)}: Markdown link escapes content root: {href}")
            continue
        if not target.is_file():
            errors.append(f"{path.relative_to(root)}: broken Markdown link: {href}")
    return errors


def resolve_root(argument: str | None) -> Path:
    if argument:
        return Path(argument).resolve()
    configured = os.environ.get("CS_LEARNING_CONTENT")
    if configured:
        return Path(configured).resolve()
    private_root = Path("data/content")
    return private_root.resolve() if private_root.exists() else Path("content-demo").resolve()


def collect(root: Path):
    nodes = {}
    errors: list[str] = []
    nodes_dir = root / "nodes"
    if not nodes_dir.is_dir():
        return {}, [f"missing nodes directory: {nodes_dir}"]

    for path in sorted(nodes_dir.rglob("*.md")):
        if path.name == "index.md":
            continue
        meta = parse_frontmatter(path.read_text(encoding="utf-8"))
        errors.extend(validate_body_links(path, root, path.read_text(encoding="utf-8")))
        slug = str(meta.get("slug") or path.stem.lower().replace(" ", "-"))
        location = path.relative_to(root).as_posix()
        if slug in nodes:
            errors.append(f"{location}: duplicate slug {slug!r} (also {nodes[slug]['path']})")
            continue
        nodes[slug] = {
            "path": location,
            "prerequisites": as_list(meta.get("prerequisites")),
            "related": as_list(meta.get("related")),
            "meta": meta,
        }
    return nodes, errors


def validate(root: Path) -> list[str]:
    nodes, errors = collect(root)
    for slug, node in nodes.items():
        for field in ("prerequisites", "related"):
            values = node[field]
            if len(values) != len(set(values)):
                errors.append(f"{node['path']}: duplicate values in {field}")
            for target in values:
                if target == slug:
                    errors.append(f"{node['path']}: self-link in {field}: {target}")
                elif target not in nodes:
                    errors.append(f"{node['path']}: missing {field} target: {target}")
        forbidden = {"mastery", "score", "attempts", "fail_streak"} & set(node["meta"])
        if forbidden:
            errors.append(f"{node['path']}: runtime mastery fields in frontmatter: {sorted(forbidden)}")

    graph = {slug: node["prerequisites"] for slug, node in nodes.items()}
    visiting: set[str] = set()
    visited: set[str] = set()

    def visit(slug: str, trail: list[str]) -> None:
        if slug in visiting:
            cycle = trail[trail.index(slug):] + [slug]
            errors.append(f"prerequisite cycle: {' -> '.join(cycle)}")
            return
        if slug in visited:
            return
        visiting.add(slug)
        for target in graph.get(slug, []):
            if target in graph:
                visit(target, trail + [target])
        visiting.remove(slug)
        visited.add(slug)

    for slug in sorted(graph):
        visit(slug, [slug])
    return errors


def main() -> int:
    root = resolve_root(sys.argv[1] if len(sys.argv) > 1 else None)
    errors = validate(root)
    if errors:
        print(f"Knowledge graph validation failed for {root}", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(f"Knowledge graph metadata verified: {root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
