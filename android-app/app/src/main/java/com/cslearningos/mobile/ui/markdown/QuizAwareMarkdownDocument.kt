package com.cslearningos.mobile.ui.markdown

import com.cslearningos.mobile.markdown.AssistantMarkdownNormalizer

object QuizAwareMarkdownDocument {
    fun parse(markdown: String): List<MarkdownBlock> {
        if (StandardMarkdownDocument.requiresPlainTextFallback(markdown)) {
            return StandardMarkdownDocument.plainTextFallback(markdown)
        }
        val normalizedMarkdown = AssistantMarkdownNormalizer.normalize(markdown)
        if (StandardMarkdownDocument.requiresPlainTextFallback(normalizedMarkdown)) {
            return StandardMarkdownDocument.plainTextFallback(normalizedMarkdown)
        }
        if (normalizedMarkdown.isBlank()) return emptyList()

        val blocks = mutableListOf<MarkdownBlock>()
        val buffer = mutableListOf<String>()
        val lines = normalizedMarkdown.lines()
        var index = 0
        var openFence: String? = null

        fun flushBuffer() {
            if (buffer.none { it.isNotBlank() }) {
                buffer.clear()
                return
            }
            blocks += StandardMarkdownDocument.parse(buffer.joinToString("\n"))
            buffer.clear()
        }

        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trimStart()

            if (openFence != null) {
                buffer += line
                if (trimmed.startsWith(openFence!!)) {
                    openFence = null
                }
                index += 1
                continue
            }

            when {
                trimmed.startsWith("```") -> {
                    openFence = "```"
                    buffer += line
                }

                trimmed.startsWith("~~~") -> {
                    openFence = "~~~"
                    buffer += line
                }

                trimmed.startsWith(":::") && trimmed.removePrefix(":::").trimStart().startsWith("quiz") -> {
                    flushBuffer()
                    val quizLines = mutableListOf<String>()
                    val rawInfo = trimmed.removePrefix(":::").trim().removePrefix("quiz").trim()
                    val info = quizInfo(rawInfo)
                    quizPayload(rawInfo)?.let { quizLines += it }
                    index += 1
                    while (index < lines.size && lines[index].trim() != ":::") {
                        quizLines += lines[index].trimEnd()
                        index += 1
                    }
                    blocks += MarkdownQuizBlock(info = info, lines = quizLines)
                }

                else -> buffer += line
            }

            index += 1
        }

        flushBuffer()
        return blocks
    }

    private fun quizInfo(rawInfo: String): String {
        val trimmed = rawInfo.trim()
        if (trimmed.startsWithQuizField()) return ""
        val idMatch = QuizInfoWithInlinePayload.matchEntire(trimmed)
        return idMatch?.groupValues?.get(1).orEmpty().ifBlank { trimmed }
    }

    private fun quizPayload(rawInfo: String): String? {
        val trimmed = rawInfo.trim()
        if (trimmed.startsWithQuizField()) return trimmed
        val idMatch = QuizInfoWithInlinePayload.matchEntire(trimmed) ?: return null
        return idMatch.groupValues[2].trim().takeIf { it.startsWithQuizField() }
    }

    private fun String.startsWithQuizField(): Boolean =
        QuizFieldPrefix.containsMatchIn(this)

    private val QuizFieldPrefix = Regex("""^(question|answer|explanation)\s*:""", RegexOption.IGNORE_CASE)
    private val QuizInfoWithInlinePayload = Regex("""^(id=[A-Za-z0-9_-]+)\s+(.+)$""")
}
