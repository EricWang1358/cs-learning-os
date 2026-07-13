package com.cslearningos.mobile.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "processed_commands")
data class ProcessedCommandEntity(
    @PrimaryKey @ColumnInfo(name = "command_id") val commandId: String,
    @ColumnInfo(name = "command_type") val commandType: String,
    @ColumnInfo(name = "request_fingerprint") val requestFingerprint: String,
    @ColumnInfo(name = "result_type") val resultType: String,
    @ColumnInfo(name = "result_payload_json") val resultPayloadJson: String,
    @ColumnInfo(name = "processed_at") val processedAt: Long
)

@Entity(
    tableName = "replication_outbox",
    indices = [
        Index(value = ["command_id"], unique = true),
        Index(value = ["state", "created_at"])
    ]
)
data class ReplicationOutboxEntity(
    @PrimaryKey @ColumnInfo(name = "change_id") val changeId: String,
    @ColumnInfo(name = "command_id") val commandId: String,
    @ColumnInfo(name = "aggregate_type") val aggregateType: String,
    @ColumnInfo(name = "aggregate_id") val aggregateId: String,
    val operation: String,
    @ColumnInfo(name = "base_revision") val baseRevision: Long?,
    @ColumnInfo(name = "new_revision") val newRevision: Long,
    @ColumnInfo(name = "domain_schema_version") val domainSchemaVersion: Int,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "payload_hash") val payloadHash: String,
    val state: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
