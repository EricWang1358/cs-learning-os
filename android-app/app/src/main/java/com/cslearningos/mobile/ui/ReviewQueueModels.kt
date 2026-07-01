package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.domain.ReviewRating

fun selectQuizAfterReview(
    currentQuiz: QuizItemEntity,
    currentDueQuizzes: List<QuizItemEntity>,
    rating: ReviewRating
): QuizItemEntity? {
    if (rating == ReviewRating.Again) return currentQuiz
    return currentDueQuizzes.firstOrNull { it.id != currentQuiz.id }
}
