package com.cslearningos.mobile.content.domain

import com.cslearningos.mobile.core.kernel.EntityRevision

@JvmInline
value class NodeId(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

data class ContentAreaRef(
    val id: String,
    val slug: String
)

data class ContentNode(
    val id: NodeId,
    val title: String,
    val markdownBody: String,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: EntityRevision,
    val deletedAt: Long?,
    val area: ContentAreaRef,
    val track: String,
    val order: Int,
    val summary: String,
    val visibility: String,
    val isStarter: Boolean,
    val isChecked: Boolean
)
