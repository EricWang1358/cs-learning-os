package com.cslearningos.mobile.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningRepositoryPolicyTest {
    @Test
    fun markReadOnlyUpdatesReadingTrace() {
        val node = LearningNodeEntity(
            id = "node-1",
            title = "Cache locality",
            markdownBody = "# Cache locality",
            createdAt = 100L,
            updatedAt = 200L,
            lastReadAt = null,
            revision = 7L,
            syncStatus = SyncStatus.clean,
            deletedAt = null
        )

        val updated = node.withReadTrace(now = 900L)

        assertEquals(900L, updated.lastReadAt)
        assertEquals(200L, updated.updatedAt)
        assertEquals(7L, updated.revision)
        assertEquals(SyncStatus.clean, updated.syncStatus)
        assertNull(updated.deletedAt)
    }

    @Test
    fun backupRoundTripPreservesReaderQuestions() {
        val backup = LearningBackup(
            schemaVersion = BackupCodec.SchemaVersion,
            exportedAt = 1_000L,
            nodes = emptyList(),
            quizzes = emptyList(),
            reviewStates = emptyList(),
            attempts = emptyList(),
            readerQuestions = listOf(
                ReaderQuestionEntity(
                    id = "q-1",
                    nodeId = "node-1",
                    body = "Why does leaq not touch memory?",
                    createdAt = 700L,
                    resolvedAt = null,
                    syncStatus = SyncStatus.dirty,
                    deletedAt = null
                )
            )
        )

        val decoded = BackupCodec.decode(BackupCodec.encode(backup))

        assertEquals(1, decoded.readerQuestions.size)
        assertEquals("q-1", decoded.readerQuestions.single().id)
        assertEquals("node-1", decoded.readerQuestions.single().nodeId)
        assertEquals("Why does leaq not touch memory?", decoded.readerQuestions.single().body)
        assertNull(decoded.readerQuestions.single().resolvedAt)
    }

    @Test
    fun backupRoundTripPreservesCaptureSlips() {
        val backup = LearningBackup(
            schemaVersion = BackupCodec.SchemaVersion,
            exportedAt = 1_000L,
            nodes = emptyList(),
            quizzes = emptyList(),
            reviewStates = emptyList(),
            attempts = emptyList(),
            readerQuestions = emptyList(),
            captureSlips = listOf(
                CaptureSlipEntity(
                    id = "slip-1",
                    body = "I want to learn virtual memory.",
                    type = CaptureSlipType.concept_seed,
                    topicHint = "Virtual memory",
                    sourceLabel = "video",
                    linkedNodeId = null,
                    status = CaptureSlipStatus.inbox,
                    createdAt = 700L,
                    updatedAt = 700L,
                    revision = 1L,
                    syncStatus = SyncStatus.dirty,
                    deletedAt = null
                )
            )
        )

        val decoded = BackupCodec.decode(BackupCodec.encode(backup))

        assertEquals(1, decoded.captureSlips.size)
        assertEquals("slip-1", decoded.captureSlips.single().id)
        assertEquals("I want to learn virtual memory.", decoded.captureSlips.single().body)
        assertEquals(CaptureSlipType.concept_seed, decoded.captureSlips.single().type)
        assertEquals(CaptureSlipStatus.inbox, decoded.captureSlips.single().status)
    }

    @Test
    fun saveNodeTombstonesMarkdownQuizWhenSourceBlockIsRemoved() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        val original = repository.saveNode(
            id = "node-1",
            title = "Binary search",
            markdownBody = """
                # Binary search

                :::quiz id=mid-overflow
                question: Why avoid `(l + r) / 2`?
                answer: It can overflow before division.
                :::
            """.trimIndent(),
            now = 1_000L
        )

        repository.saveNode(
            id = original.id,
            title = original.title,
            markdownBody = "# Binary search\n\nUse `l + (r - l) / 2`.",
            now = 2_000L
        )

        val quiz = dao.quizzes.getValue("${original.id}:mid-overflow")
        assertEquals(2_000L, quiz.deletedAt)
        assertEquals(SyncStatus.deleted, quiz.syncStatus)
        assertTrue(dao.getActiveQuizzesForNode(original.id).isEmpty())
        assertTrue("Removed markdown quiz should leave the FTS index", dao.deletedQuizFtsIds.contains(quiz.id))
    }

    @Test
    fun anonymousMarkdownQuizRemovalDoesNotMoveReviewIdentityToAnotherCard() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        val original = repository.saveNode(
            id = null,
            title = "Anonymous cards",
            markdownBody = """
                :::quiz
                question: First card?
                answer: First answer.
                :::

                :::quiz
                question: Second card?
                answer: Second answer.
                :::
            """.trimIndent(),
            now = 1_000L
        )
        val firstId = dao.quizzes.values.single { it.prompt == "First card?" }.id
        val secondId = dao.quizzes.values.single { it.prompt == "Second card?" }.id

        repository.saveNode(
            id = original.id,
            title = original.title,
            markdownBody = """
                :::quiz
                question: Second card?
                answer: Second answer.
                :::
            """.trimIndent(),
            now = 2_000L
        )

        assertEquals(2_000L, dao.quizzes.getValue(firstId).deletedAt)
        assertNull(dao.quizzes.getValue(secondId).deletedAt)
        assertEquals("Second card?", dao.quizzes.getValue(secondId).prompt)
    }

    @Test
    fun seedStarterContentUpsertsNodesQuizzesAndReviewState() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        val pack = StarterContentPackage(
            nodes = listOf(
                LearningNodeEntity(
                    id = "starter:node:algorithms/binary-search",
                    title = "Binary Search",
                    markdownBody = "# Binary Search",
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                    lastReadAt = null,
                    revision = 1L,
                    syncStatus = SyncStatus.clean,
                    deletedAt = null
                )
            ),
            quizzes = listOf(
                QuizItemEntity(
                    id = "starter:quiz:gdb",
                    nodeId = null,
                    prompt = "What does stepi do?",
                    answer = "One instruction.",
                    explanation = "",
                    source = QuizSource.manual,
                    sourceAnchor = "quizzes/gdb.md",
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                    revision = 1L,
                    syncStatus = SyncStatus.clean,
                    deletedAt = null
                )
            ),
            reviewStates = listOf(
                ReviewStateEntity(
                    quizId = "starter:quiz:gdb",
                    ease = 2.5,
                    intervalDays = 0,
                    dueAt = 1_000L,
                    lastResult = ReviewResult.again,
                    attemptCount = 0,
                    updatedAt = 1_000L
                )
            )
        )

        repository.seedStarterContent(pack)

        assertEquals("Binary Search", dao.nodes.getValue("starter:node:algorithms/binary-search").title)
        assertEquals("What does stepi do?", dao.quizzes.getValue("starter:quiz:gdb").prompt)
        assertEquals(1_000L, dao.reviewStates.getValue("starter:quiz:gdb").dueAt)
    }

    @Test
    fun convertedCaptureSlipLinksToSavedNodeAndLeavesInbox() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        val slip = CaptureSlipEntity(
            id = "slip-1",
            body = "What is a page fault?",
            type = CaptureSlipType.question,
            topicHint = "Virtual Memory",
            sourceLabel = null,
            linkedNodeId = null,
            status = CaptureSlipStatus.inbox,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            revision = 1L,
            syncStatus = SyncStatus.dirty,
            deletedAt = null
        )
        dao.captureSlips[slip.id] = slip

        repository.markCaptureSlipConverted(slipId = slip.id, nodeId = "node-1", now = 2_000L)

        val updated = dao.captureSlips.getValue(slip.id)
        assertEquals(CaptureSlipStatus.converted, updated.status)
        assertEquals("node-1", updated.linkedNodeId)
        assertEquals(2_000L, updated.updatedAt)
        assertEquals(2L, updated.revision)
    }
}

