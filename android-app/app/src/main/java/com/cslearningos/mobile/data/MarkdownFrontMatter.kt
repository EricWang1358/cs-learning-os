package com.cslearningos.mobile.data

data class ParsedMarkdown(
    val metadata: Map<String, String>,
    val body: String
) {
    val title: String?
        get() = metadata["title"]
}

object MarkdownFrontMatter {
    fun parse(markdown: String): ParsedMarkdown {
        val normalized = markdown.trimStart()
        if (!normalized.startsWith("---")) {
            return ParsedMarkdown(metadata = emptyMap(), body = markdown.trim())
        }

        val lines = normalized.lineSequence().toList()
        val closingIndex = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (closingIndex < 0) {
            return ParsedMarkdown(metadata = emptyMap(), body = markdown.trim())
        }

        val metadataLines = lines.drop(1).take(closingIndex)
        val body = lines.drop(closingIndex + 2).joinToString("\n").trim()
        return ParsedMarkdown(
            metadata = metadataLines.mapNotNull(::parseMetadataLine).toMap(),
            body = body
        )
    }

    private fun parseMetadataLine(line: String): Pair<String, String>? {
        val separator = line.indexOf(':')
        if (separator <= 0) return null
        val key = line.take(separator).trim()
        val value = line.drop(separator + 1).trim().trim('"')
        if (key.isBlank() || value.isBlank()) return null
        return key to value
    }
}
