package com.cslearningos.graph.data

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * schema v8 → v9: KnowledgeGraph 纯增量迁移(RFC §3.3)。
 *
 * ## 迁移策略
 * - **纯增量, 只 CREATE**: 只新增 kg_question / kg_edge / kg_proposal / kg_mastery /
 *   kg_mastery_event 五表及其索引, 全部带 IF NOT EXISTS; 绝不 ALTER/DROP v8 旧表
 *   (learning_nodes / quiz_items / review_states / processed_commands /
 *   replication_outbox / node_fts 原样保留并复用)。
 * - **无回填**: kg_mastery 是可重建投影, 冷启动为 UNKNOWN, 随复习判定事件自然积累;
 *   如需从历史 review_states 预热投影, 由后续可选 worker(应用层)按
 *   kg_mastery_event 事实源重放生成, 不属于本迁移职责(RFC §6 v1.1 才联动 SM-2)。
 * - 迁移完成后所有 kg 写路径自动具备幂等(processed_commands)与同步(replication_outbox)
 *   语义 —— 由 [RoomKgStore] 管线在事务内写入这两张现有表, 本迁移不碰它们。
 *
 * ## 与 Room 运行时 schema 校验的兼容性(已按 Room 2.6.1 源码核对)
 * Room 在每次迁移执行后做 TableInfo 校验: 列、**外键集**、**索引集** 三方都必须
 * 与实体注解期望严格一致, 否则抛 "Migration didn't properly handle"。由此产生四处
 * 与 RFC §3.3 原文的**受控偏差**(建表语句仅裁剪/改写无法表达或无法运行的从句,
 * 另有一条附加索引):
 *
 * 1. **跨模块外键裁剪**: RFC 中 root_node_id / parent_node_id / child_node_id /
 *    area_id / node_id 的 `REFERENCES learning_nodes(id)` / `REFERENCES areas(id)`
 *    无法在实体注解中声明(parent 实体类在 data:content-room, 不在本模块编译路径;
 *    注解缺失而 DDL 存在会被 PRAGMA foreign_key_list 读回并判为不匹配 → 崩溃)。
 *    故迁移 DDL 中这 5 处 REFERENCES 以行内注释形式保留原文, 引用完整性由应用层
 *    保证(节点一律经 application:content 的 SaveNodeCommand 创建后才可挂边)。
 *    kg_edge.scope_question_id → kg_question(question_id) 是模块内外键,
 *    注解([KgEdgeEntity])与 DDL 双写, 保持一致。
 *
 * 2. **partial / expression 索引的镜像注解**: @Index 无法表达 WHERE 谓词与表达式,
 *    但升级校验会把迁移创建的索引按 `PRAGMA index_xinfo` 读回(表达式项 cid<0 被
 *    跳过, WHERE 谓词不可见)并与实体 @Index 严格比对。因此实体上声明了与读回形状
 *    一致的**镜像 @Index**(同名/同列/同 unique, 见 Entities.kt), 使迁移路径校验通过;
 *    全新安装路径不做该校验, Room 会按镜像自动生成"全量唯一"的错误索引, 由
 *    [GraphSchemaV9.freshInstallCallback] 在建库后 DROP 并按 RFC 原文重建。
 *
 * 3. **附加索引 idx_kg_edge_scopeq**(RFC §3.3 之外的唯一语句级新增): 消除 Room
 *    对 kg_edge.scope_question_id 外键列的全表扫描告警并加速树查询, 纯增量无破坏性,
 *    与实体 @Index 镜像一致(见 [ADDITIVE_INDEX_STATEMENTS])。
 *
 * 4. **表达式唯一索引的 SQLite 3.8 便携改写**(见 [PARTIAL_UNIQUE_INDEX_STATEMENTS]):
 *    RFC 原文的 `ON ...(COALESCE(col,''), ...)` 表达式索引需 SQLite 3.9+, 在
 *    Robolectric(sqlite4java 3.8.7)与 minSdk 26 的旧设备上是 syntax error; 拆成语义
 *    等价的两个"纯列 + WHERE 谓词" partial unique 索引, 镜像 @Index 同步改名。
 *
 * ## 宿主集成(组合根三行)
 * ```
 * @Database(entities = [ ..., KgQuestionEntity::class, KgEdgeEntity::class,
 *     KgProposalEntity::class, KgMasteryEntity::class, KgMasteryEventEntity::class],
 *     version = 9 /* , exportSchema = true, 导出 v9 schema JSON 供 MigrationTestHelper */)
 * // Room.databaseBuilder(...).addMigrations(MIGRATION_8_9)
 * //     .addCallback(GraphSchemaV9.freshInstallCallback)
 * ```
 */
