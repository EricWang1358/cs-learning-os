package com.cslearningos.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cslearningos.mobile.ui.markdown.MarkdownBlock
import com.cslearningos.mobile.ui.markdown.MarkdownCodeBlock
import com.cslearningos.mobile.ui.markdown.MarkdownHeadingBlock
import com.cslearningos.mobile.ui.markdown.MarkdownHorizontalRuleBlock
import com.cslearningos.mobile.ui.markdown.MarkdownInline
import com.cslearningos.mobile.ui.markdown.MarkdownListBlock
import com.cslearningos.mobile.ui.markdown.MarkdownListItem
import com.cslearningos.mobile.ui.markdown.MarkdownParagraphBlock
import com.cslearningos.mobile.ui.markdown.MarkdownQuizBlock
import com.cslearningos.mobile.ui.markdown.MarkdownQuoteBlock
import com.cslearningos.mobile.ui.markdown.MarkdownTableBlock
import com.cslearningos.mobile.ui.markdown.MarkdownLinkAnnotationTag
import com.cslearningos.mobile.ui.markdown.QuizAwareMarkdownDocument
import com.cslearningos.mobile.ui.markdown.buildMarkdownAnnotatedText

private val CardShape = RoundedCornerShape(10.dp)
private val BlockShape = RoundedCornerShape(10.dp)
private val PillShape = RoundedCornerShape(999.dp)

@Composable
fun MarkdownRenderer(markdown: String, modifier: Modifier = Modifier, card: Boolean = true) {
    val blocks = remember(markdown) { QuizAwareMarkdownDocument.parse(markdown) }

    Column(
        modifier = if (card) {
            modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 8.dp,
                    shape = CardShape,
                    ambientColor = WorkbenchColors.Surface.copy(alpha = 0.32f),
                    spotColor = WorkbenchColors.Surface.copy(alpha = 0.38f)
                )
                .clip(CardShape)
                .background(WorkbenchColors.SurfaceCard)
                .border(1.dp, WorkbenchColors.Line, CardShape)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        } else {
            modifier.fillMaxWidth()
        },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            MarkdownBlockView(block = block)
        }
    }
}

@Composable
private fun MarkdownBlockView(block: MarkdownBlock) {
    when (block) {
        is MarkdownCodeBlock -> CodeBlock(block)
        is MarkdownHeadingBlock -> HeadingBlock(block)
        MarkdownHorizontalRuleBlock -> HorizontalRuleBlock()
        is MarkdownListBlock -> ListBlock(block)
        is MarkdownParagraphBlock -> ParagraphBlock(block.inlines)
        is MarkdownQuizBlock -> QuizBlock(block)
        is MarkdownQuoteBlock -> QuoteBlock(block)
        is MarkdownTableBlock -> TableBlock(block)
    }
}

@Composable
private fun HeadingBlock(block: MarkdownHeadingBlock) {
    val style = when (block.level) {
        1 -> MaterialTheme.typography.headlineSmall.copy(
            color = WorkbenchColors.InkStrong,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 30.sp
        )

        2 -> MaterialTheme.typography.titleLarge.copy(
            color = WorkbenchColors.InkStrong,
            fontWeight = FontWeight.Bold,
            lineHeight = 26.sp
        )

        else -> MaterialTheme.typography.titleMedium.copy(
            color = WorkbenchColors.Ink,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 22.sp
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (block.level == 1) 8.dp else 2.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RichText(inlines = block.inlines, style = style)
        if (block.level <= 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (block.level == 1) 0.38f else 0.24f)
                    .background(WorkbenchColors.Accent, RoundedCornerShape(999.dp))
                    .padding(vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun ParagraphBlock(inlines: List<MarkdownInline>, modifier: Modifier = Modifier) {
    RichText(
        inlines = inlines,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = WorkbenchColors.Ink,
            lineHeight = 20.sp
        ),
        modifier = modifier
    )
}

@Composable
private fun ListBlock(block: MarkdownListBlock) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        block.items.forEach { item ->
            ListItemBlock(item)
        }
    }
}

@Composable
private fun ListItemBlock(item: MarkdownListItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (item.depth * 14).dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (item.marker.endsWith(".")) {
            Text(
                text = item.marker,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = WorkbenchColors.AccentStrong,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.width(28.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(5.dp)
                    .background(WorkbenchColors.AccentStrong, CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item.blocks.forEach { block ->
                MarkdownBlockView(block)
            }
        }
    }
}

@Composable
private fun QuoteBlock(block: MarkdownQuoteBlock) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BlockShape)
            .background(WorkbenchColors.SurfaceElevated)
            .border(1.dp, WorkbenchColors.LineStrong, BlockShape)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .background(WorkbenchColors.Success, RoundedCornerShape(999.dp))
                .padding(vertical = 30.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            block.blocks.forEach { child ->
                MarkdownBlockView(child)
            }
        }
    }
}

