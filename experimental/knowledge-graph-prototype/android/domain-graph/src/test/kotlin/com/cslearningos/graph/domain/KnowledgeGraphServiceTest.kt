package com.cslearningos.graph.domain

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

/** KnowledgeGraphService(内存门面)行为测试: 幂等/环检测/ProblemLocal 隔离/共享子树/瓶颈/导出/提案 */
class KnowledgeGraphServiceTest {

    // ------------------------------------------------------------------
    // 测试装置
    // ------------------------------------------------------------------

    /** 序号 ID 生成器(确定性) */
    private class SeqIds : IdGenerator {
        private var n = 0
        override fun nextId(prefix: String): String = "$prefix-${++n}"
    }

    /** 固定时钟(可手动推进) */
    private class FixedClock(var t: Long = 1_000_000L) : Clock {
        override fun now(): Long = t
    }

    private fun newService(
        clock: FixedClock = FixedClock(),
        proposer: PrerequisiteProposer? = null,
    ): KnowledgeGraphService =
        if (proposer == null) {
            KnowledgeGraphService(clock = clock, idGenerator = SeqIds())
        } else {
            KnowledgeGraphService(clock = clock, idGenerator = SeqIds(), prerequisiteProposer = proposer)
        }

    private fun <T> DomainResult<T>.unwrap(): T = when (this) {
        is DomainResult.Ok -> value
        is DomainResult.Err -> fail("期望 Ok 实为 Err: $error")
    }

    private fun <T> DomainResult<T>.unwrapErr(): KgError = when (this) {
        is DomainResult.Ok -> fail("期望 Err 实为 Ok: $value")
        is DomainResult.Err -> error
    }

    private fun nodeIdByTitle(svc: KnowledgeGraphService, title: String): String =
        svc.storage.nodes.entries.single { it.value.title == title }.key

    private fun spec(title: String, vararg children: PrerequisiteSpec) =
        PrerequisiteSpec(title = title, children = children.toList())

    // ------------------------------------------------------------------
    // 建题 + 追加前置 + 树视图
    // ------------------------------------------------------------------

    @Test
    fun `建题-自动分配序号且可物化树`() = runBlocking {
        val svc = newService()
        val q1 = svc.createQuestion("c1", "两数之和", KgCategory.ALGORITHM).unwrap()
        val q2 = svc.createQuestion("c2", "三数之和", KgCategory.ALGORITHM).unwrap()
        assertEquals(1, q1.problemNo)
        assertEquals(2, q2.problemNo)

        svc.appendPrerequisites("c3", q1.rootNodeId, listOf(spec("哈希表", spec("数组")))).unwrap()
        val tree = svc.treeOf(q1.questionId).unwrap()
        assertEquals(q1.questionId, tree.questionId)
        assertEquals(3, tree.nodes.size)
        assertEquals(
            listOf(listOf(q1.rootNodeId), listOf(nodeIdByTitle(svc, "哈希表")), listOf(nodeIdByTitle(svc, "数组"))),
            tree.layers,
        )
    }

    // ------------------------------------------------------------------
    // 幂等
    // ------------------------------------------------------------------

    @Test
    fun `幂等-同commandId同指纹重放返回相同结果且无双写`() = runBlocking {
        val svc = newService()
        val first = svc.createQuestion("cmd-x", "题", KgCategory.CS_BASIC).unwrap()
        val second = svc.createQuestion("cmd-x", "题", KgCategory.CS_BASIC).unwrap()
        assertEquals(first, second)
        assertEquals(1, svc.storage.questions.size)
        assertEquals(1, svc.storage.nodes.size)
        assertEquals(1, svc.storage.outbox.size) // 无双写

        svc.appendPrerequisites("cmd-a", first.rootNodeId, listOf(spec("前置甲"))).unwrap()
        val edgesBefore = svc.storage.edges.size
        val outboxBefore = svc.storage.outbox.size
        val replay = svc.appendPrerequisites("cmd-a", first.rootNodeId, listOf(spec("前置甲"))).unwrap()
        assertEquals(edgesBefore, svc.storage.edges.size)
        assertEquals(outboxBefore, svc.storage.outbox.size)
        assertEquals(2, replay.nodes.size) // 返回首次快照
    }

