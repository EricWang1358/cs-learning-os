@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cslearningos.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PanelShape = RoundedCornerShape(16.dp)
private val CardShape = RoundedCornerShape(16.dp)

private object WorkbenchActionTokens {
    val Gap = 8.dp
    val MinHeight = 44.dp
    val HorizontalPadding = 12.dp
    val CornerRadius = 12.dp
    val FontSize = 13.sp
    const val MenuLabelMaxLines = 2
}

data class WorkbenchMenuOption(
    val text: String,
    val onClick: () -> Unit
)

fun eyebrowLetterSpacingValue(text: String): Float {
    val trimmed = text.trim()
    val hanCount = trimmed.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
    val hasHan = hanCount > 0

    return when {
        hasHan && trimmed.length <= 4 -> 0.1f
        hasHan && trimmed.length <= 6 -> 0.18f
        hasHan -> 0.28f
        trimmed.length <= 8 -> 0.8f
        else -> 1.0f
    }
}

@Composable
fun SectionHeader(eyebrow: String, title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Eyebrow(eyebrow)
        Text(title, color = WorkbenchColors.InkStrong, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
        Text(body, color = WorkbenchColors.Muted.copy(alpha = 0.92f), fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
fun DetailHeading(eyebrow: String, title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, WorkbenchColors.Line), PanelShape)
            .background(WorkbenchColors.SurfaceCard.copy(alpha = 0.76f), PanelShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Eyebrow(eyebrow)
        Text(title, color = WorkbenchColors.InkStrong, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 25.sp)
        Text(body, color = WorkbenchColors.Muted.copy(alpha = 0.92f), fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
fun WorkbenchCard(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    motionPolicy: ScreenMotionPolicy = screenMotionPolicy(AppScreen.Capture),
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (accent) WorkbenchColors.Accent else WorkbenchColors.Line,
        animationSpec = tween(motionPolicy.stateMillis, easing = FastOutSlowInEasing),
        label = "workbench-card-border"
    )
    val containerColor by animateColorAsState(
        targetValue = if (accent) {
            WorkbenchColors.SurfaceCard.copy(alpha = 0.99f)
        } else {
            WorkbenchColors.SurfaceCard
        },
        animationSpec = tween(motionPolicy.stateMillis, easing = FastOutSlowInEasing),
        label = "workbench-card-container"
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (accent) 6.dp else 2.dp,
                shape = CardShape,
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.35f)
            ),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (accent) BorderStroke(1.dp, borderColor) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
    motionPolicy: ScreenMotionPolicy = screenMotionPolicy(AppScreen.Capture),
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.7f else 1f,
        animationSpec = tween(motionPolicy.pressMillis, easing = FastOutSlowInEasing),
        label = "interactive-card-press-alpha"
    )
    WorkbenchCard(
        accent = accent || pressed,
        motionPolicy = motionPolicy,
        modifier = modifier
            .heightIn(min = 72.dp)
            .alpha(pressAlpha)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        content = content
    )
}

@Composable
fun EmptyWorkbenchCard(title: String, body: String, modifier: Modifier = Modifier) {
    WorkbenchCard(modifier = modifier) {
        Eyebrow("empty state")
        Text(title, color = WorkbenchColors.InkStrong, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
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
    InteractiveCard(onClick = onClick, accent = accent, modifier = modifier.widthIn(min = 136.dp).heightIn(min = 106.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Eyebrow(eyebrow)
            if (metric != null) {
                Text(metric, color = WorkbenchColors.Accent, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Text(title, color = WorkbenchColors.InkStrong, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 25.sp)
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
        else -> WorkbenchColors.AccentStrong
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = WorkbenchActionTokens.MinHeight),
        shape = RoundedCornerShape(WorkbenchActionTokens.CornerRadius),
        border = BorderStroke(1.dp, if (danger) WorkbenchColors.Danger.copy(alpha = 0.58f) else WorkbenchColors.LineStrong),
        contentPadding = PaddingValues(horizontal = WorkbenchActionTokens.HorizontalPadding),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = WorkbenchColors.SurfaceCard.copy(alpha = 0.44f),
            disabledContentColor = WorkbenchColors.Muted
        )
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = WorkbenchActionTokens.FontSize
        )
    }
}

@Composable
fun WorkbenchMenuButton(
    text: String,
    options: List<WorkbenchMenuOption>,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    expandToContainer: Boolean = false
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        WorkbenchButton(
            text = text,
            onClick = { expanded = true },
            modifier = if (expandToContainer) Modifier.fillMaxWidth() else Modifier,
            primary = primary,
            enabled = options.isNotEmpty()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.text, maxLines = WorkbenchActionTokens.MenuLabelMaxLines) },
                    onClick = {
                        option.onClick()
                        expanded = false
                    }
                )
            }
        }
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
    minLines: Int = 1,
    onSubmit: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(12.dp)
    val minHeight = ((minLines.coerceAtLeast(1) * 24) + 32).dp

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        minLines = minLines,
        cursorBrush = SolidColor(WorkbenchColors.Accent),
        keyboardOptions = KeyboardOptions(
            imeAction = if (onSubmit == null) ImeAction.Default else ImeAction.Send
        ),
        keyboardActions = KeyboardActions(
            onSend = { onSubmit?.invoke() }
        ),
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
                Text(label.uppercase(), color = WorkbenchColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
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
fun StatusBanner(message: UiText?) {
    val resolved = message.resolve()
    AnimatedVisibility(
        visible = resolved != null,
        enter = fadeIn(tween(WorkbenchMotion.StateMillis)) + expandVertically(tween(WorkbenchMotion.DisclosureMillis)),
        exit = fadeOut(tween(WorkbenchMotion.StateMillis)) + shrinkVertically(tween(WorkbenchMotion.DisclosureMillis))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(WorkbenchColors.Accent.copy(alpha = 0.11f))
                .border(BorderStroke(1.dp, WorkbenchColors.Accent.copy(alpha = 0.32f)), CardShape)
                .padding(12.dp)
        ) {
            Text(resolved.orEmpty(), color = WorkbenchColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Eyebrow(text: String) {
    Text(
        text = text.uppercase(),
        color = WorkbenchColors.Muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = eyebrowLetterSpacingValue(text).sp
    )
}

@Composable
fun InlineMetricBadge(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(WorkbenchColors.Surface.copy(alpha = 0.72f))
            .border(BorderStroke(1.dp, WorkbenchColors.LineStrong.copy(alpha = 0.85f)), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = WorkbenchColors.Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = WorkbenchColors.InkStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(WorkbenchColors.Accent.copy(alpha = 0.12f), WorkbenchColors.SurfaceCard)
                )
            )
            .border(BorderStroke(1.dp, WorkbenchColors.Accent.copy(alpha = 0.28f)), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label.uppercase(), color = WorkbenchColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = WorkbenchColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun MetaPill(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(WorkbenchColors.Surface.copy(alpha = 0.56f))
            .border(BorderStroke(1.dp, WorkbenchColors.Line), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label.uppercase(), color = WorkbenchColors.Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(value, color = WorkbenchColors.InkStrong, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

internal fun toolbarRows(itemWidths: List<Int>, availableWidth: Int, gap: Int): List<List<Int>> {
    if (itemWidths.isEmpty()) return emptyList()
    if (itemWidths.sum() + gap * (itemWidths.size - 1) <= availableWidth) {
        return listOf(itemWidths.indices.toList())
    }

    val columnWidth = (availableWidth - gap).coerceAtLeast(0) / TwoColumns
    return buildList {
        var index = 0
        while (index < itemWidths.size) {
            val currentCanShare = itemWidths[index] <= columnWidth
            val nextCanShare = itemWidths.getOrNull(index + 1)?.let { it <= columnWidth } == true
            if (currentCanShare && nextCanShare) {
                add(listOf(index, index + 1))
                index += TwoColumns
            } else {
                add(listOf(index))
                index += OneColumn
            }
        }
    }
}

@Composable
fun ToolbarRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val availableWidth = constraints.maxWidth.takeUnless { it == Constraints.Infinity }
            ?: measurables.sumOf { it.maxIntrinsicWidth(constraints.maxHeight) }
        val gap = WorkbenchActionTokens.Gap.toPx().toInt()
        val readableWidths = measurables.map { measurable ->
            measurable.maxIntrinsicWidth(constraints.maxHeight).coerceAtMost(availableWidth)
        }
        val rows = toolbarRows(readableWidths, availableWidth, gap)
        val placeables = arrayOfNulls<androidx.compose.ui.layout.Placeable>(measurables.size)

        rows.forEach { row ->
            val targetWidth = when (row.size) {
                OneColumn -> availableWidth
                else -> (availableWidth - gap * (row.size - OneColumn)).coerceAtLeast(0) / row.size
            }
            row.forEach { index ->
                val measureConstraints = if (rows.size == OneColumn) {
                    constraints.copy(minWidth = 0, maxWidth = availableWidth)
                } else {
                    constraints.copy(minWidth = targetWidth, maxWidth = targetWidth)
                }
                placeables[index] = measurables[index].measure(measureConstraints)
            }
        }

        val rowHeights = rows.map { row -> row.maxOf { placeables[it]!!.height } }
        val height = (rowHeights.sum() + gap * (rows.size - OneColumn).coerceAtLeast(0))
            .coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(availableWidth, height) {
            var y = 0
            rows.zip(rowHeights).forEach { (row, rowHeight) ->
                var x = 0
                row.forEach { index ->
                    val placeable = placeables[index]!!
                    placeable.placeRelative(x, y + (rowHeight - placeable.height) / TwoColumns)
                    x += placeable.width + gap
                }
                y += rowHeight + gap
            }
        }
    }
}

private const val OneColumn = 1
private const val TwoColumns = 2

@Composable
fun CollapsibleWorkbenchSection(
    eyebrow: String,
    title: String,
    body: String,
    expandLabel: String,
    collapseLabel: String,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val motionPolicy = screenMotionPolicy(if (initiallyExpanded) AppScreen.Home else AppScreen.Capture)
    WorkbenchCard(accent = expanded, motionPolicy = motionPolicy) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Eyebrow(eyebrow)
                Text(
                    text = title,
                    color = WorkbenchColors.InkStrong,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 21.sp
                )
            }
            WorkbenchButton(
                text = if (expanded) collapseLabel else expandLabel,
                onClick = { expanded = !expanded }
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(WorkbenchMotion.StateMillis)) + expandVertically(tween(WorkbenchMotion.DisclosureMillis)),
            exit = fadeOut(tween(WorkbenchMotion.StateMillis)) + shrinkVertically(tween(WorkbenchMotion.DisclosureMillis))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(body, color = WorkbenchColors.Muted, fontSize = 12.sp, lineHeight = 18.sp)
                content()
            }
        }
    }
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
