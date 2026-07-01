@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cslearningos.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PanelShape = RoundedCornerShape(14.dp)
private val CardShape = RoundedCornerShape(10.dp)

@Composable
fun SectionHeader(eyebrow: String, title: String, body: String) {
    WorkbenchCard(accent = true) {
        Eyebrow(eyebrow)
        Text(title, color = WorkbenchColors.InkStrong, fontSize = 25.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp)
        Text(body, color = WorkbenchColors.Muted, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
fun DetailHeading(eyebrow: String, title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, WorkbenchColors.Line), PanelShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(WorkbenchColors.Accent.copy(alpha = 0.18f), Color.Transparent),
                    radius = 620f
                ),
                PanelShape
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Eyebrow(eyebrow)
        Text(title, color = WorkbenchColors.InkStrong, fontSize = 31.sp, fontWeight = FontWeight.Black, lineHeight = 35.sp)
        Text(body, color = WorkbenchColors.Muted, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
fun WorkbenchCard(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (accent) 14.dp else 8.dp,
                shape = CardShape,
                ambientColor = WorkbenchColors.Surface.copy(alpha = 0.36f),
                spotColor = WorkbenchColors.Surface.copy(alpha = 0.42f)
            )
            .drawBehind {
                if (accent) {
                    drawLine(
                        color = WorkbenchColors.Accent,
                        start = Offset(0f, 10.dp.toPx()),
                        end = Offset(0f, size.height - 10.dp.toPx()),
                        strokeWidth = 3.dp.toPx()
                    )
                }
            },
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = WorkbenchColors.SurfaceCard),
        border = BorderStroke(1.dp, if (accent) WorkbenchColors.Accent else WorkbenchColors.Line)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (accent) {
                        Brush.linearGradient(
                            listOf(WorkbenchColors.Accent.copy(alpha = 0.14f), Color.Transparent)
                        )
                    } else {
                        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            content = content
        )
    }
}

@Composable
fun InteractiveCard(
    onClick: () -> Unit,
    accent: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    WorkbenchCard(
        accent = accent || pressed,
        modifier = modifier
            .heightIn(min = 72.dp)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        content = content
    )
}

@Composable
fun EmptyWorkbenchCard(title: String, body: String) {
    WorkbenchCard {
        Eyebrow("empty state")
        Text(title, color = WorkbenchColors.InkStrong, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(body, color = WorkbenchColors.Muted, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
fun WorkbenchActionTile(
    eyebrow: String,
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    metric: String? = null
) {
    InteractiveCard(onClick = onClick, accent = accent, modifier = modifier.heightIn(min = 112.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Eyebrow(eyebrow)
            if (metric != null) {
                Text(metric, color = WorkbenchColors.Accent, fontSize = 17.sp, fontWeight = FontWeight.Black)
            }
        }
        Text(title, color = WorkbenchColors.InkStrong, fontSize = 21.sp, fontWeight = FontWeight.Black, lineHeight = 25.sp)
        Text(body, color = WorkbenchColors.Muted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
fun WorkbenchButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true
) {
    val container = when {
        primary -> WorkbenchColors.Accent
        danger -> WorkbenchColors.SurfaceCard
        else -> WorkbenchColors.SurfaceCard
    }
    val content = when {
        primary -> WorkbenchColors.SurfaceSoft
        danger -> WorkbenchColors.Danger
        else -> WorkbenchColors.Accent
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (danger) WorkbenchColors.Danger.copy(alpha = 0.58f) else WorkbenchColors.LineStrong),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = WorkbenchColors.SurfaceCard.copy(alpha = 0.44f),
            disabledContentColor = WorkbenchColors.Muted
        )
    ) {
        Text(text, fontWeight = FontWeight.Black)
    }
}

@Composable
fun NavButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    WorkbenchButton(
        text = text,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        primary = selected
    )
}

@Composable
fun WorkbenchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    minLines: Int = 1
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(8.dp)
    val minHeight = ((minLines.coerceAtLeast(1) * 24) + 32).dp

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        minLines = minLines,
        cursorBrush = SolidColor(WorkbenchColors.Accent),
        textStyle = TextStyle(
            color = WorkbenchColors.Ink,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium
        ),
        modifier = Modifier.fillMaxWidth(),
        interactionSource = interaction,
        decorationBox = { innerTextField ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight.coerceAtLeast(56.dp))
                    .clip(shape)
                    .background(WorkbenchColors.Surface)
                    .border(
                        BorderStroke(
                            1.dp,
                            if (focused || pressed) WorkbenchColors.Accent else WorkbenchColors.LineStrong
                        ),
                        shape
                    )
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(label.uppercase(), color = WorkbenchColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.9.sp)
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isBlank()) {
                        Text(label, color = WorkbenchColors.Muted.copy(alpha = 0.58f), fontSize = 15.sp, lineHeight = 22.sp)
                    }
                    innerTextField()
                }
            }
        }
    )
}

@Composable
fun StatusBanner(message: String) {
    if (message.isBlank()) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(WorkbenchColors.Accent.copy(alpha = 0.11f))
            .border(BorderStroke(1.dp, WorkbenchColors.Accent.copy(alpha = 0.32f)), CardShape)
            .padding(12.dp)
    ) {
        Text(message, color = WorkbenchColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun Eyebrow(text: String) {
    Text(
        text = text.uppercase(),
        color = WorkbenchColors.Muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.2.sp
    )
}

@Composable
fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(WorkbenchColors.Accent.copy(alpha = 0.12f), WorkbenchColors.SurfaceCard)
                )
            )
            .border(BorderStroke(1.dp, WorkbenchColors.Accent.copy(alpha = 0.28f)), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label.uppercase(), color = WorkbenchColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text(value, color = WorkbenchColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun MetaPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(WorkbenchColors.Surface.copy(alpha = 0.56f))
            .border(BorderStroke(1.dp, WorkbenchColors.Line), RoundedCornerShape(10.dp))
            .padding(9.dp)
    ) {
        Text(label.uppercase(), color = WorkbenchColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text(value, color = WorkbenchColors.InkStrong, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

@Composable
fun ToolbarRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
        content = content
    )
}

fun Modifier.workbenchGrid(): Modifier = drawBehind {
    drawRect(WorkbenchColors.Surface)
    val step = 44.dp.toPx()
    val lineColor = WorkbenchColors.Accent.copy(alpha = 0.035f)
    var x = 0f
    while (x <= size.width) {
        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}
