"""Contract tests for the merged KnowledgeGraph router (RFC-knowledge-graph).

Merged under /api/kg with file-backed node creation (real ``nodes`` rows).


环境: fastapi.testclient + SQLite :memory:。覆盖 RFC §5 测试策略全部边界:
幂等重放/冲突、环检测(含菱形误报)、ProblemLocal 隔离、共享子树双树可见、
Proposal 过期/确认/编辑落库、规则引擎公式、瓶颈排序与 ≥2 棵门槛、
JD 120 题与进度汇总、导出 JSON contentHash 稳定、软删边树中消失。
"""
import json
import uuid

import pytest
from fastapi.testclient import TestClient

try:
    from .kg_router import create_kg_app
except ImportError:
    from kg_router import create_kg_app


@pytest.fixture()
def client(tmp_path):
    app = create_kg_app(":memory:", tmp_path / "content")
    with TestClient(app) as c:
        yield c


# ----------------------------------------------------------------- helpers
def cmd():
    return uuid.uuid4().hex


def create_question(client, title, **kw):
    r = client.post("/api/kg/questions", json={"commandId": cmd(), "title": title, **kw})
    assert r.status_code == 200, r.text
    return r.json()


def spec(title, children=(), existing=None):
    s = {"title": title, "children": list(children)}
    if existing:
        s["existingNodeId"] = existing
    return s


def append_specs(client, qid, specs, scope="GLOBAL", parent=None):
    body = {"commandId": cmd(), "specs": specs, "scope": scope}
    if parent:
        body["parentNodeId"] = parent
    r = client.post(f"/api/kg/questions/{qid}/prerequisites", json=body)
    assert r.status_code == 200, r.text
    return r.json()


def node_id(snap, title):
    for n in snap["nodes"]:
        if n["title"] == title:
            return n["nodeId"]
    raise AssertionError(f"node {title!r} not found: {[n['title'] for n in snap['nodes']]}")


def verify(client, node, quiz, verdict):
    r = client.post("/api/kg/verifications",
                    json={"commandId": cmd(), "nodeId": node,
                          "quizItemId": quiz, "verdict": verdict})
    assert r.status_code == 200, r.text
    return r.json()


# ----------------------------------------------------------------- 树/分层/reroot
def test_question_tree_multi_layer(client):
    q = create_question(client, "LC 300 LIS", category="ALGORITHM")
    qid, root = q["questionId"], q["rootNodeId"]
    assert q["problemNo"] == 1 and q["replayed"] is False

    snap = append_specs(client, qid, [spec("DP", [spec("Memoization", [spec("Recursion")])])])
    assert {n["title"] for n in snap["nodes"]} == {"LC 300 LIS", "DP", "Memoization", "Recursion"}
    assert snap["rootNodeId"] == root and snap["questionId"] == qid
    assert snap["layers"] == [[root], [node_id(snap, "DP")],
                              [node_id(snap, "Memoization")], [node_id(snap, "Recursion")]]
    depth = {n["nodeId"]: n["depth"] for n in snap["nodes"]}
    assert depth[root] == 0 and depth[node_id(snap, "Recursion")] == 3
    assert snap["sharedNodeIds"] == []
    assert all(n["mastery"] == "UNKNOWN" for n in snap["nodes"])
    assert len(snap["edges"]) == 3
    assert all(set(e) == {"edgeId", "parent", "child", "scope"} for e in snap["edges"])

    # reroot 是纯查询: 任意节点当根, 零写入
    dp = node_id(snap, "DP")
    r = client.get(f"/api/kg/nodes/{dp}/subtree")
    assert r.status_code == 200, r.text
    sub = r.json()
    assert sub["rootNodeId"] == dp and sub["questionId"] is None
    assert {n["title"] for n in sub["nodes"]} == {"DP", "Memoization", "Recursion"}
    assert len(sub["edges"]) == 2


