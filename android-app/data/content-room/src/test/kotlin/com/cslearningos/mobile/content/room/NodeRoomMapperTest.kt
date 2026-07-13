package com.cslearningos.mobile.content.room

import com.cslearningos.mobile.content.domain.ContentAreaRef
import com.cslearningos.mobile.content.domain.ContentNode
import com.cslearningos.mobile.content.domain.NodeId
import com.cslearningos.mobile.core.kernel.EntityRevision
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NodeRoomMapperTest {
    @Test
    fun toDomainMapsEveryContentField() {
        val entity = entity(lastReadAt = 987L, syncStatus = SyncStatus.conflicted)

        assertEquals(node(), NodeRoomMapper.toDomain(entity))
    }

    @Test
    fun toEntityPreservesExistingReadTraceAndMarksNodeDirty() {
        val mapped = NodeRoomMapper.toEntity(
            node(),
            existing = entity(lastReadAt = 987L, syncStatus = SyncStatus.clean)
        )

        assertEquals(
            LearningNodeEntity(
                id = "node-7",
                title = "Memory ordering",
                markdownBody = "# Memory ordering\n\nAcquire and release.",
                createdAt = 100L,
                updatedAt = 200L,
                lastReadAt = 987L,
                revision = 8L,
                syncStatus = SyncStatus.dirty,
                deletedAt = 300L,
                area = "concurrency",
                areaId = "area-2",
                track = "systems",
                order = 11,
                summary = "Ordering guarantees.",
                visibility = "core",
                isStarter = true,
                isChecked = true
            ),
            mapped
        )
    }

    @Test
    fun toEntityWithoutExistingEntityLeavesReadTraceUnset() {
        val mapped = NodeRoomMapper.toEntity(node(), existing = null)

        assertNull(mapped.lastReadAt)
        assertEquals(SyncStatus.dirty, mapped.syncStatus)
    }

    private fun node() = ContentNode(
        id = NodeId("node-7"),
        title = "Memory ordering",
        markdownBody = "# Memory ordering\n\nAcquire and release.",
        createdAt = 100L,
        updatedAt = 200L,
        revision = EntityRevision(8L),
        deletedAt = 300L,
        area = ContentAreaRef(id = "area-2", slug = "concurrency"),
        track = "systems",
        order = 11,
        summary = "Ordering guarantees.",
        visibility = "core",
        isStarter = true,
        isChecked = true
    )

    private fun entity(lastReadAt: Long?, syncStatus: SyncStatus) = LearningNodeEntity(
        id = "node-7",
        title = "Memory ordering",
        markdownBody = "# Memory ordering\n\nAcquire and release.",
        createdAt = 100L,
        updatedAt = 200L,
        lastReadAt = lastReadAt,
        revision = 8L,
        syncStatus = syncStatus,
        deletedAt = 300L,
        area = "concurrency",
        areaId = "area-2",
        track = "systems",
        order = 11,
        summary = "Ordering guarantees.",
        visibility = "core",
        isStarter = true,
        isChecked = true
    )
}
