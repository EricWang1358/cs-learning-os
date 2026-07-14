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

    @Test
    fun buildMarkdownAnnotatedTextOnlyAnnotatesHttpsLinks() {
        val unsafeLinks = listOf(
            "http://example.com/insecure",
            "javascript:alert(1)",
            "file:///data/local/tmp/secret",
            "content://com.example.provider/data",
            "intent://scan/#Intent;scheme=zxing;end"
        )
        val result = buildMarkdownAnnotatedText(
            unsafeLinks.mapIndexed { index, destination ->
                MarkdownLinkInline(
                    destination = destination,
                    children = listOf(MarkdownTextInline("unsafe-$index"))
                )
            } + listOf(
                MarkdownLinkInline(
                    destination = "https://example.com/secure",
                    children = listOf(MarkdownTextInline("safe"))
                )
            )
        )

        val annotations = result.text.getStringAnnotations(
            tag = MarkdownLinkAnnotationTag,
            start = 0,
            end = result.text.length
        )

        assertEquals("unsafe-0unsafe-1unsafe-2unsafe-3unsafe-4safe", result.text.text)
        assertEquals(listOf("https://example.com/secure"), annotations.map { it.item })
    }
}
