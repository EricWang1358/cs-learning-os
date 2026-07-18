package com.cslearningos.graph.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * KnowledgeGraph 五张 kg_* 表(RFC §3.3, schema v9 纯增量)的 Room 实体。
 *
 * 列名/类型/可空性与 RFC §3.3 DDL 严格一致; Kotlin 构造默认值镜像 DDL 的 DEFAULT
 * (@ColumnInfo 不声明 defaultValue —— Room 运行时只校验"实体显式声明的"默认值,
 *  迁移 DDL 中的 DEFAULT 保留, 全新安装由 Room 生成的建表语句不携带 DEFAULT,
 *  因为所有写入都经 DAO 显式给值, 不依赖数据库默认)。
 *
 * 关于索引(Room 2.6.1 TableInfo 运行时校验约束, 详见 Migration8To9 KDoc):
 * - partial / expression 索引无法用 @Index 表达, 真实索引由 [MIGRATION_8_9] 手写
 *   execSQL 建立(RFC §3.3 原文);
 * - 此处的 @Index 是与 SQLite 读回形状(PRAGMA index_xinfo, 表达式项被跳过)一致的
 *   **镜像注解**, 仅用于通过升级后的 schema 校验; 全新安装时 Room 按镜像自动生成的
 *   "全量唯一"索引语义错误, 由 [GraphSchemaV9.freshInstallCallback] 替换回 partial 版;
 * - idx_kg_edge_parent / idx_kg_edge_child 的镜像与真实索引同名同列(仅差 WHERE 谓词,
 *   谓词对校验不可见), 全新安装得到的全量索引是 partial 的语义超集, 无需替换。
 *
 * 关于外键: learning_nodes / areas 属于 data:content-room 的旧表, 其实体类不在本模块
 * 编译路径上, 无法以注解声明(FK 同样被运行时严格比对, 注解缺失而 DDL 存在会导致升级
 * 校验崩溃)。因此 root_node_id / parent_node_id / child_node_id / area_id / node_id
 * 的 REFERENCES 从句在迁移 DDL 中以注释形式保留, 引用完整性由应用层保证(节点一律经
 * application:content 的 SaveNodeCommand 创建)。kg_edge → kg_question 为模块内外键,
 * 注解与 DDL 双写, 保持一致。
 */

/** kg 表 TEXT 列的取值常量(与 RFC §3.2 枚举一一对应; DB 层以裸 TEXT 存储) */
object KgContract {
    // kg_question.status
    const val QUESTION_STATUS_ACTIVE = "ACTIVE"
    const val QUESTION_STATUS_ARCHIVED = "ARCHIVED"

    // kg_question.category (KgCategory)
    const val CATEGORY_CS_BASIC = "CS_BASIC"
    const val CATEGORY_ALGORITHM = "ALGORITHM"
    const val CATEGORY_SYSTEM_DESIGN = "SYSTEM_DESIGN"
    const val CATEGORY_BEHAVIORAL = "BEHAVIORAL"

    // kg_edge.scope_type (EdgeScope)
    const val SCOPE_GLOBAL = "GLOBAL"
    const val SCOPE_PROBLEM_LOCAL = "PROBLEM_LOCAL"

    // kg_edge.status
    const val EDGE_STATUS_ACTIVE = "ACTIVE"
    const val EDGE_STATUS_PENDING_CONFIRMATION = "PENDING_CONFIRMATION"
    const val EDGE_STATUS_REJECTED = "REJECTED" // detach 软删墓碑(被 idx_kg_edge_live 排除)

    // kg_edge.created_by
    const val CREATED_BY_USER = "USER"
    const val CREATED_BY_AI = "AI"
    const val CREATED_BY_IMPORT = "IMPORT"

    // kg_proposal.kind
    const val PROPOSAL_KIND_PREREQUISITE_CHAIN = "PREREQUISITE_CHAIN"
    const val PROPOSAL_KIND_JD_DECOMPOSITION = "JD_DECOMPOSITION"

    // kg_proposal.status (状态机: PENDING → CONFIRMED / REJECTED / EXPIRED)
    const val PROPOSAL_STATUS_PENDING = "PENDING"
    const val PROPOSAL_STATUS_CONFIRMED = "CONFIRMED"
    const val PROPOSAL_STATUS_REJECTED = "REJECTED"
    const val PROPOSAL_STATUS_EXPIRED = "EXPIRED"

    // kg_mastery.state (MasteryState)
    const val MASTERY_UNKNOWN = "UNKNOWN"
    const val MASTERY_LEARNING = "LEARNING"
    const val MASTERY_FRAGILE = "FRAGILE"
    const val MASTERY_MASTERED = "MASTERED"

    // verdict (kg_mastery.last_verdict / kg_mastery_event.verdict)
    const val VERDICT_PASS = "PASS"
    const val VERDICT_FAIL = "FAIL"
}

/**
 * kg_question —— 登记的"问题根": 把某 learning_node 注册为带独立序号的问题。
 * (area_id, problem_no) 在非归档行内唯一(partial unique index idx_kg_question_areano,
 * 由 Migration 建立, 见类 KDoc); 序号只增不改。
 */
