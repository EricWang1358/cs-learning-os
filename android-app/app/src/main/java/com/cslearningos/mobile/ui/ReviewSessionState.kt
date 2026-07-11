package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.domain.ReviewRating

internal fun LearningUiState.showReviewScreen(): LearningUiState =
    copy(
        screen = AppScreen.Review,
        selectedQuiz = dueQuizzes.firstOrNull(),
        reviewedQuiz = null,
        reviewAreaId = null,
        reviewSetupVisible = true,
        quizAnswerVisible = false,
        message = null
    )

internal fun LearningUiState.startReviewSessionForArea(areaId: String?): LearningUiState {
    val due = dueQuizzes.filter { areaId == null || it.area == areaId }
    return copy(
        selectedQuiz = due.firstOrNull(),
        reviewAreaId = areaId,
        reviewSetupVisible = false,
        quizAnswerVisible = false
    )
}

internal fun LearningUiState.selectReviewSessionArea(areaId: String?): LearningUiState =
    copy(reviewAreaId = areaId, message = null)

internal fun LearningUiState.revealReviewAnswer(): LearningUiState =
    copy(quizAnswerVisible = true, message = null)

internal fun LearningUiState.afterReviewAnswered(
    quiz: QuizItemEntity,
    rating: ReviewRating,
    savedMessage: UiText
): LearningUiState =
    copy(
        reviewedQuiz = quiz,
        selectedQuiz = selectQuizAfterReview(
            currentQuiz = quiz,
            currentDueQuizzes = dueQuizzes.filter { reviewAreaId == null || it.area == reviewAreaId },
            rating = rating
        ),
        quizAnswerVisible = false,
        message = savedMessage
    )

internal fun LearningUiState.retryReviewedQuiz(): LearningUiState =
    copy(selectedQuiz = reviewedQuiz, reviewedQuiz = null, quizAnswerVisible = false)

internal fun LearningUiState.navigateReviewPrompt(step: Int): LearningUiState {
    val cards = reviewCardsForArea(quizzes = quizzes, areaId = reviewAreaId)
    val index = cards.indexOfFirst { it.id == reviewedQuiz?.id }.coerceAtLeast(0)
    return copy(
        selectedQuiz = if (cards.isEmpty()) null else cards[(index + step + cards.size) % cards.size],
        reviewedQuiz = null,
        quizAnswerVisible = false
    )
}