private class FakeLearningDao : LearningDao {
    val nodes = linkedMapOf<String, LearningNodeEntity>()
    val quizzes = linkedMapOf<String, QuizItemEntity>()
    val reviewStates = linkedMapOf<String, ReviewStateEntity>()
    val captureSlips = linkedMapOf<String, CaptureSlipEntity>()
    val deletedQuizFtsIds = mutableListOf<String>()

    override fun observeNodes(): Flow<List<LearningNodeEntity>> = flowOf(nodes.values.toList())
    override fun observeQuizzes(): Flow<List<QuizItemEntity>> = flowOf(quizzes.values.toList())
    override fun observeOpenReaderQuestions(): Flow<List<ReaderQuestionEntity>> = flowOf(emptyList())
    override fun observeInboxCaptureSlips(): Flow<List<CaptureSlipEntity>> = flowOf(emptyList())
    override fun observeDueQuizzes(now: Long): Flow<List<QuizItemEntity>> = flowOf(emptyList())
    override suspend fun getNode(id: String): LearningNodeEntity? = nodes[id]
    override suspend fun getQuiz(id: String): QuizItemEntity? = quizzes[id]
    override suspend fun getReaderQuestion(id: String): ReaderQuestionEntity? = unsupported()
    override suspend fun getCaptureSlip(id: String): CaptureSlipEntity? = captureSlips[id]
    override suspend fun getActiveQuizzesForNode(nodeId: String): List<QuizItemEntity> =
        quizzes.values.filter { it.nodeId == nodeId && it.deletedAt == null }
    override suspend fun getActiveReaderQuestionsForNode(nodeId: String): List<ReaderQuestionEntity> = emptyList()
    override suspend fun getReviewState(quizId: String): ReviewStateEntity? = reviewStates[quizId]
    override suspend fun getAllNodes(): List<LearningNodeEntity> = nodes.values.toList()
    override suspend fun getAllQuizzes(): List<QuizItemEntity> = quizzes.values.toList()
    override suspend fun getAllReviewStates(): List<ReviewStateEntity> = emptyList()
    override suspend fun getAllAttempts(): List<ReviewAttemptEntity> = emptyList()
    override suspend fun getAllReaderQuestions(): List<ReaderQuestionEntity> = emptyList()
    override suspend fun getAllCaptureSlips(): List<CaptureSlipEntity> = captureSlips.values.toList()
    override suspend fun upsertNode(node: LearningNodeEntity) {
        nodes[node.id] = node
    }
    override suspend fun upsertQuiz(quiz: QuizItemEntity) {
        quizzes[quiz.id] = quiz
    }
    override suspend fun upsertReaderQuestion(question: ReaderQuestionEntity) = unsupported()
    override suspend fun upsertCaptureSlip(slip: CaptureSlipEntity) {
        captureSlips[slip.id] = slip
    }
    override suspend fun upsertReviewState(state: ReviewStateEntity) {
        reviewStates[state.quizId] = state
    }
    override suspend fun insertAttempt(attempt: ReviewAttemptEntity) = unsupported()
    override suspend fun upsertNodes(nodes: List<LearningNodeEntity>) {
        nodes.forEach { this.nodes[it.id] = it }
    }
    override suspend fun upsertQuizzes(quizzes: List<QuizItemEntity>) {
        quizzes.forEach { this.quizzes[it.id] = it }
    }
    override suspend fun upsertReviewStates(states: List<ReviewStateEntity>) {
        states.forEach { this.reviewStates[it.quizId] = it }
    }
    override suspend fun insertAttempts(attempts: List<ReviewAttemptEntity>) = unsupported()
    override suspend fun upsertReaderQuestions(questions: List<ReaderQuestionEntity>) = unsupported()
    override suspend fun upsertCaptureSlips(slips: List<CaptureSlipEntity>) {
        slips.forEach { captureSlips[it.id] = it }
    }
    override suspend fun upsertNodeFts(entity: NodeFtsEntity) = Unit
    override suspend fun upsertQuizFts(entity: QuizFtsEntity) = Unit
    override suspend fun deleteNodeFts(nodeId: String) = Unit
    override suspend fun deleteQuizFts(quizId: String) {
        deletedQuizFtsIds += quizId
    }
    override suspend fun deleteAllNodes() = unsupported()
    override suspend fun deleteAllQuizzes() = unsupported()
    override suspend fun deleteAllReviewStates() = unsupported()
    override suspend fun deleteAllAttempts() = unsupported()
    override suspend fun deleteAllReaderQuestions() = unsupported()
    override suspend fun deleteAllCaptureSlips() = unsupported()
    override suspend fun deleteAllNodeFts() = unsupported()
    override suspend fun deleteAllQuizFts() = unsupported()
    override suspend fun searchNodes(query: String): List<SearchResultEntity> = emptyList()
    override suspend fun searchQuizzes(query: String): List<SearchResultEntity> = emptyList()

    private fun unsupported(): Nothing = error("FakeLearningDao method is outside this test scope")
}
