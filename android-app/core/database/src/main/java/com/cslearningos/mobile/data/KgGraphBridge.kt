package com.cslearningos.mobile.data

import com.cslearningos.graph.data.KgStore
import com.cslearningos.graph.data.OutboxPort
import com.cslearningos.graph.data.ProcessedCommandPort
import com.cslearningos.graph.data.ProcessedCommandSnapshot
import com.cslearningos.graph.data.RoomKgStore
import java.security.MessageDigest
import java.util.UUID

/**
 * KnowledgeGraph composition-root bridges (RFC-knowledge-graph §4 端口与适配器).
 *
 * data:graph-room deliberately knows nothing about this module's DAOs; these
 * bridges connect its local ports to the existing processed_commands /
 * replication_outbox tables with the same discipline as SaveNodeCommand:
 * idempotency check + outbox projection run inside RoomKgStore's suspended
 * transaction (Room propagates it along the coroutine context, so calling the
 * DAO here directly joins the same transaction).
 */
class KgProcessedCommandBridge(
    private val dao: LearningDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : ProcessedCommandPort {

    override suspend fun find(commandId: String): ProcessedCommandSnapshot? =
        dao.getProcessedCommand(commandId)?.let {
            ProcessedCommandSnapshot(commandId = it.commandId, fingerprint = it.requestFingerprint)
        }

    override suspend fun record(commandId: String, fingerprint: String) {
        dao.insertProcessedCommand(
            ProcessedCommandEntity(
                commandId = commandId,
                commandType = COMMAND_TYPE,
                requestFingerprint = fingerprint,
                resultType = RESULT_TYPE,
                // RoomKgStore 的重放结果由 facade 层重算(InMemory/契约测试双跑),
                // 命令表只需登记幂等指纹, 结果载荷此处留空对象。
                resultPayloadJson = "{}",
                processedAt = clock(),
            )
        )
    }

    private companion object {
        const val COMMAND_TYPE = "kg.command"
        const val RESULT_TYPE = "kg.write-result"
    }
}

/**
 * Outbox bridge for kg aggregates (KG_QUESTION / KG_EDGE / KG_MASTERY).
 *
 * replication_outbox.command_id carries a UNIQUE index, but one kg command can
 * project MANY outbox rows (e.g. JD confirm → 120 questions + 240 edges), and
 * [OutboxPort.append] does not receive the command id. Rows therefore take a
 * per-row unique surrogate (`kg:<changeId>`); the originating aggregate is
 * always identifiable via aggregate_type + aggregate_id.
 *
 * NOTE: the current sync worker only consumes aggregate_type 'content.node' /
 * 'content.quiz'. KG_* rows stay 'pending' until the kg graph-structure sync
 * phase ships (see docs/knowledge-graph-merge.md) — they never leak into the
 * existing node/quiz upload path.
 */
class KgOutboxBridge(
    private val dao: LearningDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString().replace("-", "") },
) : OutboxPort {

    override suspend fun append(
        aggregateType: String,
        aggregateId: String,
        operation: String,
        payloadJson: String,
    ) {
        val changeId = idGenerator()
        dao.insertOutbox(
            ReplicationOutboxEntity(
                changeId = changeId,
                commandId = "kg:$changeId",
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                operation = operation,
                baseRevision = null,
                newRevision = 1,
                domainSchemaVersion = KG_DOMAIN_SCHEMA_VERSION,
                payloadJson = payloadJson,
                payloadHash = payloadJson.sha256(),
                state = STATE_PENDING,
                createdAt = clock(),
            )
        )
    }

    private companion object {
        const val STATE_PENDING = "pending"
        const val KG_DOMAIN_SCHEMA_VERSION = 1
    }
}

/** One-line factory for the composition root (ViewModel / service locator). */
object KgGraphDeps {
    fun createStore(db: LearningDatabase): KgStore = RoomKgStore(
        db = db,
        daos = db,
        processedCommands = KgProcessedCommandBridge(db.learningDao()),
        outbox = KgOutboxBridge(db.learningDao()),
    )
}

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
