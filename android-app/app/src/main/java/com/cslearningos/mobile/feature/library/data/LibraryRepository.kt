package com.cslearningos.mobile.feature.library.data

import com.cslearningos.mobile.core.common.AndroidArchitectureConstants
import com.cslearningos.mobile.content.application.ContentCommandFailure
import com.cslearningos.mobile.content.application.ContentCommandPort
import com.cslearningos.mobile.content.application.ContentCommandResult
import com.cslearningos.mobile.content.application.NodeSaveMode
import com.cslearningos.mobile.content.application.SaveNodeCommand
import com.cslearningos.mobile.content.domain.NodeId
import com.cslearningos.mobile.content.room.ContentNodeCodec
import com.cslearningos.mobile.content.room.NodeRoomMapper
import com.cslearningos.mobile.core.kernel.CommandId
import com.cslearningos.mobile.core.kernel.EntityRevision
import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.NodeFtsEntity
import com.cslearningos.mobile.data.QuizFtsEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.ReplicationOutboxEntity
import com.cslearningos.mobile.data.ReaderQuestionEntity
import com.cslearningos.mobile.data.SearchResultEntity
import com.cslearningos.mobile.data.StarterContentPackage
import com.cslearningos.mobile.data.SyncStatus
import com.cslearningos.mobile.data.withReadTrace
import com.cslearningos.mobile.feature.review.data.QuizOutboxCodec
import kotlinx.coroutines.flow.Flow
import java.text.Normalizer
import java.util.UUID
import kotlin.math.absoluteValue

/**
 * Feature-local entry point for library data operations.
 *
 * The current implementation keeps the DAO boundary narrow while the larger
 * repository split is still in progress.
 */
