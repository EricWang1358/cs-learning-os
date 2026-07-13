package com.cslearningos.mobile.assistant.impl

import com.cslearningos.mobile.assistant.api.AssistantConversationPolicy
import com.cslearningos.mobile.assistant.api.AssistantEntryRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantEntryPolicyTest {
    @Test
    fun freshEntryResetsConversation() {
        assertTrue(
            AssistantEntryPolicy.shouldReset(
                AssistantEntryRequest(AssistantConversationPolicy.Fresh)
            )
        )
    }

    @Test
    fun preserveEntryKeepsConversation() {
        assertFalse(
            AssistantEntryPolicy.shouldReset(
                AssistantEntryRequest(AssistantConversationPolicy.Preserve)
            )
        )
    }
}
