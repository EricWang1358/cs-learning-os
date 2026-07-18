package com.cslearningos.graph.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * kg_* 五表的单表 DAO。纪律:
 * - 全部为 suspend / Flow(room-ktx), 不在 DAO 内开跨表事务(跨表组合见 [GraphStoreDao],
 *   幂等 + outbox 管线见 [RoomKgStore]);
 * - 图遍历(环检测、反向可达、分层)一律在 domain 层完成, DAO 只供数据;
 * - 状态字符串字面量与 [KgContract] / RFC §3.3 DDL 注释保持一致。
 */

/** expertiseProgress 聚合行(kg_question ⋈ kg_mastery) */
data class CategoryMasteryCount(
    val category: String,
    val masteryState: String,
    val cnt: Int,
)

@Dao
interface KgQuestionDao {

    /** 登记问题根; 违反 idx_kg_question_areano((area, problem_no) 活跃唯一)抛 SQLiteConstraintException */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(question: KgQuestionEntity)

    @Query("SELECT * FROM kg_question WHERE question_id = :questionId")
    suspend fun findById(questionId: String): KgQuestionEntity?

    /**
     * (area_id, problem_no) 下一个可用序号; area_id 为 NULL 时归入 '' 桶,
     * 口径与 idx_kg_question_areano 的 COALESCE(area_id,'') 严格一致。
     * 需在写入事务内调用(与 insert 同一 [RoomKgStore.runInTransaction]), 避免并发重号。
     */
    @Query(
        """
        SELECT COALESCE(MAX(problem_no), 0) + 1 FROM kg_question
        WHERE COALESCE(area_id, '') = COALESCE(:areaId, '') AND status != 'ARCHIVED'
        """,
    )
    suspend fun nextProblemNo(areaId: String?): Int

    /** JD 批次的问题清单(expertiseProgress / 断点续跑), 按序号稳定排序 */
    @Query("SELECT * FROM kg_question WHERE jd_batch_id = :jdBatchId AND status != 'ARCHIVED' ORDER BY problem_no")
    suspend fun listByJdBatch(jdBatchId: String): List<KgQuestionEntity>

    /** 反查: 某节点被哪些(未归档)问题登记为根(共享统计 sharedByQuestions 的数据源之一) */
    @Query("SELECT * FROM kg_question WHERE root_node_id = :rootNodeId AND status != 'ARCHIVED'")
    suspend fun findByRootNodeId(rootNodeId: String): List<KgQuestionEntity>

    /** 状态迁移(归档/恢复); revision 自增, outbox 以 UPSERT 投影。返回受影响行数 */
    @Query("UPDATE kg_question SET status = :status, revision = revision + 1 WHERE question_id = :questionId")
    suspend fun updateStatus(questionId: String, status: String): Int

    /**
     * expertiseProgress 数据源: 按 (category, mastery state) 聚合某 JD 批次
     * 问题根节点的掌握度(无投影的节点计入 UNKNOWN)。
     */
    @Query(
        """
        SELECT q.category AS category, COALESCE(m.state, 'UNKNOWN') AS masteryState, COUNT(*) AS cnt
        FROM kg_question q
        LEFT JOIN kg_mastery m ON m.node_id = q.root_node_id
        WHERE q.jd_batch_id = :jdBatchId AND q.status != 'ARCHIVED'
        GROUP BY q.category, COALESCE(m.state, 'UNKNOWN')
        """,
    )
    suspend fun batchMasteryBreakdown(jdBatchId: String): List<CategoryMasteryCount>
}

@Dao
interface KgEdgeDao {

    /** 插边; 违反 idx_kg_edge_live(活跃四元组唯一)抛 SQLiteConstraintException */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(edge: KgEdgeEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(edges: List<KgEdgeEntity>)

    @Query("SELECT * FROM kg_edge WHERE edge_id = :edgeId")
    suspend fun findById(edgeId: String): KgEdgeEntity?

    /** 活跃边·按 parent(走 idx_kg_edge_parent): nodeId 的前置依赖边(parent 依赖 child) */
    @Query("SELECT * FROM kg_edge WHERE parent_node_id = :nodeId AND status = 'ACTIVE'")
    suspend fun activeEdgesFromParent(nodeId: String): List<KgEdgeEntity>

    /** 活跃边·按 child(走 idx_kg_edge_child): 反向可达"谁依赖我", blocksCount/瓶颈统计的数据源 */
    @Query("SELECT * FROM kg_edge WHERE child_node_id = :nodeId AND status = 'ACTIVE'")
    suspend fun activeEdgesToChild(nodeId: String): List<KgEdgeEntity>

    /**
     * 问题树可见边(RFC §3.1 "树 = 视图"): GLOBAL ∪ PROBLEM_LOCAL(questionId)。
     * ProblemLocal 隔离由 scope_question_id 等值过滤保证(B 的私有分支不进 A 的视图)。
     */
    @Query(
        """
        SELECT * FROM kg_edge
        WHERE status = 'ACTIVE'
          AND (scope_type = 'GLOBAL'
               OR (scope_type = 'PROBLEM_LOCAL' AND scope_question_id = :questionId))
        """,
    )
    suspend fun visibleEdgesForQuestion(questionId: String): List<KgEdgeEntity>

    /**
     * 环检测辅助: 全量活跃边一次性取出(Global ∪ 全部 Local)。
     * ADR-1 保守规则要求在 Global ∪ 全部 Local 边集上查环; 遍历在 domain 层做。
     */
    @Query("SELECT * FROM kg_edge WHERE status = 'ACTIVE'")
    suspend fun allActiveEdges(): List<KgEdgeEntity>

    /**
     * 幂等/查重前置检查, 口径与 idx_kg_edge_live 严格一致(四元组 + 非 REJECTED)。
     * 命中说明活动边已存在 —— 重放场景直接复用, 冲突场景由 domain 层拒绝。
     */
    @Query(
        """
        SELECT * FROM kg_edge
        WHERE parent_node_id = :parentNodeId
          AND child_node_id = :childNodeId
          AND scope_type = :scopeType
          AND COALESCE(scope_question_id, '') = COALESCE(:scopeQuestionId, '')
          AND status != 'REJECTED'
        LIMIT 1
        """,
    )
    suspend fun findLiveEdge(
        parentNodeId: String,
        childNodeId: String,
        scopeType: String,
        scopeQuestionId: String?,
    ): KgEdgeEntity?

    /**
     * 状态迁移(detach = REJECTED 软删墓碑, outbox 以 DELETE 投影;
     * PENDING_CONFIRMATION → ACTIVE 为 AI 边确认生效); revision 自增。返回受影响行数。
     */
    @Query("UPDATE kg_edge SET status = :status, revision = revision + 1 WHERE edge_id = :edgeId")
    suspend fun updateStatus(edgeId: String, status: String): Int

    /**
     * observeBottlenecks 数据源之一: 观察 kg_edge 的全量活跃边。
     * 组合根用 combine(observeAllActiveEdges, KgMasteryDao.observeWeakMasteries)
     * 驱动瓶颈重算(失效重算由 Room InvalidationTracker 保证)。
     */
    @Query("SELECT * FROM kg_edge WHERE status = 'ACTIVE'")
    fun observeAllActiveEdges(): Flow<List<KgEdgeEntity>>
}

@Dao
interface KgProposalDao {

