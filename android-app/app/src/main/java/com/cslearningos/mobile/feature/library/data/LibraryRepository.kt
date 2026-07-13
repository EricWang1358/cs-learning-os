package com.cslearningos.mobile.feature.library.data

import com.cslearningos.mobile.core.common.AndroidArchitectureConstants
import com.cslearningos.mobile.content.application.ContentCommandFailure
import com.cslearningos.mobile.content.application.ContentCommandPort
import com.cslearningos.mobile.content.application.ContentCommandResult
import com.cslearningos.mobile.content.application.NodeSaveMode
import com.cslearningos.mobile.content.application.SaveNodeCommand
import com.cslearningos.mobile.content.domain.NodeId
import com.cslearningos.mobile.content.room.NodeRoomMapper
import com.cslearningos.mobile.core.kernel.CommandId
import com.cslearningos.mobile.core.kernel.EntityRevision
import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.NodeFtsEntity
import com.cslearningos.mobile.data.QuizFtsEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.ReaderQuestionEntity
import com.cslearningos.mobile.data.SearchResultEntity
import com.cslearningos.mobile.data.StarterContentPackage
import com.cslearningos.mobile.data.SyncStatus
import com.cslearningos.mobile.data.withReadTrace
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
        val resolvedAreaId = areaId ?: existing?.areaId
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
        dao.upsertNode(
            node.copy(
                updatedAt = now,
                revision = node.revision + RevisionStep,
                syncStatus = SyncStatus.dirty,
                visibility = TrashVisibility
            )
        )
        dao.deleteNodeFts(node.id)
        dao.getActiveQuizzesForNode(nodeId).forEach { quiz ->
            dao.upsertQuiz(
                quiz.copy(
                    updatedAt = now,
                    revision = quiz.revision + RevisionStep,
                    syncStatus = SyncStatus.dirty,
                    visibility = TrashVisibility
                )
            )
            dao.deleteQuizFts(quiz.id)
        }
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
        dao.upsertNode(restored)
        indexNode(restored)
        dao.getActiveQuizzesForNode(nodeId).forEach { quiz ->
            val restoredQuiz = quiz.copy(
                updatedAt = now,
                revision = quiz.revision + RevisionStep,
                syncStatus = SyncStatus.dirty,
                visibility = PracticeVisibility
            )
            dao.upsertQuiz(restoredQuiz)
            indexQuiz(restoredQuiz)
        }
    }

    suspend fun permanentlyDeleteNode(nodeId: String, now: Long = System.currentTimeMillis()) {
        val node = dao.getNode(nodeId) ?: return
        val deletedNode = node.copy(
            updatedAt = now,
            revision = node.revision + RevisionStep,
            syncStatus = SyncStatus.deleted,
            deletedAt = now
        )
        dao.upsertNode(deletedNode)
        dao.deleteNodeFts(node.id)

        dao.getActiveQuizzesForNode(node.id).forEach { quiz ->
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
        dao.upsertNode(updated)
        indexNode(updated)
        dao.getActiveQuizzesForNode(nodeId).forEach { quiz ->
            val updatedQuiz = quiz.copy(
                updatedAt = now,
                revision = quiz.revision + RevisionStep,
                syncStatus = SyncStatus.dirty,
                area = area.slug
            )
            dao.upsertQuiz(updatedQuiz)
            indexQuiz(updatedQuiz)
        }
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
        if (node.deletedAt == null && node.visibility != TrashVisibility) {
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
        if (quiz.deletedAt == null && quiz.visibility != TrashVisibility) {
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

    private suspend fun deleteReviewDataForQuiz(quizId: String) {
        dao.deleteReviewStateForQuiz(quizId)
        dao.deleteReviewAttemptsForQuiz(quizId)
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
        const val CoreVisibility = "core"
        const val DefaultAreaSlug = "questions"
        const val InitialSlugSuffix = 2
        const val MinimumStableRowId = 1
        const val NewAreaName = "New Area"
        const val PracticeVisibility = "practice"
        const val RevisionStep = 1L
        const val SupportVisibility = "support"
        const val TrashVisibility = "trash"
    }
}
