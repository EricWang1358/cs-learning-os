package com.cslearningos.mobile.domain

import java.security.MessageDigest

data class ParsedQuizCard(
    val sourceAnchor: String,
    val prompt: String,
    val answer: String,
    val explanation: String
)

object MarkdownQuizParser {
    private val StartRegex = Regex("""^:::\s*quiz(?:\s+id=([A-Za-z0-9_-]+))?\s*$""")

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
            var currentKey: String? = null
            index += 1
            while (index < lines.size && lines[index].trim() != ":::") {
                val line = lines[index]
                val delimiter = line.indexOf(':')
                if (delimiter > 0) {
                    val key = line.substring(0, delimiter).trim().lowercase()
                    val value = line.substring(delimiter + 1).trim()
                    fields[key] = value
                    currentKey = key
                } else if (currentKey != null && line.startsWith(" ")) {
                    val continuation = line.trim()
                    if (continuation.isNotBlank()) {
                        fields[currentKey] = listOf(fields[currentKey].orEmpty(), continuation)
                            .filter { it.isNotBlank() }
                            .joinToString("\n")
                    }
                }
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

    private fun stableAnonymousAnchor(prompt: String, answer: String): String {
        val raw = "$prompt\n---\n$answer"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "quiz-$digest"
    }
}
