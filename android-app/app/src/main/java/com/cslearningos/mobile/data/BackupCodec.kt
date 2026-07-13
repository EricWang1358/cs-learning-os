package com.cslearningos.mobile.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque

object BackupCodec {
    const val SchemaVersion = 1
    const val MaxRecordsPerCollection = 2_000
    const val MaxMarkdownBodyCharacters = 1_000_000
    const val MaxContentStringCharacters = 100_000
    const val MaxMetadataStringCharacters = 4_096

    /** Bounds raw JSON before org.json allocates a deeply nested object graph. */
    const val MaxJsonNesting = 64

    fun encode(backup: LearningBackup): String =
        JSONObject()
            .put("schemaVersion", backup.schemaVersion)
            .put("exportedAt", backup.exportedAt)
            .put("areas", JSONArray(backup.areas.map { it.toJson() }))
            .put("nodes", JSONArray(backup.nodes.map { it.toJson() }))
            .put("quizzes", JSONArray(backup.quizzes.map { it.toJson() }))
            .put("reviewStates", JSONArray(backup.reviewStates.map { it.toJson() }))
            .put("attempts", JSONArray(backup.attempts.map { it.toJson() }))
            .put("readerQuestions", JSONArray(backup.readerQuestions.map { it.toJson() }))
            .put("captureSlips", JSONArray(backup.captureSlips.map { it.toJson() }))
            .toString(2)

    fun decode(rawJson: String): LearningBackup {
        validateRawJsonStructure(rawJson)
        val root = JSONObject(rawJson)
        val schemaVersion = root.getInt("schemaVersion")
        require(schemaVersion == SchemaVersion) {
            "Unsupported backup schema version: $schemaVersion"
        }
        val nodesJson = root.requiredArray("nodes").also { it.validateNodeStrings() }
        val areasJson = root.optionalArray("areas")
        val quizzesJson = root.requiredArray("quizzes")
        val reviewStatesJson = root.requiredArray("reviewStates")
        val attemptsJson = root.requiredArray("attempts")
        val readerQuestionsJson = root.optionalArray("readerQuestions")
        val captureSlipsJson = root.optionalArray("captureSlips")

        areasJson?.validateAreaStrings()
        quizzesJson.validateQuizStrings()
        reviewStatesJson.validateReviewStateStrings()
        attemptsJson.validateAttemptStrings()
        readerQuestionsJson?.validateReaderQuestionStrings()
        captureSlipsJson?.validateCaptureSlipStrings()

        val nodes = nodesJson.mapObjects { it.toNode() }
        val areas = areasJson?.mapObjects { it.toArea() }.orEmpty()
        val inferredAreas = if (areas.isEmpty()) inferAreasFromNodes(nodes) else areas
        return LearningBackup(
            schemaVersion = schemaVersion,
            exportedAt = root.getLong("exportedAt"),
            areas = inferredAreas,
            nodes = nodes,
            quizzes = quizzesJson.mapObjects { it.toQuiz() },
            reviewStates = reviewStatesJson.mapObjects { it.toReviewState() },
            attempts = attemptsJson.mapObjects { it.toAttempt() },
            readerQuestions = readerQuestionsJson?.mapObjects { it.toReaderQuestion() }.orEmpty(),
            captureSlips = captureSlipsJson?.mapObjects { it.toCaptureSlip() }.orEmpty()
        )
    }

    private fun AreaEntity.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("slug", slug)
            .put("name", name)
            .put("order", order)
            .put("createdAt", createdAt)
            .put("updatedAt", updatedAt)
            .put("deletedAt", deletedAt)

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
            .put("area", area)
            .put("areaId", areaId)
            .put("track", track)
            .put("order", order)
            .put("summary", summary)
            .put("visibility", visibility)
            .put("isStarter", isStarter)
            .put("isChecked", isChecked)

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
            .put("area", area)
            .put("track", track)
            .put("visibility", visibility)
            .put("isStarter", isStarter)

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

    private fun ReaderQuestionEntity.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("nodeId", nodeId)
            .put("body", body)
            .put("createdAt", createdAt)
            .put("resolvedAt", resolvedAt)
            .put("syncStatus", syncStatus.name)
            .put("deletedAt", deletedAt)

    private fun CaptureSlipEntity.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("body", body)
            .put("type", type.name)
            .put("topicHint", topicHint)
            .put("sourceLabel", sourceLabel)
            .put("linkedNodeId", linkedNodeId)
            .put("status", status.name)
            .put("createdAt", createdAt)
            .put("updatedAt", updatedAt)
            .put("revision", revision)
            .put("syncStatus", syncStatus.name)
            .put("deletedAt", deletedAt)

