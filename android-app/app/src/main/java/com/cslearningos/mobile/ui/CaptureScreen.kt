@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cslearningos.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.CaptureSlipType

@Composable
fun CaptureScreen(state: LearningUiState, viewModel: LearningViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = "quick capture",
            title = "Capture slip inbox",
            body = "Save the smallest unclear point now. Later, turn it into a Markdown node, quiz card, or AI draft."
        )
        CaptureComposer(state = state, viewModel = viewModel)
        CaptureInbox(state = state, viewModel = viewModel)
    }
}

@Composable
private fun CaptureComposer(state: LearningUiState, viewModel: LearningViewModel) {
    WorkbenchCard(accent = true) {
        Eyebrow("new slip")
        Text(
            text = "What should not be forgotten?",
            color = WorkbenchColors.InkStrong,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
        WorkbenchTextField(
            value = state.captureDraft,
            onValueChange = viewModel::setCaptureDraft,
            label = "Example: I do not understand why TLB miss triggers a page table walk.",
            minLines = 5
        )
        WorkbenchTextField(
            value = state.captureTopicHint,
            onValueChange = viewModel::setCaptureTopicHint,
            label = "Topic hint, optional"
        )
        WorkbenchTextField(
            value = state.captureSourceLabel,
            onValueChange = viewModel::setCaptureSourceLabel,
            label = "Source label, optional"
        )
        CaptureTypeRow(selected = state.captureType, onSelect = viewModel::setCaptureType)
        ToolbarRow {
            WorkbenchButton("Save slip", viewModel::saveCaptureSlip, primary = true)
            WorkbenchButton(
                text = if (state.aiProviderSettings.isConfigured) "AI draft later" else "Configure AI",
                onClick = viewModel::showMore
            )
        }
    }
}

@Composable
private fun CaptureTypeRow(selected: CaptureSlipType, onSelect: (CaptureSlipType) -> Unit) {
    ToolbarRow {
        CaptureSlipType.entries.forEach { type ->
            WorkbenchButton(
                text = type.label(),
                onClick = { onSelect(type) },
                primary = selected == type
            )
        }
    }
}

@Composable
private fun CaptureInbox(state: LearningUiState, viewModel: LearningViewModel) {
    WorkbenchCard {
        Eyebrow("inbox")
        Text(
            text = "${state.captureSlips.size} open capture slips",
            color = WorkbenchColors.InkStrong,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
        if (state.captureSlips.isEmpty()) {
            Text(
                text = "No slips yet. The goal is not long writing; it is catching useful fragments before they vanish.",
                color = WorkbenchColors.Muted,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
            return@WorkbenchCard
        }
        state.captureSlips.take(8).forEach { slip ->
            CaptureSlipCard(slip = slip, viewModel = viewModel)
        }
    }
}

@Composable
private fun CaptureSlipCard(slip: CaptureSlipEntity, viewModel: LearningViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WorkbenchColors.Surface.copy(alpha = 0.68f))
            .border(BorderStroke(1.dp, WorkbenchColors.Line), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MetaPill(slip.type.label(), slip.topicHint ?: "Inbox", Modifier.weight(1f))
            MetaPill("Source", slip.sourceLabel ?: "manual", Modifier.weight(1f))
        }
        Text(
            text = slip.body,
            color = WorkbenchColors.InkStrong,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis
        )
        ToolbarRow {
            WorkbenchButton("Turn into node", { viewModel.promoteCaptureSlipToNode(slip) }, primary = true)
            WorkbenchButton("Archive", { viewModel.archiveCaptureSlip(slip) })
        }
    }
}

private fun CaptureSlipType.label(): String =
    when (this) {
        CaptureSlipType.unclear -> "Unclear"
        CaptureSlipType.mistake -> "Mistake"
        CaptureSlipType.video_note -> "Video"
        CaptureSlipType.concept_seed -> "Concept"
        CaptureSlipType.question -> "Question"
    }
