package com.cslearningos.graph.domain

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 时钟端口(测试注入固定时钟保证确定性) */
fun interface Clock {
    fun now(): Long
}

/** ID 生成端口(测试注入序号生成器保证确定性) */
fun interface IdGenerator {
    fun nextId(prefix: String): String
}

/** AI 前置链建议端口(组合根注入; 返回建议的前置树, 提案本身不落图) */
fun interface PrerequisiteProposer {
    fun propose(nodeId: String, questionText: String, maxDepth: Int, maxBreadth: Int): PrerequisiteSpec
}

/** AI JD 拆解端口(组合根注入; 返回建议的题目列表) */
fun interface JdProposer {
    fun propose(jdText: String, categories: Int, questionsPerCategory: Int): List<JdProposalQuestion>
}

// ----------------------------------------------------------------------
// 内存存储记录(RFC §3.3 schema 的内存投影)
// ----------------------------------------------------------------------

/** kg_question 行(内存版; status 恒 ACTIVE, 不提供归档) */
data class QuestionRecord(
    val questionId: String,
    val rootNodeId: String,
    val areaId: String?,
    val problemNo: Int,
    val title: String,
    val category: KgCategory,
    val jdBatchId: String?,
    val createdAt: Long,
)

/** kg_mastery_event 行(内存版): quiz 证据, 掌握度投影的事实源 */
data class MasteryEvent(
    val eventId: String,
    val nodeId: String,
    val quizItemId: String,
    val verdict: Verdict,
    val commandId: String,
    val createdAt: Long,
)

/** 提案种类 */
enum class ProposalKind { PREREQUISITE_CHAIN, JD_DECOMPOSITION }

/** 提案状态机 */
enum class ProposalStatus { PENDING, CONFIRMED, REJECTED, EXPIRED }

/** kg_proposal 行(内存版) */
data class ProposalRecord(
    val proposalId: String,
    val kind: ProposalKind,
    val payloadJson: String,
    val status: ProposalStatus,
    val expiresAt: Long,
    val createdAt: Long,
)

/** processed_commands 行(内存版): 幂等重放凭据 + 首次执行结果 */
data class ProcessedCommand(
    val commandId: String,
    val fingerprint: String,
    val result: Any,
)

/**
 * 内存版 KgStore(契约测试双打中的 InMemoryKgStore):
 * 纯数据结构, 不加锁(并发控制由 [KnowledgeGraphService] 的 Mutex 负责).
 */
class InMemoryKgStore {
    val nodes = LinkedHashMap<String, KgNode>()
    val edges = LinkedHashMap<String, KgEdge>()
    val questions = LinkedHashMap<String, QuestionRecord>()
    val masteries = LinkedHashMap<String, NodeMastery>()
    val masteryEvents = ArrayList<MasteryEvent>()

    /** quizItemId → nodeId 绑定(recordVerification 时登记, diagnoseGap 时解析) */
    val quizIndex = LinkedHashMap<String, String>()
    val commands = LinkedHashMap<String, ProcessedCommand>()
    val proposals = LinkedHashMap<String, ProposalRecord>()

    /** replication_outbox 的内存等价物: 每次成功写命令追加一条, 用于断言"重放无双写" */
    val outbox = ArrayList<String>()
}

/**
 * 知识图谱门面(RFC §3.2 单 Facade)的纯内存实现.
 *
 * - 证明接口可用: 实现写(幂等)/读(树视图)/规则引擎联动/提案两段式/导出全链路;
 * - 所有写命令经 commandId+fingerprint 幂等: 同指纹重放返回首次结果且无双写, 异指纹抛 [KgError.CommandConflict];
 * - 环检测遵循 ADR-1: 插 GLOBAL 边在 GLOBAL ∪ 全部 PROBLEM_LOCAL 边集上查环; 插 LOCAL(q) 边在 GLOBAL ∪ LOCAL(q) 上查;
 * - 树 = 视图: treeOf 用 GLOBAL ∪ LOCAL(该问题) 边集; subtreeOf(reroot) 用全量边集, 与 treeOf 共用同一物化函数;
 * - AI 建议经注入端口获得, 永不直接生效, 一律 Proposal(TTL) → confirmProposal 幂等落库.
 */