    @Test
    fun `幂等-同commandId异指纹抛CommandConflict`() = runBlocking {
        val svc = newService()
        svc.createQuestion("cmd-x", "题", KgCategory.CS_BASIC).unwrap()
        val err = svc.createQuestion("cmd-x", "另一题", KgCategory.CS_BASIC).unwrapErr()
        assertTrue(err is KgError.CommandConflict)
        assertEquals(1, svc.storage.questions.size)
    }

    @Test
    fun `幂等-掌握度命令重放不重复累计attempts`() = runBlocking {
        val svc = newService()
        val q = svc.createQuestion("c1", "题", KgCategory.ALGORITHM).unwrap()
        val first = svc.recordVerification("v1", q.rootNodeId, "quiz-1", Verdict.PASS).unwrap()
        val replay = svc.recordVerification("v1", q.rootNodeId, "quiz-1", Verdict.PASS).unwrap()
        assertEquals(first, replay)
        assertEquals(1, replay.mastery.attempts)
        assertEquals(1, svc.storage.masteryEvents.size)
        val conflict = svc.recordVerification("v1", q.rootNodeId, "quiz-1", Verdict.FAIL).unwrapErr()
        assertTrue(conflict is KgError.CommandConflict)
    }

    @Test
    fun `指纹-canonicalize与子列表顺序空白无关`() {
        val s1 = PrerequisiteSpec(" 根 ", children = listOf(PrerequisiteSpec("B"), PrerequisiteSpec("A")))
        val s2 = PrerequisiteSpec("根", children = listOf(PrerequisiteSpec("A "), PrerequisiteSpec("B")))
        assertEquals(Fingerprint.canonicalize(s1), Fingerprint.canonicalize(s2))
        assertEquals(Fingerprint.fingerprint(s1), Fingerprint.fingerprint(s2))
        assertEquals(Fingerprint.MatchResult.NEW, Fingerprint.matches("c", "f", null))
        assertEquals(Fingerprint.MatchResult.REPLAY, Fingerprint.matches("c", "f", "f"))
        assertEquals(Fingerprint.MatchResult.CONFLICT, Fingerprint.matches("c", "f", "g"))
    }

    // ------------------------------------------------------------------
    // 环检测(服务级, 含 ADR-1)
    // ------------------------------------------------------------------

    @Test
    fun `环检测-GLOBAL回边被拒绝且整命令原子不生效`() = runBlocking {
        val svc = newService()
        val q = svc.createQuestion("c1", "题", KgCategory.ALGORITHM).unwrap()
        svc.appendPrerequisites("c2", q.rootNodeId, listOf(spec("X", spec("Y")))).unwrap()
        val edgesBefore = svc.storage.edges.size
        val err = svc.appendPrerequisites(
            "c3", nodeIdByTitle(svc, "Y"),
            listOf(PrerequisiteSpec(title = "回边", existingNodeId = q.rootNodeId)),
        ).unwrapErr()
        assertTrue(err is KgError.CycleDetected)
        val path = (err as KgError.CycleDetected).path
        assertEquals(path.first(), path.last()) // 闭合环
        assertTrue(path.containsAll(listOf(q.rootNodeId, nodeIdByTitle(svc, "X"), nodeIdByTitle(svc, "Y"))))
        assertEquals(edgesBefore, svc.storage.edges.size) // 原子: 无半拉子写入
    }

    @Test
    fun `环检测-ADR1-GLOBAL插入在GLOBAL并全部LOCAL上查环`() = runBlocking {
        val svc = newService()
        val qd = svc.createQuestion("c1", "题D", KgCategory.ALGORITHM).unwrap()
        val qe = svc.createQuestion("c2", "题E", KgCategory.ALGORITHM).unwrap()
        svc.appendPrerequisites("c3", qd.rootNodeId, listOf(spec("M"))).unwrap() // GLOBAL: rd→m
        val m = nodeIdByTitle(svc, "M")
        // qE 的私有分支: m→n(PROBLEM_LOCAL(qE))
        svc.appendPrerequisites("c4", m, listOf(spec("N")), EdgeScope.PROBLEM_LOCAL, qe.questionId).unwrap()
        val n = nodeIdByTitle(svc, "N")
        // 若只看 GLOBAL 边, n→rd 不成环; 但 LOCAL(qE) 的 m→n 让 rd→m→n→rd 成环 → 保守拒绝
        val err = svc.appendPrerequisites(
            "c5", n, listOf(PrerequisiteSpec(title = "回边", existingNodeId = qd.rootNodeId)),
        ).unwrapErr()
        assertTrue(err is KgError.CycleDetected)
    }

