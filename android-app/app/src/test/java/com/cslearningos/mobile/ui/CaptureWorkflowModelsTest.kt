package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.CaptureSlipStatus
import com.cslearningos.mobile.data.CaptureSlipType
import com.cslearningos.mobile.data.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureWorkflowModelsTest {
    @Test
    fun composerKeepsExactlyOneLocalSaveAction() {
        assertEquals(listOf(CaptureComposerAction.SaveLocal), captureComposerActions())
    }

    @Test
    fun savedSlipCardsKeepExactlyThreeActions() {
        assertEquals(
            listOf(CaptureSlipAction.MakeNode, CaptureSlipAction.ImproveWithAi, CaptureSlipAction.Archive),
            captureSlipActions()
        )
    }

    @Test
    fun savedSlipWorkflowStaysLocalInboxWithoutVisibleAiDraftFlow() {
        val model = buildCaptureSlipWorkflow(
            slip = slip(status = CaptureSlipStatus.ai_queued),
            pendingAiDraftSlipId = "slip-1",
            aiBusy = false,
            aiConfigured = true
        )

        assertEquals(CaptureSlipWorkflowStage.Inbox, model.stage)
        assertEquals("Saved locally", model.statusLabel)
        assertEquals(false, model.actionsEnabled)
        assertEquals("", model.primaryActionLabel)
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
