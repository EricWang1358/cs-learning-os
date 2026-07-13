package com.cslearningos.mobile.content.application

import com.cslearningos.mobile.content.domain.NodeId
import com.cslearningos.mobile.core.kernel.CommandId
import com.cslearningos.mobile.core.kernel.EntityRevision

enum class NodeSaveMode {
    Create,
    Update
}

data class SaveNodeCommand(
    val commandId: CommandId,
    val nodeId: NodeId,
    val mode: NodeSaveMode,
    val expectedRevision: EntityRevision?,
    val areaId: String?,
    val title: String,
    val markdownBody: String,
    val occurredAt: Long
) {
    init {
        require(mode != NodeSaveMode.Create || expectedRevision == null)
        require(mode != NodeSaveMode.Update || expectedRevision != null)
    }
}