    private fun JSONObject.toArea(): AreaEntity =
        AreaEntity(
            id = getString("id"),
            slug = optString("slug", getString("id")),
            name = optString("name", optString("slug", getString("id"))),
            order = optInt("order", 1000),
            createdAt = optLong("createdAt", 0L),
            updatedAt = optLong("updatedAt", optLong("createdAt", 0L)),
            deletedAt = nullableLong("deletedAt")
        )

    private fun JSONObject.toNode(): LearningNodeEntity =
        LearningNodeEntity(
            id = getString("id"),
            title = getString("title"),
            markdownBody = getString("markdownBody").also(::validateMarkdownBody),
            createdAt = getLong("createdAt"),
            updatedAt = getLong("updatedAt"),
            lastReadAt = nullableLong("lastReadAt"),
            revision = getLong("revision"),
            syncStatus = SyncStatus.valueOf(getString("syncStatus")),
            deletedAt = nullableLong("deletedAt"),
            area = optString("area", "questions"),
            areaId = optString("areaId", optString("area", "questions")),
            track = optString("track", "general"),
            order = optInt("order", 1000),
            summary = optString("summary", ""),
            visibility = optString("visibility", "support"),
            isStarter = optBoolean("isStarter", false),
            isChecked = optBoolean("isChecked", false)
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
            deletedAt = nullableLong("deletedAt"),
            area = optString("area", "questions"),
            track = optString("track", "general"),
            visibility = optString("visibility", "practice"),
            isStarter = optBoolean("isStarter", false)
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

    private fun JSONObject.toReaderQuestion(): ReaderQuestionEntity =
        ReaderQuestionEntity(
            id = getString("id"),
            nodeId = getString("nodeId"),
            body = getString("body"),
            createdAt = getLong("createdAt"),
            resolvedAt = nullableLong("resolvedAt"),
            syncStatus = SyncStatus.valueOf(getString("syncStatus")),
            deletedAt = nullableLong("deletedAt")
        )

    private fun JSONObject.toCaptureSlip(): CaptureSlipEntity =
        CaptureSlipEntity(
            id = getString("id"),
            body = getString("body"),
            type = CaptureSlipType.valueOf(getString("type")),
            topicHint = nullableString("topicHint"),
            sourceLabel = nullableString("sourceLabel"),
            linkedNodeId = nullableString("linkedNodeId"),
            status = CaptureSlipStatus.valueOf(getString("status")),
            createdAt = getLong("createdAt"),
            updatedAt = getLong("updatedAt"),
            revision = getLong("revision"),
            syncStatus = SyncStatus.valueOf(getString("syncStatus")),
            deletedAt = nullableLong("deletedAt")
        )

    private fun JSONObject.nullableLong(name: String): Long? =
        if (isNull(name)) null else getLong(name)

    private fun JSONObject.requiredArray(name: String): JSONArray =
        getJSONArray(name).also { it.validateRecordCount(name) }

    private fun JSONObject.optionalArray(name: String): JSONArray? =
        optJSONArray(name)?.also { it.validateRecordCount(name) }

    private fun JSONArray.validateRecordCount(name: String) {
        require(length() <= MaxRecordsPerCollection) {
            "Backup $name collection exceeds $MaxRecordsPerCollection records."
        }
    }

    private fun JSONArray.validateNodeStrings() = validateObjects { node ->
        node.validateRequiredString("id")
        node.validateRequiredString("title")
        node.validateRequiredString("markdownBody", MaxMarkdownBodyCharacters)
        node.validateOptionalString("area")
        node.validateOptionalString("areaId")
        node.validateOptionalString("track")
        node.validateOptionalString("summary", MaxContentStringCharacters)
        node.validateOptionalString("visibility")
        node.validateRequiredString("syncStatus")
    }

    private fun JSONArray.validateAreaStrings() = validateObjects { area ->
        area.validateRequiredString("id")
        area.validateOptionalString("slug")
        area.validateOptionalString("name")
    }

    private fun JSONArray.validateQuizStrings() = validateObjects { quiz ->
        quiz.validateRequiredString("id")
        quiz.validateNullableString("nodeId")
        quiz.validateRequiredString("prompt", MaxContentStringCharacters)
        quiz.validateRequiredString("answer", MaxContentStringCharacters)
        quiz.validateRequiredString("explanation", MaxContentStringCharacters)
        quiz.validateRequiredString("source")
        quiz.validateNullableString("sourceAnchor")
        quiz.validateRequiredString("syncStatus")
        quiz.validateOptionalString("area")
        quiz.validateOptionalString("track")
        quiz.validateOptionalString("visibility")
    }

    private fun JSONArray.validateReviewStateStrings() = validateObjects { state ->
        state.validateRequiredString("quizId")
        state.validateRequiredString("lastResult")
    }

    private fun JSONArray.validateAttemptStrings() = validateObjects { attempt ->
        attempt.validateRequiredString("id")
        attempt.validateRequiredString("quizId")
        attempt.validateRequiredString("result")
    }

    private fun JSONArray.validateReaderQuestionStrings() = validateObjects { question ->
        question.validateRequiredString("id")
        question.validateRequiredString("nodeId")
        question.validateRequiredString("body", MaxContentStringCharacters)
        question.validateRequiredString("syncStatus")
    }

    private fun JSONArray.validateCaptureSlipStrings() = validateObjects { slip ->
        slip.validateRequiredString("id")
        slip.validateRequiredString("body", MaxContentStringCharacters)
        slip.validateRequiredString("type")
        slip.validateNullableString("topicHint", MaxContentStringCharacters)
        slip.validateNullableString("sourceLabel")
        slip.validateNullableString("linkedNodeId")
        slip.validateRequiredString("status")
        slip.validateRequiredString("syncStatus")
    }

    private fun JSONArray.validateObjects(validator: (JSONObject) -> Unit) {
        for (index in 0 until length()) {
            validator(getJSONObject(index))
        }
    }

    private fun JSONObject.validateRequiredString(name: String, limit: Int = MaxMetadataStringCharacters) {
        validateStringLength(name, getString(name), limit)
    }

    private fun JSONObject.validateOptionalString(name: String, limit: Int = MaxMetadataStringCharacters) {
        if (has(name) && !isNull(name)) validateStringLength(name, getString(name), limit)
    }

    private fun JSONObject.validateNullableString(name: String, limit: Int = MaxMetadataStringCharacters) {
        if (!isNull(name)) validateStringLength(name, getString(name), limit)
    }

    private fun validateStringLength(name: String, value: String, limit: Int) {
        require(value.length <= limit) {
            "Backup $name exceeds $limit characters."
        }
    }

    private fun validateMarkdownBody(markdownBody: String) {
        validateStringLength("markdownBody", markdownBody, MaxMarkdownBodyCharacters)
    }

    private fun validateRawJsonStructure(rawJson: String) {
        val openings = ArrayDeque<Char>()
        var inString = false
        var escaping = false

        rawJson.forEach { character ->
            if (inString) {
                when {
                    escaping -> escaping = false
                    character == '\\' -> escaping = true
                    character == '"' -> inString = false
                }
            } else {
                when (character) {
                    '"' -> inString = true
                    '{', '[' -> {
                        openings.addLast(character)
                        require(openings.size <= MaxJsonNesting) {
                            "Backup JSON nesting exceeds $MaxJsonNesting levels."
                        }
                    }

                    '}', ']' -> {
                        require(openings.isNotEmpty() && matches(openings.removeLast(), character)) {
                            "Backup JSON contains an unmatched closure."
                        }
                    }
                }
            }
        }

        require(!inString) { "Backup JSON contains an unterminated string." }
        require(openings.isEmpty()) { "Backup JSON contains unmatched openings." }
    }

    private fun matches(opening: Char, closing: Char): Boolean =
        (opening == '{' && closing == '}') || (opening == '[' && closing == ']')

    private fun JSONObject.nullableString(name: String): String? =
        if (isNull(name)) null else getString(name)

    private fun <T> JSONArray.mapObjects(mapper: (JSONObject) -> T): List<T> =
        (0 until length()).map { index -> mapper(getJSONObject(index)) }

    private fun inferAreasFromNodes(nodes: List<LearningNodeEntity>): List<AreaEntity> =
        nodes
            .map { it.areaId.ifBlank { it.area } to it.area }
            .distinctBy { it.first }
            .mapIndexed { index, (areaId, slug) ->
                AreaEntity(
                    id = areaId,
                    slug = slug,
                    name = slug,
                    order = (index + 1) * 10,
                    createdAt = 0L,
                    updatedAt = 0L,
                    deletedAt = null
                )
            }
}
