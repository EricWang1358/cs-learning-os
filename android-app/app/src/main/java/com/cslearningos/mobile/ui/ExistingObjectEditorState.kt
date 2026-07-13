package com.cslearningos.mobile.ui

import com.cslearningos.mobile.R
import com.cslearningos.mobile.content.application.CommandFingerprint
import com.cslearningos.mobile.content.application.NodeSaveMode
import com.cslearningos.mobile.content.application.SaveNodeCommand
import com.cslearningos.mobile.content.domain.NodeId
import com.cslearningos.mobile.core.kernel.CommandId
import com.cslearningos.mobile.core.kernel.EntityRevision
import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.CaptureNodeDraft
import com.cslearningos.mobile.data.LearningRepository
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.QuizItemEntity
import java.util.UUID

internal fun LearningUiState.forNewNodeEditor(areaId: String?): LearningUiState = copy(
    screen = AppScreen.Editor,
    editorAreaId = areaId,
    editorNodeId = null,
    editorExpectedRevision = null,
    editorSourceCaptureSlipId = null,
    editorTitle = "",
    editorBody = "",
    pendingNodeSave = null,
    message = null
)

internal fun LearningUiState.forExistingNodeEditor(node: LearningNodeEntity): LearningUiState = copy(
    screen = AppScreen.Editor,
    selectedNode = node,
    editorAreaId = node.areaId,
    editorNodeId = node.id,
    editorExpectedRevision = node.revision,
    editorSourceCaptureSlipId = null,
    editorTitle = node.title,
    editorBody = node.markdownBody,
    pendingNodeSave = null,
    message = null
)

internal fun LearningUiState.forCapturePromotionEditor(
    slip: CaptureSlipEntity,
    draft: CaptureNodeDraft
): LearningUiState = copy(
    screen = AppScreen.Editor,
    editorNodeId = null,
    editorExpectedRevision = null,
    editorAreaId = draft.suggestedAreaId,
    editorSourceCaptureSlipId = slip.id,
    selectedNode = draft.suggestedNodeId?.let { nodeId -> nodes.firstOrNull { it.id == nodeId } },
    editorTitle = draft.title,
    editorBody = draft.markdownBody,
    pendingNodeSave = null,
    message = uiText(R.string.message_review_capture_draft)
)

internal fun LearningUiState.forAiCaptureDraftEditor(
    slip: CaptureSlipEntity,
    areaId: String?,
    title: String,
    body: String
): LearningUiState = copy(
    screen = AppScreen.Editor,
    editorNodeId = null,
    editorExpectedRevision = null,
    editorAreaId = areaId,
    editorSourceCaptureSlipId = slip.id,
    selectedNode = null,
    editorTitle = title,
    editorBody = body,
    pendingNodeSave = null
)

internal fun LearningUiState.withEditorTitle(value: String): LearningUiState =
    copy(editorTitle = value, pendingNodeSave = null)

internal fun LearningUiState.withEditorBody(value: String): LearningUiState =
    copy(editorBody = value, pendingNodeSave = null)

internal fun LearningUiState.withEditorAreaId(value: String): LearningUiState =
    copy(editorAreaId = value, pendingNodeSave = null)

internal fun LearningUiState.withPendingNodeSave(): LearningUiState {
    pendingNodeSave?.let { return this }
    val nodeId = editorNodeId ?: UUID.randomUUID().toString()
    val command = SaveNodeCommand(
        commandId = CommandId(UUID.randomUUID().toString()),
        nodeId = NodeId(nodeId),
        mode = if (editorNodeId == null) NodeSaveMode.Create else NodeSaveMode.Update,
        expectedRevision = editorExpectedRevision?.let(::EntityRevision),
        areaId = editorAreaId,
        title = editorTitle,
        markdownBody = editorBody,
        occurredAt = 0L
    )
    return copy(
        pendingNodeSave = PendingNodeSave(
            commandId = command.commandId.value,
            nodeId = command.nodeId.value,
            fingerprint = CommandFingerprint.of(command)
        )
    )
}

internal fun LearningUiState.cancelNodeEditor(): LearningUiState = copy(
    screen = if (selectedNode == null) AppScreen.Home else AppScreen.Reader,
    editorAreaId = null,
    pendingNodeSave = null,
    message = null
)

