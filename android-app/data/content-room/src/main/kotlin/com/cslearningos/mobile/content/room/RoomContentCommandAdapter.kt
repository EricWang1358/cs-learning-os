package com.cslearningos.mobile.content.room

import androidx.room.withTransaction
import com.cslearningos.mobile.content.application.CommandFingerprint
import com.cslearningos.mobile.content.application.ContentCommandFailure
import com.cslearningos.mobile.content.application.ContentCommandPort
import com.cslearningos.mobile.content.application.ContentCommandResult
import com.cslearningos.mobile.content.application.NodeSaveMode
import com.cslearningos.mobile.content.application.SaveNodeCommand
import com.cslearningos.mobile.content.domain.ContentAreaRef
import com.cslearningos.mobile.content.domain.ContentFailure
import com.cslearningos.mobile.content.domain.NodeEditor
import com.cslearningos.mobile.content.domain.NodeSaveDecision
import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.data.LearningDatabase
import com.cslearningos.mobile.data.ProcessedCommandEntity
import com.cslearningos.mobile.data.ReplicationOutboxEntity
import kotlinx.coroutines.CancellationException
import java.util.UUID

class RoomContentCommandAdapter(
    private val database: LearningDatabase,
    private val projectionWriter: ContentProjectionWriter = RoomContentProjectionWriter,
    private val changeIdFactory: () -> String = { UUID.randomUUID().toString() }
) : ContentCommandPort {
    override suspend fun saveNode(command: SaveNodeCommand): ContentCommandResult = try {
        database.withTransaction {
            saveNodeInTransaction(command)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        ContentCommandResult.Failure(ContentCommandFailure.Storage(STORAGE_ERROR_CODE))
    }

    private suspend fun saveNodeInTransaction(command: SaveNodeCommand): ContentCommandResult {
        val dao = database.learningDao()
        val fingerprint = CommandFingerprint.of(command)
        val processed = dao.getProcessedCommand(command.commandId.value)
        if (processed != null) {
            return if (processed.requestFingerprint == fingerprint) {
                ContentCommandResult.Success(ContentNodeCodec.decode(processed.resultPayloadJson))
            } else {
                ContentCommandResult.Failure(ContentCommandFailure.CommandReuseConflict)
            }
        }

        val existingEntity = dao.getNode(command.nodeId.value)
        val preconditionFailure = preconditionFailure(command, existingEntity != null)
        if (preconditionFailure != null) return ContentCommandResult.Failure(preconditionFailure)

        val areaId = command.areaId ?: existingEntity!!.areaId
        val areaEntity = resolveArea(dao, areaId, command.occurredAt)
            ?: return ContentCommandResult.Failure(ContentCommandFailure.Missing(AREA_TARGET))
        val decision = NodeEditor.save(
            existing = existingEntity?.let(NodeRoomMapper::toDomain),
            expectedRevision = command.expectedRevision,
            nodeId = command.nodeId,
            area = ContentAreaRef(id = areaEntity.id, slug = areaEntity.slug),
            title = command.title,
            markdownBody = command.markdownBody,
            now = command.occurredAt
        )
        val accepted = decision as? NodeSaveDecision.Accepted
            ?: return ContentCommandResult.Failure((decision as NodeSaveDecision.Rejected).failure.toApplicationFailure())

        val savedEntity = NodeRoomMapper.toEntity(accepted.node, existingEntity)
        dao.upsertNode(savedEntity)
        projectionWriter.write(dao, savedEntity, command.occurredAt)

        val payload = ContentNodeCodec.encode(accepted.node)
        dao.insertOutbox(
            ReplicationOutboxEntity(
                changeId = changeIdFactory(),
                commandId = command.commandId.value,
                aggregateType = AGGREGATE_TYPE,
                aggregateId = accepted.node.id.value,
                operation = accepted.operation.name.lowercase(),
                baseRevision = existingEntity?.revision,
                newRevision = accepted.node.revision.value,
                domainSchemaVersion = ContentNodeCodec.SchemaVersion,
                payloadJson = payload,
                payloadHash = ContentNodeCodec.sha256Hex(payload),
                state = OUTBOX_PENDING,
                createdAt = command.occurredAt
            )
        )
        dao.insertProcessedCommand(
            ProcessedCommandEntity(
                commandId = command.commandId.value,
                commandType = COMMAND_TYPE,
                requestFingerprint = fingerprint,
                resultType = RESULT_TYPE,
                resultPayloadJson = payload,
                processedAt = command.occurredAt
            )
        )
        return ContentCommandResult.Success(accepted.node)
    }

    private fun preconditionFailure(
        command: SaveNodeCommand,
        targetExists: Boolean
    ): ContentCommandFailure? = when (command.mode) {
        NodeSaveMode.Create -> when {
            targetExists -> ContentCommandFailure.Validation(CREATE_EXISTS_CODE)
            command.areaId == null -> ContentCommandFailure.Validation(CREATE_MISSING_AREA_CODE)
            else -> null
        }
        NodeSaveMode.Update -> if (!targetExists) ContentCommandFailure.Missing(NODE_TARGET) else null
    }

    private fun ContentFailure.toApplicationFailure(): ContentCommandFailure = when (this) {
        is ContentFailure.Validation -> ContentCommandFailure.Validation(code)
        ContentFailure.Deleted -> ContentCommandFailure.Deleted
        is ContentFailure.StaleRevision -> ContentCommandFailure.StaleRevision(
            expected = expected.value,
            actual = actual.value
        )
    }

    private suspend fun resolveArea(
        dao: LearningDao,
        areaIdOrSlug: String,
        now: Long
    ): AreaEntity? = dao.getArea(areaIdOrSlug)
        ?: dao.getAreaBySlug(areaIdOrSlug)
        ?: if (areaIdOrSlug == DEFAULT_AREA_SLUG) {
            createDefaultArea(dao, now)
        } else {
            null
        }

    private suspend fun createDefaultArea(
        dao: LearningDao,
        now: Long
    ): AreaEntity {
        val area = AreaEntity(
            id = DEFAULT_AREA_SLUG,
            slug = DEFAULT_AREA_SLUG,
            name = DEFAULT_AREA_SLUG,
            order = (dao.getAllAreas().maxOfOrNull { it.order } ?: 0) + AREA_ORDER_STEP,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )
        dao.upsertArea(area)
        return area
    }

    private companion object {
        const val AGGREGATE_TYPE = "content.node"
        const val AREA_TARGET = "area"
        const val COMMAND_TYPE = "content.node.save"
        const val CREATE_EXISTS_CODE = "create.node_exists"
        const val CREATE_MISSING_AREA_CODE = "create.missing_area"
        const val DEFAULT_AREA_SLUG = "questions"
        const val AREA_ORDER_STEP = 10
        const val NODE_TARGET = "node"
        const val OUTBOX_PENDING = "pending"
        const val RESULT_TYPE = "content.node"
        const val STORAGE_ERROR_CODE = "content.command.storage"
    }
}
