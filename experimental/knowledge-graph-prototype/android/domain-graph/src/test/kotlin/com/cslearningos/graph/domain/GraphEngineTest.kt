package com.cslearningos.graph.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** GraphEngine 纯函数行为测试(输入 List<节点>+List<边>, 断言对外行为而非内部状态) */
class GraphEngineTest {

    private var edgeSeq = 0

    /** 构造依赖边 p → c(p 依赖 c) */
    private fun e(
        p: String,
        c: String,
        scope: EdgeScope = EdgeScope.GLOBAL,
        q: String? = null,
    ) = KgEdge("e${edgeSeq++}", p, c, scope, q)

    private fun nodesOf(vararg ids: String): Map<String, KgNode> =
        ids.associate { it to KgNode(it, "标题-$it") }

    // ------------------------------------------------------------------
    // 环检测
    // ------------------------------------------------------------------

    @Test
    fun `环检测-直接环-返回闭合路径`() {
        val edges = listOf(e("a", "b")) // a 依赖 b
        val path = GraphEngine.wouldCreateCycle(edges, newParent = "b", newChild = "a")
        assertEquals(listOf("b", "a", "b"), path)
    }

    @Test
    fun `环检测-间接环-返回完整闭合路径`() {
        val edges = listOf(e("a", "b"), e("b", "c")) // a→b→c
        val path = GraphEngine.wouldCreateCycle(edges, newParent = "c", newChild = "a")
        assertEquals(listOf("c", "a", "b", "c"), path)
    }

    @Test
    fun `环检测-自环`() {
        val path = GraphEngine.wouldCreateCycle(emptyList(), newParent = "a", newChild = "a")
        assertEquals(listOf("a", "a"), path)
    }

    @Test
    fun `环检测-菱形合法不误报`() {
        // 菱形: root→a, root→b, a→c, b→c(c 两个父, 合法 DAG)
        val edges = listOf(e("root", "a"), e("root", "b"), e("a", "c"), e("b", "c"))
        // 再加一条 root→c(第三个父)也不成环
        assertNull(GraphEngine.wouldCreateCycle(edges, newParent = "root", newChild = "c"))
        // 新节点挂到共享叶下面也不成环
        assertNull(GraphEngine.wouldCreateCycle(edges, newParent = "c", newChild = "d"))
    }

    @Test
    fun `环检测-ProblemLocal边参与查环(ADR-1保守规则)`() {
        val global = e("a", "b")
        val local = e("b", "c", EdgeScope.PROBLEM_LOCAL, "q1")
        // GLOBAL ∪ 全部 LOCAL 上查: c→a 会成环 a→b→c→a
        val union = listOf(global, local)
        val path = GraphEngine.wouldCreateCycle(union, newParent = "c", newChild = "a")
        assertNotNull(path)
        assertEquals(listOf("c", "a", "b", "c"), path)
        // 只在 GLOBAL 边集上查则漏判 —— 说明保守规则必须带上 LOCAL 边
        assertNull(GraphEngine.wouldCreateCycle(listOf(global), newParent = "c", newChild = "a"))
    }

    // ------------------------------------------------------------------
    // 拓扑分层
    // ------------------------------------------------------------------

    @Test
    fun `分层-菱形共享节点取最长深度且只出现一次`() {
        val edges = listOf(e("root", "a"), e("root", "b"), e("a", "c"), e("b", "c"))
        val layers = GraphEngine.layers("root", edges)
        assertEquals(listOf(listOf("root"), listOf("a", "b"), listOf("c")), layers)
        // visit-once: c 只出现在第 2 层
        assertEquals(1, layers.flatten().count { it == "c" })
    }

    @Test
    fun `分层-多父取最长路径而非最短`() {
        // root→c 直达(短路径 1), root→x→c(长路径 2): depth(c) 必须取 2
        val edges = listOf(e("root", "c"), e("root", "x"), e("x", "c"))
        val layers = GraphEngine.layers("root", edges)
        assertEquals(listOf(listOf("root"), listOf("x"), listOf("c")), layers)
    }

    @Test
    fun `分层-不可达节点不进入结果`() {
        val edges = listOf(e("root", "a"), e("z", "y"))
        assertEquals(listOf(listOf("root"), listOf("a")), GraphEngine.layers("root", edges))
    }

    // ------------------------------------------------------------------
    // 树物化 / reroot
    // ------------------------------------------------------------------

