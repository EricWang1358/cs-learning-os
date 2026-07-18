"""FastAPI router for the KnowledgeGraph deep module — RFC-knowledge-graph §3.6.

Mounted under ``/api/kg`` (the package's bare ``/graph`` prefix collided with the
existing navigation-graph routes at ``/api/graph``).

14 endpoints (13 from RFC §3.6 + one additive list endpoint the desktop page needs):
  POST /api/kg/questions                       createQuestion
  GET  /api/kg/questions                       (additive) list registered question roots
  POST /api/kg/questions/{qid}/prerequisites   appendPrerequisites
  POST /api/kg/proposals/prerequisite-chain    proposePrerequisiteChain
  POST /api/kg/proposals/jd-decomposition      proposeJdDecomposition
  POST /api/kg/proposals/{pid}/confirm         confirmProposal
  POST /api/kg/verifications                   recordVerification
  GET  /api/kg/questions/{qid}/tree            treeOf
  GET  /api/kg/nodes/{nid}/subtree             subtreeOf (reroot)
  GET  /api/kg/quizzes/{qid}/gap               diagnoseGap
  GET  /api/kg/bottlenecks                     bottlenecks
  GET  /api/kg/batches/{bid}/progress          expertiseProgress
  GET  /api/kg/export3d?root=..&rootIsQuestion=..  export3d
  DELETE /api/kg/edges/{eid}                   detachEdge

Merge adaptations (package → real backend):
- kg node ids ARE real node slugs: every node the graph creates is written as a
  real markdown file under ``<content_root>/nodes/<area>/`` and upserted inside the
  same transaction (survives the ingest full-rebuild, syncs to mobile via
  sync_changes, shows up in the regular node list for editing).
- Quiz → node binding for diagnoseGap resolves through the real ``quiz_links``
  table first, falling back to the latest ``kg_mastery_event`` for that quiz id.
- Persistence: kg_store.KgGraphStore; algorithms: kg_engine (pure functions).
"""
from __future__ import annotations

import hashlib
import json
import sqlite3
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, FastAPI, Query, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

try:
    from .kg_engine import (
        CATEGORIES, EDGE_SCOPES, apply_verdict, dependents_set, export3d_payload,
        find_cycle_path, gap_priority, heuristic_jd_questions,
        heuristic_prerequisite_tree, initial_mastery, is_weak, materialize_tree,
        reachable_set,
    )
    from .kg_store import KgError, KgGraphStore
    from .node_lifecycle_service import (
        node_markdown_template, restore_file_on_failure, slugify,
        upsert_node_file_in_conn,
    )
except ImportError:  # pragma: no cover - script execution
    from kg_engine import (
        CATEGORIES, EDGE_SCOPES, apply_verdict, dependents_set, export3d_payload,
        find_cycle_path, gap_priority, heuristic_jd_questions,
        heuristic_prerequisite_tree, initial_mastery, is_weak, materialize_tree,
        reachable_set,
    )
    from kg_store import KgError, KgGraphStore
    from node_lifecycle_service import (
        node_markdown_template, restore_file_on_failure, slugify,
        upsert_node_file_in_conn,
    )


DEFAULT_KG_AREA = "knowledge-graph"
KG_TRACK = "kg"


# ---------------------------------------------------------------- request bodies
class CreateQuestionReq(BaseModel):
    commandId: str
    title: str
    category: str = "CS_BASIC"
    areaId: Optional[str] = None
    problemNo: Optional[int] = None          # default → auto-increment inside the area bucket
    jdBatchId: Optional[str] = None


class PrerequisiteSpec(BaseModel):
    """Nested prerequisite structure (RFC §3.2): multi-layer chains and
    "reference an existing node" (shared subtree) in one shape."""
    title: str
    existingNodeId: Optional[str] = None
    markdownBody: str = ""
    children: list["PrerequisiteSpec"] = Field(default_factory=list)


class AppendPrerequisitesReq(BaseModel):
    commandId: str
    parentNodeId: Optional[str] = None       # default = the question's root
    specs: list[PrerequisiteSpec]
    scope: str = "GLOBAL"                    # GLOBAL | PROBLEM_LOCAL
    scopeQuestionId: Optional[str] = None    # PROBLEM_LOCAL default = path qid


class ProposeChainReq(BaseModel):
    nodeId: str
    questionText: str
    maxDepth: int = 3
    maxBreadth: int = 5
    expiresInSeconds: int = 3600             # contract default now+1h


class ProposeJdReq(BaseModel):
    jdText: str
    categories: int = 4
    questionsPerCategory: int = 30
    expiresInSeconds: int = 3600


class ConfirmReq(BaseModel):
    commandId: str
    editedPayloadJson: Optional[str] = None  # JD flow supports overriding the payload


class VerificationReq(BaseModel):
    commandId: str
    nodeId: str
    quizItemId: str
    verdict: str                             # PASS | FAIL


PrerequisiteSpec.model_rebuild()


def normalize_kg_area(area_id: Optional[str]) -> str:
    """kg_question.area_id doubles as the on-disk area directory, so it must be a
    legal path segment on Windows (the package's ``batch:category`` form is not)."""
    return slugify(area_id) if area_id else DEFAULT_KG_AREA


