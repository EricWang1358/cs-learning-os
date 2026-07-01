package com.cslearningos.mobile.data

import com.cslearningos.mobile.domain.MarkdownQuizParser
import com.cslearningos.mobile.domain.ReviewRating
import com.cslearningos.mobile.domain.ReviewScheduleInput
import com.cslearningos.mobile.domain.ReviewScheduler
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import kotlin.math.absoluteValue

class LearningRepository(private val dao: LearningDao) {
    val nodes: Flow<List<LearningNodeEntity>> = dao.observeNodes()
    val trashNodes: Flow<List<LearningNodeEntity>> = dao.observeTrashNodes()
    val quizzes: Flow<List<QuizItemEntity>> = dao.observeQuizzes()
    val openReaderQuestions: Flow<List<ReaderQuestionEntity>> = dao.observeOpenReaderQuestions()
    val inboxCaptureSlips: Flow<List<CaptureSlipEntity>> = dao.observeInboxCaptureSlips()

    fun dueQuizzes(now: Long): Flow<List<QuizItemEntity>> = dao.observeDueQuizzes(now)

    suspend fun getNode(id: String): LearningNodeEntity? = dao.getNode(id)

    suspend fun getQuiz(id: String): QuizItemEntity? = dao.getQuiz(id)

    suspend fun saveNode(
        id: String?,
        title: String,
        markdownBody: String,
        now: Long = System.currentTimeMillis()
    ): LearningNodeEntity {
        val existing = id?.let { dao.getNode(it) }
        val node = LearningNodeEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            title = title.ifBlank { "Untitled" },
            markdownBody = markdownBody,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            lastReadAt = existing?.lastReadAt,
            revision = (existing?.revision ?: 0L) + 1L,
            syncStatus = SyncStatus.dirty,
            deletedAt = null,
            area = existing?.area ?: "questions",
            track = existing?.track ?: "general",
            order = existing?.order ?: 1000,
            summary = existing?.summary ?: markdownBody.lineSequence()
                .firstOrNull { it.isNotBlank() && !it.trim().startsWith("#") }
                ?.trim()
                .orEmpty(),
            visibility = existing?.visibility ?: "support",
            isStarter = existing?.isStarter ?: false
        )
        dao.upsertNode(node)
        indexNode(node)
        syncMarkdownQuizzes(node, now)
        return node
    }

    suspend fun markRead(nodeId: String, now: Long = System.currentTimeMillis()) {
        val node = dao.getNode(nodeId) ?: return
        if (node.deletedAt != null) return
        val updated = node.withReadTrace(now)
        dao.upsertNode(updated)
        indexNode(updated)
    }

    suspend fun moveNodeToTrash(nodeId: String, now: Long = System.currentTimeMillis()) {
        val node = dao.getNode(nodeId) ?: return
        dao.upsertNode(
            node.copy(
                updatedAt = now,
                revision = node.revision + 1,
                syncStatus = SyncStatus.dirty,
                visibility = "trash"
            )
        )
        dao.deleteNodeFts(node.id)
        dao.getActiveQuizzesForNode(nodeId).forEach { quiz ->
            dao.upsertQuiz(
                quiz.copy(
                    updatedAt = now,
                    revision = quiz.revision + 1,
                    syncStatus = SyncStatus.dirty,
                    visibility = "trash"
                )
            )
            dao.deleteQuizFts(quiz.id)
        }
    }

    suspend fun restoreNodeFromTrash(nodeId: String, now: Long = System.currentTimeMillis()) {
        val node = dao.getNode(nodeId) ?: return
        if (node.deletedAt != null) return
        val restored = node.copy(
            updatedAt = now,
            revision = node.revision + 1,
            syncStatus = SyncStatus.dirty,
            visibility = if (node.isStarter) "core" else "support"
        )
        dao.upsertNode(restored)
        indexNode(restored)
        dao.getActiveQuizzesForNode(nodeId).forEach { quiz ->
            val restoredQuiz = quiz.copy(
                updatedAt = now,
                revision = quiz.revision + 1,
                syncStatus = SyncStatus.dirty,
                visibility = "practice"
            )
            dao.upsertQuiz(restoredQuiz)
            indexQuiz(restoredQuiz)
        }
    }

    suspend fun permanentlyDeleteNode(nodeId: String, now: Long = System.currentTimeMillis()) {
        val node = dao.getNode(nodeId) ?: return
        val deletedNode = node.copy(
            updatedAt = now,
            revision = node.revision + 1,
            syncStatus = SyncStatus.deleted,
            deletedAt = now
        )
        dao.upsertNode(deletedNode)
        dao.deleteNodeFts(node.id)

        dao.getActiveQuizzesForNode(node.id).forEach { quiz ->
            dao.upsertQuiz(
                quiz.copy(
                    updatedAt = now,
                    revision = quiz.revision + 1,
                    syncStatus = SyncStatus.deleted,
                    deletedAt = now
                )
            )
            dao.deleteQuizFts(quiz.id)
        }

        dao.getActiveReaderQuestionsForNode(node.id).forEach { question ->
            dao.upsertReaderQuestion(
                question.copy(
                    syncStatus = SyncStatus.deleted,
                    deletedAt = now
                )
            )
        }
    }

    suspend fun clearStarterContent(now: Long = System.currentTimeMillis()) {
        dao.getStarterNodes().forEach { node ->
            dao.upsertNode(
                node.copy(
                    updatedAt = now,
                    revision = node.revision + 1,
                    syncStatus = SyncStatus.deleted,
                    deletedAt = now
                )
            )
            dao.deleteNodeFts(node.id)
        }
        dao.getStarterQuizzes().forEach { quiz ->
            dao.upsertQuiz(
                quiz.copy(
                    updatedAt = now,
                    revision = quiz.revision + 1,
                    syncStatus = SyncStatus.deleted,
                    deletedAt = now
                )
            )
            dao.deleteQuizFts(quiz.id)
        }
    }

    suspend fun saveReaderQuestion(
        nodeId: String,
        body: String,
        now: Long = System.currentTimeMillis()
    ): ReaderQuestionEntity {
        val question = ReaderQuestionEntity(
            id = UUID.randomUUID().toString(),
            nodeId = nodeId,
            body = body.trim(),
            createdAt = now,
            resolvedAt = null,
            syncStatus = SyncStatus.dirty,
            deletedAt = null
        )
        dao.upsertReaderQuestion(question)
        return question
    }

    suspend fun resolveReaderQuestion(questionId: String, now: Long = System.currentTimeMillis()) {
        val question = dao.getReaderQuestion(questionId) ?: return
        dao.upsertReaderQuestion(
            question.copy(
                resolvedAt = now,
                syncStatus = SyncStatus.dirty
            )
        )
    }

    suspend fun saveCaptureSlip(
        body: String,
        type: CaptureSlipType,
        topicHint: String?,
        sourceLabel: String?,
        status: CaptureSlipStatus = CaptureSlipStatus.inbox,
        now: Long = System.currentTimeMillis()
    ): CaptureSlipEntity {
        val slip = CaptureSlipEntity(
            id = UUID.randomUUID().toString(),
            body = body.trim(),
            type = type,
            topicHint = topicHint?.trim()?.ifBlank { null },
            sourceLabel = sourceLabel?.trim()?.ifBlank { null },
            linkedNodeId = null,
            status = status,
            createdAt = now,
            updatedAt = now,
            revision = 1L,
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
            revision = slip.revision + 1,
            syncStatus = SyncStatus.dirty
        )
        dao.upsertCaptureSlip(updated)
        return updated
    }

    suspend fun archiveCaptureSlip(slipId: String, now: Long = System.currentTimeMillis()) {
        val slip = dao.getCaptureSlip(slipId) ?: return
        dao.upsertCaptureSlip(
            slip.copy(
                status = CaptureSlipStatus.archived,
                updatedAt = now,
                revision = slip.revision + 1,
                syncStatus = SyncStatus.dirty
            )
        )
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
                revision = slip.revision + 1,
                syncStatus = SyncStatus.dirty
            )
        )
    }

    suspend fun seedStarterContent(pack: StarterContentPackage) {
        pack.nodes.forEach { node ->
            if (dao.getNode(node.id) == null) {
                dao.upsertNode(node)
                indexNode(node)
            }
        }
        pack.quizzes.forEach { quiz ->
            if (dao.getQuiz(quiz.id) == null) {
                dao.upsertQuiz(quiz)
                pack.reviewStates.firstOrNull { it.quizId == quiz.id }?.let { dao.upsertReviewState(it) }
                indexQuiz(quiz)
            }
        }
    }

    suspend fun saveManualQuiz(
        nodeId: String?,
        prompt: String,
        answer: String,
        explanation: String,
        now: Long = System.currentTimeMillis()
    ): QuizItemEntity {
        val quiz = QuizItemEntity(
            id = UUID.randomUUID().toString(),
            nodeId = nodeId,
            prompt = prompt,
            answer = answer,
            explanation = explanation,
            source = QuizSource.manual,
            sourceAnchor = null,
            createdAt = now,
            updatedAt = now,
            revision = 1,
            syncStatus = SyncStatus.dirty,
            deletedAt = null,
            area = snapshotAreaForNode(nodeId),
            track = snapshotTrackForNode(nodeId),
            visibility = "practice",
            isStarter = false
        )
        dao.upsertQuiz(quiz)
        dao.upsertReviewState(defaultReviewState(quiz.id, now))
        indexQuiz(quiz)
        return quiz
    }

    suspend fun answerQuiz(
        quizId: String,
        rating: ReviewRating,
        now: Long = System.currentTimeMillis()
    ) {
        val state = dao.getReviewState(quizId) ?: defaultReviewState(quizId, now)
        val result = ReviewScheduler.next(
            input = ReviewScheduleInput(
                ease = state.ease,
                intervalDays = state.intervalDays,
                attemptCount = state.attemptCount,
                answeredAt = Instant.ofEpochMilli(now)
            ),
            rating = rating
        )
        dao.upsertReviewState(
            state.copy(
                ease = result.ease,
                intervalDays = result.intervalDays,
                dueAt = result.dueAt.toEpochMilli(),
                lastResult = rating.toReviewResult(),
                attemptCount = state.attemptCount + 1,
                updatedAt = now
            )
        )
        dao.insertAttempt(
            ReviewAttemptEntity(
                id = UUID.randomUUID().toString(),
                quizId = quizId,
                result = rating.toReviewResult(),
                answeredAt = now,
                scheduledDueAt = result.dueAt.toEpochMilli()
            )
        )
    }

    suspend fun search(rawQuery: String): List<SearchResultEntity> {
        val query = rawQuery
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
        if (query.isBlank()) return emptyList()
        return dao.searchNodes(query) + dao.searchQuizzes(query)
    }

    suspend fun exportBackup(now: Long = System.currentTimeMillis()): String =
        BackupCodec.encode(
            LearningBackup(
                schemaVersion = BackupCodec.SchemaVersion,
                exportedAt = now,
                nodes = dao.getAllNodes(),
                quizzes = dao.getAllQuizzes(),
                reviewStates = dao.getAllReviewStates(),
                attempts = dao.getAllAttempts(),
                readerQuestions = dao.getAllReaderQuestions(),
                captureSlips = dao.getAllCaptureSlips()
            )
        )

    suspend fun exportReadableMarkdown(now: Long = System.currentTimeMillis()): String {
        val activeNodes = dao.getAllNodes()
            .filter { it.deletedAt == null && it.visibility != "trash" }
            .sortedWith(compareBy<LearningNodeEntity> { it.area }.thenBy { it.track }.thenBy { it.order }.thenBy { it.title })
        val activeQuestions = dao.getAllReaderQuestions()
            .filter { it.deletedAt == null && it.resolvedAt == null }
            .groupBy { it.nodeId }
        val inboxSlips = dao.getAllCaptureSlips()
            .filter {
                it.deletedAt == null && it.status in setOf(
                    CaptureSlipStatus.inbox,
                    CaptureSlipStatus.ai_queued,
                    CaptureSlipStatus.ai_drafting,
                    CaptureSlipStatus.ai_draft_ready
                )
            }
        val activeQuizzes = dao.getAllQuizzes()
            .filter { it.deletedAt == null }
            .sortedWith(compareBy<QuizItemEntity> { it.area }.thenBy { it.track }.thenBy { it.prompt })

        return buildString {
            appendLine("# CS Learning OS Markdown Export")
            appendLine()
            appendLine("- Exported at: $now")
            appendLine("- Format: readable Markdown/TXT for desktop review, printing, or manual merge.")
            appendLine()
            appendLine("## Nodes")
            appendLine()
            if (activeNodes.isEmpty()) {
                appendLine("_No active nodes._")
                appendLine()
            }
            activeNodes.forEach { node ->
                appendLine("## ${node.title}")
                appendLine()
                appendLine("Area: ${node.area} / Track: ${node.track} / Order: ${node.order}")
                if (node.summary.isNotBlank()) {
                    appendLine()
                    appendLine("> ${node.summary}")
                }
                appendLine()
                appendLine(node.markdownBody.trim())
                appendLine()
                val questions = activeQuestions[node.id].orEmpty()
                if (questions.isNotEmpty()) {
                    appendLine("### Open Questions")
                    questions.forEach { question -> appendLine("- ${question.body}") }
                    appendLine()
                }
            }

            appendLine("## Capture Slip Inbox")
            appendLine()
            if (inboxSlips.isEmpty()) {
                appendLine("_No open capture slips._")
                appendLine()
            } else {
                inboxSlips.sortedByDescending { it.createdAt }.forEach { slip ->
                    appendLine("- [${slip.type.name}] ${slip.body}")
                    slip.topicHint?.let { appendLine("  - Topic: $it") }
                    slip.sourceLabel?.let { appendLine("  - Source: $it") }
                }
                appendLine()
            }

            appendLine("## Quiz Cards")
            appendLine()
            if (activeQuizzes.isEmpty()) {
                appendLine("_No active quiz cards._")
            } else {
                activeQuizzes.forEach { quiz ->
                    appendLine("### ${quiz.prompt}")
                    appendLine()
                    appendLine("Answer: ${quiz.answer}")
                    if (quiz.explanation.isNotBlank()) {
                        appendLine()
                        appendLine(quiz.explanation)
                    }
                    appendLine()
                }
            }
        }.trimEnd() + "\n"
    }

    suspend fun restoreBackup(rawJson: String) {
        val backup = BackupCodec.decode(rawJson)
        dao.restoreBackup(
            nodes = backup.nodes,
            quizzes = backup.quizzes,
            states = backup.reviewStates,
            attempts = backup.attempts,
            questions = backup.readerQuestions,
            captureSlips = backup.captureSlips,
            nodeFts = backup.nodes
                .filter { it.deletedAt == null && it.visibility != "trash" }
                .map {
                    NodeFtsEntity(
                        rowId = stableRowId(it.id),
                        nodeId = it.id,
                        title = it.title,
                        body = it.markdownBody
                    )
                },
            quizFts = backup.quizzes
                .filter { it.deletedAt == null && it.visibility != "trash" }
                .map {
                    QuizFtsEntity(
                        rowId = stableRowId(it.id),
                        quizId = it.id,
                        prompt = it.prompt,
                        answer = it.answer
                    )
                }
        )
    }

    private suspend fun syncMarkdownQuizzes(node: LearningNodeEntity, now: Long) {
        val parsedCards = MarkdownQuizParser.parse(node.markdownBody)
        val activeAnchors = parsedCards.mapTo(mutableSetOf()) { it.sourceAnchor }

        dao.getActiveQuizzesForNode(node.id)
            .filter { it.source == QuizSource.markdown && it.sourceAnchor !in activeAnchors }
            .forEach { removed ->
                dao.upsertQuiz(
                    removed.copy(
                        updatedAt = now,
                        revision = removed.revision + 1,
                        syncStatus = SyncStatus.deleted,
                        deletedAt = now
                    )
                )
                dao.deleteQuizFts(removed.id)
            }

        parsedCards.forEach { parsed ->
            val id = "${node.id}:${parsed.sourceAnchor}"
            val existing = dao.getQuiz(id)
            val quiz = QuizItemEntity(
                id = id,
                nodeId = node.id,
                prompt = parsed.prompt,
                answer = parsed.answer,
                explanation = parsed.explanation,
                source = QuizSource.markdown,
                sourceAnchor = parsed.sourceAnchor,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                revision = (existing?.revision ?: 0L) + 1L,
                syncStatus = SyncStatus.dirty,
                deletedAt = null,
                area = node.area,
                track = node.track,
                visibility = "practice",
                isStarter = node.isStarter
            )
            dao.upsertQuiz(quiz)
            if (existing == null) {
                dao.upsertReviewState(defaultReviewState(quiz.id, now))
            }
            indexQuiz(quiz)
        }
    }

    private suspend fun indexNode(node: LearningNodeEntity) {
        dao.deleteNodeFts(node.id)
        if (node.deletedAt == null && node.visibility != "trash") {
            dao.upsertNodeFts(
                NodeFtsEntity(
                    rowId = stableRowId(node.id),
                    nodeId = node.id,
                    title = node.title,
                    body = node.markdownBody
                )
            )
        }
    }

    private suspend fun indexQuiz(quiz: QuizItemEntity) {
        dao.deleteQuizFts(quiz.id)
        if (quiz.deletedAt == null && quiz.visibility != "trash") {
            dao.upsertQuizFts(
                QuizFtsEntity(
                    rowId = stableRowId(quiz.id),
                    quizId = quiz.id,
                    prompt = quiz.prompt,
                    answer = quiz.answer
                )
            )
        }
    }

    private fun defaultReviewState(quizId: String, now: Long): ReviewStateEntity =
        ReviewStateEntity(
            quizId = quizId,
            ease = 2.5,
            intervalDays = 0,
            dueAt = now,
            lastResult = ReviewResult.again,
            attemptCount = 0,
            updatedAt = now
        )

    private fun stableRowId(id: String): Int = id.hashCode().absoluteValue.coerceAtLeast(1)

    private suspend fun snapshotAreaForNode(nodeId: String?): String =
        nodeId?.let { dao.getNode(it)?.area } ?: "questions"

    private suspend fun snapshotTrackForNode(nodeId: String?): String =
        nodeId?.let { dao.getNode(it)?.track } ?: "general"

    private fun ReviewRating.toReviewResult(): ReviewResult =
        when (this) {
            ReviewRating.Again -> ReviewResult.again
            ReviewRating.Hard -> ReviewResult.hard
            ReviewRating.Good -> ReviewResult.good
        }
}
