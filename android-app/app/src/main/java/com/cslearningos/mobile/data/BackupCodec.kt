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
    /** Bounds every raw JSON object and array, including unknown payload containers. */
    const val MaxJsonContainerEntries = MaxRecordsPerCollection
    /** Allows every supported collection at its record limit while bounding unknown payloads globally. */
    const val MaxJsonValues = 200_000

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
        val containers = ArrayDeque<RawJsonContainer>()
        var index = 0
        var rootValueSeen = false
        var valueCount = 0

        fun countValue() {
            valueCount += 1
            require(valueCount <= MaxJsonValues) {
                "Backup JSON exceeds $MaxJsonValues values."
            }
        }

        fun countContainerEntry(container: RawJsonContainer) {
            container.entryCount += 1
            require(container.entryCount <= MaxJsonContainerEntries) {
                "Backup JSON ${if (container.opening == '[') "array" else "object"} exceeds " +
                    "$MaxJsonContainerEntries entries."
            }
        }

        fun startValue(): Int {
            countValue()
            return when (rawJson[index]) {
                '{', '[' -> {
                    containers.addLast(RawJsonContainer(rawJson[index]))
                    require(containers.size <= MaxJsonNesting) {
                        "Backup JSON nesting exceeds $MaxJsonNesting levels."
                    }
                    index + 1
                }

                '"' -> consumeJsonString(rawJson, index)
                '}', ']', ',', ':' -> throw IllegalArgumentException("Backup JSON contains an unmatched closure.")
                else -> consumeRawJsonScalar(rawJson, index)
            }
        }

        while (index < rawJson.length) {
            if (rawJson[index].isWhitespace()) {
                index += 1
                continue
            }

            if (containers.isEmpty()) {
                require(!rootValueSeen) { "Backup JSON contains trailing content." }
                rootValueSeen = true
                index = startValue()
                continue
            }

            val container = containers.last()
            if (container.opening == '[') {
                when (container.state) {
                    ARRAY_EXPECT_VALUE_OR_END -> {
                        if (rawJson[index] == ']') {
                            containers.removeLast()
                            index += 1
                        } else {
                            countContainerEntry(container)
                            container.state = ARRAY_EXPECT_COMMA_OR_END
                            index = startValue()
                        }
                    }

                    ARRAY_EXPECT_COMMA_OR_END -> when (rawJson[index]) {
                        ',' -> {
                            container.state = ARRAY_EXPECT_VALUE_OR_END
                            index += 1
                        }

                        ']' -> {
                            containers.removeLast()
                            index += 1
                        }

                        else -> throw IllegalArgumentException("Backup JSON array is missing a separator.")
                    }
                }
            } else {
                when (container.state) {
                    OBJECT_EXPECT_KEY_OR_END -> {
                        if (rawJson[index] == '}') {
                            containers.removeLast()
                            index += 1
                        } else {
                            require(rawJson[index] == '"') { "Backup JSON object key must be a string." }
                            countContainerEntry(container)
                            container.state = OBJECT_EXPECT_COLON
                            index = consumeJsonString(rawJson, index)
                        }
                    }

                    OBJECT_EXPECT_COLON -> {
                        require(rawJson[index] == ':') { "Backup JSON object key is missing a value." }
                        container.state = OBJECT_EXPECT_VALUE
                        index += 1
                    }

                    OBJECT_EXPECT_VALUE -> {
                        container.state = OBJECT_EXPECT_COMMA_OR_END
                        index = startValue()
                    }

                    OBJECT_EXPECT_COMMA_OR_END -> when (rawJson[index]) {
                        ',' -> {
                            container.state = OBJECT_EXPECT_KEY_OR_END
                            index += 1
                        }

                        '}' -> {
                            containers.removeLast()
                            index += 1
                        }

                        else -> throw IllegalArgumentException("Backup JSON object is missing a separator.")
                    }
                }
            }
        }

        require(rootValueSeen) { "Backup JSON is empty." }
        require(containers.isEmpty()) { "Backup JSON contains unmatched openings." }
    }

    private fun consumeJsonString(rawJson: String, startIndex: Int): Int {
        var index = startIndex + 1
        while (index < rawJson.length) {
            when (rawJson[index]) {
                '"' -> return index + 1
                '\\' -> {
                    index += 1
                    require(index < rawJson.length) { "Backup JSON contains an unterminated string." }
                    if (rawJson[index] == 'u') {
                        require(index + 4 < rawJson.length && (index + 1..index + 4).all { rawJson[it].isDigit() || rawJson[it].lowercaseChar() in 'a'..'f' }) {
                            "Backup JSON contains an invalid string escape."
                        }
                        index += 4
                    } else {
                        require(rawJson[index] in "\\\"/bfnrt") { "Backup JSON contains an invalid string escape." }
                    }
                }
            }
            index += 1
        }
        throw IllegalArgumentException("Backup JSON contains an unterminated string.")
    }

    private fun consumeRawJsonScalar(rawJson: String, startIndex: Int): Int {
        var index = startIndex
        while (index < rawJson.length && rawJson[index] !in RAW_JSON_VALUE_DELIMITERS) {
            index += 1
        }
        require(index > startIndex) { "Backup JSON contains an invalid value." }
        return index
    }

    private data class RawJsonContainer(
        val opening: Char,
        var state: Int = ARRAY_EXPECT_VALUE_OR_END,
        var entryCount: Int = 0
    )

    private const val ARRAY_EXPECT_VALUE_OR_END = 0
    private const val ARRAY_EXPECT_COMMA_OR_END = 1
    private const val OBJECT_EXPECT_KEY_OR_END = 0
    private const val OBJECT_EXPECT_COLON = 1
    private const val OBJECT_EXPECT_VALUE = 2
    private const val OBJECT_EXPECT_COMMA_OR_END = 3
    private const val RAW_JSON_VALUE_DELIMITERS = " \t\r\n,]}"

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
