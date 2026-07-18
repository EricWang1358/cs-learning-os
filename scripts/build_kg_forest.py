"""Build a KnowledgeGraph forest from the existing node corpus.

Hybrid strategy (user choice):
- Big top-level roots become independent kg_question trees.
- Small top-level roots are grouped by (area, track) into synthetic bundle questions.
- Isolated nodes become singleton questions.
- Missing prerequisite edges are inferred from token-Jaccard similarity within the
  same area/track, with cycle detection.

All writes are idempotent (deterministic command ids + kg_processed_commands),
so the script is safe to re-run.

Run:
    .venv/Scripts/python.exe scripts/build_kg_forest.py
    .venv/Scripts/python.exe scripts/build_kg_forest.py --dry-run
"""
from __future__ import annotations

import argparse
import os
import re
import sys
from collections import defaultdict
from pathlib import Path
from typing import Optional

# Allow importing backend modules as top-level packages.
REPO_ROOT = Path(__file__).resolve().parents[1]
BACKEND_ROOT = REPO_ROOT / "backend"
sys.path.insert(0, str(BACKEND_ROOT))

from kg_engine import find_cycle_path  # noqa: E402
from kg_store import KgGraphStore  # noqa: E402
from node_lifecycle_service import (  # noqa: E402
    node_markdown_template,
    slugify,
    upsert_node_file_in_conn,
)

DEFAULT_DB = Path(
    os.environ.get("CS_LEARNING_DB", str(REPO_ROOT / "data" / "knowledge.db"))
).expanduser().resolve()
_DEFAULT_CONTENT_DIR = REPO_ROOT / "data" / "content"
DEFAULT_CONTENT_ROOT = Path(
    os.environ.get(
        "CS_LEARNING_CONTENT",
        str(_DEFAULT_CONTENT_DIR if _DEFAULT_CONTENT_DIR.is_dir() else REPO_ROOT / "content-demo"),
    )
).expanduser().resolve()

TOKEN_RE = re.compile(r"[A-Za-z][A-Za-z0-9+#]*|[一-鿿]{2,}")
STOPWORDS = frozenset({
    "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "with", "how",
    "what", "why", "is", "are", "do", "does", "you", "your", "we", "this", "that",
    "的", "了", "在", "是", "和", "与", "或", "一个", "如何", "什么", "为什么",
})


def tokenize(text: str) -> set[str]:
    return {
        t.lower()
        for t in TOKEN_RE.findall(text or "")
        if t.lower() not in STOPWORDS and len(t) >= 2
    }


def category_for_area(area: str) -> str:
    a = area.lower()
    if "algorithm" in a:
        return "ALGORITHM"
    if any(x in a for x in ("system", "network", "attack", "stack")):
        return "SYSTEM_DESIGN"
    if "behavior" in a or "ability" in a:
        return "BEHAVIORAL"
    return "CS_BASIC"


class NodeInfo:
    def __init__(self, row):
        self.slug: str = row["slug"]
        self.title: str = row["title"]
        self.area: str = row["area"]
        self.track: str = row["track"]
        self.summary: str = row["summary"] or ""
        self.body: str = row["body"] or ""
        self.display_order: int = row["display_order"]
        self.tags: set[str] = set()
        self.tokens: set[str] = tokenize(f"{self.title}\n{self.summary}\n{self.body}")


def load_nodes_and_tags(store: KgGraphStore):
    rows = store.read(lambda cur: cur.execute(
        """
        SELECT slug, title, area, track, summary, body, display_order
        FROM nodes
        WHERE visibility NOT IN ('archive', 'trash')
        """
    ).fetchall())
    nodes: dict[str, NodeInfo] = {}
    for row in rows:
        nodes[row["slug"]] = NodeInfo(row)
    tag_rows = store.read(lambda cur: cur.execute(
        "SELECT node_slug, tag_name FROM node_tags"
    ).fetchall())
    for r in tag_rows:
        if r["node_slug"] in nodes:
            nodes[r["node_slug"]].tags.add(r["tag_name"])
            nodes[r["node_slug"]].tokens.add(r["tag_name"].lower())
    return nodes


