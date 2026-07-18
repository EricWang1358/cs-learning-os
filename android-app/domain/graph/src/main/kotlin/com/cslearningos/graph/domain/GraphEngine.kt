package com.cslearningos.graph.domain

/**
 * 图节点(图算法输入的最小载体, 对应 learning_nodes 的一行).
 * 引擎只依赖 id/title, 不依赖任何存储/Android 类型.
 */
data class KgNode(
    val nodeId: String,
    val title: String,
    val markdownBody: String = "",
)

/**
 * 依赖边: [parentNodeId] 依赖 [childNodeId](child 是前置).
 * [scopeQuestionId] 仅当 [scope] = [EdgeScope.PROBLEM_LOCAL] 时有意义.
 */
data class KgEdge(
    val edgeId: String,
    val parentNodeId: String,
    val childNodeId: String,
    val scope: EdgeScope = EdgeScope.GLOBAL,
    val scopeQuestionId: String? = null,
)

/**
 * 纯函数图算法引擎(RFC §3.1/§3.4/§3.5).
 * 全部函数无副作用、确定性输出(邻接按 id 排序), 输入均为普通 List/Map, 便于单测与跨端复用.
 */
object GraphEngine {

    // ------------------------------------------------------------------
    // 基础遍历
    // ------------------------------------------------------------------

