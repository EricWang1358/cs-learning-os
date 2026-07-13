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
    private val FieldLabelRegex = Regex("""\b(question|answer|explanation)\s*:\s*""", RegexOption.IGNORE_CASE)

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

    private fun applyQuizLine(line: String, fields: MutableMap<String, String>) {
        val trimmed = line.trim()
        val labels = FieldLabelRegex.findAll(trimmed).toList()
        if (labels.isNotEmpty()) {
            val firstLabel = labels.first()
            val prefix = trimmed.substring(0, firstLabel.range.first).trim()
            val currentKey = fields.keys.lastOrNull()
            if (prefix.isNotBlank() && currentKey != null) {
                fields.appendFieldValue(currentKey, prefix)
            }
            labels.forEachIndexed { index, match ->
                val key = match.groupValues[1].lowercase()
                val valueStart = match.range.last + 1
                val valueEnd = labels.getOrNull(index + 1)?.range?.first ?: trimmed.length
                fields.appendFieldValue(key, trimmed.substring(valueStart, valueEnd).trim())
            }
            return
        }

        val currentKey = fields.keys.lastOrNull() ?: return
        if (trimmed.isNotBlank()) {
            fields.appendFieldValue(currentKey, trimmed)
        }
    }

    private fun MutableMap<String, String>.appendFieldValue(key: String, value: String) {
        if (value.isBlank()) {
            putIfAbsent(key, "")
            return
        }
        this[key] = listOf(this[key].orEmpty(), value)
            .filter { it.isNotBlank() }
            .joinToString("\n")
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
