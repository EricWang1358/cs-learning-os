package com.cslearningos.graph.data

import androidx.room.RoomDatabase
import androidx.room.withTransaction

/**
 * 跨表事务组合操作(纯 kg 表内)。
 *
 * 实现说明: 原计划用 @Dao 抽象类 + 构造注入兄弟 DAO 表达 @Transaction 组合, 但
 * Room 2.6.1 的 DAO 构造规则只允许"无参构造 或 仅一个具体 Database 类型参数"
 * (本模块不持有宿主 @Database 类, 无法以具体类型注入), 因此改为普通类 +
 * [RoomDatabase.withTransaction] —— 与 Room 生成的 @Transaction 方法语义完全等价
 * (同一挂起事务, 支持嵌套并入外层事务)。
 *
 * 纪律: 这里只做"kg 表之间"的原子组合, 不涉及 processed_commands / replication_outbox
 * —— 那两步由 [RoomKgStore] 的事务管线在外层 withTransaction 中追加(内层自动并入)。
 */
class GraphStoreDao(
    private val db: RoomDatabase,
    private val questionDao: KgQuestionDao,
    private val edgeDao: KgEdgeDao,
    private val proposalDao: KgProposalDao,
    private val masteryDao: KgMasteryDao,
) {

    /**
     * 记录一次验证(掌握度投影 + 事件追加)的原子组合:
     * mastery 新值由调用方(规则引擎, RFC §3.5)在同一事务内 read-modify-write 算出,
     * 与 event 追加要么全部生效要么全部回滚。
     */
    suspend fun recordVerificationAtomic(mastery: KgMasteryEntity, event: KgMasteryEventEntity) =
        db.withTransaction {
            masteryDao.upsert(mastery)
            masteryDao.insertEvent(event)
        }

    /**
     * 提案确认的原子组合(状态机 CAS + 编辑落库 + 结果落库):
     * 1. editedPayloadJson 非空 → 仅 PENDING 可编辑;
     * 2. PENDING → CONFIRMED CAS(回填确认 commandId), 失败返回 false(已终态, 交给
     *    [RoomKgStore] 区分幂等重放/过期/重复确认);
     * 3. 确认结果(问题 + 边)与同事务落库 —— 任一唯一约束冲突则整体回滚,
     *    提案保持 PENDING, 不出现"确认了一半"的中间态。
     */
    suspend fun confirmProposalAtomic(
        proposalId: String,
        commandId: String,
        editedPayloadJson: String?,
        questions: List<KgQuestionEntity>,
        edges: List<KgEdgeEntity>,
    ): Boolean = db.withTransaction {
        if (editedPayloadJson != null) {
            proposalDao.editPayloadIfPending(proposalId, editedPayloadJson)
        }
        val cas = proposalDao.transitionFromPending(
            proposalId = proposalId,
            toStatus = KgContract.PROPOSAL_STATUS_CONFIRMED,
            commandId = commandId,
        )
        if (cas != 1) return@withTransaction false
        questions.forEach { questionDao.insert(it) }
        edgeDao.insertAll(edges)
        true
    }

    /**
     * 批量建边的前置查重 + 插入组合(appendPrerequisites / JD 导入共用):
     * 对每条边先按 idx_kg_edge_live 口径查重, 已存在的活跃边跳过(幂等重放容忍),
     * 只插入真正的新边; 返回实际插入的边。环检测在调用前的 domain 层已完成。
     */
    suspend fun insertNewEdgesSkippingLiveDuplicates(
        edges: List<KgEdgeEntity>,
    ): List<KgEdgeEntity> = db.withTransaction {
        val inserted = mutableListOf<KgEdgeEntity>()
        edges.forEach { edge ->
            val existing = edgeDao.findLiveEdge(
                parentNodeId = edge.parentNodeId,
                childNodeId = edge.childNodeId,
                scopeType = edge.scopeType,
                scopeQuestionId = edge.scopeQuestionId,
            )
            if (existing == null) {
                edgeDao.insert(edge)
                inserted += edge
            }
        }
        inserted
    }
}
