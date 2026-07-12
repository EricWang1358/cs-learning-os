package com.cslearningos.mobile.ui

import com.cslearningos.mobile.R
import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.feature.assistant.domain.AssistantAreaOption
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
    val title: UiText = uiText(R.string.ai_status_optional_title),
    val body: UiText = uiText(R.string.ai_status_optional_body)
)

fun AiProviderSettings.missingRequiredFields(): List<Int> =
    buildList {
        if (apiKey.isBlank()) add(R.string.more_api_key_label)
        if (baseUrl.isBlank()) add(R.string.more_base_url_label)
        if (model.isBlank()) add(R.string.more_model_label)
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

fun aiDraftContextNodeTitles(existingNodes: List<String>): List<String> =
    existingNodes
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(12)

fun buildCaptureAiDraftPrompt(
    slip: CaptureSlipEntity,
    existingNodes: List<String>,
    areas: List<AssistantAreaOption> = emptyList()
): String {
    val knownNodes = aiDraftContextNodeTitles(existingNodes)
        .joinToString(separator = "\n") { "- $it" }
        .ifBlank { "- No existing nodes yet" }
    val knownAreas = areas.joinToString(separator = "\n") { area ->
        val examples = area.exampleTitles.joinToString().takeIf { it.isNotBlank() } ?: "no examples yet"
        "- ${area.id}: ${area.name} (examples: $examples)"
    }.ifBlank { "- No existing Areas available" }
    return """
        Convert this mobile capture slip into a useful CS Learning OS Markdown node draft.

        Return Markdown only. Do not wrap the answer in code fences.
        If one existing Area below is a clear fit, begin with <!-- cs-area: AREA_ID --> and <!-- cs-area-reason: one concrete reason -->.
        If no Area is a clear fit, omit both directives. Never invent an Area.
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

        Existing Areas that may be relevant:
        $knownAreas

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

data class AssistantComposerTextAttachment(
    val fileName: String,
    val characterCount: Int,
    val preview: String
)

fun assistantComposerTextAttachment(value: String): AssistantComposerTextAttachment? {
    val normalized = value.trim()
    if (normalized.length < AssistantComposerAttachmentThreshold) return null
    val preview = normalized
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
        .take(AssistantComposerAttachmentPreviewCharacters)
    return AssistantComposerTextAttachment(
        fileName = "material.txt",
        characterCount = normalized.length,
        preview = preview
    )
}

private const val AssistantComposerAttachmentThreshold = 700
private const val AssistantComposerAttachmentPreviewCharacters = 96
