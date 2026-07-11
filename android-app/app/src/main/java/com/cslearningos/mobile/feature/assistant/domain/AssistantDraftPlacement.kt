package com.cslearningos.mobile.feature.assistant.domain

data class AssistantDraftPlacement(
    val markdown: String,
    val areaId: String?,
    val placementReason: String?,
    val captureSuggestion: String?
)

fun parseAssistantDraftPlacement(reply: String, areas: List<AssistantAreaOption>): AssistantDraftPlacement {
    val requestedAreaId = AssistantAreaDirective.find(reply)?.groupValues?.get(1)?.trim()
    val areaId = areas.firstOrNull { it.id == requestedAreaId }?.id
    val placementReason = AssistantAreaReasonDirective.find(reply)
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    val captureSuggestion = AssistantCaptureDirective.findAll(reply)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n\n")
        .takeIf { it.isNotBlank() }
    return AssistantDraftPlacement(
        markdown = reply
            .replace(AssistantAreaDirective, "")
            .replace(AssistantAreaReasonDirective, "")
            .replace(AssistantCaptureDirective, "")
            .trim(),
        areaId = areaId,
        placementReason = placementReason?.takeIf { areaId != null },
        captureSuggestion = captureSuggestion
    )
}

private val AssistantAreaDirective = Regex("^\\s*<!--\\s*cs-area:\\s*([^>]+?)\\s*-->\\s*", RegexOption.MULTILINE)
private val AssistantAreaReasonDirective = Regex("^\\s*<!--\\s*cs-area-reason:\\s*([^>]+?)\\s*-->\\s*", RegexOption.MULTILINE)
private val AssistantCaptureDirective = Regex("^\\s*<!--\\s*cs-capture:\\s*([^>]+?)\\s*-->\\s*", RegexOption.MULTILINE)
