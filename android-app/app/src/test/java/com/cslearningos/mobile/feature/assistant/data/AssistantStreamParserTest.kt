package com.cslearningos.mobile.feature.assistant.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantStreamParserTest {
    @Test
    fun parsesContentDeltaFromOpenAiServerSentEvent() {
        assertEquals(
            "virtual memory",
            assistantStreamDelta("data: {\"choices\":[{\"delta\":{\"content\":\"virtual memory\"}}]}")
        )
    }

    @Test
    fun ignoresTerminalAndNonContentEvents() {
        assertNull(assistantStreamDelta("data: [DONE]"))
        assertNull(assistantStreamDelta("data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}"))
        assertNull(assistantStreamDelta("data: {\"choices\":[{\"delta\":{\"content\":null}}]}"))
        assertNull(assistantStreamDelta("data: {\"choices\":[{\"delta\":{\"content\":\"null\"}}]}"))
    }

    @Test
    fun recognizesServerSentEventControlFrames() {
        assertTrue(isAssistantSseControlLine("event: message"))
        assertTrue(isAssistantSseControlLine("id: 42"))
        assertTrue(isAssistantSseControlLine(": heartbeat"))
    }
}
