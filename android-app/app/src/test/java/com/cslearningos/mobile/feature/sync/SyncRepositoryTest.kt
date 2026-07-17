package com.cslearningos.mobile.feature.sync

import androidx.test.core.app.ApplicationProvider
import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.CaptureSlipStatus
import com.cslearningos.mobile.data.CaptureSlipType
import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.NodeFtsEntity
import com.cslearningos.mobile.data.QuizFtsEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.QuizSource
import com.cslearningos.mobile.data.ReaderQuestionEntity
import com.cslearningos.mobile.data.ReviewAttemptEntity
import com.cslearningos.mobile.data.ReviewResult
import com.cslearningos.mobile.data.ReviewStateEntity
import com.cslearningos.mobile.data.SyncStatus
import java.lang.reflect.Proxy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
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
    fun reviewAttemptFromDesktopAppliesIdempotentlyAndRebuildsState() = runTest {
        val dao = FakeDao()
        dao.quizzes["q1"] = localQuiz("q1")
        val attemptRecord = SyncRecord.ReviewAttempt(
            id = "att-1",
            quizId = "q1",
            grade = "easy",
            answeredAt = "2026-07-16T09:30:00+00:00",
            elapsedMs = 0,
            note = ""
        )
        fun transport() = FakeTransport(
            manifests = ArrayDeque(
                listOf(
                    SyncManifest(
                        false, 1, "srv", 4, false,
                        listOf(SyncChange("review_attempt", "att-1", null, null, false, null))
                    )
                )
            ),
            records = mapOf("review_attempt" to listOf(attemptRecord))
        )
        val repository = SyncRepository(dao.proxy(), transport(), store) { 100L
        }

        val report = repository.pullAndApply(scope)

        assertEquals(1, report.appliedAttempts)
        val attempt = dao.attempts.getValue("att-1")
        assertEquals(ReviewResult.good, attempt.result) // desktop easy collapses to good
        assertTrue(attempt.scheduledDueAt > attempt.answeredAt)
        val state = dao.reviewStates.getValue("q1")
        assertEquals(1, state.attemptCount)
        assertEquals(1, state.intervalDays)

        val second = SyncRepository(dao.proxy(), transport(), store) { 200L }
        val replay = second.pullAndApply(scope)
        assertEquals(0, replay.appliedAttempts)
        assertEquals(1, dao.attempts.size)
    }

    @Test
    fun reviewAttemptForUnknownQuizIsSkipped() = runTest {
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
            records = mapOf(
                "review_attempt" to listOf(
                    SyncRecord.ReviewAttempt("att-1", "ghost-quiz", "good", "2026-07-16T09:30:00+00:00", 0, "")
                )
            )
        )
        val repository = SyncRepository(dao.proxy(), transport, store) { 100L }

        val report = repository.pullAndApply(scope)

        assertEquals(1, report.skippedAttempts)
        assertEquals(0, report.appliedAttempts)
    }

    @Test
    fun pushLocalChangesUploadsAttemptsCapturesAndQuestions() = runTest {
        val dao = FakeDao()
        dao.attempts["att-old"] = ReviewAttemptEntity("att-old", "q1", ReviewResult.good, 10L, 11L)
        dao.attempts["att-new"] = ReviewAttemptEntity("att-new", "q1", ReviewResult.hard, 20L, 22L)
        store.lastAttemptUploadAt = 15L
        dao.slips["slip-1"] = dirtySlip("slip-1")
        dao.questions["rq-1"] = dirtyQuestion("rq-1")
        val transport = FakeTransport(manifests = ArrayDeque(emptyList()), records = emptyMap())
        val repository = SyncRepository(dao.proxy(), transport, store) { 100L }

        val report = repository.pushLocalChanges()

        assertEquals(1, report.uploadedAttempts)
        assertEquals(listOf("att-new"), transport.pushedAttempts.map { it.getString("clientAttemptId") })
        assertEquals(20L, store.lastAttemptUploadAt)
        assertEquals(1, report.uploadedCaptures)
        assertEquals(SyncStatus.clean, dao.slips.getValue("slip-1").syncStatus)
        assertEquals(1, report.uploadedQuestions)
        assertEquals(SyncStatus.clean, dao.questions.getValue("rq-1").syncStatus)
        assertEquals(0, report.rejected)
    }

    @Test
    fun pushHighWaterStaysWhenServerRejects() = runTest {
        val dao = FakeDao()
        dao.attempts["att-1"] = ReviewAttemptEntity("att-1", "q1", ReviewResult.good, 20L, 22L)
        val transport = FakeTransport(manifests = ArrayDeque(emptyList()), records = emptyMap())
        transport.attemptsReceipts = listOf(SyncReceipt("att-1", SyncReceipt.STATUS_REJECTED, "unknown_quiz"))
        val repository = SyncRepository(dao.proxy(), transport, store) { 100L }

        val report = repository.pushLocalChanges()

        assertEquals(1, report.rejected)
        assertEquals(0, report.uploadedAttempts)
        assertEquals(0L, store.lastAttemptUploadAt)
    }

    private fun localQuiz(id: String) = QuizItemEntity(
        id = id,
        nodeId = null,
        prompt = "Q",
        answer = "A",
        explanation = "E",
        source = QuizSource.markdown,
        sourceAnchor = null,
        createdAt = 1L,
        updatedAt = 1L,
        revision = 1,
        syncStatus = SyncStatus.clean,
        deletedAt = null,
        area = "algorithms",
        baseRevision = 1
    )

    private fun dirtySlip(id: String) = CaptureSlipEntity(
        id = id,
        body = "TLB 是页表缓存",
        type = CaptureSlipType.concept_seed,
        topicHint = "TLB",
        sourceLabel = "phone",
        linkedNodeId = null,
        status = CaptureSlipStatus.inbox,
        createdAt = 10L,
        updatedAt = 10L,
        revision = 1,
        syncStatus = SyncStatus.dirty,
        deletedAt = null
    )

    private fun dirtyQuestion(id: String) = ReaderQuestionEntity(
        id = id,
        nodeId = "n1",
        body = "为什么？",
        createdAt = 10L,
        resolvedAt = null,
        syncStatus = SyncStatus.dirty,
        deletedAt = null
    )

    private class FakeTransport(
        private val healthServerId: String = "srv",
        private val manifests: ArrayDeque<SyncManifest>,
        private val records: Map<String, List<SyncRecord>>
    ) : SyncTransport {
        val manifestCursors = mutableListOf<Long>()
        val pushedAttempts = mutableListOf<JSONObject>()
        val pushedCaptures = mutableListOf<JSONObject>()
        val pushedQuestions = mutableListOf<JSONObject>()
        var attemptsReceipts: List<SyncReceipt> = emptyList()
        var capturesReceipts: List<SyncReceipt> = emptyList()
        var questionsReceipts: List<SyncReceipt> = emptyList()

        override suspend fun health(): SyncHealth = SyncHealth(1, healthServerId, 1)

        override suspend fun manifest(cursor: Long, serverId: String, scope: SyncScope): SyncManifest {
            manifestCursors += cursor
            return manifests.removeFirst()
        }

        override suspend fun pull(entityType: String, ids: List<String>, scope: SyncScope): List<SyncRecord> =
            (records[entityType] ?: emptyList()).filter { it.id in ids }

        override suspend fun pushAttempts(items: List<JSONObject>): List<SyncReceipt> {
            pushedAttempts += items
            return attemptsReceipts.ifEmpty {
                items.map { SyncReceipt(it.getString("clientAttemptId"), SyncReceipt.STATUS_ACCEPTED, null) }
            }
        }

        override suspend fun pushCaptures(items: List<JSONObject>): List<SyncReceipt> {
            pushedCaptures += items
            return capturesReceipts.ifEmpty {
                items.map { SyncReceipt(it.getString("id"), SyncReceipt.STATUS_ACCEPTED, null) }
            }
        }

        override suspend fun pushReaderQuestions(items: List<JSONObject>): List<SyncReceipt> {
            pushedQuestions += items
            return questionsReceipts.ifEmpty {
                items.map { SyncReceipt(it.getString("clientId"), SyncReceipt.STATUS_ACCEPTED, null) }
            }
        }

        override suspend fun pair(endpoint: String, token: String, deviceName: String): SyncPairing.PairResult =
            SyncPairing.PairResult(
                deviceId = "device-test",
                credential = "css_test",
                serverId = healthServerId,
                protocolVersion = 1
            )
    }

    private class FakeDao {
        val nodes = linkedMapOf<String, LearningNodeEntity>()
        val quizzes = linkedMapOf<String, QuizItemEntity>()
        val areas = linkedMapOf<String, AreaEntity>()
        val questions = linkedMapOf<String, ReaderQuestionEntity>()
        val slips = linkedMapOf<String, CaptureSlipEntity>()
        val attempts = linkedMapOf<String, ReviewAttemptEntity>()
        val reviewStates = linkedMapOf<String, ReviewStateEntity>()
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
                    "getAllAttempts" -> attempts.values.toList()
                    "getAllCaptureSlips" -> slips.values.toList()
                    "getAllReaderQuestions" -> questions.values.toList()
                    "upsertCaptureSlip" -> {
                        val slip = args[0] as CaptureSlipEntity
                        slips[slip.id] = slip
                        Unit
                    }
                    "upsertReaderQuestion" -> {
                        val question = args[0] as ReaderQuestionEntity
                        questions[question.id] = question
                        Unit
                    }
                    "upsertReviewState" -> {
                        val state = args[0] as ReviewStateEntity
                        reviewStates[state.quizId] = state
                        Unit
                    }
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
                        (args[5] as List<ReviewAttemptEntity>).forEach { attempts[it.id] = it }
                        (args[6] as List<ReviewStateEntity>).forEach { reviewStates[it.quizId] = it }
                        nodeFts += args[7] as List<NodeFtsEntity>
                        quizFts += args[8] as List<QuizFtsEntity>
                        deletedNodeFts += args[9] as List<String>
                        deletedQuizFts += args[10] as List<String>
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
