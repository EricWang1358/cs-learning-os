package com.cslearningos.graph.data

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

// ---------------------------------------------------------------------------
// 本地端口(由 app 组合根桥接到现有 data:content-room 的 DAO, 本模块不直接依赖 content)
// ---------------------------------------------------------------------------

/** processed_commands 行的最小投影(现有 v8 表, 语义与 SaveNodeCommand 一致) */
data class ProcessedCommandSnapshot(
    val commandId: String,
    val fingerprint: String,
)

/**
 * 复用现有 processed_commands 表的本地端口。
 * 实现方注意: 两个方法都必须在**调用方所在的事务**内执行(Room 挂起事务沿协程上下文
 * 传播, 直接用同一 RoomDatabase 的 DAO 即可自动并入), 以保证"kg 写入 + 命令记录 +
 * outbox"三者原子。
 */
interface ProcessedCommandPort {
    /** 按 commandId 查已处理命令; 不存在返回 null */
    suspend fun find(commandId: String): ProcessedCommandSnapshot?

    /** 记录已处理命令(commandId + fingerprint) */
    suspend fun record(commandId: String, fingerprint: String)
}

/**
 * 复用现有 replication_outbox 表的本地端口。
 * 实现方写入时 state 固定 'pending'(其余列如 event_id / created_at 由实现方按现有
 * OutboxAppender 纪律生成), 同步上传由既有 Phase 2A outbox worker 消费。
 */
interface OutboxPort {
    /**
     * @param aggregateType [RoomKgStore.AGGREGATE_QUESTION] / [RoomKgStore.AGGREGATE_EDGE] / [RoomKgStore.AGGREGATE_MASTERY]
     * @param operation [RoomKgStore.OP_UPSERT] / [RoomKgStore.OP_DELETE]
     * @param payloadJson 聚合快照 JSON(detach 时为删除前快照, 供对端幂等投影)
     */
    suspend fun append(aggregateType: String, aggregateId: String, operation: String, payloadJson: String)
}

// ---------------------------------------------------------------------------
// 存储层异常(application 层映射为 KgError: CommandConflict / NotFound / ProposalExpired / Storage)
// ---------------------------------------------------------------------------

/** 同 commandId 不同 fingerprint → KgError.CommandConflict */
class KgCommandConflictException(val commandId: String) :
    IllegalStateException("commandId=$commandId 已以不同指纹处理过, 拒绝重放")

/** 目标行不存在 → KgError.NotFound */
class KgNotFoundException(val kind: String, val id: String) :
    NoSuchElementException("$kind 不存在: $id")

/** 提案 TTL 已过(已顺手落库 EXPIRED) → KgError.ProposalExpired */
class KgProposalExpiredException(val proposalId: String) :
    IllegalStateException("proposal 已过期: $proposalId")

/** 提案已处终态(非幂等重放的重复确认/拒绝) → KgError.ProposalShapeInvalid 或 Storage */
class KgProposalNotPendingException(val proposalId: String, val currentStatus: String) :
    IllegalStateException("proposal=$proposalId 当前状态 $currentStatus, 仅 PENDING 可迁移")

/** 幂等写入结果: 本次实际执行 vs 同指纹重放跳过 */
enum class KgWriteResult { EXECUTED, REPLAYED }

/** 事务管线第三步: 待追加的 outbox 行 */
private data class OutboxSpec(
    val aggregateType: String,
    val aggregateId: String,
    val operation: String,
    val payloadJson: String,
)

// ---------------------------------------------------------------------------
// KgStore 持久化端口(进程内直依赖; InMemoryKgStore 与本实现双跑同一契约测试 —— RFC §4/§5)
// ---------------------------------------------------------------------------

