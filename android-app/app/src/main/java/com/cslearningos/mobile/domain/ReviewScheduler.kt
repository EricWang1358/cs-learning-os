package com.cslearningos.mobile.domain

import java.time.Instant
import kotlin.math.max
import kotlin.math.roundToInt

enum class ReviewRating {
    Again,
    Hard,
    Good
}

data class ReviewScheduleInput(
    val ease: Double,
    val intervalDays: Int,
    val attemptCount: Int,
    val answeredAt: Instant
)

data class ReviewScheduleResult(
    val ease: Double,
    val intervalDays: Int,
    val dueAt: Instant
)

object ReviewScheduler {
    private const val DaySeconds = 86_400L
    private const val MinimumEase = 1.3

    fun next(input: ReviewScheduleInput, rating: ReviewRating): ReviewScheduleResult {
        val nextEase = when (rating) {
            ReviewRating.Again -> max(MinimumEase, input.ease - 0.2)
            ReviewRating.Hard -> input.ease
            ReviewRating.Good -> input.ease + 0.1
        }
        val nextInterval = when (rating) {
            ReviewRating.Again -> 0
            ReviewRating.Hard -> 1
            ReviewRating.Good -> {
                val base = if (input.intervalDays <= 0) 1 else input.intervalDays
                max(1, (base * nextEase).roundToInt())
            }
        }
        return ReviewScheduleResult(
            ease = nextEase,
            intervalDays = nextInterval,
            dueAt = input.answeredAt.plusSeconds(nextInterval * DaySeconds)
        )
    }
}