def jd_area_id(batch_id: str, category: str) -> str:
    return normalize_kg_area(f"jd-{batch_id[:8]}-{category}")


# ------------------------------------------------------------------ router factory
def create_kg_router(store: KgGraphStore, content_root: Path) -> APIRouter:
    router = APIRouter(prefix="/api/kg", tags=["knowledge-graph"])
    content_root = Path(content_root)

    # ---------------- row loading helpers ----------------
    def get_node(cur, nid):
        return cur.execute("SELECT slug, title FROM nodes WHERE slug = ?", (nid,)).fetchone()

    def get_question(cur, qid):
        return cur.execute("SELECT * FROM kg_question WHERE question_id = ?", (qid,)).fetchone()

    def active_edge_rows(cur, qid=None):
        """ACTIVE edge set:
        qid=None → GLOBAL ∪ all LOCAL (conservative cycle check / reroot / gap /
        bottlenecks); qid given → GLOBAL ∪ PROBLEM_LOCAL(qid) (that question's
        tree view, RFC §3.1)."""
        if qid is None:
            rows = cur.execute("SELECT * FROM kg_edge WHERE status = 'ACTIVE'").fetchall()
        else:
            rows = cur.execute(
                "SELECT * FROM kg_edge WHERE status = 'ACTIVE' AND (scope_type = 'GLOBAL'"
                " OR (scope_type = 'PROBLEM_LOCAL' AND scope_question_id = ?))",
                (qid,)).fetchall()
        return [{"edgeId": r["edge_id"], "parent": r["parent_node_id"],
                 "child": r["child_node_id"], "scope": r["scope_type"],
                 "scope_qid": r["scope_question_id"]} for r in rows]

    def node_titles(cur, node_ids):
        ids = list(node_ids)
        if not ids:
            return {}
        marks = ",".join("?" * len(ids))
        return {r["slug"]: r["title"] for r in cur.execute(
            f"SELECT slug, title FROM nodes WHERE slug IN ({marks})", ids).fetchall()}

    def mastery_map(cur, node_ids):
        ids = list(node_ids)
        if not ids:
            return {}
        marks = ",".join("?" * len(ids))
        return {r["node_id"]: dict(r) for r in cur.execute(
            f"SELECT * FROM kg_mastery WHERE node_id IN ({marks})", ids).fetchall()}

    def question_membership(cur):
        """node_id → {question_id}: reachable set of every ACTIVE question tree."""
        membership = {}
        questions = cur.execute(
            "SELECT question_id, root_node_id FROM kg_question"
            " WHERE status = 'ACTIVE'").fetchall()
        for q in questions:
            edges = active_edge_rows(cur, q["question_id"])
            for n in reachable_set(q["root_node_id"],
                                   [(e["parent"], e["child"]) for e in edges]):
                membership.setdefault(n, set()).add(q["question_id"])
        return membership

    def snapshot(cur, root_node_id, question_id, max_depth=None):
        """TreeSnapshot DTO (RFC §3.2): tree = view, UI-render-ready."""
        edges = active_edge_rows(cur, question_id)
        tree = materialize_tree(root_node_id, edges)
        node_ids = tree["node_ids"]
        if max_depth is not None:
            node_ids = {n for n in node_ids
                        if tree["layers_map"].get(n, 0) <= max_depth}
        mm = mastery_map(cur, node_ids)
        titles = node_titles(cur, node_ids)
        nodes = [{"nodeId": n, "title": titles.get(n, ""),
                  "depth": tree["layers_map"].get(n, 0),
                  "parentCount": tree["parent_count"].get(n, 0),
                  "mastery": mm.get(n, initial_mastery())["state"]}
                 for n in node_ids]
        nodes.sort(key=lambda x: (x["depth"], x["nodeId"]))
        edge_dtos = [{"edgeId": e["edgeId"], "parent": e["parent"],
                      "child": e["child"], "scope": e["scope"]}
                     for e in tree["edges"]
                     if e["parent"] in node_ids and e["child"] in node_ids]
        edge_dtos.sort(key=lambda e: (e["parent"], e["child"], e["edgeId"]))
        groups = {}
        for n in node_ids:
            groups.setdefault(tree["layers_map"].get(n, 0), []).append(n)
        layers = [sorted(groups[d]) for d in sorted(groups)]
        shared = sorted(n for n in node_ids if tree["parent_count"].get(n, 0) >= 2)
        return {"rootNodeId": root_node_id, "questionId": question_id,
                "nodes": nodes, "edges": edge_dtos,
                "layers": layers, "sharedNodeIds": shared}

    # ---------------- write helpers ----------------
    def create_kg_node(cur, title, area, markdown_body=""):
        """Create a REAL learning node (file-backed) for graph use.

        ingest rebuilds ``nodes`` from markdown files on every run, so a DB-only
        row would vanish. Writing the file + upserting inside the caller's
        transaction keeps kg writes atomic; on rollback the file is removed by
        restore_file_on_failure. The upsert also logs sync_changes, so kg-created
        notes flow to mobile through the existing sync protocol.
        """
        slug_base = slugify(title)
        if slug_base == "untitled-node":
            # CJK titles slugify to nothing — fall back to a stable hash slug.
            slug_base = f"kg-{hashlib.sha1(title.encode('utf-8')).hexdigest()[:10]}"
        node_dir = content_root / "nodes" / area
        slug = slug_base
        counter = 2
        while cur.execute("SELECT 1 FROM nodes WHERE slug = ?", (slug,)).fetchone() \
                or (node_dir / f"{slug}.md").exists():
            slug = f"{slug_base}-{counter}"
            counter += 1
        node_dir.mkdir(parents=True, exist_ok=True)
        node_path = node_dir / f"{slug}.md"
        text = node_markdown_template(
            slug, title, area, KG_TRACK, "", [], "support", "draft", 1000)
        if markdown_body.strip():
            text += "\n" + markdown_body.strip() + "\n"
        with restore_file_on_failure(node_path):
            node_path.write_text(text, encoding="utf-8")
            upsert_node_file_in_conn(store.connection, content_root, node_path)
        return slug

    class EdgeWriter:
        """In-transaction edge writer: accumulates edges inserted by this
        transaction so the cycle check sees them (conservative rule ADR-1:
        GLOBAL edges checked against GLOBAL ∪ all LOCAL; LOCAL(q) edges against
        GLOBAL ∪ LOCAL(q))."""

        def __init__(self, cur):
            self.cur = cur
            self.edges = active_edge_rows(cur)  # GLOBAL ∪ all LOCAL, ACTIVE

        def insert(self, parent, child, scope, scope_qid, created_by="USER"):
            if scope == "GLOBAL":
                check = [(e["parent"], e["child"]) for e in self.edges]
            else:
                check = [(e["parent"], e["child"]) for e in self.edges
                         if e["scope"] == "GLOBAL" or e["scope_qid"] == scope_qid]
            path = find_cycle_path(check, parent, child)
            if path is not None:
                raise KgError("CycleDetected", 422, path=path)
            eid = store.new_id()
            try:
                self.cur.execute(
                    "INSERT INTO kg_edge (edge_id, parent_node_id, child_node_id,"
                    " scope_type, scope_question_id, status, created_by, revision,"
                    " created_at) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, 1, ?)",
                    (eid, parent, child, scope, scope_qid, created_by, store.now()))
            except sqlite3.IntegrityError:
                # partial unique index: same live (parent,child,scope,qid) → reuse
                row = self.cur.execute(
                    "SELECT edge_id FROM kg_edge WHERE parent_node_id = ?"
                    " AND child_node_id = ? AND scope_type = ?"
                    " AND COALESCE(scope_question_id, '') = COALESCE(?, '')"
                    " AND status != 'REJECTED'",
                    (parent, child, scope, scope_qid)).fetchone()
                if row is None:
                    raise
                eid, created = row["edge_id"], False
            else:
                created = True
            self.edges.append({"edgeId": eid, "parent": parent, "child": child,
                               "scope": scope, "scope_qid": scope_qid})
            if created:
                store.append_outbox(self.cur, "kg_edge", eid, "INSERT",
                                    {"parent": parent, "child": child, "scope": scope})
            return eid, created

    def validate_spec(spec, path="tree"):
        """PrerequisiteSpec payload shape validation (confirm stage)."""
        if not isinstance(spec, dict) or not isinstance(spec.get("title"), str) \
                or not spec["title"].strip():
            raise KgError("ProposalShapeInvalid", 422,
                          reason=f"{path}: spec needs non-empty 'title'")
        if spec.get("existingNodeId") is not None \
                and not isinstance(spec["existingNodeId"], str):
            raise KgError("ProposalShapeInvalid", 422,
                          reason=f"{path}: 'existingNodeId' must be a string")
        children = spec.get("children") or []
        if not isinstance(children, list):
            raise KgError("ProposalShapeInvalid", 422,
                          reason=f"{path}: 'children' must be a list")
        for i, child in enumerate(children):
            validate_spec(child, f"{path}.children[{i}]")

    def materialize_specs(cur, writer, parent_id, specs, scope, scope_qid,
                          created_nodes, reused_nodes, created_by="USER", area=None):
        """Recursively materialize PrerequisiteSpec: existingNodeId → reuse
        (shared), otherwise create a real node; every edge insertion runs the
        conservative cycle check (cycle → 422, whole transaction rolls back)."""
        node_area = area or DEFAULT_KG_AREA
        for spec in specs:
            d = spec if isinstance(spec, dict) else spec.model_dump()
            existing = d.get("existingNodeId")
            if existing:
                if not get_node(cur, existing):
                    raise KgError("NotFound", 404, kind="node", id=existing)
                nid = existing
                reused_nodes.append(nid)
            else:
                nid = create_kg_node(cur, d["title"], node_area, d.get("markdownBody", ""))
                created_nodes.append(nid)
            writer.insert(parent_id, nid, scope, scope_qid, created_by)
            materialize_specs(cur, writer, nid, d.get("children") or [],
                              scope, scope_qid, created_nodes, reused_nodes,
                              created_by, node_area)

    # ================= write endpoints (idempotent commands) =================

    @router.post("/questions")
    def create_question(req: CreateQuestionReq):
        """createQuestion: register a node as a question root; (area_id, problem_no) unique."""
        if req.category not in CATEGORIES:
            raise KgError("Validation", 400,
                          reason=f"category must be one of {list(CATEGORIES)}")
        area = normalize_kg_area(req.areaId)
        fp = {"op": "createQuestion", "body": req.model_dump(mode="json")}

        def write(cur):
            root = create_kg_node(cur, req.title, area)
            problem_no = req.problemNo
            if problem_no is None:
                row = cur.execute(
                    "SELECT COALESCE(MAX(problem_no), 0) + 1 AS nxt FROM kg_question"
                    " WHERE COALESCE(area_id, '') = COALESCE(?, '')",
                    (area,)).fetchone()
                problem_no = row["nxt"]
            qid = store.new_id()
            try:
                cur.execute(
                    "INSERT INTO kg_question (question_id, root_node_id, area_id,"
                    " problem_no, title, category, jd_batch_id, status, revision,"
                    " created_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 1, ?)",
                    (qid, root, area, problem_no, req.title, req.category,
                     req.jdBatchId, store.now()))
            except sqlite3.IntegrityError:
                raise KgError("AlreadyExists", 409, kind="kg_question",
                              areaId=area, problemNo=problem_no)
            store.append_outbox(cur, "kg_question", qid, "INSERT",
                                {"title": req.title, "category": req.category})
            return {"questionId": qid, "rootNodeId": root, "problemNo": problem_no,
                    "title": req.title, "category": req.category, "areaId": area}

        result, replayed = store.run_idempotent(req.commandId, fp, write)
        return {**result, "replayed": replayed}

    @router.get("/questions")
    def list_questions(limit: int = Query(200, ge=1, le=1000),
                       offset: int = Query(0, ge=0)):
        """(Additive, not in RFC §3.6) List registered question roots — the
        desktop 3D page needs an entry point to pick a root from."""
        def op(cur):
            total = cur.execute(
                "SELECT COUNT(*) AS n FROM kg_question WHERE status != 'ARCHIVED'"
            ).fetchone()["n"]
            rows = cur.execute(
                "SELECT question_id, root_node_id, area_id, problem_no, title,"
                " category, jd_batch_id, created_at FROM kg_question"
                " WHERE status != 'ARCHIVED'"
                " ORDER BY created_at DESC, question_id LIMIT ? OFFSET ?",
                (limit, offset)).fetchall()
            questions = [{"questionId": r["question_id"], "rootNodeId": r["root_node_id"],
                          "areaId": r["area_id"], "problemNo": r["problem_no"],
                          "title": r["title"], "category": r["category"],
                          "jdBatchId": r["jd_batch_id"], "createdAt": r["created_at"]}
                         for r in rows]
            return {"questions": questions, "total": total}

        return store.read(op)

    @router.post("/questions/{qid}/prerequisites")
    def append_prerequisites(qid: str, req: AppendPrerequisitesReq):
        """appendPrerequisites: materialize nested prerequisites; returns the
        question's TreeSnapshot."""
        if req.scope not in EDGE_SCOPES:
            raise KgError("Validation", 400, reason=f"scope must be one of {list(EDGE_SCOPES)}")
        scope_qid = (req.scopeQuestionId or qid) if req.scope == "PROBLEM_LOCAL" else None
        fp = {"op": "appendPrerequisites", "qid": qid, "body": req.model_dump(mode="json")}

        def write(cur):
            q = get_question(cur, qid)
            if not q:
                raise KgError("NotFound", 404, kind="kg_question", id=qid)
            parent = req.parentNodeId or q["root_node_id"]
            if not get_node(cur, parent):
                raise KgError("NotFound", 404, kind="node", id=parent)
            if scope_qid and not get_question(cur, scope_qid):
                raise KgError("NotFound", 404, kind="kg_question", id=scope_qid)
            writer = EdgeWriter(cur)
            created, reused = [], []
            materialize_specs(cur, writer, parent, req.specs, req.scope,
                              scope_qid, created, reused, area=q["area_id"])
            snap = snapshot(cur, q["root_node_id"], qid)
            snap["createdNodeIds"] = created
            snap["reusedNodeIds"] = reused
            return snap

        result, replayed = store.run_idempotent(req.commandId, fp, write)
        return {**result, "replayed": replayed}

    # ================= AI proposals (two-phase: propose → confirm) =================

    @router.post("/proposals/prerequisite-chain")
    def propose_prerequisite_chain(req: ProposeChainReq):
        """proposePrerequisiteChain: only writes kg_proposal (PENDING, now+1h).

        Port note — ModelGateway (feature:assistant) is the production source of
        AI suggestions; the deterministic heuristic generator is injected here so
        the flow works offline and tests stay reproducible."""
        def op(cur):
            if not get_node(cur, req.nodeId):
                raise KgError("NotFound", 404, kind="node", id=req.nodeId)
            existing = {r["title"].lower(): r["slug"] for r in
                        cur.execute("SELECT slug, title FROM nodes").fetchall()}
            tree, reused = heuristic_prerequisite_tree(
                req.questionText, req.maxDepth, req.maxBreadth, existing)
            pid, now = store.new_id(), store.now()
            expires_at = now + int(req.expiresInSeconds) * 1000
            payload = {"nodeId": req.nodeId, "scope": "GLOBAL", "tree": tree}
            cur.execute(
                "INSERT INTO kg_proposal (proposal_id, kind, payload_json, status,"
                " model_ref, command_id, expires_at, created_at)"
                " VALUES (?, 'PREREQUISITE_CHAIN', ?, 'PENDING', ?, NULL, ?, ?)",
                (pid, json.dumps(payload, ensure_ascii=False),
                 "heuristic-keyword-v1", expires_at, now))
            store.append_outbox(cur, "kg_proposal", pid, "INSERT",
                                {"kind": "PREREQUISITE_CHAIN"})
            return {"proposalId": pid, "tree": tree,
                    "reusedNodeIds": sorted(set(reused)), "expiresAt": expires_at}

        return store.write(op)

    @router.post("/proposals/jd-decomposition")
    def propose_jd_decomposition(req: ProposeJdReq):
        """proposeJdDecomposition: JD → N categories × M questions skeleton."""
        def op(cur):
            questions = heuristic_jd_questions(req.jdText, req.categories,
                                               req.questionsPerCategory)
            pid, batch_id, now = store.new_id(), store.new_id(), store.now()
            expires_at = now + int(req.expiresInSeconds) * 1000
            payload = {"batchId": batch_id, "questions": questions}
            cur.execute(
                "INSERT INTO kg_proposal (proposal_id, kind, payload_json, status,"
                " model_ref, command_id, expires_at, created_at)"
                " VALUES (?, 'JD_DECOMPOSITION', ?, 'PENDING', ?, NULL, ?, ?)",
                (pid, json.dumps(payload, ensure_ascii=False),
                 "heuristic-jd-v1", expires_at, now))
            store.append_outbox(cur, "kg_proposal", pid, "INSERT",
                                {"kind": "JD_DECOMPOSITION", "batchId": batch_id})
            return {"proposalId": pid, "batchId": batch_id,
                    "questions": questions, "expiresAt": expires_at}

        return store.write(op)

    def materialize_chain_payload(cur, payload, created_n, reused_n):
        if not isinstance(payload, dict) or not isinstance(payload.get("nodeId"), str) \
                or not isinstance(payload.get("tree"), dict):
            raise KgError("ProposalShapeInvalid", 422,
                          reason="expected {nodeId: str, tree: PrerequisiteSpec}")
        validate_spec(payload["tree"])
        if not get_node(cur, payload["nodeId"]):
            raise KgError("NotFound", 404, kind="node", id=payload["nodeId"])
        scope = payload.get("scope", "GLOBAL")
        scope_qid = payload.get("scopeQuestionId")
        if scope not in EDGE_SCOPES or (scope == "PROBLEM_LOCAL" and not scope_qid):
            raise KgError("ProposalShapeInvalid", 422,
                          reason="scope must be GLOBAL, or PROBLEM_LOCAL with scopeQuestionId")
        writer = EdgeWriter(cur)
        materialize_specs(cur, writer, payload["nodeId"], [payload["tree"]],
                          scope, scope_qid, created_n, reused_n, created_by="AI")

    def materialize_jd_payload(cur, payload, created_q, created_n, reused_n):
        if not isinstance(payload, dict) or not isinstance(payload.get("batchId"), str) \
                or not isinstance(payload.get("questions"), list):
            raise KgError("ProposalShapeInvalid", 422,
                          reason="expected {batchId: str, questions: [...]}")
        writer = EdgeWriter(cur)
        for i, q in enumerate(payload["questions"]):
            if not isinstance(q, dict) or not isinstance(q.get("title"), str) \
                    or not q["title"].strip() or not isinstance(q.get("seq"), int) \
                    or q.get("category") not in CATEGORIES:
                raise KgError(
                    "ProposalShapeInvalid", 422,
                    reason=f"questions[{i}] needs {{title: str, seq: int,"
                           f" category: one of {list(CATEGORIES)}}}")
            seeds = []
            for j, s in enumerate(q.get("seedPrerequisites") or []):
                if isinstance(s, str):
                    s = {"title": s}
                validate_spec(s, f"questions[{i}].seedPrerequisites[{j}]")
                seeds.append(s)
            # (area_id, problem_no) unique: seq restarts per category, so the area
            # namespaces by batch+category; slugified to stay a legal directory name.
            area_id = jd_area_id(payload["batchId"], q["category"])
            root = create_kg_node(cur, q["title"], area_id)
            created_n.append(root)
            qid = store.new_id()
            try:
                cur.execute(
                    "INSERT INTO kg_question (question_id, root_node_id, area_id,"
                    " problem_no, title, category, jd_batch_id, status, revision,"
                    " created_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 1, ?)",
                    (qid, root, area_id, q["seq"], q["title"],
                     q["category"], payload["batchId"], store.now()))
            except sqlite3.IntegrityError:
                raise KgError("AlreadyExists", 409, kind="kg_question",
                              areaId=area_id, problemNo=q["seq"])
            created_q.append(qid)
            store.append_outbox(cur, "kg_question", qid, "INSERT",
                                {"jdBatchId": payload["batchId"], "title": q["title"]})
            materialize_specs(cur, writer, root, seeds, "GLOBAL", None,
                              created_n, reused_n, created_by="AI", area=area_id)

    @router.post("/proposals/{pid}/confirm")
    def confirm_proposal(pid: str, req: ConfirmReq):
        """confirmProposal: expiry + shape validation → materialize nodes/edges in
        one transaction. JD flow supports editedPayloadJson overriding the payload."""
        fp = {"op": "confirmProposal", "pid": pid, "body": req.model_dump(mode="json")}

        def write(cur):
            p = cur.execute("SELECT * FROM kg_proposal WHERE proposal_id = ?",
                            (pid,)).fetchone()
            if not p:
                raise KgError("NotFound", 404, kind="kg_proposal", id=pid)
            if p["status"] != "PENDING":
                raise KgError("ProposalNotPending", 409,
                              proposalId=pid, status=p["status"])
            if store.now() >= p["expires_at"]:
                raise KgError("ProposalExpired", 410, proposalId=pid)
            try:
                payload = (json.loads(req.editedPayloadJson)
                           if req.editedPayloadJson else json.loads(p["payload_json"]))
            except json.JSONDecodeError:
                raise KgError("ProposalShapeInvalid", 422,
                              reason="editedPayloadJson is not valid JSON")
            created_q, created_n, reused_n = [], [], []
            if p["kind"] == "PREREQUISITE_CHAIN":
                materialize_chain_payload(cur, payload, created_n, reused_n)
            elif p["kind"] == "JD_DECOMPOSITION":
                materialize_jd_payload(cur, payload, created_q, created_n, reused_n)
            else:
                raise KgError("ProposalShapeInvalid", 422,
                              reason=f"unknown proposal kind {p['kind']!r}")
            cur.execute("UPDATE kg_proposal SET status = 'CONFIRMED', command_id = ?"
                        " WHERE proposal_id = ?", (req.commandId, pid))
            store.append_outbox(cur, "kg_proposal", pid, "CONFIRM", {"kind": p["kind"]})
            return {"createdQuestionIds": created_q, "createdNodeIds": created_n,
                    "reusedNodeIds": reused_n}

        result, replayed = store.run_idempotent(req.commandId, fp, write)
        return {**result, "replayed": replayed}

    @router.post("/verifications")
    def record_verification(req: VerificationReq):
        """recordVerification: §3.5 rule engine updates kg_mastery + appends
        kg_mastery_event. quizItemId is free-form: when it matches a real quiz id
        the binding is also resolvable via quiz_links; the event row itself is the
        authoritative quiz → node binding evidence."""
        verdict = req.verdict.upper()
        if verdict not in ("PASS", "FAIL"):
            raise KgError("Validation", 400, reason="verdict must be PASS | FAIL")
        fp = {"op": "recordVerification", "body": req.model_dump(mode="json")}

        def write(cur):
            if not get_node(cur, req.nodeId):
                raise KgError("NotFound", 404, kind="node", id=req.nodeId)
            row = cur.execute("SELECT * FROM kg_mastery WHERE node_id = ?",
                              (req.nodeId,)).fetchone()
            current = dict(row) if row else initial_mastery()
            cur.execute("INSERT INTO kg_mastery_event (event_id, node_id, quiz_item_id,"
                        " verdict, command_id, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                        (store.new_id(), req.nodeId, req.quizItemId, verdict,
                         req.commandId, store.now()))
            recent = [r["verdict"] for r in cur.execute(
                "SELECT verdict FROM kg_mastery_event WHERE node_id = ?"
                " ORDER BY rowid DESC LIMIT 3", (req.nodeId,)).fetchall()]
            updated = apply_verdict(current, recent, verdict, store.now())
            cur.execute(
                "INSERT INTO kg_mastery (node_id, state, score, attempts, fail_streak,"
                " last_verdict, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, 1)"
                " ON CONFLICT(node_id) DO UPDATE SET state = excluded.state,"
                " score = excluded.score, attempts = excluded.attempts,"
                " fail_streak = excluded.fail_streak,"
                " last_verdict = excluded.last_verdict,"
                " updated_at = excluded.updated_at, revision = kg_mastery.revision + 1",
                (req.nodeId, updated["state"], updated["score"], updated["attempts"],
                 updated["fail_streak"], updated["last_verdict"], updated["updated_at"]))
            store.append_outbox(cur, "kg_mastery", req.nodeId, "UPSERT",
                                {"state": updated["state"], "score": updated["score"]})
            # suggestedNext: up to 3 direct prerequisites with the lowest score
            prereqs = cur.execute(
                "SELECT e.child_node_id AS nid, n.title AS title, m.state AS state,"
                " m.score AS score FROM kg_edge e"
                " JOIN nodes n ON n.slug = e.child_node_id"
                " LEFT JOIN kg_mastery m ON m.node_id = e.child_node_id"
                " WHERE e.parent_node_id = ? AND e.status = 'ACTIVE'",
                (req.nodeId,)).fetchall()
            prereqs = sorted(prereqs, key=lambda r: (
                r["score"] if r["score"] is not None else 0.0, r["nid"]))[:3]
            suggested = [{"nodeId": r["nid"], "title": r["title"],
                          "mastery": r["state"] or "UNKNOWN",
                          "score": float(r["score"]) if r["score"] is not None else 0.0}
                         for r in prereqs]
            return {"nodeId": req.nodeId,
                    "mastery": {"state": updated["state"], "score": updated["score"],
                                "attempts": updated["attempts"],
                                "failStreak": updated["fail_streak"],
                                "lastVerdict": updated["last_verdict"]},
                    "suggestedNext": suggested}

        result, replayed = store.run_idempotent(req.commandId, fp, write)
        return {**result, "replayed": replayed}

    @router.delete("/edges/{eid}")
    def detach_edge(eid: str, commandId: str = Query(...)):
        """detachEdge: status → REJECTED soft delete (keeps partial unique index semantics)."""
        fp = {"op": "detachEdge", "eid": eid}

        def write(cur):
            row = cur.execute("SELECT * FROM kg_edge WHERE edge_id = ?", (eid,)).fetchone()
            if not row:
                raise KgError("NotFound", 404, kind="kg_edge", id=eid)
            cur.execute("UPDATE kg_edge SET status = 'REJECTED', revision = revision + 1"
                        " WHERE edge_id = ?", (eid,))
            store.append_outbox(cur, "kg_edge", eid, "DELETE", {"edgeId": eid})
            return {"edgeId": eid, "status": "REJECTED"}

        result, replayed = store.run_idempotent(commandId, fp, write)
        return {**result, "replayed": replayed}

    # ================= read endpoints (UI-render-ready) =================

    @router.get("/questions/{qid}/tree")
    def tree_of(qid: str):
        """treeOf: GLOBAL ∪ PROBLEM_LOCAL(qid) ACTIVE edge view."""
        def op(cur):
            q = get_question(cur, qid)
            if not q:
                raise KgError("NotFound", 404, kind="kg_question", id=qid)
            return snapshot(cur, q["root_node_id"], qid)

        return store.read(op)

    @router.get("/nodes/{nid}/subtree")
    def subtree_of(nid: str, maxDepth: int = Query(32, ge=1, le=256)):
        """subtreeOf: reroot pure query (zero writes), any node as root, global DAG."""
        def op(cur):
            if not get_node(cur, nid):
                raise KgError("NotFound", 404, kind="node", id=nid)
            snap = snapshot(cur, nid, None, max_depth=maxDepth)
            snap["maxDepth"] = maxDepth
            return snap

        return store.read(op)

    @router.get("/quizzes/{qid}/gap")
    def diagnose_gap(qid: str):
        """diagnoseGap (rule engine, offline): weakest direct prerequisite by
        (1-score)×(1+blocksCount). Quiz → node binding: real quiz_links first,
        else the latest kg_mastery_event recorded for this quiz id."""
        def op(cur):
            link = cur.execute(
                "SELECT node_slug FROM quiz_links WHERE quiz_id = ? LIMIT 1",
                (qid,)).fetchone()
            if link:
                node_id = link["node_slug"]
            else:
                event = cur.execute(
                    "SELECT node_id FROM kg_mastery_event WHERE quiz_item_id = ?"
                    " ORDER BY rowid DESC LIMIT 1", (qid,)).fetchone()
                if not event:
                    raise KgError("NotFound", 404, kind="quiz_item", id=qid)
                node_id = event["node_id"]
            edges = active_edge_rows(cur)  # GLOBAL ∪ all LOCAL
            pairs = [(e["parent"], e["child"]) for e in edges]
            children = [e["child"] for e in edges if e["parent"] == node_id]
            if not children:
                return {"quizItemId": qid, "failedNodeId": node_id,
                        "weakestPrerequisite": None, "suggestedAction": "LEAF_REINFORCE"}
            mm = mastery_map(cur, children)
            titles = node_titles(cur, children)
            best = None
            for c in children:
                m = mm.get(c, initial_mastery())
                blocks = len(dependents_set(c, pairs))
                priority = gap_priority(m["score"], blocks)
                if best is None or (-priority, c) < best[0]:
                    fails = cur.execute(
                        "SELECT COUNT(*) AS n FROM kg_mastery_event"
                        " WHERE node_id = ? AND verdict = 'FAIL'", (c,)).fetchone()["n"]
                    best = ((-priority, c),
                            {"nodeId": c, "title": titles.get(c, ""),
                             "mastery": m["state"], "score": float(m["score"]),
                             "blocksCount": blocks, "recentFailures": fails})
            return {"quizItemId": qid, "failedNodeId": node_id,
                    "weakestPrerequisite": best[1],
                    "suggestedAction": "REINFORCE_PREREQUISITE"}

        return store.read(op)

    @router.get("/bottlenecks")
    def bottlenecks(minDistinctQuestions: int = Query(2, ge=1),
                    limit: int = Query(20, ge=1, le=200)):
        """bottlenecks (§3.5, parity with the Kotlin GraphEngine semantics):
        candidates = reflexive ancestor closure of weak nodes (FRAGILE/LEARNING
        with score<0.6) inside each ACTIVE question tree's visible edge set
        (GLOBAL ∪ PROBLEM_LOCAL(q)); dependentFailCount = distinct questions whose
        closure covers the candidate (>= threshold); sorted by dependentFailCount
        desc → blocksCount desc → nodeId asc."""
        def op(cur):
            edges = active_edge_rows(cur)   # GLOBAL ∪ all LOCAL: blocksCount base
            pairs = [(e["parent"], e["child"]) for e in edges]
            mastery = {r["node_id"]: dict(r)
                       for r in cur.execute("SELECT * FROM kg_mastery").fetchall()}
            weak_ids = {nid for nid, m in mastery.items() if is_weak(m)}
            dependents_of = {}
            questions = cur.execute(
                "SELECT question_id, root_node_id FROM kg_question"
                " WHERE status = 'ACTIVE' ORDER BY question_id").fetchall()
            for q in questions:
                qid = q["question_id"]
                q_pairs = [(e["parent"], e["child"])
                           for e in active_edge_rows(cur, qid)]
                visible = reachable_set(q["root_node_id"], q_pairs)
                for w in sorted(visible & weak_ids):
                    for ancestor in reachable_set(w, q_pairs):
                        dependents_of.setdefault(ancestor, set()).add(qid)
            titles = node_titles(cur, dependents_of.keys())
            items = []
            for nid, qset in dependents_of.items():
                if len(qset) < minDistinctQuestions:
                    continue
                m = mastery.get(nid, initial_mastery())  # non-weak ancestor → UNKNOWN/0.0
                fails = cur.execute(
                    "SELECT COUNT(*) AS n FROM kg_mastery_event"
                    " WHERE node_id = ? AND verdict = 'FAIL'", (nid,)).fetchone()["n"]
                items.append({
                    "nodeId": nid, "title": titles.get(nid, ""),
                    "mastery": m["state"],
                    "score": float(m["score"]),
                    "blocksCount": len(dependents_set(nid, pairs)),
                    "distinctQuestionCount": len(qset),
                    "dependentFailCount": len(qset),
                    "recentFailures": fails})
            items.sort(key=lambda x: (-x["dependentFailCount"],
                                      -x["blocksCount"], x["nodeId"]))
            return {"items": items[:limit],
                    "minDistinctQuestions": minDistinctQuestions}

        return store.read(op)

    @router.get("/batches/{bid}/progress")
    def expertise_progress(bid: str):
        """expertiseProgress: root-node mastery rollup by jd_batch_id + perCategory."""
        def op(cur):
            rows = cur.execute(
                "SELECT q.question_id, q.category, m.state FROM kg_question q"
                " LEFT JOIN kg_mastery m ON m.node_id = q.root_node_id"
                " WHERE q.jd_batch_id = ? AND q.status != 'ARCHIVED'", (bid,)).fetchall()

            def blank():
                return {"total": 0, "mastered": 0, "fragile": 0,
                        "learning": 0, "unknown": 0, "progress": 0.0}

            agg, per_cat = blank(), {}
            for r in rows:
                state = (r["state"] or "UNKNOWN").lower()
                bucket = per_cat.setdefault(r["category"], blank())
                for a in (agg, bucket):
                    a["total"] += 1
                    if state in ("mastered", "fragile", "learning", "unknown"):
                        a[state] += 1
            for a in [agg, *per_cat.values()]:
                a["progress"] = (a["mastered"] / a["total"]) if a["total"] else 0.0
            return {"jdBatchId": bid, **agg, "perCategory": per_cat}

        return store.read(op)

    @router.get("/export3d")
    def export3d(root: str = Query(...), rootIsQuestion: bool = Query(False)):
        """export3d: strict RFC §3.4 JSON (schemaVersion=1, contentHash, nodes with
        layer/mastery/score/parentCount/sharedByQuestions/isRoot, links with scope,
        stable (layer, id) ordering)."""
        def op(cur):
            if rootIsQuestion:
                q = get_question(cur, root)
                if not q:
                    raise KgError("NotFound", 404, kind="kg_question", id=root)
                root_node, qid = q["root_node_id"], root
            else:
                if not get_node(cur, root):
                    raise KgError("NotFound", 404, kind="node", id=root)
                root_node, qid = root, None
            edges = active_edge_rows(cur, qid)
            node_ids = reachable_set(root_node, [(e["parent"], e["child"]) for e in edges])
            titles = node_titles(cur, node_ids)
            node_rows = {nid: {"title": titles.get(nid, "")} for nid in node_ids}
            return export3d_payload(root_node, node_rows, edges,
                                    mastery_map(cur, node_ids),
                                    question_membership(cur), store.now())

        return store.read(op)

    return router


# ---------------------------------------------------------------------- app factory
def create_kg_app(db_path: str, content_root: Path) -> FastAPI:
    """Standalone app factory (tests / smoke runs). Production wires the router
    into backend.api via create_kg_router instead."""
    store = KgGraphStore(db_path)
    app = FastAPI(title="CS Learning OS — KnowledgeGraph", version="rfc-kg-1")
    app.state.kg_store = store

    @app.exception_handler(KgError)
    async def _kg_error_handler(_: Request, exc: KgError):
        return JSONResponse(status_code=exc.http_status,
                            content={"error": exc.kind, **exc.details})

    app.include_router(create_kg_router(store, content_root))
    return app
