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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CardShape = RoundedCornerShape(10.dp)
private val BlockShape = RoundedCornerShape(10.dp)
private val PillShape = RoundedCornerShape(999.dp)
private val ListPattern = Regex("""^(\s*)([-*+]|\d+\.)\s+(.*)$""")

@Composable
fun MarkdownRenderer(markdown: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
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
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        parseMarkdownBlocks(markdown).forEach { block ->
            when (block) {
                is MarkdownBlock.Code -> CodeBlock(block)
                is MarkdownBlock.Heading -> HeadingBlock(block)
                is MarkdownBlock.ListItem -> ListItemBlock(block)
                is MarkdownBlock.Paragraph -> ParagraphBlock(block.text)
                is MarkdownBlock.Quiz -> QuizBlock(block)
                is MarkdownBlock.Quote -> QuoteBlock(block.lines)
                MarkdownBlock.Space -> Spacer(modifier = Modifier.size(2.dp))
            }
        }
    }
}

@Composable
private fun HeadingBlock(block: MarkdownBlock.Heading) {
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
            .padding(top = if (block.level == 1) 8.dp else 6.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = block.text, style = style)
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
private fun ParagraphBlock(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = WorkbenchColors.Ink,
            lineHeight = 20.sp
        ),
        modifier = Modifier.padding(vertical = 1.dp)
    )
}

@Composable
private fun ListItemBlock(block: MarkdownBlock.ListItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (block.depth * 14).dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(if (block.marker.endsWith(".")) 0.dp else 5.dp)
                .background(WorkbenchColors.AccentStrong, CircleShape)
        )
        if (block.marker.endsWith(".")) {
            Text(
                text = block.marker,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = WorkbenchColors.AccentStrong,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.width(28.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = block.text,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = WorkbenchColors.Ink,
                lineHeight = 20.sp
            )
        )
    }
}

@Composable
private fun QuoteBlock(lines: List<String>) {
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
        Text(
            text = lines.joinToString("\n"),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = WorkbenchColors.Ink,
                lineHeight = 20.sp
            )
        )
    }
}

@Composable
private fun CodeBlock(block: MarkdownBlock.Code) {
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
            text = block.lines.joinToString("\n"),
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
private fun QuizBlock(block: MarkdownBlock.Quiz) {
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

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.lines()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trimStart()

        when {
            line.isBlank() -> {
                blocks += MarkdownBlock.Space
                index += 1
            }
            trimmed.startsWith("```") -> {
                val (block, nextIndex) = readCodeBlock(lines, index)
                blocks += block
                index = nextIndex
            }
            trimmed.startsWith(":::") && trimmed.removePrefix(":::").trimStart().startsWith("quiz") -> {
                val (block, nextIndex) = readQuizBlock(lines, index)
                blocks += block
                index = nextIndex
            }
            trimmed.startsWith(">") -> {
                val (block, nextIndex) = readQuoteBlock(lines, index)
                blocks += block
                index = nextIndex
            }
            trimmed.startsWith("### ") -> {
                blocks += MarkdownBlock.Heading(level = 3, text = trimmed.removePrefix("### ").trim())
                index += 1
            }
            trimmed.startsWith("## ") -> {
                blocks += MarkdownBlock.Heading(level = 2, text = trimmed.removePrefix("## ").trim())
                index += 1
            }
            trimmed.startsWith("# ") -> {
                blocks += MarkdownBlock.Heading(level = 1, text = trimmed.removePrefix("# ").trim())
                index += 1
            }
            ListPattern.matches(line) -> {
                val match = ListPattern.matchEntire(line)
                val indent = match?.groupValues?.get(1).orEmpty().length
                blocks += MarkdownBlock.ListItem(
                    depth = indent / 2,
                    marker = match?.groupValues?.get(2).orEmpty(),
                    text = match?.groupValues?.get(3).orEmpty()
                )
                index += 1
            }
            else -> {
                blocks += MarkdownBlock.Paragraph(line.trim())
                index += 1
            }
        }
    }

    return blocks
}

private fun readCodeBlock(lines: List<String>, startIndex: Int): Pair<MarkdownBlock.Code, Int> {
    val opener = lines[startIndex].trimStart()
    val content = mutableListOf<String>()
    var index = startIndex + 1

    while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
        content += lines[index]
        index += 1
    }

    val nextIndex = if (index < lines.size) index + 1 else index
    return MarkdownBlock.Code(
        info = opener.removePrefix("```").trim(),
        lines = content
    ) to nextIndex
}

private fun readQuizBlock(lines: List<String>, startIndex: Int): Pair<MarkdownBlock.Quiz, Int> {
    val opener = lines[startIndex].trimStart()
    val content = mutableListOf<String>()
    var index = startIndex + 1

    while (index < lines.size && lines[index].trim() != ":::") {
        content += lines[index].trimEnd()
        index += 1
    }

    val nextIndex = if (index < lines.size) index + 1 else index
    return MarkdownBlock.Quiz(
        info = opener.removePrefix(":::").trim().removePrefix("quiz").trim(),
        lines = content
    ) to nextIndex
}

private fun readQuoteBlock(lines: List<String>, startIndex: Int): Pair<MarkdownBlock.Quote, Int> {
    val content = mutableListOf<String>()
    var index = startIndex

    while (index < lines.size && lines[index].trimStart().startsWith(">")) {
        content += lines[index]
            .trimStart()
            .removePrefix(">")
            .trimStart()
        index += 1
    }

    return MarkdownBlock.Quote(lines = content) to index
}

private sealed interface MarkdownBlock {
    data class Code(val info: String, val lines: List<String>) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class ListItem(val depth: Int, val marker: String, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Quiz(val info: String, val lines: List<String>) : MarkdownBlock
    data class Quote(val lines: List<String>) : MarkdownBlock
    object Space : MarkdownBlock
}
