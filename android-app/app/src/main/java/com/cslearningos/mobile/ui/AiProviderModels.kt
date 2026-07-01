package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.CaptureSlipEntity
import org.json.JSONObject

enum class AiServiceStatusKind {
    Idle,
    Info,
    Loading,
    Success,
    Warning,
    Error
}

data class AiServiceStatus(
    val kind: AiServiceStatusKind = AiServiceStatusKind.Info,
    val title: String = "AI is optional",
    val body: String = "Add a provider, API key, base URL, and model to turn capture slips into editable Markdown drafts."
)

fun AiProviderSettings.missingRequiredFields(): List<String> =
    buildList {
        if (apiKey.isBlank()) add("API key")
        if (baseUrl.isBlank()) add("Base URL")
        if (model.isBlank()) add("Model")
    }

fun aiModelsUrl(baseUrl: String): String =
    baseUrl.trim().trimEnd('/') + "/models"

fun aiChatCompletionsUrl(baseUrl: String): String =
    baseUrl.trim().trimEnd('/') + "/chat/completions"

fun parseOpenAiModelIds(raw: String): List<String> {
    val data = JSONObject(raw).optJSONArray("data") ?: return emptyList()
    return buildList {
        for (index in 0 until data.length()) {
            val id = data.optJSONObject(index)?.optString("id").orEmpty()
            if (id.isNotBlank()) add(id)
        }
    }
}

fun parseOpenAiChatContent(raw: String): String =
    JSONObject(raw)
        .getJSONArray("choices")
        .getJSONObject(0)
        .getJSONObject("message")
        .getString("content")
        .trim()

fun buildCaptureAiDraftPrompt(slip: CaptureSlipEntity, existingNodes: List<String>): String {
    val knownNodes = existingNodes.take(12).joinToString(separator = "\n") { "- $it" }.ifBlank { "- No existing nodes yet" }
    return """
        Convert this mobile capture slip into a useful CS Learning OS Markdown node draft.

        Return Markdown only. Do not wrap the answer in code fences.
        Include these sections:
        # A concise learning-node title
        ## Captured Context
        ## Explanation
        ## Common Mistake
        ## Quiz Seeds
        ## Next Step

        The draft should be clear enough for a student to review later, but it must stay editable and should not pretend to be final truth if the capture is ambiguous.

        Existing node titles that may be relevant:
        $knownNodes

        Capture type: ${slip.type.name}
        Topic hint: ${slip.topicHint.orEmpty().ifBlank { "None" }}
        Source label: ${slip.sourceLabel.orEmpty().ifBlank { "None" }}
        Capture body:
        ${slip.body}
    """.trimIndent()
}

fun titleFromAiMarkdown(markdown: String, fallback: String): String =
    markdown
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("# ") }
        ?.trimStart('#')
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: fallback
