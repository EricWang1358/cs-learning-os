package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.CaptureSlipStatus
import com.cslearningos.mobile.data.CaptureSlipType
import com.cslearningos.mobile.data.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureEditorStateTest {
    @Test
    fun existingCaptureEditorRetainsIdentityAndPrefillsFields() {
        val slip = CaptureSlipEntity(
            id = "slip-1",
            body = "Why does a page fault block?",
            type = CaptureSlipType.question,
            topicHint = "Virtual memory",
            sourceLabel = null,
            linkedNodeId = "node-1",
            status = CaptureSlipStatus.ai_draft_ready,
            createdAt = 1L,
            updatedAt = 2L,
            revision = 3L,
            syncStatus = SyncStatus.dirty,
            deletedAt = null
        )

        val editor = LearningUiState(
            captureDraft = "Stale draft",
            captureEditorId = "stale-slip",
            pendingAiDraftSlipId = "pending-slip"
        ).forExistingCaptureEditor(slip)

        assertEquals(AppScreen.Capture, editor.screen)
        assertEquals(slip.id, editor.captureEditorId)
        assertEquals(slip.body, editor.captureDraft)
        assertEquals(slip.topicHint, editor.captureTopicHint)
        assertEquals("", editor.captureSourceLabel)
        assertEquals(slip.type, editor.captureType)
        assertNull(editor.pendingAiDraftSlipId)
    }

    @Test
    fun clearedCaptureEditorRemovesIdentityAndDraftFields() {
        val cleared = LearningUiState(
            captureEditorId = "slip-1",
            captureDraft = "Capture",
            captureTopicHint = "Topic",
            captureSourceLabel = "Source"
        ).clearedCaptureEditor()

        assertNull(cleared.captureEditorId)
        assertEquals("", cleared.captureDraft)
        assertEquals("", cleared.captureTopicHint)
        assertEquals("", cleared.captureSourceLabel)
    }
}
