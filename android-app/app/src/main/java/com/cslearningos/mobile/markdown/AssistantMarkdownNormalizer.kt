package com.cslearningos.mobile.markdown

/**
 * Normalizes common malformed Markdown produced by chat models before parsing
 * or saving assistant drafts. The rules are intentionally narrow: they only
 * fix heading syntax that is visibly intended to be a heading and unwrap
 * accidental fenced blocks whose info string is actually a heading.
 */
object AssistantMarkdownNormalizer {
    fun normalize(markdown: String): String {
        if (markdown.isBlank()) return markdown.trim()
        val lines = markdown
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(InlineFenceStart, "\n$1")
            .lines()
        val normalized = mutableListOf<String>()
        var index = 0
        var openFence: String? = null
        var openFenceInfo: String? = null
        var ignoredClosingFence: String? = null

        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trimStart()

            if (ignoredClosingFence != null && trimmed == ignoredClosingFence) {
                ignoredClosingFence = null
                index += 1
                continue
            }

            if (openFence != null) {
                if (trimmed == openFence) {
                    normalized += trimmed
                    openFence = null
                    openFenceInfo = null
                    index += 1
                    continue
                }
                if (shouldCloseFenceBeforeLine(trimmed, openFenceInfo)) {
                    normalized += openFence
                    openFence = null
                    openFenceInfo = null
                    continue
                }
                normalized += normalizeFenceBodyLine(line)
                index += 1
                continue
            }

            val malformedFence = MalformedFenceHeader.matchEntire(trimmed)
            if (malformedFence != null) {
                val fence = malformedFence.groupValues[1]
                val info = malformedFence.groupValues[2]
                val firstCodeLine = malformedFence.groupValues[3].trimStart()
                val accidentalHeading = headingFromFenceInfo(info + firstCodeLine)
                if (accidentalHeading != null) {
                    normalized += accidentalHeading
                    ignoredClosingFence = fence
                    index += 1
                    continue
                }
                normalized += "$fence$info"
                if (firstCodeLine.isNotBlank()) {
                    normalized += firstCodeLine
                }
                openFence = fence
                openFenceInfo = info
                index += 1
                continue
            }

            val fenceLine = FenceLine.matchEntire(trimmed)
            if (fenceLine != null) {
                val fence = fenceLine.groupValues[1]
                val info = fenceLine.groupValues[2].trim()
                val accidentalHeading = headingFromFenceInfo(info)
                if (accidentalHeading != null) {
                    normalized += accidentalHeading
                    ignoredClosingFence = fence
                    index += 1
                    continue
                }
                normalized += if (info.isBlank()) fence else "$fence$info"
                openFence = fence
                openFenceInfo = info
                index += 1
                continue
            }

            val splitHeadingTable = splitHeadingCollapsedIntoTableHeader(line, lines.getOrNull(index + 1))
            if (splitHeadingTable != null) {
                normalized += splitHeadingTable
                index += 1
                continue
            }

            normalized += normalizeLine(line)
            index += 1
        }

        if (openFence != null) {
            normalized += openFence
        }

        return normalized.joinToString("\n").trim()
    }

    private fun normalizeLine(line: String): String {
        val indent = line.takeWhile { it == ' ' || it == '\t' }
        val content = line.drop(indent.length)
        val expandedContent = expandInlineStructures(content)
        val expandedLines = expandedContent.lines()
        if (expandedLines.size > 1) {
            return expandedLines.joinToString("\n") { expandedLine -> normalizeLine(indent + expandedLine) }
        }
        val normalizedContent = expandedLines.single()
        val textPrefixedHeading = TextPrefixedHeading.matchEntire(normalizedContent)
        if (textPrefixedHeading != null) {
            return indent + normalizeHeading(textPrefixedHeading.groupValues[1] + textPrefixedHeading.groupValues[2])
        }
        val heading = TightHeading.matchEntire(normalizedContent)
        if (heading != null) {
            return indent + normalizeHeading(heading.groupValues[1] + heading.groupValues[2])
        }
        val bullet = TightBullet.matchEntire(normalizedContent)
        if (bullet != null) {
            return indent + "${bullet.groupValues[1]} ${bullet.groupValues[2].trimStart()}"
        }
        return (indent + normalizedContent).trimEnd()
    }

    private fun normalizeHeading(raw: String): String {
        val match = TightHeading.matchEntire(raw.trim()) ?: return raw.trim()
        return "${match.groupValues[1]} ${match.groupValues[2].trim()}"
    }

    private fun normalizeFenceBodyLine(line: String): String = line.trimEnd()

    private fun headingFromFenceInfo(info: String): String? {
        val trimmed = info.trim()
        val textPrefixedHeading = TextPrefixedHeading.matchEntire(trimmed)
        if (textPrefixedHeading != null) {
            return normalizeHeading(textPrefixedHeading.groupValues[1] + textPrefixedHeading.groupValues[2])
        }
        val tightHeading = TightHeading.matchEntire(trimmed)
        if (tightHeading != null) {
            return normalizeHeading(tightHeading.groupValues[1] + tightHeading.groupValues[2])
        }
        return null
    }

    private fun shouldCloseFenceBeforeLine(trimmed: String, fenceInfo: String?): Boolean {
        if (trimmed.isBlank()) return false
        if (fenceInfo.isNullOrBlank()) {
            return MarkdownSectionLine.matches(trimmed)
        }
        return MarkdownHeadingLine.matches(trimmed)
    }

    private fun expandInlineStructures(content: String): String {
        var normalized = content
        normalized = normalized.replace(InlineHeading, "\n$1 ")
        return normalized
    }

    private fun splitHeadingCollapsedIntoTableHeader(line: String, nextLine: String?): List<String>? {
        val trimmedNext = nextLine?.trim().orEmpty()
        if (!TableDelimiterLine.matches(trimmedNext)) return null

        val indent = line.takeWhile { it == ' ' || it == '\t' }
        val content = line.drop(indent.length)
        val match = HeadingWithTableHeader.matchEntire(content) ?: return null
        val heading = normalizeHeading(match.groupValues[1])
        val header = match.groupValues[2].trim().trimStart('|')
        if (header.isBlank()) return null
        return listOf(
            indent + heading,
            "",
            indent + "|$header"
        )
    }

    private val TightHeading = Regex("^(#{1,6})([^#\\s].*)$")
    private val TextPrefixedHeading = Regex("(?i)^text(#{1,6})([^#\\s].*)$")
    private val TightBullet = Regex("^([-*])([^\\s].*)$")
    private val InlineFenceStart = Regex("(?<=\\S)(```|~~~)(?=[A-Za-z0-9]|$)")
    private val InlineHeading = Regex("(?<=\\S)(#{2,6})(?=[^#\\s])")
    private val FenceLine = Regex("^(```|~~~)\\s*([^\\s].*)?$")
    private val MalformedFenceHeader = Regex("^(```|~~~)(kotlin|java|json|text|markdown|md|xml|yaml|yml|bash|sh|python|js|ts)([^\\s].+)$", RegexOption.IGNORE_CASE)
    private val MarkdownHeadingLine = Regex("^#{1,6}\\s*[^#\\s].*$")
    private val MarkdownSectionLine = Regex("^(#{1,6}\\s*[^#\\s].*|[-*]\\s+.+)$")
    private val HeadingWithTableHeader = Regex("^(#{1,6}\\s+[^|]+?)\\|(.+\\|\\s*)$")
    private val TableDelimiterLine = Regex("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$")
}
