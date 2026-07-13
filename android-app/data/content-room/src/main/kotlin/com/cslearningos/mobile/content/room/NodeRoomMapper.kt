package com.cslearningos.mobile.content.room

import com.cslearningos.mobile.content.domain.ContentAreaRef
import com.cslearningos.mobile.content.domain.ContentNode
import com.cslearningos.mobile.content.domain.NodeId
import com.cslearningos.mobile.core.kernel.EntityRevision
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.SyncStatus

object NodeRoomMapper {
    fun toDomain(entity: LearningNodeEntity): ContentNode =
        ContentNode(
            id = NodeId(entity.id),
            title = entity.title,
            markdownBody = entity.markdownBody,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            revision = EntityRevision(entity.revision),
            deletedAt = entity.deletedAt,
            area = ContentAreaRef(id = entity.areaId, slug = entity.area),
            track = entity.track,
            order = entity.order,
            summary = entity.summary,
            visibility = entity.visibility,
            isStarter = entity.isStarter,
            isChecked = entity.isChecked
        )

    fun toEntity(node: ContentNode, existing: LearningNodeEntity?): LearningNodeEntity =
        LearningNodeEntity(
            id = node.id.value,
            title = node.title,
            markdownBody = node.markdownBody,
            createdAt = node.createdAt,
            updatedAt = node.updatedAt,
            lastReadAt = existing?.lastReadAt,
            revision = node.revision.value,
            syncStatus = SyncStatus.dirty,
            deletedAt = node.deletedAt,
            area = node.area.slug,
            areaId = node.area.id,
            track = node.track,
            order = node.order,
            summary = node.summary,
            visibility = node.visibility,
            isStarter = node.isStarter,
            isChecked = node.isChecked
        )
}
