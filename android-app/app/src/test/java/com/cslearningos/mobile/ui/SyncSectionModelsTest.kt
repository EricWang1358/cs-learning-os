package com.cslearningos.mobile.ui

import com.cslearningos.mobile.feature.sync.SyncReport
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SyncSectionModelsTest {
    @Test
    fun pullReportArgsIncludeBiteCardsAsFirstClassStudyContent() {
        val report = SyncReport(
            pulledNodes = 2,
            pulledQuizzes = 3,
            pulledBiteCards = 5,
            conflicts = 1
        )

        assertArrayEquals(arrayOf(2, 3, 5, 1), syncPullReportArgs(report))
    }
}
