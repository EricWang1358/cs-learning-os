package com.cslearningos.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterContentImporterTest {
    @Test
    fun frontMatterExtractsTitleAndKeepsPortableMarkdownBody() {
        val parsed = MarkdownFrontMatter.parse(
            """
            ---
            title: "Binary Search"
            area: algorithms
            track: search-patterns
            ---

            # Binary Search

            ## Core Idea

            Keep a monotonic boundary.
            """.trimIndent()
        )

        assertEquals("Binary Search", parsed.title)
        assertEquals("algorithms", parsed.metadata["area"])
        assertTrue(parsed.body.startsWith("# Binary Search"))
        assertTrue(parsed.body.contains("## Core Idea"))
    }

    @Test
    fun starterContentCreatesNodesFromDesktopMarkdownFiles() {
        val pack = StarterContentImporter.fromMarkdownAssets(
            nodeFiles = listOf(
                MarkdownAsset(
                    path = "nodes/algorithms/binary-search.md",
                    text = """
                        ---
                        title: "Binary Search"
                        area: algorithms
                        track: search-patterns
                        ---

                        # Binary Search

                        ## Why It Matters

                        Find a boundary.
                    """.trimIndent()
                )
            ),
            quizFiles = emptyList(),
            now = 1_000L
        )

        assertEquals(1, pack.nodes.size)
        assertEquals("starter:node:algorithms/binary-search", pack.nodes.single().id)
        assertEquals("Binary Search", pack.nodes.single().title)
        assertTrue(pack.nodes.single().markdownBody.startsWith("# Binary Search"))
    }

    @Test
    fun starterContentCreatesDueQuizFromStandaloneQuizMarkdown() {
        val pack = StarterContentImporter.fromMarkdownAssets(
            nodeFiles = emptyList(),
            quizFiles = listOf(
                MarkdownAsset(
                    path = "quizzes/cs-fundamentals/gdb.md",
                    text = """
                        ---
                        title: "GDB basics"
                        ---

                        # GDB basics

                        ## Prompt

                        What does `stepi` do?

                        ## Answer

                        It executes one machine instruction.

                        ## Explanation

                        Use it while inspecting assembly.
                    """.trimIndent()
                )
            ),
            now = 2_000L
        )

        assertEquals(1, pack.quizzes.size)
        assertEquals("starter:quiz:cs-fundamentals/gdb", pack.quizzes.single().id)
        assertEquals("What does `stepi` do?", pack.quizzes.single().prompt)
        assertEquals("It executes one machine instruction.", pack.quizzes.single().answer)
        assertEquals(2_000L, pack.reviewStates.single().dueAt)
    }
}
