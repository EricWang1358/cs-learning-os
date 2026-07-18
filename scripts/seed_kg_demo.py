"""Seed demo knowledge-graph trees from REAL production nodes (desktop DB).

4 question trees with deliberate subtree intersections (RFC-knowledge-graph):
  Q1 Bomb Lab Phase 2      -> gdb-disassemble -> gdb-basics ┐
  Q2 Bomb Lab Secret Phase -> gdb-examine-memory -> gdb-basics ├ shared ×3 trees
  Q3 Attack Lab Phase 2    -> gdb-stepi -> gdb-basics ┘
  Q3/Q4 share buffer-overflow-techniques -> stack-frame -> stack-basics (×2)
  Q2/Q4 share x86-64-calling-convention (×2); Q1/Q2 share x86-64-registers (×2)

Verifications paint the mastery map: gdb-basics FAIL×2 (top bottleneck, 3
trees), buffer-overflow FAIL×1 (bottleneck, 2 trees), several MASTERED /
LEARNING nodes for color variety. Quiz ids use real quizzes where they exist.

Fully idempotent: fixed commandIds — safe to re-run, replayed writes are skipped.
Run: .venv/Scripts/python.exe scripts/seed_kg_demo.py  (API must be running)
"""
from __future__ import annotations

import json
import urllib.request
import urllib.error

API = "http://127.0.0.1:8000"


