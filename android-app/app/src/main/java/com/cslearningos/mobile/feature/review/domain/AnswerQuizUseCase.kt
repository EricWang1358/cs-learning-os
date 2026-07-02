package com.cslearningos.mobile.feature.review.domain

import com.cslearningos.mobile.domain.ReviewRating
import com.cslearningos.mobile.feature.review.data.ReviewRepository

class AnswerQuizUseCase(
    private val repository: ReviewRepository
) {
    suspend operator fun invoke(
        quizId: String,
        rating: ReviewRating,
        now: Long = System.currentTimeMillis()
    ) {
        repository.answerQuiz(quizId = quizId, rating = rating, now = now)
    }
}
