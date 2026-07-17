package com.cslearningos.mobile.feature.sync

/** Parses the desktop quiz Markdown layout (## Prompt / ## Answer / ## Explanation). */
object DesktopQuizMarkdown {

    data class ParsedDesktopQuiz(
        val prompt: String,
        val answer: String,
        val explanation: String
    )

    private val SectionRegex = Regex("^##\\s+(Prompt|Answer|Explanation)\\s*$", RegexOption.IGNORE_CASE)

    fun parse(body: String): ParsedDesktopQuiz? {
        val sections = linkedMapOf<String, StringBuilder>()
        var current: String? = null
        body.lineSequence().forEach { line ->
            val match = SectionRegex.matchEntire(line.trim())
            if (match != null) {
                current = match.groupValues[1].lowercase()
                sections.getOrPut(current!!) { StringBuilder() }
            } else if (current != null) {
                sections.getValue(current!!).appendLine(line)
            }
        }
        val prompt = sections["prompt"]?.toString()?.trim().orEmpty()
        val answer = sections["answer"]?.toString()?.trim().orEmpty()
        if (prompt.isBlank() || answer.isBlank()) return null
        return ParsedDesktopQuiz(
            prompt = prompt,
            answer = answer,
            explanation = sections["explanation"]?.toString()?.trim().orEmpty()
        )
    }
}