    // ------------------------------------------------------------------
    // ProblemLocal 隔离 / 共享子树
    // ------------------------------------------------------------------

    @Test
    fun `ProblemLocal隔离-B的私有分支不出现在A的treeOf`() = runBlocking {
        val svc = newService()
        val qa = svc.createQuestion("c1", "题A", KgCategory.ALGORITHM).unwrap()
        val qb = svc.createQuestion("c2", "题B", KgCategory.ALGORITHM).unwrap()
        svc.appendPrerequisites("c3", qa.rootNodeId, listOf(spec("共享基础S"))).unwrap()
        val s = nodeIdByTitle(svc, "共享基础S")
        svc.appendPrerequisites("c4", qb.rootNodeId, listOf(PrerequisiteSpec(title = "引用S", existingNodeId = s))).unwrap()
        // B 在共享节点下挂私有分支
        svc.appendPrerequisites("c5", s, listOf(spec("B的私有分支")), EdgeScope.PROBLEM_LOCAL, qb.questionId).unwrap()

        val treeA = svc.treeOf(qa.questionId).unwrap()
        val treeB = svc.treeOf(qb.questionId).unwrap()
        assertTrue(treeA.nodes.any { it.title == "共享基础S" })
        assertTrue(treeA.nodes.none { it.title == "B的私有分支" }) // 隔离
        assertTrue(treeB.nodes.any { it.title == "B的私有分支" })
        assertTrue(treeB.nodes.any { it.title == "共享基础S" })
    }

    @Test
    fun `共享子树-修改对双树可见`() = runBlocking {
        val svc = newService()
        val qa = svc.createQuestion("c1", "题A", KgCategory.ALGORITHM).unwrap()
        val qb = svc.createQuestion("c2", "题B", KgCategory.ALGORITHM).unwrap()
        svc.appendPrerequisites("c3", qa.rootNodeId, listOf(spec("共享基础S"))).unwrap()
        val s = nodeIdByTitle(svc, "共享基础S")
        svc.appendPrerequisites("c4", qb.rootNodeId, listOf(PrerequisiteSpec(title = "引用S", existingNodeId = s))).unwrap()
        // 在共享节点下追加 GLOBAL 子节点 → 两棵树都可见
        svc.appendPrerequisites("c5", s, listOf(spec("S的下级X"))).unwrap()
        assertTrue(svc.treeOf(qa.questionId).unwrap().nodes.any { it.title == "S的下级X" })
        assertTrue(svc.treeOf(qb.questionId).unwrap().nodes.any { it.title == "S的下级X" })
    }

    @Test
    fun `reroot-subtreeOf以共享节点为根且不包含问题根`() = runBlocking {
        val svc = newService()
        val qa = svc.createQuestion("c1", "题A", KgCategory.ALGORITHM).unwrap()
        val qb = svc.createQuestion("c2", "题B", KgCategory.ALGORITHM).unwrap()
        svc.appendPrerequisites("c3", qa.rootNodeId, listOf(spec("S", spec("X")))).unwrap()
        val s = nodeIdByTitle(svc, "S")
        svc.appendPrerequisites("c4", qb.rootNodeId, listOf(PrerequisiteSpec(title = "引用S", existingNodeId = s))).unwrap()

        val sub = svc.subtreeOf(s).unwrap()
        assertEquals(s, sub.rootNodeId)
        assertEquals(setOf(s, nodeIdByTitle(svc, "X")), sub.nodes.map { it.nodeId }.toSet())
        assertTrue(sub.nodes.none { it.nodeId == qa.rootNodeId || it.nodeId == qb.rootNodeId })
        assertEquals(listOf(s), sub.layers[0])
    }

    // ------------------------------------------------------------------
    // 掌握度联动 + 漏洞诊断
    // ------------------------------------------------------------------