def test_diamond_does_not_false_positive_cycle(client):
    q = create_question(client, "Q-diamond")
    qid = q["questionId"]
    snap = append_specs(client, qid, [spec("B", [spec("D")]), spec("C")])
    c, d = node_id(snap, "C"), node_id(snap, "D")
    # 菱形闭合: C → D (引用已有节点) 不构成环, 不得误报
    snap2 = append_specs(client, qid, [spec("C", existing=c, children=[spec("D", existing=d)])])
    assert len(snap2["edges"]) == 4
    assert d in snap2["sharedNodeIds"]               # parentCount>=2 → 共享节点
    pc = {n["nodeId"]: n["parentCount"] for n in snap2["nodes"]}
    assert pc[d] == 2
    # 最长路径分层: D 在第 2 层 (root→B→D / root→C→D)
    layers = snap2["layers"]
    assert layers[0] == [q["rootNodeId"]] and d in layers[2]


def test_real_cycle_rejected_with_path(client):
    q = create_question(client, "Q-cycle")
    qid, root = q["questionId"], q["rootNodeId"]
    snap = append_specs(client, qid, [spec("A", [spec("B")])])
    a, b = node_id(snap, "A"), node_id(snap, "B")
    # 在 B 下挂 root → B→root 与 root→A→B 成环
    r = client.post(f"/api/kg/questions/{qid}/prerequisites",
                    json={"commandId": cmd(), "parentNodeId": b,
                          "specs": [spec("back", existing=root)], "scope": "GLOBAL"})
    assert r.status_code == 422, r.text
    err = r.json()
    assert err["error"] == "CycleDetected"
    path = err["path"]
    assert path[0] == b and path[-1] == b            # 闭环路径
    assert root in path and a in path
    # 成环 → 整笔事务回滚, 树不变
    snap2 = client.get(f"/api/kg/questions/{qid}/tree").json()
    assert len(snap2["edges"]) == 2 and len(snap2["nodes"]) == 3


def test_problem_local_isolation(client):
    qa = create_question(client, "QA")
    qb = create_question(client, "QB")
    append_specs(client, qa["questionId"], [spec("SharedGlobal")])
    append_specs(client, qa["questionId"], [spec("PrivA")], scope="PROBLEM_LOCAL")
    append_specs(client, qb["questionId"], [spec("PrivB")], scope="PROBLEM_LOCAL")

    ta = client.get(f"/api/kg/questions/{qa['questionId']}/tree").json()
    tb = client.get(f"/api/kg/questions/{qb['questionId']}/tree").json()
    assert {n["title"] for n in ta["nodes"]} == {"QA", "SharedGlobal", "PrivA"}
    assert {n["title"] for n in tb["nodes"]} == {"QB", "PrivB"}  # 彼此的私有分支互不可见
    scopes_a = {e["scope"] for e in ta["edges"]}
    assert scopes_a == {"GLOBAL", "PROBLEM_LOCAL"}
    assert {e["scope"] for e in tb["edges"]} == {"PROBLEM_LOCAL"}


def test_shared_subtree_visible_in_both_trees(client):
    qa = create_question(client, "QA")
    qb = create_question(client, "QB")
    snap = append_specs(client, qa["questionId"], [spec("Shared", [spec("LeafS")])])
    shared = node_id(snap, "Shared")
    # B 树通过 existingNodeId 零拷贝引用同一子树 (部分继承 = Global 引用 + 私有分支)
    append_specs(client, qb["questionId"], [spec("Shared", existing=shared)])
    # 修改共享子树 → 双树可见
    append_specs(client, qa["questionId"], [spec("LeafZ")], parent=shared)

    for q in (qa, qb):
        t = client.get(f"/api/kg/questions/{q['questionId']}/tree").json()
        titles = {n["title"] for n in t["nodes"]}
        assert {"Shared", "LeafS", "LeafZ"} <= titles
        assert shared in t["sharedNodeIds"]