@Entity(
    tableName = "kg_question",
    indices = [
        // 镜像注解(非真实语义): 真实索引为 Migration 内
        // CREATE UNIQUE INDEX idx_kg_question_areano
        //   ON kg_question(COALESCE(area_id,''), problem_no) WHERE status != 'ARCHIVED'
        Index(name = "idx_kg_question_areano", value = ["problem_no"], unique = true),
    ],
)
data class KgQuestionEntity(
    @PrimaryKey
    @ColumnInfo(name = "question_id") val questionId: String,
    /** 外键 → learning_nodes(id), 由应用层保证(见类 KDoc) */
    @ColumnInfo(name = "root_node_id") val rootNodeId: String,
    /** 外键 → areas(id), 可空(NULL 归入 '' 序号桶), 由应用层保证 */
    @ColumnInfo(name = "area_id") val areaId: String? = null,
    @ColumnInfo(name = "problem_no") val problemNo: Int,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "category") val category: String = KgContract.CATEGORY_CS_BASIC,
    @ColumnInfo(name = "jd_batch_id") val jdBatchId: String? = null,
    @ColumnInfo(name = "status") val status: String = KgContract.QUESTION_STATUS_ACTIVE,
    @ColumnInfo(name = "revision") val revision: Long = 1,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * kg_edge —— 全局一张 DAG 的边: parent 依赖 child(child 是前置)。
 * scope: GLOBAL 对所有树可见; PROBLEM_LOCAL 仅 scope_question_id 那棵树可见。
 * 活跃唯一性(parent, child, scope_type, COALESCE(scope_question_id,'')) 且
 * status != 'REJECTED' 由 partial unique index idx_kg_edge_live 保证(Migration 建立)。
 */
@Entity(
    tableName = "kg_edge",
    foreignKeys = [
        ForeignKey(
            entity = KgQuestionEntity::class,
            parentColumns = ["question_id"],
            childColumns = ["scope_question_id"],
        ),
    ],
    indices = [
        // 镜像注解(非真实语义): 真实索引为 Migration 内
        // CREATE UNIQUE INDEX idx_kg_edge_live
        //   ON kg_edge(parent_node_id, child_node_id, scope_type, COALESCE(scope_question_id,''))
        //   WHERE status != 'REJECTED'
        Index(
            name = "idx_kg_edge_live",
            value = ["parent_node_id", "child_node_id", "scope_type"],
            unique = true,
        ),
        // 与真实索引同名同列(真实版带 WHERE status='ACTIVE', 谓词对校验不可见)
        Index(name = "idx_kg_edge_parent", value = ["parent_node_id"]),
        Index(name = "idx_kg_edge_child", value = ["child_node_id"]),
        // 附加索引(RFC §3.3 之外的新增, 见 Migration8To9 KDoc): 消除 FK 列全表扫描告警,
        // 加速"问题树可见边"查询的 scope_question_id 等值过滤
        Index(name = "idx_kg_edge_scopeq", value = ["scope_question_id"]),
    ],
)
data class KgEdgeEntity(
    @PrimaryKey
    @ColumnInfo(name = "edge_id") val edgeId: String,
    /** 外键 → learning_nodes(id), 由应用层保证 */
    @ColumnInfo(name = "parent_node_id") val parentNodeId: String,
    /** 外键 → learning_nodes(id), 由应用层保证 */
    @ColumnInfo(name = "child_node_id") val childNodeId: String,
    @ColumnInfo(name = "scope_type") val scopeType: String = KgContract.SCOPE_GLOBAL,
    /** 外键 → kg_question(question_id), 注解 + DDL 双写; scope_type=GLOBAL 时为 NULL */
    @ColumnInfo(name = "scope_question_id") val scopeQuestionId: String? = null,
    @ColumnInfo(name = "status") val status: String = KgContract.EDGE_STATUS_ACTIVE,
    @ColumnInfo(name = "created_by") val createdBy: String = KgContract.CREATED_BY_USER,
    @ColumnInfo(name = "revision") val revision: Long = 1,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * kg_proposal —— AI 产出的两段式门禁: PENDING(带 TTL) → CONFIRMED / REJECTED / EXPIRED。
 * AI 产出永不直接生效; command_id 在确认时回填为确认命令的 commandId。
 */
@Entity(tableName = "kg_proposal")
data class KgProposalEntity(
    @PrimaryKey
    @ColumnInfo(name = "proposal_id") val proposalId: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "status") val status: String = KgContract.PROPOSAL_STATUS_PENDING,
    @ColumnInfo(name = "model_ref") val modelRef: String? = null,
    @ColumnInfo(name = "command_id") val commandId: String? = null,
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * kg_mastery —— 节点掌握度投影(可重建; kg_mastery_event 的 quiz 证据为事实源)。
 * 状态机与 score 规则见 RFC §3.5(规则引擎在 domain 层, 本表只存投影结果)。
 */
@Entity(tableName = "kg_mastery")
data class KgMasteryEntity(
    @PrimaryKey
    /** 外键 → learning_nodes(id), 由应用层保证 */
    @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "state") val state: String = KgContract.MASTERY_UNKNOWN,
    @ColumnInfo(name = "score") val score: Double = 0.0,
    @ColumnInfo(name = "attempts") val attempts: Int = 0,
    @ColumnInfo(name = "fail_streak") val failStreak: Int = 0,
    @ColumnInfo(name = "last_verdict") val lastVerdict: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "revision") val revision: Long = 1,
)

/**
 * kg_mastery_event —— 判定事件(事实源, 只增不改)。
 * command_id 关联幂等命令, 重放时按 processed_commands 判重后整行跳过。
 */
@Entity(tableName = "kg_mastery_event")
data class KgMasteryEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "node_id") val nodeId: String,
    @ColumnInfo(name = "quiz_item_id") val quizItemId: String,
    @ColumnInfo(name = "verdict") val verdict: String,
    @ColumnInfo(name = "command_id") val commandId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
