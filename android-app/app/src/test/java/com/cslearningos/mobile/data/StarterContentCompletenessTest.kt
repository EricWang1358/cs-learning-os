package com.cslearningos.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterContentCompletenessTest {
    @Test
    fun starterPackIsRichEnoughToDemonstrateMobileLibraryAndReviewOrder() {
        val pack = StarterContentImporter.fromMarkdownAssets(
            nodeFiles = listOf(
                node("nodes/cs-fundamentals/x86.md", "x86", "cs-fundamentals", "x86-64-assembly", 30),
                node("nodes/cs-fundamentals/virtual-memory.md", "Virtual Memory", "cs-fundamentals", "memory-hierarchy", 20),
                node("nodes/algorithms/binary-search.md", "Binary Search", "algorithms", "search-patterns", 10),
                node("nodes/questions/capture-inbox.md", "Capture Inbox", "questions", "inbox", 10)
            ),
            quizFiles = listOf(
                quiz("quizzes/cs-fundamentals/cache.md", "Cache locality", "cs-fundamentals", "memory-hierarchy"),
                quiz("quizzes/cs-fundamentals/gdb.md", "GDB stepi", "cs-fundamentals", "gdb-debugging")
            ),
            now = 1_000L
        )

        assertTrue(pack.nodes.map { it.area }.toSet().size >= 3)
        assertTrue(pack.nodes.filter { it.area == "cs-fundamentals" }.map { it.track }.toSet().size >= 2)
        assertEquals(2, pack.quizzes.size)
        assertEquals(listOf(1_000L, 1_000L), pack.reviewStates.map { it.dueAt })
    }

    private fun node(path: String, title: String, area: String, track: String, order: Int) =
        MarkdownAsset(
            path = path,
            text = """
                ---
                title: "$title"
                area: $area
                track: $track
                order: $order
                visibility: core
                summary: "$title summary."
                ---

                # $title

                ## Core

                $title body.
            """.trimIndent()
        )

    private fun quiz(path: String, title: String, area: String, track: String) =
        MarkdownAsset(
            path = path,
            text = """
                ---
                title: "$title"
                area: $area
                track: $track
                ---

                # $title

                ## Prompt

                What is $title?

                ## Answer

                A concrete review answer for $title.

                ## Explanation

                A detailed explanation so the demo does not feel empty.
            """.trimIndent()
        )
}
