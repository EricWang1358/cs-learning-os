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
                    val responseBody = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    send(
                        ModelEvent.Failed(
                            request.runId,
                            responseFailure(responseCode, responseBody)
                        )
                    )
                    return@withContext
                }

                var receivedSseFrame = false
                val rawResponse = StringBuilder()
                responseStream?.bufferedReader()?.use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
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
                                        ModelFailure.Protocol(parsed.message.safeMessage())
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
                                rawResponse.appendLine(line)
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

    private fun responseFailure(statusCode: Int, responseBody: String): ModelFailure =
        when (statusCode) {
            HttpURLConnection.HTTP_UNAUTHORIZED,
            HttpURLConnection.HTTP_FORBIDDEN -> ModelFailure.Authentication(statusCode)
            TooManyRequestsStatus -> ModelFailure.RateLimited(null)
            else -> ModelFailure.Http(statusCode, responseBody.safeMessage())
        }

    private fun String?.safeMessage(): String =
        orEmpty()
            .replace(apiKey, "[redacted]")
            .ifBlank { "Model transport failed." }
            .take(MaximumErrorCharacters)

    private companion object {
        const val DefaultTemperature = 0.25
        const val FallbackChunkSize = 24
        const val MaximumErrorCharacters = 240
        const val TooManyRequestsStatus = 429
        val HttpSuccessRange = 200..299
    }
}