object GraphSchemaV9 {

    /**
     * 五张 kg 表的建表语句(列定义与 RFC §3.3 逐字一致; 跨模块 REFERENCES 见类 KDoc)。
     * 顺序保证 kg_edge 引用的 kg_question 先建(SQLite 建表不强制, 仅为可读性)。
     */
    private val TABLE_STATEMENTS = listOf(
        """
        CREATE TABLE IF NOT EXISTS kg_question (
          question_id TEXT PRIMARY KEY NOT NULL,
          root_node_id TEXT NOT NULL, -- REFERENCES learning_nodes(id): 跨模块外键, 应用层保证(见类 KDoc)
          area_id TEXT,               -- REFERENCES areas(id): 同上
          problem_no INTEGER NOT NULL,
          title TEXT NOT NULL,
          category TEXT NOT NULL DEFAULT 'CS_BASIC',
          jd_batch_id TEXT,
          status TEXT NOT NULL DEFAULT 'ACTIVE',          -- ACTIVE | ARCHIVED
          revision INTEGER NOT NULL DEFAULT 1,
          created_at INTEGER NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS kg_edge (
          edge_id TEXT PRIMARY KEY NOT NULL,
          parent_node_id TEXT NOT NULL,   -- REFERENCES learning_nodes(id): 跨模块外键, 应用层保证
          child_node_id  TEXT NOT NULL,   -- REFERENCES learning_nodes(id): 同上
          scope_type TEXT NOT NULL DEFAULT 'GLOBAL',      -- GLOBAL | PROBLEM_LOCAL
          scope_question_id TEXT REFERENCES kg_question(question_id),
          status TEXT NOT NULL DEFAULT 'ACTIVE',          -- ACTIVE | PENDING_CONFIRMATION | REJECTED
          created_by TEXT NOT NULL DEFAULT 'USER',        -- USER | AI | IMPORT
          revision INTEGER NOT NULL DEFAULT 1,
          created_at INTEGER NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS kg_proposal (
          proposal_id TEXT PRIMARY KEY NOT NULL,
          kind TEXT NOT NULL,                              -- PREREQUISITE_CHAIN | JD_DECOMPOSITION
          payload_json TEXT NOT NULL,
          status TEXT NOT NULL DEFAULT 'PENDING',          -- PENDING | CONFIRMED | REJECTED | EXPIRED
          model_ref TEXT, command_id TEXT,
          expires_at INTEGER NOT NULL, created_at INTEGER NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS kg_mastery (
          node_id TEXT PRIMARY KEY NOT NULL,  -- REFERENCES learning_nodes(id): 跨模块外键, 应用层保证
          state TEXT NOT NULL DEFAULT 'UNKNOWN',           -- UNKNOWN|LEARNING|FRAGILE|MASTERED
          score REAL NOT NULL DEFAULT 0.0,
          attempts INTEGER NOT NULL DEFAULT 0,
          fail_streak INTEGER NOT NULL DEFAULT 0,
          last_verdict TEXT, updated_at INTEGER NOT NULL,
          revision INTEGER NOT NULL DEFAULT 1
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS kg_mastery_event (
          event_id TEXT PRIMARY KEY NOT NULL,
          node_id TEXT NOT NULL, quiz_item_id TEXT NOT NULL,
          verdict TEXT NOT NULL, command_id TEXT NOT NULL,
          created_at INTEGER NOT NULL
        )
        """,
    )

    /**
     * 普通(非唯一)partial 索引: 与实体 @Index 镜像同名同列, WHERE 谓词对校验不可见,
     * 因此迁移建 partial 版(RFC 原文), 全新安装由 Room 自动建全量版(语义超集), 两者兼容。
     */
    private val PLAIN_INDEX_STATEMENTS = listOf(
        "CREATE INDEX IF NOT EXISTS idx_kg_edge_parent ON kg_edge(parent_node_id) WHERE status = 'ACTIVE'",
        "CREATE INDEX IF NOT EXISTS idx_kg_edge_child ON kg_edge(child_node_id) WHERE status = 'ACTIVE'",
    )

    /**
     * RFC §3.3 之外的**附加索引**(唯一的语句级新增): Room 对 kg_edge.scope_question_id
     * 外键列报全表扫描告警, 而"问题树可见边"查询(scope_question_id 等值过滤)是其
     * 高频路径; 与实体 @Index 镜像完全一致, 对 backend/FastAPI 共享 DDL 无影响(纯增量)。
     */
    private val ADDITIVE_INDEX_STATEMENTS = listOf(
        "CREATE INDEX IF NOT EXISTS idx_kg_edge_scopeq ON kg_edge(scope_question_id)",
    )

