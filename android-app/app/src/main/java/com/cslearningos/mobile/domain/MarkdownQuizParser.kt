package com.cslearningos.mobile.domain

import java.security.MessageDigest

data class ParsedQuizCard(
    val sourceAnchor: String,
    val prompt: String,
    val answer: String,
    val explanation: String
)

object MarkdownQuizParser {
    private val StartRegex = Regex("""^:::\s*quiz(?:\s+id=([A-Za-z0-9_-]+))?\s*(.*)$""")
    private val FieldRegex = Regex("""^(question|answer|explanation)\s*:\s*(.*)$""", RegexOption.IGNORE_CASE)

    fun parse(markdown: String): List<ParsedQuizCard> {
        val lines = markdown.lines()
        val cards = mutableListOf<ParsedQuizCard>()
        var index = 0

        while (index < lines.size) {
            val match = StartRegex.matchEntire(lines[index].trim())
            if (match == null) {
                index += 1
                continue
            }

            val fields = linkedMapOf<String, String>()
            val inlinePayload = match.groupValues.getOrNull(2).orEmpty().trim()
            if (inlinePayload.isNotBlank()) {
                applyQuizLine(inlinePayload, fields)
            }
            index += 1
            while (index < lines.size && lines[index].trim() != ":::") {
                applyQuizLine(lines[index], fields)
                index += 1
            }

            val prompt = fields["question"].orEmpty()
            val answer = fields["answer"].orEmpty()
            if (prompt.isNotBlank() && answer.isNotBlank()) {
                cards += ParsedQuizCard(
                    sourceAnchor = match.groupValues.getOrNull(1)
                        ?.takeIf { it.isNotBlank() }
                        ?: stableAnonymousAnchor(prompt = prompt, answer = answer),
                    prompt = prompt,
                    answer = answer,
                    explanation = fields["explanation"].orEmpty()
                )
            }
            index += 1
        }

        return cards
    }

    private fun applyQuizLine(line: String, fields: MutableMap<String, String>): String? {
        val trimmed = line.trim()
        val field = FieldRegex.matchEntire(trimmed)
        if (field != null) {
            val key = field.groupValues[1].lowercase()
            fields[key] = field.groupValues[2].trim()
            return key
        }

        val currentKey = fields.keys.lastOrNull() ?: return null
        if (trimmed.isNotBlank()) {
            fields[currentKey] = listOf(fields[currentKey].orEmpty(), trimmed)
                .filter { it.isNotBlank() }
                .joinToString("\n")
        }
        return currentKey
    }

    private fun stableAnonymousAnchor(prompt: String, answer: String): String {
        val raw = "$prompt\n---\n$answer"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "quiz-$digest"
    }
}
