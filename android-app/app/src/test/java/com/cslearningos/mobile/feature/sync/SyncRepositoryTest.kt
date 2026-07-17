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
import com.cslearningos.mobile.data.ReplicationOutboxEntity
import com.cslearningos.mobile.data.ReviewAttemptEntity
import com.cslearningos.mobile.data.ReviewResult
import com.cslearningos.mobile.data.ReviewStateEntity
import com.cslearningos.mobile.data.SyncStatus
import com.cslearningos.mobile.feature.review.data.QuizOutboxCodec
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
    fun pullSplitsManifestIdsToFitTheServerPullLimit() = runTest {
        val changes = (1..201).map { index ->
            SyncChange("node", "n$index", 1, "h$index", tombstone = false, area = "algorithms")
        }
        val records = (1..201).map { index -> nodeRecord("n$index", 1) }
        val transport = FakeTransport(
            manifests = ArrayDeque(
                listOf(SyncManifest(false, 1, "srv", 201, false, changes))
            ),
            records = mapOf("node" to records)
        )
        val repository = SyncRepository(FakeDao().proxy(), transport, store) { 100L }

        repository.pullAndApply(scope)

        assertEquals(listOf(200, 1), transport.pullRequests["node"]?.map { it.size })
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
    fun pushLocalChangesUploadsPendingNodeOutboxAndAcknowledgesMatchingRevision() = runTest {
        val dao = FakeDao()
        dao.nodes["n1"] = localNode("n1", SyncStatus.dirty, baseRevision = 1, revision = 2).copy(
            title = "Phone title",
            markdownBody = "# Phone title\n\nEdited on phone.",
            summary = "phone summary"
        )
        dao.outbox["node-change-1"] = pendingNodeOutbox("node-change-1", "n1", baseRevision = 1, revision = 2)
        val transport = FakeTransport(manifests = ArrayDeque(emptyList()), records = emptyMap())
        val repository = SyncRepository(dao.proxy(), transport, store) { 100L }

        val report = repository.pushLocalChanges()

        assertEquals(1, report.uploadedNodes)
        assertEquals(0, report.rejected)
        assertEquals(1, transport.pushedNodes.size)
        assertEquals("node-change-1", transport.pushedNodes.single().getString("changeId"))
        assertEquals("n1", transport.pushedNodes.single().getString("id"))
        assertEquals(1L, transport.pushedNodes.single().getLong("baseRevision"))
        assertEquals(2L, transport.pushedNodes.single().getLong("revision"))
        assertEquals(SyncStatus.clean, dao.nodes.getValue("n1").syncStatus)
        assertEquals(2L, dao.nodes.getValue("n1").baseRevision)
        assertTrue(dao.outbox.isEmpty())
    }

    @Test
    fun rejectedNodePushKeepsTheOutboxForConflictRecovery() = runTest {
        val dao = FakeDao()
        dao.nodes["n1"] = localNode("n1", SyncStatus.dirty, baseRevision = 1, revision = 2)
        dao.outbox["node-change-1"] = pendingNodeOutbox("node-change-1", "n1", baseRevision = 1, revision = 2)
        val transport = FakeTransport(manifests = ArrayDeque(emptyList()), records = emptyMap())
        transport.nodeReceipts = listOf(
            SyncReceipt("n1", SyncReceipt.STATUS_REJECTED, "stale_revision", revision = 2)
        )
        val repository = SyncRepository(dao.proxy(), transport, store) { 100L }

        val report = repository.pushLocalChanges()

        assertEquals(0, report.uploadedNodes)
        assertEquals(1, report.rejected)
        assertEquals(SyncStatus.dirty, dao.nodes.getValue("n1").syncStatus)
        assertTrue("node-change-1" in dao.outbox)
    }

    @Test
    fun earlierNodeReceiptDoesNotClearALaterLocalEdit() = runTest {
        val dao = FakeDao()
        dao.nodes["n1"] = localNode("n1", SyncStatus.dirty, baseRevision = 1, revision = 3)
        dao.outbox["node-change-1"] = pendingNodeOutbox("node-change-1", "n1", baseRevision = 1, revision = 2)
        val transport = FakeTransport(manifests = ArrayDeque(emptyList()), records = emptyMap())
        val repository = SyncRepository(dao.proxy(), transport, store) { 100L }

        val report = repository.pushLocalChanges()

        assertEquals(1, report.uploadedNodes)
        assertEquals(SyncStatus.dirty, dao.nodes.getValue("n1").syncStatus)
        assertEquals(1L, dao.nodes.getValue("n1").baseRevision)
        assertTrue(dao.outbox.isEmpty())
    }

    @Test
    fun deletedNodePushUsesTombstoneAndMarksLocalDeletionSynced() = runTest {
        val dao = FakeDao()
        dao.nodes["n1"] = localNode("n1", SyncStatus.deleted, baseRevision = 1, revision = 2).copy(
            visibility = "trash",
            deletedAt = 50L
        )
        dao.outbox["node-change-1"] = pendingNodeOutbox(
            "node-change-1",
            "n1",
            baseRevision = 1,
            revision = 2,
            deletedAt = 50L,
            visibility = "trash"
        )
        val transport = FakeTransport(manifests = ArrayDeque(emptyList()), records = emptyMap())
        val repository = SyncRepository(dao.proxy(), transport, store) { 100L }

        val report = repository.pushLocalChanges()

        assertEquals(1, report.uploadedNodes)
        assertEquals(0, report.rejected)
        assertTrue(transport.pushedNodes.single().getBoolean("tombstone"))
        assertEquals(SyncStatus.clean, dao.nodes.getValue("n1").syncStatus)
        assertTrue(dao.outbox.isEmpty())
    }

    @Test
    fun acceptedQuizPushAcknowledgesOnlyTheMatchingCurrentRevision() = runTest {
        val dao = FakeDao()
        dao.quizzes["q1"] = localQuiz("q1").copy(
            prompt = "Phone prompt",
            answer = "Phone answer",
            explanation = "Phone explanation",
            revision = 2,
            baseRevision = 1,
            syncStatus = SyncStatus.dirty
        )
        dao.outbox["quiz-change-1"] = pendingQuizOutbox("quiz-change-1", dao.quizzes.getValue("q1"), baseRevision = 1)
        val transport = FakeTransport(manifests = ArrayDeque(emptyList()), records = emptyMap())
        val repository = SyncRepository(dao.proxy(), transport, store) { 100L }

        val report = repository.pushLocalChanges()

        assertEquals(1, report.uploadedQuizzes)
        assertEquals(0, report.rejected)
        assertEquals("quiz-change-1", transport.pushedQuizzes.single().getString("changeId"))
        assertEquals("q1", transport.pushedQuizzes.single().getString("id"))
        assertTrue(!transport.pushedQuizzes.single().has("title"))
        assertTrue(!transport.pushedQuizzes.single().has("summary"))
        assertTrue(!transport.pushedQuizzes.single().has("difficulty"))
        assertTrue(transport.pushedQuizzes.single().getString("body").contains("## Prompt"))
        assertEquals(SyncStatus.clean, dao.quizzes.getValue("q1").syncStatus)
        assertEquals(2L, dao.quizzes.getValue("q1").baseRevision)
        assertTrue(dao.outbox.isEmpty())
    }

    @Test
    fun deletedQuizPushUsesTombstoneAndMarksLocalDeletionSynced() = runTest {
        val dao = FakeDao()
        dao.quizzes["q1"] = localQuiz("q1").copy(
            revision = 2,
            baseRevision = 1,
            syncStatus = SyncStatus.deleted,
            visibility = "trash",
            deletedAt = 60L
        )
        dao.outbox["quiz-change-1"] = pendingQuizOutbox("quiz-change-1", dao.quizzes.getValue("q1"), baseRevision = 1)
        val transport = FakeTransport(manifests = ArrayDeque(emptyList()), records = emptyMap())
        val repository = SyncRepository(dao.proxy(), transport, store) { 100L }

        val report = repository.pushLocalChanges()

        assertEquals(1, report.uploadedQuizzes)
        assertEquals(0, report.rejected)
        assertTrue(transport.pushedQuizzes.single().getBoolean("tombstone"))
        assertEquals(SyncStatus.clean, dao.quizzes.getValue("q1").syncStatus)
        assertTrue(dao.outbox.isEmpty())
    }

    @Test
    fun rejectedQuizPushRetainsOutboxAndNewerLocalEdit() = runTest {
        val dao = FakeDao()
        dao.quizzes["q1"] = localQuiz("q1").copy(
            revision = 3,
            baseRevision = 1,
            syncStatus = SyncStatus.dirty
        )
        dao.outbox["quiz-change-1"] = pendingQuizOutbox(
            "quiz-change-1",
            dao.quizzes.getValue("q1").copy(revision = 2),
            baseRevision = 1
        )
        val transport = FakeTransport(manifests = ArrayDeque(emptyList()), records = emptyMap())
        transport.quizReceipts = listOf(
            SyncReceipt("q1", SyncReceipt.STATUS_REJECTED, "stale_revision", revision = 2)
        )
        val repository = SyncRepository(dao.proxy(), transport, store) { 100L }

        val report = repository.pushLocalChanges()

        assertEquals(0, report.uploadedQuizzes)
        assertEquals(1, report.rejected)
        assertEquals(SyncStatus.dirty, dao.quizzes.getValue("q1").syncStatus)
        assertTrue("quiz-change-1" in dao.outbox)
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

    private fun pendingNodeOutbox(
        changeId: String,
        nodeId: String,
        baseRevision: Long?,
        revision: Long,
        deletedAt: Long? = null,
        visibility: String = "core"
    ) = ReplicationOutboxEntity(
        changeId = changeId,
        commandId = "command-$changeId",
        aggregateType = "content.node",
        aggregateId = nodeId,
        operation = "update",
        baseRevision = baseRevision,
        newRevision = revision,
        domainSchemaVersion = 1,
        payloadJson = JSONObject()
            .put("schemaVersion", 1)
            .put("id", nodeId)
            .put("title", "Phone title")
            .put("markdownBody", "# Phone title\n\nEdited on phone.")
            .put("createdAt", 1)
            .put("updatedAt", 2)
            .put("revision", revision)
            .put("deletedAt", deletedAt ?: JSONObject.NULL)
            .put("areaId", "algorithms")
            .put("areaSlug", "algorithms")
            .put("track", "general")
            .put("order", 1000)
            .put("summary", "phone summary")
            .put("visibility", visibility)
            .put("isStarter", false)
            .put("isChecked", false)
            .toString(),
        payloadHash = "hash-$changeId",
        state = "pending",
        createdAt = 1L
    )

    private fun pendingQuizOutbox(
        changeId: String,
        quiz: QuizItemEntity,
        baseRevision: Long?
    ) = ReplicationOutboxEntity(
        changeId = changeId,
        commandId = "command-$changeId",
        aggregateType = "content.quiz",
        aggregateId = quiz.id,
        operation = "update",
        baseRevision = baseRevision,
        newRevision = quiz.revision,
        domainSchemaVersion = QuizOutboxCodec.SchemaVersion,
        payloadJson = QuizOutboxCodec.encode(quiz),
        payloadHash = "hash-$changeId",
        state = "pending",
        createdAt = 1L
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
        val pushedNodes = mutableListOf<JSONObject>()
        val pushedQuizzes = mutableListOf<JSONObject>()
        val pullRequests = mutableMapOf<String, MutableList<List<String>>>()
        var attemptsReceipts: List<SyncReceipt> = emptyList()
        var capturesReceipts: List<SyncReceipt> = emptyList()
        var questionsReceipts: List<SyncReceipt> = emptyList()
        var nodeReceipts: List<SyncReceipt> = emptyList()
        var quizReceipts: List<SyncReceipt> = emptyList()

        override suspend fun health(): SyncHealth = SyncHealth(1, healthServerId, 1)

        override suspend fun devicePolicy(): SyncDevicePolicy =
            SyncDevicePolicy(
                id = "device-test",
                name = "test",
                scopes = setOf("sync:read", "sync:push"),
                revokedAt = null
            )

        override suspend fun manifest(cursor: Long, serverId: String, scope: SyncScope): SyncManifest {
            manifestCursors += cursor
            return manifests.removeFirst()
        }

        override suspend fun pull(entityType: String, ids: List<String>, scope: SyncScope): List<SyncRecord> {
            pullRequests.getOrPut(entityType) { mutableListOf() } += ids
            return (records[entityType] ?: emptyList()).filter { it.id in ids }
        }

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

        override suspend fun pushNodes(items: List<JSONObject>): List<SyncReceipt> {
            pushedNodes += items
            return nodeReceipts.ifEmpty {
                items.map {
                    SyncReceipt(
                        id = it.getString("id"),
                        status = SyncReceipt.STATUS_ACCEPTED,
                        reason = null,
                        revision = it.getLong("revision")
                    )
                }
            }
        }

        override suspend fun pushQuizzes(items: List<JSONObject>): List<SyncReceipt> {
            pushedQuizzes += items
            return quizReceipts.ifEmpty {
                items.map {
                    SyncReceipt(
                        id = it.getString("id"),
                        status = SyncReceipt.STATUS_ACCEPTED,
                        reason = null,
                        revision = it.getLong("revision")
                    )
                }
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
        val outbox = linkedMapOf<String, ReplicationOutboxEntity>()

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
                    "getPendingNodeOutbox" -> outbox.values
                        .filter { it.aggregateType == "content.node" && it.state == "pending" }
                        .take(args[0] as Int)
                    "getPendingQuizOutbox" -> outbox.values
                        .filter { it.aggregateType == "content.quiz" && it.state == "pending" }
                        .take(args[0] as Int)
                    "acknowledgeNodeContentPush" -> {
                        val changeId = args[0] as String
                        val nodeId = args[1] as String
                        val localRevision = args[2] as Long
                        val serverRevision = args[3] as Long
                        nodes.computeIfPresent(nodeId) { _, node ->
                            if (node.revision == localRevision && node.syncStatus == SyncStatus.deleted && node.deletedAt != null) {
                                node.copy(syncStatus = SyncStatus.clean)
                            } else if (node.revision == localRevision && node.syncStatus == SyncStatus.dirty) {
                                node.copy(syncStatus = SyncStatus.clean, baseRevision = serverRevision)
                            } else {
                                node
                            }
                        }
                        outbox.remove(changeId)
                        Unit
                    }
                    "acknowledgeQuizContentPush" -> {
                        val changeId = args[0] as String
                        val quizId = args[1] as String
                        val localRevision = args[2] as Long
                        val serverRevision = args[3] as Long
                        quizzes.computeIfPresent(quizId) { _, quiz ->
                            if (quiz.revision == localRevision && quiz.syncStatus == SyncStatus.deleted && quiz.deletedAt != null) {
                                quiz.copy(syncStatus = SyncStatus.clean)
                            } else if (quiz.revision == localRevision && quiz.syncStatus == SyncStatus.dirty) {
                                quiz.copy(syncStatus = SyncStatus.clean, baseRevision = serverRevision)
                            } else {
                                quiz
                            }
                        }
                        outbox.remove(changeId)
                        Unit
                    }
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
