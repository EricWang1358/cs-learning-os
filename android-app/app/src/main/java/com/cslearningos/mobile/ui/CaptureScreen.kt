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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

private data class CaptureScreenState(
    val draft: String,
    val topicHint: String,
    val sourceLabel: String,
    val type: CaptureSlipType,
    val captureSlips: List<CaptureSlipEntity>,
    val archivedCaptureSlips: List<CaptureSlipEntity>
)

@Composable
fun CaptureScreen(state: LearningUiState, viewModel: LearningViewModel) {
    val screenState = state.toCaptureScreenState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = stringResource(R.string.capture_eyebrow),
            title = stringResource(R.string.capture_title),
            body = stringResource(R.string.capture_body)
        )
        CaptureComposer(state = screenState, viewModel = viewModel)
        CaptureInbox(state = screenState, viewModel = viewModel)
        CaptureArchive(state = screenState, viewModel = viewModel)
    }
}

@Composable
private fun CaptureComposer(state: CaptureScreenState, viewModel: LearningViewModel) {
    WorkbenchCard(accent = true) {
        Eyebrow(stringResource(R.string.capture_composer_eyebrow))
        Text(
            text = stringResource(R.string.capture_composer_title),
            color = WorkbenchColors.InkStrong,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )
        WorkbenchTextField(
            value = state.draft,
            onValueChange = viewModel::setCaptureDraft,
            label = stringResource(R.string.capture_draft_field),
            minLines = 4
        )
        WorkbenchTextField(
            value = state.topicHint,
            onValueChange = viewModel::setCaptureTopicHint,
            label = stringResource(R.string.capture_topic_hint_field)
        )
        WorkbenchTextField(
            value = state.sourceLabel,
            onValueChange = viewModel::setCaptureSourceLabel,
            label = stringResource(R.string.capture_source_label_field)
        )
        CaptureTypeRow(selected = state.type, onSelect = viewModel::setCaptureType)
        ToolbarRow {
            WorkbenchButton(stringResource(R.string.capture_save_slip), viewModel::saveCaptureSlip, primary = true)
        }
    }
}

@Composable
private fun CaptureTypeRow(selected: CaptureSlipType, onSelect: (CaptureSlipType) -> Unit) {
    WorkbenchMenuButton(
        text = selected.label(),
        options = CaptureSlipType.entries.map { type ->
            WorkbenchMenuOption(type.label()) { onSelect(type) }
        },
        primary = true,
        modifier = Modifier.fillMaxWidth(),
        expandToContainer = true
    )
}

@Composable
private fun CaptureInbox(state: CaptureScreenState, viewModel: LearningViewModel) {
    WorkbenchCard {
        Eyebrow(stringResource(R.string.capture_inbox_eyebrow))
        Text(
            text = stringResource(R.string.capture_inbox_title, state.captureSlips.size),
            color = WorkbenchColors.InkStrong,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold
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
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun CaptureArchive(state: CaptureScreenState, viewModel: LearningViewModel) {
    var deleteForeverSlipId by rememberSaveable { mutableStateOf<String?>(null) }
    WorkbenchCard {
        Eyebrow(stringResource(R.string.capture_archived_eyebrow))
        Text(
            text = stringResource(R.string.capture_archived_title, state.archivedCaptureSlips.size),
            color = WorkbenchColors.InkStrong,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold
        )
        if (state.archivedCaptureSlips.isEmpty()) {
            Text(
                text = stringResource(R.string.capture_archived_empty_body),
                color = WorkbenchColors.Muted,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
            return@WorkbenchCard
        }
        state.archivedCaptureSlips.take(8).forEach { slip ->
            CaptureSlipCard(
                slip = slip,
                viewModel = viewModel,
                onDeleteForeverRequest = { deleteForeverSlipId = it.id }
            )
        }
    }
    state.archivedCaptureSlips.firstOrNull { it.id == deleteForeverSlipId }?.let { slip ->
        ConfirmDestructiveDialog(
            title = stringResource(R.string.capture_delete_forever_confirm_title),
            body = stringResource(R.string.capture_delete_forever_confirm_body, captureSlipLabel(slip)),
            confirmLabel = stringResource(R.string.common_delete_forever),
            onDismiss = { deleteForeverSlipId = null },
            onConfirm = {
                deleteForeverSlipId = null
                viewModel.permanentlyDeleteCaptureSlip(slip)
            }
        )
    }
}

@Composable
private fun CaptureSlipCard(
    slip: CaptureSlipEntity,
    viewModel: LearningViewModel,
    onDeleteForeverRequest: ((CaptureSlipEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    val workflow = buildCaptureSlipWorkflow(
        context = context,
        slip = slip
    )
    val actions = captureSlipActions(slip)
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
        if (actions.isNotEmpty()) {
            ToolbarRow {
                actions.forEach { action ->
                    when (action) {
                        CaptureSlipAction.MakeNode -> WorkbenchButton(
                            text = stringResource(R.string.capture_make_node),
                            onClick = { viewModel.promoteCaptureSlipToNode(slip) },
                            primary = true
                        )

                        CaptureSlipAction.ImproveWithAi -> WorkbenchButton(
                            text = stringResource(R.string.assistant_improve_object),
                            onClick = { viewModel.assistantActions.reviseCapture(slip) },
                            primary = actions.size == 1
                        )

                        CaptureSlipAction.Archive -> WorkbenchButton(
                            text = stringResource(R.string.capture_archive),
                            onClick = { viewModel.archiveCaptureSlip(slip) }
                        )

                        CaptureSlipAction.Restore -> WorkbenchButton(
                            text = stringResource(R.string.common_restore),
                            onClick = { viewModel.restoreCaptureSlip(slip) },
                            primary = true
                        )

                        CaptureSlipAction.DeleteForever -> WorkbenchButton(
                            text = stringResource(R.string.common_delete_forever),
                            onClick = { onDeleteForeverRequest?.invoke(slip) },
                            danger = true
                        )
                    }
                }
            }
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

private fun LearningUiState.toCaptureScreenState(): CaptureScreenState =
    CaptureScreenState(
        draft = captureDraft,
        topicHint = captureTopicHint,
        sourceLabel = captureSourceLabel,
        type = captureType,
        captureSlips = captureSlips,
        archivedCaptureSlips = archivedCaptureSlips
    )

private fun captureSlipLabel(slip: CaptureSlipEntity): String =
    slip.topicHint
        ?.takeIf { it.isNotBlank() }
        ?: slip.body.lineSequence().firstOrNull { it.isNotBlank() }?.take(32).orEmpty()