    /**
     * 从 [root] 沿边方向(parent → child)求可达闭包(含 [root] 自身).
     * 用于"树 = 视图"的可达集计算与 reroot.
     */
    fun reachableFrom(root: String, edges: List<KgEdge>): Set<String> {
        val adj = childrenIndex(edges)
        val seen = LinkedHashSet<String>()
        val stack = ArrayDeque<String>()
        seen.add(root)
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val u = stack.removeLast()
            for (e in adj[u].orEmpty()) {
                if (seen.add(e.childNodeId)) stack.addLast(e.childNodeId)
            }
        }
        return seen
    }

    // ------------------------------------------------------------------
    // 1. 环检测
    // ------------------------------------------------------------------

    /**
     * 预插入环检测: 若加入边 [newParent] → [newChild], 是否会在 [edges] 上成环.
     *
     * 判定: 沿边方向从 [newChild] 出发 DFS 可达 [newParent] 即成环(新边把路径闭合成环).
     * 返回成环路径 `[newParent, newChild, ..., newParent]`(依赖顺序, 首尾闭合); 不成环返回 null.
     *
     * ADR-1 保守规则由调用方组合边集实现: 插 GLOBAL 边时传入 GLOBAL ∪ 全部 PROBLEM_LOCAL 边.
     */
    fun wouldCreateCycle(edges: List<KgEdge>, newParent: String, newChild: String): List<String>? {
        if (newParent == newChild) return listOf(newParent, newChild)
        val adj = childrenIndex(edges)
        val prev = HashMap<String, String>()
        val visited = HashSet<String>()
        val stack = ArrayDeque<String>()
        visited.add(newChild)
        stack.addLast(newChild)
        var found = false
        while (stack.isNotEmpty() && !found) {
            val u = stack.removeLast()
            for (e in adj[u].orEmpty()) {
                val v = e.childNodeId
                if (visited.add(v)) {
                    prev[v] = u
                    if (v == newParent) {
                        found = true
                        break
                    }
                    stack.addLast(v)
                }
            }
        }
        if (!found) return null
        // 重建 newChild → ... → newParent 路径, 再在前面补上经新边回到 newChild 的 newParent
        val path = ArrayList<String>()
        var cur: String? = newParent
        while (cur != null) {
            path.add(cur)
            if (cur == newChild) break
            cur = prev[cur]
        }
        path.reverse()
        return ArrayList<String>(path.size + 1).apply {
            add(newParent)
            addAll(path)
        }
    }

    // ------------------------------------------------------------------
    // 2. 拓扑分层(最长路径深度)
    // ------------------------------------------------------------------

    /**
     * 从 [root] 可达闭包上的拓扑分层: 返回 List(深度 → 该层节点 id 列表, 层内按 id 排序),
     * 第 0 层为 [root]. 节点深度 = 距 root 的最长路径(菱形多父取最长, 保证共享节点只出现一次).
     * 不可达节点不进入结果. 图必须无环(写入侧已用 [wouldCreateCycle] 保证).
     */
    fun layers(root: String, edges: List<KgEdge>): List<List<String>> = groupByDepth(topologicalDepths(root, edges))

    /**
     * 计算 [root] 可达闭包内每个节点的最长路径深度(Kahn 拓扑序 + DP).
     * 确定性: 入度为 0 的初始集合与邻接均按 id 排序.
     */
    fun topologicalDepths(root: String, edges: List<KgEdge>): Map<String, Int> {
        val reachable = reachableFrom(root, edges)
        if (reachable.isEmpty()) return emptyMap()
        val sub = edges.filter { it.parentNodeId in reachable && it.childNodeId in reachable }
        val remaining = reachable.associateWithTo(HashMap()) { 0 }
        for (e in sub) remaining.merge(e.childNodeId, 1, Int::plus)
        val adj = childrenIndex(sub)
        val depth = reachable.associateWithTo(HashMap()) { 0 }
        val queue = ArrayDeque<String>()
        remaining.filter { it.value == 0 }.keys.sorted().forEach { queue.addLast(it) }
        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            val du = depth.getValue(u)
            for (e in adj[u].orEmpty()) {
                val v = e.childNodeId
                if (du + 1 > depth.getValue(v)) depth[v] = du + 1
                val left = remaining.getValue(v) - 1
                remaining[v] = left
                if (left == 0) queue.addLast(v)
            }
        }
        return depth
    }

    // ------------------------------------------------------------------
    // 3. 树物化(reroot 即本函数换根, 零特殊逻辑)
    // ------------------------------------------------------------------

    /**
     * 把 [root] 在 [edges] 上的可达闭包物化为 UI 直渲的 [TreeSnapshot].
     *
     * - 节点深度取最长路径(同 [layers]); 节点按 (depth, nodeId) 排序, 边按 (parent, child, edgeId) 排序;
     * - [KGNodeDto.parentCount] 为本视图边集内的父边数; [TreeSnapshot.sharedNodeIds] = parentCount >= 2 的节点;
     * - [masteries] 缺失的节点按 UNKNOWN 零值投影输出;
     * - reroot: 调用方以任意节点为 [root] 传入即可, 无任何特殊分支.
     */
    fun buildTreeSnapshot(
        root: String,
        nodes: Map<String, KgNode>,
        edges: List<KgEdge>,
        masteries: Map<String, NodeMastery>,
        questionId: String? = null,
    ): TreeSnapshot {
        val depths = topologicalDepths(root, edges)
        val inTree = depths.keys
        val treeEdges = edges.filter { it.parentNodeId in inTree && it.childNodeId in inTree }
        val parentCount = treeEdges.groupBy({ it.childNodeId }, { it.parentNodeId })
            .mapValues { it.value.size }
        val nodeDtos = inTree.map { id ->
            KGNodeDto(
                nodeId = id,
                title = nodes[id]?.title ?: id,
                depth = depths.getValue(id),
                parentCount = parentCount[id] ?: 0,
                mastery = masteries[id] ?: NodeMastery(nodeId = id),
            )
        }.sortedWith(compareBy({ it.depth }, { it.nodeId }))
        val edgeDtos = treeEdges
            .map { KGEdgeDto(it.edgeId, it.parentNodeId, it.childNodeId, it.scope) }
            .sortedWith(compareBy({ it.parent }, { it.child }, { it.edgeId }))
        val shared = parentCount.filterValues { it >= 2 }.keys.toSortedSet()
        return TreeSnapshot(
            rootNodeId = root,
            questionId = questionId,
            nodes = nodeDtos,
            edges = edgeDtos,
            layers = groupByDepth(depths),
            sharedNodeIds = shared,
        )
    }

    // ------------------------------------------------------------------
    // 4. blocksCount(反向可达下游数)
    // ------------------------------------------------------------------

    /**
     * 每个节点的"阻塞面": 沿 parent 方向反向可达的下游节点数(即全网传递依赖该节点的节点数, 不含自身).
     * 对应 RFC §3.5 blocksCount 定义; 懒算, 由调用方在图变更时重算.
     */
    fun blocksCount(edges: List<KgEdge>): Map<String, Int> {
        // 反向邻接: child → 直接依赖它的 parent 列表
        val dependents = edges.groupBy({ it.childNodeId }, { it.parentNodeId })
        val allNodes = HashSet<String>(edges.size * 2)
        for (e in edges) {
            allNodes.add(e.parentNodeId)
            allNodes.add(e.childNodeId)
        }
        val result = HashMap<String, Int>(allNodes.size)
        for (n in allNodes) {
            val seen = HashSet<String>()
            val stack = ArrayDeque<String>()
            seen.add(n)
            stack.addLast(n)
            while (stack.isNotEmpty()) {
                val u = stack.removeLast()
                for (p in dependents[u].orEmpty()) {
                    if (seen.add(p)) stack.addLast(p)
                }
            }
            result[n] = seen.size - 1 // 不含自身
        }
        return result
    }

    /** 单节点版 [blocksCount]: [nodeId] 的反向可达下游节点数(不在图中的节点为 0) */
    fun blocksCount(nodeId: String, edges: List<KgEdge>): Int = blocksCount(edges)[nodeId] ?: 0

    // ------------------------------------------------------------------
    // 5. 瓶颈计算
    // ------------------------------------------------------------------

    /**
     * 瓶颈聚合(RFC §3.5):
     * 1) 弱节点 = [masteries] 中 FRAGILE/LEARNING 且 score < 0.6 的节点(见 [MasteryEngine.isWeak]);
     * 2) 对每棵问题树(边集 = GLOBAL ∪ PROBLEM_LOCAL(该问题)), 取树内弱节点的依赖闭包(沿边方向, 含弱节点自身,
     *    即"该弱节点及其全部传递前置"都算被它依赖);
     * 3) 祖先节点按"被多少棵不同问题的弱节点依赖"聚合计数, 入选门槛 >= [minDistinctQuestions];
     * 4) 按 (distinctQuestionCount 降序, blocksCount 降序, nodeId 升序) 排序, 截取 [limit].
     *
     * [questionRoots] = questionId → rootNodeId; [recentFailures] 为各节点近几次验证的 FAIL 数(展示用).
     */
    fun bottlenecks(
        nodes: Map<String, KgNode>,
        masteries: Map<String, NodeMastery>,
        recentFailures: Map<String, Int>,
        edges: List<KgEdge>,
        questionRoots: Map<String, String>,
        minDistinctQuestions: Int = 2,
        limit: Int = 20,
    ): List<BottleneckNode> {
        val weakIds = masteries.values
            .filter { MasteryEngine.isWeak(it) }
            .mapTo(HashSet()) { it.nodeId }
        val blocks = blocksCount(edges)
        val dependentsOf = HashMap<String, MutableSet<String>>() // 祖先 → 依赖它的问题集合
        for ((questionId, root) in questionRoots.toSortedMap()) {
            val treeEdges = edges.filter { it.scope == EdgeScope.GLOBAL || it.scopeQuestionId == questionId }
            val reachable = reachableFrom(root, treeEdges)
            for (w in reachable.filter { it in weakIds }.sorted()) {
                for (ancestor in reachableFrom(w, treeEdges)) { // 自反闭包: 含 w 自身
                    dependentsOf.getOrPut(ancestor) { sortedSetOf() }.add(questionId)
                }
            }
        }
        return dependentsOf.entries
            .filter { it.value.size >= minDistinctQuestions }
            .map { (nodeId, questions) ->
                BottleneckNode(
                    nodeId = nodeId,
                    title = nodes[nodeId]?.title ?: nodeId,
                    mastery = masteries[nodeId] ?: NodeMastery(nodeId = nodeId),
                    blocksCount = blocks[nodeId] ?: 0,
                    distinctQuestionCount = questions.size,
                    recentFailures = recentFailures[nodeId] ?: 0,
                )
            }
            .sortedWith(
                compareByDescending<BottleneckNode> { it.distinctQuestionCount }
                    .thenByDescending { it.blocksCount }
                    .thenBy { it.nodeId },
            )
            .take(limit)
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /** 子节点邻接表, 邻接边按 (childNodeId, edgeId) 排序保证算法确定性 */
    private fun childrenIndex(edges: List<KgEdge>): Map<String, List<KgEdge>> =
        edges.groupBy { it.parentNodeId }
            .mapValues { (_, list) -> list.sortedWith(compareBy({ it.childNodeId }, { it.edgeId })) }

    /** 深度 → 有序节点列表(层内按 id 排序, 层号连续 0..maxDepth) */
    private fun groupByDepth(depths: Map<String, Int>): List<List<String>> {
        if (depths.isEmpty()) return emptyList()
        val maxDepth = depths.values.max()
        val layers = List(maxDepth + 1) { ArrayList<String>() }
        for ((id, d) in depths) layers[d].add(id)
        return layers.map { it.sorted() }
    }
}