internal fun LearningUiState.forExistingCaptureEditor(slip: CaptureSlipEntity): LearningUiState = copy(
    screen = AppScreen.Capture,
    captureEditorId = slip.id,
    captureExpectedRevision = slip.revision,
    captureDraft = slip.body,
    captureTopicHint = slip.topicHint.orEmpty(),
    captureSourceLabel = slip.sourceLabel.orEmpty(),
    captureType = slip.type,
    pendingAiDraftSlipId = null,
    message = null
)

internal fun LearningUiState.clearedCaptureEditor(): LearningUiState = copy(
    captureEditorId = null,
    captureExpectedRevision = null,
    captureDraft = "",
    captureTopicHint = "",
    captureSourceLabel = ""
)

internal fun LearningUiState.afterNodeSaved(node: LearningNodeEntity): LearningUiState = copy(
    screen = AppScreen.Reader,
    selectedNode = node,
    editorAreaId = null,
    editorExpectedRevision = null,
    editorSourceCaptureSlipId = null,
    pendingNodeSave = null,
    message = uiText(R.string.message_node_saved)
)

internal fun LearningUiState.afterCaptureSaved(): LearningUiState =
    clearedCaptureEditor().copy(message = uiText(R.string.message_capture_saved_to_inbox))

internal fun LearningUiState.afterQuizSaved(quiz: QuizItemEntity): LearningUiState = copy(
    screen = if (quiz.nodeId != null && selectedNode != null) AppScreen.Reader else AppScreen.Review,
    selectedQuiz = quiz,
    reviewedQuiz = null,
    reviewAreaId = quiz.area,
    reviewSetupVisible = quiz.nodeId != null && selectedNode != null,
    quizAnswerVisible = false,
    quizEditorId = null,
    quizExpectedRevision = null,
    quizAreaId = null,
    message = uiText(R.string.message_quiz_saved)
)

internal fun LearningUiState.withObjectSaveRejected(): LearningUiState =
    copy(message = uiText(R.string.message_assistant_source_unavailable))

suspend fun LearningRepository.saveNodeFromEditor(state: LearningUiState): LearningNodeEntity =
    state.pendingNodeSave?.let { pending ->
        saveNode(
            SaveNodeCommand(
                commandId = CommandId(pending.commandId),
                nodeId = NodeId(pending.nodeId),
                mode = if (state.editorNodeId == null) NodeSaveMode.Create else NodeSaveMode.Update,
                expectedRevision = state.editorExpectedRevision?.let(::EntityRevision),
                areaId = state.editorAreaId,
                title = state.editorTitle,
                markdownBody = state.editorBody,
                occurredAt = System.currentTimeMillis()
            )
        )
    } ?: throw IllegalStateException("Node save is missing a pending command.")

suspend fun LearningRepository.saveCaptureFromEditor(state: LearningUiState): CaptureSlipEntity =
    saveCaptureSlip(
        id = state.captureEditorId,
        expectedRevision = state.captureExpectedRevision,
        body = state.captureDraft,
        type = state.captureType,
        topicHint = state.captureTopicHint,
        sourceLabel = state.captureSourceLabel
    )

suspend fun LearningRepository.saveQuizFromEditor(state: LearningUiState): QuizItemEntity =
    saveManualQuiz(
        id = state.quizEditorId,
        expectedRevision = state.quizExpectedRevision,
        nodeId = state.quizNodeIdForSave(),
        areaId = state.quizAreaIdForSave(),
        prompt = state.quizPrompt,
        answer = state.quizAnswer,
        explanation = state.quizExplanation
    )

internal fun LearningUiState.forExistingQuizEditorWithoutSelectedNode(quiz: QuizItemEntity): LearningUiState =
    forExistingQuizEditor(quiz).copy(selectedNode = null)

internal fun LearningUiState.quizNodeIdForSave(): String? =
    if (quizEditorId != null) selectedQuiz?.nodeId else selectedNode?.id ?: selectedQuiz?.nodeId

internal fun LearningUiState.quizAreaIdForSave(): String? =
    if (quizNodeIdForSave() == null) quizAreaId else null