    @Test
    fun `树物化-共享节点标记与视图内容`() {
        val edges = listOf(e("root", "a"), e("root", "b"), e("a", "c"), e("b", "c"))
        val snap = GraphEngine.buildTreeSnapshot(
            root = "root",
            nodes = nodesOf("root", "a", "b", "c"),
            edges = edges,
            masteries = emptyMap(),
            questionId = "q1",
        )
        assertEquals("q1", snap.questionId)
        assertEquals(4, snap.nodes.size)
        assertEquals(setOf("c"), snap.sharedNodeIds)
        assertEquals(2, snap.nodes.single { it.nodeId == "c" }.parentCount)
        assertEquals(0, snap.nodes.single { it.nodeId == "root" }.depth)
        assertEquals(2, snap.nodes.single { it.nodeId == "c" }.depth)
        assertEquals(listOf(listOf("root"), listOf("a", "b"), listOf("c")), snap.layers)
        // 缺省掌握度按 UNKNOWN 零值输出
        assertEquals(MasteryState.UNKNOWN, snap.nodes.single { it.nodeId == "c" }.mastery.state)
    }

    @Test
    fun `reroot-任意节点当根-复用同一物化零特殊逻辑`() {
        val edges = listOf(e("root", "a"), e("root", "b"), e("a", "c"), e("b", "c"))
        val snap = GraphEngine.buildTreeSnapshot(
            root = "a",
            nodes = nodesOf("root", "a", "b", "c"),
            edges = edges,
            masteries = emptyMap(),
        )
        assertEquals("a", snap.rootNodeId)
        assertEquals(null, snap.questionId)
        assertEquals(setOf("a", "c"), snap.nodes.map { it.nodeId }.toSet())
        assertEquals(listOf(listOf("a"), listOf("c")), snap.layers)
        // 该视图内 c 只有一个父 → 不是共享节点
        assertTrue(snap.sharedNodeIds.isEmpty())
    }

    // ------------------------------------------------------------------
    // blocksCount
    // ------------------------------------------------------------------

    @Test
    fun `blocksCount-链式反向可达下游数`() {
        val edges = listOf(e("a", "b"), e("b", "c"))
        val blocks = GraphEngine.blocksCount(edges)
        assertEquals(0, blocks["a"]) // 无人依赖根
        assertEquals(1, blocks["b"])
        assertEquals(2, blocks["c"]) // a、b 都传递依赖 c
        assertEquals(2, GraphEngine.blocksCount("c", edges))
        assertEquals(0, GraphEngine.blocksCount("不存在", edges))
    }

    @Test
    fun `blocksCount-菱形底端阻塞全部上游`() {
        val edges = listOf(e("root", "a"), e("root", "b"), e("a", "c"), e("b", "c"))
        assertEquals(3, GraphEngine.blocksCount("c", edges))
    }

    // ------------------------------------------------------------------
    // 瓶颈聚合
    // ------------------------------------------------------------------

    @Test
    fun `瓶颈-三棵树共享前置-门槛与排序`() {
        // q1: r1→cA→mid→base; q2: r2→cB→mid; q3: r3→cC→loner
        val edges = listOf(
            e("r1", "cA"), e("cA", "mid"), e("mid", "base"),
            e("r2", "cB"), e("cB", "mid"),
            e("r3", "cC"), e("cC", "loner"),
        )
        val nodes = nodesOf("r1", "cA", "mid", "base", "r2", "cB", "r3", "cC", "loner")
        val masteries = mapOf(
            "cA" to NodeMastery("cA", MasteryState.FRAGILE, score = 0.4, attempts = 1, failStreak = 1),
            "cB" to NodeMastery("cB", MasteryState.FRAGILE, score = 0.3, attempts = 1, failStreak = 1),
            "cC" to NodeMastery("cC", MasteryState.LEARNING, score = 0.5, attempts = 1),
        )
        val questionRoots = mapOf("q1" to "r1", "q2" to "r2", "q3" to "r3")

        val result = GraphEngine.bottlenecks(
            nodes = nodes,
            masteries = masteries,
            recentFailures = emptyMap(),
            edges = edges,
            questionRoots = questionRoots,
            minDistinctQuestions = 2,
            limit = 20,
        )
        // base 与 mid 各被 q1、q2 两棵树的弱节点依赖; loner 只被 q3 一棵 → 落选
        assertEquals(listOf("base", "mid"), result.map { it.nodeId })
        assertEquals(2, result[0].distinctQuestionCount)
        // 排序: 棵数相同按 blocksCount 降序(base=5 > mid=4)
        assertTrue(result[0].blocksCount > result[1].blocksCount)
        assertEquals("标题-base", result[0].title)
    }

