package com.cslearningos.mobile.feature.assistant.domain

import com.cslearningos.mobile.data.CaptureSlipType
import com.cslearningos.mobile.markdown.AssistantMarkdownNormalizer

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
        val placementReason: String? = null,
        val captureSuggestion: String? = null
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
    val rawNormalizedReply = AssistantMarkdownNormalizer.normalize(reply)
    val rawDirectives = NodeDirectiveLine.findAll(rawNormalizedReply).toList()
    val rawKnownDirectiveCount =
        rawDirectives.count { match -> match.nodeDirectiveValue("cs-title") != null } +
            rawDirectives.count { match -> match.nodeDirectiveValue("cs-area") != null } +
            rawDirectives.count { match -> match.nodeDirectiveValue("cs-area-reason") != null } +
            rawDirectives.count { match -> match.nodeDirectiveValue("cs-capture") != null }
    if (rawKnownDirectiveCount != rawDirectives.size) return null

    val normalizedReply = AssistantMarkdownNormalizer.normalize(reply.extractNodeDraftPayload())
    val directives = NodeDirectiveLine.findAll(normalizedReply).toList()
    val titleDirectives = directives.mapNotNull { match -> match.nodeDirectiveValue("cs-title") }
    val areaDirectives = directives.mapNotNull { match -> match.nodeDirectiveValue("cs-area") }
    val reasonDirectives = directives.mapNotNull { match -> match.nodeDirectiveValue("cs-area-reason") }
    val captureDirectives = directives.mapNotNull { match -> match.nodeDirectiveValue("cs-capture") }
    val looseAreaMatches = LooseAreaCommentLine.findAll(normalizedReply).toList()
    val looseAreaDirectives = looseAreaMatches
        .mapNotNull { match -> match.groupValues[1].matchAreaId(areas) }
    val knownDirectiveCount = titleDirectives.size + areaDirectives.size + reasonDirectives.size + captureDirectives.size
    if (knownDirectiveCount != directives.size) return null
    if (looseAreaMatches.size != looseAreaDirectives.size) return null
    if (titleDirectives.size > 1) return null
    if (areaDirectives.size + looseAreaDirectives.size > 1 || reasonDirectives.size > 1 || captureDirectives.size > 1) return null
    if (target.id == null && target.areaId == null && areaDirectives.isEmpty() && areas.isEmpty()) return null

    val areaId = (areaDirectives.singleOrNull() ?: looseAreaDirectives.singleOrNull())?.let { requestedAreaId ->
        areas.firstOrNull { it.id == requestedAreaId }?.id ?: return null
    } ?: target.areaId ?: normalizedReply.looseAreaId(areas)
    val placementReason = reasonDirectives.singleOrNull()?.takeIf { areaDirectives.isNotEmpty() }
    val captureSuggestion = captureDirectives.singleOrNull()
    val explicitTitle = titleDirectives.singleOrNull()
        ?.cleanPlainTitle()
        ?.takeIf(String::isNotBlank)
    val markdown = normalizedReply
        .replace(NodeTitleDirectiveLine, "")
        .replace(NodeAreaDirectiveLine, "")
        .replace(NodeAreaReasonDirectiveLine, "")
        .replace(NodeCaptureDirectiveLine, "")
        .replace(LooseAreaCommentLine, "")
        .replace(LooseAreaLine, "")
        .trim()
        .takeIf(String::isNotBlank)
        ?: return null
    if (explicitTitle == null && !markdown.hasMarkdownTitleHeading()) return null
    return AssistantEditProposal.Node(
        target = target,
        titleHint = explicitTitle ?: target.titleHint,
        markdown = if (explicitTitle == null) markdown else markdown.dropLeadingMarkdownTitleHeading().trim(),
        areaId = areaId,
        placementReason = placementReason,
        captureSuggestion = captureSuggestion
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

private fun String.extractNodeDraftPayload(): String {
    val normalizedOriginal = AssistantMarkdownNormalizer.normalize(this)
    if (normalizedOriginal.hasMarkdownTitleHeadingAfterOptionalDirectives()) {
        return this
    }

    MarkdownFenceBlock.findAll(this)
        .map { match -> match.groupValues[2].trim() }
        .firstOrNull { payload ->
            AssistantMarkdownNormalizer.normalize(payload).hasMarkdownTitleHeadingAfterOptionalDirectives()
        }
        ?.let { return it }

    val lines = lines()
    val firstHeadingIndex = lines.indexOfFirst { line -> line.trim().matches(TitleHeadingLine) }
    if (firstHeadingIndex >= 0) {
        val directivePrefix = lines.take(firstHeadingIndex)
            .filter { line ->
                val trimmed = line.trim()
                trimmed.isBlank() ||
                    NodeAreaDirectiveLine.matches(trimmed) ||
                    NodeTitleDirectiveLine.matches(trimmed) ||
                    NodeAreaReasonDirectiveLine.matches(trimmed) ||
                    NodeCaptureDirectiveLine.matches(trimmed) ||
                    LooseAreaCommentLine.matches(trimmed) ||
                    LooseAreaLine.matches(trimmed)
            }
        return (directivePrefix + lines.drop(firstHeadingIndex)).joinToString("\n").trim()
    }

    return this
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

private fun String.looseAreaId(areas: List<AssistantAreaOption>): String? {
    val rawArea = LooseAreaLine.find(this)
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val normalized = rawArea.lowercase()
    return areas.firstOrNull { area ->
        area.id.equals(rawArea, ignoreCase = true) ||
            area.name.equals(rawArea, ignoreCase = true) ||
            normalized.startsWith("${area.id.lowercase()} ") ||
            normalized.startsWith("${area.name.lowercase()} ")
    }?.id
}

private fun String.matchAreaId(areas: List<AssistantAreaOption>): String? {
    val rawArea = trim()
    val normalized = rawArea.lowercase()
    return areas.firstOrNull { area ->
        area.id.equals(rawArea, ignoreCase = true) ||
            area.name.equals(rawArea, ignoreCase = true) ||
            normalized.startsWith("${area.id.lowercase()} ") ||
            normalized.startsWith("${area.name.lowercase()} ")
    }?.id
}

private fun String.hasMarkdownTitleHeading(): Boolean =
    lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?.matches(TitleHeadingLine) == true

private fun String.hasMarkdownTitleHeadingAfterOptionalDirectives(): Boolean =
    lineSequence()
        .map { it.trim() }
        .firstOrNull { line ->
            line.isNotBlank() &&
                !NodeAreaDirectiveLine.matches(line) &&
                !NodeTitleDirectiveLine.matches(line) &&
                !NodeAreaReasonDirectiveLine.matches(line) &&
                !NodeCaptureDirectiveLine.matches(line) &&
                !LooseAreaCommentLine.matches(line) &&
                !LooseAreaLine.matches(line)
        }
        ?.matches(TitleHeadingLine) == true

private fun String.dropLeadingMarkdownTitleHeading(): String {
    val lines = lines()
    val firstContentIndex = lines.indexOfFirst { it.isNotBlank() }
    if (firstContentIndex < 0) return this
    if (!lines[firstContentIndex].trim().matches(TitleHeadingLine)) return this
    return (lines.take(firstContentIndex) + lines.drop(firstContentIndex + 1))
        .dropWhile { it.isBlank() }
        .joinToString("\n")
}

private fun String.cleanPlainTitle(): String =
    trim()
        .trim('#', '*', '`', '"', '\'', ' ')
        .replace(Regex("\\s+"), " ")

private val NodeDirectiveLine = Regex("^\\s*<!--\\s*(cs-[^>]*?)\\s*-->\\s*(?:\\r?\\n|$)", RegexOption.MULTILINE)
private val NodeTitleDirectiveLine = Regex("^\\s*<!--\\s*cs-title\\s*:\\s*[^>]+?\\s*-->\\s*(?:\\r?\\n|$)", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
private val NodeAreaDirectiveLine = Regex("^\\s*<!--\\s*cs-area\\s*:\\s*[^>]+?\\s*-->\\s*(?:\\r?\\n|$)", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
private val NodeAreaReasonDirectiveLine = Regex("^\\s*<!--\\s*cs-area-reason\\s*:\\s*[^>]+?\\s*-->\\s*(?:\\r?\\n|$)", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
private val NodeCaptureDirectiveLine = Regex("^\\s*<!--\\s*cs-capture\\s*:\\s*[^>]+?\\s*-->\\s*(?:\\r?\\n|$)", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
private val LooseAreaLine = Regex("^\\s*(?:Area|区域|分类)\\s*[:：]\\s*(.+?)\\s*(?:\\r?\\n|$)", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
private val LooseAreaCommentLine = Regex("^\\s*<!--\\s*area\\s*:\\s*([^>]+?)\\s*-->\\s*(?:\\r?\\n|$)", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
private val CaptureTypeDirective = Regex("<!--\\s*cs-capture-type:\\s*([^>]+?)\\s*-->", RegexOption.IGNORE_CASE)
private val MarkdownFenceBlock = Regex("```\\s*([A-Za-z0-9_-]*)\\s*\\n(.*?)\\n```", setOf(RegexOption.DOT_MATCHES_ALL))
private val TitleHeadingLine = Regex("#\\s+\\S.*")