/**
 * KnowledgeGraph 的持久化端口。写方法全部满足 RFC §3.1 不变量:
 * 幂等(commandId+fingerprint, processed_commands 语义) 且与 replication_outbox 同事务。
 *
 * DTO 为裸类型(String/Int/Long)镜像: application 层的 value class(QuestionId 等)与
 * 枚举(KgCategory/EdgeScope/MasteryState/Verdict)在 facade 边界收敛, 不渗入 DB 层。
 */
interface KgStore {
    // ---- 写(幂等; 同事务追加 replication_outbox) ----

    /** 登记问题根; problemNo 由调用方分配(见 [nextProblemNo], 应与写同事务) */
    suspend fun insertQuestion(commandId: String, fingerprint: String, question: KgQuestionDto): KgWriteResult

    /** 批量挂边(已按 idx_kg_edge_live 口径存在的活跃边跳过, 只插新边) */
    suspend fun appendEdges(commandId: String, fingerprint: String, edges: List<KgEdgeDto>): KgWriteResult

    /** 删边(软删: status → REJECTED 墓碑, outbox operation = DELETE) */
    suspend fun detachEdge(commandId: String, fingerprint: String, edgeId: String): KgWriteResult

    /** 记录一次验证: mastery 投影 upsert + 事件追加(原子), outbox 投影 mastery */
    suspend fun recordVerification(
        commandId: String,
        fingerprint: String,
        mastery: KgMasteryDto,
        event: KgMasteryEventDto,
    ): KgWriteResult

    /** AI 建议落库(PENDING + TTL); 非幂等命令(propose 无 commandId), 不写 outbox */
    suspend fun insertProposal(proposal: KgProposalDto)

    /**
     * 确认提案: 过期检查(顺手落库 EXPIRED) → 幂等判重 → CAS 状态机 → 编辑落库 →
     * 结果(问题 + 边)同事务落库 → processed_commands → outbox。
     */
    suspend fun confirmProposal(
        commandId: String,
        fingerprint: String,
        proposalId: String,
        now: Long,
        editedPayloadJson: String?,
        questions: List<KgQuestionDto>,
        edges: List<KgEdgeDto>,
    ): KgWriteResult

    /** 过期清理: PENDING 且 TTL 到期 → EXPIRED, 返回清理行数(写路径惰性触发或周期任务) */
    suspend fun expireOverdueProposals(now: Long): Int

    // ---- 读(domain 层图遍历/规则引擎的取数面) ----

    suspend fun questionById(questionId: String): KgQuestionDto?
    suspend fun nextProblemNo(areaId: String?): Int
    suspend fun questionsByJdBatch(jdBatchId: String): List<KgQuestionDto>
    suspend fun questionsByRootNodeId(rootNodeId: String): List<KgQuestionDto>
    suspend fun batchMasteryBreakdown(jdBatchId: String): List<CategoryMasteryCount>

    suspend fun edgeById(edgeId: String): KgEdgeDto?
    suspend fun activeEdgesFromParent(nodeId: String): List<KgEdgeDto>
    suspend fun activeEdgesToChild(nodeId: String): List<KgEdgeDto>
    suspend fun visibleEdgesForQuestion(questionId: String): List<KgEdgeDto>
    suspend fun allActiveEdges(): List<KgEdgeDto>
    suspend fun findLiveEdge(
        parentNodeId: String,
        childNodeId: String,
        scopeType: String,
        scopeQuestionId: String?,
    ): KgEdgeDto?

    suspend fun proposalById(proposalId: String): KgProposalDto?

    suspend fun masteryByNodeId(nodeId: String): KgMasteryDto?
    suspend fun masteriesByNodeIds(nodeIds: List<String>): List<KgMasteryDto>
    suspend fun recentVerdicts(nodeId: String, limit: Int): List<String>
    suspend fun countRecentFailures(nodeId: String, since: Long): Int
    suspend fun weakMasteries(): List<KgMasteryDto>

    /** observeBottlenecks 数据源: 组合根 combine 两个 Flow 后在 domain 层做反向可达统计 */
    fun observeAllActiveEdges(): Flow<List<KgEdgeDto>>
    fun observeWeakMasteries(): Flow<List<KgMasteryDto>>

