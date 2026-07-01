package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.QuizSource
import com.cslearningos.mobile.data.SyncStatus
import com.cslearningos.mobile.domain.ReviewRating
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewQueueModelsTest {
    @Test
    fun againKeepsCurrentQuizSelectedForSameSessionRetry() {
        val current = quiz("q1")
        val next = selectQuizAfterReview(
            currentQuiz = current,
            currentDueQuizzes = listOf(current, quiz("q2")),
            rating = ReviewRating.Again
        )

        assertEquals("q1", next?.id)
    }

    @Test
    fun hardAndGoodAdvanceToNextDueQuiz() {
        val current = quiz("q1")
        val next = selectQuizAfterReview(
            currentQuiz = current,
            currentDueQuizzes = listOf(current, quiz("q2")),
            rating = ReviewRating.Good
        )

        assertEquals("q2", next?.id)
    }

    private fun quiz(id: String) =
        QuizItemEntity(
            id = id,
            nodeId = null,
            prompt = "Prompt $id",
            answer = "Answer $id",
            explanation = "Explanation $id",
            source = QuizSource.manual,
            sourceAnchor = null,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null
        )
}
