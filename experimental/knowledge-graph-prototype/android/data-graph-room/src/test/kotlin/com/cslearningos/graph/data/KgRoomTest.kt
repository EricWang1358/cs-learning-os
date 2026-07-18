package com.cslearningos.graph.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DAO / 迁移回调 / 事务管线的 JVM 冒烟测试骨架(Robolectric + in-memory Room, RFC §5 环境约定)。
 * 运行: ./gradlew :data:graph-room:testDebugUnitTest
 *
 * 覆盖: ①序号分配与 partial unique 索引(全新安装回调) ②问题树可见边(ProblemLocal 隔离)
 * ③活跃边查重/墓碑重建 ④提案状态机 CAS 与过期清理 ⑤mastery upsert + 事件序
 * ⑥RoomKgStore 幂等管线(重放/异指纹拒绝)与 outbox 投影。
 * 注: 与 InMemoryKgStore 双跑的完整契约测试由 application:graph 侧持有, 此处只做适配层冒烟。
 */

@Database(
    entities = [
        KgQuestionEntity::class,
        KgEdgeEntity::class,
        KgProposalEntity::class,
        KgMasteryEntity::class,
        KgMasteryEventEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class TestGraphDatabase : RoomDatabase(), KgDaoProvider {
    abstract override fun kgQuestionDao(): KgQuestionDao
    abstract override fun kgEdgeDao(): KgEdgeDao
    abstract override fun kgProposalDao(): KgProposalDao
    abstract override fun kgMasteryDao(): KgMasteryDao
}

private class FakeProcessedCommandPort : ProcessedCommandPort {
    val records = mutableMapOf<String, String>()

    override suspend fun find(commandId: String): ProcessedCommandSnapshot? =
        records[commandId]?.let { ProcessedCommandSnapshot(commandId, it) }

    override suspend fun record(commandId: String, fingerprint: String) {
        records[commandId] = fingerprint
    }
}

private class FakeOutboxPort : OutboxPort {
    data class Row(val aggregateType: String, val aggregateId: String, val operation: String, val payloadJson: String)

    val rows = mutableListOf<Row>()

    override suspend fun append(aggregateType: String, aggregateId: String, operation: String, payloadJson: String) {
        rows += Row(aggregateType, aggregateId, operation, payloadJson)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KgRoomTest {

    private lateinit var db: TestGraphDatabase
    private lateinit var commands: FakeProcessedCommandPort
    private lateinit var outbox: FakeOutboxPort
    private lateinit var store: RoomKgStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TestGraphDatabase::class.java)
            // 全新安装: 将 Room 按镜像 @Index 自动建的"全量唯一"索引替换回 RFC partial 版
            .addCallback(GraphSchemaV9.freshInstallCallback)
            .build()
        commands = FakeProcessedCommandPort()
        outbox = FakeOutboxPort()
        store = RoomKgStore(db, db, commands, outbox)
    }

    @After
    fun tearDown() = db.close()

    // ---------------- fixtures ----------------

    private fun question(
        id: String,
        areaId: String? = null,
        problemNo: Int,
        status: String = KgContract.QUESTION_STATUS_ACTIVE,
    ) = KgQuestionEntity(
        questionId = id,
        rootNodeId = "root-$id",
        areaId = areaId,
        problemNo = problemNo,
        title = "题$id",
        createdAt = 1L,
        status = status,
    )

    private fun edge(
        id: String,
        parent: String,
        child: String,
        scopeType: String = KgContract.SCOPE_GLOBAL,
        scopeQuestionId: String? = null,
    ) = KgEdgeEntity(
        edgeId = id,
        parentNodeId = parent,
        childNodeId = child,
        scopeType = scopeType,
        scopeQuestionId = scopeQuestionId,
        createdAt = 1L,
    )

    private fun mastery(nodeId: String, state: String, score: Double) = KgMasteryEntity(
        nodeId = nodeId,
        state = state,
        score = score,
        updatedAt = 1L,
    )

    // ---------------- ① 序号分配 + partial unique 索引 ----------------

    @Test
    fun `nextProblemNo 按 area 桶独立递增且空桶从 1 开始`() = runBlocking {
        val dao = db.kgQuestionDao()
        assertEquals(1, dao.nextProblemNo(null))
        dao.insert(question("q1", areaId = null, problemNo = 1))
        dao.insert(question("q2", areaId = "a1", problemNo = 1))
        assertEquals(2, dao.nextProblemNo(null))
        assertEquals(2, dao.nextProblemNo("a1"))
        assertEquals(1, dao.nextProblemNo("a2"))
    }

    @Test
    fun `partial unique 索引已按 RFC 建立(partial 且表达式键)`() = runBlocking {
        db.query("SELECT sql FROM sqlite_master WHERE name = 'idx_kg_question_areano'", null).use { c ->
            assertTrue(c.moveToFirst())
            val sql = c.getString(0)
            assertTrue(sql.contains("WHERE", ignoreCase = true))
            assertTrue(sql.contains("COALESCE", ignoreCase = true))
        }
        db.query("SELECT sql FROM sqlite_master WHERE name = 'idx_kg_edge_live'", null).use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getString(0).contains("WHERE", ignoreCase = true))
        }
    }

    @Test
    fun `同 area 桶同序号且未归档冲突, 不同 area 或已归档放行`() = runBlocking {
        val dao = db.kgQuestionDao()
        dao.insert(question("q1", areaId = "a1", problemNo = 1))
        // 不同 area: 放行
        dao.insert(question("q2", areaId = "a2", problemNo = 1))
        // NULL area 与 '' 不同桶? 否 —— COALESCE 统一入 '' 桶, 与 area='a1' 不冲突
        dao.insert(question("q3", areaId = null, problemNo = 1))
        // 同桶同序号(未归档): 冲突
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insert(question("q4", areaId = "a1", problemNo = 1)) }
        }
        // 归档后同序号: 放行(partial WHERE status != 'ARCHIVED')
        dao.updateStatus("q1", KgContract.QUESTION_STATUS_ARCHIVED)
        dao.insert(question("q5", areaId = "a1", problemNo = 1))
        // NULL 桶同序号: 冲突
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insert(question("q6", areaId = null, problemNo = 1)) }
        }
    }

    // ---------------- ② 问题树可见边(ProblemLocal 隔离) ----------------

    @Test
    fun `visibleEdgesForQuestion 只含 GLOBAL 与本题 LOCAL`() = runBlocking {
        val dao = db.kgEdgeDao()
        dao.insert(edge("e-global", "p", "c1"))
        dao.insert(edge("e-local-q1", "p", "c2", KgContract.SCOPE_PROBLEM_LOCAL, "q1"))
        dao.insert(edge("e-local-q2", "p", "c3", KgContract.SCOPE_PROBLEM_LOCAL, "q2"))
        dao.insert(
            edge("e-pending", "p", "c4").copy(status = KgContract.EDGE_STATUS_PENDING_CONFIRMATION),
        )

        assertEquals(
            setOf("e-global", "e-local-q1"),
            dao.visibleEdgesForQuestion("q1").map { it.edgeId }.toSet(),
        )
        assertEquals(
            setOf("e-global", "e-local-q2"),
            dao.visibleEdgesForQuestion("q2").map { it.edgeId }.toSet(),
        )
        // 环检测取全量活跃边: 含双方 LOCAL, 不含 PENDING
        assertEquals(
            setOf("e-global", "e-local-q1", "e-local-q2"),
            dao.allActiveEdges().map { it.edgeId }.toSet(),
        )
        Unit
    }

    // ---------------- ③ 活跃边查重 / 墓碑重建 ----------------

    @Test
    fun `findLiveEdge 命中活跃四元组, REJECTED 墓碑可重建同四元组`() = runBlocking {
        val dao = db.kgEdgeDao()
        dao.insert(edge("e1", "p", "c"))
        assertNotNull(dao.findLiveEdge("p", "c", KgContract.SCOPE_GLOBAL, null))
        // 活跃重复: partial unique 冲突
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insert(edge("e2", "p", "c")) }
        }
        // 软删后重建: 放行
        dao.updateStatus("e1", KgContract.EDGE_STATUS_REJECTED)
        assertNull(dao.findLiveEdge("p", "c", KgContract.SCOPE_GLOBAL, null))
        dao.insert(edge("e2", "p", "c"))
        assertEquals("e2", dao.findLiveEdge("p", "c", KgContract.SCOPE_GLOBAL, null)?.edgeId)
    }

    @Test
    fun `双向索引查询按 parent 与 child 取活跃边`() = runBlocking {
        val dao = db.kgEdgeDao()
        dao.insert(edge("e1", "p", "c1"))
        dao.insert(edge("e2", "p", "c2"))
        dao.insert(edge("e3", "x", "c1"))
        assertEquals(setOf("e1", "e2"), dao.activeEdgesFromParent("p").map { it.edgeId }.toSet())
        assertEquals(setOf("e1", "e3"), dao.activeEdgesToChild("c1").map { it.edgeId }.toSet())
    }

    // ---------------- ④ 提案状态机 ----------------

    @Test
    fun `proposal CAS 仅 PENDING 可迁移, 过期清理生效`() = runBlocking {
        val dao = db.kgProposalDao()
        dao.insert(
            KgProposalEntity(
                proposalId = "p1",
                kind = KgContract.PROPOSAL_KIND_PREREQUISITE_CHAIN,
                payloadJson = "{}",
                expiresAt = 100L,
                createdAt = 1L,
            ),
        )
        assertEquals(1, dao.transitionFromPending("p1", KgContract.PROPOSAL_STATUS_CONFIRMED, "cmd-1"))
        // 终态后再次迁移: CAS 失败 + command_id 已回填
        assertEquals(0, dao.transitionFromPending("p1", KgContract.PROPOSAL_STATUS_REJECTED))
        assertEquals("cmd-1", dao.findById("p1")?.commandId)

        dao.insert(
            KgProposalEntity(
                proposalId = "p2",
                kind = KgContract.PROPOSAL_KIND_JD_DECOMPOSITION,
                payloadJson = "{}",
                expiresAt = 50L,
                createdAt = 1L,
            ),
        )
        assertEquals(1, dao.expireOverdue(now = 60L))
        assertEquals(KgContract.PROPOSAL_STATUS_EXPIRED, dao.findById("p2")?.status)
        assertEquals(0, dao.expireOverdue(now = 60L))
    }

    // ---------------- ⑤ mastery upsert + 事件序 ----------------

    @Test
    fun `mastery upsert 与 recentVerdicts 新到旧排序`() = runBlocking {
        val dao = db.kgMasteryDao()
        dao.upsert(mastery("n1", KgContract.MASTERY_LEARNING, 0.2))
        dao.upsert(mastery("n1", KgContract.MASTERY_FRAGILE, 0.0))
        assertEquals(KgContract.MASTERY_FRAGILE, dao.findByNodeId("n1")?.state)

        dao.insertEvent(KgMasteryEventEntity("ev1", "n1", "quiz1", KgContract.VERDICT_PASS, "cmd-1", 10L))
        dao.insertEvent(KgMasteryEventEntity("ev2", "n1", "quiz2", KgContract.VERDICT_FAIL, "cmd-2", 20L))
        assertEquals(
            listOf(KgContract.VERDICT_FAIL, KgContract.VERDICT_PASS),
            dao.recentVerdicts("n1", 3),
        )
        assertEquals(1, dao.countRecentFailures("n1", since = 15L))
        // 弱节点快照: FRAGILE 且 score<0.6 入选
        assertEquals(listOf("n1"), dao.weakMasteries().map { it.nodeId })
    }

    @Test
    fun `observeWeakMasteries 随 upsert 失效重发`() = runBlocking {
        val deferred = async(Dispatchers.IO) {
            db.kgMasteryDao().observeWeakMasteries().first()
        }
        assertEquals(0, deferred.await().size)
        db.kgMasteryDao().upsert(mastery("n1", KgContract.MASTERY_LEARNING, 0.4))
        val second = async(Dispatchers.IO) {
            db.kgMasteryDao().observeWeakMasteries().first { it.isNotEmpty() }
        }
        assertEquals(1, withTimeout(5_000) { second.await() }.size)
    }

    // ---------------- ⑥ RoomKgStore 幂等管线 + outbox ----------------

    @Test
    fun `insertQuestion 同指纹重放跳过, 异指纹拒绝, outbox 仅投影一次`() = runBlocking {
        val q = KgQuestionDto(
            questionId = "q1",
            rootNodeId = "r1",
            areaId = "a1",
            problemNo = 1,
            title = "两数之和",
            createdAt = 1L,
        )
        assertEquals(KgWriteResult.EXECUTED, store.insertQuestion("cmd-1", "fp-1", q))
        assertEquals(KgWriteResult.REPLAYED, store.insertQuestion("cmd-1", "fp-1", q))
        assertThrows(KgCommandConflictException::class.java) {
            runBlocking { store.insertQuestion("cmd-1", "fp-other", q.copy(problemNo = 2)) }
        }
        assertEquals(1, commands.records.size)
        assertEquals(1, outbox.rows.size)
        val row = outbox.rows.single()
        assertEquals(RoomKgStore.AGGREGATE_QUESTION, row.aggregateType)
        assertEquals(RoomKgStore.OP_UPSERT, row.operation)
        assertEquals("q1", row.aggregateId)
        assertTrue(row.payloadJson.contains("\"problemNo\":1"))
    }

    @Test
    fun `detachEdge 软删墓碑并投影 DELETE, 缺失边抛 NotFound`() = runBlocking {
        store.appendEdges("cmd-1", "fp-1", listOf(KgEdgeDto("e1", "p", "c", createdAt = 1L)))
        assertEquals(KgWriteResult.EXECUTED, store.detachEdge("cmd-2", "fp-2", "e1"))
        assertEquals(KgContract.EDGE_STATUS_REJECTED, db.kgEdgeDao().findById("e1")?.status)
        assertEquals(
            RoomKgStore.OP_DELETE,
            outbox.rows.last().operation,
        )
        assertThrows(KgNotFoundException::class.java) {
            runBlocking { store.detachEdge("cmd-3", "fp-3", "e-missing") }
        }
    }

    @Test
    fun `appendEdges 对活跃重复边幂等跳过且不重复投影`() = runBlocking {
        val e = KgEdgeDto("e1", "p", "c", createdAt = 1L)
        store.appendEdges("cmd-1", "fp-1", listOf(e))
        // 不同 commandId 但边已活跃存在: 执行成功但 0 新边 0 新 outbox
        assertEquals(
            KgWriteResult.EXECUTED,
            store.appendEdges("cmd-2", "fp-2", listOf(e.copy(edgeId = "e1-dup"))),
        )
        assertEquals(1, db.kgEdgeDao().allActiveEdges().size)
        assertEquals(1, outbox.rows.size)
    }

    @Test
    fun `confirmProposal 过期落库 EXPIRED 并抛错, CAS 结果同事务落库`() = runBlocking {
        store.insertProposal(
            KgProposalDto("p1", KgContract.PROPOSAL_KIND_PREREQUISITE_CHAIN, "{}", expiresAt = 100L, createdAt = 1L),
        )
        // 未过期确认
        assertEquals(
            KgWriteResult.EXECUTED,
            store.confirmProposal(
                commandId = "cmd-1",
                fingerprint = "fp-1",
                proposalId = "p1",
                now = 50L,
                editedPayloadJson = null,
                questions = listOf(
                    KgQuestionDto("q1", "r1", null, 1, "题1", createdAt = 1L),
                ),
                edges = listOf(KgEdgeDto("e1", "r1", "c1", createdAt = 1L)),
            ),
        )
        assertEquals(KgContract.PROPOSAL_STATUS_CONFIRMED, db.kgProposalDao().findById("p1")?.status)
        assertEquals("cmd-1", db.kgProposalDao().findById("p1")?.commandId)
        assertEquals(1, db.kgQuestionDao().nextProblemNo(null).minus(1))
        assertEquals(2, outbox.rows.size) // question + edge 各一

        // 过期提案: 落库 EXPIRED 后抛 ProposalExpired
        store.insertProposal(
            KgProposalDto("p2", KgContract.PROPOSAL_KIND_JD_DECOMPOSITION, "{}", expiresAt = 10L, createdAt = 1L),
        )
        assertThrows(KgProposalExpiredException::class.java) {
            runBlocking {
                store.confirmProposal("cmd-9", "fp-9", "p2", now = 99L, null, emptyList(), emptyList())
            }
        }
        assertEquals(KgContract.PROPOSAL_STATUS_EXPIRED, db.kgProposalDao().findById("p2")?.status)
    }

    @Test
    fun `expertiseProgress 聚合按 category 与 mastery 分桶`() = runBlocking {
        val dao = db.kgQuestionDao()
        dao.insert(question("q1", problemNo = 1).copy(jdBatchId = "jd1", category = KgContract.CATEGORY_ALGORITHM))
        dao.insert(question("q2", problemNo = 2).copy(jdBatchId = "jd1", category = KgContract.CATEGORY_ALGORITHM))
        dao.insert(question("q3", problemNo = 3).copy(jdBatchId = "jd1", category = KgContract.CATEGORY_CS_BASIC))
        db.kgMasteryDao().upsert(mastery("root-q1", KgContract.MASTERY_MASTERED, 0.9))

        val rows = dao.batchMasteryBreakdown("jd1")
        val byCategoryState = rows.associate { (it.category to it.masteryState) to it.cnt }
        assertEquals(1, byCategoryState[KgContract.CATEGORY_ALGORITHM to KgContract.MASTERY_MASTERED])
        assertEquals(1, byCategoryState[KgContract.CATEGORY_ALGORITHM to KgContract.MASTERY_UNKNOWN])
        assertEquals(1, byCategoryState[KgContract.CATEGORY_CS_BASIC to KgContract.MASTERY_UNKNOWN])
    }
}