def assert_content_db_alignment(store: KgGraphStore, content_root: Path) -> None:
    """Reject writes when the DB was last ingested from another content root."""
    row = store.read(
        lambda cur: cur.execute(
            "SELECT value FROM schema_meta WHERE key = 'last_ingest_content_root'"
        ).fetchone()
    )
    if row is None or not row[0]:
        return
    recorded = Path(row[0]).expanduser().resolve()
    requested = Path(content_root).expanduser().resolve()
    if recorded != requested:
        raise RuntimeError(
            "Content root does not match the database ingest root: "
            f"db={recorded} requested={requested}. "
            "Run ingest with the requested root first or pass the matching --content-root."
        )


def load_prereq_links(store: KgGraphStore) -> list[tuple[str, str]]:
    rows = store.read(lambda cur: cur.execute(
        "SELECT source_slug, target_slug FROM links WHERE kind = 'prerequisite'"
    ).fetchall())
    return [(r["source_slug"], r["target_slug"]) for r in rows]


def build_children(links: list[tuple[str, str]]) -> dict[str, list[str]]:
    children: dict[str, list[str]] = defaultdict(list)
    for src, tgt in links:
        children[src].append(tgt)
    return children


def compute_degrees(nodes: dict[str, NodeInfo], children: dict[str, list[str]]):
    outdeg: dict[str, int] = {slug: len(children.get(slug, [])) for slug in nodes}
    indeg: dict[str, int] = {slug: 0 for slug in nodes}
    for src, tgts in children.items():
        for tgt in tgts:
            if tgt in indeg:
                indeg[tgt] += 1
    return outdeg, indeg


def reachable_from(root: str, children: dict[str, list[str]]) -> set[str]:
    seen: set[str] = set()
    stack = [root]
    while stack:
        n = stack.pop()
        if n in seen:
            continue
        seen.add(n)
        for c in children.get(n, []):
            if c not in seen:
                stack.append(c)
    return seen


def path_to_leaf_length(n: str, children: dict[str, list[str]], memo: dict[str, int]) -> int:
    if n in memo:
        return memo[n]
    kids = children.get(n, [])
    if not kids:
        memo[n] = 0
        return 0
    memo[n] = 1 + max(path_to_leaf_length(c, children, memo) for c in kids if c != n)
    return memo[n]


def is_bundle_slug(slug: str) -> bool:
    return slug.startswith("kg-bundle-")


def infer_missing_edges(
    nodes: dict[str, NodeInfo],
    children: dict[str, list[str]],
    outdeg: dict[str, int],
    indeg: dict[str, int],
    max_inferred: int,
    sim_threshold: float,
) -> list[tuple[str, str]]:
    """Infer prerequisite edges for isolated original nodes using token Jaccard."""
    # Pre-compute depth-to-leaf to prefer more foundational candidates.
    depth_memo: dict[str, int] = {}
    for slug in nodes:
        path_to_leaf_length(slug, children, depth_memo)

    inferred: list[tuple[str, str]] = []
    for slug, node in nodes.items():
        # Only connect isolated *original* nodes; never wire into/out of synthetic bundles.
        if is_bundle_slug(slug) or outdeg.get(slug, 0) >= 1 or indeg.get(slug, 0) > 0:
            continue
        candidates = []
        for other_slug, other in nodes.items():
            if other_slug == slug or is_bundle_slug(other_slug):
                continue
            if other.area != node.area and other.track != node.track:
                continue
            # avoid creating a cycle: candidate must not already be reachable from slug
            if other_slug in reachable_from(slug, children):
                continue
            inter = node.tokens & other.tokens
            if not inter:
                continue
            union = node.tokens | other.tokens
            jaccard = len(inter) / len(union)
            tag_bonus = 0.1 * len(node.tags & other.tags)
            depth_bonus = 0.05 * max(0, depth_memo.get(other_slug, 0) - depth_memo.get(slug, 0))
            score = jaccard + tag_bonus + depth_bonus
            if score >= sim_threshold:
                candidates.append((score, other_slug))
        candidates.sort(reverse=True)
        for _, target in candidates[:max_inferred]:
            inferred.append((slug, target))
            # update graph on the fly so later candidates see new reachability
            children[slug].append(target)
    return inferred


