package com.cslearningos.mobile.feature.sync

import androidx.test.core.app.ApplicationProvider
import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.NodeFtsEntity
import com.cslearningos.mobile.data.QuizFtsEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.ReaderQuestionEntity
import com.cslearningos.mobile.data.SyncStatus
import java.lang.reflect.Proxy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncRepositoryTest {

    private val scope = SyncScope(areas = listOf("algorithms"))

    private lateinit var store: SyncStateStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("cs_learning_sync", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = SyncStateStore(context)
    }

    private fun nodeRecord(id: String, revision: Long, body: String = "# Node $id", area: String = "algorithms") =
        SyncRecord.Node(
            id = id,
            title = "Node $id",
            area = area,
            track = "general",
            summary = "summary",
            body = body,
            visibility = "core",
            revision = revision,
            updatedAt = "2026-07-17T10:00:00+00:00",
            hash = "hash-$id"
        )

    private fun quizRecord(id: String, revision: Long) = SyncRecord.Quiz(
        id = id,
        title = "Quiz $id",
        area = "algorithms",
        difficulty = "easy",
        summary = "",
        body = "# Quiz\n\n## Prompt\n\nWhat is TLB?\n\n## Answer\n\nA translation cache.\n\n## Explanation\n\nIt caches page walks.",
        visibility = "practice",
        revision = revision,
        updatedAt = "2026-07-17T10:00:00+00:00",
        hash = "hash-$id"
    )

    private fun localNode(id: String, syncStatus: SyncStatus, baseRevision: Long, revision: Long = 5) =
        LearningNodeEntity(
            id = id,
            title = "Local $id",
            markdownBody = "# Local $id\n\nlocal text",
            createdAt = 1L,
            updatedAt = 2L,
            lastReadAt = null,
            revision = revision,
            syncStatus = syncStatus,
            deletedAt = null,
            baseRevision = baseRevision
        )

    @Test
    fun baselinePullCreatesNodeAreaAndFtsAndSavesCursor() = runTest {
        val dao = FakeDao()
        val transport = FakeTransport(
            manifests = ArrayDeque(
                listOf(
                    SyncManifest(
                        reset = false,
                        protocolVersion = 1,
                        serverId = "srv",
                        cursor = 7,
                        hasMore = false,
                        changes = listOf(
                            SyncChange("node", "n1", 2, "h", tombstone = false, area = "algorithms")
                        )
                    )
                )
            ),
            records = mapOf("node" to listOf(nodeRecord("n1", 2)))
        )
        val repository = SyncRepository(dao.proxy(), transport, store) { 100L }

        val report = repository.pullAndApply(scope)

        assertEquals(1, report.pulledNodes)
        assertEquals(7L, report.cursor)
        assertEquals(7L, store.cursor)
        assertEquals("srv", store.serverId)
        val stored = dao.nodes.getValue("n1")
        assertEquals(SyncStatus.clean, stored.syncStatus)
        assertEquals(2L, stored.baseRevision)
        assertEquals("algorithms", stored.areaId)
        assertTrue(dao.areas.containsKey("algorithms"))
        assertTrue(dao.nodeFts.any { it.nodeId == "n1" })
    }

    @Test
    fun replayingSamePullIsIdempotent() = runTest {
        val dao = FakeDao()
        fun transport() = FakeTransport(
            manifests = ArrayDeque(
                listOf(
                    SyncManifest(false, 1, "srv", 7, false, listOf(SyncChange("node", "n1", 2, "h", false, "algorithms")))
                )
            ),
            records = mapOf("node" to listOf(nodeRecord("n1", 2)))
        )
        val repository = SyncRepository(dao.proxy(), transport(), store) { 100L }
        repository.pullAndApply(scope)

        val second = SyncRepository(dao.proxy(), transport(), store) { 200L }
        val report = second.pullAndApply(scope)

        assertEquals(0, report.pulledNodes)
        assertEquals(2L, dao.nodes.getValue("n1").baseRevision)
        assertEquals(1, dao.nodeFts.count { it.nodeId == "n1" })
    }

    @Test
    fun dirtyLocalConflictCreatesCopyAndAppliesRemote() = runTest {
        val dao = FakeDao()
        dao.nodes["n1"] = localNode("n1", SyncStatus.dirty, baseRevision = 1, revision = 9)
        val transport = FakeTransport(
            manifests = ArrayDeque(
                listOf(
                    SyncManifest(false, 1, "srv", 7, false, listOf(SyncChange("node", "n1", 2, "h", false, "algorithms")))
                )
            ),
            records = mapOf("node" to listOf(nodeRecord("n1", 2, body = "# Remote better")))
        )
        val repository = SyncRepository(dao.proxy(), transport, store) { 100L }

        val report = repository.pullAndApply(scope)

        assertEquals(1, report.conflicts)
        assertEquals("# Remote better", dao.nodes.getValue("n1").markdownBody)
        assertEquals(SyncStatus.clean, dao.nodes.getValue("n1").syncStatus)
        val conflictCopy = dao.nodes.values.firstOrNull { it.id.startsWith("conflict-") }
        assertNotNull(conflictCopy)
        assertEquals(SyncStatus.conflicted, conflictCopy!!.syncStatus)
        assertTrue(conflictCopy.markdownBody.contains("local text"))
    }

    @Test
    fun tombstoneSoftDeletesCleanLocalAndKeepsDirtyLocal() = runTest {
        val dao = FakeDao()
        dao.nodes["clean"] = localNode("clean", SyncStatus.clean, baseRevision = 2)
        dao.nodes["dirty"] = localNode("dirty", SyncStatus.dirty, baseRevision = 2)
        val transport = FakeTransport(
            manifests = ArrayDeque(
                listOf(
                    SyncManifest(
                        false, 1, "srv", 9, false,
                        listOf(
                            SyncChange("node", "clean", 3, null, tombstone = true, area = null),
                            SyncChange("node", "dirty", 3, null, tombstone = true, area = null)
                        )
                    )
                )
            ),
            records = emptyMap()
        )
        val repository = SyncRepository(dao.proxy(), transport, store) { 100L }

        val report = repository.pullAndApply(scope)

        assertEquals(1, report.removed)
        assertEquals(1, report.conflicts)
        assertNotNull(dao.nodes.getValue("clean").deletedAt)
        assertNull(dao.nodes.getValue("dirty").deletedAt)
        assertEquals(SyncStatus.conflicted, dao.nodes.getValue("dirty").syncStatus)
    }

    @Test
    fun entityLeavingScopeRemovesPreviouslySyncedCleanCopy() = runTest {
        val dao = FakeDao()
        dao.nodes["n1"] = localNode("n1", SyncStatus.clean, baseRevision = 2)
        val transport = FakeTransport(
            manifests = ArrayDeque(
                listOf(
                    SyncManifest(false, 1, "srv", 9, false, listOf(SyncChange("node", "n1", 3, "h", false, "systems")))
                )
            ),
            records = emptyMap()
        )
        val repository = SyncRepository(dao.proxy(), transport, store) { 100L }

        val report = repository.pullAndApply(scope)

        assertEquals(1, report.removed)
        assertNotNull(dao.nodes.getValue("n1").deletedAt)
        assertTrue(dao.deletedNodeFts.contains("n1"))
    }

    @Test
    fun serverIdChangeRebaselinesCursor() = runTest {
        store.serverId = "old-server"
        store.cursor = 55
        store.scopeFingerprint = scope.fingerprint()
        val dao = FakeDao()
        val transport = FakeTransport(
            healthServerId = "new-server",
            manifests = ArrayDeque(
                listOf(SyncManifest(false, 1, "new-server", 3, false, emptyList()))
            ),
            records = emptyMap()
        )
        val repository = SyncRepository(dao.proxy(), transport, store) { 100L }

        repository.pullAndApply(scope)

        assertEquals(listOf(0L), transport.manifestCursors)
        assertEquals("new-server", store.serverId)
        assertEquals(3L, store.cursor)
    }

    @Test
    fun quizRecordConvertsDesktopSections() = runTest {
        val dao = FakeDao()
        val transport = FakeTransport(
            manifests = ArrayDeque(
                listOf(
                    SyncManifest(false, 1, "srv", 4, false, listOf(SyncChange("quiz", "q1", 1, "h", false, "algorithms")))
                )
            ),
            records = mapOf("quiz" to listOf(quizRecord("q1", 1)))
        )
        val repository = SyncRepository(dao.proxy(), transport, store) { 100L }

        val report = repository.pullAndApply(scope)

        assertEquals(1, report.pulledQuizzes)
        val quiz = dao.quizzes.getValue("q1")
        assertEquals("What is TLB?", quiz.prompt)
        assertEquals("A translation cache.", quiz.answer)
        assertTrue(quiz.explanation.contains("page walks"))
        assertTrue(dao.quizFts.any { it.quizId == "q1" })
    }

    @Test
    fun reviewAttemptChangesAreCountedAsSkipped() = runTest {
        val dao = FakeDao()
        val transport = FakeTransport(
            manifests = ArrayDeque(
                listOf(
                    SyncManifest(
                        false, 1, "srv", 4, false,
                        listOf(SyncChange("review_attempt", "att-1", null, null, false, null))
                    )
                )
            ),
            records = emptyMap()
        )
        val repository = SyncRepository(dao.proxy(), transport, store) { 100L }

        val report = repository.pullAndApply(scope)

        assertEquals(1, report.skippedAttempts)
        assertEquals(0, report.totalApplied)
    }

    private class FakeTransport(
        private val healthServerId: String = "srv",
        private val manifests: ArrayDeque<SyncManifest>,
        private val records: Map<String, List<SyncRecord>>
    ) : SyncTransport {
        val manifestCursors = mutableListOf<Long>()

        override suspend fun health(): SyncHealth = SyncHealth(1, healthServerId, 1)

        override suspend fun manifest(cursor: Long, serverId: String, scope: SyncScope): SyncManifest {
            manifestCursors += cursor
            return manifests.removeFirst()
        }

        override suspend fun pull(entityType: String, ids: List<String>, scope: SyncScope): List<SyncRecord> =
            (records[entityType] ?: emptyList()).filter { it.id in ids }
    }

    private class FakeDao {
        val nodes = linkedMapOf<String, LearningNodeEntity>()
        val quizzes = linkedMapOf<String, QuizItemEntity>()
        val areas = linkedMapOf<String, AreaEntity>()
        val questions = linkedMapOf<String, ReaderQuestionEntity>()
        val slips = linkedMapOf<String, CaptureSlipEntity>()
        val nodeFts = mutableListOf<NodeFtsEntity>()
        val quizFts = mutableListOf<QuizFtsEntity>()
        val deletedNodeFts = mutableListOf<String>()
        val deletedQuizFts = mutableListOf<String>()

        @Suppress("UNCHECKED_CAST")
        fun proxy(): LearningDao =
            Proxy.newProxyInstance(
                LearningDao::class.java.classLoader,
                arrayOf(LearningDao::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "getNode" -> nodes[args[0]]
                    "getQuiz" -> quizzes[args[0]]
                    "getArea" -> areas[args[0]]
                    "getAreaBySlug" -> areas.values.firstOrNull { it.slug == args[0] }
                    "getReaderQuestion" -> questions[args[0]]
                    "getCaptureSlip" -> slips[args[0]]
                    "markNodeSyncedDeleted" -> {
                        nodes.computeIfPresent(args[0] as String) { _, node ->
                            node.copy(
                                deletedAt = args[1] as Long,
                                updatedAt = args[2] as Long,
                                syncStatus = SyncStatus.clean
                            )
                        }
                        Unit
                    }
                    "markQuizSyncedDeleted" -> {
                        quizzes.computeIfPresent(args[0] as String) { _, quiz ->
                            quiz.copy(
                                deletedAt = args[1] as Long,
                                updatedAt = args[2] as Long,
                                syncStatus = SyncStatus.clean
                            )
                        }
                        Unit
                    }
                    "deleteNodeFts" -> {
                        deletedNodeFts += args[0] as String
                        Unit
                    }
                    "deleteQuizFts" -> {
                        deletedQuizFts += args[0] as String
                        Unit
                    }
                    "applySyncBatch" -> {
                        (args[0] as List<AreaEntity>).forEach { areas[it.id] = it }
                        (args[1] as List<LearningNodeEntity>).forEach { nodes[it.id] = it }
                        (args[2] as List<QuizItemEntity>).forEach { quizzes[it.id] = it }
                        (args[3] as List<ReaderQuestionEntity>).forEach { questions[it.id] = it }
                        (args[4] as List<CaptureSlipEntity>).forEach { slips[it.id] = it }
                        nodeFts += args[6] as List<NodeFtsEntity>
                        quizFts += args[7] as List<QuizFtsEntity>
                        deletedNodeFts += args[8] as List<String>
                        deletedQuizFts += args[9] as List<String>
                        Unit
                    }
                    else -> when {
                        method.returnType == Boolean::class.javaPrimitiveType -> false
                        method.returnType == Int::class.javaPrimitiveType -> 0
                        method.returnType == Long::class.javaPrimitiveType -> 0L
                        else -> null
                    }
                }
            } as LearningDao
    }
}
