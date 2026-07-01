@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cslearningos.mobile.ui

import com.cslearningos.mobile.R
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
            eyebrow = stringResource(R.string.capture_eyebrow),
            title = stringResource(R.string.capture_title),
            body = stringResource(R.string.capture_body)
        )
        CaptureComposer(state = state, viewModel = viewModel)
        CollapsibleWorkbenchSection(
            eyebrow = stringResource(R.string.capture_composer_eyebrow),
            title = stringResource(R.string.capture_chain_title),
            body = stringResource(R.string.capture_chain_summary),
            expandLabel = stringResource(R.string.common_open),
            collapseLabel = stringResource(R.string.common_close),
            initiallyExpanded = screenHelpInitiallyExpanded(AppScreen.Capture)
        ) {
            Text(
                text = stringResource(R.string.capture_ai_chain_body),
                color = WorkbenchColors.Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
        AiDraftPreflight(state = state, viewModel = viewModel)
        CaptureInbox(state = state, viewModel = viewModel)
    }
}

@Composable
private fun CaptureComposer(state: LearningUiState, viewModel: LearningViewModel) {
    WorkbenchCard(accent = true) {
        Eyebrow(stringResource(R.string.capture_composer_eyebrow))
        Text(
            text = stringResource(R.string.capture_composer_title),
            color = WorkbenchColors.InkStrong,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
        WorkbenchTextField(
            value = state.captureDraft,
            onValueChange = viewModel::setCaptureDraft,
            label = stringResource(R.string.capture_draft_field),
            minLines = 4
        )
        WorkbenchTextField(
            value = state.captureTopicHint,
            onValueChange = viewModel::setCaptureTopicHint,
            label = stringResource(R.string.capture_topic_hint_field)
        )
        WorkbenchTextField(
            value = state.captureSourceLabel,
            onValueChange = viewModel::setCaptureSourceLabel,
            label = stringResource(R.string.capture_source_label_field)
        )
        CaptureTypeRow(selected = state.captureType, onSelect = viewModel::setCaptureType)
        ToolbarRow {
            WorkbenchButton(stringResource(R.string.capture_save_slip), viewModel::saveCaptureSlip, primary = true)
            WorkbenchButton(
                text = stringResource(if (state.aiProviderSettings.isConfigured) R.string.capture_ai_draft_later else R.string.capture_set_up_ai),
                onClick = if (state.aiProviderSettings.isConfigured) viewModel::saveCaptureSlipForAiDraft else viewModel::showAiServiceSettings
            )
        }
    }
}

@Composable
private fun AiDraftPreflight(state: LearningUiState, viewModel: LearningViewModel) {
    val slip = state.pendingAiDraftSlipId?.let { slipId -> state.captureSlips.firstOrNull { it.id == slipId } } ?: return
    val settings = state.aiProviderSettings
    val contextTitles = aiDraftContextNodeTitles(state.nodes.map { it.title })
    WorkbenchCard(accent = true) {
        val context = LocalContext.current
        Eyebrow(stringResource(R.string.capture_preflight_eyebrow))
        Text(
            text = stringResource(R.string.capture_preflight_title),
            color = WorkbenchColors.InkStrong,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = stringResource(
                R.string.capture_preflight_body,
                slip.topicHint ?: stringResource(R.string.capture_preflight_target_auto),
                slip.sourceLabel ?: stringResource(R.string.capture_preflight_source_manual)
            ),
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
            Eyebrow(stringResource(R.string.capture_preflight_send_eyebrow))
            Text(stringResource(R.string.capture_preflight_type, slip.type.label()), color = WorkbenchColors.Muted, fontSize = 13.sp)
            Text(slip.body, color = WorkbenchColors.InkStrong, fontSize = 15.sp, lineHeight = 22.sp)
            Text(
                stringResource(R.string.capture_preflight_provider_model, settings.provider, settings.model, settings.baseUrl),
                color = WorkbenchColors.Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Text(
                text = stringResource(R.string.capture_preflight_context, contextTitles.joinToString().ifBlank { stringResource(R.string.common_no_existing_nodes) }),
                color = WorkbenchColors.Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
        Text(
            text = state.aiServiceStatus.body.resolve(context),
            color = WorkbenchColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        ToolbarRow {
            WorkbenchButton(stringResource(R.string.common_validate), viewModel::validateAiSettings, enabled = !state.aiBusy)
            WorkbenchButton(stringResource(R.string.capture_generate_draft), viewModel::confirmAiDraftPreflight, primary = true, enabled = !state.aiBusy)
            WorkbenchButton(stringResource(R.string.common_cancel), viewModel::cancelAiDraftPreflight)
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
        Eyebrow(stringResource(R.string.capture_inbox_eyebrow))
        Text(
            text = stringResource(R.string.capture_inbox_title, state.captureSlips.size),
            color = WorkbenchColors.InkStrong,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
        if (state.captureSlips.isEmpty()) {
            Text(
                text = stringResource(R.string.capture_inbox_empty_body),
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
    val context = LocalContext.current
    val workflow = buildCaptureSlipWorkflow(
        context = context,
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
            MetaPill(workflow.statusLabel, slip.topicHint ?: stringResource(R.string.capture_topic_inbox), Modifier.weight(1f))
            MetaPill(stringResource(R.string.capture_source_label), slip.sourceLabel ?: stringResource(R.string.common_manual), Modifier.weight(1f))
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
            WorkbenchButton(stringResource(R.string.capture_make_node), { viewModel.promoteCaptureSlipToNode(slip) }, primary = true)
            WorkbenchButton(
                text = if (aiConfigured) workflow.primaryActionLabel else stringResource(R.string.capture_set_up_ai),
                onClick = if (aiConfigured) {
                    { viewModel.prepareAiDraftForSlip(slip) }
                } else {
                    viewModel::showAiServiceSettings
                },
                enabled = workflow.actionsEnabled
            )
            WorkbenchButton(stringResource(R.string.capture_archive), { viewModel.archiveCaptureSlip(slip) })
        }
    }
}

@Composable
private fun CaptureSlipType.label(): String =
    when (this) {
        CaptureSlipType.unclear -> stringResource(R.string.capture_type_unclear)
        CaptureSlipType.mistake -> stringResource(R.string.capture_type_mistake)
        CaptureSlipType.video_note -> stringResource(R.string.capture_type_video)
        CaptureSlipType.concept_seed -> stringResource(R.string.capture_type_concept)
        CaptureSlipType.question -> stringResource(R.string.capture_type_question)
    }
