package com.cslearningos.mobile.ui.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

const val MarkdownLinkAnnotationTag = "markdown-link"

data class MarkdownCodeSpan(
    val text: String,
    val start: Int,
    val end: Int
)

data class MarkdownAnnotatedText(
    val text: AnnotatedString,
    val codeSpans: List<MarkdownCodeSpan>
)

fun buildMarkdownAnnotatedText(inlines: List<MarkdownInline>): MarkdownAnnotatedText {
    val codeSpans = mutableListOf<MarkdownCodeSpan>()
    val text = buildAnnotatedString {
        appendMarkdownInlines(
            inlines = inlines,
            codeSpans = codeSpans
        )
    }
    return MarkdownAnnotatedText(text = text, codeSpans = codeSpans)
}

private fun AnnotatedString.Builder.appendMarkdownInlines(
    inlines: List<MarkdownInline>,
    codeSpans: MutableList<MarkdownCodeSpan>
) {
    inlines.forEach { inline ->
        when (inline) {
            is MarkdownCodeInline -> {
                val start = length
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace
                    )
                ) {
                    append(inline.text)
                }
                codeSpans += MarkdownCodeSpan(
                    text = inline.text,
                    start = start,
                    end = length
                )
            }

            is MarkdownEmphasisInline -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendMarkdownInlines(inline.children, codeSpans)
            }

            MarkdownLineBreakInline -> append("\n")
            is MarkdownLinkInline -> {
                val start = length
                if (inline.children.isEmpty()) {
                    append(inline.destination)
                } else {
                    appendMarkdownInlines(inline.children, codeSpans)
                }
                if (length > start) {
                    addStringAnnotation(
                        tag = MarkdownLinkAnnotationTag,
                        annotation = inline.destination,
                        start = start,
                        end = length
                    )
                    addStyle(
                        style = SpanStyle(textDecoration = TextDecoration.Underline),
                        start = start,
                        end = length
                    )
                }
            }

            is MarkdownStrongInline -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendMarkdownInlines(inline.children, codeSpans)
            }

            is MarkdownTextInline -> append(inline.text)
        }
    }
}
