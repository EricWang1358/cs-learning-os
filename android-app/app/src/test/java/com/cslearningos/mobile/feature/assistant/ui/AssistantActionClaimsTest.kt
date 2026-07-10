package com.cslearningos.mobile.feature.assistant.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantActionClaimsTest {
    @Test
    fun captureSaveActionCanOnlyBeClaimedOnce() {
        val messages = listOf(
            AssistantMessage(
                id = "reply",
                role = AssistantMessageRole.Assistant,
                body = "A useful explanation",
                action = AssistantMessageAction.SaveCapture("A useful explanation")
            )
        )

        val firstClaim = claimCaptureSaveAction(messages, "reply")
        val secondClaim = claimCaptureSaveAction(firstClaim?.messages.orEmpty(), "reply")

        assertNotNull(firstClaim)
        assertEquals("A useful explanation", firstClaim?.action?.body)
        assertNull(secondClaim)
    }
}