    @Test
    fun `recordVerification-联动掌握度并给出得分最低3个前置`() = runBlocking {
        val svc = newService()
        val q = svc.createQuestion("c1", "题", KgCategory.ALGORITHM).unwrap()
        svc.appendPrerequisites("c2", q.rootNodeId, listOf(spec("甲"), spec("乙"), spec("丙"))).unwrap()
        val (a, b, c) = listOf("甲", "乙", "丙").map { nodeIdByTitle(svc, it) }
        svc.recordVerification("v-a", a, "quiz-a", Verdict.FAIL).unwrap() // 0.0 FRAGILE
        svc.recordVerification("v-b", b, "quiz-b", Verdict.PASS).unwrap() // 0.2 LEARNING
        svc.recordVerification("v-c1", c, "quiz-c1", Verdict.PASS).unwrap()
        svc.recordVerification("v-c2", c, "quiz-c2", Verdict.PASS).unwrap() // 0.4 LEARNING

        val update = svc.recordVerification("v-r", q.rootNodeId, "quiz-r", Verdict.FAIL).unwrap()
        assertEquals(MasteryState.FRAGILE, update.mastery.state)
        assertEquals(0.0, update.mastery.score, 1e-9)
        assertEquals(listOf(a, b, c), update.suggestedNext.map { it.nodeId }) // 按得分升序
        assertTrue(update.suggestedNext.size <= 3)
    }

    @Test
    fun `recordVerification-近3次含FAIL不升MASTERED-窗口滑动后升级`() = runBlocking {
        // RFC §3.5: MASTERED 需 score>=0.8 且近 3 次(含本次)无 FAIL;
        // 历史 verdict 由服务从 kg_mastery_event 在写入本次事件前查询
        val svc = newService()
        val q = svc.createQuestion("c1", "题", KgCategory.ALGORITHM).unwrap()
        val n = q.rootNodeId
        // [PASS,PASS,PASS,PASS] → 第 4 次 0.8 且近 3 次无 FAIL → MASTERED
        repeat(4) { svc.recordVerification("p$it", n, "quiz-p$it", Verdict.PASS).unwrap() }
        assertEquals(MasteryState.MASTERED, svc.storage.masteries.getValue(n).state)

        svc.recordVerification("f1", n, "quiz-f1", Verdict.FAIL).unwrap() // 0.55 FRAGILE
        val s1 = svc.recordVerification("p4", n, "quiz-p4", Verdict.PASS).unwrap() // 0.75, 窗口 [P,F]+P
        assertEquals(MasteryState.LEARNING, s1.mastery.state)
        val s2 = svc.recordVerification("p5", n, "quiz-p5", Verdict.PASS).unwrap() // 0.95, 窗口 [F,P]+P
        assertEquals(0.95, s2.mastery.score, 1e-9)
        assertEquals(MasteryState.LEARNING, s2.mastery.state) // score 够但近 3 次含 FAIL
        val s3 = svc.recordVerification("p6", n, "quiz-p6", Verdict.PASS).unwrap() // 1.0, 窗口 [P,P]+P
        assertEquals(MasteryState.MASTERED, s3.mastery.state) // FAIL 滑出窗口
    }

    @Test
    fun `diagnoseGap-定位加权最弱前置`() = runBlocking {
        val svc = newService()
        val q = svc.createQuestion("c1", "题", KgCategory.ALGORITHM).unwrap()
        svc.appendPrerequisites("c2", q.rootNodeId, listOf(spec("甲"), spec("乙"))).unwrap()
        val a = nodeIdByTitle(svc, "甲")
        val b = nodeIdByTitle(svc, "乙")
        svc.recordVerification("v-a", a, "quiz-a", Verdict.FAIL).unwrap() // 甲 0.0
        svc.recordVerification("v-b", b, "quiz-b", Verdict.PASS).unwrap() // 乙 0.2
        svc.recordVerification("v-r", q.rootNodeId, "quiz-r", Verdict.FAIL).unwrap()

        val d = svc.diagnoseGap("quiz-r").unwrap()
        assertEquals(q.rootNodeId, d.failedNodeId)
        assertEquals(a, d.weakestPrerequisite?.nodeId) // 甲: (1-0)*(1+1)=2 > 乙: 0.8*2=1.6
        assertEquals(1, d.weakestPrerequisite?.blocksCount) // 根依赖它
        assertEquals(1, d.weakestPrerequisite?.recentFailures)
        assertEquals(SuggestedAction.REINFORCE_PREREQUISITE, d.suggestedAction)
    }

