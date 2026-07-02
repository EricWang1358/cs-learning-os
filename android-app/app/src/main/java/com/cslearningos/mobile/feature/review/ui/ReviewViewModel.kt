package com.cslearningos.mobile.feature.review.ui

import androidx.lifecycle.ViewModel
import com.cslearningos.mobile.feature.review.data.ReviewRepository
import com.cslearningos.mobile.feature.review.domain.AnswerQuizUseCase

class ReviewViewModel(
    private val repository: ReviewRepository,
    private val answerQuiz: AnswerQuizUseCase
) : ViewModel()
