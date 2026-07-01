package com.cslearningos.mobile.data

import org.json.JSONArray
import org.json.JSONObject

object BackupCodec {
    const val SchemaVersion = 1

    fun encode(backup: LearningBackup): String =
        JSONObject()
            .put("schemaVersion", backup.schemaVersion)
            .put("exportedAt", backup.exportedAt)
            .put("nodes", JSONArray(backup.nodes.map { it.toJson() }))
            .put("quizzes", JSONArray(backup.quizzes.map { it.toJson() }))
            .put("reviewStates", JSONArray(backup.reviewStates.map { it.toJson() }))
            .put("attempts", JSONArray(backup.attempts.map { it.toJson() }))
            .toString(2)

    fun decode(rawJson: String): LearningBackup {
        val root = JSONObject(rawJson)
        val schemaVersion = root.getInt("schemaVersion")
        require(schemaVersion == SchemaVersion) {
            "Unsupported backup schema version: $schemaVersion"
        }
        return LearningBackup(
            schemaVersion = schemaVersion,
            exportedAt = root.getLong("exportedAt"),
            nodes = root.getJSONArray("nodes").mapObjects { it.toNode() },
            quizzes = root.getJSONArray("quizzes").mapObjects { it.toQuiz() },
            reviewStates = root.getJSONArray("reviewStates").mapObjects { it.toReviewState() },
            attempts = root.getJSONArray("attempts").mapObjects { it.toAttempt() }
        )
    }

    private fun LearningNodeEntity.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("title", title)
            .put("markdownBody", markdownBody)
            .put("createdAt", createdAt)
            .put("updatedAt", updatedAt)
            .put("lastReadAt", lastReadAt)
            .put("revision", revision)
            .put("syncStatus", syncStatus.name)
            .put("deletedAt", deletedAt)

    private fun QuizItemEntity.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("nodeId", nodeId)
            .put("prompt", prompt)
            .put("answer", answer)
            .put("explanation", explanation)
            .put("source", source.name)
            .put("sourceAnchor", sourceAnchor)
            .put("createdAt", createdAt)
            .put("updatedAt", updatedAt)
            .put("revision", revision)
            .put("syncStatus", syncStatus.name)
            .put("deletedAt", deletedAt)

    private fun ReviewStateEntity.toJson(): JSONObject =
        JSONObject()
            .put("quizId", quizId)
            .put("ease", ease)
            .put("intervalDays", intervalDays)
            .put("dueAt", dueAt)
            .put("lastResult", lastResult.name)
            .put("attemptCount", attemptCount)
            .put("updatedAt", updatedAt)

    private fun ReviewAttemptEntity.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("quizId", quizId)
            .put("result", result.name)
            .put("answeredAt", answeredAt)
            .put("scheduledDueAt", scheduledDueAt)

    private fun JSONObject.toNode(): LearningNodeEntity =
        LearningNodeEntity(
            id = getString("id"),
            title = getString("title"),
            markdownBody = getString("markdownBody"),
            createdAt = getLong("createdAt"),
            updatedAt = getLong("updatedAt"),
            lastReadAt = nullableLong("lastReadAt"),
            revision = getLong("revision"),
            syncStatus = SyncStatus.valueOf(getString("syncStatus")),
            deletedAt = nullableLong("deletedAt")
        )

    private fun JSONObject.toQuiz(): QuizItemEntity =
        QuizItemEntity(
            id = getString("id"),
            nodeId = nullableString("nodeId"),
            prompt = getString("prompt"),
            answer = getString("answer"),
            explanation = getString("explanation"),
            source = QuizSource.valueOf(getString("source")),
            sourceAnchor = nullableString("sourceAnchor"),
            createdAt = getLong("createdAt"),
            updatedAt = getLong("updatedAt"),
            revision = getLong("revision"),
            syncStatus = SyncStatus.valueOf(getString("syncStatus")),
            deletedAt = nullableLong("deletedAt")
        )

    private fun JSONObject.toReviewState(): ReviewStateEntity =
        ReviewStateEntity(
            quizId = getString("quizId"),
            ease = getDouble("ease"),
            intervalDays = getInt("intervalDays"),
            dueAt = getLong("dueAt"),
            lastResult = ReviewResult.valueOf(getString("lastResult")),
            attemptCount = getInt("attemptCount"),
            updatedAt = getLong("updatedAt")
        )

    private fun JSONObject.toAttempt(): ReviewAttemptEntity =
        ReviewAttemptEntity(
            id = getString("id"),
            quizId = getString("quizId"),
            result = ReviewResult.valueOf(getString("result")),
            answeredAt = getLong("answeredAt"),
            scheduledDueAt = getLong("scheduledDueAt")
        )

    private fun JSONObject.nullableLong(name: String): Long? =
        if (isNull(name)) null else getLong(name)

    private fun JSONObject.nullableString(name: String): String? =
        if (isNull(name)) null else getString(name)

    private fun <T> JSONArray.mapObjects(mapper: (JSONObject) -> T): List<T> =
        (0 until length()).map { index -> mapper(getJSONObject(index)) }
}
