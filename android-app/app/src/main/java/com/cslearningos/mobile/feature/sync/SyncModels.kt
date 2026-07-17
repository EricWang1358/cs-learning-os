package com.cslearningos.mobile.feature.sync

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Subset policy shared with the desktop. Areas are labels, not entities. */
data class SyncScope(
    val areas: List<String> = emptyList(),
    val includeDueReviews: Boolean = false,
    val pinnedNodeIds: List<String> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("areas", JSONArray(areas))
        .put("includeDueReviews", includeDueReviews)
        .put("pinnedNodeIds", JSONArray(pinnedNodeIds))

    /** Cursor is only valid for one scope; changing the fingerprint re-baselines. */
    fun fingerprint(): String = sha256(toJson().toString())

    fun includesArea(area: String?): Boolean = area != null && area in areas
}

data class SyncChange(
    val type: String,
    val id: String,
    val revision: Long?,
    val hash: String?,
    val tombstone: Boolean,
    val area: String?
)

data class SyncManifest(
    val reset: Boolean,
    val protocolVersion: Int,
    val serverId: String,
    val cursor: Long,
    val hasMore: Boolean,
    val changes: List<SyncChange>
)

data class SyncHealth(
    val protocolVersion: Int,
    val serverId: String,
    val pairedDevices: Int
)

sealed interface SyncRecord {
    val type: String
    val id: String

    data class Node(
        override val id: String,
        val title: String,
        val area: String,
        val track: String,
        val summary: String,
        val body: String,
        val visibility: String,
        val revision: Long,
        val updatedAt: String,
        val hash: String
    ) : SyncRecord {
        override val type: String get() = TYPE
        companion object { const val TYPE = "node" }
    }

    data class Quiz(
        override val id: String,
        val title: String,
        val area: String,
        val difficulty: String,
        val summary: String,
        val body: String,
        val visibility: String,
        val revision: Long,
        val updatedAt: String,
        val hash: String
    ) : SyncRecord {
        override val type: String get() = TYPE
        companion object { const val TYPE = "quiz" }
    }

    data class ReviewAttempt(
        override val id: String,
        val quizId: String,
        val grade: String,
        val answeredAt: String,
        val elapsedMs: Long,
        val note: String
    ) : SyncRecord {
        override val type: String get() = TYPE
        companion object { const val TYPE = "review_attempt" }
    }

    data class ReaderQuestion(
        override val id: String,
        val targetType: String,
        val targetId: String,
        val question: String,
        val status: String,
        val createdAt: String,
        val resolvedAt: String,
        val resolutionNote: String
    ) : SyncRecord {
        override val type: String get() = TYPE
        companion object { const val TYPE = "reader_question" }
    }

    data class CaptureSlip(
        override val id: String,
        val body: String,
        val slipType: String,
        val topicHint: String,
        val sourceLabel: String,
        val status: String,
        val revision: Long,
        val createdAt: String,
        val updatedAt: String
    ) : SyncRecord {
        override val type: String get() = TYPE
        companion object { const val TYPE = "capture_slip" }
    }
}

data class SyncReport(
    val pulledNodes: Int = 0,
    val pulledQuizzes: Int = 0,
    val pulledQuestions: Int = 0,
    val pulledCaptureSlips: Int = 0,
    val appliedAttempts: Int = 0,
    val skippedAttempts: Int = 0,
    val skippedRecords: Int = 0,
    val removed: Int = 0,
    val conflicts: Int = 0,
    val cursor: Long = 0,
    val serverId: String = ""
) {
    val totalApplied: Int get() = pulledNodes + pulledQuizzes + pulledQuestions + pulledCaptureSlips
}

data class SyncReceipt(
    val id: String,
    val status: String,
    val reason: String?,
    val revision: Long? = null
) {
    val accepted: Boolean get() = status == STATUS_ACCEPTED || status == STATUS_DUPLICATE

    companion object {
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_DUPLICATE = "duplicate"
        const val STATUS_REJECTED = "rejected"
    }
}

data class SyncPushReport(
    val uploadedAttempts: Int = 0,
    val uploadedCaptures: Int = 0,
    val uploadedQuestions: Int = 0,
    val uploadedNodes: Int = 0,
    val uploadedQuizzes: Int = 0,
    val rejected: Int = 0
) {
    val totalUploaded: Int get() = uploadedAttempts + uploadedCaptures + uploadedQuestions + uploadedNodes + uploadedQuizzes
}

