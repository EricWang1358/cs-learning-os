package com.cslearningos.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReviewSchedulerTest {
    private val now = Instant.parse("2026-07-01T00:00:00Z")

    @Test
    fun againKeepsCardDueTodayAndLowersEase() {
        val result = ReviewScheduler.next(
            input = ReviewScheduleInput(
                ease = 2.5,
                intervalDays = 5,
                attemptCount = 3,
                answeredAt = now
            ),
            rating = ReviewRating.Again
        )

        assertEquals(2.3, result.ease, 0.001)
        assertEquals(0, result.intervalDays)
        assertEquals(now, result.dueAt)
    }

    @Test
    fun hardSchedulesTomorrowAndKeepsEaseStable() {
        val result = ReviewScheduler.next(
            input = ReviewScheduleInput(
                ease = 2.5,
                intervalDays = 4,
                attemptCount = 2,
                answeredAt = now
            ),
            rating = ReviewRating.Hard
        )

        assertEquals(2.5, result.ease, 0.001)
        assertEquals(1, result.intervalDays)
        assertEquals(now.plusSeconds(86_400), result.dueAt)
    }

    @Test
    fun goodIncreasesEaseAndGrowsInterval() {
        val result = ReviewScheduler.next(
            input = ReviewScheduleInput(
                ease = 2.5,
                intervalDays = 4,
                attemptCount = 2,
                answeredAt = now
            ),
            rating = ReviewRating.Good
        )

        assertEquals(2.6, result.ease, 0.001)
        assertTrue(result.intervalDays >= 10)
        assertEquals(now.plusSeconds(result.intervalDays * 86_400L), result.dueAt)
    }
}
