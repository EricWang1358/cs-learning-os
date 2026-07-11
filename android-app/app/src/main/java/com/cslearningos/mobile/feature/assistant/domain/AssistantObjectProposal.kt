package com.cslearningos.mobile.feature.assistant.domain

import com.cslearningos.mobile.data.CaptureSlipType

sealed interface AssistantEditTarget {
    val id: String?
    val revision: Long

    data class Node(
        override val id: String?,
        override val revision: Long,
        val titleHint: String,
        val markdown: String,
        val areaId: String?
    ) : AssistantEditTarget

    data class Quiz(
        override val id: String,
        override val revision: Long,
        val nodeId: String?,
        val prompt: String,
        val answer: String,
        val explanation: String
    ) : AssistantEditTarget

    data class Capture(
        override val id: String,
        override val revision: Long,
        val body: String,
        val topicHint: String,
        val sourceLabel: String,
        val type: CaptureSlipType
    ) : AssistantEditTarget
}

sealed interface AssistantEditProposal {
    val target: AssistantEditTarget

    data class Node(
        override val target: AssistantEditTarget.Node,
        val titleHint: String,
        val markdown: String,
        val areaId: String?,
        val placementReason: String? = null
    ) : AssistantEditProposal

    data class Quiz(
        override val target: AssistantEditTarget.Quiz,
        val prompt: String,
        val answer: String,
        val explanation: String
    ) : AssistantEditProposal

    data class Capture(
        override val target: AssistantEditTarget.Capture,
        val body: String,
        val topicHint: String,
        val sourceLabel: String,
        val type: CaptureSlipType
    ) : AssistantEditProposal
}

fun parseAssistantObjectProposal(
    target: AssistantEditTarget,
    reply: String,
    areas: List<AssistantAreaOption>
): AssistantEditProposal? = when (target) {
    is AssistantEditTarget.Node -> parseNodeProposal(target, reply, areas)

    is AssistantEditTarget.Quiz -> parseQuizProposal(target, reply)
    is AssistantEditTarget.Capture -> parseCaptureProposal(target, reply)
}

fun AssistantEditProposal.nextTarget(): AssistantEditTarget = when (this) {
    is AssistantEditProposal.Node -> target.copy(titleHint = titleHint, markdown = markdown, areaId = areaId)
    is AssistantEditProposal.Quiz -> target.copy(prompt = prompt, answer = answer, explanation = explanation)
    is AssistantEditProposal.Capture -> target.copy(body = body, topicHint = topicHint, sourceLabel = sourceLabel, type = type)
}

private fun parseQuizProposal(target: AssistantEditTarget.Quiz, reply: String): AssistantEditProposal.Quiz? {
    val prompt = reply.singleDirectiveBlock("cs-quiz-prompt") ?: return null
    val answer = reply.singleDirectiveBlock("cs-quiz-answer") ?: return null
    val explanation = reply.singleDirectiveBlock("cs-quiz-explanation") ?: return null
    if (reply.withoutDirectiveBlocks("cs-quiz-prompt", "cs-quiz-answer", "cs-quiz-explanation").isNotBlank()) return null
    return AssistantEditProposal.Quiz(target, prompt, answer, explanation)
}

private fun parseNodeProposal(
    target: AssistantEditTarget.Node,
    reply: String,
    areas: List<AssistantAreaOption>
): AssistantEditProposal.Node? {
    val directives = NodeDirectiveLine.findAll(reply).toList()
    val areaDirectives = directives.mapNotNull { match -> match.nodeDirectiveValue("cs-area") }
    val reasonDirectives = directives.mapNotNull { match -> match.nodeDirectiveValue("cs-area-reason") }
    val captureDirectives = directives.mapNotNull { match -> match.nodeDirectiveValue("cs-capture") }
    val knownDirectiveCount = areaDirectives.size + reasonDirectives.size + captureDirectives.size
    if (knownDirectiveCount != directives.size) return null
    if (areaDirectives.size > 1 || reasonDirectives.size > 1 || captureDirectives.size > 1) return null
    if (target.id == null && target.areaId == null && areaDirectives.isEmpty()) return null

    val areaId = areaDirectives.singleOrNull()?.let { requestedAreaId ->
        areas.firstOrNull { it.id == requestedAreaId }?.id ?: return null
    } ?: target.areaId
    val placementReason = reasonDirectives.singleOrNull()?.takeIf { areaDirectives.isNotEmpty() }
    val markdown = reply
        .replace(NodeAreaDirectiveLine, "")
        .replace(NodeAreaReasonDirectiveLine, "")
        .replace(NodeCaptureDirectiveLine, "")
        .trim()
        .takeIf(String::isNotBlank)
        ?: return null
    return AssistantEditProposal.Node(
        target = target,
        titleHint = target.titleHint,
        markdown = markdown,
        areaId = areaId,
        placementReason = placementReason
    )
}

private fun parseCaptureProposal(target: AssistantEditTarget.Capture, reply: String): AssistantEditProposal.Capture? {
    val body = reply.singleDirectiveBlock("cs-capture-body") ?: return null
    val topicHint = reply.singleDirectiveBlock("cs-capture-topic") ?: return null
    val sourceLabel = reply.singleDirectiveBlock("cs-capture-source") ?: return null
    val typeMatches = CaptureTypeDirective.findAll(reply).toList()
    if (typeMatches.size != 1) return null
    val typeName = typeMatches.single().groupValues[1].trim()
    val type = CaptureSlipType.entries.firstOrNull { it.name == typeName } ?: return null
    if (reply.withoutDirectiveBlocks("cs-capture-body", "cs-capture-topic", "cs-capture-source")
            .replace(CaptureTypeDirective, "")
            .isNotBlank()
    ) return null
    return AssistantEditProposal.Capture(target, body, topicHint, sourceLabel, type)
}

private fun String.singleDirectiveBlock(name: String): String? {
    val matches = directiveRegex(name).findAll(this).toList()
    return matches
        .singleOrNull()
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?.takeIf(String::isNotBlank)
}

private fun String.withoutDirectiveBlocks(vararg names: String): String =
    names.fold(this) { remaining, name -> remaining.replace(directiveRegex(name), "") }.trim()

private fun directiveRegex(name: String): Regex =
    Regex("<!--\\s*$name\\s*-->(.*?)<!--\\s*/$name\\s*-->", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

private fun MatchResult.nodeDirectiveValue(name: String): String? =
    Regex("^$name\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
        .matchEntire(groupValues[1].trim())
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?.takeIf(String::isNotBlank)

private val NodeDirectiveLine = Regex("^\\s*<!--\\s*(cs-[^>]*?)\\s*-->\\s*(?:\\r?\\n|$)", RegexOption.MULTILINE)
private val NodeAreaDirectiveLine = Regex("^\\s*<!--\\s*cs-area\\s*:\\s*[^>]+?\\s*-->\\s*(?:\\r?\\n|$)", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
private val NodeAreaReasonDirectiveLine = Regex("^\\s*<!--\\s*cs-area-reason\\s*:\\s*[^>]+?\\s*-->\\s*(?:\\r?\\n|$)", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
private val NodeCaptureDirectiveLine = Regex("^\\s*<!--\\s*cs-capture\\s*:\\s*[^>]+?\\s*-->\\s*(?:\\r?\\n|$)", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
private val CaptureTypeDirective = Regex("<!--\\s*cs-capture-type:\\s*([^>]+?)\\s*-->", RegexOption.IGNORE_CASE)