    @Test
    fun `diagnoseGap-叶子节点无前置-LeafReinforce`() = runBlocking {
        val svc = newService()
        val q = svc.createQuestion("c1", "题", KgCategory.ALGORITHM).unwrap()
        svc.appendPrerequisites("c2", q.rootNodeId, listOf(spec("叶"))).unwrap()
        val leaf = nodeIdByTitle(svc, "叶")
        svc.recordVerification("v-l", leaf, "quiz-l", Verdict.FAIL).unwrap()
        val d = svc.diagnoseGap("quiz-l").unwrap()
        assertEquals(leaf, d.failedNodeId)
        assertEquals(null, d.weakestPrerequisite)
        assertEquals(SuggestedAction.LEAF_REINFORCE, d.suggestedAction)
    }

    // ------------------------------------------------------------------
    // 瓶颈
    // ------------------------------------------------------------------

    @Test
    fun `瓶颈-三棵树共享前置-大于等于2棵树才入选且排序正确`() = runBlocking {
        val svc = newService()
        val q1 = svc.createQuestion("c1", "题1", KgCategory.ALGORITHM).unwrap()
        svc.appendPrerequisites("a1", q1.rootNodeId, listOf(spec("链A", spec("中点mid", spec("地基base"))))).unwrap()
        val mid = nodeIdByTitle(svc, "中点mid")
        val q2 = svc.createQuestion("c2", "题2", KgCategory.ALGORITHM).unwrap()
        svc.appendPrerequisites(
            "a2", q2.rootNodeId,
            listOf(spec("链B", PrerequisiteSpec(title = "引用mid", existingNodeId = mid))),
        ).unwrap()
        val q3 = svc.createQuestion("c3", "题3", KgCategory.ALGORITHM).unwrap()
        svc.appendPrerequisites("a3", q3.rootNodeId, listOf(spec("链C", spec("孤点loner")))).unwrap()

        val (cA, cB, cC) = listOf("链A", "链B", "链C").map { nodeIdByTitle(svc, it) }
        svc.recordVerification("w1", cA, "quiz-1", Verdict.FAIL).unwrap()
        svc.recordVerification("w2", cB, "quiz-2", Verdict.FAIL).unwrap()
        svc.recordVerification("w3", cC, "quiz-3", Verdict.FAIL).unwrap()

        val result = svc.bottlenecks(minDistinctQuestions = 2, limit = 20).unwrap()
        // base、mid 各被 q1/q2 两棵树的弱节点依赖; loner 只被 q3 → 落选; 弱节点自身(1 棵)也落选
        assertEquals(listOf(nodeIdByTitle(svc, "地基base"), mid), result.map { it.nodeId })
        assertEquals(2, result[0].distinctQuestionCount)
        assertTrue(result[0].blocksCount > result[1].blocksCount) // base 阻塞面更大排前

        // q3 的弱链也挂到 mid 下后, mid 升至 3 棵
        svc.appendPrerequisites(
            "a4", cC, listOf(PrerequisiteSpec(title = "引用mid2", existingNodeId = mid)),
        ).unwrap()
        val after = svc.bottlenecks(minDistinctQuestions = 2, limit = 20).unwrap()
        assertEquals(3, after.single { it.nodeId == mid }.distinctQuestionCount)
    }

    // ------------------------------------------------------------------
    // 导出
    // ------------------------------------------------------------------

