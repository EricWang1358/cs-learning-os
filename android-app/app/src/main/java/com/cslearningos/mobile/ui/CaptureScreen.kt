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
            body = "Write the smallest unclear point. Then promote it to Markdown, quiz material, or an AI-reviewed draft."
        )
        CaptureComposer(state = state, viewModel = viewModel)
        AiDraftPreflight(state = state, viewModel = viewModel)
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
            minLines = 4
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
                text = if (state.aiProviderSettings.isConfigured) "AI draft later" else "Set up AI",
                onClick = if (state.aiProviderSettings.isConfigured) viewModel::saveCaptureSlipForAiDraft else viewModel::showAiServiceSettings
            )
        }
        Text(
            text = "AI chain: save slip -> review preflight -> Validate if needed -> Generate -> edit Markdown -> Save. The model never creates a final node without your Save Markdown.",
            color = WorkbenchColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun AiDraftPreflight(state: LearningUiState, viewModel: LearningViewModel) {
    val slip = state.pendingAiDraftSlipId?.let { slipId -> state.captureSlips.firstOrNull { it.id == slipId } } ?: return
    val settings = state.aiProviderSettings
    val contextTitles = aiDraftContextNodeTitles(state.nodes.map { it.title })
    WorkbenchCard(accent = true) {
        Eyebrow("ai draft preflight")
        Text(
            text = "Ready to generate an editable node draft",
            color = WorkbenchColors.InkStrong,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "Target: ${slip.topicHint ?: "Let AI infer topic"} / Source: ${slip.sourceLabel ?: "manual"}. The result opens in Edit Mode and still needs your Save Markdown confirmation.",
            color = WorkbenchColors.Muted,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(WorkbenchColors.Surface.copy(alpha = 0.68f))
                .border(BorderStroke(1.dp, WorkbenchColors.LineStrong), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Eyebrow("will send")
            Text("Type: ${slip.type.label()}", color = WorkbenchColors.Muted, fontSize = 13.sp)
            Text(slip.body, color = WorkbenchColors.InkStrong, fontSize = 15.sp, lineHeight = 22.sp)
            Text(
                "Provider: ${settings.provider} / Model: ${settings.model} / Base URL: ${settings.baseUrl}",
                color = WorkbenchColors.Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Text(
                text = "Node title context: ${contextTitles.joinToString().ifBlank { "No existing nodes yet" }}",
                color = WorkbenchColors.Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
        Text(
            text = state.aiServiceStatus.body,
            color = WorkbenchColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        ToolbarRow {
            WorkbenchButton("Validate", viewModel::validateAiSettings, enabled = !state.aiBusy)
            WorkbenchButton("Generate draft", viewModel::confirmAiDraftPreflight, primary = true, enabled = !state.aiBusy)
            WorkbenchButton("Cancel", viewModel::cancelAiDraftPreflight)
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
            CaptureSlipCard(
                slip = slip,
                viewModel = viewModel,
                aiConfigured = state.aiProviderSettings.isConfigured,
                aiBusy = state.aiBusy,
                pendingAiDraftSlipId = state.pendingAiDraftSlipId
            )
        }
    }
}

@Composable
private fun CaptureSlipCard(
    slip: CaptureSlipEntity,
    viewModel: LearningViewModel,
    aiConfigured: Boolean,
    aiBusy: Boolean,
    pendingAiDraftSlipId: String?
) {
    val workflow = buildCaptureSlipWorkflow(
        slip = slip,
        pendingAiDraftSlipId = pendingAiDraftSlipId,
        aiBusy = aiBusy,
        aiConfigured = aiConfigured
    )
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
            MetaPill(workflow.statusLabel, slip.topicHint ?: "Inbox", Modifier.weight(1f))
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
        Text(
            text = workflow.detail,
            color = WorkbenchColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        ToolbarRow {
            WorkbenchButton("Make node", { viewModel.promoteCaptureSlipToNode(slip) }, primary = true)
            WorkbenchButton(
                text = if (aiConfigured) workflow.primaryActionLabel else "Set up AI",
                onClick = if (aiConfigured) {
                    { viewModel.prepareAiDraftForSlip(slip) }
                } else {
                    viewModel::showAiServiceSettings
                },
                enabled = workflow.actionsEnabled
            )
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
