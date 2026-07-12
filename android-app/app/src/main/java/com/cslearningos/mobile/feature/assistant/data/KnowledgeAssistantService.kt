package com.cslearningos.mobile.feature.assistant.data

import com.cslearningos.mobile.core.common.AndroidArchitectureConstants
import com.cslearningos.mobile.feature.assistant.domain.KnowledgeAssistantChatMessage
import com.cslearningos.mobile.ui.aiChatCompletionsUrl
import com.cslearningos.mobile.ui.parseOpenAiChatContent
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface KnowledgeAssistantService {
    suspend fun streamReply(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        messages: List<KnowledgeAssistantChatMessage>,
        onDelta: suspend (String) -> Unit
    )
}

class OpenAiCompatibleKnowledgeAssistantService : KnowledgeAssistantService {
    override suspend fun streamReply(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        messages: List<KnowledgeAssistantChatMessage>,
        onDelta: suspend (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val payloadMessages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
        messages.forEach { message ->
            if (message.content.isNotBlank()) {
                payloadMessages.put(
                    JSONObject()
                        .put("role", message.role)
                        .put("content", message.content)
                )
            }
        }
        val payload = JSONObject()
            .put("model", model)
            .put("temperature", AssistantTemperature)
            .put("stream", true)
            .put("messages", payloadMessages)
        val connection = (URL(aiChatCompletionsUrl(baseUrl)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = AndroidArchitectureConstants.AiConnectTimeoutMillis
            readTimeout = AndroidArchitectureConstants.AiReadTimeoutMillis
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "text/event-stream, application/json")
            setRequestProperty("Content-Type", "application/json")
        }
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion {
            connection.disconnect()
        }
        try {
            connection.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val responseCode = connection.responseCode
            val stream = if (responseCode in HttpSuccessRange) connection.inputStream else connection.errorStream
            var receivedSseFraming = false
            val rawResponse = stream?.bufferedReader()?.use { reader ->
                buildString {
                    while (true) {
                        val line = reader.readLine() ?: break
                        currentCoroutineContext().ensureActive()
                        when {
                            line.trimStart().startsWith(SseDataPrefix) -> {
                                receivedSseFraming = true
                                assistantStreamError(line)?.let { message ->
                                    throw IllegalStateException(message)
                                }
                                assistantStreamDelta(line)?.let { delta -> onDelta(delta) }
                            }

                            isAssistantSseControlLine(line) -> receivedSseFraming = true
                            line.isNotBlank() -> appendLine(line)
                        }
                    }
                }
            }.orEmpty().trim()
            if (responseCode !in HttpSuccessRange) {
                throw IllegalStateException("HTTP $responseCode: ${rawResponse.take(MaximumErrorCharacters)}")
            }
            if (!receivedSseFraming && rawResponse.isNotBlank()) {
                parseOpenAiChatContent(rawResponse)
                    .chunked(FallbackChunkSize)
                    .forEach { chunk -> onDelta(chunk) }
            }
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
        }
    }

    private companion object {
        const val AssistantTemperature = 0.25
        const val FallbackChunkSize = 24
        const val MaximumErrorCharacters = 240
        val HttpSuccessRange = 200..299
    }
}

fun assistantStreamDelta(line: String): String? {
    val payload = assistantSsePayload(line) ?: return null
    if (payload.isBlank() || payload == SseDoneMarker) return null
    return JSONObject(payload)
        .optJSONArray("choices")
        ?.optJSONObject(0)
        ?.optJSONObject("delta")
        ?.optString("content")
        // Some compatible gateways serialize an absent token as the string "null".
        ?.takeUnless { it.isNullAssistantToken() }
}

fun isAssistantSseControlLine(line: String): Boolean {
    val trimmed = line.trimStart()
    return trimmed.startsWith("event:") || trimmed.startsWith("id:") || trimmed.startsWith(":")
}

private fun assistantStreamError(line: String): String? {
    val payload = assistantSsePayload(line) ?: return null
    if (payload.isBlank() || payload == SseDoneMarker) return null
    return runCatching {
        JSONObject(payload)
            .optJSONObject("error")
            ?.optString("message")
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

private fun assistantSsePayload(line: String): String? =
    line
        .trimStart()
        .takeIf { it.startsWith(SseDataPrefix) }
        ?.removePrefix(SseDataPrefix)
        ?.trim()

private const val SseDataPrefix = "data:"
private const val SseDoneMarker = "[DONE]"

private fun String.isNullAssistantToken(): Boolean =
    isBlank() || equals("null", ignoreCase = true)