    /** AI 建议落库(PENDING + TTL); AI 产出永不直接生效(RFC §3.1) */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(proposal: KgProposalEntity)

    @Query("SELECT * FROM kg_proposal WHERE proposal_id = :proposalId")
    suspend fun findById(proposalId: String): KgProposalEntity?

    /**
     * 状态机 CAS: 仅 PENDING 可迁移到 CONFIRMED / REJECTED / EXPIRED。
     * commandId 非空时一并回填(确认命令留痕); 返回 0 表示已是终态,
     * 由调用方区分"幂等重放 / 过期 / 重复确认"。
     */
    @Query(
        """
        UPDATE kg_proposal
        SET status = :toStatus, command_id = COALESCE(:commandId, command_id)
        WHERE proposal_id = :proposalId AND status = 'PENDING'
        """,
    )
    suspend fun transitionFromPending(proposalId: String, toStatus: String, commandId: String? = null): Int

    /** 确认时允许编辑 payload(editedPayloadJson); 仅 PENDING 可编辑 */
    @Query("UPDATE kg_proposal SET payload_json = :payloadJson WHERE proposal_id = :proposalId AND status = 'PENDING'")
    suspend fun editPayloadIfPending(proposalId: String, payloadJson: String): Int

    /** 过期清理: PENDING 且 TTL 到期 → EXPIRED。由写路径惰性触发或 app 周期任务调用 */
    @Query("UPDATE kg_proposal SET status = 'EXPIRED' WHERE status = 'PENDING' AND expires_at < :now")
    suspend fun expireOverdue(now: Long): Int

    @Query("SELECT * FROM kg_proposal WHERE status = 'PENDING' ORDER BY created_at")
    suspend fun listPending(): List<KgProposalEntity>
}

@Dao
interface KgMasteryDao {

    /**
     * 掌握度投影 upsert(可重建投影; quiz 证据 kg_mastery_event 为事实源)。
     * 新值由调用方在同一事务内 read-modify-write(规则引擎, RFC §3.5)计算。
     */
    @Upsert
    suspend fun upsert(mastery: KgMasteryEntity)

    @Upsert
    suspend fun upsertAll(masteries: List<KgMasteryEntity>)

    /** 事件追加(事实源, 只增不改) */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: KgMasteryEventEntity)

    @Query("SELECT * FROM kg_mastery WHERE node_id = :nodeId")
    suspend fun findByNodeId(nodeId: String): KgMasteryEntity?

    @Query("SELECT * FROM kg_mastery WHERE node_id IN (:nodeIds)")
    suspend fun findByNodeIds(nodeIds: List<String>): List<KgMasteryEntity>

    /** 近 N 次判定(新→旧): 规则引擎"近 3 次无 FAIL → MASTERED"等判定的数据源 */
    @Query("SELECT verdict FROM kg_mastery_event WHERE node_id = :nodeId ORDER BY created_at DESC, event_id DESC LIMIT :limit")
    suspend fun recentVerdicts(nodeId: String, limit: Int): List<String>

    /** 近期失败计数(瓶颈 BottleneckNode.recentFailures 字段, since 由 domain 层按窗口给) */
    @Query("SELECT COUNT(*) FROM kg_mastery_event WHERE node_id = :nodeId AND verdict = 'FAIL' AND created_at >= :since")
    suspend fun countRecentFailures(nodeId: String, since: Long): Int

    /** 瓶颈规则引擎输入(RFC §3.5): 弱节点 = FRAGILE/LEARNING 且 score < 0.6, 一次性快照 */
    @Query("SELECT * FROM kg_mastery WHERE score < 0.6 AND state IN ('FRAGILE', 'LEARNING')")
    suspend fun weakMasteries(): List<KgMasteryEntity>

    /**
     * observeBottlenecks 数据源: 观察 kg_mastery 的弱节点集。
     * 与 [KgEdgeDao.observeAllActiveEdges] combine 后在 domain 层做反向可达统计。
     */
    @Query("SELECT * FROM kg_mastery WHERE score < 0.6 AND state IN ('FRAGILE', 'LEARNING')")
    fun observeWeakMasteries(): Flow<List<KgMasteryEntity>>
}
