"""FastAPI blueprint for the KnowledgeGraph deep module — RFC §3.6 REST contract.

13 endpoints, 与 Kotlin `KnowledgeGraph` facade (RFC §3.2) 一一对应:
  POST /graph/questions                       createQuestion
  POST /graph/questions/{qid}/prerequisites   appendPrerequisites
  POST /graph/proposals/prerequisite-chain    proposePrerequisiteChain
  POST /graph/proposals/jd-decomposition      proposeJdDecomposition
  POST /graph/proposals/{pid}/confirm         confirmProposal
  POST /graph/verifications                   recordVerification
  GET  /graph/questions/{qid}/tree            treeOf
  GET  /graph/nodes/{nid}/subtree             subtreeOf (reroot)
  GET  /graph/quizzes/{qid}/gap               diagnoseGap
  GET  /graph/bottlenecks                     bottlenecks
  GET  /graph/batches/{bid}/progress          expertiseProgress
  GET  /graph/export3d?root=..&rootIsQuestion=..  export3d
  DELETE /graph/edges/{eid}                   detachEdge

持久化: graph_store.GraphStore (SQLite); 算法: graph_engine (纯函数)。
"""
from __future__ import annotations

import json
import sqlite3
from typing import Optional

from fastapi import APIRouter, FastAPI, Query, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from graph_engine import (
    CATEGORIES, EDGE_SCOPES, apply_verdict, dependents_set, export3d_payload,
    find_cycle_path, gap_priority, heuristic_jd_questions,
    heuristic_prerequisite_tree, initial_mastery, is_weak, materialize_tree,
    reachable_set,
)
from graph_store import GraphStore, KgError


# ---------------------------------------------------------------- request bodies
class CreateQuestionReq(BaseModel):
    commandId: str
    title: str
    category: str = "CS_BASIC"
    areaId: Optional[str] = None
    problemNo: Optional[int] = None          # 缺省 → 该 area 内自增 (序号只增不改)
    jdBatchId: Optional[str] = None


class PrerequisiteSpec(BaseModel):
    """嵌套前置结构 (RFC §3.2): 同时表达多层链与"引用已有节点"(共享子树)。"""
    title: str
    existingNodeId: Optional[str] = None
    markdownBody: str = ""
    children: list["PrerequisiteSpec"] = Field(default_factory=list)


class AppendPrerequisitesReq(BaseModel):
    commandId: str
    parentNodeId: Optional[str] = None       # 缺省 = 该 question 的 root
    specs: list[PrerequisiteSpec]
    scope: str = "GLOBAL"                    # GLOBAL | PROBLEM_LOCAL
    scopeQuestionId: Optional[str] = None    # PROBLEM_LOCAL 时缺省 = path 的 qid


class ProposeChainReq(BaseModel):
    nodeId: str
    questionText: str
    maxDepth: int = 3
    maxBreadth: int = 5
    expiresInSeconds: int = 3600             # 契约默认 now+1h; 参数化便于测试过期分支


class ProposeJdReq(BaseModel):
    jdText: str
    categories: int = 4
    questionsPerCategory: int = 30
    expiresInSeconds: int = 3600


class ConfirmReq(BaseModel):
    commandId: str
    editedPayloadJson: Optional[str] = None  # JD 场景支持编辑后覆盖原 payload


class VerificationReq(BaseModel):
    commandId: str
    nodeId: str
    quizItemId: str
    verdict: str                             # PASS | FAIL


PrerequisiteSpec.model_rebuild()


