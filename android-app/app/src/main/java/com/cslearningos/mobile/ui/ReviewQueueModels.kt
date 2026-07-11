package com.cslearningos.mobile.ui

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

fun revealedReviewControls(): List<ReviewRevealedControl> =
    listOf(ReviewRevealedControl.EditMenu, ReviewRevealedControl.Again, ReviewRevealedControl.Hard, ReviewRevealedControl.Good)

fun reviewEditMenuActions(): List<ReviewEditMenuAction> =
    listOf(ReviewEditMenuAction.ManualEdit, ReviewEditMenuAction.ImproveWithAi)

fun selectQuizAfterReview(
    currentQuiz: QuizItemEntity,
    currentDueQuizzes: List<QuizItemEntity>,
    rating: ReviewRating
): QuizItemEntity? {
    if (rating == ReviewRating.Again) return currentQuiz
    return currentDueQuizzes.firstOrNull { it.id != currentQuiz.id }
}
