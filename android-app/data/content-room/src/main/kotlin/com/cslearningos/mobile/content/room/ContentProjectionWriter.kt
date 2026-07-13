package com.cslearningos.mobile.content.room

import com.cslearningos.mobile.content.domain.MarkdownQuizParser
import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.NodeFtsEntity
import com.cslearningos.mobile.data.QuizFtsEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.QuizSource
import com.cslearningos.mobile.data.ReviewResult
import com.cslearningos.mobile.data.ReviewStateEntity
import com.cslearningos.mobile.data.SyncStatus
import kotlin.math.absoluteValue

fun interface ContentProjectionWriter {
    suspend fun write(dao: LearningDao, node: LearningNodeEntity, timestamp: Long)
}

object RoomContentProjectionWriter : ContentProjectionWriter {
    override suspend fun write(dao: LearningDao, node: LearningNodeEntity, timestamp: Long) {
        indexNode(dao, node)
        syncMarkdownQuizzes(dao, node, timestamp)
    }

    private suspend fun syncMarkdownQuizzes(
        dao: LearningDao,
        node: LearningNodeEntity,
        timestamp: Long
    ) {
        val parsedCards = MarkdownQuizParser.parse(node.markdownBody)
        val activeAnchors = parsedCards.mapTo(mutableSetOf()) { it.sourceAnchor }

        dao.getActiveQuizzesForNode(node.id)
            .filter { it.source == QuizSource.markdown && it.sourceAnchor !in activeAnchors }
            .forEach { removed ->
                dao.upsertQuiz(
                    removed.copy(
                        updatedAt = timestamp,
                        revision = removed.revision + REVISION_STEP,
                        syncStatus = SyncStatus.deleted,
                        deletedAt = timestamp
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
                createdAt = existing?.createdAt ?: timestamp,
                updatedAt = timestamp,
                revision = (existing?.revision ?: 0L) + REVISION_STEP,
                syncStatus = SyncStatus.dirty,
                deletedAt = null,
                area = node.area,
                track = node.track,
                visibility = PRACTICE_VISIBILITY,
                isStarter = node.isStarter
            )
            dao.upsertQuiz(quiz)
            if (existing == null) {
                dao.upsertReviewState(defaultReviewState(quiz.id, timestamp))
            }
            indexQuiz(dao, quiz)
        }
    }

    private suspend fun indexNode(dao: LearningDao, node: LearningNodeEntity) {
        dao.deleteNodeFts(node.id)
        if (node.deletedAt == null && node.visibility != TRASH_VISIBILITY) {
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

    private suspend fun indexQuiz(dao: LearningDao, quiz: QuizItemEntity) {
        dao.deleteQuizFts(quiz.id)
        if (quiz.deletedAt == null && quiz.visibility != TRASH_VISIBILITY) {
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

    private fun defaultReviewState(quizId: String, timestamp: Long) = ReviewStateEntity(
        quizId = quizId,
        ease = DEFAULT_REVIEW_EASE,
        intervalDays = INITIAL_INTERVAL_DAYS,
        dueAt = timestamp,
        lastResult = ReviewResult.again,
        attemptCount = INITIAL_ATTEMPT_COUNT,
        updatedAt = timestamp
    )

    private fun stableRowId(id: String): Int = id.hashCode().absoluteValue.coerceAtLeast(MINIMUM_STABLE_ROW_ID)

    private const val DEFAULT_REVIEW_EASE = 2.5
    private const val INITIAL_ATTEMPT_COUNT = 0
    private const val INITIAL_INTERVAL_DAYS = 0
    private const val MINIMUM_STABLE_ROW_ID = 1
    private const val PRACTICE_VISIBILITY = "practice"
    private const val REVISION_STEP = 1L
    private const val TRASH_VISIBILITY = "trash"
}
