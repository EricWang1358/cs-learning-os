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
import java.util.ArrayDeque

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
    const val MaxInputChars = 1_000_000
    const val MaxNesting = 64

    private val parser = Parser.builder()
        .extensions(listOf(TablesExtension.create()))
        .build()

    fun parse(markdown: String): List<MarkdownBlock> {
        if (markdown.isBlank()) return emptyList()
        if (requiresPlainTextFallback(markdown)) return plainTextFallback(markdown)
        val document = parser.parse(markdown)
        if (hasExcessiveAstNesting(document)) return plainTextFallback(markdown)
        return try {
            parseChildren(document, depth = 0)
        } catch (_: MarkdownDepthLimitExceeded) {
            plainTextFallback(markdown)
        }
    }

    fun requiresPlainTextFallback(markdown: String): Boolean =
        markdown.length > MaxInputChars || exceedsNestingLimit(markdown)

    fun plainTextFallback(markdown: String): List<MarkdownBlock> =
        listOf(MarkdownParagraphBlock(listOf(MarkdownTextInline(markdown))))

    private fun exceedsNestingLimit(markdown: String): Boolean =
        markdown.lineSequence().any { line ->
            var index = 0
            var quoteDepth = 0
            while (index < line.length) {
                while (index < line.length && line[index].isWhitespace()) index += 1
                if (index >= line.length || line[index] != '>') break
                quoteDepth += 1
                if (quoteDepth > MaxNesting) return@any true
                index += 1
            }

            val indentation = line.takeWhile { it == ' ' || it == '\t' }
            val indentationDepth = indentation.count { it == '\t' } + indentation.count { it == ' ' } / 2
            indentationDepth > MaxNesting || line.hasExcessiveInlineDelimiterRun()
        }

    private fun String.hasExcessiveInlineDelimiterRun(): Boolean {
        var runLength = 0
        var delimiter: Char? = null

        forEach { character ->
            if (character == '*' || character == '_') {
                runLength = if (character == delimiter) runLength + 1 else 1
                delimiter = character
                if (runLength > MaxNesting * 2) return true
            } else {
                runLength = 0
                delimiter = null
            }
        }

        return false
    }

    private fun hasExcessiveAstNesting(root: Node): Boolean {
        val pending = ArrayDeque<NodeDepth>()
        pending.addLast(NodeDepth(node = root, blockDepth = 0, inlineDepth = 0))

        while (pending.isNotEmpty()) {
            val current = pending.removeLast()
            var child = current.node.firstChild
            while (child != null) {
                val blockDepth = current.blockDepth + when (current.node) {
                    is BlockQuote, is ListItem -> 1
                    else -> 0
                }
                val inlineDepth = if (child.isInlineNode()) {
                    if (current.node.isInlineNode()) current.inlineDepth + 1 else 0
                } else {
                    0
                }
                if (blockDepth > MaxNesting || inlineDepth > MaxNesting) return true
                pending.addLast(NodeDepth(child, blockDepth, inlineDepth))
                child = child.next
            }
        }

        return false
    }

    private fun Node.isInlineNode(): Boolean =
        this is Text ||
            this is StrongEmphasis ||
            this is Emphasis ||
            this is Code ||
            this is Link ||
            this is SoftLineBreak ||
            this is HardLineBreak ||
            this is HtmlInline ||
            this is Image

    private fun parseChildren(parent: Node, depth: Int): List<MarkdownBlock> {
        enforceDepthLimit(depth)
        val blocks = mutableListOf<MarkdownBlock>()
        var child = parent.firstChild

        while (child != null) {
            when (child) {
                is Heading -> blocks += MarkdownHeadingBlock(
                    level = child.level,
                    inlines = parseInlines(child, depth = 0)
                )

                is Paragraph -> blocks += MarkdownParagraphBlock(parseInlines(child, depth = 0))
                is BlockQuote -> blocks += MarkdownQuoteBlock(parseChildren(child, depth + 1))
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
        enforceDepthLimit(depth)
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
                cells += parseInlines(child, depth = 0)
            }
            child = child.next
        }
        return cells
    }

    private fun parseInlines(parent: Node, depth: Int): List<MarkdownInline> {
        enforceDepthLimit(depth)
        val inlines = mutableListOf<MarkdownInline>()
        var child = parent.firstChild

        while (child != null) {
            when (child) {
                is Text -> inlines += MarkdownTextInline(child.literal.orEmpty())
                is StrongEmphasis -> inlines += MarkdownStrongInline(parseInlines(child, depth + 1))
                is Emphasis -> inlines += MarkdownEmphasisInline(parseInlines(child, depth + 1))
                is Code -> inlines += MarkdownCodeInline(child.literal.orEmpty())
                is Link -> inlines += MarkdownLinkInline(
                    destination = child.destination.orEmpty(),
                    children = parseInlines(child, depth + 1)
                )

                is SoftLineBreak, is HardLineBreak -> inlines += MarkdownLineBreakInline
                is HtmlInline -> inlines += MarkdownTextInline(child.literal.orEmpty())
                is Image -> inlines += parseInlines(child, depth + 1)
                else -> if (child.firstChild != null) {
                    inlines += parseInlines(child, depth + 1)
                }
            }
            child = child.next
        }

        return mergeAdjacentText(inlines)
    }

    private fun enforceDepthLimit(depth: Int) {
        if (depth > MaxNesting) throw MarkdownDepthLimitExceeded
    }

    private data class NodeDepth(
        val node: Node,
        val blockDepth: Int,
        val inlineDepth: Int
    )

    private data object MarkdownDepthLimitExceeded : RuntimeException()

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