# ------------------------------------------------------------------ router 工厂
def create_router(store: GraphStore) -> APIRouter:
    router = APIRouter()

    # ---------------- 行加载 helpers ----------------
    def get_node(cur, nid):
        return cur.execute("SELECT * FROM learning_nodes WHERE id = ?", (nid,)).fetchone()

    def get_question(cur, qid):
        return cur.execute("SELECT * FROM kg_question WHERE question_id = ?", (qid,)).fetchone()

    def active_edge_rows(cur, qid=None):
        """ACTIVE 边集:
        qid=None → GLOBAL ∪ 全部 LOCAL (保守环检测 / reroot / gap / bottlenecks);
        qid 给定 → GLOBAL ∪ PROBLEM_LOCAL(qid) (该 question 的树视图, RFC §3.1)。"""
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
        return {r["id"]: r["title"] for r in cur.execute(
            f"SELECT id, title FROM learning_nodes WHERE id IN ({marks})", ids).fetchall()}

    def mastery_map(cur, node_ids):
        ids = list(node_ids)
        if not ids:
            return {}
        marks = ",".join("?" * len(ids))
        return {r["node_id"]: dict(r) for r in cur.execute(
            f"SELECT * FROM kg_mastery WHERE node_id IN ({marks})", ids).fetchall()}

    def question_membership(cur):
        """node_id → {question_id}: 每棵 ACTIVE 问题树 (GLOBAL∪LOCAL(q)) 的可达集。"""
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
        """TreeSnapshot DTO (RFC §3.2): 树 = 视图, UI 直渲。"""
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

    # ---------------- 写 helpers ----------------
    class EdgeWriter:
        """事务内边写入器: 累积本事务已插边, 环检测可看到 (保守规则 ADR-1:
        GLOBAL 边在 GLOBAL ∪ 全部 LOCAL 上查; LOCAL(q) 边在 GLOBAL ∪ LOCAL(q) 上查)。"""

        def __init__(self, cur):
            self.cur = cur
            self.edges = active_edge_rows(cur)  # GLOBAL ∪ 全部 LOCAL, ACTIVE

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
                # partial unique index: 同 (parent,child,scope,qid) 活跃边 → 复用
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

    def create_learning_node(cur, title, markdown_body=""):
        nid = store.new_id()
        cur.execute("INSERT INTO learning_nodes (id, title, markdown_body, created_at)"
                    " VALUES (?, ?, ?, ?)", (nid, title, markdown_body, store.now()))
        store.append_outbox(cur, "learning_node", nid, "INSERT", {"title": title})
        return nid

    def validate_spec(spec, path="tree"):
        """PrerequisiteSpec payload 形状校验 (confirm 阶段, RFC KgError.ProposalShapeInvalid)。"""
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
                          created_nodes, reused_nodes, created_by="USER"):
        """递归物化 PrerequisiteSpec: existingNodeId → 复用(共享), 否则新建节点;
        每条边插入前做保守环检测 (成环 → 422, 整笔事务回滚)。"""
        for spec in specs:
            d = spec if isinstance(spec, dict) else spec.model_dump()
            existing = d.get("existingNodeId")
            if existing:
                if not get_node(cur, existing):
                    raise KgError("NotFound", 404, kind="learning_node", id=existing)
                nid = existing
                reused_nodes.append(nid)
            else:
                nid = create_learning_node(cur, d["title"], d.get("markdownBody", ""))
                created_nodes.append(nid)
            writer.insert(parent_id, nid, scope, scope_qid, created_by)
            materialize_specs(cur, writer, nid, d.get("children") or [],
                              scope, scope_qid, created_nodes, reused_nodes, created_by)

    # ================= 写端点 (幂等命令) =================

    @router.post("/graph/questions")
    def create_question(req: CreateQuestionReq):
        """createQuestion: 登记 learning_node 为问题根; (area_id, problem_no) 唯一。"""
        if req.category not in CATEGORIES:
            raise KgError("Validation", 400,
                          reason=f"category must be one of {list(CATEGORIES)}")
        fp = {"op": "createQuestion", "body": req.model_dump(mode="json")}

        def write(cur):
            root = create_learning_node(cur, req.title)
            if req.areaId:
                cur.execute("INSERT OR IGNORE INTO areas (id) VALUES (?)", (req.areaId,))
            problem_no = req.problemNo
            if problem_no is None:
                row = cur.execute(
                    "SELECT COALESCE(MAX(problem_no), 0) + 1 AS nxt FROM kg_question"
                    " WHERE COALESCE(area_id, '') = COALESCE(?, '')",
                    (req.areaId,)).fetchone()
                problem_no = row["nxt"]
            qid = store.new_id()
            try:
                cur.execute(
                    "INSERT INTO kg_question (question_id, root_node_id, area_id,"
                    " problem_no, title, category, jd_batch_id, status, revision,"
                    " created_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 1, ?)",
                    (qid, root, req.areaId, problem_no, req.title, req.category,
                     req.jdBatchId, store.now()))
            except sqlite3.IntegrityError:
                raise KgError("AlreadyExists", 409, kind="kg_question",
                              areaId=req.areaId, problemNo=problem_no)
            store.append_outbox(cur, "kg_question", qid, "INSERT",
                                {"title": req.title, "category": req.category})
            return {"questionId": qid, "rootNodeId": root, "problemNo": problem_no,
                    "title": req.title, "category": req.category}

        result, replayed = store.run_idempotent(req.commandId, fp, write)
        return {**result, "replayed": replayed}

    @router.post("/graph/questions/{qid}/prerequisites")
    def append_prerequisites(qid: str, req: AppendPrerequisitesReq):
        """appendPrerequisites: 嵌套物化前置; 返回该 question 的 TreeSnapshot。"""
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
                raise KgError("NotFound", 404, kind="learning_node", id=parent)
            if scope_qid and not get_question(cur, scope_qid):
                raise KgError("NotFound", 404, kind="kg_question", id=scope_qid)
            writer = EdgeWriter(cur)
            created, reused = [], []
            materialize_specs(cur, writer, parent, req.specs, req.scope,
                              scope_qid, created, reused)
            snap = snapshot(cur, q["root_node_id"], qid)
            snap["createdNodeIds"] = created
            snap["reusedNodeIds"] = reused
            return snap

        result, replayed = store.run_idempotent(req.commandId, fp, write)
        return {**result, "replayed": replayed}

    # ================= AI 建议 (两阶段: propose 不落库 → confirm 物化) =================

    @router.post("/graph/proposals/prerequisite-chain")
    def propose_prerequisite_chain(req: ProposeChainReq):
        """proposePrerequisiteChain: 只写 kg_proposal(PENDING, now+1h), 不落图。

        端口注释 — ModelGateway (feature:assistant) 为生产环境 AI 建议唯一来源;
        此处注入确定性启发式生成器 (graph_engine.heuristic_prerequisite_tree)。
        """
        def op(cur):
            if not get_node(cur, req.nodeId):
                raise KgError("NotFound", 404, kind="learning_node", id=req.nodeId)
            existing = {r["title"].lower(): r["id"] for r in
                        cur.execute("SELECT id, title FROM learning_nodes").fetchall()}
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

    @router.post("/graph/proposals/jd-decomposition")
    def propose_jd_decomposition(req: ProposeJdReq):
        """proposeJdDecomposition: JD → N 类 × M 题骨架, 只写 kg_proposal。"""
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
            raise KgError("NotFound", 404, kind="learning_node", id=payload["nodeId"])
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
            root = create_learning_node(cur, q["title"])
            created_n.append(root)
            qid = store.new_id()
            # (area_id, problem_no) 唯一: 同 batch 内 seq 按类别从 1 重排,
            # 故 area 用 batchId:category 命名空间隔离, problem_no = seq。
            area_id = f"{payload['batchId']}:{q['category']}"
            cur.execute("INSERT OR IGNORE INTO areas (id) VALUES (?)", (area_id,))
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
                              created_n, reused_n, created_by="AI")

    @router.post("/graph/proposals/{pid}/confirm")
    def confirm_proposal(pid: str, req: ConfirmReq):
        """confirmProposal: 校验未过期 + payload 形状 → 同事务物化为节点与边。

        JD 场景支持 editedPayloadJson 整体覆盖原 payload (仍须过形状校验)。
        """
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

    @router.post("/graph/verifications")
    def record_verification(req: VerificationReq):
        """recordVerification: §3.5 规则引擎更新 kg_mastery + 追加 kg_mastery_event。"""
        verdict = req.verdict.upper()
        if verdict not in ("PASS", "FAIL"):
            raise KgError("Validation", 400, reason="verdict must be PASS | FAIL")
        fp = {"op": "recordVerification", "body": req.model_dump(mode="json")}

        def write(cur):
            if not get_node(cur, req.nodeId):
                raise KgError("NotFound", 404, kind="learning_node", id=req.nodeId)
            cur.execute("INSERT INTO quiz_items (id, node_id, created_at)"
                        " VALUES (?, ?, ?) ON CONFLICT(id) DO NOTHING",
                        (req.quizItemId, req.nodeId, store.now()))
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
            # suggestedNext: 直接前置中 score 最低的 3 个
            prereqs = cur.execute(
                "SELECT e.child_node_id AS nid, n.title AS title, m.state AS state,"
                " m.score AS score FROM kg_edge e"
                " JOIN learning_nodes n ON n.id = e.child_node_id"
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

    @router.delete("/graph/edges/{eid}")
    def detach_edge(eid: str, commandId: str = Query(...)):
        """detachEdge: status → REJECTED 软删 (保持 partial unique index 语义)。"""
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

    # ================= 读端点 (UI 直渲) =================

    @router.get("/graph/questions/{qid}/tree")
    def tree_of(qid: str):
        """treeOf: GLOBAL ∪ PROBLEM_LOCAL(qid) 的 ACTIVE 边视图。"""
        def op(cur):
            q = get_question(cur, qid)
            if not q:
                raise KgError("NotFound", 404, kind="kg_question", id=qid)
            return snapshot(cur, q["root_node_id"], qid)

        return store.read(op)

    @router.get("/graph/nodes/{nid}/subtree")
    def subtree_of(nid: str, maxDepth: int = Query(32, ge=1, le=256)):
        """subtreeOf: reroot 纯查询 (零写入), 任意节点当根, 走全局 DAG。"""
        def op(cur):
            if not get_node(cur, nid):
                raise KgError("NotFound", 404, kind="learning_node", id=nid)
            snap = snapshot(cur, nid, None, max_depth=maxDepth)
            snap["maxDepth"] = maxDepth
            return snap

        return store.read(op)

    @router.get("/graph/quizzes/{qid}/gap")
    def diagnose_gap(qid: str):
        """diagnoseGap (规则引擎, 离线): 直接前置中 (1-score)×(1+blocksCount) 最高者。"""
        def op(cur):
            quiz = cur.execute("SELECT * FROM quiz_items WHERE id = ?", (qid,)).fetchone()
            if not quiz or not quiz["node_id"]:
                raise KgError("NotFound", 404, kind="quiz_item", id=qid)
            node_id = quiz["node_id"]
            edges = active_edge_rows(cur)  # GLOBAL ∪ 全部 LOCAL
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

    @router.get("/graph/bottlenecks")
    def bottlenecks(minDistinctQuestions: int = Query(2, ge=1),
                    limit: int = Query(20, ge=1, le=200)):
        """bottlenecks (§3.5, 与 Kotlin GraphEngine.bottlenecks 统一语义):
        候选 = 每棵 ACTIVE 问题树可见边集 (GLOBAL ∪ PROBLEM_LOCAL(q)) 内,
        弱节点 (score<0.6 的 FRAGILE/LEARNING) 的自反祖先闭包 — 含弱节点自身
        及其全部可达祖先, 祖先不再要求自身为弱节点。
        dependentFailCount = 闭包覆盖该候选的不同问题棵数 (>= 门槛入选);
        按 dependentFailCount 降序 → blocksCount 降序 → nodeId 升序。"""
        def op(cur):
            edges = active_edge_rows(cur)   # GLOBAL ∪ 全部 LOCAL: blocksCount 基数
            pairs = [(e["parent"], e["child"]) for e in edges]
            mastery = {r["node_id"]: dict(r)
                       for r in cur.execute("SELECT * FROM kg_mastery").fetchall()}
            weak_ids = {nid for nid, m in mastery.items() if is_weak(m)}
            # 候选 → 依赖它的不同问题集合 (自反祖先闭包)
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
                m = mastery.get(nid, initial_mastery())  # 非弱祖先 → UNKNOWN/0.0
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

    @router.get("/graph/batches/{bid}/progress")
    def expertise_progress(bid: str):
        """expertiseProgress: 按 jd_batch_id 汇总根节点掌握度 + perCategory。"""
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

    @router.get("/graph/export3d")
    def export3d(root: str = Query(...), rootIsQuestion: bool = Query(False)):
        """export3d: 严格输出 RFC §3.4 JSON (schemaVersion=1, contentHash,
        nodes 含 layer/mastery/score/parentCount/sharedByQuestions/isRoot,
        links 含 scope, 按 (layer, id) 稳定排序)。"""
        def op(cur):
            if rootIsQuestion:
                q = get_question(cur, root)
                if not q:
                    raise KgError("NotFound", 404, kind="kg_question", id=root)
                root_node, qid = q["root_node_id"], root
            else:
                if not get_node(cur, root):
                    raise KgError("NotFound", 404, kind="learning_node", id=root)
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


# ---------------------------------------------------------------------- app 工厂
def create_app(db_path: str = "cs_learning_os.db") -> FastAPI:
    """应用工厂: 默认文件库 cs_learning_os.db; 测试传 ':memory:' / 临时文件。"""
    store = GraphStore(db_path)
    app = FastAPI(title="CS Learning OS — KnowledgeGraph", version="rfc-kg-1")
    app.state.store = store

    @app.exception_handler(KgError)
    async def _kg_error_handler(_: Request, exc: KgError):
        return JSONResponse(status_code=exc.http_status,
                            content={"error": exc.kind, **exc.details})

    app.include_router(create_router(store))
    return app


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("graph_service:create_app", factory=True,
                host="127.0.0.1", port=8000, reload=False)