    /**
     * 事务边界(与 data:content-room 同纪律): 块内所有 DAO/端口调用共享同一挂起事务;
     * 嵌套调用自动并入外层 —— 例如 domain 层可在块内先 [nextProblemNo] 再 [insertQuestion],
     * 保证序号分配与插入原子。
     */
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}

// ---------------------------------------------------------------------------
// Room 实现
// ---------------------------------------------------------------------------

/** 宿主 @Database(schema v9) 实现此接口向本模块暴露 kg DAO(4 个抽象方法皆为 Room 生成, 接口委托一行即可) */
interface KgDaoProvider {
    fun kgQuestionDao(): KgQuestionDao
    fun kgEdgeDao(): KgEdgeDao
    fun kgProposalDao(): KgProposalDao
    fun kgMasteryDao(): KgMasteryDao
}

/**
 * KgStore 的 Room 实现: 事务管线 = withTransaction { 幂等判重 → kg 写入 →
 * 记 processed_commands → 追加 replication_outbox(state='pending') }。
 * 与现有 data:content-room 的 SaveNodeCommand 管线同纪律, 仅聚合类型不同。
 */
class RoomKgStore(
    private val db: RoomDatabase,
    daos: KgDaoProvider,
    private val processedCommands: ProcessedCommandPort,
    private val outbox: OutboxPort,
    private val json: Json = Json { encodeDefaults = true },
) : KgStore {

    companion object {
        const val AGGREGATE_QUESTION = "KG_QUESTION"
        const val AGGREGATE_EDGE = "KG_EDGE"
        const val AGGREGATE_MASTERY = "KG_MASTERY"
        const val OP_UPSERT = "UPSERT"
        const val OP_DELETE = "DELETE"
    }

    private val questionDao: KgQuestionDao = daos.kgQuestionDao()
    private val edgeDao: KgEdgeDao = daos.kgEdgeDao()
    private val proposalDao: KgProposalDao = daos.kgProposalDao()
    private val masteryDao: KgMasteryDao = daos.kgMasteryDao()

    /** 跨表组合(普通类, 本模块内聚构造; 事务经 db.withTransaction 并入外层管线) */
    private val graphStoreDao: GraphStoreDao =
        GraphStoreDao(db, questionDao, edgeDao, proposalDao, masteryDao)

    // ---------------- 写 ----------------

    override suspend fun insertQuestion(
        commandId: String,
        fingerprint: String,
        question: KgQuestionDto,
    ): KgWriteResult = runIdempotentWrite(commandId, fingerprint) {
        questionDao.insert(question.toEntity())
        listOf(
            OutboxSpec(AGGREGATE_QUESTION, question.questionId, OP_UPSERT, json.encodeToString(KgQuestionDto.serializer(), question)),
        )
    }

    override suspend fun appendEdges(
        commandId: String,
        fingerprint: String,
        edges: List<KgEdgeDto>,
    ): KgWriteResult = runIdempotentWrite(commandId, fingerprint) {
        val inserted = graphStoreDao.insertNewEdgesSkippingLiveDuplicates(edges.map { it.toEntity() })
        inserted.map {
            OutboxSpec(AGGREGATE_EDGE, it.edgeId, OP_UPSERT, json.encodeToString(KgEdgeDto.serializer(), it.toDto()))
        }
    }

    override suspend fun detachEdge(
        commandId: String,
        fingerprint: String,
        edgeId: String,
    ): KgWriteResult = runIdempotentWrite(commandId, fingerprint) {
        val edge = edgeDao.findById(edgeId) ?: throw KgNotFoundException("kg_edge", edgeId)
        val rows = edgeDao.updateStatus(edgeId, KgContract.EDGE_STATUS_REJECTED)
        check(rows == 1) { "kg_edge 状态迁移失败: $edgeId" }
        listOf(
            // 删除前快照随行(outbox 可表达"删边", RFC §3.3 软删 + revision)
            OutboxSpec(AGGREGATE_EDGE, edgeId, OP_DELETE, json.encodeToString(KgEdgeDto.serializer(), edge.toDto())),
        )
    }

    override suspend fun recordVerification(
        commandId: String,
        fingerprint: String,
        mastery: KgMasteryDto,
        event: KgMasteryEventDto,
    ): KgWriteResult = runIdempotentWrite(commandId, fingerprint) {
        graphStoreDao.recordVerificationAtomic(mastery.toEntity(), event.toEntity())
        listOf(
            OutboxSpec(AGGREGATE_MASTERY, mastery.nodeId, OP_UPSERT, json.encodeToString(KgMasteryDto.serializer(), mastery)),
        )
    }

    override suspend fun insertProposal(proposal: KgProposalDto) {
        proposalDao.insert(proposal.toEntity())
    }

    override suspend fun confirmProposal(
        commandId: String,
        fingerprint: String,
        proposalId: String,
        now: Long,
        editedPayloadJson: String?,
        questions: List<KgQuestionDto>,
        edges: List<KgEdgeDto>,
    ): KgWriteResult {
        // 过期门禁前置(独立小事务落库 EXPIRED 后抛错, 保证错误确定性; 重放同错)。
        // 注意: 非 PENDING 的冲突判定必须放在写事务内、幂等判重之后 —— 同命令重放
        // (提案已 CONFIRMED)要先被 processed_commands 拦截返回 REPLAYED, 不能误抛。
        val proposal = proposalDao.findById(proposalId)
            ?: throw KgNotFoundException("kg_proposal", proposalId)
        if (proposal.status == KgContract.PROPOSAL_STATUS_PENDING && proposal.expiresAt < now) {
            runInTransaction {
                proposalDao.transitionFromPending(proposalId, KgContract.PROPOSAL_STATUS_EXPIRED)
            }
            throw KgProposalExpiredException(proposalId)
        }
        return runIdempotentWrite(commandId, fingerprint) {
            // 事务内重读(以 CAS 为准); 非幂等重放的重复确认/拒绝在此拒绝
            val current = proposalDao.findById(proposalId)
                ?: throw KgNotFoundException("kg_proposal", proposalId)
            if (current.status != KgContract.PROPOSAL_STATUS_PENDING) {
                throw KgProposalNotPendingException(proposalId, current.status)
            }
            val confirmed = graphStoreDao.confirmProposalAtomic(
                proposalId = proposalId,
                commandId = commandId,
                editedPayloadJson = editedPayloadJson,
                questions = questions.map { it.toEntity() },
                edges = edges.map { it.toEntity() },
            )
            check(confirmed) { "kg_proposal CAS 失败(并发迁移): $proposalId" }
            questions.map {
                OutboxSpec(AGGREGATE_QUESTION, it.questionId, OP_UPSERT, json.encodeToString(KgQuestionDto.serializer(), it))
            } + edges.map {
                OutboxSpec(AGGREGATE_EDGE, it.edgeId, OP_UPSERT, json.encodeToString(KgEdgeDto.serializer(), it))
            }
        }
    }

    override suspend fun expireOverdueProposals(now: Long): Int = proposalDao.expireOverdue(now)

    // ---------------- 读 ----------------

    override suspend fun questionById(questionId: String): KgQuestionDto? =
        questionDao.findById(questionId)?.toDto()

    override suspend fun nextProblemNo(areaId: String?): Int = questionDao.nextProblemNo(areaId)

    override suspend fun questionsByJdBatch(jdBatchId: String): List<KgQuestionDto> =
        questionDao.listByJdBatch(jdBatchId).map { it.toDto() }

    override suspend fun questionsByRootNodeId(rootNodeId: String): List<KgQuestionDto> =
        questionDao.findByRootNodeId(rootNodeId).map { it.toDto() }

    override suspend fun batchMasteryBreakdown(jdBatchId: String): List<CategoryMasteryCount> =
        questionDao.batchMasteryBreakdown(jdBatchId)

    override suspend fun edgeById(edgeId: String): KgEdgeDto? = edgeDao.findById(edgeId)?.toDto()

    override suspend fun activeEdgesFromParent(nodeId: String): List<KgEdgeDto> =
        edgeDao.activeEdgesFromParent(nodeId).map { it.toDto() }

    override suspend fun activeEdgesToChild(nodeId: String): List<KgEdgeDto> =
        edgeDao.activeEdgesToChild(nodeId).map { it.toDto() }

    override suspend fun visibleEdgesForQuestion(questionId: String): List<KgEdgeDto> =
        edgeDao.visibleEdgesForQuestion(questionId).map { it.toDto() }

    override suspend fun allActiveEdges(): List<KgEdgeDto> =
        edgeDao.allActiveEdges().map { it.toDto() }

    override suspend fun findLiveEdge(
        parentNodeId: String,
        childNodeId: String,
        scopeType: String,
        scopeQuestionId: String?,
    ): KgEdgeDto? = edgeDao.findLiveEdge(parentNodeId, childNodeId, scopeType, scopeQuestionId)?.toDto()

    override suspend fun proposalById(proposalId: String): KgProposalDto? =
        proposalDao.findById(proposalId)?.toDto()

    override suspend fun masteryByNodeId(nodeId: String): KgMasteryDto? =
        masteryDao.findByNodeId(nodeId)?.toDto()

    override suspend fun masteriesByNodeIds(nodeIds: List<String>): List<KgMasteryDto> =
        masteryDao.findByNodeIds(nodeIds).map { it.toDto() }

    override suspend fun recentVerdicts(nodeId: String, limit: Int): List<String> =
        masteryDao.recentVerdicts(nodeId, limit)

    override suspend fun countRecentFailures(nodeId: String, since: Long): Int =
        masteryDao.countRecentFailures(nodeId, since)

    override suspend fun weakMasteries(): List<KgMasteryDto> =
        masteryDao.weakMasteries().map { it.toDto() }

    override fun observeAllActiveEdges(): Flow<List<KgEdgeDto>> =
        edgeDao.observeAllActiveEdges().map { list -> list.map { it.toDto() } }

    override fun observeWeakMasteries(): Flow<List<KgMasteryDto>> =
        masteryDao.observeWeakMasteries().map { list -> list.map { it.toDto() } }

    override suspend fun <T> runInTransaction(block: suspend () -> T): T = db.withTransaction(block)

    // ---------------- 事务管线 ----------------

    /**
     * 幂等写管线(与 SaveNodeCommand 同纪律):
     * 1. processed_commands 判重: 同 commandId 同 fingerprint → REPLAYED(跳过全部写入);
     *    同 commandId 异 fingerprint → 抛 [KgCommandConflictException];
     * 2. [kgWrites] 执行 kg 表写入并返回待投影的 outbox 行;
     * 3. 记 processed_commands;
     * 4. 追加 replication_outbox(state='pending' 由端口实现方保证)。
     * 全部步骤在同一挂起事务内, 异常即整体回滚。
     */
    private suspend fun runIdempotentWrite(
        commandId: String,
        fingerprint: String,
        kgWrites: suspend () -> List<OutboxSpec>,
    ): KgWriteResult = runInTransaction {
        when (val existing = processedCommands.find(commandId)) {
            null -> {
                val outboxSpecs = kgWrites()
                processedCommands.record(commandId, fingerprint)
                outboxSpecs.forEach {
                    outbox.append(it.aggregateType, it.aggregateId, it.operation, it.payloadJson)
                }
                KgWriteResult.EXECUTED
            }
            else -> {
                if (existing.fingerprint != fingerprint) throw KgCommandConflictException(commandId)
                KgWriteResult.REPLAYED
            }
        }
    }
}