# ----------------------------------------------------------------- 幂等
def test_idempotent_replay_and_conflict_409(client):
    body = {"commandId": "cmd-1", "title": "Idempotent Q", "category": "CS_BASIC"}
    r1 = client.post("/api/kg/questions", json=body)
    assert r1.status_code == 200 and r1.json()["replayed"] is False
    # 同 commandId + 同指纹 → 缓存重放, 不产生新问题
    r2 = client.post("/api/kg/questions", json=body)
    assert r2.status_code == 200
    assert r2.json()["questionId"] == r1.json()["questionId"]
    assert r2.json()["replayed"] is True
    # 同 commandId + 异指纹 → 409 CommandConflict
    r3 = client.post("/api/kg/questions", json={**body, "title": "Different"})
    assert r3.status_code == 409
    assert r3.json()["error"] == "CommandConflict"
    assert r3.json()["commandId"] == "cmd-1"


# ----------------------------------------------------------------- Proposal 两阶段
def test_proposal_confirm_materializes_and_expiry_rejected(client):
    q = create_question(client, "DP 题")
    qid, root = q["questionId"], q["rootNodeId"]
    snap = append_specs(client, qid, [spec("recursion")])
    recursion_id = node_id(snap, "recursion")

    # propose: 只写 kg_proposal(PENDING), 不落图; 关键词命中已有节点 → 复用
    r = client.post("/api/kg/proposals/prerequisite-chain",
                    json={"nodeId": root,
                          "questionText": "dynamic programming recursion memoization",
                          "maxDepth": 2, "maxBreadth": 3})
    assert r.status_code == 200, r.text
    prop = r.json()
    assert prop["tree"]["children"] and prop["expiresAt"] > 0
    assert recursion_id in prop["reusedNodeIds"]

    # confirm: 物化为节点与边 (同事务); reused 不新建
    c1 = client.post(f"/api/kg/proposals/{prop['proposalId']}/confirm",
                     json={"commandId": "cmd-confirm-a"})
    assert c1.status_code == 200, c1.text
    res = c1.json()
    assert len(res["createdNodeIds"]) >= 3 and res["createdQuestionIds"] == []
    assert recursion_id in res["reusedNodeIds"]
    titles = {n["title"] for n in client.get(f"/api/kg/questions/{qid}/tree").json()["nodes"]}
    assert {"recursion", "dynamic", "programming", "memoization"} <= titles

    # confirm 幂等重放: 同 commandId 返回缓存, 不重复落库
    c2 = client.post(f"/api/kg/proposals/{prop['proposalId']}/confirm",
                     json={"commandId": "cmd-confirm-a"})
    assert c2.status_code == 200
    assert c2.json()["replayed"] is True
    assert c2.json()["createdNodeIds"] == res["createdNodeIds"]

    # 已 CONFIRMED 的 proposal 用新 commandId 再确认 → 409
    c3 = client.post(f"/api/kg/proposals/{prop['proposalId']}/confirm",
                     json={"commandId": cmd()})
    assert c3.status_code == 409 and c3.json()["error"] == "ProposalNotPending"

    # 过期拒绝 (expiresInSeconds=-1 → 立即过期; 契约默认 now+1h)
    r2 = client.post("/api/kg/proposals/prerequisite-chain",
                     json={"nodeId": root, "questionText": "graphs bfs",
                           "expiresInSeconds": -1})
    c4 = client.post(f"/api/kg/proposals/{r2.json()['proposalId']}/confirm",
                     json={"commandId": cmd()})
    assert c4.status_code == 410
    assert c4.json()["error"] == "ProposalExpired"