class LibraryRepository(
    private val dao: LearningDao,
    private val contentCommands: ContentCommandPort
) {
    val areas: Flow<List<AreaEntity>> = dao.observeAreas()
    val nodes: Flow<List<LearningNodeEntity>> = dao.observeNodes()
    val trashNodes: Flow<List<LearningNodeEntity>> = dao.observeTrashNodes()

    suspend fun getNode(id: String): LearningNodeEntity? = dao.getNode(id)

    suspend fun saveNode(
        id: String?,
        expectedRevision: Long? = null,
        title: String,
        markdownBody: String,
        areaId: String? = null,
        now: Long = System.currentTimeMillis()
    ): LearningNodeEntity {
        val existing = id?.let { nodeId ->
            dao.getNode(nodeId) ?: throw IllegalArgumentException("Node is no longer available.")
        }
        val resolvedAreaId = areaId ?: existing?.areaId ?: DefaultAreaSlug
        return saveNode(
            SaveNodeCommand(
                commandId = CommandId(UUID.randomUUID().toString()),
                nodeId = NodeId(existing?.id ?: UUID.randomUUID().toString()),
                mode = if (existing == null) NodeSaveMode.Create else NodeSaveMode.Update,
                expectedRevision = (expectedRevision ?: existing?.revision)?.let(::EntityRevision),
                areaId = resolvedAreaId,
                title = title,
                markdownBody = markdownBody,
                occurredAt = now
            )
        )
    }

    suspend fun saveNode(command: SaveNodeCommand): LearningNodeEntity = when (val result = contentCommands.saveNode(command)) {
        is ContentCommandResult.Success -> NodeRoomMapper.toEntity(
            node = result.node,
            existing = dao.getNode(result.node.id.value)
        )
        is ContentCommandResult.Failure -> throw compatibilitySaveFailure(result.failure)
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
        val updated = node.copy(
            updatedAt = now,
            revision = node.revision + RevisionStep,
            syncStatus = SyncStatus.dirty,
            visibility = TrashVisibility
        )
        val updatedQuizzes = dao.getActiveQuizzesForNode(nodeId).map { quiz ->
            quiz.copy(
                updatedAt = now,
                revision = quiz.revision + RevisionStep,
                syncStatus = SyncStatus.dirty,
                visibility = TrashVisibility
            )
        }
        dao.saveNodeAndQuizzesWithContentOutbox(
            node = updated,
            nodeFts = null,
            nodeOutbox = nodeContentOutbox(previous = node, updated = updated, now = now),
            quizzes = updatedQuizzes,
            quizFts = emptyList(),
            quizOutbox = updatedQuizzes.map { quiz ->
                quizContentOutbox(previousRevision = quiz.revision - RevisionStep, updated = quiz, now = now)
            }
        )
    }

    suspend fun restoreNodeFromTrash(nodeId: String, now: Long = System.currentTimeMillis()) {
        val node = dao.getNode(nodeId) ?: return
        if (node.deletedAt != null) return
        val area = resolveArea(node.areaId.ifBlank { node.area }, now)
        val restored = node.copy(
            updatedAt = now,
            revision = node.revision + RevisionStep,
            syncStatus = SyncStatus.dirty,
            area = area.slug,
            areaId = area.id,
            visibility = if (node.isStarter) CoreVisibility else SupportVisibility
        )
        val restoredQuizzes = dao.getActiveQuizzesForNode(nodeId).map { quiz ->
            quiz.copy(
                updatedAt = now,
                revision = quiz.revision + RevisionStep,
                syncStatus = SyncStatus.dirty,
                visibility = PracticeVisibility
            )
        }
        dao.saveNodeAndQuizzesWithContentOutbox(
            node = restored,
            nodeFts = nodeFts(restored),
            nodeOutbox = nodeContentOutbox(previous = node, updated = restored, now = now),
            quizzes = restoredQuizzes,
            quizFts = restoredQuizzes.mapNotNull(::quizFts),
            quizOutbox = restoredQuizzes.map { quiz ->
                quizContentOutbox(previousRevision = quiz.revision - RevisionStep, updated = quiz, now = now)
            }
        )
    }

    suspend fun permanentlyDeleteNode(nodeId: String, now: Long = System.currentTimeMillis()) {
        val node = dao.getNode(nodeId) ?: return
        val deletedNode = node.copy(
            updatedAt = now,
            revision = node.revision + RevisionStep,
            syncStatus = SyncStatus.deleted,
            deletedAt = now
        )
        val deletedQuizzes = dao.getActiveQuizzesForNode(node.id).map { quiz ->
            quiz.copy(
                updatedAt = now,
                revision = quiz.revision + RevisionStep,
                syncStatus = SyncStatus.deleted,
                deletedAt = now
            )
        }
        dao.saveNodeAndQuizzesWithContentOutbox(
            node = deletedNode,
            nodeFts = null,
            nodeOutbox = nodeContentOutbox(previous = node, updated = deletedNode, now = now),
            quizzes = deletedQuizzes,
            quizFts = emptyList(),
            quizOutbox = deletedQuizzes.map { quiz ->
                quizContentOutbox(previousRevision = quiz.revision - RevisionStep, updated = quiz, now = now)
            }
        )

        deletedQuizzes.forEach { quiz ->
            deleteReviewDataForQuiz(quiz.id)
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
                    revision = node.revision + RevisionStep,
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
                    revision = quiz.revision + RevisionStep,
                    syncStatus = SyncStatus.deleted,
                    deletedAt = now
                )
            )
            dao.deleteQuizFts(quiz.id)
            deleteReviewDataForQuiz(quiz.id)
        }
    }

    suspend fun seedStarterContent(pack: StarterContentPackage) {
        pack.nodes.forEach { node ->
            ensureAreaExists(areaId = node.areaId, slug = node.area, now = node.updatedAt)
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

    suspend fun createArea(name: String, now: Long = System.currentTimeMillis()): AreaEntity {
        val normalizedName = name.trim().ifBlank { NewAreaName }
        val slug = uniqueAreaSlug(normalizedName)
        val area = AreaEntity(
            id = slug,
            slug = slug,
            name = normalizedName,
            order = nextAreaOrder(),
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )
        dao.upsertArea(area)
        return area
    }

    suspend fun renameArea(areaId: String, name: String, now: Long = System.currentTimeMillis()) {
        val area = dao.getArea(areaId) ?: return
        dao.upsertArea(
            area.copy(
                name = name.trim().ifBlank { area.name },
                updatedAt = now
            )
        )
    }

    suspend fun deleteAreaIfEmpty(areaId: String, now: Long = System.currentTimeMillis()): Boolean {
        if (dao.countActiveNodesInArea(areaId) > 0) return false
        val area = dao.getArea(areaId) ?: return false
        dao.upsertArea(area.copy(updatedAt = now, deletedAt = now))
        return true
    }

    suspend fun moveNodeToArea(nodeId: String, targetAreaId: String, now: Long = System.currentTimeMillis()) {
        val node = dao.getNode(nodeId) ?: return
        val area = resolveArea(targetAreaId, now)
        val updated = node.copy(
            updatedAt = now,
            revision = node.revision + RevisionStep,
            syncStatus = SyncStatus.dirty,
            area = area.slug,
            areaId = area.id
        )
        val updatedQuizzes = dao.getActiveQuizzesForNode(nodeId).map { quiz ->
            quiz.copy(
                updatedAt = now,
                revision = quiz.revision + RevisionStep,
                syncStatus = SyncStatus.dirty,
                area = area.slug
            )
        }
        dao.saveNodeAndQuizzesWithContentOutbox(
            node = updated,
            nodeFts = nodeFts(updated),
            nodeOutbox = nodeContentOutbox(previous = node, updated = updated, now = now),
            quizzes = updatedQuizzes,
            quizFts = updatedQuizzes.mapNotNull(::quizFts),
            quizOutbox = updatedQuizzes.map { quiz ->
                quizContentOutbox(previousRevision = quiz.revision - RevisionStep, updated = quiz, now = now)
            }
        )
    }

    suspend fun toggleNodeChecked(nodeId: String, now: Long = System.currentTimeMillis()) {
        val node = dao.getNode(nodeId) ?: return
        val updated = node.copy(
            updatedAt = now,
            revision = node.revision + RevisionStep,
            syncStatus = SyncStatus.dirty,
            isChecked = !node.isChecked
        )
        dao.upsertNode(updated)
        indexNode(updated)
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

    private suspend fun indexNode(node: LearningNodeEntity) {
        dao.deleteNodeFts(node.id)
        val fts = nodeFts(node)
        if (fts != null) {
            dao.upsertNodeFts(fts)
        }
    }

    private suspend fun indexQuiz(quiz: QuizItemEntity) {
        dao.deleteQuizFts(quiz.id)
        val fts = quizFts(quiz)
        if (fts != null) {
            dao.upsertQuizFts(fts)
        }
    }

    private suspend fun deleteReviewDataForQuiz(quizId: String) {
        dao.deleteReviewStateForQuiz(quizId)
        dao.deleteReviewAttemptsForQuiz(quizId)
    }

    private fun nodeFts(node: LearningNodeEntity): NodeFtsEntity? =
        if (node.deletedAt == null && node.visibility != TrashVisibility) {
            NodeFtsEntity(
                rowId = stableRowId(node.id),
                nodeId = node.id,
                title = node.title,
                body = node.markdownBody
            )
        } else {
            null
        }

    private fun quizFts(quiz: QuizItemEntity): QuizFtsEntity? =
        if (quiz.deletedAt == null && quiz.visibility != TrashVisibility) {
            QuizFtsEntity(
                rowId = stableRowId(quiz.id),
                quizId = quiz.id,
                prompt = quiz.prompt,
                answer = quiz.answer
            )
        } else {
            null
        }

    private fun nodeContentOutbox(
        previous: LearningNodeEntity,
        updated: LearningNodeEntity,
        now: Long
    ): ReplicationOutboxEntity {
        val payload = ContentNodeCodec.encode(NodeRoomMapper.toDomain(updated))
        return ReplicationOutboxEntity(
            changeId = UUID.randomUUID().toString(),
            commandId = UUID.randomUUID().toString(),
            aggregateType = ContentNodeAggregateType,
            aggregateId = updated.id,
            operation = UpdateOperation,
            baseRevision = previous.revision,
            newRevision = updated.revision,
            domainSchemaVersion = ContentNodeCodec.SchemaVersion,
            payloadJson = payload,
            payloadHash = ContentNodeCodec.sha256Hex(payload),
            state = PendingOutboxState,
            createdAt = now
        )
    }

    private fun quizContentOutbox(
        previousRevision: Long,
        updated: QuizItemEntity,
        now: Long
    ): ReplicationOutboxEntity {
        val payload = QuizOutboxCodec.encode(updated)
        return ReplicationOutboxEntity(
            changeId = UUID.randomUUID().toString(),
            commandId = UUID.randomUUID().toString(),
            aggregateType = QuizAggregateType,
            aggregateId = updated.id,
            operation = UpdateOperation,
            baseRevision = previousRevision,
            newRevision = updated.revision,
            domainSchemaVersion = QuizOutboxCodec.SchemaVersion,
            payloadJson = payload,
            payloadHash = QuizOutboxCodec.sha256Hex(payload),
            state = PendingOutboxState,
            createdAt = now
        )
    }

    private fun stableRowId(id: String): Int = id.hashCode().absoluteValue.coerceAtLeast(MinimumStableRowId)

    private fun compatibilitySaveFailure(failure: ContentCommandFailure): RuntimeException = when (failure) {
        is ContentCommandFailure.Validation,
        is ContentCommandFailure.Missing -> IllegalArgumentException("Node save was rejected.")
        ContentCommandFailure.Deleted,
        is ContentCommandFailure.StaleRevision,
        ContentCommandFailure.CommandReuseConflict,
        is ContentCommandFailure.Storage -> IllegalStateException("Node save could not be completed.")
    }

    private suspend fun resolveArea(areaIdOrSlug: String, now: Long): AreaEntity =
        dao.getArea(areaIdOrSlug)
            ?: dao.getAreaBySlug(areaIdOrSlug)
            ?: createImplicitArea(areaIdOrSlug, now)

    private suspend fun ensureAreaExists(areaId: String, slug: String, now: Long) {
        if (dao.getArea(areaId) != null || dao.getAreaBySlug(slug) != null) return
        dao.upsertArea(
            AreaEntity(
                id = areaId,
                slug = slug,
                name = slug,
                order = nextAreaOrder(),
                createdAt = now,
                updatedAt = now,
                deletedAt = null
            )
        )
    }

    private suspend fun createImplicitArea(slugSeed: String, now: Long): AreaEntity {
        val slug = slugSeed.ifBlank { DefaultAreaSlug }
        val area = AreaEntity(
            id = slug,
            slug = slug,
            name = slug,
            order = nextAreaOrder(),
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )
        dao.upsertArea(area)
        return area
    }

    private suspend fun nextAreaOrder(): Int =
        (dao.getAllAreas().maxOfOrNull { it.order } ?: 0) + AndroidArchitectureConstants.AreaOrderStep

    private suspend fun uniqueAreaSlug(name: String): String {
        val base = slugify(name).ifBlank { DefaultAreaSlug }
        var candidate = base
        var suffix = InitialSlugSuffix
        while (dao.getArea(candidate) != null || dao.getAreaBySlug(candidate) != null) {
            candidate = "$base-$suffix"
            suffix += 1
        }
        return candidate
    }

    private fun slugify(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    private companion object {
        const val ContentNodeAggregateType = "content.node"
        const val CoreVisibility = "core"
        const val DefaultAreaSlug = "questions"
        const val InitialSlugSuffix = 2
        const val MinimumStableRowId = 1
        const val NewAreaName = "New Area"
        const val PendingOutboxState = "pending"
        const val PracticeVisibility = "practice"
        const val QuizAggregateType = "content.quiz"
        const val RevisionStep = 1L
        const val SupportVisibility = "support"
        const val TrashVisibility = "trash"
        const val UpdateOperation = "update"
    }
}
