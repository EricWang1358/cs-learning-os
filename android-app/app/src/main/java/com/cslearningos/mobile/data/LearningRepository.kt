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
    val quizzes: Flow<List<QuizItemEntity>> = dao.observeQuizzes()

    fun dueQuizzes(now: Long): Flow<List<QuizItemEntity>> = dao.observeDueQuizzes(now)

    suspend fun getNode(id: String): LearningNodeEntity? = dao.getNode(id)

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
            deletedAt = null
        )
        dao.upsertNode(node)
        indexNode(node)
        syncMarkdownQuizzes(node, now)
        return node
    }

    suspend fun markRead(nodeId: String, now: Long = System.currentTimeMillis()) {
        val node = dao.getNode(nodeId) ?: return
        val updated = node.copy(
            lastReadAt = now,
            updatedAt = now,
            revision = node.revision + 1,
            syncStatus = SyncStatus.dirty
        )
        dao.upsertNode(updated)
        indexNode(updated)
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
            deletedAt = null
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
                attempts = dao.getAllAttempts()
            )
        )

    suspend fun restoreBackup(rawJson: String) {
        val backup = BackupCodec.decode(rawJson)
        dao.restoreBackup(
            nodes = backup.nodes,
            quizzes = backup.quizzes,
            states = backup.reviewStates,
            attempts = backup.attempts,
            nodeFts = backup.nodes
                .filter { it.deletedAt == null }
                .map {
                    NodeFtsEntity(
                        rowId = stableRowId(it.id),
                        nodeId = it.id,
                        title = it.title,
                        body = it.markdownBody
                    )
                },
            quizFts = backup.quizzes
                .filter { it.deletedAt == null }
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
        MarkdownQuizParser.parse(node.markdownBody).forEach { parsed ->
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
                deletedAt = null
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
        if (node.deletedAt == null) {
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
        if (quiz.deletedAt == null) {
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

    private fun ReviewRating.toReviewResult(): ReviewResult =
        when (this) {
            ReviewRating.Again -> ReviewResult.again
            ReviewRating.Hard -> ReviewResult.hard
            ReviewRating.Good -> ReviewResult.good
        }
}
