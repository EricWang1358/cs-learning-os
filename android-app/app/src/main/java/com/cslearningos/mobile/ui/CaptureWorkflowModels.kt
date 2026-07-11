package com.cslearningos.mobile.ui

import android.content.Context
import com.cslearningos.mobile.R
import com.cslearningos.mobile.data.CaptureSlipEntity

enum class CaptureSlipWorkflowStage {
    Inbox,
    QueuedForAi,
    Drafting,
    DraftReady,
    NeedsAiSetup
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
    slip: CaptureSlipEntity,
    pendingAiDraftSlipId: String?,
    aiBusy: Boolean,
    aiConfigured: Boolean
): CaptureSlipWorkflowModel {
    return CaptureSlipWorkflowModel(
        stage = CaptureSlipWorkflowStage.Inbox,
        statusLabel = context?.getString(R.string.capture_workflow_status_saved) ?: "Saved locally",
        detail = context?.getString(R.string.capture_workflow_detail_saved)
            ?: "This slip is saved locally. Turn it into a node, improve it with AI, or archive it.",
        primaryActionLabel = "",
        actionsEnabled = false
    )
}
