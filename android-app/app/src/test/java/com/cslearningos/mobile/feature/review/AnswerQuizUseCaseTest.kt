package com.cslearningos.mobile.feature.review

import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.NodeFtsEntity
import com.cslearningos.mobile.data.ProcessedCommandEntity
import com.cslearningos.mobile.data.QuizFtsEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.QuizSource
import com.cslearningos.mobile.data.ReaderQuestionEntity
import com.cslearningos.mobile.data.ReviewAttemptEntity
import com.cslearningos.mobile.data.ReviewResult
import com.cslearningos.mobile.data.ReviewStateEntity
import com.cslearningos.mobile.data.ReplicationOutboxEntity
import com.cslearningos.mobile.domain.ReviewRating
import com.cslearningos.mobile.feature.review.data.ReviewRepository
import com.cslearningos.mobile.feature.assistant.data.AssistantConversationEntity
import com.cslearningos.mobile.feature.review.domain.AnswerQuizUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AnswerQuizUseCaseTest {
    @Test
    fun answerQuizDelegatesRatingToReviewRepository() = runTest {
        val dao = FakeReviewLearningDao(
            states = linkedMapOf(
                "quiz-1" to ReviewStateEntity(
                    quizId = "quiz-1",
                    ease = 2.5,
                    intervalDays = 0,
                    dueAt = 1_000L,
                    lastResult = ReviewResult.again,
                    attemptCount = 0,
                    updatedAt = 1_000L
                )
            )
        )
        val useCase = AnswerQuizUseCase(ReviewRepository(dao))

        useCase("quiz-1", ReviewRating.Good, now = 2_000L)

        val updatedState = dao.states.getValue("quiz-1")
        assertEquals(2.6, updatedState.ease, 0.0)
        assertEquals(1, updatedState.intervalDays)
        assertEquals(86_402_000L, updatedState.dueAt)
        assertEquals(ReviewResult.good, updatedState.lastResult)
        assertEquals(1, updatedState.attemptCount)
        assertEquals(2_000L, updatedState.updatedAt)
        assertEquals(1, dao.attempts.size)
        val attempt = dao.attempts.single()
        assertEquals("quiz-1", attempt.quizId)
        assertEquals(ReviewResult.good, attempt.result)
        assertEquals(2_000L, attempt.answeredAt)
        assertEquals(86_402_000L, attempt.scheduledDueAt)
        assertNotNull(attempt.id)
    }
}

