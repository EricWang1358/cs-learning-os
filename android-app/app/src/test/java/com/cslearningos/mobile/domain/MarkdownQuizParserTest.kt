package com.cslearningos.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownQuizParserTest {
    @Test
    fun parsesQuizFenceWithStableAnchor() {
        val markdown = """
            # Binary Search

            :::quiz id=mid-overflow
            question: Why avoid `(l + r) / 2`?
            answer: It can overflow before division.
            explanation: Use `l + (r - l) / 2`.
            :::
        """.trimIndent()

        val cards = MarkdownQuizParser.parse(markdown)

        assertEquals(1, cards.size)
        assertEquals("mid-overflow", cards.single().sourceAnchor)
        assertEquals("Why avoid `(l + r) / 2`?", cards.single().prompt)
        assertEquals("It can overflow before division.", cards.single().answer)
        assertEquals("Use `l + (r - l) / 2`.", cards.single().explanation)
    }

    @Test
    fun derivesAnchorWhenFenceDoesNotProvideOne() {
        val markdown = """
            :::quiz
            question: What does FTS provide?
            answer: Local full-text search.
            :::
        """.trimIndent()

        val cards = MarkdownQuizParser.parse(markdown)

        assertEquals(1, cards.size)
        assertEquals("quiz-1", cards.single().sourceAnchor)
        assertEquals("", cards.single().explanation)
    }
}