    @Test
    fun `导出-contentHash对相同图稳定-对变更图不同`() = runBlocking {
        val svc = newService()
        val q = svc.createQuestion("c1", "题", KgCategory.ALGORITHM).unwrap()
        svc.appendPrerequisites("c2", q.rootNodeId, listOf(spec("前置甲"))).unwrap()

        val e1 = svc.export3d(q.questionId, rootIsQuestion = true).unwrap()
        val e2 = svc.export3d(q.questionId, rootIsQuestion = true).unwrap()
        assertEquals(e1.contentHash, e2.contentHash) // 稳定
        assertEquals(2, e1.nodeCount)
        assertEquals(1, e1.edgeCount)
        assertTrue(e1.payloadJson.contains("\"schemaVersion\":1"))
        assertTrue(e1.payloadJson.contains("\"contentHash\":\"${e1.contentHash}\""))
        // 节点按 (layer, id) 排序: 根(layer 0)先于前置(layer 1)
        val rootPos = e1.payloadJson.indexOf("\"id\":\"${q.rootNodeId}\"")
        val childPos = e1.payloadJson.indexOf("\"id\":\"${nodeIdByTitle(svc, "前置甲")}\"")
        assertTrue(rootPos in 0 until childPos)

        svc.appendPrerequisites("c3", q.rootNodeId, listOf(spec("前置乙"))).unwrap()
        val e3 = svc.export3d(q.questionId, rootIsQuestion = true).unwrap()
        assertNotEquals(e1.contentHash, e3.contentHash) // 变更 → hash 不同
        assertEquals(3, e3.nodeCount)
    }

    @Test
    fun `导出-links按source-target-scope升序稳定排序`() = runBlocking {
        val svc = newService()
        val q1 = svc.createQuestion("c1", "题1", KgCategory.ALGORITHM).unwrap()
        val q2 = svc.createQuestion("c2", "题2", KgCategory.ALGORITHM).unwrap()
        // 先建 PROBLEM_LOCAL(q2) 边 r→x(edgeId 较小), 再建同端点 GLOBAL 边(edgeId 较大):
        // 若按 (parent,child,edgeId) 排序 LOCAL 在前; 契约要求 (source,target,scope) 升序 → GLOBAL 在前
        svc.appendPrerequisites(
            "c3", q1.rootNodeId, listOf(spec("子X")), EdgeScope.PROBLEM_LOCAL, q2.questionId,
        ).unwrap()
        val x = nodeIdByTitle(svc, "子X")
        svc.appendPrerequisites(
            "c4", q1.rootNodeId, listOf(PrerequisiteSpec(title = "引用X", existingNodeId = x)),
        ).unwrap()

        val e = svc.export3d(q1.rootNodeId, rootIsQuestion = false).unwrap()
        assertEquals(2, e.edgeCount)
        val globalPos = e.payloadJson.indexOf("\"scope\":\"GLOBAL\"")
        val localPos = e.payloadJson.indexOf("\"scope\":\"PROBLEM_LOCAL\"")
        assertTrue(globalPos in 0 until localPos) // GLOBAL 字典序先于 PROBLEM_LOCAL
        val linksSection = e.payloadJson.substringAfter("\"links\":[")
        assertTrue(
            linksSection.startsWith(
                "{\"source\":\"${q1.rootNodeId}\",\"target\":\"$x\",\"scope\":\"GLOBAL\"}," +
                    "{\"source\":\"${q1.rootNodeId}\",\"target\":\"$x\",\"scope\":\"PROBLEM_LOCAL\"}",
            ),
        )
    }

    // ------------------------------------------------------------------
    // JD 批次进度
    // ------------------------------------------------------------------

    @Test
    fun `expertiseProgress-按状态计数且按分类聚合`() = runBlocking {
        val svc = newService()
        val q1 = svc.createQuestion("c1", "题1", KgCategory.ALGORITHM, jdBatchId = "b1").unwrap()
        svc.appendPrerequisites("a1", q1.rootNodeId, listOf(spec("前置T1"))).unwrap()
        val q2 = svc.createQuestion("c2", "题2", KgCategory.ALGORITHM, jdBatchId = "b1").unwrap()
        svc.appendPrerequisites("a2", q2.rootNodeId, listOf(spec("前置T2"))).unwrap()
        val t1 = nodeIdByTitle(svc, "前置T1")
        val t2 = nodeIdByTitle(svc, "前置T2")
        repeat(4) { svc.recordVerification("m$it", t1, "quiz-t1-$it", Verdict.PASS).unwrap() } // MASTERED
        svc.recordVerification("f1", t2, "quiz-t2", Verdict.FAIL).unwrap() // FRAGILE

        val p = svc.expertiseProgress("b1").unwrap()
        assertEquals(4, p.total) // 2 根 + 2 前置
        assertEquals(1, p.mastered)
        assertEquals(1, p.fragile)
        assertEquals(0, p.learning)
        assertEquals(2, p.unknown)
        assertEquals(0.25, p.progress, 1e-9)
        assertEquals(4, p.perCategory[KgCategory.ALGORITHM]?.total)
        assertEquals(1, p.perCategory[KgCategory.ALGORITHM]?.mastered)
    }

