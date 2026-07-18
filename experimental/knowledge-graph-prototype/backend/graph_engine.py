"""Pure-Python graph algorithms for the KnowledgeGraph deep module.

Implements RFC-knowledge-graph §3.1 (invariants), §3.4 (3D export contract) and
§3.5 (offline rule engine) as side-effect-free functions over plain data
structures, so the same logic can be unit-tested without a database and kept in
parity with the Kotlin/Room implementation.

No I/O, no sqlite imports here.
"""
from __future__ import annotations

import hashlib
import json
import re
from collections import deque

# --- §3.5 rule-engine constants (verbatim from the RFC) ----------------------
PASS_DELTA = 0.2      # PASS → score = min(1, score + 0.2)
FAIL_DELTA = 0.25     # FAIL → score = max(0, score - 0.25)
MASTERED_SCORE = 0.8  # MASTERED 需 score >= 0.8 且近 3 次无 FAIL
WEAK_SCORE = 0.6      # bottlenecks 候选: score < 0.6 的 FRAGILE/LEARNING 节点
RECENT_WINDOW = 3     # "近 3 次无 FAIL"

CATEGORIES = ("CS_BASIC", "ALGORITHM", "SYSTEM_DESIGN", "BEHAVIORAL")
EDGE_SCOPES = ("GLOBAL", "PROBLEM_LOCAL")
VERDICTS = ("PASS", "FAIL")


# ---------------------------------------------------------------------------
# fingerprint: canonical JSON (sorted keys) → SHA-256, RFC §3.1 幂等命令不变量
# ---------------------------------------------------------------------------

