package com.cslearningos.mobile.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

enum class SyncStatus {
    clean,
    dirty,
    deleted,
    conflicted
}

enum class QuizSource {
    manual,
    markdown
}

enum class ReviewResult {
    again,
    hard,
    good
}

enum class CaptureSlipType {
    unclear,
    mistake,
    video_note,
    concept_seed,
    question
}

enum class CaptureSlipStatus {
    inbox,
    ai_queued,
    ai_drafting,
    ai_draft_ready,
    linked,
    converted,
    archived
}

@Entity(tableName = "learning_nodes")
data class LearningNodeEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "markdown_body") val markdownBody: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_read_at") val lastReadAt: Long?,
    val revision: Long,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
    val area: String = "questions",
    @ColumnInfo(name = "area_id") val areaId: String = area,
    val track: String = "general",
    val order: Int = 1000,
    val summary: String = "",
    val visibility: String = "support",
    @ColumnInfo(name = "is_starter") val isStarter: Boolean = false,
    @ColumnInfo(name = "is_checked") val isChecked: Boolean = false
)

fun LearningNodeEntity.withReadTrace(now: Long): LearningNodeEntity =
    copy(lastReadAt = now)

@Entity(tableName = "reader_questions")
data class ReaderQuestionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "node_id") val nodeId: String,
    val body: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "resolved_at") val resolvedAt: Long?,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?
)

@Entity(tableName = "capture_slips")
data class CaptureSlipEntity(
    @PrimaryKey val id: String,
    val body: String,
    val type: CaptureSlipType,
    @ColumnInfo(name = "topic_hint") val topicHint: String?,
    @ColumnInfo(name = "source_label") val sourceLabel: String?,
    @ColumnInfo(name = "linked_node_id") val linkedNodeId: String?,
    val status: CaptureSlipStatus,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val revision: Long,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?
)

@Entity(tableName = "quiz_items")
data class QuizItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "node_id") val nodeId: String?,
    val prompt: String,
    val answer: String,
    val explanation: String,
    val source: QuizSource,
    @ColumnInfo(name = "source_anchor") val sourceAnchor: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val revision: Long,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
    val area: String = "questions",
    val track: String = "general",
    val visibility: String = "practice",
    @ColumnInfo(name = "is_starter") val isStarter: Boolean = false
)

@Entity(tableName = "review_states", primaryKeys = ["quiz_id"])
data class ReviewStateEntity(
    @ColumnInfo(name = "quiz_id") val quizId: String,
    val ease: Double,
    @ColumnInfo(name = "interval_days") val intervalDays: Int,
    @ColumnInfo(name = "due_at") val dueAt: Long,
    @ColumnInfo(name = "last_result") val lastResult: ReviewResult,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(tableName = "review_attempts")
data class ReviewAttemptEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "quiz_id") val quizId: String,
    val result: ReviewResult,
    @ColumnInfo(name = "answered_at") val answeredAt: Long,
    @ColumnInfo(name = "scheduled_due_at") val scheduledDueAt: Long
)

@Fts4
@Entity(tableName = "node_fts")
data class NodeFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Int,
    @ColumnInfo(name = "node_id")
    val nodeId: String,
    val title: String,
    val body: String
)

@Fts4
@Entity(tableName = "quiz_fts")
data class QuizFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Int,
    @ColumnInfo(name = "quiz_id")
    val quizId: String,
    val prompt: String,
    val answer: String
)

data class SearchResultEntity(
    val type: String,
    val id: String,
    val title: String,
    val snippet: String
)