def test_jd_confirm_edited_payload_override_and_shape_validation(client):
    r = client.post("/api/kg/proposals/jd-decomposition",
                    json={"jdText": "sre linux", "categories": 1, "questionsPerCategory": 2})
    assert r.status_code == 200, r.text
    pid = r.json()["proposalId"]
    # editedPayloadJson 覆盖: 标题/类别/批次全部以编辑后为准
    edited = {"batchId": "batch-edited", "questions": [
        {"category": "ALGORITHM", "seq": 1, "title": "Edited Q1",
         "seedPrerequisites": [{"title": "E1-pre"}]},
        {"category": "ALGORITHM", "seq": 2, "title": "Edited Q2", "seedPrerequisites": []}]}
    c = client.post(f"/api/kg/proposals/{pid}/confirm",
                    json={"commandId": cmd(), "editedPayloadJson": json.dumps(edited, ensure_ascii=False)})
    assert c.status_code == 200, c.text
    assert len(c.json()["createdQuestionIds"]) == 2
    q1 = c.json()["createdQuestionIds"][0]
    assert client.get(f"/api/kg/questions/{q1}/tree").json()["nodes"][0]["title"] == "Edited Q1"
    prog = client.get("/api/kg/batches/batch-edited/progress").json()
    assert prog["total"] == 2 and prog["perCategory"]["ALGORITHM"]["total"] == 2

    # 形状校验: 缺 questions → 422 ProposalShapeInvalid
    r2 = client.post("/api/kg/proposals/jd-decomposition",
                     json={"jdText": "x", "categories": 1, "questionsPerCategory": 1})
    c2 = client.post(f"/api/kg/proposals/{r2.json()['proposalId']}/confirm",
                     json={"commandId": cmd(),
                           "editedPayloadJson": json.dumps({"batchId": "b2"})})
    assert c2.status_code == 422
    assert c2.json()["error"] == "ProposalShapeInvalid"


# ----------------------------------------------------------------- §3.5 规则引擎
def test_record_verification_rule_engine_formula(client):
    q = create_question(client, "Q-mastery")
    root = q["rootNodeId"]
    # FAIL → fail_streak+1, score=max(0,-0.25)=0, attempts>=1 且 fail_streak>=1 → FRAGILE
    m = verify(client, root, "quiz-1", "FAIL")["mastery"]
    assert m["state"] == "FRAGILE" and m["score"] == 0.0
    assert m["failStreak"] == 1 and m["attempts"] == 1
    # PASS → fail_streak=0, score=min(1,+0.2)。§3.5 的算术: 从 0.0 起 +0.2/次,
    # 第 3 次 PASS 后 score=0.6 仍 LEARNING; 第 4 次 PASS 达 0.8 且近 3 次无 FAIL → MASTERED。
    m = verify(client, root, "quiz-1", "PASS")["mastery"]
    assert m["state"] == "LEARNING" and m["score"] == pytest.approx(0.2)
    m = verify(client, root, "quiz-1", "PASS")["mastery"]
    assert m["state"] == "LEARNING" and m["score"] == pytest.approx(0.4)
    m = verify(client, root, "quiz-1", "PASS")["mastery"]
    assert m["state"] == "LEARNING" and m["score"] == pytest.approx(0.6)
    m = verify(client, root, "quiz-1", "PASS")["mastery"]
    assert m["state"] == "MASTERED" and m["score"] == pytest.approx(0.8)
    # "近 3 次无 FAIL" 子句: score 够但近 3 次含 FAIL → 仍 LEARNING
    verify(client, root, "quiz-1", "FAIL")                       # 0.8-0.25=0.55 FRAGILE
    verify(client, root, "quiz-1", "PASS")                       # 0.75 LEARNING
    m = verify(client, root, "quiz-1", "PASS")["mastery"]        # 0.95 但近3次含FAIL
    assert m["state"] == "LEARNING" and m["score"] == pytest.approx(0.95)
    m = verify(client, root, "quiz-1", "PASS")["mastery"]        # 近3次全PASS → MASTERED
    assert m["state"] == "MASTERED"
    # suggestedNext: 前置按 score 升序, 最多 3 个
    assert isinstance(m, dict)


def test_record_verification_suggested_next_and_idempotency(client):
    q = create_question(client, "Q-suggest")
    root = q["rootNodeId"]
    snap = append_specs(client, q["questionId"], [spec("P1"), spec("P2"), spec("P3"), spec("P4")])
    p1 = node_id(snap, "P1")
    for _ in range(4):
        verify(client, p1, "quiz-p1", "PASS")                    # P1 MASTERED(0.8)
    r = client.post("/api/kg/verifications",
                    json={"commandId": "cmd-ver-1", "nodeId": root,
                          "quizItemId": "quiz-root", "verdict": "FAIL"})
    assert r.status_code == 200, r.text
    nxt = r.json()["suggestedNext"]
    assert len(nxt) == 3                                         # 4 个前置取最低 3 个
    assert p1 not in {n["nodeId"] for n in nxt}                  # 已掌握者排最后, 被截断
    assert all(n["score"] == 0.0 for n in nxt)
    # 幂等重放: 同 commandId 不重复追加事件
    r2 = client.post("/api/kg/verifications",
                     json={"commandId": "cmd-ver-1", "nodeId": root,
                           "quizItemId": "quiz-root", "verdict": "FAIL"})
    assert r2.json()["replayed"] is True
    assert r2.json()["mastery"]["attempts"] == 1


