package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.domain.ReviewRating

enum class ReviewRevealedControl {
    EditMenu,
    Again,
    Hard,
    Good
}

enum class ReviewEditMenuAction {
    ManualEdit,
    ImproveWithAi
}

enum class ReviewStage {
    Setup,
    Prompt,
    Rating,
    Explanation
}

data class ReviewAreaSummary(
    val areaId: String?,
    val dueCount: Int,
    val totalCount: Int
)

data class ReviewProgress(
    val current: Int,
    val total: Int
)

fun reviewStage(setupVisible: Boolean, answerVisible: Boolean, reviewed: Boolean): ReviewStage =
    when {
        setupVisible -> ReviewStage.Setup
        reviewed -> ReviewStage.Explanation
        answerVisible -> ReviewStage.Rating
        else -> ReviewStage.Prompt
    }

fun revealedReviewControls(): List<ReviewRevealedControl> =
    listOf(ReviewRevealedControl.EditMenu, ReviewRevealedControl.Again, ReviewRevealedControl.Hard, ReviewRevealedControl.Good)

fun reviewEditMenuActions(): List<ReviewEditMenuAction> =
    listOf(ReviewEditMenuAction.ManualEdit, ReviewEditMenuAction.ImproveWithAi)

fun buildReviewAreaSummaries(
    areas: List<AreaEntity>,
    dueQuizzes: List<QuizItemEntity>,
    quizzes: List<QuizItemEntity>
): List<ReviewAreaSummary> =
    listOf(
        ReviewAreaSummary(
            areaId = null,
            dueCount = dueQuizzes.size,
            totalCount = quizzes.size
        )
    ) + areas
        .filter { it.deletedAt == null }
        .sortedWith(compareBy<AreaEntity> { it.order }.thenBy { it.name.lowercase() })
        .map { area ->
            ReviewAreaSummary(
                areaId = area.slug,
                dueCount = dueQuizzes.count { it.area == area.slug },
                totalCount = quizzes.count { it.area == area.slug }
            )
        }

fun reviewCardsForArea(quizzes: List<QuizItemEntity>, areaId: String?): List<QuizItemEntity> =
    quizzes.filter { areaId == null || it.area == areaId }

fun reviewProgress(selectedQuizId: String?, cards: List<QuizItemEntity>): ReviewProgress {
    val total = cards.size
    if (total == 0) return ReviewProgress(current = 0, total = 0)
    val index = cards.indexOfFirst { it.id == selectedQuizId }
    return ReviewProgress(
        current = if (index >= 0) index + 1 else 1,
        total = total
    )
}

fun selectQuizAfterReview(
    currentQuiz: QuizItemEntity,
    currentDueQuizzes: List<QuizItemEntity>,
    rating: ReviewRating
): QuizItemEntity? {
    if (rating == ReviewRating.Again) return currentQuiz
    return currentDueQuizzes.firstOrNull { it.id != currentQuiz.id }
}