private class FakeReviewLearningDao(
    val states: LinkedHashMap<String, ReviewStateEntity> = linkedMapOf(),
    val attempts: MutableList<ReviewAttemptEntity> = mutableListOf()
) : LearningDao {
    override fun observeAreas(): Flow<List<AreaEntity>> = flowOf(emptyList())
    override fun observeNodes(): Flow<List<LearningNodeEntity>> = flowOf(emptyList())
    override fun observeTrashNodes(): Flow<List<LearningNodeEntity>> = flowOf(emptyList())
    override fun observeQuizzes(): Flow<List<QuizItemEntity>> = flowOf(emptyList())
    override fun observeOpenReaderQuestions(): Flow<List<ReaderQuestionEntity>> = flowOf(emptyList())
    override fun observeInboxCaptureSlips(): Flow<List<CaptureSlipEntity>> = flowOf(emptyList())
    override fun observeArchivedCaptureSlips(): Flow<List<CaptureSlipEntity>> = flowOf(emptyList())
    override fun observeDueQuizzes(now: Long): Flow<List<QuizItemEntity>> = flowOf(emptyList())
    override suspend fun getArea(id: String): AreaEntity? = unsupported()
    override suspend fun getAreaBySlug(slug: String): AreaEntity? = unsupported()
    override suspend fun getNode(id: String): LearningNodeEntity? = unsupported()
    override suspend fun getQuiz(id: String): QuizItemEntity? = unsupported()
    override suspend fun getReaderQuestion(id: String): ReaderQuestionEntity? = unsupported()
    override suspend fun getCaptureSlip(id: String): CaptureSlipEntity? = unsupported()
    override suspend fun getActiveQuizzesForNode(nodeId: String): List<QuizItemEntity> = unsupported()
    override suspend fun getActiveReaderQuestionsForNode(nodeId: String): List<ReaderQuestionEntity> = unsupported()
    override suspend fun getReviewState(quizId: String): ReviewStateEntity? = states[quizId]
    override suspend fun getAllAreas(): List<AreaEntity> = unsupported()
    override suspend fun getAllNodes(): List<LearningNodeEntity> = unsupported()
    override suspend fun getStarterNodes(): List<LearningNodeEntity> = unsupported()
    override suspend fun getAllQuizzes(): List<QuizItemEntity> = unsupported()
    override suspend fun getStarterQuizzes(): List<QuizItemEntity> = unsupported()
    override suspend fun getAllReviewStates(): List<ReviewStateEntity> = unsupported()
    override suspend fun getAllAttempts(): List<ReviewAttemptEntity> = unsupported()
    override suspend fun getAllReaderQuestions(): List<ReaderQuestionEntity> = unsupported()
    override suspend fun getAllCaptureSlips(): List<CaptureSlipEntity> = unsupported()
    override suspend fun latestAssistantConversation(): AssistantConversationEntity? = unsupported()
    override suspend fun recentAssistantConversations(limit: Int): List<AssistantConversationEntity> = unsupported()
    override suspend fun getAssistantConversation(id: String): AssistantConversationEntity? = unsupported()
    override suspend fun deleteAssistantConversation(id: String) = unsupported()
    override suspend fun countActiveNodesInArea(areaId: String): Int = unsupported()
    override suspend fun getProcessedCommand(commandId: String): ProcessedCommandEntity? = unsupported()
    override suspend fun insertProcessedCommand(command: ProcessedCommandEntity) = unsupported()
    override suspend fun insertOutbox(item: ReplicationOutboxEntity) = unsupported()
    override suspend fun getOutboxForCommand(commandId: String): ReplicationOutboxEntity? = unsupported()
    override suspend fun upsertArea(area: AreaEntity) = unsupported()
    override suspend fun upsertNode(node: LearningNodeEntity) = unsupported()
    override suspend fun upsertQuiz(quiz: QuizItemEntity) = unsupported()
    override suspend fun upsertReaderQuestion(question: ReaderQuestionEntity) = unsupported()
    override suspend fun upsertCaptureSlip(slip: CaptureSlipEntity) = unsupported()
    override suspend fun upsertAssistantConversation(conversation: AssistantConversationEntity) = unsupported()
    override suspend fun upsertReviewState(state: ReviewStateEntity) {
        states[state.quizId] = state
    }
    override suspend fun insertAttempt(attempt: ReviewAttemptEntity) {
        attempts += attempt
    }
    override suspend fun upsertAreas(areas: List<AreaEntity>) = unsupported()
    override suspend fun upsertNodes(nodes: List<LearningNodeEntity>) = unsupported()
    override suspend fun upsertQuizzes(quizzes: List<QuizItemEntity>) = unsupported()
    override suspend fun upsertReviewStates(states: List<ReviewStateEntity>) = unsupported()
    override suspend fun insertAttempts(attempts: List<ReviewAttemptEntity>) = unsupported()
    override suspend fun upsertReaderQuestions(questions: List<ReaderQuestionEntity>) = unsupported()
    override suspend fun upsertCaptureSlips(slips: List<CaptureSlipEntity>) = unsupported()
    override suspend fun upsertNodeFts(entity: NodeFtsEntity) = unsupported()
    override suspend fun upsertQuizFts(entity: QuizFtsEntity) = unsupported()
    override suspend fun deleteNodeFts(nodeId: String) = unsupported()
    override suspend fun deleteQuizFts(quizId: String) = unsupported()
    override suspend fun deleteAllAreas() = unsupported()
    override suspend fun deleteAllNodes() = unsupported()
    override suspend fun deleteAllQuizzes() = unsupported()
    override suspend fun deleteAllReviewStates() = unsupported()
    override suspend fun deleteAllAttempts() = unsupported()
    override suspend fun deleteReviewStateForQuiz(quizId: String) = unsupported()
    override suspend fun deleteReviewAttemptsForQuiz(quizId: String) = unsupported()
    override suspend fun deleteAllReaderQuestions() = unsupported()
    override suspend fun deleteAllCaptureSlips() = unsupported()
    override suspend fun deleteAllNodeFts() = unsupported()
    override suspend fun deleteAllQuizFts() = unsupported()
    override suspend fun searchNodes(query: String): List<com.cslearningos.mobile.data.SearchResultEntity> = unsupported()
    override suspend fun searchQuizzes(query: String): List<com.cslearningos.mobile.data.SearchResultEntity> = unsupported()

    private fun unsupported(): Nothing = error("FakeReviewLearningDao method is outside this test scope")
}
