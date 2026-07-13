package com.cslearningos.mobile.assistant.openai

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiSseParserTest {
    @Test
    fun parsesTokenAndTerminalFrames() {
        assertEquals(
            OpenAiSseResult.Token("virtual memory"),
            OpenAiSseParser.parse(
                "data: {\"choices\":[{\"delta\":{\"content\":\"virtual memory\"}}]}"
            )
        )
        assertEquals(OpenAiSseResult.Done, OpenAiSseParser.parse("data: [DONE]"))
    }

    @Test
    fun ignoresMissingAndNullTokens() {
        assertEquals(
            OpenAiSseResult.Ignored,
            OpenAiSseParser.parse("data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}")
        )
        assertEquals(
            OpenAiSseResult.Ignored,
            OpenAiSseParser.parse("data: {\"choices\":[{\"delta\":{\"content\":null}}]}")
        )
        assertEquals(
            OpenAiSseResult.Ignored,
            OpenAiSseParser.parse("data: {\"choices\":[{\"delta\":{\"content\":\"null\"}}]}")
        )
    }

    @Test
    fun recognizesControlAndErrorFrames() {
        assertEquals(OpenAiSseResult.Control, OpenAiSseParser.parse("event: message"))
        assertEquals(OpenAiSseResult.Control, OpenAiSseParser.parse("id: 42"))
        assertEquals(OpenAiSseResult.Control, OpenAiSseParser.parse(": heartbeat"))
        assertEquals(
            OpenAiSseResult.Error("invalid key"),
            OpenAiSseParser.parse("data: {\"error\":{\"message\":\"invalid key\"}}")
        )
    }

    @Test
    fun normalizesEndpointAndParsesNonStreamingContent() {
        assertEquals(
            "https://example.test/v1/chat/completions",
            openAiChatCompletionsUrl(" https://example.test/v1/ ")
        )
        assertEquals(
            "answer",
            parseOpenAiChatContent("{\"choices\":[{\"message\":{\"content\":\"answer\"}}]}")
        )
    }
}
