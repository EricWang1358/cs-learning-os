@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cslearningos.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cslearningos.mobile.R
import com.cslearningos.mobile.feature.assistant.domain.AssistantAgentInteraction
import com.cslearningos.mobile.feature.assistant.domain.SelectContextItem

@Composable
fun AssistantAgentInteractionCard(
    interaction: AssistantAgentInteraction,
    onReply: (String) -> Unit,
    onConfirmAreaMove: (AssistantAgentInteraction.MoveNodeArea) -> Unit
) {
    when (interaction) {
        is AssistantAgentInteraction.Confirm -> AssistantConfirmActionCard(
            interaction = interaction,
            onReply = onReply
        )

        is AssistantAgentInteraction.SelectContext -> AssistantSelectContextActionCard(
            interaction = interaction,
            onReply = onReply
        )

        is AssistantAgentInteraction.MoveNodeArea -> AssistantMoveNodeAreaCard(
            interaction = interaction,
            onConfirm = onConfirmAreaMove
        )
    }
}

@Composable
private fun AssistantMoveNodeAreaCard(
    interaction: AssistantAgentInteraction.MoveNodeArea,
    onConfirm: (AssistantAgentInteraction.MoveNodeArea) -> Unit
) {
    AgentActionSurface {
        Text("Move note to Area", color = WorkbenchColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(interaction.targetAreaId, color = WorkbenchColors.InkStrong, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(interaction.reason, color = WorkbenchColors.Muted, fontSize = 12.sp, lineHeight = 17.sp)
        AgentChoiceButton(icon = "\u2713", label = stringResource(R.string.assistant_agent_accept), modifier = Modifier.fillMaxWidth()) {
            onConfirm(interaction)
        }
    }
}

@Composable
private fun AssistantConfirmActionCard(
    interaction: AssistantAgentInteraction.Confirm,
    onReply: (String) -> Unit
) {
    var customOpen by remember(interaction) { mutableStateOf(false) }
    var customText by remember(interaction) { mutableStateOf("") }
    val acceptLabel = stringResource(R.string.assistant_agent_accept)
    val rejectLabel = stringResource(R.string.assistant_agent_reject)
    val customLabel = stringResource(R.string.assistant_agent_custom)
    AgentActionSurface {
        Text(
            text = interaction.title,
            color = WorkbenchColors.Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        if (interaction.body.isNotBlank()) {
            Text(
                text = interaction.body,
                color = WorkbenchColors.Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            AgentChoiceButton(icon = "\u2713", label = acceptLabel, modifier = Modifier.weight(1f)) {
                onReply(interaction.acceptReply)
            }
            AgentChoiceButton(icon = "\u00D7", label = rejectLabel, modifier = Modifier.weight(1f), danger = true) {
                onReply(interaction.rejectReply)
            }
            AgentChoiceButton(icon = "\u270E", label = customLabel, modifier = Modifier.weight(1f), accent = true) {
                customOpen = !customOpen
            }
        }
        AnimatedVisibility(visible = customOpen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AgentInlineInput(
                    value = customText,
                    onValueChange = { customText = it },
                    placeholder = interaction.customPlaceholder,
                    modifier = Modifier.weight(1f),
                    onSubmit = {
                        if (customText.isNotBlank()) onReply(customText.trim())
                    }
                )
                AgentArrowButton(
                    enabled = customText.isNotBlank(),
                    onClick = { if (customText.isNotBlank()) onReply(customText.trim()) }
                )
            }
        }
    }
}

@Composable
private fun AssistantSelectContextActionCard(
    interaction: AssistantAgentInteraction.SelectContext,
    onReply: (String) -> Unit
) {
    var customOpen by remember(interaction) { mutableStateOf(false) }
    var customText by remember(interaction) { mutableStateOf("") }
    var selectedIds by remember(interaction) {
        mutableStateOf(interaction.items.filter { it.selected }.map { it.id }.toSet())
    }
    val acceptLabel = stringResource(R.string.assistant_agent_accept)
    val rejectLabel = stringResource(R.string.assistant_agent_reject)
    val customLabel = stringResource(R.string.assistant_agent_custom)
    val rejectSelectionReply = stringResource(R.string.assistant_agent_reject_selection_reply)
    val customPlaceholder = stringResource(R.string.assistant_agent_custom_placeholder)
    AgentActionSurface {
        Text(
            text = interaction.title,
            color = WorkbenchColors.Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        if (interaction.body.isNotBlank()) {
            Text(
                text = interaction.body,
                color = WorkbenchColors.Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
        interaction.items.forEach { item ->
            SelectContextRow(
                item = item,
                selected = item.id in selectedIds,
                onToggle = {
                    selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            AgentChoiceButton(icon = "\u2713", label = acceptLabel, modifier = Modifier.weight(1f)) {
                val selectedTitles = interaction.items.filter { it.id in selectedIds }.joinToString { it.title }
                onReply("${interaction.confirmReplyPrefix} $selectedTitles".trim())
            }
            AgentChoiceButton(icon = "\u00D7", label = rejectLabel, modifier = Modifier.weight(1f), danger = true) {
                onReply(rejectSelectionReply)
            }
            AgentChoiceButton(icon = "\u270E", label = customLabel, modifier = Modifier.weight(1f), accent = true) {
                customOpen = !customOpen
            }
        }
        AnimatedVisibility(visible = customOpen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AgentInlineInput(
                    value = customText,
                    onValueChange = { customText = it },
                    placeholder = customPlaceholder,
                    modifier = Modifier.weight(1f),
                    onSubmit = {
                        if (customText.isNotBlank()) onReply(customText.trim())
                    }
                )
                AgentArrowButton(
                    enabled = customText.isNotBlank(),
                    onClick = { if (customText.isNotBlank()) onReply(customText.trim()) }
                )
            }
        }
    }
}
@Composable
private fun AgentActionSurface(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AgentActionCornerRadius))
            .background(WorkbenchColors.Surface.copy(alpha = 0.72f))
            .border(BorderStroke(1.dp, WorkbenchColors.Line), RoundedCornerShape(AgentActionCornerRadius))
            .padding(AgentActionPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

private val AgentActionCornerRadius = 16.dp
private val AgentActionPadding = 10.dp

@Composable
private fun AgentChoiceButton(
    icon: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val color = when {
        danger -> WorkbenchColors.Danger
        accent -> WorkbenchColors.AccentStrong
        else -> WorkbenchColors.InkStrong
    }
    Row(
        modifier = modifier
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(WorkbenchColors.SurfaceCard.copy(alpha = 0.78f))
            .border(BorderStroke(1.dp, WorkbenchColors.Line.copy(alpha = 0.86f)), RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = icon, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SelectContextRow(
    item: SelectContextItem,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WorkbenchColors.SurfaceCard.copy(alpha = if (selected) 0.92f else 0.58f))
            .border(
                BorderStroke(1.dp, if (selected) WorkbenchColors.Accent.copy(alpha = 0.45f) else WorkbenchColors.Line),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onToggle)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(item.title, color = WorkbenchColors.InkStrong, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (item.body.isNotBlank()) {
                Text(item.body, color = WorkbenchColors.Muted, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Text(
            text = if (selected) "\u2713" else "\u25CB",
            color = if (selected) WorkbenchColors.AccentStrong else WorkbenchColors.Muted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 24.dp),
        )
    }
}

@Composable
private fun AgentInlineInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSubmit: () -> Unit
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        cursorBrush = SolidColor(WorkbenchColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSubmit() }),
        textStyle = TextStyle(
            color = WorkbenchColors.Ink,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium
        ),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .heightIn(min = 46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(WorkbenchColors.SurfaceCard)
                    .border(BorderStroke(1.dp, WorkbenchColors.LineStrong), RoundedCornerShape(13.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isBlank()) {
                    Text(placeholder, color = WorkbenchColors.Muted.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1)
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun AgentArrowButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(46.dp)
            .widthIn(min = 46.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (enabled) WorkbenchColors.Accent else WorkbenchColors.Line)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("\u2191", color = WorkbenchColors.Surface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
