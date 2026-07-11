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
    fun primaryActionsUseOneFourActionStripInProductPathOrder() {
        val summary = buildDashboardSummary(LearningUiState())

        assertEquals(
            listOf(DashboardAction.Capture, DashboardAction.Create, DashboardAction.Review, DashboardAction.Search),
            summary.primaryActions
        )
        assertEquals(
            summary.primaryActions,
            summary.firstScreenActions
        )
    }

    @Test
    fun summaryOnlyFeedsContinueReadingAfterActionStrip() {
        val older = node(id = "older", title = "Older note", updatedAt = 100L, lastReadAt = 200L)
        val recent = node(id = "recent", title = "Recent read", updatedAt = 300L, lastReadAt = 900L)

        val summary = buildDashboardSummary(
            LearningUiState(
                nodes = listOf(older, recent),
                dueQuizzes = listOf(quiz(id = "quiz-1")),
                readerQuestions = listOf(readerQuestion(id = "rq-1", nodeId = recent.id)),
                captureSlips = listOf(captureSlip(id = "slip-1"))
            )
        )

        assertEquals("Recent read", summary.recentNode?.title)
    }

    @Test
    fun summaryHandlesEmptyLibraryWithoutFakeRecentNode() {
        val summary = buildDashboardSummary(LearningUiState())

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
