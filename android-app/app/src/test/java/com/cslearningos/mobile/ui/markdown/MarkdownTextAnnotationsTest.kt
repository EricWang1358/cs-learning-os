package com.cslearningos.mobile.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextAnnotationsTest {
    @Test
    fun buildMarkdownAnnotatedTextPreservesLinkDestinationsLineBreaksAndCodeSpans() {
        val result = buildMarkdownAnnotatedText(
            listOf(
                MarkdownTextInline("See "),
                MarkdownLinkInline(
                    destination = "https://example.com/cache",
                    children = listOf(MarkdownTextInline("cache article"))
                ),
                MarkdownTextInline(" then run "),
                MarkdownCodeInline("mov"),
                MarkdownLineBreakInline,
                MarkdownStrongInline(listOf(MarkdownTextInline("carefully")))
            )
        )

        val annotations = result.text.getStringAnnotations(
            tag = MarkdownLinkAnnotationTag,
            start = 0,
            end = result.text.length
        )

        assertEquals("See cache article then run mov\ncarefully", result.text.text)
        assertEquals(1, annotations.size)
        assertEquals("https://example.com/cache", annotations.single().item)
        assertTrue(result.codeSpans.any { it.text == "mov" })
    }
}
