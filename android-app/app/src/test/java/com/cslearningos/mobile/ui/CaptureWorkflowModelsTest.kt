package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.CaptureSlipStatus
import com.cslearningos.mobile.data.CaptureSlipType
import com.cslearningos.mobile.data.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureWorkflowModelsTest {
    @Test
    fun aiQueuedSlipShowsExplicitStatusAndGenerateAction() {
        val model = buildCaptureSlipWorkflow(
            slip = slip(status = CaptureSlipStatus.ai_queued),
            pendingAiDraftSlipId = "slip-1",
            aiBusy = false,
            aiConfigured = true
        )

        assertEquals(CaptureSlipWorkflowStage.QueuedForAi, model.stage)
        assertEquals("Queued for AI", model.statusLabel)
        assertTrue(model.primaryActionLabel.contains("Generate"))
        assertTrue(model.detail.contains("editable Markdown draft"))
    }

    @Test
    fun draftingSlipDisablesActionsAndExplainsCurrentWork() {
        val model = buildCaptureSlipWorkflow(
            slip = slip(status = CaptureSlipStatus.ai_drafting),
            pendingAiDraftSlipId = "slip-1",
            aiBusy = true,
            aiConfigured = true
        )

        assertEquals(CaptureSlipWorkflowStage.Drafting, model.stage)
        assertEquals("Drafting", model.statusLabel)
        assertEquals(false, model.actionsEnabled)
        assertTrue(model.detail.contains("model is working"))
    }

    private fun slip(status: CaptureSlipStatus) =
        CaptureSlipEntity(
            id = "slip-1",
            body = "I do not understand virtual memory page faults.",
            type = CaptureSlipType.unclear,
            topicHint = "Virtual Memory",
            sourceLabel = "lecture video",
            linkedNodeId = null,
            status = status,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            revision = 1L,
            syncStatus = SyncStatus.dirty,
            deletedAt = null
        )
}