fun parseSyncReceipts(json: JSONObject): List<SyncReceipt> {
    val receipts = json.optJSONArray("receipts") ?: return emptyList()
    return buildList {
        for (index in 0 until receipts.length()) {
            val item = receipts.getJSONObject(index)
            add(
                SyncReceipt(
                    id = item.getString("id"),
                    status = item.getString("status"),
                    reason = item.optString("reason").takeIf { it.isNotBlank() },
                    revision = if (item.isNull("revision")) null else item.optLong("revision")
                )
            )
        }
    }
}

fun parseSyncHealth(json: JSONObject): SyncHealth =
    SyncHealth(
        protocolVersion = json.getInt("protocolVersion"),
        serverId = json.getString("serverId"),
        pairedDevices = json.optInt("pairedDevices", 0)
    )

fun parseSyncManifest(json: JSONObject): SyncManifest {
    val changesJson = json.optJSONArray("changes") ?: JSONArray()
    val changes = buildList {
        for (index in 0 until changesJson.length()) {
            val item = changesJson.getJSONObject(index)
            add(
                SyncChange(
                    type = item.getString("type"),
                    id = item.getString("id"),
                    revision = if (item.isNull("revision")) null else item.getLong("revision"),
                    hash = item.optString("hash").takeIf { it.isNotBlank() },
                    tombstone = item.optBoolean("tombstone", false),
                    area = item.optString("area").takeIf { it.isNotBlank() }
                )
            )
        }
    }
    return SyncManifest(
        reset = json.optBoolean("reset", false),
        protocolVersion = json.getInt("protocolVersion"),
        serverId = json.getString("serverId"),
        cursor = json.getLong("cursor"),
        hasMore = json.optBoolean("hasMore", false),
        changes = changes
    )
}

fun parseSyncRecord(json: JSONObject): SyncRecord? = when (json.optString("type")) {
    SyncRecord.Node.TYPE -> SyncRecord.Node(
        id = json.getString("id"),
        title = json.optString("title"),
        area = json.optString("area"),
        track = json.optString("track", "general"),
        summary = json.optString("summary"),
        body = json.getString("body"),
        visibility = json.optString("visibility", "support"),
        revision = json.optLong("revision"),
        updatedAt = json.optString("updatedAt"),
        hash = json.optString("hash")
    )

    SyncRecord.Quiz.TYPE -> SyncRecord.Quiz(
        id = json.getString("id"),
        title = json.optString("title"),
        area = json.optString("area"),
        difficulty = json.optString("difficulty", "medium"),
        summary = json.optString("summary"),
        body = json.getString("body"),
        visibility = json.optString("visibility", "practice"),
        revision = json.optLong("revision"),
        updatedAt = json.optString("updatedAt"),
        hash = json.optString("hash")
    )

    SyncRecord.ReviewAttempt.TYPE -> SyncRecord.ReviewAttempt(
        id = json.getString("id"),
        quizId = json.getString("quizId"),
        grade = json.getString("grade"),
        answeredAt = json.optString("answeredAt"),
        elapsedMs = json.optLong("elapsedMs"),
        note = json.optString("note")
    )

    SyncRecord.ReaderQuestion.TYPE -> SyncRecord.ReaderQuestion(
        id = json.getString("id"),
        targetType = json.optString("targetType"),
        targetId = json.optString("targetId"),
        question = json.getString("question"),
        status = json.optString("status", "open"),
        createdAt = json.optString("createdAt"),
        resolvedAt = json.optString("resolvedAt"),
        resolutionNote = json.optString("resolutionNote")
    )

    SyncRecord.CaptureSlip.TYPE -> SyncRecord.CaptureSlip(
        id = json.getString("id"),
        body = json.getString("body"),
        slipType = json.optString("slipType", "concept_seed"),
        topicHint = json.optString("topicHint"),
        sourceLabel = json.optString("sourceLabel"),
        status = json.optString("status", "inbox"),
        revision = json.optLong("revision"),
        createdAt = json.optString("createdAt"),
        updatedAt = json.optString("updatedAt")
    )

    else -> null
}

internal fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
