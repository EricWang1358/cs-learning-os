package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.QuizSource
import com.cslearningos.mobile.data.SyncStatus
import com.cslearningos.mobile.domain.ReviewRating
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewQueueModelsTest {
    @Test
    fun reviewStageShowsExactlyOneLayer() {
        assertEquals(ReviewStage.Setup, reviewStage(setupVisible = true, answerVisible = false, reviewed = false))
        assertEquals(ReviewStage.Prompt, reviewStage(setupVisible = false, answerVisible = false, reviewed = false))
        assertEquals(ReviewStage.Rating, reviewStage(setupVisible = false, answerVisible = true, reviewed = false))
        assertEquals(ReviewStage.Explanation, reviewStage(setupVisible = false, answerVisible = false, reviewed = true))
    }

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

    @Test
    fun revealedReviewUsesOneEditMenuThenThreeRatings() {
        assertEquals(
            listOf(
                ReviewRevealedControl.EditMenu,
                ReviewRevealedControl.Again,
                ReviewRevealedControl.Hard,
                ReviewRevealedControl.Good
            ),
            revealedReviewControls()
        )
        assertEquals(
            listOf(ReviewEditMenuAction.ManualEdit, ReviewEditMenuAction.ImproveWithAi),
            reviewEditMenuActions()
        )
    }

    @Test
    fun reviewAreaSummariesExposeAllAreasAndScopedCounts() {
        val areas = listOf(
            area(id = "os", name = "OS", order = 1),
            area(id = "network", name = "Network", order = 2)
        )
        val due = listOf(
            quiz("q1", area = "os"),
            quiz("q2", area = "os"),
            quiz("q3", area = "network")
        )
        val all = due + quiz("q4", area = "network")

        assertEquals(
            listOf(
                ReviewAreaSummary(areaId = null, dueCount = 3, totalCount = 4),
                ReviewAreaSummary(areaId = "os", dueCount = 2, totalCount = 2),
                ReviewAreaSummary(areaId = "network", dueCount = 1, totalCount = 2)
            ),
            buildReviewAreaSummaries(areas = areas, dueQuizzes = due, quizzes = all)
        )
    }

    @Test
    fun reviewAreaSummariesUseAreaSlugSoImportedIdsStillMatchQuizCounts() {
        val areas = listOf(
            area(id = "systems-memory", slug = "systems", name = "Systems", order = 1),
            area(id = "network-basics", slug = "network", name = "Network", order = 2)
        )
        val due = listOf(
            quiz("q1", area = "systems"),
            quiz("q2", area = "network")
        )

        assertEquals(
            listOf(
                ReviewAreaSummary(areaId = null, dueCount = 2, totalCount = 2),
                ReviewAreaSummary(areaId = "systems", dueCount = 1, totalCount = 1),
                ReviewAreaSummary(areaId = "network", dueCount = 1, totalCount = 1)
            ),
            buildReviewAreaSummaries(areas = areas, dueQuizzes = due, quizzes = due)
        )
    }

    @Test
    fun reviewProgressUsesOnlyCardsFromSelectedArea() {
        val cards = reviewCardsForArea(
            quizzes = listOf(
                quiz("q1", area = "os"),
                quiz("q2", area = "os"),
                quiz("q3", area = "network")
            ),
            areaId = "os"
        )

        assertEquals(
            ReviewProgress(current = 2, total = 2),
            reviewProgress(selectedQuizId = "q2", cards = cards)
        )
    }

    @Test
    fun answeredReviewAdvancesWithinSelectedAreaOnly() {
        val current = quiz("q1", area = "os")
        val state = LearningUiState(
            dueQuizzes = listOf(
                current,
                quiz("q2", area = "network"),
                quiz("q3", area = "os")
            ),
            reviewAreaId = "os"
        )

        val next = state.afterReviewAnswered(
            quiz = current,
            rating = ReviewRating.Good,
            savedMessage = UiText.Dynamic("saved")
        ).selectedQuiz

        assertEquals("q3", next?.id)
    }

    private fun area(id: String, name: String, order: Int, slug: String = id) =
        AreaEntity(
            id = id,
            slug = slug,
            name = name,
            order = order,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            deletedAt = null
        )

    private fun quiz(id: String, area: String = "questions") =
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
            deletedAt = null,
            area = area
        )
}
