package com.cslearningos.mobile.feature.assistant.data

import com.cslearningos.mobile.feature.assistant.domain.AssistantConversation
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversationMessage
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversationRole
import com.cslearningos.mobile.feature.assistant.domain.AssistantWorkingDraft
import org.junit.Assert.assertEquals
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
            workingDraft = AssistantWorkingDraft(
                titleHint = "Virtual memory",
                markdown = "# Virtual memory",
                areaId = "systems",
                nodeId = "node-42"
            )
        )

        val restored = AssistantConversationCodec.decode(AssistantConversationCodec.encode(conversation))

        assertEquals(conversation, restored)
    }
}
