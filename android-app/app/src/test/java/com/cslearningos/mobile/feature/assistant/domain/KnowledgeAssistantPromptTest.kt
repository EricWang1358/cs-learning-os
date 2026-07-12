package com.cslearningos.mobile.feature.assistant.domain

import com.cslearningos.mobile.feature.assistant.ui.AssistantMessage
import com.cslearningos.mobile.feature.assistant.ui.AssistantMessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeAssistantPromptTest {
    @Test
    fun chatMessagesPreserveSameWindowConversationAsRoles() {
        val messages = buildKnowledgeAssistantChatMessages(
            listOf(
                AssistantMessage("u1", AssistantMessageRole.User, "创建笔记：秦始皇的历史功绩"),
                AssistantMessage("a1", AssistantMessageRole.Assistant, "Working draft updated. Open it to review or edit."),
                AssistantMessage("u2", AssistantMessageRole.User, "构建笔记")
            )
        )

        assertEquals(
            listOf("user", "assistant", "user"),
            messages.map { it.role }
        )
        assertEquals("创建笔记：秦始皇的历史功绩", messages[0].content)
        assertEquals("构建笔记", messages[2].content)
    }

    @Test
    fun chatMessagesCompactOlderTurnsBeforeRecentRoleMessages() {
        val longBody = "x".repeat(900)
        val history = (1..10).flatMap { index ->
            listOf(
                AssistantMessage("u$index", AssistantMessageRole.User, "older user $index $longBody"),
                AssistantMessage("a$index", AssistantMessageRole.Assistant, "older assistant $index $longBody")
            )
        }

        val messages = buildKnowledgeAssistantChatMessages(history)

        assertEquals("user", messages.first().role)
        assertTrue(messages.first().content.startsWith("Compressed earlier conversation:"))
        assertTrue(messages.first().content.length <= 1_000)
        assertTrue(messages.any { it.content.contains("older user 10") })
        assertTrue(messages.none { it.content.contains(longBody) })
    }
}