    /**
     * partial unique 索引(**受控偏差 4: SQLite 3.8 便携改写**)。
     *
     * RFC §3.3 原文使用**表达式索引** `ON kg_question(COALESCE(area_id,''), problem_no)` —
     * 表达式索引需要 SQLite 3.9+, 而 Robolectric 的 sqlite4java 为 3.8.7, 且 minSdk 26
     * 的部分旧设备同样是 3.8.x, 原文 DDL 在这些环境直接 syntax error。
     *
     * 语义等价的便携改写(NULL 语义保持不变, 应用层零改动): 每个表达式唯一索引拆成
     * 两个**纯列 + WHERE 谓词**的 partial unique 索引(partial index 自 SQLite 3.8.0 起
     * 支持), 分别覆盖"引用列 IS NULL"(归入全局桶)与"IS NOT NULL"(按值分桶)两种情况 —
     * 谓词已保证被索引列不含 NULL, 故唯一性判定与 COALESCE 原文完全一致:
     * - idx_kg_question_areano_null: 无 area 的问题共享一个 (problem_no) 序号桶;
     * - idx_kg_question_areano_area: 每个 area 内 (area_id, problem_no) 唯一;
     * - idx_kg_edge_live_global: 无 scope 问题的活跃边三元组唯一;
     * - idx_kg_edge_live_scoped: 带 scope 问题的活跃边四元组唯一。
     * (均带 status 谓词, 归档问题/墓碑边不占位。)
     */
    private val PARTIAL_UNIQUE_INDEX_STATEMENTS = listOf(
        """
        CREATE UNIQUE INDEX IF NOT EXISTS idx_kg_question_areano_null
          ON kg_question(problem_no) WHERE status != 'ARCHIVED' AND area_id IS NULL
        """,
        """
        CREATE UNIQUE INDEX IF NOT EXISTS idx_kg_question_areano_area
          ON kg_question(area_id, problem_no) WHERE status != 'ARCHIVED' AND area_id IS NOT NULL
        """,
        """
        CREATE UNIQUE INDEX IF NOT EXISTS idx_kg_edge_live_global
          ON kg_edge(parent_node_id, child_node_id, scope_type)
          WHERE status != 'REJECTED' AND scope_question_id IS NULL
        """,
        """
        CREATE UNIQUE INDEX IF NOT EXISTS idx_kg_edge_live_scoped
          ON kg_edge(parent_node_id, child_node_id, scope_type, scope_question_id)
          WHERE status != 'REJECTED' AND scope_question_id IS NOT NULL
        """,
    )

    /** RFC §3.3 全部 DDL(迁移路径逐条 execSQL) + 附加索引(见 [ADDITIVE_INDEX_STATEMENTS] 说明) */
    val ALL_STATEMENTS: List<String> =
        TABLE_STATEMENTS + PLAIN_INDEX_STATEMENTS + PARTIAL_UNIQUE_INDEX_STATEMENTS + ADDITIVE_INDEX_STATEMENTS

    /**
     * 全新安装(直接建 v9 库, 不走迁移)时, Room 按实体镜像 @Index 自动生成的四个
     * idx_kg_*_live/areano 索引是"全量唯一"索引(无 WHERE 谓词), 语义错误(跨桶互锁 /
     * 墓碑阻挡重建), 必须替换回 partial 版。宿主注册:
     * `.addCallback(GraphSchemaV9.freshInstallCallback)`。
     * (迁移路径无需此回调 —— 迁移内建的就是 partial 版; 未注册时全新安装会
     * 以唯一约束冲突的形式"响亮失败", 不会静默产生脏数据。)
     */
    val freshInstallCallback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS idx_kg_question_areano_null")
            db.execSQL("DROP INDEX IF EXISTS idx_kg_question_areano_area")
            db.execSQL("DROP INDEX IF EXISTS idx_kg_edge_live_global")
            db.execSQL("DROP INDEX IF EXISTS idx_kg_edge_live_scoped")
            PARTIAL_UNIQUE_INDEX_STATEMENTS.forEach(db::execSQL)
        }
    }
}

/**
 * 纯增量迁移本体: 逐条 execSQL RFC §3.3 全部 CREATE TABLE / INDEX 语句(IF NOT EXISTS,
 * 可安全重入)。宿主 RoomDatabase 注册: `.addMigrations(MIGRATION_8_9)`。
 */
val MIGRATION_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        GraphSchemaV9.ALL_STATEMENTS.forEach(db::execSQL)
    }
}
