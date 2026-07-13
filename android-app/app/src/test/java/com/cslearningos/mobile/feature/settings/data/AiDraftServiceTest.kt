package com.cslearningos.mobile.feature.settings.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDraftServiceTest {
    @Test
    fun fetchModelsRejectsHttpEndpointBeforeOpeningConnection() = runBlocking {
        var connectionAttempts = 0
        val service = OpenAiCompatibleDraftService(
            connectionFactory = {
                connectionAttempts += 1
                error("Connection must not be opened")
            }
        )

        val failure = runCatching { service.fetchModelIds("http://provider.test/v1", "sk-test") }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(0, connectionAttempts)
    }

    @Test
    fun requestDraftRejectsMalformedEndpointBeforeOpeningConnection() = runBlocking {
        var connectionAttempts = 0
        val service = OpenAiCompatibleDraftService(
            connectionFactory = {
                connectionAttempts += 1
                error("Connection must not be opened")
            }
        )

        val failure = runCatching {
            service.requestDraft("https:///v1", "sk-test", "model", "prompt")
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(0, connectionAttempts)
    }
}
