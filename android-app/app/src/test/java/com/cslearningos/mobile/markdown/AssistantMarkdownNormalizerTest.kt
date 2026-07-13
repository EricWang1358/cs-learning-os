package com.cslearningos.mobile.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMarkdownNormalizerTest {
    @Test
    fun normalizeSplitsCollapsedHeadingsAndBullets() {
        val normalized = AssistantMarkdownNormalizer.normalize(
            "# Kotlin##Core concepts-Source files use the .kt extension"
        )

        assertEquals(
            """
            # Kotlin
            ## Core concepts-Source files use the .kt extension
            """.trimIndent(),
            normalized
        )
    }

    @Test
    fun normalizeClosesBrokenFenceBeforeNextHeading() {
        val normalized = AssistantMarkdownNormalizer.normalize(
            """
            ```kotlin
            fun main() {}
            ## Common mistakes
            - Missing main arguments
            """.trimIndent()
        )

        assertTrue(normalized.contains("```\n## Common mistakes"))
        assertTrue(normalized.contains("- Missing main arguments"))
    }

    @Test
    fun normalizeSplitsMalformedFenceHeaderFromFirstCodeLine() {
        val normalized = AssistantMarkdownNormalizer.normalize(
            """
            ```kotlinpackage hello
            fun main() {}
            ```
            """.trimIndent()
        )

        assertEquals(
            """
            ```kotlin
            package hello
            fun main() {}
            ```
            """.trimIndent(),
            normalized
        )
    }

    @Test
    fun normalizeUnwrapsFenceInfoHeadingIntoPlainHeading() {
        val normalized = AssistantMarkdownNormalizer.normalize(
            """
            ```text#Output format
            - Use Markdown.
            ```
            """.trimIndent()
        )

        assertEquals(
            """
            # Output format
            - Use Markdown.
            """.trimIndent(),
            normalized
        )
    }

    @Test
    fun normalizeSplitsHeadingThatWasCollapsedIntoTableHeader() {
        val normalized = AssistantMarkdownNormalizer.normalize(
            """
            ## Examples|Request | Result |
            |---|---|
            |Read a PDF|No skill
            """.trimIndent()
        )

        assertEquals(
            """
            ## Examples

            |Request | Result |
            |---|---|
            |Read a PDF|No skill|
            """.trimIndent(),
            normalized
        )
    }

    @Test
    fun normalizeRepairsCompleteGfmTableBoundariesAndSeparatesItFromParagraph() {
        val normalized = AssistantMarkdownNormalizer.normalize(
            """
            Recommended Areas:
            Topic | Area
            --- | ---
            Virtual Memory | cs-fundamentals
            """.trimIndent()
        )

        assertEquals(
            """
            Recommended Areas:

            |Topic | Area|
            |--- | ---|
            |Virtual Memory | cs-fundamentals|
            """.trimIndent(),
            normalized
        )
    }

    @Test
    fun normalizeRepairsTableWithPipeInsideInlineCodeCell() {
        val normalized = AssistantMarkdownNormalizer.normalize(
            """
            Syntax | Example
            --- | ---
            Kotlin | `a | b`
            """.trimIndent()
        )

        assertEquals(
            """
            |Syntax | Example|
            |--- | ---|
            |Kotlin | `a | b`|
            """.trimIndent(),
            normalized
        )
    }

    @Test
    fun normalizeSeparatesAdjacentDifferentWidthTablesWithOneBlankLine() {
        val normalized = AssistantMarkdownNormalizer.normalize(
            """
            Name | Value
            --- | ---
            one | 1
            Left | Middle | Right
            --- | --- | ---
            a | b | c
            """.trimIndent()
        )

        assertEquals(
            """
            |Name | Value|
            |--- | ---|
            |one | 1|

            |Left | Middle | Right|
            |--- | --- | ---|
            |a | b | c|
            """.trimIndent(),
            normalized
        )
    }

    @Test
    fun normalizeDoesNotRepairTableLikeContentInsideCodeOrQuizFences() {
        val markdown = """
            ```text
            Topic | Area
            --- | ---
            Virtual Memory | cs-fundamentals
            ```

            :::quiz
            Topic | Area
            --- | ---
            Virtual Memory | cs-fundamentals
            :::
            """.trimIndent()

        assertEquals(markdown, AssistantMarkdownNormalizer.normalize(markdown))
    }

    @Test
    fun normalizeLeavesIncompleteTableCandidateAndPipeProseUntouched() {
        val markdown = """
            Topic | Area
            --- | ---

            Use `left | right` when describing a pipe.
            """.trimIndent()

        assertEquals(markdown, AssistantMarkdownNormalizer.normalize(markdown))
    }
}
