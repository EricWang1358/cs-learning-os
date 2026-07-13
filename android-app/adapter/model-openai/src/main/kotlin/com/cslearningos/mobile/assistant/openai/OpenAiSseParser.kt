package com.cslearningos.mobile.assistant.openai

import org.json.JSONObject

sealed interface OpenAiSseResult {
    data class Token(val value: String) : OpenAiSseResult
    data class Error(val message: String) : OpenAiSseResult
    data object Control : OpenAiSseResult
    data object Done : OpenAiSseResult
    data object Ignored : OpenAiSseResult
}

object OpenAiSseParser {
    fun parse(line: String): OpenAiSseResult {
        val trimmed = line.trimStart()
        if (
            trimmed.startsWith("event:") ||
            trimmed.startsWith("id:") ||
            trimmed.startsWith(":")
        ) {
            return OpenAiSseResult.Control
        }
        if (!trimmed.startsWith(SseDataPrefix)) return OpenAiSseResult.Ignored

        val payload = trimmed.removePrefix(SseDataPrefix).trim()
        if (payload == SseDoneMarker) return OpenAiSseResult.Done
        if (payload.isBlank()) return OpenAiSseResult.Ignored

        val json = runCatching { JSONObject(payload) }
            .getOrElse { return OpenAiSseResult.Error("Invalid streaming response.") }
        json.optJSONObject("error")
            ?.optString("message")
            ?.takeIf { it.isNotBlank() }
            ?.let { return OpenAiSseResult.Error(it) }

        val token = json
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("delta")
            ?.optString("content")
            ?.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
        return token?.let(OpenAiSseResult::Token) ?: OpenAiSseResult.Ignored
    }
}

fun openAiChatCompletionsUrl(baseUrl: String): String {
    val normalized = baseUrl.trim().trimEnd('/')
    return if (normalized.endsWith(ChatCompletionsPath)) normalized else normalized + ChatCompletionsPath
}

fun parseOpenAiChatContent(raw: String): String =
    JSONObject(raw)
        .getJSONArray("choices")
        .getJSONObject(0)
        .getJSONObject("message")
        .getString("content")

private const val SseDataPrefix = "data:"
private const val SseDoneMarker = "[DONE]"
private const val ChatCompletionsPath = "/chat/completions"
