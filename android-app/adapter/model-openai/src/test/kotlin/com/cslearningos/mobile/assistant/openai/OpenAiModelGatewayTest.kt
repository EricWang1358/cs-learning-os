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
    fun acceptsAtLimitJsonFallbackWithoutTrailingNewline() = runTest {
        val runId = AssistantRunId("run-1")
        val prefix = "{\"choices\":[{\"message\":{\"content\":\""
        val suffix = "\"}}]}"
        val content = "x".repeat(1_048_576 - prefix.length - suffix.length)
        val response = prefix + content + suffix
        val connection = FakeHttpConnection(statusCode = 200, response = response)

        assertEquals(1_048_576, response.toByteArray(Charsets.UTF_8).size)

        val events = gateway(connection).stream(request(runId)).toList()

        assertEquals(ModelEvent.Completed(runId), events.last())
        assertFalse(events.any { it is ModelEvent.Failed })
        assertEquals(content, events.filterIsInstance<ModelEvent.Token>().joinToString("") { it.value })
    }

    @Test
    fun usesGenericTextForNonAuthHttpErrors() = runTest {
        val sentinel = "provider-http-sentinel"
        val connection = FakeHttpConnection(statusCode = 500, response = sentinel)

        val events = gateway(connection).stream(request(AssistantRunId("run-1"))).toList()

        val failed = events.single() as ModelEvent.Failed
        assertEquals(ModelFailure.Http(500, "HTTP 500"), failed.failure)
        assertFalse(failed.toString().contains(sentinel))
    }

    @Test
    fun usesGenericTextForSseProviderErrors() = runTest {
        val sentinel = "provider-sse-sentinel"
        val connection = FakeHttpConnection(
            statusCode = 200,
            response = "data: {\"error\":{\"message\":\"$sentinel\"}}\n"
        )

        val events = gateway(connection).stream(request(AssistantRunId("run-1"))).toList()

        val failed = events.single() as ModelEvent.Failed
        assertEquals(ModelFailure.Protocol("Model streaming failed."), failed.failure)
        assertFalse(failed.toString().contains(sentinel))
    }

    @Test
    fun rejectsOversizedSuccessfulResponseComposedOfBlankCrlfLines() = runTest {
        val connection = FakeHttpConnection(
            statusCode = 200,
            responseStream = CumulativeBlankLineInputStream()
        )

        val events = gateway(connection).stream(request(AssistantRunId("run-1"))).toList()

        assertEquals(
            listOf(
                ModelEvent.Failed(
                    AssistantRunId("run-1"),
                    ModelFailure.Protocol("Model response exceeded maximum size.")
                )
            ),
            events
        )
    }

    @Test
    fun closesSuccessfulSseResponseWhenDisconnectDoesNotCloseIt() = runTest {
        val response = CloseTrackingInputStream("data: [DONE]\n".toByteArray())
        val connection = FakeHttpConnection(
            statusCode = 200,
            responseStream = response,
            disconnectClosesResponse = false
        )

        val events = gateway(connection).stream(request(AssistantRunId("run-1"))).toList()

        assertEquals(listOf(ModelEvent.Completed(AssistantRunId("run-1"))), events)
        assertTrue(response.closed)
    }

    @Test
    fun disablesRedirectFollowingForApiKeyBearingConnection() = runTest {
        val connection = FakeHttpConnection(
            statusCode = 200,
            response = "data: [DONE]\n"
        )

        gateway(connection).stream(request(AssistantRunId("run-1"))).toList()

        assertFalse(connection.instanceFollowRedirects)
    }

    @Test
    fun failsSafelyWhenNonSseFallbackExceedsResponseLimit() = runTest {
        val oversizedResponse = OversizedLineInputStream()
        val connection = FakeHttpConnection(
            statusCode = 200,
            responseStream = oversizedResponse
        )

        val events = gateway(connection).stream(request(AssistantRunId("run-1"))).toList()

        val failed = events.single() as ModelEvent.Failed
        assertEquals(
            ModelFailure.Protocol("Model response exceeded maximum size."),
            failed.failure
        )
        assertTrue(oversizedResponse.closed)
    }

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

    @Test
    fun rejectsHttpEndpointBeforeCreatingConnection() = runTest {
        var connectionAttempts = 0
        val gateway = OpenAiModelGateway(
            baseUrl = "http://example.test/v1",
            apiKey = "test-key",
            model = "test-model",
            connectTimeoutMillis = 1_000,
            readTimeoutMillis = 2_000,
            connectionFactory = {
                connectionAttempts += 1
                error("Connection must not be created")
            }
        )

        val events = gateway.stream(request(AssistantRunId("run-1"))).toList()

        assertEquals(0, connectionAttempts)
        assertTrue(events.single() is ModelEvent.Failed)
    }

    @Test
    fun rejectsMalformedEndpointBeforeCreatingConnection() = runTest {
        var connectionAttempts = 0
        val gateway = OpenAiModelGateway(
            baseUrl = "https:///v1",
            apiKey = "test-key",
            model = "test-model",
            connectTimeoutMillis = 1_000,
            readTimeoutMillis = 2_000,
            connectionFactory = {
                connectionAttempts += 1
                error("Connection must not be created")
            }
        )

        val events = gateway.stream(request(AssistantRunId("run-1"))).toList()

        assertEquals(0, connectionAttempts)
        assertTrue(events.single() is ModelEvent.Failed)
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
    private val responseStream: InputStream = ByteArrayInputStream(response.toByteArray()),
    private val disconnectClosesResponse: Boolean = true
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
        if (disconnectClosesResponse) responseStream.close()
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

private class OversizedLineInputStream : InputStream() {
    private var bytesRead = 0
    var closed = false
        private set

    override fun read(): Int {
        if (bytesRead > 1_048_576) {
            error("Response reader consumed beyond the overflow byte")
        }
        bytesRead += 1
        return 'x'.code
    }

    override fun close() {
        closed = true
    }
}

private class CumulativeBlankLineInputStream : InputStream() {
    private var bytesRead = 0

    override fun read(): Int {
        if (bytesRead > 1_048_576) return -1
        val nextByte = if (bytesRead % 2 == 0) '\r' else '\n'
        bytesRead += 1
        return nextByte.code
    }
}

private class CloseTrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
    var closed = false
        private set

    override fun close() {
        closed = true
        super.close()
    }
}