@Composable
private fun CodeBlock(block: MarkdownCodeBlock) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BlockShape)
            .background(WorkbenchColors.Surface)
            .border(1.dp, WorkbenchColors.LineStrong, BlockShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WorkbenchColors.SurfaceSoft)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (block.info.isBlank()) "code" else block.info.trim(),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = WorkbenchColors.Accent,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Text(
            text = block.code,
            style = MaterialTheme.typography.bodySmall.copy(
                color = WorkbenchColors.Ink,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp
            ),
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun TableBlock(block: MarkdownTableBlock) {
    val columnCount = maxOf(
        block.headers.size,
        block.rows.maxOfOrNull { it.size } ?: 0
    ).coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BlockShape)
            .background(WorkbenchColors.Surface.copy(alpha = 0.72f))
            .border(1.dp, WorkbenchColors.LineStrong, BlockShape)
    ) {
        TableRowBlock(
            cells = padTableCells(block.headers, columnCount),
            header = true
        )
        block.rows.forEach { row ->
            TableRowBlock(cells = padTableCells(row, columnCount), header = false)
        }
    }
}

@Composable
private fun TableRowBlock(
    cells: List<List<MarkdownInline>>,
    header: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (header) WorkbenchColors.SurfaceSoft else Color.Transparent)
    ) {
        cells.forEachIndexed { index, cell ->
            val borderModifier = if (index < cells.lastIndex) {
                Modifier.border(
                    width = 1.dp,
                    color = WorkbenchColors.Line,
                    shape = RoundedCornerShape(0.dp)
                )
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(borderModifier)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                RichText(
                    inlines = cell,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = WorkbenchColors.InkStrong,
                        fontWeight = if (header) FontWeight.Bold else FontWeight.Medium,
                        lineHeight = 18.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun HorizontalRuleBlock() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(WorkbenchColors.LineStrong, RoundedCornerShape(999.dp))
            .padding(vertical = 1.dp)
    )
}

@Composable
private fun QuizBlock(block: MarkdownQuizBlock) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BlockShape)
            .background(WorkbenchColors.SurfaceSoft)
            .border(1.dp, WorkbenchColors.AccentStrong, BlockShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "QUIZ",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = WorkbenchColors.Surface,
                    fontWeight = FontWeight.Black
                ),
                modifier = Modifier
                    .clip(PillShape)
                    .background(WorkbenchColors.Accent)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            if (block.info.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = block.info.trim(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = WorkbenchColors.Muted,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        }
        block.lines.forEach { line ->
            if (line.isBlank()) {
                Spacer(modifier = Modifier.size(2.dp))
            } else {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = WorkbenchColors.Ink,
                        lineHeight = 20.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun RichText(
    inlines: List<MarkdownInline>,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val annotated = remember(inlines) { buildMarkdownAnnotatedText(inlines) }
    val displayText = remember(annotated) {
        buildAnnotatedString {
            append(annotated.text)

            annotated.text.getStringAnnotations(
                tag = MarkdownLinkAnnotationTag,
                start = 0,
                end = annotated.text.length
            ).forEach { annotation ->
                addStyle(
                    style = SpanStyle(
                        color = WorkbenchColors.Accent,
                        textDecoration = TextDecoration.Underline
                    ),
                    start = annotation.start,
                    end = annotation.end
                )
            }

            annotated.codeSpans.forEach { span ->
                addStyle(
                    style = SpanStyle(
                        color = WorkbenchColors.AccentStrong,
                        background = WorkbenchColors.Surface,
                        fontFamily = FontFamily.Monospace
                    ),
                    start = span.start,
                    end = span.end
                )
            }
        }
    }

    ClickableText(
        text = displayText,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            displayText
                .getStringAnnotations(
                    tag = MarkdownLinkAnnotationTag,
                    start = offset,
                    end = offset
                )
                .firstOrNull()
                ?.let { uriHandler.openUri(it.item) }
        }
    )
}

private fun List<List<MarkdownInline>>.padTo(size: Int): List<List<MarkdownInline>> =
    this + List((size - this.size).coerceAtLeast(0)) { emptyList() }

private fun padTableCells(
    cells: List<List<MarkdownInline>>,
    columnCount: Int
): List<List<MarkdownInline>> = cells.padTo(columnCount)