    @Test
    fun `瓶颈-弱节点自身计入闭包-棵数升序排序与门槛收紧`() {
        // q1: r1→w1→shared; q2: r2→w2→shared; q3: r3→w3→shared(三棵树的弱节点都依赖 shared)
        val edges = listOf(
            e("r1", "w1"), e("w1", "shared"),
            e("r2", "w2"), e("w2", "shared"),
            e("r3", "w3"), e("w3", "shared"),
        )
        val nodes = nodesOf("r1", "w1", "r2", "w2", "r3", "w3", "shared")
        val masteries = mapOf(
            "w1" to NodeMastery("w1", MasteryState.FRAGILE, score = 0.4),
            "w2" to NodeMastery("w2", MasteryState.FRAGILE, score = 0.4),
            "w3" to NodeMastery("w3", MasteryState.FRAGILE, score = 0.4),
        )
        val questionRoots = mapOf("q1" to "r1", "q2" to "r2", "q3" to "r3")

        val result = GraphEngine.bottlenecks(nodes, masteries, emptyMap(), edges, questionRoots, 2, 20)
        // shared 被 3 棵树的弱节点依赖排第一; 弱节点自身各只被 1 棵树"依赖"(自反) → 落选
        assertEquals(listOf("shared"), result.map { it.nodeId })
        assertEquals(3, result[0].distinctQuestionCount)
        // 门槛收紧到 4 → 无人入选
        assertTrue(
            GraphEngine.bottlenecks(nodes, masteries, emptyMap(), edges, questionRoots, 4, 20).isEmpty(),
        )
    }

    @Test
    fun `瓶颈-棵数与阻塞面全并列时按nodeId升序`() {
        // 排序键: distinctQuestionCount 降序 → blocksCount 降序 → nodeId 升序
        // q1: r1→w1→{z,a}; q2: r2→w2→{z,a}: a/z 同被 2 棵树的弱节点依赖且阻塞面相同
        val edges = listOf(
            e("r1", "w1"), e("w1", "z"), e("w1", "a"),
            e("r2", "w2"), e("w2", "z"), e("w2", "a"),
        )
        val nodes = nodesOf("r1", "w1", "r2", "w2", "z", "a")
        val masteries = mapOf(
            "w1" to NodeMastery("w1", MasteryState.FRAGILE, score = 0.4),
            "w2" to NodeMastery("w2", MasteryState.FRAGILE, score = 0.4),
        )
        val result = GraphEngine.bottlenecks(
            nodes, masteries, emptyMap(), edges, mapOf("q1" to "r1", "q2" to "r2"), 2, 20,
        )
        assertEquals(listOf("a", "z"), result.map { it.nodeId }) // 前两级全并列 → nodeId 升序
        assertTrue(result.all { it.distinctQuestionCount == 2 && it.blocksCount == 4 })
    }

    @Test
    fun `瓶颈-LOCAL(q)边只属于该问题的树`() {
        // q1 的弱节点经 LOCAL(q1) 边依赖 localBase; q2 树看不到这条边
        val edges = listOf(
            e("r1", "w1"), e("w1", "localBase", EdgeScope.PROBLEM_LOCAL, "q1"),
            e("r2", "w2"), e("w2", "gBase"),
        )
        val nodes = nodesOf("r1", "w1", "localBase", "r2", "w2", "gBase")
        val masteries = mapOf(
            "w1" to NodeMastery("w1", MasteryState.FRAGILE, score = 0.4),
            "w2" to NodeMastery("w2", MasteryState.FRAGILE, score = 0.4),
        )
        val result = GraphEngine.bottlenecks(
            nodes, masteries, emptyMap(), edges, mapOf("q1" to "r1", "q2" to "r2"), 1, 20,
        )
        val byId = result.associateBy { it.nodeId }
        assertEquals(setOf("localBase", "gBase"), byId.keys - setOf("w1", "w2"))
        assertEquals(1, byId.getValue("localBase").distinctQuestionCount)
    }
}
