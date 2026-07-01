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
    context: Context? = null,
    slip: CaptureSlipEntity,
    pendingAiDraftSlipId: String?,
    aiBusy: Boolean,
    aiConfigured: Boolean
): CaptureSlipWorkflowModel {
    val isPending = slip.id == pendingAiDraftSlipId
    return when {
        slip.status == CaptureSlipStatus.ai_drafting || (isPending && aiBusy) -> CaptureSlipWorkflowModel(
            stage = CaptureSlipWorkflowStage.Drafting,
            statusLabel = context?.getString(R.string.capture_workflow_status_drafting) ?: "Drafting",
            detail = context?.getString(R.string.capture_workflow_detail_drafting)
                ?: "The model is working on this slip. The result will open as an editable Markdown draft before any node is saved.",
            primaryActionLabel = context?.getString(R.string.capture_workflow_action_working) ?: "Working",
            actionsEnabled = false
        )
        slip.status == CaptureSlipStatus.ai_queued || isPending -> CaptureSlipWorkflowModel(
            stage = CaptureSlipWorkflowStage.QueuedForAi,
            statusLabel = context?.getString(R.string.capture_workflow_status_queued) ?: "Queued for AI",
            detail = context?.getString(R.string.capture_workflow_detail_queued)
                ?: "Ready to generate an editable Markdown draft. The preflight card shows every slip field and node-title context that will be sent.",
            primaryActionLabel = context?.getString(R.string.common_generate_draft) ?: "Generate draft",
            actionsEnabled = !aiBusy
        )
        slip.status == CaptureSlipStatus.ai_draft_ready -> CaptureSlipWorkflowModel(
            stage = CaptureSlipWorkflowStage.DraftReady,
            statusLabel = context?.getString(R.string.capture_workflow_status_ready) ?: "Draft ready",
            detail = context?.getString(R.string.capture_workflow_detail_ready)
                ?: "A draft was generated. Review and save it from Edit Mode, or generate again if you left the editor.",
            primaryActionLabel = context?.getString(R.string.capture_workflow_action_generate_again) ?: "Generate again",
            actionsEnabled = !aiBusy
        )
        !aiConfigured -> CaptureSlipWorkflowModel(
            stage = CaptureSlipWorkflowStage.NeedsAiSetup,
            statusLabel = context?.getString(R.string.capture_workflow_status_needs_setup) ?: "Needs AI setup",
            detail = context?.getString(R.string.capture_workflow_detail_needs_setup)
                ?: "Save the slip locally now. Configure a provider later if you want AI to expand it into Markdown.",
            primaryActionLabel = context?.getString(R.string.capture_set_up_ai) ?: "Set up AI",
            actionsEnabled = true
        )
        else -> CaptureSlipWorkflowModel(
            stage = CaptureSlipWorkflowStage.Inbox,
            statusLabel = context?.getString(R.string.capture_workflow_status_inbox) ?: "Inbox",
            detail = context?.getString(R.string.capture_workflow_detail_inbox)
                ?: "Promote this slip manually into a Markdown node, or queue it for AI drafting.",
            primaryActionLabel = context?.getString(R.string.capture_workflow_action_ai_draft) ?: "AI draft",
            actionsEnabled = !aiBusy
        )
    }
}
