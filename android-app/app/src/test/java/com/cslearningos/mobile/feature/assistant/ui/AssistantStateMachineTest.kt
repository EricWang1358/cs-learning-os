package com.cslearningos.mobile.feature.assistant.ui

import com.cslearningos.mobile.feature.assistant.domain.AssistantAgentInteraction
import com.cslearningos.mobile.feature.assistant.domain.AssistantEditTarget
import com.cslearningos.mobile.feature.assistant.domain.AssistantRequestMode
import com.cslearningos.mobile.feature.assistant.domain.AssistantReviewSession
import com.cslearningos.mobile.feature.assistant.domain.SelectContextItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantStateMachineTest {
    @Test
    fun requestModeUsesPendingDraftRequestBeforeClassifier() {
        val mode = AssistantUiState(
            pendingDraftRequest = "create note about binary tree"
        ).requestModeFor("what is a binary tree")

        assertEquals(AssistantRequestMode.Draft, mode)
    }

    @Test
    fun requestModeFollowsReviewSessionBeforeClassifier() {
        val mode = AssistantUiState(
            reviewSession = AssistantReviewSession.AwaitingTopic
        ).requestModeFor("virtual memory")

        assertEquals(AssistantRequestMode.ReviewQuestion, mode)
    }

    @Test
    fun showDraftChecklistClearsBusyAndMarksPendingRequest() {
        val responseId = "assistant-1"
        val interaction = AssistantAgentInteraction.SelectContext(
            title = "Select outputs",
            body = "Choose what to create",
            items = listOf(
                SelectContextItem(
                    id = "create_node",
                    title = "Create node",
                    body = "Editable note",
                    selected = true
                )
            ),
            confirmReplyPrefix = "Generate the editable draft for:"
        )
        val initial = AssistantUiState(
            messages = listOf(
                AssistantMessage(
                    id = responseId,
                    role = AssistantMessageRole.Assistant,
                    body = "",
                    isStreaming = true
                )
            ),
            isBusy = true
        )

        val updated = initial.showDraftChecklist(
            responseMessageId = responseId,
            request = "create note about binary tree",
            interaction = interaction,
            body = "Choose outputs"
        )

        val reply = updated.messages.single()
        assertFalse(updated.isBusy)
        assertEquals("create note about binary tree", updated.pendingDraftRequest)
        assertFalse(reply.isStreaming)
        assertTrue(reply.action is AssistantMessageAction.AgentInteraction)
    }

    @Test
    fun completeAssistantTurnMarksEditableRepliesForAutoOpen() {
        val responseId = "assistant-1"
        val initial = AssistantUiState(
            messages = listOf(
                AssistantMessage(
                    id = responseId,
                    role = AssistantMessageRole.Assistant,
                    body = "",
                    isStreaming = true
                )
            ),
            isBusy = true
        )

        val updated = initial.completeAssistantTurn(
            responseMessageId = responseId,
            visibleBody = "Draft ready",
            action = AssistantMessageAction.OpenEditableDraft(
                titleHint = "Binary tree",
                markdown = "# Binary tree"
            ),
            captureSuggestion = null,
            nextEditTarget = null,
            nextReviewSession = null
        )

        assertEquals(responseId, updated.pendingAutoOpenMessageId)
        assertFalse(updated.messages.single().isStreaming)
    }

    @Test
    fun restoreConversationClearsTransientAssistantFlags() {
        val target = AssistantEditTarget.Node(
            id = "node-1",
            revision = 3L,
            titleHint = "Binary tree",
            markdown = "# Binary tree",
            areaId = "algorithms"
        )
        val restored = AssistantUiState(
            isBusy = true,
            historyVisible = true,
            pendingDraftRequest = "create note",
            pendingAutoOpenMessageId = "assistant-1"
        ).restoreConversation(
            messages = listOf(
                AssistantMessage(
                    id = "assistant-2",
                    role = AssistantMessageRole.Assistant,
                    body = "Saved conversation"
                )
            ),
            editTarget = target
        )

        assertFalse(restored.isBusy)
        assertFalse(restored.historyVisible)
        assertNull(restored.pendingDraftRequest)
        assertNull(restored.pendingAutoOpenMessageId)
        assertEquals(target, restored.editTarget)
        assertEquals("Saved conversation", restored.messages.single().body)
    }
}
