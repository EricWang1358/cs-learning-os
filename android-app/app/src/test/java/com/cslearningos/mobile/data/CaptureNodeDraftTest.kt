package com.cslearningos.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureNodeDraftTest {
    @Test
    fun captureSlipBecomesStructuredNodeDraft() {
        val slip = CaptureSlipEntity(
            id = "slip-1",
            body = "I do not understand why TLB miss triggers a page table walk.",
            type = CaptureSlipType.unclear,
            topicHint = "Virtual Memory",
            sourceLabel = "CSAPP video",
            linkedNodeId = null,
            status = CaptureSlipStatus.inbox,
            createdAt = 1L,
            updatedAt = 1L,
            revision = 1L,
            syncStatus = SyncStatus.dirty,
            deletedAt = null
        )

        val draft = CaptureNodeDraft.fromSlip(slip, existingNodes = emptyList())

        assertEquals("Virtual Memory", draft.title)
        assertEquals(null, draft.suggestedNodeId)
        assertTrue(draft.markdownBody.contains("## Captured Question"))
        assertTrue(draft.markdownBody.contains("I do not understand why TLB miss triggers a page table walk."))
        assertTrue(draft.markdownBody.contains("Source: CSAPP video"))
        assertTrue(draft.markdownBody.contains("Type: unclear"))
    }

    @Test
    fun captureDraftSuggestsExistingNodeWhenTopicMatchesTitle() {
        val node = LearningNodeEntity(
            id = "node-vm",
            title = "Virtual Memory",
            markdownBody = "# Virtual Memory",
            createdAt = 1L,
            updatedAt = 1L,
            lastReadAt = null,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null
        )
        val slip = CaptureSlipEntity(
            id = "slip-1",
            body = "What is a page fault?",
            type = CaptureSlipType.question,
            topicHint = "virtual memory",
            sourceLabel = null,
            linkedNodeId = null,
            status = CaptureSlipStatus.inbox,
            createdAt = 1L,
            updatedAt = 1L,
            revision = 1L,
            syncStatus = SyncStatus.dirty,
            deletedAt = null
        )

        val draft = CaptureNodeDraft.fromSlip(slip, existingNodes = listOf(node))

        assertEquals("node-vm", draft.suggestedNodeId)
        assertTrue(draft.markdownBody.contains("Suggested existing node: Virtual Memory"))
    }
}