# ----------------------------------------------------------------- diagnoseGap
def test_diagnose_gap_priority(client):
    q = create_question(client, "Q-gap")
    qid, root = q["questionId"], q["rootNodeId"]
    snap = append_specs(client, qid, [spec("C1"), spec("C2"), spec("C3")])
    c1, c2, c3 = (node_id(snap, t) for t in ("C1", "C2", "C3"))
    for _ in range(4):
        verify(client, c1, "quiz-c1", "PASS")                    # C1 MASTERED(0.8)
    q2 = create_question(client, "Q-gap-2")
    append_specs(client, q2["questionId"], [spec("C2", existing=c2)])  # C2 多一个下游
    verify(client, root, "quiz-fail", "FAIL")                    # quiz → node 绑定

    r = client.get("/api/kg/quizzes/quiz-fail/gap")
    assert r.status_code == 200, r.text
    gap = r.json()
    assert gap["failedNodeId"] == root
    assert gap["suggestedAction"] == "REINFORCE_PREREQUISITE"
    w = gap["weakestPrerequisite"]
    # 优先级 (1-score)×(1+blocksCount): C2=(1-0)*(1+2)=3 > C3=2 > C1=(1-0.8)*2=0.4
    assert w["nodeId"] == c2
    assert w["blocksCount"] == 2 and w["score"] == 0.0

    # 无前置 → LEAF_REINFORCE
    verify(client, c3, "quiz-leaf", "FAIL")
    leaf = client.get("/api/kg/quizzes/quiz-leaf/gap").json()
    assert leaf["suggestedAction"] == "LEAF_REINFORCE"
    assert leaf["weakestPrerequisite"] is None


# ----------------------------------------------------------------- bottlenecks
def test_bottlenecks_threshold_and_sorting(client):
    qa = create_question(client, "QA")
    snap = append_specs(client, qa["questionId"], [spec("A", [spec("B")]), spec("C")])
    ra, a, b, c = qa["rootNodeId"], node_id(snap, "A"), node_id(snap, "B"), node_id(snap, "C")
    qb = create_question(client, "QB")
    rb = qb["rootNodeId"]
    append_specs(client, qb["questionId"], [spec("B", existing=b), spec("C", existing=c)])
    for n in (ra, a, b, c, rb):
        verify(client, n, f"quiz-{n[:8]}", "FAIL")               # 全部置弱 FRAGILE(0<0.6)

    r = client.get("/api/kg/bottlenecks")
    assert r.status_code == 200, r.text
    items = r.json()["items"]
    ids = [i["nodeId"] for i in items]
    # 统一语义: dependentFailCount = 覆盖候选的不同问题棵数 (自反祖先闭包)。
    # B 被 QA(经 RA/A/B 闭包) 与 QB(经 RB/B 闭包) 覆盖 → 2; C 同理 → 2。
    # A/RA 只在 QA 闭包、RB 只在 QB 闭包 → 棵数 1, 被 ≥2 棵门槛挡掉。
    # 棵数并列 → blocksCount 降序: B(3: A/RA/RB) > C(2: RA/RB)。
    assert ids == [b, c]
    assert items[0]["distinctQuestionCount"] == 2 and items[0]["dependentFailCount"] == 2
    assert items[1]["dependentFailCount"] == 2
    assert items[0]["blocksCount"] == 3 and items[1]["blocksCount"] == 2
    assert items[0]["recentFailures"] == 1
    # 门槛提高 → 无入选
    assert client.get("/api/kg/bottlenecks?minDistinctQuestions=3").json()["items"] == []


