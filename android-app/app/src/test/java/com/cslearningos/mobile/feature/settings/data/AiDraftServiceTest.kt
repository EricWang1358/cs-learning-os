package com.cslearningos.mobile.feature.settings.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDraftServiceTest {
    @Test
    fun fetchModelsDisablesRedirectFollowing() = runBlocking {
        val connection = FakeDraftHttpConnection(response = "{\"data\":[]}")

        OpenAiCompatibleDraftService { connection }
            .fetchModelIds("https://provider.test/v1", "sk-test-key")

        assertFalse(connection.instanceFollowRedirects)
    }

    @Test
    fun requestDraftDisablesRedirectFollowing() = runBlocking {
        val connection = FakeDraftHttpConnection(
            response = "{\"choices\":[{\"message\":{\"content\":\"draft\"}}]}"
        )

        OpenAiCompatibleDraftService { connection }
            .requestDraft("https://provider.test/v1", "sk-test-key", "model", "prompt")

        assertFalse(connection.instanceFollowRedirects)
    }

    @Test
    fun fetchModelsRejectsOversizedSuccessfulResponse() = runBlocking {
        val connection = FakeDraftHttpConnection(response = "x".repeat(1_048_577))

        val failure = runCatching {
            OpenAiCompatibleDraftService { connection }
                .fetchModelIds("https://provider.test/v1", "sk-test-key")
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("exceeded"))
    }

    @Test
    fun fetchModelsRejectsOversizedErrorResponse() = runBlocking {
        val connection = FakeDraftHttpConnection(
            statusCode = 500,
            response = "x".repeat(1_048_577)
        )

        val failure = runCatching {
            OpenAiCompatibleDraftService { connection }
                .fetchModelIds("https://provider.test/v1", "sk-test-key")
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("exceeded"))
    }

    @Test
    fun fetchModelsDoesNotExposeApiKeyFromErrorResponse() = runBlocking {
        val apiKey = "sk-super-secret-key"
        val connection = FakeDraftHttpConnection(
            statusCode = 500,
            response = "{\"error\":\"$apiKey\"}"
        )

        val failure = runCatching {
            OpenAiCompatibleDraftService { connection }
                .fetchModelIds("https://provider.test/v1", apiKey)
        }.exceptionOrNull()

        assertFalse(failure?.message.orEmpty().contains(apiKey))
        assertFalse(failure?.safeAiError().orEmpty().contains(apiKey))
    }

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

private class FakeDraftHttpConnection(
    url: URL = URL("https://provider.test/v1/models"),
    private val statusCode: Int = 200,
    response: String = ""
) : HttpURLConnection(url) {
    private val responseStream = ByteArrayInputStream(response.toByteArray())
    private val requestBody = ByteArrayOutputStream()

    override fun getOutputStream(): ByteArrayOutputStream = requestBody
    override fun getResponseCode(): Int = statusCode
    override fun getInputStream() = responseStream
    override fun getErrorStream() = responseStream
    override fun disconnect() = Unit
    override fun usingProxy(): Boolean = false
    override fun connect() = Unit
}
