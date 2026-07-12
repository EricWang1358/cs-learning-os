package com.cslearningos.mobile.feature.capture.data

import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.CaptureSlipStatus
import com.cslearningos.mobile.data.CaptureSlipType
import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.data.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class CaptureRepository(
    private val dao: LearningDao
) {
    val inboxCaptureSlips: Flow<List<CaptureSlipEntity>> = dao.observeInboxCaptureSlips()
    val archivedCaptureSlips: Flow<List<CaptureSlipEntity>> = dao.observeArchivedCaptureSlips()

    suspend fun getCaptureSlip(id: String): CaptureSlipEntity? = dao.getCaptureSlip(id)

    suspend fun saveCaptureSlip(
        id: String? = null,
        expectedRevision: Long? = null,
        body: String,
        type: CaptureSlipType,
        topicHint: String?,
        sourceLabel: String?,
        status: CaptureSlipStatus = CaptureSlipStatus.inbox,
        now: Long = System.currentTimeMillis()
    ): CaptureSlipEntity {
        val existing = if (id == null) {
            null
        } else {
            val slip = requireNotNull(dao.getCaptureSlip(id)) { "Capture slip $id does not exist." }
            check(slip.deletedAt == null) { "Capture slip $id has been deleted." }
            check(expectedRevision == null || slip.revision == expectedRevision) { "Capture slip $id changed before save." }
            slip
        }
        val slip = existing?.copy(
            body = body.trim(),
            type = type,
            topicHint = topicHint?.trim()?.ifBlank { null },
            sourceLabel = sourceLabel?.trim()?.ifBlank { null },
            updatedAt = now,
            revision = existing.revision + RevisionStep,
            syncStatus = SyncStatus.dirty
        ) ?: CaptureSlipEntity(
            id = UUID.randomUUID().toString(),
            body = body.trim(),
            type = type,
            topicHint = topicHint?.trim()?.ifBlank { null },
            sourceLabel = sourceLabel?.trim()?.ifBlank { null },
            linkedNodeId = null,
            status = status,
            createdAt = now,
            updatedAt = now,
            revision = InitialRevision,
            syncStatus = SyncStatus.dirty,
            deletedAt = null
        )
        dao.upsertCaptureSlip(slip)
        return slip
    }

    suspend fun updateCaptureSlipStatus(
        slipId: String,
        status: CaptureSlipStatus,
        now: Long = System.currentTimeMillis()
    ): CaptureSlipEntity? {
        val slip = dao.getCaptureSlip(slipId) ?: return null
        val updated = slip.copy(
            status = status,
            updatedAt = now,
            revision = slip.revision + RevisionStep,
            syncStatus = SyncStatus.dirty
        )
        dao.upsertCaptureSlip(updated)
        return updated
    }

    suspend fun archiveCaptureSlip(slipId: String, now: Long = System.currentTimeMillis()): CaptureSlipEntity? {
        val slip = dao.getCaptureSlip(slipId) ?: return null
        if (slip.deletedAt != null) return null
        if (slip.status == CaptureSlipStatus.archived) return slip
        if (slip.status !in ArchivableStatuses) return null
        val updated = slip.copy(
            status = CaptureSlipStatus.archived,
            updatedAt = now,
            revision = slip.revision + RevisionStep,
            syncStatus = SyncStatus.dirty
        )
        dao.upsertCaptureSlip(updated)
        return updated
    }

    suspend fun restoreCaptureSlip(slipId: String, now: Long = System.currentTimeMillis()): CaptureSlipEntity? {
        val slip = dao.getCaptureSlip(slipId) ?: return null
        if (slip.deletedAt != null) return null
        if (slip.status != CaptureSlipStatus.archived) return null
        val restored = slip.copy(
            status = CaptureSlipStatus.inbox,
            updatedAt = now,
            revision = slip.revision + RevisionStep,
            syncStatus = SyncStatus.dirty
        )
        dao.upsertCaptureSlip(restored)
        return restored
    }

    suspend fun permanentlyDeleteCaptureSlip(slipId: String, now: Long = System.currentTimeMillis()): CaptureSlipEntity? {
        val slip = dao.getCaptureSlip(slipId) ?: return null
        if (slip.deletedAt != null || slip.status != CaptureSlipStatus.archived) return null
        val deleted = slip.copy(
            updatedAt = now,
            revision = slip.revision + RevisionStep,
            syncStatus = SyncStatus.deleted,
            deletedAt = now
        )
        dao.upsertCaptureSlip(deleted)
        return deleted
    }

    private companion object {
        val ArchivableStatuses = setOf(
            CaptureSlipStatus.inbox,
            CaptureSlipStatus.ai_queued,
            CaptureSlipStatus.ai_drafting,
            CaptureSlipStatus.ai_draft_ready
        )
        const val InitialRevision = 1L
        const val RevisionStep = 1L
    }

    suspend fun markCaptureSlipConverted(
        slipId: String,
        nodeId: String,
        now: Long = System.currentTimeMillis()
    ) {
        val slip = dao.getCaptureSlip(slipId) ?: return
        dao.upsertCaptureSlip(
            slip.copy(
                linkedNodeId = nodeId,
                status = CaptureSlipStatus.converted,
                updatedAt = now,
                revision = slip.revision + RevisionStep,
                syncStatus = SyncStatus.dirty
            )
        )
    }
}