    // ------------------------------------------------------------------
    // 提案两段式
    // ------------------------------------------------------------------

    @Test
    fun `提案-确认落库-编辑生效-重放无双写`() = runBlocking {
        var proposed = PrerequisiteSpec("占位")
        val clock = FixedClock()
        val svc = newService(clock, PrerequisiteProposer { _, _, _, _ -> proposed })
        val q = svc.createQuestion("c1", "题", KgCategory.ALGORITHM).unwrap()
        svc.appendPrerequisites("c2", q.rootNodeId, listOf(spec("已有基础E"))).unwrap()
        val existing = nodeIdByTitle(svc, "已有基础E")

        proposed = PrerequisiteSpec(
            "建议链",
            children = listOf(
                PrerequisiteSpec(title = "引用已有", existingNodeId = existing),
                PrerequisiteSpec(title = "全新点"),
            ),
        )
        val proposal = svc.proposePrerequisiteChain(q.rootNodeId, "题面").unwrap()
        assertEquals(listOf(existing), proposal.reusedNodeIds)
        assertEquals(clock.t + KnowledgeGraphService.PROPOSAL_TTL_MS, proposal.expiresAt)
        // 确认前不落图
        assertTrue(svc.treeOf(q.questionId).unwrap().nodes.none { it.title == "建议链" })

        // 用户编辑标题后确认
        val edited = svc.storage.proposals.getValue(proposal.proposalId).payloadJson
            .replace("全新点", "编辑后的点")
        val confirm = svc.confirmProposal("cmd-c1", proposal.proposalId, edited).unwrap()
        assertEquals(2, confirm.createdNodeIds.size)
        assertEquals(listOf(existing), confirm.reusedNodeIds)
        assertTrue(svc.treeOf(q.questionId).unwrap().nodes.any { it.title == "编辑后的点" })
        assertTrue(svc.treeOf(q.questionId).unwrap().nodes.any { it.title == "建议链" })

        // 重放: 相同结果且无双写
        val nodesBefore = svc.storage.nodes.size
        val replay = svc.confirmProposal("cmd-c1", proposal.proposalId, edited).unwrap()
        assertEquals(confirm, replay)
        assertEquals(nodesBefore, svc.storage.nodes.size)
    }

    @Test
    fun `提案-过期拒绝并标记EXPIRED`() = runBlocking {
        val clock = FixedClock()
        val svc = newService(clock)
        val q = svc.createQuestion("c1", "题", KgCategory.ALGORITHM).unwrap()
        val proposal = svc.proposePrerequisiteChain(q.rootNodeId, "题面").unwrap()
        clock.t += KnowledgeGraphService.PROPOSAL_TTL_MS + 1
        val err = svc.confirmProposal("cmd-c2", proposal.proposalId).unwrapErr()
        assertTrue(err is KgError.ProposalExpired)
        assertEquals(ProposalStatus.EXPIRED, svc.storage.proposals.getValue(proposal.proposalId).status)
    }

    @Test
    fun `JD提案-确认后批量建题并入批次`() = runBlocking {
        val svc = newService()
        val proposal = svc.proposeJdDecomposition("高级后端 JD", categories = 2, questionsPerCategory = 2).unwrap()
        assertEquals(4, proposal.questions.size)
        val confirm = svc.confirmProposal("cmd-jd", proposal.proposalId).unwrap()
        assertEquals(4, confirm.createdQuestionIds.size)
        assertEquals(4, confirm.createdNodeIds.size) // 4 个题目根
        val progress = svc.expertiseProgress(proposal.batchId).unwrap()
        assertEquals(4, progress.total)
        assertEquals(4, progress.unknown)
    }
}
