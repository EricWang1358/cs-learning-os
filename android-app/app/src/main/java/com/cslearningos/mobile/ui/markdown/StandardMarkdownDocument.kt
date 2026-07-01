package com.cslearningos.mobile.ui.markdown

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListBlock as CommonmarkListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

sealed interface MarkdownBlock

data class MarkdownHeadingBlock(
    val level: Int,
    val inlines: List<MarkdownInline>
) : MarkdownBlock

data class MarkdownParagraphBlock(
    val inlines: List<MarkdownInline>
) : MarkdownBlock

data class MarkdownListBlock(
    val ordered: Boolean,
    val items: List<MarkdownListItem>
) : MarkdownBlock

data class MarkdownListItem(
    val depth: Int,
    val marker: String,
    val blocks: List<MarkdownBlock>
)

data class MarkdownQuoteBlock(
    val blocks: List<MarkdownBlock>
) : MarkdownBlock

data class MarkdownCodeBlock(
    val info: String,
    val code: String
) : MarkdownBlock

data object MarkdownHorizontalRuleBlock : MarkdownBlock

data class MarkdownTableBlock(
    val headers: List<List<MarkdownInline>>,
    val rows: List<List<List<MarkdownInline>>>
) : MarkdownBlock

data class MarkdownQuizBlock(
    val info: String,
    val lines: List<String>
) : MarkdownBlock

sealed interface MarkdownInline

data class MarkdownTextInline(
    val text: String
) : MarkdownInline

data class MarkdownStrongInline(
    val children: List<MarkdownInline>
) : MarkdownInline

data class MarkdownEmphasisInline(
    val children: List<MarkdownInline>
) : MarkdownInline

data class MarkdownCodeInline(
    val text: String
) : MarkdownInline

data class MarkdownLinkInline(
    val destination: String,
    val children: List<MarkdownInline>
) : MarkdownInline

data object MarkdownLineBreakInline : MarkdownInline

object StandardMarkdownDocument {
    private val parser = Parser.builder()
        .extensions(listOf(TablesExtension.create()))
        .build()

    fun parse(markdown: String): List<MarkdownBlock> {
        if (markdown.isBlank()) return emptyList()
        val document = parser.parse(markdown)
        return parseChildren(document, depth = 0)
    }

    private fun parseChildren(parent: Node, depth: Int): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        var child = parent.firstChild

        while (child != null) {
            when (child) {
                is Heading -> blocks += MarkdownHeadingBlock(
                    level = child.level,
                    inlines = parseInlines(child)
                )

                is Paragraph -> blocks += MarkdownParagraphBlock(parseInlines(child))
                is BlockQuote -> blocks += MarkdownQuoteBlock(parseChildren(child, depth))
                is org.commonmark.node.BulletList -> blocks += parseList(child, depth)
                is OrderedList -> blocks += parseList(child, depth)
                is FencedCodeBlock -> blocks += MarkdownCodeBlock(
                    info = child.info.orEmpty().trim(),
                    code = child.literal.orEmpty().trimEnd()
                )

                is IndentedCodeBlock -> blocks += MarkdownCodeBlock(
                    info = "",
                    code = child.literal.orEmpty().trimEnd()
                )

                is ThematicBreak -> blocks += MarkdownHorizontalRuleBlock
                is TableBlock -> blocks += parseTable(child)
                is HtmlBlock -> {
                    val text = child.literal.orEmpty().trim()
                    if (text.isNotBlank()) {
                        blocks += MarkdownParagraphBlock(listOf(MarkdownTextInline(text)))
                    }
                }
            }
            child = child.next
        }

        return blocks
    }

    private fun parseList(list: CommonmarkListBlock, depth: Int): MarkdownListBlock {
        val ordered = list is OrderedList
        val startNumber = (list as? OrderedList)?.startNumber ?: 1
        val delimiter = (list as? OrderedList)?.markerDelimiter ?: '.'
        val bulletMarker = (list as? org.commonmark.node.BulletList)?.bulletMarker ?: '-'
        val items = mutableListOf<MarkdownListItem>()
        var child = list.firstChild
        var index = 0

        while (child != null) {
            if (child is ListItem) {
                val marker = if (ordered) {
                    "${startNumber + index}$delimiter"
                } else {
                    bulletMarker.toString()
                }
                items += MarkdownListItem(
                    depth = depth,
                    marker = marker,
                    blocks = parseChildren(child, depth + 1)
                )
                index += 1
            }
            child = child.next
        }

        return MarkdownListBlock(ordered = ordered, items = items)
    }

    private fun parseTable(block: TableBlock): MarkdownTableBlock {
        val headers = mutableListOf<List<MarkdownInline>>()
        val rows = mutableListOf<List<List<MarkdownInline>>>()
        var child = block.firstChild

        while (child != null) {
            when (child) {
                is TableHead -> {
                    var row = child.firstChild
                    while (row != null) {
                        if (row is TableRow) {
                            headers += parseTableRow(row)
                        }
                        row = row.next
                    }
                }

                is TableBody -> {
                    var row = child.firstChild
                    while (row != null) {
                        if (row is TableRow) {
                            rows += parseTableRow(row)
                        }
                        row = row.next
                    }
                }
            }
            child = child.next
        }

        return MarkdownTableBlock(headers = headers, rows = rows)
    }

    private fun parseTableRow(row: TableRow): List<List<MarkdownInline>> {
        val cells = mutableListOf<List<MarkdownInline>>()
        var child = row.firstChild
        while (child != null) {
            if (child is TableCell) {
                cells += parseInlines(child)
            }
            child = child.next
        }
        return cells
    }

    private fun parseInlines(parent: Node): List<MarkdownInline> {
        val inlines = mutableListOf<MarkdownInline>()
        var child = parent.firstChild

        while (child != null) {
            when (child) {
                is Text -> inlines += MarkdownTextInline(child.literal.orEmpty())
                is StrongEmphasis -> inlines += MarkdownStrongInline(parseInlines(child))
                is Emphasis -> inlines += MarkdownEmphasisInline(parseInlines(child))
                is Code -> inlines += MarkdownCodeInline(child.literal.orEmpty())
                is Link -> inlines += MarkdownLinkInline(
                    destination = child.destination.orEmpty(),
                    children = parseInlines(child)
                )

                is SoftLineBreak, is HardLineBreak -> inlines += MarkdownLineBreakInline
                is HtmlInline -> inlines += MarkdownTextInline(child.literal.orEmpty())
                is Image -> inlines += parseInlines(child)
                else -> if (child.firstChild != null) {
                    inlines += parseInlines(child)
                }
            }
            child = child.next
        }

        return mergeAdjacentText(inlines)
    }

    private fun mergeAdjacentText(inlines: List<MarkdownInline>): List<MarkdownInline> {
        if (inlines.isEmpty()) return inlines

        val merged = mutableListOf<MarkdownInline>()
        inlines.forEach { inline ->
            val last = merged.lastOrNull()
            if (last is MarkdownTextInline && inline is MarkdownTextInline) {
                merged[merged.lastIndex] = MarkdownTextInline(last.text + inline.text)
            } else {
                merged += inline
            }
        }
        return merged
    }
}
