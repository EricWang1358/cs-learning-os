package com.cslearningos.mobile.feature.settings.data

import com.cslearningos.mobile.core.common.AndroidArchitectureConstants
import com.cslearningos.mobile.ui.aiChatCompletionsUrl
import com.cslearningos.mobile.ui.aiModelsUrl
import com.cslearningos.mobile.ui.parseOpenAiChatContent
import com.cslearningos.mobile.ui.parseOpenAiModelIds
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface AiDraftService {
    suspend fun fetchModelIds(baseUrl: String, apiKey: String): List<String>

    suspend fun requestDraft(baseUrl: String, apiKey: String, model: String, prompt: String): String
}

class OpenAiCompatibleDraftService : AiDraftService {
    override suspend fun fetchModelIds(baseUrl: String, apiKey: String): List<String> =
        withContext(Dispatchers.IO) {
            parseOpenAiModelIds(
                openAiGet(
                    url = aiModelsUrl(baseUrl),
                    apiKey = apiKey
                )
            )
        }

    override suspend fun requestDraft(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("model", model)
            .put("temperature", 0.2)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put(
                                "content",
                                "You create concise, editable Markdown learning-node drafts for a local-first study app."
                            )
                    )
                    .put(JSONObject().put("role", "user").put("content", prompt))
            )
        parseOpenAiChatContent(
            openAiPost(
                url = aiChatCompletionsUrl(baseUrl),
                apiKey = apiKey,
                payload = payload
            )
        ).ifBlank {
            throw IllegalStateException("The model returned an empty draft.")
        }
    }
}

fun Throwable.safeAiError(): String =
    (message ?: javaClass.simpleName)
        .replace(Regex("sk-[A-Za-z0-9_-]+"), "sk-...")
        .take(260)

private fun openAiGet(url: String, apiKey: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = AndroidArchitectureConstants.AiConnectTimeoutMillis
        readTimeout = AndroidArchitectureConstants.AiReadTimeoutMillis
        setRequestProperty("Authorization", "Bearer $apiKey")
        setRequestProperty("Accept", "application/json")
    }
    return connection.useResponse()
}

private fun openAiPost(url: String, apiKey: String, payload: JSONObject): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = AndroidArchitectureConstants.AiConnectTimeoutMillis
        readTimeout = AndroidArchitectureConstants.AiReadTimeoutMillis
        doOutput = true
        setRequestProperty("Authorization", "Bearer $apiKey")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Content-Type", "application/json")
    }
    connection.outputStream.use { stream ->
        stream.write(payload.toString().toByteArray(Charsets.UTF_8))
    }
    return connection.useResponse()
}

private fun HttpURLConnection.useResponse(): String =
    try {
        val responseBody = (if (responseCode in 200..299) inputStream else errorStream)
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()
        if (responseCode !in 200..299) {
            throw IllegalStateException("HTTP $responseCode: ${responseBody.take(240)}")
        }
        responseBody
    } finally {
        disconnect()
    }
