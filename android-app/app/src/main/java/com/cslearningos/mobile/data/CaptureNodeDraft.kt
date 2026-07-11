package com.cslearningos.mobile.data

data class CaptureNodeDraft(
    val title: String,
    val suggestedNodeId: String?,
    val suggestedAreaId: String?,
    val markdownBody: String
) {
    companion object {
        fun fromSlip(slip: CaptureSlipEntity, existingNodes: List<LearningNodeEntity>): CaptureNodeDraft {
            val title = slip.topicHint?.trim()?.takeIf { it.isNotBlank() }
                ?: slip.body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(64)
                ?: "New learning thread"
            val suggestedNode = slip.topicHint?.let { hint ->
                existingNodes.firstOrNull { node -> node.title.equals(hint.trim(), ignoreCase = true) }
            }

            return CaptureNodeDraft(
                title = title,
                suggestedNodeId = suggestedNode?.id,
                suggestedAreaId = suggestedNode?.areaId,
                markdownBody = buildString {
                    append("# ").append(title).append("\n\n")
                    suggestedNode?.let { append("> Suggested existing node: ").append(it.title).append("\n\n") }
                    append("## Captured Question\n\n")
                    append(slip.body.trim()).append("\n\n")
                    append("## What I Need To Understand\n\n")
                    append("- \n\n")
                    append("## Notes\n\n")
                    slip.sourceLabel?.let { append("> Source: ").append(it).append("\n") }
                    append("> Type: ").append(slip.type.name).append("\n")
                }
            )
        }
    }
}
