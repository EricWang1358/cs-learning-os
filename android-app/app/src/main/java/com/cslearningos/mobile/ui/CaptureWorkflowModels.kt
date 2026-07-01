package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.CaptureSlipStatus

enum class CaptureSlipWorkflowStage {
    Inbox,
    QueuedForAi,
    Drafting,
    DraftReady,
    NeedsAiSetup
}

data class CaptureSlipWorkflowModel(
    val stage: CaptureSlipWorkflowStage,
    val statusLabel: String,
    val detail: String,
    val primaryActionLabel: String,
    val actionsEnabled: Boolean
)

fun buildCaptureSlipWorkflow(
    slip: CaptureSlipEntity,
    pendingAiDraftSlipId: String?,
    aiBusy: Boolean,
    aiConfigured: Boolean
): CaptureSlipWorkflowModel {
    val isPending = slip.id == pendingAiDraftSlipId
    return when {
        slip.status == CaptureSlipStatus.ai_drafting || (isPending && aiBusy) -> CaptureSlipWorkflowModel(
            stage = CaptureSlipWorkflowStage.Drafting,
            statusLabel = "Drafting",
            detail = "The model is working on this slip. The result will open as an editable Markdown draft before any node is saved.",
            primaryActionLabel = "Working",
            actionsEnabled = false
        )
        slip.status == CaptureSlipStatus.ai_queued || isPending -> CaptureSlipWorkflowModel(
            stage = CaptureSlipWorkflowStage.QueuedForAi,
            statusLabel = "Queued for AI",
            detail = "Ready to generate an editable Markdown draft. The preflight card shows every slip field and node-title context that will be sent.",
            primaryActionLabel = "Generate draft",
            actionsEnabled = !aiBusy
        )
        slip.status == CaptureSlipStatus.ai_draft_ready -> CaptureSlipWorkflowModel(
            stage = CaptureSlipWorkflowStage.DraftReady,
            statusLabel = "Draft ready",
            detail = "A draft was generated. Review and save it from Edit Mode, or generate again if you left the editor.",
            primaryActionLabel = "Generate again",
            actionsEnabled = !aiBusy
        )
        !aiConfigured -> CaptureSlipWorkflowModel(
            stage = CaptureSlipWorkflowStage.NeedsAiSetup,
            statusLabel = "Needs AI setup",
            detail = "Save the slip locally now. Configure a provider later if you want AI to expand it into Markdown.",
            primaryActionLabel = "Set up AI",
            actionsEnabled = true
        )
        else -> CaptureSlipWorkflowModel(
            stage = CaptureSlipWorkflowStage.Inbox,
            statusLabel = "Inbox",
            detail = "Promote this slip manually into a Markdown node, or queue it for AI drafting.",
            primaryActionLabel = "AI draft",
            actionsEnabled = !aiBusy
        )
    }
}