def test_bottlenecks_shared_non_weak_ancestor(client):
    """语义放宽: 候选 = 弱节点的自反祖先闭包, 祖先自身无需为弱节点。
    3 棵树各有弱节点依赖共享非弱祖先 P → P 入选且 dependentFailCount=3,
    排在一个 dependentFailCount=2 的弱节点之前。"""
    qa = create_question(client, "QA")
    snap = append_specs(client, qa["questionId"], [spec("WA", [spec("P")])])
    wa, p = node_id(snap, "WA"), node_id(snap, "P")
    wb = wc = None
    for title in ("QB", "QC"):
        q = create_question(client, title)
        s = append_specs(client, q["questionId"],
                         [spec(f"W{title[-1]}", [spec("P", existing=p)])])
        if title == "QB":
            wb = node_id(s, "WB")
        else:
            wc = node_id(s, "WC")
    # 对照组: 弱节点 X 被 QD/QE 两棵树依赖 → dependentFailCount=2
    qd = create_question(client, "QD")
    x = node_id(append_specs(client, qd["questionId"], [spec("X")]), "X")
    qe = create_question(client, "QE")
    append_specs(client, qe["questionId"], [spec("X", existing=x)])
    for i, n in enumerate((wa, wb, wc, x)):
        verify(client, n, f"quiz-w-{i}", "FAIL")                 # 置弱; P 保持非弱

    r = client.get("/api/kg/bottlenecks")
    assert r.status_code == 200, r.text
    items = r.json()["items"]
    ids = [i["nodeId"] for i in items]
    # 非弱祖先 P 入选并排最前: 3 棵 > X 的 2 棵; WA/WB/WC 各只被 1 棵树覆盖 → 落选
    assert ids == [p, x]
    assert items[0]["dependentFailCount"] == 3
    assert items[0]["mastery"] == "UNKNOWN" and items[0]["score"] == 0.0
    assert items[0]["blocksCount"] == 6          # WA/WB/WC + 3 个 root 传递依赖 P
    assert items[0]["recentFailures"] == 0
    assert items[1]["dependentFailCount"] == 2 and items[1]["mastery"] == "FRAGILE"
    # 门槛收紧到 3 → 只剩 P; 到 4 → 无人入选
    r3 = client.get("/api/kg/bottlenecks?minDistinctQuestions=3").json()["items"]
    assert [i["nodeId"] for i in r3] == [p]
    assert client.get("/api/kg/bottlenecks?minDistinctQuestions=4").json()["items"] == []


# ----------------------------------------------------------------- JD 120 题 + 进度
def test_jd_decomposition_120_questions_and_progress(client):
    r = client.post("/api/kg/proposals/jd-decomposition",
                    json={"jdText": "backend python fastapi sqlite redis kafka docker k8s grpc",
                          "categories": 4, "questionsPerCategory": 30})
    assert r.status_code == 200, r.text
    prop = r.json()
    assert len(prop["questions"]) == 4 * 30 == 120
    assert {q["category"] for q in prop["questions"]} == {
        "CS_BASIC", "ALGORITHM", "SYSTEM_DESIGN", "BEHAVIORAL"}
    for q in prop["questions"]:
        assert q["seq"] >= 1 and q["title"] and len(q["seedPrerequisites"]) == 2

    c = client.post(f"/api/kg/proposals/{prop['proposalId']}/confirm", json={"commandId": cmd()})
    assert c.status_code == 200, c.text
    res = c.json()
    assert len(res["createdQuestionIds"]) == 120
    assert len(res["createdNodeIds"]) == 120 + 240               # 120 root + 每题 2 种子前置

    batch = prop["batchId"]
    prog = client.get(f"/api/kg/batches/{batch}/progress").json()
    assert prog["jdBatchId"] == batch
    assert prog["total"] == 120 and prog["unknown"] == 120 and prog["progress"] == 0.0
    assert len(prog["perCategory"]) == 4
    assert all(v["total"] == 30 for v in prog["perCategory"].values())

    # 刷一棵树到 MASTERED → 汇总与 perCategory 同步变化
    tree = client.get(f"/api/kg/questions/{res['createdQuestionIds'][0]}/tree").json()
    assert len(tree["nodes"]) == 3                               # root + 2 种子前置
    for _ in range(4):
        verify(client, tree["rootNodeId"], "quiz-jd-1", "PASS")
    prog = client.get(f"/api/kg/batches/{batch}/progress").json()
    assert prog["mastered"] == 1 and prog["unknown"] == 119
    assert prog["progress"] == pytest.approx(1 / 120)
    assert prog["perCategory"]["CS_BASIC"]["mastered"] == 1


