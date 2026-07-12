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
    fun composerKeepsExactlyOneLocalSaveAction() {
        assertEquals(listOf(CaptureComposerAction.SaveLocal), captureComposerActions())
    }

    @Test
    fun inboxSlipCardsKeepExactlyThreeActions() {
        assertEquals(
            listOf(CaptureSlipAction.MakeNode, CaptureSlipAction.ImproveWithAi, CaptureSlipAction.Archive),
            captureSlipActions(slip(status = CaptureSlipStatus.inbox))
        )
    }

    @Test
    fun archivedSlipCardsExposeRestoreAndDeleteForeverOnly() {
        assertEquals(
            listOf(CaptureSlipAction.Restore, CaptureSlipAction.DeleteForever),
            captureSlipActions(slip(status = CaptureSlipStatus.archived))
        )
    }

    @Test
    fun normalInboxWorkflowStaysSavedLocally() {
        val model = buildCaptureSlipWorkflow(
            slip = slip(status = CaptureSlipStatus.inbox)
        )

        assertEquals(CaptureSlipWorkflowStage.Inbox, model.stage)
        assertEquals("Saved locally", model.statusLabel)
        assertEquals(false, model.actionsEnabled)
        assertEquals("", model.primaryActionLabel)
    }

    @Test
    fun archivedWorkflowExplainsThatTheSlipCanBeRestoredOrDeleted() {
        val model = buildCaptureSlipWorkflow(
            slip = slip(status = CaptureSlipStatus.archived)
        )

        assertEquals(CaptureSlipWorkflowStage.Archived, model.stage)
        assertEquals("Archived", model.statusLabel)
        assertTrue(model.detail.contains("restore", ignoreCase = true))
        assertTrue(model.detail.contains("delete", ignoreCase = true))
    }

    @Test
    fun legacyAiQueuedWorkflowNeedsAttentionAndUsesSingleImproveAction() {
        assertLegacyAiNeedsAttention(CaptureSlipStatus.ai_queued, "queued")
    }

    @Test
    fun legacyAiDraftingWorkflowNeedsAttentionAndUsesSingleImproveAction() {
        assertLegacyAiNeedsAttention(CaptureSlipStatus.ai_drafting, "drafting")
    }

    @Test
    fun legacyAiDraftReadyWorkflowNeedsAttentionAndUsesSingleImproveAction() {
        assertLegacyAiNeedsAttention(CaptureSlipStatus.ai_draft_ready, "draft ready")
    }

    private fun assertLegacyAiNeedsAttention(status: CaptureSlipStatus, priorStateLabel: String) {
        val model = buildCaptureSlipWorkflow(
            slip = slip(status = status)
        )

        assertEquals("LegacyAi", model.stage.name)
        assertEquals("Needs attention", model.statusLabel)
        assertEquals("Improve with AI", model.primaryActionLabel)
        assertEquals(false, model.actionsEnabled)
        assertEquals(
            listOf(CaptureSlipAction.ImproveWithAi),
            captureSlipActions(slip(status = status))
        )
        assertTrue(model.detail.contains(priorStateLabel, ignoreCase = true))
        assertTrue(model.detail.contains("prior AI", ignoreCase = true))
        assertTrue(model.detail.contains("single Improve with AI action"))
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
