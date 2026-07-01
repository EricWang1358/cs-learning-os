package com.cslearningos.mobile.ui.markdown

object QuizAwareMarkdownDocument {
    fun parse(markdown: String): List<MarkdownBlock> {
        if (markdown.isBlank()) return emptyList()

        val blocks = mutableListOf<MarkdownBlock>()
        val buffer = mutableListOf<String>()
        val lines = markdown.lines()
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
                    val info = trimmed.removePrefix(":::").trim().removePrefix("quiz").trim()
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
}
