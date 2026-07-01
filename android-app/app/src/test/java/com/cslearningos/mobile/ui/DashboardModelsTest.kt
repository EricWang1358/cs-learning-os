package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.QuizSource
import com.cslearningos.mobile.data.ReaderQuestionEntity
import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.CaptureSlipStatus
import com.cslearningos.mobile.data.CaptureSlipType
import com.cslearningos.mobile.data.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardModelsTest {
    @Test
    fun primaryActionsPrioritizeSearchCaptureCreateAndReview() {
        val summary = buildDashboardSummary(LearningUiState())

        assertEquals(
            listOf(DashboardAction.Search, DashboardAction.Capture, DashboardAction.Create, DashboardAction.Review),
            summary.primaryActions
        )
        assertEquals(
            listOf(DashboardAction.Search, DashboardAction.Capture, DashboardAction.Create, DashboardAction.Review),
            summary.firstScreenActions
        )
    }

    @Test
    fun summaryDerivesTodayCountsAndRecentNode() {
        val older = node(id = "older", title = "Older note", updatedAt = 100L, lastReadAt = 200L)
        val recent = node(id = "recent", title = "Recent read", updatedAt = 300L, lastReadAt = 900L)
        val dueQuiz = quiz(id = "quiz-1")
        val openQuestion = readerQuestion(id = "rq-1", nodeId = recent.id)
        val captureSlip = captureSlip(id = "slip-1")

        val summary = buildDashboardSummary(
            LearningUiState(
                nodes = listOf(older, recent),
                dueQuizzes = listOf(dueQuiz),
                readerQuestions = listOf(openQuestion),
                captureSlips = listOf(captureSlip)
            )
        )

        assertEquals(2, summary.nodeCount)
        assertEquals(1, summary.dueReviewCount)
        assertEquals(1, summary.openQuestionCount)
        assertEquals(1, summary.captureSlipCount)
        assertEquals("Recent read", summary.recentNode?.title)
    }

    @Test
    fun summaryHandlesEmptyLibraryWithoutFakeRecentNode() {
        val summary = buildDashboardSummary(LearningUiState())

        assertEquals(0, summary.nodeCount)
        assertEquals(0, summary.dueReviewCount)
        assertEquals(0, summary.openQuestionCount)
        assertEquals(0, summary.captureSlipCount)
        assertNull(summary.recentNode)
    }

    private fun node(
        id: String,
        title: String,
        updatedAt: Long,
        lastReadAt: Long?
    ): LearningNodeEntity =
        LearningNodeEntity(
            id = id,
            title = title,
            markdownBody = "# $title",
            createdAt = 1L,
            updatedAt = updatedAt,
            lastReadAt = lastReadAt,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null
        )

    private fun quiz(id: String): QuizItemEntity =
        QuizItemEntity(
            id = id,
            nodeId = null,
            prompt = "What is cache locality?",
            answer = "Accessing nearby memory.",
            explanation = "",
            source = QuizSource.manual,
            sourceAnchor = null,
            createdAt = 1L,
            updatedAt = 1L,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null
        )

    private fun readerQuestion(id: String, nodeId: String): ReaderQuestionEntity =
        ReaderQuestionEntity(
            id = id,
            nodeId = nodeId,
            body = "What is unclear here?",
            createdAt = 1L,
            resolvedAt = null,
            syncStatus = SyncStatus.dirty,
            deletedAt = null
        )

    private fun captureSlip(id: String): CaptureSlipEntity =
        CaptureSlipEntity(
            id = id,
            body = "I want to learn virtual memory.",
            type = CaptureSlipType.concept_seed,
            topicHint = "Virtual memory",
            sourceLabel = null,
            linkedNodeId = null,
            status = CaptureSlipStatus.inbox,
            createdAt = 1L,
            updatedAt = 1L,
            revision = 1L,
            syncStatus = SyncStatus.dirty,
            deletedAt = null
        )
}
