package com.cslearningos.mobile.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizAwareMarkdownDocumentTest {
    @Test
    fun parseSupportsStandardMarkdownBlocksAndInlines() {
        val blocks = QuizAwareMarkdownDocument.parse(
            """
            # Virtual Memory
            
            Intro with **bold**, *italic*, `code`, and [link](https://example.com).
            
            > Quote line
            
            - first bullet
            - second bullet
            
            1. first step
            2. second step
            
            ---
            
            ```c
            mov %rax, %rbx
            ```
            
            | Term | Meaning |
            | --- | --- |
            | TLB | Translation cache |
            """.trimIndent()
        )

        assertEquals(MarkdownHeadingBlock::class.java, blocks[0]::class.java)
        assertEquals(MarkdownParagraphBlock::class.java, blocks[1]::class.java)
        assertEquals(MarkdownQuoteBlock::class.java, blocks[2]::class.java)
        assertEquals(MarkdownListBlock::class.java, blocks[3]::class.java)
        assertEquals(MarkdownListBlock::class.java, blocks[4]::class.java)
        assertEquals(MarkdownHorizontalRuleBlock::class.java, blocks[5]::class.java)
        assertEquals(MarkdownCodeBlock::class.java, blocks[6]::class.java)
        assertEquals(MarkdownTableBlock::class.java, blocks[7]::class.java)

        val paragraph = blocks[1] as MarkdownParagraphBlock
        assertTrue(paragraph.inlines.any { it is MarkdownStrongInline })
        assertTrue(paragraph.inlines.any { it is MarkdownEmphasisInline })
        assertTrue(paragraph.inlines.any { it is MarkdownCodeInline && it.text == "code" })
        assertTrue(
            paragraph.inlines.any {
                it is MarkdownLinkInline &&
                    it.destination == "https://example.com"
            }
        )

        val orderedList = blocks[4] as MarkdownListBlock
        assertTrue(orderedList.ordered)
        assertEquals("1.", orderedList.items.first().marker)

        val table = blocks[7] as MarkdownTableBlock
        assertEquals(2, table.headers.size)
        assertEquals(1, table.rows.size)
    }

    @Test
    fun parsePreservesQuizBlocksBetweenMarkdownSegments() {
        val blocks = QuizAwareMarkdownDocument.parse(
            """
            ## Cache
            
            before quiz
            
            :::quiz id=cache-hit
            question: What does a cache hit avoid?
            answer: A slower lookup.
            :::
            
            after quiz
            """.trimIndent()
        )

        assertEquals(4, blocks.size)
        assertEquals(MarkdownHeadingBlock::class.java, blocks[0]::class.java)
        assertEquals(MarkdownParagraphBlock::class.java, blocks[1]::class.java)
        assertEquals(MarkdownQuizBlock::class.java, blocks[2]::class.java)
        assertEquals(MarkdownParagraphBlock::class.java, blocks[3]::class.java)

        val quiz = blocks[2] as MarkdownQuizBlock
        assertEquals("id=cache-hit", quiz.info)
        assertEquals("question: What does a cache hit avoid?", quiz.lines.first())
    }

    @Test
    fun parseNormalizesCommonAiMarkdownHeadingMistakes() {
        val blocks = QuizAwareMarkdownDocument.parse(
            """
            ##Prompt basics

            text#Task description

            ```text#Output format
            - Use Markdown.
            ```
            """.trimIndent()
        )

        assertEquals(4, blocks.size)
        assertEquals(MarkdownHeadingBlock::class.java, blocks[0]::class.java)
        assertEquals(MarkdownHeadingBlock::class.java, blocks[1]::class.java)
        assertEquals(MarkdownHeadingBlock::class.java, blocks[2]::class.java)
        assertEquals(MarkdownListBlock::class.java, blocks[3]::class.java)
        assertEquals(2, (blocks[0] as MarkdownHeadingBlock).level)
        assertEquals(1, (blocks[1] as MarkdownHeadingBlock).level)
        assertEquals(1, (blocks[2] as MarkdownHeadingBlock).level)
    }

    @Test
    fun parseSplitsInlineOpeningFenceAfterTextInsteadOfLeavingEmptyTrailingCodeBlock() {
        val blocks = QuizAwareMarkdownDocument.parse(
            """
            ## Examples or cases

            定义接口```kotlin
            interface MyInterface {
            fun bar() // abstract method
            }
            ```
            """.trimIndent()
        )

        assertEquals(3, blocks.size)
        assertEquals(MarkdownHeadingBlock::class.java, blocks[0]::class.java)
        assertEquals(MarkdownParagraphBlock::class.java, blocks[1]::class.java)
        assertEquals(MarkdownCodeBlock::class.java, blocks[2]::class.java)
        val code = blocks[2] as MarkdownCodeBlock
        assertEquals("kotlin", code.info)
        assertTrue(code.code.contains("interface MyInterface"))
        assertTrue(code.code.contains("fun bar()"))
    }
}