class KnowledgeGraphService(
    private val store: InMemoryKgStore = InMemoryKgStore(),
    private val clock: Clock = Clock { System.currentTimeMillis() },
    private val idGenerator: IdGenerator = IdGenerator { prefix -> prefix + "-" + UUID.randomUUID().toString() },
    private val prerequisiteProposer: PrerequisiteProposer = PrerequisiteProposer { _, questionText, _, _ ->
        PrerequisiteSpec(title = questionText.trim().ifEmpty { "未命名前置" })
    },
    private val jdProposer: JdProposer = JdProposer { jdText, categories, questionsPerCategory ->
        val cats = KgCategory.entries
        (0 until categories).flatMap { c ->
            (1..questionsPerCategory).map { seq ->
                JdProposalQuestion(
                    category = cats[c % cats.size],
                    seq = seq,
                    title = "${cats[c % cats.size]} 第 ${seq} 题: ${jdText.trim().take(24)}",
                )
            }
        }
    },
) {
    private val mutex = Mutex()
    private val revisionFlow = MutableStateFlow(0L)

    /** 只读访问底层存储(契约测试断言用) */
    val storage: InMemoryKgStore get() = store

    // ------------------------------------------------------------------
    // 写(幂等)
    // ------------------------------------------------------------------

    /**
     * 登记问题根(RFC: Question = 登记的 root).
     * [problemNo] 缺省时按 (areaId) 分组取 max+1 自动分配; (areaId, problemNo) 冲突返回 [KgError.Storage].
     */
    suspend fun createQuestion(
        commandId: String,
        title: String,
        category: KgCategory,
        areaId: String? = null,
        problemNo: Int? = null,
        jdBatchId: String? = null,
    ): DomainResult<QuestionSummary> = mutex.withLock {
        val fp = Fingerprint.fingerprintOf(
            listOf("createQuestion", title.trim(), category.name, areaId.orEmpty(),
                problemNo?.toString().orEmpty(), jdBatchId.orEmpty()).joinToString("\n"),
        )
        runIdempotent(commandId, fp) {
            if (title.isBlank()) {
                return@runIdempotent DomainResult.Err(KgError.ProposalShapeInvalid("title 不能为空"))
            }
            val no = problemNo ?: nextProblemNo(areaId)
            val clash = store.questions.values.any {
                (it.areaId ?: "") == (areaId ?: "") && it.problemNo == no
            }
            if (clash) {
                return@runIdempotent DomainResult.Err(
                    KgError.Storage("题目序号冲突: area=${areaId.orEmpty()} problemNo=$no"),
                )
            }
            val nodeId = idGenerator.nextId("node")
            val questionId = idGenerator.nextId("question")
            store.nodes[nodeId] = KgNode(nodeId, title.trim())
            store.questions[questionId] =
                QuestionRecord(questionId, nodeId, areaId, no, title.trim(), category, jdBatchId, clock.now())
            store.outbox.add("QUESTION_CREATED:$questionId:$nodeId")
            DomainResult.Ok(QuestionSummary(questionId, nodeId, no, title.trim(), category))
        }
    }

    /**
     * 在 [parentNodeId] 下追加前置子树(嵌套 [PrerequisiteSpec] 展开).
     * existingNodeId 引用已有节点 → 共享子树(零拷贝); 否则创建新节点.
     * 整命令原子生效: 任一边成环/引用不存在即整体拒绝.
     * 返回快照: [scopeQuestionId] 或 parent 为某问题根时返回该问题树, 否则返回 parent 的 reroot 子树.
     */
    suspend fun appendPrerequisites(
        commandId: String,
        parentNodeId: String,
        specs: List<PrerequisiteSpec>,
        scope: EdgeScope = EdgeScope.GLOBAL,
        scopeQuestionId: String? = null,
    ): DomainResult<TreeSnapshot> = mutex.withLock {
        val fp = Fingerprint.fingerprintOf(buildString {
            append("appendPrerequisites\n").append(parentNodeId).append('\n')
                .append(scope.name).append('\n').append(scopeQuestionId.orEmpty())
            for (s in specs) append('\n').append(Fingerprint.canonicalize(s))
        })
        runIdempotent(commandId, fp) {
            if (!store.nodes.containsKey(parentNodeId)) {
                return@runIdempotent DomainResult.Err(KgError.NotFound("node", parentNodeId))
            }
            if (specs.isEmpty()) {
                return@runIdempotent DomainResult.Err(KgError.ProposalShapeInvalid("specs 不能为空"))
            }
            if (scope == EdgeScope.PROBLEM_LOCAL && scopeQuestionId == null) {
                return@runIdempotent DomainResult.Err(
                    KgError.ProposalShapeInvalid("PROBLEM_LOCAL 边必须携带 scopeQuestionId"),
                )
            }
            if (scopeQuestionId != null && !store.questions.containsKey(scopeQuestionId)) {
                return@runIdempotent DomainResult.Err(KgError.NotFound("question", scopeQuestionId))
            }
            val err = applySpecs(parentNodeId, specs, scope, scopeQuestionId)
            if (err != null) return@runIdempotent DomainResult.Err(err)
            DomainResult.Ok(snapshotAfterAppend(parentNodeId, scopeQuestionId))
        }
    }

    /** 删边(软删的内存等价: 直接移除 + outbox 记录) */
    suspend fun detachEdge(commandId: String, edgeId: String): DomainResult<Unit> = mutex.withLock {
        val fp = Fingerprint.fingerprintOf("detachEdge\n$edgeId")
        runIdempotent(commandId, fp) {
            if (store.edges.remove(edgeId) == null) {
                DomainResult.Err(KgError.NotFound("edge", edgeId))
            } else {
                store.outbox.add("EDGE_DETACHED:$edgeId")
                DomainResult.Ok(Unit)
            }
        }
    }

    /**
     * 记录一次验证判定: 联动 [MasteryEngine] 更新掌握度投影, 登记 quizItemId→nodeId 绑定(供 diagnoseGap),
     * 并给出 suggestedNext = 该节点直接前置中得分最低的至多 3 个(并列按 nodeId 升序).
     */
    suspend fun recordVerification(
        commandId: String,
        nodeId: String,
        quizItemId: String,
        verdict: Verdict,
    ): DomainResult<MasteryUpdate> = mutex.withLock {
        val fp = Fingerprint.fingerprintOf("recordVerification\n$nodeId\n$quizItemId\n${verdict.name}")
        runIdempotent(commandId, fp) {
            if (!store.nodes.containsKey(nodeId)) {
                return@runIdempotent DomainResult.Err(KgError.NotFound("node", nodeId))
            }
            val now = clock.now()
            // 写入本次事件前, 从 kg_mastery_event 取该节点最近历史判定(时间升序),
            // 供规则引擎判定"近 3 次(含本次)无 FAIL"(RFC §3.5)
            val recentVerdicts = store.masteryEvents
                .filter { it.nodeId == nodeId }
                .takeLast(MasteryEngine.RECENT_WINDOW - 1)
                .map { it.verdict }
            val updated = MasteryEngine.applyVerdict(nodeId, store.masteries[nodeId], recentVerdicts, verdict, now)
            store.masteries[nodeId] = updated
            store.masteryEvents.add(
                MasteryEvent(idGenerator.nextId("evt"), nodeId, quizItemId, verdict, commandId, now),
            )
            store.quizIndex[quizItemId] = nodeId
            store.outbox.add("MASTERY_RECORDED:$nodeId:${verdict.name}")
            DomainResult.Ok(MasteryUpdate(nodeId, updated, suggestedNextLocked(nodeId)))
        }
    }

    // ------------------------------------------------------------------
    // AI 建议(提案不落图, 确认后生效)
    // ------------------------------------------------------------------

    /** 生成前置链提案(带 TTL, PENDING); 提案内容来自注入的 AI 端口, 确认前不影响图 */
    suspend fun proposePrerequisiteChain(
        nodeId: String,
        questionText: String,
        maxDepth: Int = 3,
        maxBreadth: Int = 5,
    ): DomainResult<PrerequisiteProposal> = mutex.withLock {
        if (!store.nodes.containsKey(nodeId)) {
            return@withLock DomainResult.Err(KgError.NotFound("node", nodeId))
        }
        val tree = prerequisiteProposer.propose(nodeId, questionText, maxDepth, maxBreadth)
        val proposalId = idGenerator.nextId("proposal")
        val expiresAt = clock.now() + PROPOSAL_TTL_MS
        val reused = collectExistingNodeIds(tree)
        val payload = MiniJson.write(
            linkedMapOf("targetNodeId" to nodeId, "tree" to specToMap(tree)),
        )
        store.proposals[proposalId] =
            ProposalRecord(proposalId, ProposalKind.PREREQUISITE_CHAIN, payload, ProposalStatus.PENDING, expiresAt, clock.now())
        DomainResult.Ok(PrerequisiteProposal(proposalId, tree, reused, expiresAt))
    }

    /** 生成 JD 拆解提案(带 TTL, PENDING); 确认时批量建题 + 种子前置 */
    suspend fun proposeJdDecomposition(
        jdText: String,
        categories: Int = 4,
        questionsPerCategory: Int = 30,
    ): DomainResult<JdProposal> = mutex.withLock {
        if (jdText.isBlank()) {
            return@withLock DomainResult.Err(KgError.ProposalShapeInvalid("jdText 不能为空"))
        }
        val batchId = idGenerator.nextId("jdb")
        val questions = jdProposer.propose(jdText, categories, questionsPerCategory)
        val proposalId = idGenerator.nextId("proposal")
        val expiresAt = clock.now() + PROPOSAL_TTL_MS
        val payload = MiniJson.write(
            linkedMapOf(
                "batchId" to batchId,
                "questions" to questions.map { q ->
                    linkedMapOf(
                        "category" to q.category.name,
                        "seq" to q.seq,
                        "title" to q.title,
                        "seedPrerequisites" to q.seedPrerequisites.map { specToMap(it) },
                    )
                },
            ),
        )
        store.proposals[proposalId] =
            ProposalRecord(proposalId, ProposalKind.JD_DECOMPOSITION, payload, ProposalStatus.PENDING, expiresAt, clock.now())
        DomainResult.Ok(JdProposal(proposalId, batchId, questions, expiresAt))
    }

    /**
     * 确认提案(幂等): 支持 [editedPayloadJson] 覆盖原负载(用户编辑后确认).
     * 过期 → 标记 EXPIRED 并返回 [KgError.ProposalExpired]; 负载非法 → [KgError.ProposalShapeInvalid].
     */
    suspend fun confirmProposal(
        commandId: String,
        proposalId: String,
        editedPayloadJson: String? = null,
    ): DomainResult<ConfirmResult> = mutex.withLock {
        val fp = Fingerprint.fingerprintOf(
            "confirmProposal\n$proposalId\n${editedPayloadJson?.trim().orEmpty()}",
        )
        runIdempotent(commandId, fp) {
            val rec = store.proposals[proposalId]
                ?: return@runIdempotent DomainResult.Err(KgError.NotFound("proposal", proposalId))
            if (rec.status == ProposalStatus.EXPIRED || clock.now() > rec.expiresAt) {
                store.proposals[proposalId] = rec.copy(status = ProposalStatus.EXPIRED)
                return@runIdempotent DomainResult.Err(KgError.ProposalExpired(proposalId))
            }
            if (rec.status != ProposalStatus.PENDING) {
                return@runIdempotent DomainResult.Err(KgError.Storage("提案状态非 PENDING: ${rec.status}"))
            }
            val payloadText = editedPayloadJson?.takeIf { it.isNotBlank() } ?: rec.payloadJson
            val payload = try {
                MiniJson.parse(payloadText)
            } catch (e: IllegalArgumentException) {
                return@runIdempotent DomainResult.Err(
                    KgError.ProposalShapeInvalid("payload JSON 解析失败: ${e.message}"),
                )
            }
            val createdQuestions = ArrayList<String>()
            val createdNodes = ArrayList<String>()
            val reusedNodes = ArrayList<String>()
            val err = when (rec.kind) {
                ProposalKind.PREREQUISITE_CHAIN -> confirmChain(payload, createdNodes, reusedNodes)
                ProposalKind.JD_DECOMPOSITION -> confirmJd(payload, createdQuestions, createdNodes, reusedNodes)
            }
            if (err != null) return@runIdempotent DomainResult.Err(err)
            store.proposals[proposalId] = rec.copy(status = ProposalStatus.CONFIRMED)
            DomainResult.Ok(ConfirmResult(createdQuestions, createdNodes, reusedNodes))
        }
    }

    // ------------------------------------------------------------------
    // 读(UI 直渲)
    // ------------------------------------------------------------------

    /** 问题树视图: root 可达闭包, 边集 = GLOBAL ∪ PROBLEM_LOCAL(该问题) —— ProblemLocal 隔离的实现点 */
    suspend fun treeOf(questionId: String): DomainResult<TreeSnapshot> = mutex.withLock {
        if (!store.questions.containsKey(questionId)) {
            DomainResult.Err(KgError.NotFound("question", questionId))
        } else {
            DomainResult.Ok(treeSnapshotLocked(questionId))
        }
    }

    /**
     * reroot(纯查询, 零写入): 以任意节点为根物化子树, 复用与 treeOf 相同的 [GraphEngine.buildTreeSnapshot].
     * 边集 = 全量(GLOBAL ∪ 全部 LOCAL), 用于自由探索; [maxDepth] 截断过深层级(保留 depth < maxDepth 的节点).
     */
    suspend fun subtreeOf(nodeId: String, maxDepth: Int = 32): DomainResult<TreeSnapshot> = mutex.withLock {
        if (!store.nodes.containsKey(nodeId)) {
            DomainResult.Err(KgError.NotFound("node", nodeId))
        } else {
            val snap = GraphEngine.buildTreeSnapshot(
                nodeId, store.nodes, store.edges.values.toList(), store.masteries, null,
            )
            DomainResult.Ok(pruneDepth(snap, maxDepth.coerceAtLeast(1)))
        }
    }

    /** 漏洞诊断(规则引擎, 离线): quizItemId → 失败节点 → 直接前置中 (1-score)*(1+blocksCount) 最高者 */
    suspend fun diagnoseGap(quizItemId: String): DomainResult<GapDiagnosis> = mutex.withLock {
        val nodeId = store.quizIndex[quizItemId]
            ?: return@withLock DomainResult.Err(KgError.NotFound("quizItem", quizItemId))
        val allEdges = store.edges.values.toList()
        val blocks = GraphEngine.blocksCount(allEdges)
        val candidates = allEdges
            .filter { it.parentNodeId == nodeId }
            .map { it.childNodeId }
            .distinct()
            .map { childId ->
                MasteryEngine.GapCandidate(
                    nodeId = childId,
                    title = store.nodes[childId]?.title ?: childId,
                    mastery = store.masteries[childId] ?: NodeMastery(nodeId = childId),
                    blocksCount = blocks[childId] ?: 0,
                    recentFailures = recentFailuresLocked(childId),
                )
            }
        DomainResult.Ok(MasteryEngine.diagnoseGap(quizItemId, nodeId, candidates))
    }

    /** 瓶颈列表(RFC §3.5): 被 >= [minDistinctQuestions] 棵不同问题的弱节点依赖的祖先, 降序截取 [limit] */
    suspend fun bottlenecks(
        minDistinctQuestions: Int = 2,
        limit: Int = 20,
    ): DomainResult<List<BottleneckNode>> = mutex.withLock {
        DomainResult.Ok(bottlenecksLocked(minDistinctQuestions, limit))
    }

    /** JD 批次专家进度: 批次内全部问题树的节点并集上按掌握度状态计数 */
    suspend fun expertiseProgress(jdBatchId: String): DomainResult<ExpertiseProgress> = mutex.withLock {
        val questions = store.questions.values.filter { it.jdBatchId == jdBatchId }
        if (questions.isEmpty()) {
            DomainResult.Err(KgError.NotFound("jdBatch", jdBatchId))
        } else {
            DomainResult.Ok(progressLocked(questions))
        }
    }

    /**
     * 3D 导出(RFC §3.4): schemaVersion=1, 节点按 (layer, id)、links 按 (source, target, scope) 稳定排序,
     * contentHash = SHA-256(节点+边集合的规范串), 相同图必得相同 hash(与导出时间无关).
     */
    suspend fun export3d(root: String, rootIsQuestion: Boolean): DomainResult<GraphExport> = mutex.withLock {
        val snap: TreeSnapshot = if (rootIsQuestion) {
            if (!store.questions.containsKey(root)) {
                return@withLock DomainResult.Err(KgError.NotFound("question", root))
            }
            treeSnapshotLocked(root)
        } else {
            if (!store.nodes.containsKey(root)) {
                return@withLock DomainResult.Err(KgError.NotFound("node", root))
            }
            GraphEngine.buildTreeSnapshot(root, store.nodes, store.edges.values.toList(), store.masteries, null)
        }
        DomainResult.Ok(exportLocked(snap))
    }

    /** 瓶颈观察流: 每次成功写命令后重算(内存版 observeBottlenecks) */
    fun observeBottlenecks(limit: Int = 5): Flow<List<BottleneckNode>> =
        revisionFlow.map { mutex.withLock { bottlenecksLocked(DEFAULT_MIN_DISTINCT_QUESTIONS, limit) } }

    // ------------------------------------------------------------------
    // 内部: 幂等骨架
    // ------------------------------------------------------------------

    /**
     * 幂等命令骨架: 已处理命令同指纹 → 重放首次结果; 异指纹 → [KgError.CommandConflict];
     * 新命令执行 [op], 仅成功(Ok)时登记并推进版本号(失败命令可安全重试).
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> runIdempotent(
        commandId: String,
        fingerprint: String,
        op: () -> DomainResult<T>,
    ): DomainResult<T> {
        val seen = store.commands[commandId]
        if (seen != null) {
            return when (Fingerprint.matches(commandId, fingerprint, seen.fingerprint)) {
                Fingerprint.MatchResult.REPLAY -> seen.result as DomainResult<T>
                else -> DomainResult.Err(KgError.CommandConflict(commandId))
            }
        }
        val result = op()
        if (result is DomainResult.Ok) {
            store.commands[commandId] = ProcessedCommand(commandId, fingerprint, result)
            revisionFlow.value += 1
        }
        return result
    }

    // ------------------------------------------------------------------
    // 内部: 前置子树展开(计划 → 环检测 → 落库, 原子)
    // ------------------------------------------------------------------

    private fun applySpecs(
        parentNodeId: String,
        specs: List<PrerequisiteSpec>,
        scope: EdgeScope,
        scopeQuestionId: String?,
        created: MutableList<String>? = null,
        reused: MutableList<String>? = null,
    ): KgError? {
        // 阶段 1: 展开计划(不落地)
        val plannedNodes = ArrayList<KgNode>()
        val plannedEdges = ArrayList<Pair<String, String>>() // parent -> child
        fun walk(parent: String, children: List<PrerequisiteSpec>): KgError? {
            for (spec in children) {
                val existing = spec.existingNodeId
                val nodeId: String
                if (existing != null) {
                    if (!store.nodes.containsKey(existing)) return KgError.NotFound("node", existing)
                    nodeId = existing
                    reused?.add(existing)
                } else {
                    nodeId = idGenerator.nextId("node")
                    plannedNodes.add(KgNode(nodeId, spec.title.trim(), spec.markdownBody.trim()))
                    created?.add(nodeId)
                }
                plannedEdges.add(parent to nodeId)
                walk(nodeId, spec.children)?.let { return it }
            }
            return null
        }
        walk(parentNodeId, specs)?.let { return it }

        // 阶段 2: 逐边环检测(ADR-1: GLOBAL 在 GLOBAL ∪ 全部 LOCAL 上查; LOCAL(q) 在 GLOBAL ∪ LOCAL(q) 上查)
        val base: List<KgEdge> = when (scope) {
            EdgeScope.GLOBAL -> store.edges.values.toList()
            EdgeScope.PROBLEM_LOCAL -> store.edges.values.filter {
                it.scope == EdgeScope.GLOBAL || it.scopeQuestionId == scopeQuestionId
            }
        }
        val working = base.toMutableList()
        var plannedSeq = 0
        for ((p, c) in plannedEdges) {
            GraphEngine.wouldCreateCycle(working, p, c)?.let { return KgError.CycleDetected(it) }
            working.add(KgEdge("planned-${plannedSeq++}", p, c, scope, scopeQuestionId))
        }

        // 阶段 3: 落库 + outbox(同事务语义)
        for (n in plannedNodes) store.nodes[n.nodeId] = n
        for ((p, c) in plannedEdges) {
            val edgeId = idGenerator.nextId("edge")
            store.edges[edgeId] = KgEdge(edgeId, p, c, scope, scopeQuestionId)
            store.outbox.add("EDGE_CREATED:$edgeId:$p->$c:$scope")
        }
        return null
    }

    // ------------------------------------------------------------------
    // 内部: 提案确认执行
    // ------------------------------------------------------------------

    private fun confirmChain(
        payload: Any?,
        created: MutableList<String>,
        reused: MutableList<String>,
    ): KgError? {
        val map = payload as? Map<*, *> ?: return KgError.ProposalShapeInvalid("payload 应为 JSON 对象")
        val target = map["targetNodeId"] as? String
            ?: return KgError.ProposalShapeInvalid("缺少 targetNodeId")
        if (!store.nodes.containsKey(target)) return KgError.NotFound("node", target)
        val tree = try {
            specFromJson(map["tree"])
        } catch (e: IllegalArgumentException) {
            return KgError.ProposalShapeInvalid(e.message ?: "tree 非法")
        }
        return applySpecs(target, listOf(tree), EdgeScope.GLOBAL, null, created, reused)
    }

    private fun confirmJd(
        payload: Any?,
        createdQuestions: MutableList<String>,
        createdNodes: MutableList<String>,
        reusedNodes: MutableList<String>,
    ): KgError? {
        val map = payload as? Map<*, *> ?: return KgError.ProposalShapeInvalid("payload 应为 JSON 对象")
        val batchId = map["batchId"] as? String ?: return KgError.ProposalShapeInvalid("缺少 batchId")
        val questions = map["questions"] as? List<*> ?: return KgError.ProposalShapeInvalid("缺少 questions 数组")
        val batchArea = JD_AREA_PREFIX + batchId // 借用 areaId 保证 (area, problemNo) 跨批次唯一
        for ((idx, raw) in questions.withIndex()) {
            val qm = raw as? Map<*, *> ?: return KgError.ProposalShapeInvalid("questions[$idx] 应为对象")
            val title = (qm["title"] as? String)?.takeIf { it.isNotBlank() }
                ?: return KgError.ProposalShapeInvalid("questions[$idx].title 缺失")
            val category = try {
                KgCategory.valueOf((qm["category"] as? String) ?: KgCategory.CS_BASIC.name)
            } catch (e: IllegalArgumentException) {
                return KgError.ProposalShapeInvalid("questions[$idx].category 非法")
            }
            val seeds = (qm["seedPrerequisites"] as? List<*> ?: emptyList<Any?>()).map {
                try {
                    specFromJson(it)
                } catch (e: IllegalArgumentException) {
                    return KgError.ProposalShapeInvalid("questions[$idx].seedPrerequisites 非法: ${e.message}")
                }
            }
            val nodeId = idGenerator.nextId("node")
            val questionId = idGenerator.nextId("question")
            val problemNo = nextProblemNo(batchArea)
            store.nodes[nodeId] = KgNode(nodeId, title.trim())
            store.questions[questionId] = QuestionRecord(
                questionId, nodeId, batchArea, problemNo, title.trim(), category, batchId, clock.now(),
            )
            store.outbox.add("QUESTION_CREATED:$questionId:$nodeId")
            createdQuestions.add(questionId)
            createdNodes.add(nodeId)
            if (seeds.isNotEmpty()) {
                applySpecs(nodeId, seeds, EdgeScope.GLOBAL, null, createdNodes, reusedNodes)?.let { return it }
            }
        }
        return null
    }

    // ------------------------------------------------------------------
    // 内部: 读路径辅助
    // ------------------------------------------------------------------

    /** 问题树快照(边集 GLOBAL ∪ LOCAL(该问题)) */
    private fun treeSnapshotLocked(questionId: String): TreeSnapshot {
        val q = store.questions.getValue(questionId)
        val edges = store.edges.values.filter {
            it.scope == EdgeScope.GLOBAL || it.scopeQuestionId == questionId
        }
        return GraphEngine.buildTreeSnapshot(q.rootNodeId, store.nodes, edges, store.masteries, questionId)
    }

    /** appendPrerequisites 的返回快照: 优先归属问题树, 否则 reroot 子树 */
    private fun snapshotAfterAppend(parentNodeId: String, scopeQuestionId: String?): TreeSnapshot {
        val questionId = scopeQuestionId
            ?: store.questions.values.firstOrNull { it.rootNodeId == parentNodeId }?.questionId
        return if (questionId != null) {
            treeSnapshotLocked(questionId)
        } else {
            GraphEngine.buildTreeSnapshot(parentNodeId, store.nodes, store.edges.values.toList(), store.masteries, null)
        }
    }

    /** maxDepth 截断: 仅保留 depth < maxDepth 的节点及其内部边(shareNodeIds/parentCount 按截断前视图保留) */
    private fun pruneDepth(snap: TreeSnapshot, maxDepth: Int): TreeSnapshot {
        val keep = snap.nodes.filter { it.depth < maxDepth }.mapTo(HashSet()) { it.nodeId }
        if (keep.size == snap.nodes.size) return snap
        return snap.copy(
            nodes = snap.nodes.filter { it.nodeId in keep },
            edges = snap.edges.filter { it.parent in keep && it.child in keep },
            layers = snap.layers.take(maxDepth).map { layer -> layer.filter { it in keep } }
                .filter { it.isNotEmpty() },
            sharedNodeIds = snap.sharedNodeIds.intersect(keep),
        )
    }

    /** 直接前置中得分最低 3 个(suggestedNext); depth 记 1(直接前置), parentCount 基于全量边 */
    private fun suggestedNextLocked(nodeId: String): List<KGNodeDto> {
        val allEdges = store.edges.values.toList()
        return allEdges
            .filter { it.parentNodeId == nodeId }
            .map { it.childNodeId }
            .distinct()
            .map { childId -> childId to (store.masteries[childId] ?: NodeMastery(nodeId = childId)) }
            .sortedWith(compareBy<Pair<String, NodeMastery>> { it.second.score }.thenBy { it.first })
            .take(3)
            .map { (childId, mastery) ->
                KGNodeDto(
                    nodeId = childId,
                    title = store.nodes[childId]?.title ?: childId,
                    depth = 1,
                    parentCount = allEdges.count { it.childNodeId == childId },
                    mastery = mastery,
                )
            }
    }

    /** 近 [window] 次验证中的 FAIL 次数 */
    private fun recentFailuresLocked(nodeId: String, window: Int = 3): Int =
        store.masteryEvents
            .filter { it.nodeId == nodeId }
            .takeLast(window)
            .count { it.verdict == Verdict.FAIL }

    private fun bottlenecksLocked(minDistinctQuestions: Int, limit: Int): List<BottleneckNode> {
        val recent = store.masteries.keys.associateWith { recentFailuresLocked(it) }
        return GraphEngine.bottlenecks(
            nodes = store.nodes,
            masteries = store.masteries,
            recentFailures = recent,
            edges = store.edges.values.toList(),
            questionRoots = store.questions.entries.associate { it.key to it.value.rootNodeId },
            minDistinctQuestions = minDistinctQuestions,
            limit = limit,
        )
    }

    private fun progressLocked(questions: List<QuestionRecord>): ExpertiseProgress {
        val perCategory = LinkedHashMap<KgCategory, CategoryProgress>()
        val allNodeIds = LinkedHashSet<String>()
        for (category in KgCategory.entries) {
            val inCategory = questions.filter { it.category == category }
            if (inCategory.isEmpty()) continue
            val nodeIds = inCategory
                .flatMap { q -> treeSnapshotLocked(q.questionId).nodes.map { it.nodeId } }
                .toSet()
            allNodeIds.addAll(nodeIds)
            val mastered = nodeIds.count { store.masteries[it]?.state == MasteryState.MASTERED }
            perCategory[category] = CategoryProgress(
                total = nodeIds.size,
                mastered = mastered,
                progress = if (nodeIds.isEmpty()) 0.0 else mastered.toDouble() / nodeIds.size,
            )
        }
        var mastered = 0
        var fragile = 0
        var learning = 0
        var unknown = 0
        for (id in allNodeIds) {
            when (store.masteries[id]?.state ?: MasteryState.UNKNOWN) {
                MasteryState.MASTERED -> mastered++
                MasteryState.FRAGILE -> fragile++
                MasteryState.LEARNING -> learning++
                MasteryState.UNKNOWN -> unknown++
            }
        }
        val total = allNodeIds.size
        return ExpertiseProgress(
            total = total,
            mastered = mastered,
            fragile = fragile,
            learning = learning,
            unknown = unknown,
            progress = if (total == 0) 0.0 else mastered.toDouble() / total,
            perCategory = perCategory,
        )
    }

    // ------------------------------------------------------------------
    // 内部: 导出(RFC §3.4 契约)
    // ------------------------------------------------------------------

    private data class ExportNode(
        val id: String,
        val title: String,
        val layer: Int,
        val mastery: String,
        val score: Double,
        val parentCount: Int,
        val sharedByQuestions: Int,
        val isRoot: Boolean,
    )

    private data class ExportLink(val source: String, val target: String, val scope: String)

    private fun exportLocked(snap: TreeSnapshot): GraphExport {
        val sharedBy = sharedByQuestionsLocked()
        val nodes = snap.nodes.map { n ->
            ExportNode(
                id = n.nodeId,
                title = n.title,
                layer = n.depth,
                mastery = n.mastery.state.name,
                score = n.mastery.score,
                parentCount = n.parentCount,
                sharedByQuestions = sharedBy[n.nodeId] ?: 0,
                isRoot = n.nodeId == snap.rootNodeId,
            )
        }.sortedWith(compareBy({ it.layer }, { it.id })) // 契约: 按 (layer, id) 稳定排序
        // 契约: links 按 (source, target, scope) 升序稳定排序(与后端一致)
        val links = snap.edges
            .map { ExportLink(it.parent, it.child, it.scope.name) }
            .sortedWith(compareBy({ it.source }, { it.target }, { it.scope }))

        val contentHash = Fingerprint.sha256Hex(buildString {
            append("GraphExport:v1")
            for (n in nodes.sortedBy { it.id }) {
                append("\nN|").append(n.id).append('|').append(n.title).append('|')
                    .append(n.layer).append('|').append(n.mastery).append('|')
                    .append(formatDouble(n.score)).append('|').append(n.parentCount).append('|')
                    .append(n.sharedByQuestions).append('|').append(n.isRoot)
            }
            for (l in links) { // links 已按 (source, target, scope) 排序
                append("\nL|").append(l.source).append('>').append(l.target).append(':').append(l.scope)
            }
        })

        val payload = buildString {
            append("{\"schemaVersion\":1,\"contentHash\":").append(MiniJson.quote(contentHash))
            append(",\"generatedAt\":").append(clock.now())
            append(",\"nodes\":[")
            nodes.forEachIndexed { i, n ->
                if (i > 0) append(',')
                append("{\"id\":").append(MiniJson.quote(n.id))
                append(",\"title\":").append(MiniJson.quote(n.title))
                append(",\"layer\":").append(n.layer)
                append(",\"mastery\":").append(MiniJson.quote(n.mastery))
                append(",\"score\":").append(formatDouble(n.score))
                append(",\"parentCount\":").append(n.parentCount)
                append(",\"sharedByQuestions\":").append(n.sharedByQuestions)
                append(",\"isRoot\":").append(n.isRoot)
                append('}')
            }
            append("],\"links\":[")
            links.forEachIndexed { i, l ->
                if (i > 0) append(',')
                append("{\"source\":").append(MiniJson.quote(l.source))
                append(",\"target\":").append(MiniJson.quote(l.target))
                append(",\"scope\":").append(MiniJson.quote(l.scope))
                append('}')
            }
            append("]}")
        }
        return GraphExport(payload, contentHash, nodes.size, links.size)
    }

    /** 每个节点被多少棵( ACTIVE 问题树)覆盖 */
    private fun sharedByQuestionsLocked(): Map<String, Int> {
        val result = HashMap<String, Int>()
        for ((questionId, q) in store.questions) {
            val edges = store.edges.values.filter {
                it.scope == EdgeScope.GLOBAL || it.scopeQuestionId == questionId
            }
            for (nodeId in GraphEngine.reachableFrom(q.rootNodeId, edges)) {
                result.merge(nodeId, 1, Int::plus)
            }
        }
        return result
    }

    // ------------------------------------------------------------------
    // 内部: spec JSON 与杂项
    // ------------------------------------------------------------------

    private fun specToMap(spec: PrerequisiteSpec): Map<String, Any?> = linkedMapOf(
        "title" to spec.title,
        "existingNodeId" to spec.existingNodeId,
        "markdownBody" to spec.markdownBody,
        "children" to spec.children.map { specToMap(it) },
    )

    private fun specFromJson(value: Any?): PrerequisiteSpec {
        val map = value as? Map<*, *> ?: throw IllegalArgumentException("PrerequisiteSpec 应为 JSON 对象")
        val title = map["title"] as? String ?: throw IllegalArgumentException("title 缺失或非字符串")
        val existing = map["existingNodeId"] as? String
        val body = map["markdownBody"] as? String ?: ""
        val children = (map["children"] as? List<*>)?.map { specFromJson(it) } ?: emptyList()
        return PrerequisiteSpec(title = title, existingNodeId = existing, markdownBody = body, children = children)
    }

    private fun collectExistingNodeIds(spec: PrerequisiteSpec): List<String> {
        val acc = LinkedHashSet<String>()
        fun walk(s: PrerequisiteSpec) {
            s.existingNodeId?.let { acc.add(it) }
            s.children.forEach(::walk)
        }
        walk(spec)
        return acc.toList()
    }

    /** (areaId) 分组内下一个题目序号(max+1, 从 1 起) */
    private fun nextProblemNo(areaId: String?): Int =
        store.questions.values
            .filter { (it.areaId ?: "") == (areaId ?: "") }
            .maxOfOrNull { it.problemNo }
            ?.plus(1) ?: 1

    private fun formatDouble(v: Double): String =
        if (v % 1.0 == 0.0) "${v.toLong()}.0" else v.toString()

    companion object {
        /** 提案 TTL(15 分钟) */
        const val PROPOSAL_TTL_MS = 15L * 60L * 1000L

        /** 瓶颈默认入选门槛(棵数) */
        const val DEFAULT_MIN_DISTINCT_QUESTIONS = 2

        /** JD 批次题目借用的 areaId 前缀 */
        const val JD_AREA_PREFIX = "jd:"
    }
}