# ----------------------------------------------------------------- export3d
def test_export3d_contract_and_content_hash(client):
    q = create_question(client, "LC 300 LIS")
    qid, root = q["questionId"], q["rootNodeId"]
    append_specs(client, qid, [spec("DP", [spec("Recursion")]), spec("BinarySearch")])

    r1 = client.get("/api/kg/export3d", params={"root": qid, "rootIsQuestion": True})
    assert r1.status_code == 200, r.text
    p1 = r1.json()
    assert p1["schemaVersion"] == 1
    assert len(p1["nodes"]) == 4 and len(p1["links"]) == 3
    pairs = [(n["layer"], n["id"]) for n in p1["nodes"]]
    assert pairs == sorted(pairs)                                # (layer, id) 稳定排序
    root_dto = next(n for n in p1["nodes"] if n["isRoot"])
    assert root_dto["id"] == root and root_dto["layer"] == 0
    assert root_dto["sharedByQuestions"] == 1
    for n in p1["nodes"]:
        assert {"id", "title", "layer", "mastery", "score",
                "parentCount", "sharedByQuestions", "isRoot"} <= set(n)
    assert all(set(l) == {"source", "target", "scope"} for l in p1["links"])
    assert {l["scope"] for l in p1["links"]} == {"GLOBAL"}

    # contentHash: 图不变 → 稳定 (generatedAt 不参与)
    p2 = client.get("/api/kg/export3d", params={"root": qid, "rootIsQuestion": True}).json()
    assert p2["contentHash"] == p1["contentHash"]
    # 图变更 → hash 变更
    append_specs(client, qid, [spec("Greedy")])
    p3 = client.get("/api/kg/export3d", params={"root": qid, "rootIsQuestion": True}).json()
    assert p3["contentHash"] != p1["contentHash"]
    assert len(p3["nodes"]) == 5

    # reroot 导出: 任意节点当根
    dp = next(n["id"] for n in p3["nodes"] if n["title"] == "DP")
    r4 = client.get("/api/kg/export3d", params={"root": dp, "rootIsQuestion": False})
    assert r4.status_code == 200, r.text
    assert {n["title"] for n in r4.json()["nodes"]} == {"DP", "Recursion"}


# ----------------------------------------------------------------- detachEdge
def test_detach_edge_soft_delete(client):
    q = create_question(client, "Q-detach")
    qid = q["questionId"]
    snap = append_specs(client, qid, [spec("A", [spec("B")])])
    a = node_id(snap, "A")
    edge = next(e for e in snap["edges"] if e["child"] == a)

    r = client.delete(f"/api/kg/edges/{edge['edgeId']}", params={"commandId": "cmd-detach-1"})
    assert r.status_code == 200, r.text
    assert r.json()["status"] == "REJECTED"
    # 软删后从树中消失 (B 经 A 才可达, 一并不可达)
    snap2 = client.get(f"/api/kg/questions/{qid}/tree").json()
    assert {n["title"] for n in snap2["nodes"]} == {"Q-detach"}
    assert snap2["edges"] == []
    # 幂等重放
    r2 = client.delete(f"/api/kg/edges/{edge['edgeId']}", params={"commandId": "cmd-detach-1"})
    assert r2.status_code == 200 and r2.json()["replayed"] is True
    # partial unique index 语义: REJECTED 不阻挡同边重建
    snap3 = append_specs(client, qid, [spec("A", [spec("B")])])
    assert "A" in {n["title"] for n in snap3["nodes"]}
    assert len(snap3["edges"]) == 2
    assert edge["edgeId"] not in {e["edgeId"] for e in snap3["edges"]}
