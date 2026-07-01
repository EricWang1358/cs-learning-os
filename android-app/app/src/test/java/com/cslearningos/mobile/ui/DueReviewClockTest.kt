package com.cslearningos.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DueReviewClockTest {
    @Test
    fun dueReviewClockRefreshesEveryMinute() {
        assertEquals(60_000L, DueReviewRefreshIntervalMillis)
    }
}
