#!/usr/bin/env python3
"""Generate JSON and HTML indexes for a Markdown knowledge map."""

from __future__ import annotations

import html
import json
import re
import sys
from pathlib import Path


FRONTMATTER_RE = re.compile(r"^---\s*\n(.*?)\n---\s*\n", re.DOTALL)


def parse_scalar(value: str):
    value = value.strip()
    if not value:
        return []
    if value.startswith("[") and value.endswith("]"):
        inner = value[1:-1].strip()
        if not inner:
            return []
        return [item.strip().strip("\"'") for item in inner.split(",")]
    return value.strip("\"'")


def parse_frontmatter(text: str) -> dict:
    match = FRONTMATTER_RE.match(text)
    if not match:
        return {}

    data = {}
    current_key = None
    for raw_line in match.group(1).splitlines():
        line = raw_line.rstrip()
        if not line.strip():
            continue
        if line.startswith("  - ") and current_key:
            if not isinstance(data.get(current_key), list):
                data[current_key] = []
            data[current_key].append(line[4:].strip().strip("\"'"))
            continue
        if ":" in line:
            key, value = line.split(":", 1)
            current_key = key.strip()
            data[current_key] = parse_scalar(value)
    return data


def collect_nodes(root: Path) -> list[dict]:
    nodes_dir = root / "nodes"
    nodes = []
    for path in sorted(nodes_dir.rglob("*.md")):
        if path.name == "index.md":
            continue
        text = path.read_text(encoding="utf-8")
        meta = parse_frontmatter(text)
        rel = path.relative_to(root).as_posix()
        title = meta.get("title") or path.stem.replace("-", " ").title()
        nodes.append(
            {
                "title": title,
                "path": rel,
                "area": meta.get("area", path.parent.name),
                "status": meta.get("status", ""),
                "visibility": meta.get("visibility", "support"),
                "tags": meta.get("tags", []),
                "summary": meta.get("summary", ""),
                "related": meta.get("related", []),
                "prerequisites": meta.get("prerequisites", []),
            }
        )
    return nodes


def build_html(nodes: list[dict]) -> str:
    by_area = {}
    for node in nodes:
        by_area.setdefault(node["area"], []).append(node)

    sections = []
    for area, items in sorted(by_area.items()):
        cards = []
        for node in items:
            tags = " ".join(f"<span>{html.escape(tag)}</span>" for tag in node["tags"])
            cards.append(
                f"""
                <article class="node {html.escape(node["visibility"])}">
                  <a href="{html.escape(node["path"])}">{html.escape(node["title"])}</a>
                  <p>{html.escape(node["summary"])}</p>
                  <div class="meta">
                    <strong>{html.escape(node["visibility"])}</strong>
                    {tags}
                  </div>
                </article>
                """
            )
        sections.append(
            f"""
            <section>
              <h2>{html.escape(area)}</h2>
              <div class="grid">{''.join(cards)}</div>
            </section>
            """
        )

    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Personal CS Knowledge Map</title>
  <style>
    :root {{
      color-scheme: light;
      --ink: #18211f;
      --muted: #63706b;
      --paper: #f7f4ed;
      --line: #d9d1c3;
      --core: #0f766e;
      --support: #315f8c;
      --archive: #8a6f2a;
    }}
    body {{
      margin: 0;
      font-family: "Aptos", "Segoe UI", sans-serif;
      background: var(--paper);
      color: var(--ink);
    }}
    main {{
      width: min(1120px, calc(100% - 32px));
      margin: 40px auto;
    }}
    h1 {{
      font-size: 34px;
      margin: 0 0 8px;
    }}
    .lead {{
      color: var(--muted);
      margin: 0 0 28px;
    }}
    section {{
      border-top: 1px solid var(--line);
      padding: 24px 0;
    }}
    h2 {{
      margin: 0 0 14px;
      font-size: 20px;
      text-transform: capitalize;
    }}
    .grid {{
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 12px;
    }}
    .node {{
      background: rgba(255, 255, 255, 0.62);
      border: 1px solid var(--line);
      border-left: 4px solid var(--support);
      border-radius: 8px;
      padding: 14px;
    }}
    .node.core {{ border-left-color: var(--core); }}
    .node.archive {{ border-left-color: var(--archive); }}
    a {{
      color: var(--ink);
      font-weight: 700;
      text-decoration: none;
    }}
    p {{
      color: var(--muted);
      line-height: 1.5;
    }}
    .meta {{
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      font-size: 12px;
      color: var(--muted);
    }}
    .meta span, .meta strong {{
      border: 1px solid var(--line);
      border-radius: 999px;
      padding: 2px 8px;
      background: rgba(255, 255, 255, 0.5);
    }}
  </style>
</head>
<body>
  <main>
    <h1>Personal CS Knowledge Map</h1>
    <p class="lead">Generated from Markdown nodes. Markdown stays the source of truth.</p>
    {''.join(sections)}
  </main>
</body>
</html>
"""


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    if not (root / "nodes").exists():
        print(f"Missing nodes directory: {root / 'nodes'}", file=sys.stderr)
        return 1

    nodes = collect_nodes(root)
    (root / "index.json").write_text(
        json.dumps({"nodes": nodes}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    (root / "index.html").write_text(build_html(nodes), encoding="utf-8")
    print(f"Indexed {len(nodes)} nodes in {root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