def call(method: str, path: str, body: dict | None = None) -> dict:
    req = urllib.request.Request(
        f"{API}{path}",
        method=method,
        data=json.dumps(body).encode("utf-8") if body is not None else None,
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        raise SystemExit(f"{method} {path} -> HTTP {exc.code}: {exc.read().decode()}")


def ex(node_id: str, children: list | None = None, title: str = "") -> dict:
    """PrerequisiteSpec referencing an EXISTING real node (shared subtree)."""
    spec = {"title": title or node_id, "existingNodeId": node_id,
            "markdownBody": "", "children": children or []}
    return spec


# ---------------------------------------------------------------- tree specs
TREES = [
    {
        "key": "q1",
        "title": "Demo · Bomb Lab Phase 2：读出循环与比较逻辑",
        "category": "CS_BASIC",
        "specs": [ex("bomb-lab-phase2-core", [
            ex("gdb-disassemble", [ex("gdb-basics")]),
            ex("x86-64-cmp-and-jumps", [
                ex("x86-64-mov-and-suffixes", [ex("x86-64-registers")]),
            ]),
        ])],
    },
    {
        "key": "q2",
        "title": "Demo · Bomb Lab Secret Phase：递归与调用约定",
        "category": "CS_BASIC",
        "specs": [ex("bomb-lab-secret-phase-core", [
            ex("recursion", [
                ex("x86-64-calling-convention", [ex("x86-64-registers")]),
            ]),
            ex("gdb-examine-memory", [ex("gdb-basics")]),
        ])],
    },
    {
        "key": "q3",
        "title": "Demo · Attack Lab Phase 2：栈注入布置",
        "category": "CS_BASIC",
        "specs": [ex("attacklab-phase2-core", [
            ex("buffer-overflow-techniques", [
                ex("stack-frame", [ex("stack-basics")]),
            ]),
            ex("gdb-stepi", [ex("gdb-basics")]),
        ])],
    },
    {
        "key": "q4",
        "title": "Demo · Attack Lab Phase 4：ROP 链构造",
        "category": "CS_BASIC",
        "specs": [ex("attacklab-phase4-core", [
            ex("rop-fundamentals", [
                ex("buffer-overflow-techniques", [
                    ex("stack-frame", [ex("stack-basics")]),
                ]),
                ex("x86-64-calling-convention"),
            ]),
        ])],
    },
]

# (node, quiz, verdict) — real quiz ids where they exist
VERIFICATIONS = [
    ("gdb-basics", "gdb-basics-quiz", "FAIL"),
    ("gdb-basics", "gdb-basics-quiz", "FAIL"),                      # FRAGILE, 3 trees → 瓶颈 #1
    ("buffer-overflow-techniques", "demo-quiz-buffer-overflow", "FAIL"),  # FRAGILE, 2 trees → 瓶颈 #2
    ("rop-fundamentals", "demo-quiz-rop", "FAIL"),                  # FRAGILE but only 1 tree → 对照组
    ("x86-64-registers", "x86-64-trace-rax-two-functions-wa2", "PASS"),
    ("x86-64-registers", "x86-64-trace-rax-two-functions-wa2", "PASS"),
    ("x86-64-registers", "x86-64-trace-rax-two-functions-wa2", "PASS"),
    ("x86-64-registers", "x86-64-trace-rax-two-functions-wa2", "PASS"),  # MASTERED
    ("stack-basics", "demo-quiz-stack-basics", "PASS"),
    ("stack-basics", "demo-quiz-stack-basics", "PASS"),
    ("stack-basics", "demo-quiz-stack-basics", "PASS"),
    ("stack-basics", "demo-quiz-stack-basics", "PASS"),             # MASTERED
    ("gdb-disassemble", "gdb-basics-quiz", "PASS"),
    ("gdb-disassemble", "gdb-basics-quiz", "PASS"),                 # LEARNING 0.4
    ("x86-64-calling-convention", "shark-tank-passcode-calling-convention", "PASS"),  # LEARNING
    ("stack-frame", "demo-quiz-stack-frame", "PASS"),               # LEARNING
    ("bomb-lab-phase2-core", "demo-quiz-bomb-phase2", "PASS"),
    ("bomb-lab-phase2-core", "demo-quiz-bomb-phase2", "PASS"),
    ("bomb-lab-phase2-core", "demo-quiz-bomb-phase2", "PASS"),      # LEARNING 0.6
]


def main() -> None:
    question_ids: dict[str, str] = {}
    for tree in TREES:
        created = call("POST", "/api/kg/questions", {
            "commandId": f"demo-kg-{tree['key']}-create",
            "title": tree["title"],
            "category": tree["category"],
            "areaId": "knowledge-graph",
        })
        qid = created["questionId"]
        question_ids[tree["key"]] = qid
        snap = call("POST", f"/api/kg/questions/{qid}/prerequisites", {
            "commandId": f"demo-kg-{tree['key']}-specs",
            "specs": tree["specs"],
            "scope": "GLOBAL",
        })
        tag = "replayed" if created.get("replayed") else "created"
        print(f"[{tag}] {tree['key']}: {tree['title']}")
        print(f"    questionId={qid}  nodes={len(snap['nodes'])}  edges={len(snap['edges'])}"
              f"  shared={len(snap['sharedNodeIds'])}")

    for i, (node, quiz, verdict) in enumerate(VERIFICATIONS):
        result = call("POST", "/api/kg/verifications", {
            "commandId": f"demo-kg-verify-{i:02d}",
            "nodeId": node,
            "quizItemId": quiz,
            "verdict": verdict,
        })
        state = result["mastery"]["state"]
        print(f"  verify {node:<28} {verdict:<4} -> {state}"
              f" ({'replayed' if result.get('replayed') else 'recorded'})")

    print("\n--- bottlenecks (threshold 2) ---")
    for item in call("GET", "/api/kg/bottlenecks")["items"]:
        print(f"  {item['title']:<40} {item['mastery']:<8}"
              f" trees={item['dependentFailCount']} blocks={item['blocksCount']}")

    print("\n--- export3d sanity ---")
    for key, qid in question_ids.items():
        payload = call("GET", f"/api/kg/export3d?root={qid}&rootIsQuestion=true")
        shared = sum(1 for n in payload["nodes"] if n["sharedByQuestions"] >= 2)
        print(f"  {key}: nodes={len(payload['nodes'])} links={len(payload['links'])}"
              f" sharedNodes={shared} hash={payload['contentHash'][:12]}")

    print("\nDone. Open 知识树 3D (/knowledge-graph) and refresh.")


if __name__ == "__main__":
    main()
