package com.cslearningos.mobile.ui

import android.content.Context
import com.cslearningos.mobile.R
import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.CaptureSlipStatus

enum class CaptureSlipWorkflowStage {
    Inbox,
    QueuedForAi,
    Drafting,
    DraftReady,
    NeedsAiSetup,
    LegacyAi
}

enum class CaptureComposerAction {
    SaveLocal
}

enum class CaptureSlipAction {
    MakeNode,
    ImproveWithAi,
    Archive
}

data class CaptureSlipWorkflowModel(
    val stage: CaptureSlipWorkflowStage,
    val statusLabel: String,
    val detail: String,
    val primaryActionLabel: String,
    val actionsEnabled: Boolean
)

fun captureComposerActions(): List<CaptureComposerAction> =
    listOf(CaptureComposerAction.SaveLocal)

fun captureSlipActions(): List<CaptureSlipAction> =
    listOf(CaptureSlipAction.MakeNode, CaptureSlipAction.ImproveWithAi, CaptureSlipAction.Archive)

fun buildCaptureSlipWorkflow(
    context: Context? = null,
    slip: CaptureSlipEntity
): CaptureSlipWorkflowModel {
    val priorAiState = slip.status.priorAiStateLabel()
    if (priorAiState != null) {
        return CaptureSlipWorkflowModel(
            stage = CaptureSlipWorkflowStage.LegacyAi,
            statusLabel = context?.getString(R.string.capture_workflow_status_legacy_ai_needs_attention)
                ?: "Needs attention",
            detail = context?.getString(R.string.capture_workflow_detail_legacy_ai, priorAiState)
                ?: "This slip still carries a prior AI $priorAiState state from an older workflow. Use the single Improve with AI action to continue; no old preflight path will run.",
            primaryActionLabel = context?.getString(R.string.assistant_improve_object) ?: "Improve with AI",
            actionsEnabled = false
        )
    }

    return CaptureSlipWorkflowModel(
        stage = CaptureSlipWorkflowStage.Inbox,
        statusLabel = context?.getString(R.string.capture_workflow_status_saved) ?: "Saved locally",
        detail = context?.getString(R.string.capture_workflow_detail_saved)
            ?: "This slip is saved locally. Turn it into a node, improve it with AI, or archive it.",
        primaryActionLabel = "",
        actionsEnabled = false
    )
}

private fun CaptureSlipStatus.priorAiStateLabel(): String? =
    when (this) {
        CaptureSlipStatus.ai_queued -> "queued"
        CaptureSlipStatus.ai_drafting -> "drafting"
        CaptureSlipStatus.ai_draft_ready -> "draft ready"
        else -> null
    }
