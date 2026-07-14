package com.cslearningos.mobile.assistant.openai

import com.cslearningos.mobile.assistant.domain.ModelCapabilities
import com.cslearningos.mobile.assistant.domain.ModelEvent
import com.cslearningos.mobile.assistant.domain.ModelFailure
import com.cslearningos.mobile.assistant.domain.ModelGateway
import com.cslearningos.mobile.assistant.domain.ModelRequest
import com.cslearningos.mobile.assistant.domain.InvalidProviderEndpointMessage
import com.cslearningos.mobile.assistant.domain.isValidProviderEndpoint
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class OpenAiModelGateway(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val connectTimeoutMillis: Int,
    private val readTimeoutMillis: Int,
    private val temperature: Double = DefaultTemperature,
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    }
) : ModelGateway {
    override suspend fun capabilities(): ModelCapabilities =
        ModelCapabilities(
            streaming = true,
            structuredOutput = false,
            toolCalls = false,
            contextWindowTokens = null
        )

    @OptIn(InternalCoroutinesApi::class)
    override fun stream(request: ModelRequest): Flow<ModelEvent> = channelFlow {
        withContext(Dispatchers.IO) {
            if (!isValidProviderEndpoint(baseUrl)) {
                send(ModelEvent.Failed(request.runId, ModelFailure.Transport(InvalidProviderEndpointMessage)))
                return@withContext
            }
            val connection = connectionFactory(URL(openAiChatCompletionsUrl(baseUrl))).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                doOutput = true
                instanceFollowRedirects = false
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Accept", "text/event-stream, application/json")
                setRequestProperty("Content-Type", "application/json")
            }
            val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion(
                onCancelling = true,
                invokeImmediately = true
            ) { cause ->
                if (cause != null) connection.disconnect()
            }
            try {
                connection.outputStream.use { output ->
                    output.write(request.toPayload().toString().toByteArray(Charsets.UTF_8))
                }
                val responseCode = connection.responseCode
                val responseStream = if (responseCode in HttpSuccessRange) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                if (responseCode !in HttpSuccessRange) {
                    responseStream?.use { it.discardBoundedResponse() }
                    send(
                        ModelEvent.Failed(
                            request.runId,
                            responseFailure(responseCode)
                        )
                    )
                    return@withContext
                }

                var receivedSseFrame = false
                val rawResponse = StringBuilder()
                responseStream?.let { response ->
                    BoundedResponseInputStream(response).use { stream ->
                        while (true) {
                            val line = try {
                                stream.readBoundedLine()
                            } catch (_: ResponseTooLargeException) {
                                send(
                                    ModelEvent.Failed(
                                        request.runId,
                                        ModelFailure.Protocol(ResponseTooLargeMessage)
                                    )
                                )
                                return@withContext
                            } ?: break
                            currentCoroutineContext().ensureActive()
                            when (val parsed = OpenAiSseParser.parse(line)) {
                                is OpenAiSseResult.Token -> {
                                    receivedSseFrame = true
                                    send(ModelEvent.Token(request.runId, parsed.value))
                                }
                                is OpenAiSseResult.Error -> {
                                    receivedSseFrame = true
                                    send(
                                        ModelEvent.Failed(
                                            request.runId,
                                            ModelFailure.Protocol(GenericStreamingFailureMessage)
                                        )
                                    )
                                    return@withContext
                                }
                                OpenAiSseResult.Control -> receivedSseFrame = true
                                OpenAiSseResult.Done -> {
                                    receivedSseFrame = true
                                    break
                                }
                                OpenAiSseResult.Ignored -> if (line.isNotBlank()) {
                                    rawResponse.append(line).append('\n')
                                }
                            }
                        }
                    }
                }

                if (!receivedSseFrame && rawResponse.isNotBlank()) {
                    val content = runCatching { parseOpenAiChatContent(rawResponse.toString()) }
                        .getOrElse {
                            send(
                                ModelEvent.Failed(
                                    request.runId,
                                    ModelFailure.Protocol("Invalid model response.")
                                )
                            )
                            return@withContext
                        }
                    content.chunked(FallbackChunkSize).forEach { chunk ->
                        send(ModelEvent.Token(request.runId, chunk))
                    }
                }
                send(ModelEvent.Completed(request.runId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                send(
                    ModelEvent.Failed(
                        request.runId,
                        ModelFailure.Transport(failure.message.safeMessage())
                    )
                )
            } finally {
                cancellationHandle?.dispose()
                connection.disconnect()
            }
        }
    }

    private fun ModelRequest.toPayload(): JSONObject {
        val payloadMessages = JSONArray()
        messages.forEach { message ->
            if (message.content.isNotBlank()) {
                payloadMessages.put(
                    JSONObject()
                        .put("role", message.role.wireValue)
                        .put("content", message.content)
                )
            }
        }
        return JSONObject()
            .put("model", model)
            .put("temperature", temperature)
            .put("stream", true)
            .put("messages", payloadMessages)
    }

    private fun responseFailure(statusCode: Int): ModelFailure =
        when (statusCode) {
            HttpURLConnection.HTTP_UNAUTHORIZED,
            HttpURLConnection.HTTP_FORBIDDEN -> ModelFailure.Authentication(statusCode)
            TooManyRequestsStatus -> ModelFailure.RateLimited(null)
            else -> ModelFailure.Http(statusCode, "HTTP $statusCode")
        }

    private fun String?.safeMessage(): String =
        orEmpty()
            .replace(apiKey, "[redacted]")
            .ifBlank { "Model transport failed." }
            .take(MaximumErrorCharacters)

    private fun java.io.InputStream.discardBoundedResponse() {
        val response = BoundedResponseInputStream(this)
        val buffer = ByteArray(ResponseBodyMaximumBytes.coerceAtMost(8_192))
        while (true) {
            val bytesRead = response.read(buffer)
            if (bytesRead < 0) break
        }
    }

    private fun java.io.InputStream.readBoundedLine(): String? {
        val line = java.io.ByteArrayOutputStream()
        while (true) {
            val nextByte = read()
            if (nextByte < 0) {
                return line.takeIf { it.size() > 0 }?.toString(Charsets.UTF_8.name())
            }
            if (nextByte == '\n'.code) {
                val bytes = line.toByteArray()
                val length = if (bytes.lastOrNull() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
                return bytes.copyOf(length).toString(Charsets.UTF_8)
            }
            if (line.size() >= ResponseBodyMaximumBytes) {
                throw ResponseTooLargeException()
            }
            line.write(nextByte)
        }
    }

    private companion object {
        const val DefaultTemperature = 0.25
        const val FallbackChunkSize = 24
        const val MaximumErrorCharacters = 240
        const val ResponseBodyMaximumBytes = 1_048_576
        const val ResponseTooLargeMessage = "Model response exceeded maximum size."
        const val GenericStreamingFailureMessage = "Model streaming failed."
        const val TooManyRequestsStatus = 429
        val HttpSuccessRange = 200..299
    }

    private class ResponseTooLargeException : IllegalStateException(ResponseTooLargeMessage)

    private class BoundedResponseInputStream(
        private val delegate: java.io.InputStream
    ) : java.io.InputStream() {
        private var bytesRead = 0

        override fun read(): Int {
            val nextByte = delegate.read()
            if (nextByte < 0) return -1
            if (bytesRead >= ResponseBodyMaximumBytes) throw ResponseTooLargeException()
            bytesRead += 1
            return nextByte
        }

        override fun close() {
            delegate.close()
        }
    }
}