def select_core_leaves(
    leaves: list[str],
    nodes: dict[str, NodeInfo],
    indeg: dict[str, int],
    limit: int = 20,
) -> list[tuple[str, int]]:
    scored = [
        (slug, indeg.get(slug, 0) + len(nodes[slug].tokens) // 50)
        for slug in leaves if not is_bundle_slug(slug)
    ]
    scored.sort(key=lambda x: (-x[1], x[0]))
    return scored[:limit]


class TreePlan:
    def __init__(self, root_node_id: str, title: str, area_id: str, category: str, bundle: bool = False):
        self.root_node_id = root_node_id
        self.title = title
        self.area_id = area_id
        self.category = category
        self.bundle = bundle
        self.edges: set[tuple[str, str]] = set()  # (parent, child)


def build_forest(
    nodes: dict[str, NodeInfo],
    children: dict[str, list[str]],
    outdeg: dict[str, int],
    indeg: dict[str, int],
    big_threshold: int,
) -> list[TreePlan]:
    plans: list[TreePlan] = []

    roots = [
        s for s in nodes
        if not is_bundle_slug(s) and indeg[s] == 0 and outdeg[s] > 0
    ]
    isolated = [
        s for s in nodes
        if not is_bundle_slug(s) and indeg[s] == 0 and outdeg[s] == 0
    ]

    big_roots = []
    small_roots: list[str] = []
    for r in roots:
        if len(reachable_from(r, children)) >= big_threshold:
            big_roots.append(r)
        else:
            small_roots.append(r)

    # Independent big-root questions
    for r in sorted(big_roots):
        n = nodes[r]
        plan = TreePlan(
            root_node_id=r,
            title=f"How to: {n.title}",
            area_id=n.area,
            category=category_for_area(n.area),
        )
        for src in reachable_from(r, children):
            for tgt in children.get(src, []):
                plan.edges.add((src, tgt))
        plans.append(plan)

    # Group small roots by (area, track) into bundle questions
    groups: dict[tuple[str, str], list[str]] = defaultdict(list)
    for r in sorted(small_roots):
        n = nodes[r]
        groups[(n.area, n.track)].append(r)

    for (area, track), members in sorted(groups.items()):
        bundle_slug = slugify(f"kg-bundle-{area}-{track}")
        bundle_title = f"Review bundle · {area} / {track}"
        plan = TreePlan(
            root_node_id=bundle_slug,
            title=bundle_title,
            area_id=area,
            category=category_for_area(area),
            bundle=True,
        )
        for member in members:
            plan.edges.add((bundle_slug, member))
            for src in reachable_from(member, children):
                for tgt in children.get(src, []):
                    plan.edges.add((src, tgt))
        plans.append(plan)

    # Singleton questions for isolated nodes
    for s in sorted(isolated):
        n = nodes[s]
        plans.append(TreePlan(
            root_node_id=s,
            title=f"Review: {n.title}",
            area_id=n.area,
            category=category_for_area(n.area),
        ))

    return plans


def create_bundle_node(
    store: KgGraphStore,
    content_root: Path,
    slug: str,
    title: str,
    area: str,
    track: str,
):
    node_dir = content_root / "nodes" / area
    node_dir.mkdir(parents=True, exist_ok=True)
    path = node_dir / f"{slug}.md"
    text = node_markdown_template(
        slug=slug,
        title=title,
        area=area,
        track=track,
        summary=f"Auto-generated review bundle for {area} / {track}.",
        tags=["kg-bundle"],
        visibility="support",
        status="draft",
        order=1000,
    )
    path.write_text(text, encoding="utf-8")
    upsert_node_file_in_conn(store.connection, content_root, path)
    return slug


def ensure_bundle_nodes(
    store: KgGraphStore,
    content_root: Path,
    plans: list[TreePlan],
    nodes: dict[str, NodeInfo],
):
    for plan in plans:
        if not plan.bundle:
            continue
        if plan.root_node_id in nodes:
            continue
        area, track = plan.area_id, "general"
        create_bundle_node(store, content_root, plan.root_node_id, plan.title, area, track)
        # refresh our in-memory node set so edge inserts can reference it
        row = store.read(lambda cur: cur.execute(
            "SELECT slug, title, area, track, summary, body, display_order FROM nodes WHERE slug = ?",
            (plan.root_node_id,),
        ).fetchone())
        if row:
            nodes[plan.root_node_id] = NodeInfo(row)


def next_problem_no(store: KgGraphStore, area_id: str) -> int:
    row = store.read(lambda cur: cur.execute(
        """
        SELECT COALESCE(MAX(problem_no), 0) + 1 AS nxt
        FROM kg_question
        WHERE status != 'ARCHIVED' AND COALESCE(area_id, '') = ?
        """,
        (area_id or "",),
    ).fetchone())
    return row["nxt"]


def write_forest(
    store: KgGraphStore,
    plans: list[TreePlan],
    created_by: str = "IMPORT",
):
    problem_no_cursor: dict[str, int] = {}

    def get_problem_no(area_id: str) -> int:
        if area_id not in problem_no_cursor:
            problem_no_cursor[area_id] = next_problem_no(store, area_id)
        n = problem_no_cursor[area_id]
        problem_no_cursor[area_id] = n + 1
        return n

    created_questions = 0
    created_edges = 0

    for plan in plans:
        # Skip questions that already point to this root (e.g., from a previous run).
        existing_q = store.read(lambda cur: cur.execute(
            "SELECT question_id FROM kg_question WHERE root_node_id = ? AND status = 'ACTIVE' LIMIT 1",
            (plan.root_node_id,),
        ).fetchone())

        if existing_q is None:
            q_command_id = f"build-forest-v2-q-{plan.root_node_id}"
            q_fp = {"op": "buildForestQuestion", "root": plan.root_node_id, "title": plan.title}

            def make_q_writer(p: TreePlan):
                def q_writer(cur):
                    problem_no = get_problem_no(p.area_id)
                    qid = store.new_id()
                    cur.execute(
                        """
                        INSERT INTO kg_question (
                            question_id, root_node_id, area_id, problem_no, title,
                            category, jd_batch_id, status, revision, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 1, ?)
                        """,
                        (qid, p.root_node_id, p.area_id, problem_no, p.title,
                         p.category, None, store.now()),
                    )
                    store.append_outbox(cur, "kg_question", qid, "INSERT",
                                        {"title": p.title, "category": p.category})
                    return {"questionId": qid, "rootNodeId": p.root_node_id}
                return q_writer

            result, replayed = store.run_idempotent(q_command_id, q_fp, make_q_writer(plan))
            if not replayed:
                created_questions += 1

        for parent, child in sorted(plan.edges):
            e_command_id = f"build-forest-v2-edge-{parent}-{child}"
            e_fp = {"op": "buildForestEdge", "parent": parent, "child": child}

            def make_e_writer(p_parent: str, p_child: str):
                def e_writer(cur):
                    existing = cur.execute(
                        """
                        SELECT edge_id FROM kg_edge
                        WHERE parent_node_id = ? AND child_node_id = ?
                          AND scope_type = 'GLOBAL'
                          AND COALESCE(scope_question_id, '') = ''
                          AND status != 'REJECTED'
                        """,
                        (p_parent, p_child),
                    ).fetchone()
                    if existing:
                        return {"edgeId": existing["edge_id"], "created": False}
                    active = [
                        (r["parent_node_id"], r["child_node_id"])
                        for r in cur.execute(
                            "SELECT parent_node_id, child_node_id FROM kg_edge WHERE status = 'ACTIVE'"
                        ).fetchall()
                    ]
                    if find_cycle_path(active, p_parent, p_child) is not None:
                        return {"edgeId": None, "created": False, "skippedCycle": True}
                    eid = store.new_id()
                    cur.execute(
                        """
                        INSERT INTO kg_edge (
                            edge_id, parent_node_id, child_node_id, scope_type,
                            scope_question_id, status, created_by, revision, created_at
                        ) VALUES (?, ?, ?, 'GLOBAL', NULL, 'ACTIVE', ?, 1, ?)
                        """,
                        (eid, p_parent, p_child, created_by, store.now()),
                    )
                    store.append_outbox(cur, "kg_edge", eid, "INSERT",
                                        {"parent": p_parent, "child": p_child, "scope": "GLOBAL"})
                    return {"edgeId": eid, "created": True}
                return e_writer

            e_result, e_replayed = store.run_idempotent(
                e_command_id, e_fp, make_e_writer(parent, child)
            )
            if e_result.get("created") and not e_replayed:
                created_edges += 1

    return created_questions, created_edges


def main():
    parser = argparse.ArgumentParser(description="Build KG forest from existing nodes")
    parser.add_argument("--db", type=Path, default=DEFAULT_DB)
    parser.add_argument("--content-root", type=Path, default=DEFAULT_CONTENT_ROOT)
    parser.add_argument("--big-threshold", type=int, default=5,
                        help="Minimum closure size for an independent question root")
    parser.add_argument("--max-inferred", type=int, default=2,
                        help="Max inferred prerequisite edges per under-linked node")
    parser.add_argument("--sim-threshold", type=float, default=0.15,
                        help="Minimum Jaccard+score for inferred edges")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    store = KgGraphStore(str(args.db))
    try:
        assert_content_db_alignment(store, args.content_root)
        print(f"Loading nodes from {args.db} ...")
        nodes = load_nodes_and_tags(store)
        print(f"  {len(nodes)} nodes")

        links = load_prereq_links(store)
        print(f"  {len(links)} explicit prerequisite links")

        children = build_children(links)
        outdeg, indeg = compute_degrees(nodes, children)

        inferred = infer_missing_edges(
            nodes, children, outdeg, indeg,
            max_inferred=args.max_inferred,
            sim_threshold=args.sim_threshold,
        )
        print(f"  {len(inferred)} inferred prerequisite edges")

        # recompute after inference
        outdeg, indeg = compute_degrees(nodes, children)

        leaves = [s for s in nodes if outdeg[s] == 0 and indeg[s] > 0]
        core_leaves = select_core_leaves(leaves, nodes, indeg)
        print(f"\nTop core leaves (shared prerequisites):")
        for slug, score in core_leaves[:10]:
            print(f"  {slug:<45} shared={indeg[slug]:<3} score={score}")

        plans = build_forest(nodes, children, outdeg, indeg, args.big_threshold)
        print(f"\nForest plan: {len(plans)} questions")
        print(f"  big-root questions:  {sum(1 for p in plans if not p.bundle and p.root_node_id in nodes and outdeg[p.root_node_id] > 0)}")
        print(f"  bundle questions:    {sum(1 for p in plans if p.bundle)}")
        print(f"  singleton questions: {sum(1 for p in plans if not p.bundle and outdeg[p.root_node_id] == 0)}")

        covered = set()
        for plan in plans:
            covered.add(plan.root_node_id)
            for parent, child in plan.edges:
                covered.add(parent)
                covered.add(child)
        print(f"  nodes covered:       {len(covered)} / {len(nodes)}")

        if args.dry_run:
            print("\nDry run complete; no writes.")
            return

        print("\nEnsuring bundle nodes exist ...")
        ensure_bundle_nodes(store, args.content_root, plans, nodes)

        print("Writing questions and edges ...")
        created_q, created_e = write_forest(store, plans)
        print(f"  created questions: {created_q}")
        print(f"  created edges:     {created_e}")
        print("\nDone. Refresh /knowledge-graph to see the new trees.")
    finally:
        store.close()


if __name__ == "__main__":
    main()
