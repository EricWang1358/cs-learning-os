package com.cslearningos.mobile.feature.assistant.data

import com.cslearningos.mobile.feature.assistant.domain.AssistantConversation
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversationMessage
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversationRole
import com.cslearningos.mobile.feature.assistant.domain.AssistantEditTarget
import com.cslearningos.mobile.feature.assistant.ui.AssistantMessage
import com.cslearningos.mobile.feature.assistant.ui.AssistantMessageAction
import com.cslearningos.mobile.feature.assistant.ui.AssistantMessageRole
import com.cslearningos.mobile.feature.assistant.ui.toStoredMessage
import com.cslearningos.mobile.feature.assistant.ui.toUiMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantConversationCodecTest {
    @Test
    fun roundTripsDisplayableMessagesWithoutRestoringTransientActions() {
        val conversation = AssistantConversation(
            id = "conversation-1",
            messages = listOf(
                AssistantConversationMessage(
                    role = AssistantConversationRole.User,
                    body = "Explain virtual memory"
                ),
                AssistantConversationMessage(
                    role = AssistantConversationRole.Assistant,
                    body = "Virtual memory separates addresses from physical memory."
                )
            ),
            editTarget = AssistantEditTarget.Node(
                id = "node-42",
                revision = 5L,
                titleHint = "Virtual memory",
                markdown = "# Virtual memory",
                areaId = "systems"
            )
        )

        val restored = AssistantConversationCodec.decode(AssistantConversationCodec.encode(conversation))

        assertEquals(conversation, restored)
    }

    @Test
    fun roundTripsPendingCaptureEditTarget() {
        val conversation = AssistantConversation(
            id = "conversation-2",
            messages = emptyList(),
            editTarget = AssistantEditTarget.Capture(
                id = "capture-1",
                revision = 3L,
                body = "Explain the TLB miss.",
                topicHint = "Virtual memory",
                sourceLabel = "Lecture 5",
                type = com.cslearningos.mobile.data.CaptureSlipType.question
            )
        )

        val restored = AssistantConversationCodec.decode(AssistantConversationCodec.encode(conversation))

        assertEquals(conversation.editTarget, restored.editTarget)
    }

    @Test
    fun roundTripsPendingQuizEditTarget() {
        val conversation = AssistantConversation(
            id = "conversation-quiz-target",
            messages = emptyList(),
            editTarget = AssistantEditTarget.Quiz(
                id = "quiz-1",
                revision = 9L,
                nodeId = "node-1",
                prompt = "What does a page table map?",
                answer = "Virtual pages to physical frames.",
                explanation = "The page table translates virtual page numbers into physical frame numbers."
            )
        )

        val restored = AssistantConversationCodec.decode(AssistantConversationCodec.encode(conversation))

        assertEquals(conversation.editTarget, restored.editTarget)
    }

    @Test
    fun roundTripsAssistantConfirmationActionThroughStoredMessages() {
        val message = AssistantMessage(
            id = "reply",
            role = AssistantMessageRole.Assistant,
            body = "Draft ready.",
            action = AssistantMessageAction.OpenEditableQuizDraft(
                quizId = "quiz-1",
                nodeId = "node-1",
                prompt = "Prompt",
                answer = "Answer",
                explanation = "Explanation",
                expectedRevision = 7L
            )
        )
        val conversation = AssistantConversation(
            id = "conversation-3",
            messages = listOf(message.toStoredMessage())
        )

        val restored = AssistantConversationCodec.decode(AssistantConversationCodec.encode(conversation))
            .messages
            .single()
            .toUiMessage()

        assertEquals(message.action, restored.action)
    }

    @Test
    fun roundTripsNewQuizDraftActionThroughStoredMessages() {
        val message = AssistantMessage(
            id = "reply",
            role = AssistantMessageRole.Assistant,
            body = "Review-card draft ready.",
            action = AssistantMessageAction.OpenNewQuizDraft(
                prompt = "What does virtual memory separate?",
                answer = "Virtual addresses from physical memory.",
                explanation = "This is the core abstraction behind paging."
            )
        )
        val conversation = AssistantConversation(
            id = "conversation-new-quiz",
            messages = listOf(message.toStoredMessage())
        )

        val restored = AssistantConversationCodec.decode(AssistantConversationCodec.encode(conversation))
            .messages
            .single()
            .toUiMessage()

        assertEquals(message.action, restored.action)
    }

    @Test
    fun roundTripsNewNodeDraftActionWithNullNodeIdThroughStoredMessages() {
        val message = AssistantMessage(
            id = "reply",
            role = AssistantMessageRole.Assistant,
            body = "Draft ready.",
            action = AssistantMessageAction.OpenEditableDraft(
                titleHint = "New node",
                markdown = "# New node\n\nBody",
                areaId = "systems",
                nodeId = null,
                expectedRevision = 0L,
                placementReason = "Fits Systems"
            )
        )
        val conversation = AssistantConversation(
            id = "conversation-new-node",
            messages = listOf(message.toStoredMessage())
        )

        val restored = AssistantConversationCodec.decode(AssistantConversationCodec.encode(conversation))
            .messages
            .single()
            .toUiMessage()

        assertEquals(message.action, restored.action)
    }

    @Test
    fun rejectsMalformedNewNodeDraftNodeIdFields() {
        listOf("\"\"", "42").forEach { malformedNodeId ->
            val restored = AssistantConversationCodec.decode(
                """
                {
                  "id": "conversation-malformed-action",
                  "messages": [
                    {
                      "role": "Assistant",
                      "body": "Draft ready.",
                      "action": {
                        "kind": "open_node_draft",
                        "node_id": $malformedNodeId,
                        "expected_revision": 0,
                        "title_hint": "New node",
                        "markdown": "# New node"
                      }
                    }
                  ]
                }
                """.trimIndent()
            )

            assertNull(restored.messages.single().action)
        }
    }

    @Test
    fun legacyWorkingDraftWithoutNodeIdRestoresAsNewNodeEditTarget() {
        val restored = AssistantConversationCodec.decode(
            """
            {
              "id": "conversation-legacy",
              "messages": [],
              "working_draft": {
                "title_hint": "Draft title",
                "markdown": "# Draft title\n\nBody",
                "area_id": "systems",
                "placement_reason": "Fits Systems"
              }
            }
            """.trimIndent()
        )

        val target = restored.editTarget as AssistantEditTarget.Node
        assertNull(target.id)
        assertEquals(0L, target.revision)
        assertEquals("Draft title", target.titleHint)
        assertEquals("# Draft title\n\nBody", target.markdown)
        assertEquals("systems", target.areaId)
    }
}
