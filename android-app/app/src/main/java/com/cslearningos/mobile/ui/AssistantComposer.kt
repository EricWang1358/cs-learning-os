package com.cslearningos.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cslearningos.mobile.R

@Composable
internal fun AssistantComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(WorkbenchColors.Surface)
            .padding(AssistantUiTokens.ComposerPadding),
        horizontalArrangement = Arrangement.spacedBy(AssistantUiTokens.ComposerItemSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistantInputField(
            value = value,
            onValueChange = onValueChange,
            onSubmit = onSend,
            modifier = Modifier.weight(1f)
        )
        if (enabled) {
            ComposerSendButton(
                canSend = value.isNotBlank(),
                onClick = onSend
            )
        } else {
            ComposerStopButton(onClick = onStop)
        }
    }
}

@Composable
private fun ComposerSendButton(canSend: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.assistant_send_description)
    IconButton(
        onClick = onClick,
        enabled = canSend,
        modifier = Modifier
            .size(AssistantUiTokens.SendButtonSize)
            .clip(CircleShape)
            .background(
                if (canSend) WorkbenchColors.Accent else WorkbenchColors.SurfaceCard
            )
            .semantics { contentDescription = description }
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            tint = if (canSend) WorkbenchColors.OnAccent else WorkbenchColors.Muted,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun ComposerStopButton(onClick: () -> Unit) {
    val description = stringResource(R.string.assistant_stop_description)
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(AssistantUiTokens.SendButtonSize)
            .clip(CircleShape)
            .background(WorkbenchColors.Danger.copy(alpha = 0.14f))
            .semantics { contentDescription = description }
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = null,
            tint = WorkbenchColors.Danger,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun AssistantInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(AssistantUiTokens.ComposerCornerRadius)
    val attachment = assistantComposerTextAttachment(value)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        cursorBrush = SolidColor(WorkbenchColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSubmit() }),
        textStyle = TextStyle(
            color = WorkbenchColors.Ink,
            fontSize = AssistantUiTokens.InputTextSize,
            lineHeight = AssistantUiTokens.InputLineHeight,
            fontWeight = FontWeight.Medium
        ),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .heightIn(min = AssistantUiTokens.ComposerMinHeight)
                    .clip(shape)
                    .background(WorkbenchColors.SurfaceCard)
                    .padding(horizontal = AssistantUiTokens.InputHorizontalPadding),
                contentAlignment = Alignment.CenterStart
            ) {
                when {
                    value.isBlank() -> {
                    Text(
                        text = stringResource(R.string.assistant_input_label),
                        color = WorkbenchColors.Muted.copy(alpha = AssistantUiTokens.InputHintAlpha),
                        fontSize = AssistantUiTokens.InputTextSize
                    )
                    }

                    attachment != null -> {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${attachment.fileName} · ${attachment.characterCount} chars",
                                color = WorkbenchColors.InkStrong,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            if (attachment.preview.isNotBlank()) {
                                Text(
                                    text = attachment.preview,
                                    color = WorkbenchColors.Muted,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                if (attachment == null) {
                    innerTextField()
                } else {
                    Box(modifier = Modifier.heightIn(max = 1.dp)) {
                        innerTextField()
                    }
                }
            }
        }
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Composer · Day")
@Composable
private fun AssistantComposerDayPreview() {
    WorkbenchTheme(appearanceMode = AppearanceMode.Day, dynamicColor = false) {
        AssistantComposer(
            value = "什么是缺页中断？",
            onValueChange = {},
            onSend = {},
            onStop = {},
            enabled = true
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Composer · Busy Night")
@Composable
private fun AssistantComposerBusyNightPreview() {
    WorkbenchTheme(appearanceMode = AppearanceMode.Night, dynamicColor = false) {
        AssistantComposer(
            value = "",
            onValueChange = {},
            onSend = {},
            onStop = {},
            enabled = false
        )
    }
}
