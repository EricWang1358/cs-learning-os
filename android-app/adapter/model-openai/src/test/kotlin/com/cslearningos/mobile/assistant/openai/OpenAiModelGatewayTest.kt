package com.cslearningos.mobile.assistant.openai

import com.cslearningos.mobile.assistant.domain.AssistantRunId
import com.cslearningos.mobile.assistant.domain.ModelEvent
import com.cslearningos.mobile.assistant.domain.ModelFailure
import com.cslearningos.mobile.assistant.domain.ModelMessage
import com.cslearningos.mobile.assistant.domain.ModelRequest
import com.cslearningos.mobile.assistant.domain.ModelRole
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiModelGatewayTest {
    @Test
    fun emitsProviderNeutralTokensAndCompletion() = runTest {
        val connection = FakeHttpConnection(
            statusCode = 200,
            response = "data: {\"choices\":[{\"delta\":{\"content\":\"answer\"}}]}\n\ndata: [DONE]\n"
        )
        val gateway = gateway(connection)
        val runId = AssistantRunId("run-1")

        val events = gateway.stream(request(runId)).toList()

        assertEquals(
            listOf(ModelEvent.Token(runId, "answer"), ModelEvent.Completed(runId)),
            events
        )
        assertEquals("Bearer test-key", connection.requestProperties["Authorization"])
        assertTrue(connection.requestBody().contains("\"model\":\"test-model\""))
        assertTrue(connection.requestBody().contains("\"role\":\"system\""))
        assertTrue(connection.disconnected)
    }

    @Test
    fun mapsUnauthorizedResponseWithoutLeakingKey() = runTest {
        val connection = FakeHttpConnection(
            statusCode = 401,
            response = "{\"error\":{\"message\":\"bad test-key\"}}"
        )
        val runId = AssistantRunId("run-1")

        val events = gateway(connection).stream(request(runId)).toList()

        assertEquals(1, events.size)
        val failed = events.single() as ModelEvent.Failed
        assertEquals(ModelFailure.Authentication(401), failed.failure)
        assertFalse(failed.toString().contains("test-key"))
    }

    @Test
    fun doneFrameTerminatesWithoutParsingTrailingData() = runTest {
        val connection = FakeHttpConnection(
            statusCode = 200,
            response = "data: [DONE]\n\ndata: not-json\n"
        )
        val runId = AssistantRunId("run-1")

        val events = gateway(connection).stream(request(runId)).toList()

        assertEquals(listOf(ModelEvent.Completed(runId)), events)
    }

    @Test
    fun cancellationDisconnectsABlockedResponseRead() = runBlocking {
        val blockingInput = DisconnectableBlockingInputStream()
        val connection = FakeHttpConnection(
            statusCode = 200,
            responseStream = blockingInput
        )
        val collection = launch { gateway(connection).stream(request(AssistantRunId("run-1"))).toList() }
        yield()
        assertTrue(withContext(Dispatchers.IO) { blockingInput.readStarted.await(2, TimeUnit.SECONDS) })

        collection.cancel()
        val cancelledPromptly = withTimeoutOrNull(500) {
            collection.join()
            true
        } ?: false
        if (!cancelledPromptly) {
            connection.disconnect()
            collection.cancelAndJoin()
        }

        assertTrue("Cancellation must interrupt a blocked response read", cancelledPromptly)
        assertTrue(connection.disconnected)
    }

    private fun gateway(connection: FakeHttpConnection): OpenAiModelGateway =
        OpenAiModelGateway(
            baseUrl = "https://example.test/v1",
            apiKey = "test-key",
            model = "test-model",
            connectTimeoutMillis = 1_000,
            readTimeoutMillis = 2_000,
            connectionFactory = { connection }
        )

    private fun request(runId: AssistantRunId): ModelRequest =
        ModelRequest(
            runId = runId,
            messages = listOf(
                ModelMessage(ModelRole.System, "system prompt"),
                ModelMessage(ModelRole.User, "question")
            )
        )
}

private class FakeHttpConnection(
    url: URL = URL("https://example.test/v1/chat/completions"),
    private val statusCode: Int,
    response: String = "",
    private val responseStream: InputStream = ByteArrayInputStream(response.toByteArray())
) : HttpURLConnection(url) {
    private val requestBytes = ByteArrayOutputStream()
    val requestProperties = linkedMapOf<String, String>()
    var disconnected = false
        private set

    override fun getOutputStream(): ByteArrayOutputStream = requestBytes
    override fun getResponseCode(): Int = statusCode
    override fun getInputStream(): InputStream = responseStream
    override fun getErrorStream(): InputStream = responseStream
    override fun setRequestProperty(key: String, value: String) {
        requestProperties[key] = value
    }
    override fun disconnect() {
        disconnected = true
        responseStream.close()
    }
    override fun usingProxy(): Boolean = false
    override fun connect() = Unit

    fun requestBody(): String = requestBytes.toString(Charsets.UTF_8.name())
}

private class DisconnectableBlockingInputStream : InputStream() {
    val readStarted = CountDownLatch(1)
    private val disconnected = CountDownLatch(1)

    override fun read(): Int {
        readStarted.countDown()
        disconnected.await()
        return -1
    }

    override fun close() {
        disconnected.countDown()
    }
}