def canonical_json(obj) -> str:
    """Canonical JSON: sorted keys + tight separators → stable across processes."""
    return json.dumps(obj, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def fingerprint(obj) -> str:
    return hashlib.sha256(canonical_json(obj).encode("utf-8")).hexdigest()


# ---------------------------------------------------------------------------
# 环检测 — 保守规则 (RFC §3.1 / ADR-1): 调用方传入 GLOBAL∪(全部或本问题) LOCAL
# 的 ACTIVE 边集; 判断新增 parent→child 是否成环, 并返回用户可读的成环路径。
# 边方向: parent 依赖 child (child 是前置)。
# ---------------------------------------------------------------------------

def find_cycle_path(edges, parent, child):
    """Return the cycle path ``[parent, child, ..., parent]`` if adding the edge
    ``parent → child`` closes a cycle in ``edges`` (iterable of (parent, child)
    pairs), else ``None``. Diamonds do not false-positive: we require an actual
    path ``child ⇢ parent`` in the existing graph."""
    if parent == child:
        return [parent, child]
    adjacency = {}
    for p, c in edges:
        adjacency.setdefault(p, []).append(c)
    # BFS: does a path child ⇢ parent already exist (following edge direction)?
    prev = {child: None}
    queue = deque([child])
    while queue:
        node = queue.popleft()
        if node == parent:
            break
        for nxt in adjacency.get(node, ()):
            if nxt not in prev:
                prev[nxt] = node
                queue.append(nxt)
    if parent not in prev:
        return None
    back = []
    node = parent
    while node is not None:
        back.append(node)
        node = prev[node]
    back.reverse()                 # child → … → parent
    return [parent] + back         # parent → child → … → parent (闭环路径)


# ---------------------------------------------------------------------------
# 可达性 / 反向可达 / 最长路径分层 / 树物化 (树 = 视图, RFC §3.1)
# ---------------------------------------------------------------------------

def reachable_set(root, edges):
    """正向可达: 从 root 沿 parent→child 方向可达的节点集 (含 root)。"""
    adjacency = {}
    for p, c in edges:
        adjacency.setdefault(p, []).append(c)
    seen = {root}
    queue = deque([root])
    while queue:
        node = queue.popleft()
        for nxt in adjacency.get(node, ()):
            if nxt not in seen:
                seen.add(nxt)
                queue.append(nxt)
    return seen


def dependents_set(node, edges):
    """反向可达: 所有(传递)依赖于 node 的下游节点 — blocksCount 的基数 (§3.5)。"""
    reverse = {}
    for p, c in edges:
        reverse.setdefault(c, []).append(p)
    seen = set()
    stack = [node]
    while stack:
        cur = stack.pop()
        for p in reverse.get(cur, ()):
            if p not in seen:
                seen.add(p)
                stack.append(p)
    return seen


def longest_path_layers(root, edges):
    """layer(n) = 从 root 出发的最长拓扑距离 (RFC §3.4)。DAG 由环检测保证。"""
    parents = {}
    nodes = {root}
    for p, c in edges:
        parents.setdefault(c, []).append(p)
        nodes.add(p)
        nodes.add(c)
    memo = {root: 0}

    def layer(n):
        if n in memo:
            return memo[n]
        memo[n] = 0  # guard (输入必为 DAG)
        memo[n] = max((layer(p) + 1 for p in parents.get(n, ())), default=0)
        return memo[n]

    return {n: layer(n) for n in nodes}


def materialize_tree(root, edge_rows):
    """树物化: 从 root 沿给定边集可达的子图 + 最长路径分层 + 共享节点标记。

    ``edge_rows``: iterable of dicts 至少含键 ``parent`` / ``child`` (其余键原样保留)。
    返回 dict(node_ids, edges, layers_map, layers, parent_count, shared_node_ids):
      - layers: [[nodeId...], ...] 按层分组, 层内按 id 排序 (稳定输出);
      - shared_node_ids: parentCount >= 2 的节点 (共享子树, 零拷贝)。
    """
    edge_rows = list(edge_rows)
    pairs = [(e["parent"], e["child"]) for e in edge_rows]
    nodes = reachable_set(root, pairs)
    edges = [e for e in edge_rows if e["parent"] in nodes and e["child"] in nodes]
    layers_map = longest_path_layers(root, [(e["parent"], e["child"]) for e in edges])
    # parentCount: 在输入边集上统计入度 — 父节点可不在本树可达集内
    # (跨树共享场景: B 树 root → 共享节点 的 GLOBAL 边也属于 A 树的边集)。
    parent_count = {n: 0 for n in nodes}
    for e in edge_rows:
        if e["child"] in nodes:
            parent_count[e["child"]] = parent_count.get(e["child"], 0) + 1
    groups = {}
    for n in nodes:
        groups.setdefault(layers_map.get(n, 0), []).append(n)
    layers = [sorted(groups[d]) for d in sorted(groups)]
    shared = sorted(n for n, cnt in parent_count.items() if cnt >= 2)
    return {
        "node_ids": nodes,
        "edges": edges,
        "layers_map": layers_map,
        "layers": layers,
        "parent_count": parent_count,
        "shared_node_ids": shared,
    }


# ---------------------------------------------------------------------------
# §3.5 瓶颈/掌握度规则引擎 (离线, 逐字实现)
# ---------------------------------------------------------------------------

def initial_mastery():
    """无证据节点的投影初值 (kg_mastery DDL DEFAULT: state=UNKNOWN, score=0.0)。"""
    return {"state": "UNKNOWN", "score": 0.0, "attempts": 0,
            "fail_streak": 0, "last_verdict": None}


def apply_verdict(mastery, recent_verdicts, verdict, now_ms):
    """RFC §3.5 逐字:

    - verdict FAIL → ``fail_streak+1``; ``score = max(0, score-0.25)``;
      state: ``attempts>=1 → FRAGILE (若 fail_streak>=1) 否则 LEARNING``。
    - verdict PASS → ``fail_streak=0``; ``score = min(1, score+0.2)``;
      state: ``score>=0.8 且近 3 次无 FAIL → MASTERED, 否则 LEARNING``。

    ``recent_verdicts`` 必须包含本次 verdict, 最新在前, 至多 RECENT_WINDOW 条。
    注: 从 0.0 起每次 PASS +0.2, 故连续第 4 次 PASS 才达到 0.8 → MASTERED;
    第 3 次 PASS 后 score=0.6 仍为 LEARNING (该算术由公式直接推出)。
    """
    m = dict(mastery)
    if verdict == "FAIL":
        m["fail_streak"] = int(m.get("fail_streak", 0)) + 1
        m["score"] = max(0.0, float(m.get("score", 0.0)) - FAIL_DELTA)
        m["attempts"] = int(m.get("attempts", 0)) + 1
        if m["attempts"] >= 1:
            m["state"] = "FRAGILE" if m["fail_streak"] >= 1 else "LEARNING"
    elif verdict == "PASS":
        m["fail_streak"] = 0
        m["score"] = min(1.0, float(m.get("score", 0.0)) + PASS_DELTA)
        m["attempts"] = int(m.get("attempts", 0)) + 1
        no_recent_fail = "FAIL" not in list(recent_verdicts)[:RECENT_WINDOW]
        m["state"] = "MASTERED" if (m["score"] >= MASTERED_SCORE and no_recent_fail) else "LEARNING"
    else:
        raise ValueError(f"unknown verdict: {verdict!r}")
    m["last_verdict"] = verdict
    m["updated_at"] = now_ms
    return m


def is_weak(mastery):
    """§3.5 bottlenecks 候选条件: FRAGILE/LEARNING 且 score < 0.6。"""
    return (mastery.get("state") in ("FRAGILE", "LEARNING")
            and float(mastery.get("score", 0.0)) < WEAK_SCORE)


def gap_priority(score, blocks_count):
    """§3.5 diagnoseGap 优先级: (1-mastery) × (1+blocksCount)。"""
    return (1.0 - float(score)) * (1.0 + int(blocks_count))


# ---------------------------------------------------------------------------
# §3.4 3D 导出 JSON 契约 (schemaVersion=1)
# ---------------------------------------------------------------------------

def export3d_payload(root_node_id, node_rows, edge_rows, mastery_rows, membership, now_ms):
    """构造 RFC §3.4 导出 JSON。

    - ``node_rows``: {node_id: {"title": ...}};
    - ``edge_rows``: dicts 含 parent/child/scope (导出链接 source=parent, target=child);
    - ``mastery_rows``: {node_id: mastery dict} (缺省 UNKNOWN/0.0);
    - ``membership``: {node_id: set(question_id)} → sharedByQuestions;
    - nodes 按 (layer, id)、links 按 (source, target, scope) 稳定排序;
    - contentHash = sha256(canonical JSON of {schemaVersion, nodes, links}),
      不含 generatedAt — 图不变则 hash 不变, 图变更则 hash 变更。
    """
    tree = materialize_tree(root_node_id, edge_rows)
    nodes = []
    for nid in tree["node_ids"]:
        m = mastery_rows.get(nid, initial_mastery())
        info = node_rows.get(nid, {})
        nodes.append({
            "id": nid,
            "title": info.get("title", ""),
            "layer": tree["layers_map"].get(nid, 0),
            "mastery": m.get("state", "UNKNOWN"),
            "score": float(m.get("score", 0.0)),
            "parentCount": tree["parent_count"].get(nid, 0),
            "sharedByQuestions": len(membership.get(nid, ())),
            "isRoot": nid == root_node_id,
        })
    nodes.sort(key=lambda n: (n["layer"], n["id"]))
    links = sorted(
        ({"source": e["parent"], "target": e["child"], "scope": e["scope"]}
         for e in tree["edges"]),
        key=lambda l: (l["source"], l["target"], l["scope"]),
    )
    content_hash = fingerprint({"schemaVersion": 1, "nodes": nodes, "links": links})
    return {
        "schemaVersion": 1,
        "contentHash": content_hash,
        "generatedAt": now_ms,
        "nodes": nodes,
        "links": links,
    }


# ---------------------------------------------------------------------------
# Deterministic heuristic proposal generators (AI 建议两阶段的第一阶段, 不落库)。
#
# 端口注释 — ModelGateway (feature:assistant): 生产环境这里应注入真实 LLM 网关,
# 它是 AI 建议的唯一来源 (RFC §4 端口与适配器)。本模块以确定性启发式替代,
# 保证离线可用、测试可重现; confirm 阶段的形状校验不依赖生成器来源。
# ---------------------------------------------------------------------------

_WORD_RE = re.compile(r"[A-Za-z][A-Za-z0-9+#]*|[一-鿿]{2,}")
_STOPWORDS = frozenset({
    "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "with", "how",
    "what", "why", "is", "are", "do", "does", "you", "your", "we", "this", "that",
})


def extract_keywords(text, limit=16):
    """从自由文本提取有序去重关键词 (中英混合, 确定性)。"""
    keywords, seen = [], set()
    for word in _WORD_RE.findall(text or ""):
        key = word.lower()
        if key in _STOPWORDS or key in seen:
            continue
        seen.add(key)
        keywords.append(word)
        if len(keywords) >= limit:
            break
    return keywords


def heuristic_prerequisite_tree(question_text, max_depth=3, max_breadth=5,
                                existing_by_title=None):
    """从 questionText 提取关键词生成 PrerequisiteSpec 树 (深度/广度受限, 确定性)。

    命中 ``existing_by_title`` (title.lower() → node_id) 的关键词复用已有节点
    (共享子树, existingNodeId), 并计入 reusedNodeIds。
    返回 (tree: PrerequisiteSpec-dict, reused_node_ids: list)。
    """
    existing_by_title = existing_by_title or {}
    max_depth = max(1, int(max_depth))
    max_breadth = max(1, int(max_breadth))
    keywords = (extract_keywords(question_text, limit=max_breadth * max_depth)
                or ["fundamentals"])
    reused = []

    def make_spec(title):
        node_id = existing_by_title.get(title.lower())
        if node_id:
            reused.append(node_id)
        return {"title": title, "existingNodeId": node_id,
                "markdownBody": "", "children": []}

    pool = list(keywords)
    root_children = [make_spec(t) for t in pool[:max_breadth]]
    del pool[:max_breadth]
    frontier = list(root_children)
    depth = 1
    while pool and depth < max_depth:
        next_frontier = []
        for spec in frontier:
            for _ in range(min(2, max_breadth)):
                if not pool:
                    break
                child = make_spec(pool.pop(0))
                spec["children"].append(child)
                next_frontier.append(child)
        frontier = next_frontier
        depth += 1
    tree = {
        "title": "前置知识链: " + ", ".join(keywords[:3]),
        "existingNodeId": None,
        "markdownBody": f"heuristic proposal for: {question_text}",
        "children": root_children,
    }
    return tree, reused


def heuristic_jd_questions(jd_text, categories=4, questions_per_category=30):
    """按 N 类 × M 生成题目骨架 (确定性): 标题 = 类别 + JD 关键词轮换 + 序号。"""
    keywords = extract_keywords(jd_text, limit=16) or ["engineering"]
    cats = list(CATEGORIES[:max(1, min(int(categories), len(CATEGORIES)))])
    per_category = max(1, int(questions_per_category))
    questions = []
    for ci, cat in enumerate(cats):
        for seq in range(1, per_category + 1):
            kw = keywords[(ci * per_category + seq - 1) % len(keywords)]
            questions.append({
                "category": cat,
                "seq": seq,
                "title": f"[{cat}] {kw} 专题 {seq:02d}",
                "seedPrerequisites": [
                    {"title": f"{kw} 基础", "existingNodeId": None,
                     "markdownBody": "", "children": []},
                    {"title": f"{kw} 进阶", "existingNodeId": None,
                     "markdownBody": "", "children": []},
                ],
            })
    return questions
