package com.cslearningos.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
        assertTrue(cards.single().sourceAnchor.startsWith("quiz-"))
        assertEquals("", cards.single().explanation)
    }

    @Test
    fun anonymousAnchorFollowsContentWhenEarlierAnonymousBlockIsRemoved() {
        val original = """
            :::quiz
            question: First card?
            answer: First answer.
            :::

            :::quiz
            question: Second card?
            answer: Second answer.
            :::
        """.trimIndent()
        val edited = """
            :::quiz
            question: Second card?
            answer: Second answer.
            :::
        """.trimIndent()

        val originalCards = MarkdownQuizParser.parse(original)
        val editedCard = MarkdownQuizParser.parse(edited).single()

        assertNotEquals(originalCards[0].sourceAnchor, originalCards[1].sourceAnchor)
        assertEquals(originalCards[1].sourceAnchor, editedCard.sourceAnchor)
    }

    @Test
    fun parsesIndentedMultilineFields() {
        val markdown = """
            :::quiz id=stack-frame
            question: Why does `call` change `%rsp`?
              Mention the return address.
            answer: It pushes the return address.
            explanation: The CPU stores where execution should resume.
              That address becomes the top stack value.
            :::
        """.trimIndent()

        val card = MarkdownQuizParser.parse(markdown).single()

        assertEquals("Why does `call` change `%rsp`?\nMention the return address.", card.prompt)
        assertEquals("It pushes the return address.", card.answer)
        assertEquals(
            "The CPU stores where execution should resume.\nThat address becomes the top stack value.",
            card.explanation
        )
    }
}
