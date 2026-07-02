package com.cslearningos.mobile.feature.review.data

import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.QuizSource
import com.cslearningos.mobile.data.ReviewAttemptEntity
import com.cslearningos.mobile.data.ReviewResult
import com.cslearningos.mobile.data.ReviewStateEntity
import com.cslearningos.mobile.data.SyncStatus
import com.cslearningos.mobile.domain.ReviewRating
import com.cslearningos.mobile.domain.ReviewScheduleInput
import com.cslearningos.mobile.domain.ReviewScheduler
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import kotlin.math.absoluteValue

class ReviewRepository(
    private val dao: LearningDao
) {
    val quizzes: Flow<List<QuizItemEntity>> = dao.observeQuizzes()

    fun dueQuizzes(now: Long): Flow<List<QuizItemEntity>> = dao.observeDueQuizzes(now)

    suspend fun getQuiz(id: String): QuizItemEntity? = dao.getQuiz(id)

    suspend fun saveManualQuiz(
        nodeId: String?,
        prompt: String,
        answer: String,
        explanation: String,
        now: Long = System.currentTimeMillis()
    ): QuizItemEntity {
        val quiz = QuizItemEntity(
            id = UUID.randomUUID().toString(),
            nodeId = nodeId,
            prompt = prompt,
            answer = answer,
            explanation = explanation,
            source = QuizSource.manual,
            sourceAnchor = null,
            createdAt = now,
            updatedAt = now,
            revision = InitialRevision,
            syncStatus = SyncStatus.dirty,
            deletedAt = null,
            area = snapshotAreaForNode(nodeId),
            track = snapshotTrackForNode(nodeId),
            visibility = PracticeVisibility,
            isStarter = false
        )
        dao.upsertQuiz(quiz)
        dao.upsertReviewState(defaultReviewState(quiz.id, now))
        indexQuiz(quiz)
        return quiz
    }

    suspend fun answerQuiz(
        quizId: String,
        rating: ReviewRating,
        now: Long = System.currentTimeMillis()
    ) {
        val state = dao.getReviewState(quizId) ?: defaultReviewState(quizId, now)
        val result = ReviewScheduler.next(
            input = ReviewScheduleInput(
                ease = state.ease,
                intervalDays = state.intervalDays,
                attemptCount = state.attemptCount,
                answeredAt = Instant.ofEpochMilli(now)
            ),
            rating = rating
        )
        dao.upsertReviewState(
            state.copy(
                ease = result.ease,
                intervalDays = result.intervalDays,
                dueAt = result.dueAt.toEpochMilli(),
                lastResult = rating.toReviewResult(),
                attemptCount = state.attemptCount + AttemptIncrement,
                updatedAt = now
            )
        )
        dao.insertAttempt(
            ReviewAttemptEntity(
                id = UUID.randomUUID().toString(),
                quizId = quizId,
                result = rating.toReviewResult(),
                answeredAt = now,
                scheduledDueAt = result.dueAt.toEpochMilli()
            )
        )
    }

    private suspend fun indexQuiz(quiz: QuizItemEntity) {
        dao.deleteQuizFts(quiz.id)
        if (quiz.deletedAt == null && quiz.visibility != TrashVisibility) {
            dao.upsertQuizFts(
                com.cslearningos.mobile.data.QuizFtsEntity(
                    rowId = quiz.id.hashCode().absoluteValue.coerceAtLeast(MinimumStableRowId),
                    quizId = quiz.id,
                    prompt = quiz.prompt,
                    answer = quiz.answer
                )
            )
        }
    }

    private fun defaultReviewState(quizId: String, now: Long): ReviewStateEntity =
        ReviewStateEntity(
            quizId = quizId,
            ease = DefaultReviewEase,
            intervalDays = InitialIntervalDays,
            dueAt = now,
            lastResult = ReviewResult.again,
            attemptCount = InitialAttemptCount,
            updatedAt = now
        )

    private suspend fun snapshotAreaForNode(nodeId: String?): String =
        nodeId?.let { dao.getNode(it)?.area } ?: DefaultAreaSlug

    private suspend fun snapshotTrackForNode(nodeId: String?): String =
        nodeId?.let { dao.getNode(it)?.track } ?: DefaultTrack

    private fun ReviewRating.toReviewResult(): ReviewResult =
        when (this) {
            ReviewRating.Again -> ReviewResult.again
            ReviewRating.Hard -> ReviewResult.hard
            ReviewRating.Good -> ReviewResult.good
        }

    private companion object {
        const val AttemptIncrement = 1
        const val DefaultAreaSlug = "questions"
        const val DefaultReviewEase = 2.5
        const val DefaultTrack = "general"
        const val InitialAttemptCount = 0
        const val InitialIntervalDays = 0
        const val InitialRevision = 1L
        const val MinimumStableRowId = 1
        const val PracticeVisibility = "practice"
        const val TrashVisibility = "trash"
    }
}
