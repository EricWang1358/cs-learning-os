package com.cslearningos.mobile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.cslearningos.mobile.feature.assistant.data.AssistantConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningDao {
    @Query("SELECT * FROM areas WHERE deleted_at IS NULL ORDER BY `order` ASC, name ASC")
    fun observeAreas(): Flow<List<AreaEntity>>

    @Query("SELECT * FROM learning_nodes WHERE deleted_at IS NULL AND visibility != 'trash' ORDER BY area ASC, track ASC, `order` ASC, updated_at DESC")
    fun observeNodes(): Flow<List<LearningNodeEntity>>

    @Query("SELECT * FROM learning_nodes WHERE deleted_at IS NULL AND visibility = 'trash' ORDER BY updated_at DESC")
    fun observeTrashNodes(): Flow<List<LearningNodeEntity>>

    @Query("SELECT * FROM quiz_items WHERE deleted_at IS NULL AND visibility != 'trash' ORDER BY updated_at DESC")
    fun observeQuizzes(): Flow<List<QuizItemEntity>>

    @Query("SELECT * FROM reader_questions WHERE deleted_at IS NULL AND resolved_at IS NULL ORDER BY created_at DESC")
    fun observeOpenReaderQuestions(): Flow<List<ReaderQuestionEntity>>

    @Query(
        """
        SELECT * FROM capture_slips
        WHERE deleted_at IS NULL
          AND status IN ('inbox', 'ai_queued', 'ai_drafting', 'ai_draft_ready')
        ORDER BY created_at DESC
        """
    )
    fun observeInboxCaptureSlips(): Flow<List<CaptureSlipEntity>>

    @Query(
        """
        SELECT * FROM capture_slips
        WHERE deleted_at IS NULL
          AND status = 'archived'
        ORDER BY updated_at DESC
        """
    )
    fun observeArchivedCaptureSlips(): Flow<List<CaptureSlipEntity>>

    @Query(
        """
        SELECT quiz_items.* FROM quiz_items
        INNER JOIN review_states ON quiz_items.id = review_states.quiz_id
        WHERE quiz_items.deleted_at IS NULL AND quiz_items.visibility != 'trash' AND review_states.due_at <= :now
        ORDER BY review_states.due_at ASC
        """
    )
    fun observeDueQuizzes(now: Long): Flow<List<QuizItemEntity>>

    @Query("SELECT * FROM areas WHERE id = :id AND deleted_at IS NULL LIMIT 1")
    suspend fun getArea(id: String): AreaEntity?

    @Query("SELECT * FROM areas WHERE slug = :slug AND deleted_at IS NULL LIMIT 1")
    suspend fun getAreaBySlug(slug: String): AreaEntity?

    @Query("SELECT * FROM learning_nodes WHERE id = :id LIMIT 1")
    suspend fun getNode(id: String): LearningNodeEntity?

    @Query("SELECT * FROM quiz_items WHERE id = :id LIMIT 1")
    suspend fun getQuiz(id: String): QuizItemEntity?

    @Query("SELECT * FROM reader_questions WHERE id = :id LIMIT 1")
    suspend fun getReaderQuestion(id: String): ReaderQuestionEntity?

    @Query("SELECT * FROM capture_slips WHERE id = :id LIMIT 1")
    suspend fun getCaptureSlip(id: String): CaptureSlipEntity?

    @Query("SELECT * FROM quiz_items WHERE node_id = :nodeId AND deleted_at IS NULL")
    suspend fun getActiveQuizzesForNode(nodeId: String): List<QuizItemEntity>

    @Query("SELECT * FROM reader_questions WHERE node_id = :nodeId AND deleted_at IS NULL")
    suspend fun getActiveReaderQuestionsForNode(nodeId: String): List<ReaderQuestionEntity>

    @Query("SELECT * FROM review_states WHERE quiz_id = :quizId LIMIT 1")
    suspend fun getReviewState(quizId: String): ReviewStateEntity?

    @Query("SELECT * FROM areas")
    suspend fun getAllAreas(): List<AreaEntity>

    @Query("SELECT * FROM learning_nodes")
    suspend fun getAllNodes(): List<LearningNodeEntity>

    @Query("SELECT * FROM learning_nodes WHERE is_starter = 1 AND deleted_at IS NULL")
    suspend fun getStarterNodes(): List<LearningNodeEntity>

    @Query("SELECT * FROM quiz_items")
    suspend fun getAllQuizzes(): List<QuizItemEntity>

    @Query("SELECT * FROM quiz_items WHERE is_starter = 1 AND deleted_at IS NULL")
    suspend fun getStarterQuizzes(): List<QuizItemEntity>

    @Query("SELECT * FROM review_states")
    suspend fun getAllReviewStates(): List<ReviewStateEntity>

    @Query("SELECT * FROM review_attempts")
    suspend fun getAllAttempts(): List<ReviewAttemptEntity>

    @Query("SELECT * FROM reader_questions")
    suspend fun getAllReaderQuestions(): List<ReaderQuestionEntity>

    @Query("SELECT * FROM capture_slips")
    suspend fun getAllCaptureSlips(): List<CaptureSlipEntity>

    @Query("SELECT * FROM assistant_conversations ORDER BY updated_at DESC LIMIT 1")
    suspend fun latestAssistantConversation(): AssistantConversationEntity?

    @Query("SELECT * FROM assistant_conversations ORDER BY updated_at DESC LIMIT :limit")
    suspend fun recentAssistantConversations(limit: Int): List<AssistantConversationEntity>

    @Query("SELECT * FROM assistant_conversations WHERE id = :id LIMIT 1")
    suspend fun getAssistantConversation(id: String): AssistantConversationEntity?

    @Query("DELETE FROM assistant_conversations WHERE id = :id")
    suspend fun deleteAssistantConversation(id: String)

    @Query("SELECT COUNT(*) FROM learning_nodes WHERE area_id = :areaId AND deleted_at IS NULL")
    suspend fun countActiveNodesInArea(areaId: String): Int

    @Query("SELECT * FROM processed_commands WHERE command_id = :commandId LIMIT 1")
    suspend fun getProcessedCommand(commandId: String): ProcessedCommandEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProcessedCommand(command: ProcessedCommandEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOutbox(item: ReplicationOutboxEntity)

    @Query("SELECT * FROM replication_outbox WHERE command_id = :commandId LIMIT 1")
    suspend fun getOutboxForCommand(commandId: String): ReplicationOutboxEntity?

    @Query(
        """
        SELECT * FROM replication_outbox
        WHERE aggregate_type = 'content.node' AND state = 'pending'
        ORDER BY created_at ASC, change_id ASC
        LIMIT :limit
        """
    )
    suspend fun getPendingNodeOutbox(limit: Int): List<ReplicationOutboxEntity>

    @Query(
        """
        SELECT * FROM replication_outbox
        WHERE aggregate_type = 'content.quiz' AND state = 'pending'
        ORDER BY created_at ASC, change_id ASC
        LIMIT :limit
        """
    )
    suspend fun getPendingQuizOutbox(limit: Int): List<ReplicationOutboxEntity>

    @Query(
        """
        UPDATE learning_nodes
        SET sync_status = 'clean', base_revision = :serverRevision
        WHERE id = :nodeId
          AND revision = :localRevision
          AND sync_status = 'dirty'
          AND deleted_at IS NULL
        """
    )
    suspend fun markNodeContentSynced(nodeId: String, localRevision: Long, serverRevision: Long)

    @Query(
        """
        UPDATE quiz_items
        SET sync_status = 'clean', base_revision = :serverRevision
        WHERE id = :quizId
          AND revision = :localRevision
          AND sync_status = 'dirty'
          AND deleted_at IS NULL
        """
    )
    suspend fun markQuizContentSynced(quizId: String, localRevision: Long, serverRevision: Long)

    @Query("DELETE FROM replication_outbox WHERE change_id = :changeId")
    suspend fun deleteOutboxItem(changeId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArea(area: AreaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNode(node: LearningNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuiz(quiz: QuizItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReaderQuestion(question: ReaderQuestionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCaptureSlip(slip: CaptureSlipEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssistantConversation(conversation: AssistantConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReviewState(state: ReviewStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: ReviewAttemptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAreas(areas: List<AreaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNodes(nodes: List<LearningNodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuizzes(quizzes: List<QuizItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReviewStates(states: List<ReviewStateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempts(attempts: List<ReviewAttemptEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReaderQuestions(questions: List<ReaderQuestionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCaptureSlips(slips: List<CaptureSlipEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNodeFts(entity: NodeFtsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuizFts(entity: QuizFtsEntity)

    @Query("DELETE FROM node_fts WHERE node_id = :nodeId")
    suspend fun deleteNodeFts(nodeId: String)

    @Query("DELETE FROM quiz_fts WHERE quiz_id = :quizId")
    suspend fun deleteQuizFts(quizId: String)

    @Transaction
    suspend fun saveManualQuizWithOutbox(
        quiz: QuizItemEntity,
        initialReviewState: ReviewStateEntity?,
        fts: QuizFtsEntity?,
        outbox: ReplicationOutboxEntity
    ) {
        upsertQuiz(quiz)
        if (initialReviewState != null) upsertReviewState(initialReviewState)
        deleteQuizFts(quiz.id)
        if (fts != null) upsertQuizFts(fts)
        insertOutbox(outbox)
    }

    @Query("DELETE FROM areas")
    suspend fun deleteAllAreas()

    @Query("DELETE FROM learning_nodes")
    suspend fun deleteAllNodes()

    @Query("DELETE FROM quiz_items")
    suspend fun deleteAllQuizzes()

    @Query("DELETE FROM review_states")
    suspend fun deleteAllReviewStates()

    @Query("DELETE FROM review_attempts")
    suspend fun deleteAllAttempts()

    @Query("DELETE FROM review_states WHERE quiz_id = :quizId")
    suspend fun deleteReviewStateForQuiz(quizId: String)

    @Query("DELETE FROM review_attempts WHERE quiz_id = :quizId")
    suspend fun deleteReviewAttemptsForQuiz(quizId: String)

    @Query("DELETE FROM reader_questions")
    suspend fun deleteAllReaderQuestions()

    @Query("DELETE FROM capture_slips")
    suspend fun deleteAllCaptureSlips()

    @Query("DELETE FROM node_fts")
    suspend fun deleteAllNodeFts()

    @Query("DELETE FROM quiz_fts")
    suspend fun deleteAllQuizFts()

    @Query("DELETE FROM processed_commands")
    suspend fun deleteAllProcessedCommands()

    @Query("DELETE FROM replication_outbox")
    suspend fun deleteAllOutboxItems()

    @Query("UPDATE learning_nodes SET deleted_at = :deletedAt, sync_status = 'clean', updated_at = :updatedAt WHERE id = :id")
    suspend fun markNodeSyncedDeleted(id: String, deletedAt: Long, updatedAt: Long)

    @Query("UPDATE quiz_items SET deleted_at = :deletedAt, sync_status = 'clean', updated_at = :updatedAt WHERE id = :id")
    suspend fun markQuizSyncedDeleted(id: String, deletedAt: Long, updatedAt: Long)

    @Transaction
    suspend fun acknowledgeNodeContentPush(
        changeId: String,
        nodeId: String,
        localRevision: Long,
        serverRevision: Long
    ) {
        val node = getNode(nodeId)
        if (node != null && node.revision == localRevision && node.syncStatus == SyncStatus.deleted && node.deletedAt != null) {
            markNodeSyncedDeleted(nodeId, node.deletedAt, node.updatedAt)
        } else {
            markNodeContentSynced(nodeId, localRevision, serverRevision)
        }
        deleteOutboxItem(changeId)
    }

    @Transaction
    suspend fun acknowledgeQuizContentPush(
        changeId: String,
        quizId: String,
        localRevision: Long,
        serverRevision: Long
    ) {
        val quiz = getQuiz(quizId)
        if (quiz != null && quiz.revision == localRevision && quiz.syncStatus == SyncStatus.deleted && quiz.deletedAt != null) {
            markQuizSyncedDeleted(quizId, quiz.deletedAt, quiz.updatedAt)
        } else {
            markQuizContentSynced(quizId, localRevision, serverRevision)
        }
        deleteOutboxItem(changeId)
    }

    @Transaction
    suspend fun saveNodeAndQuizzesWithContentOutbox(
        node: LearningNodeEntity,
        nodeFts: NodeFtsEntity?,
        nodeOutbox: ReplicationOutboxEntity,
        quizzes: List<QuizItemEntity>,
        quizFts: List<QuizFtsEntity>,
        quizOutbox: List<ReplicationOutboxEntity>
    ) {
        upsertNode(node)
        deleteNodeFts(node.id)
        if (nodeFts != null) upsertNodeFts(nodeFts)
        insertOutbox(nodeOutbox)
        quizzes.forEach { quiz ->
            upsertQuiz(quiz)
            deleteQuizFts(quiz.id)
        }
        quizFts.forEach { upsertQuizFts(it) }
        quizOutbox.forEach { insertOutbox(it) }
    }

    @Transaction
    suspend fun applySyncBatch(
        areas: List<AreaEntity>,
        nodes: List<LearningNodeEntity>,
        quizzes: List<QuizItemEntity>,
        questions: List<ReaderQuestionEntity>,
        captureSlips: List<CaptureSlipEntity>,
        attempts: List<ReviewAttemptEntity>,
        reviewStates: List<ReviewStateEntity>,
        nodeFts: List<NodeFtsEntity>,
        quizFts: List<QuizFtsEntity>,
        deletedNodeFtsIds: List<String>,
        deletedQuizFtsIds: List<String>
    ) {
        upsertAreas(areas)
        upsertNodes(nodes)
        upsertQuizzes(quizzes)
        upsertReaderQuestions(questions)
        upsertCaptureSlips(captureSlips)
        insertAttempts(attempts)
        upsertReviewStates(reviewStates)
        deletedNodeFtsIds.forEach { deleteNodeFts(it) }
        deletedQuizFtsIds.forEach { deleteQuizFts(it) }
        nodeFts.forEach { upsertNodeFts(it) }
        quizFts.forEach { upsertQuizFts(it) }
    }

    @Transaction
    suspend fun applySyncBatch(
        areas: List<AreaEntity>,
        nodes: List<LearningNodeEntity>,
        quizzes: List<QuizItemEntity>,
        states: List<ReviewStateEntity>,
        attempts: List<ReviewAttemptEntity>,
        questions: List<ReaderQuestionEntity>,
        captureSlips: List<CaptureSlipEntity>,
        biteCards: List<BiteCardEntity>,
        deletedNodeFtsIds: List<Int>,
        deletedQuizFtsIds: List<Int>,
        nodeFts: List<NodeFtsEntity>,
        quizFts: List<QuizFtsEntity>
    ) {
        upsertAreas(areas)
        upsertNodes(nodes)
        upsertQuizzes(quizzes)
        upsertReaderQuestions(questions)
        upsertCaptureSlips(captureSlips)
        upsertBiteCards(biteCards)
        insertAttempts(attempts)
        upsertReviewStates(reviewStates)
        deletedNodeFtsIds.forEach { deleteNodeFts(it) }
        deletedQuizFtsIds.forEach { deleteQuizFts(it) }
        nodeFts.forEach { upsertNodeFts(it) }
        quizFts.forEach { upsertQuizFts(it) }
    }

    @Query(
        """
        SELECT 'node' AS type, node_id AS id, title, snippet(node_fts, 3, '[', ']', '...', 12) AS snippet
        FROM node_fts
        WHERE node_fts MATCH :query
        """
    )
    suspend fun searchNodes(query: String): List<SearchResultEntity>

    @Query(
        """
        SELECT 'quiz' AS type, quiz_id AS id, prompt AS title, snippet(quiz_fts, 3, '[', ']', '...', 12) AS snippet
        FROM quiz_fts
        WHERE quiz_fts MATCH :query
        """
    )
    suspend fun searchQuizzes(query: String): List<SearchResultEntity>

    // ---- Daily Bite cards ----

    @Query("SELECT * FROM bite_cards WHERE status = 'active' ORDER BY updated_at DESC")
    fun observeBiteCards(): Flow<List<BiteCardEntity>>

    @Query("SELECT * FROM bite_cards WHERE status = 'active' ORDER BY updated_at DESC")
    suspend fun getBiteCards(): List<BiteCardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBiteCards(cards: List<BiteCardEntity>)

    @Query("DELETE FROM bite_cards")
    suspend fun deleteAllBiteCards()

    @Transaction
    suspend fun clearAll() {
        deleteAllAttempts()
        deleteAllReviewStates()
        deleteAllQuizzes()
        deleteAllReaderQuestions()
        deleteAllCaptureSlips()
        deleteAllBiteCards()
        deleteAllAreas()
        deleteAllNodes()
        deleteAllQuizFts()
        deleteAllNodeFts()
    }

    @Transaction
    suspend fun restoreBackup(
        areas: List<AreaEntity>,
        nodes: List<LearningNodeEntity>,
        quizzes: List<QuizItemEntity>,
        states: List<ReviewStateEntity>,
        attempts: List<ReviewAttemptEntity>,
        questions: List<ReaderQuestionEntity>,
        captureSlips: List<CaptureSlipEntity>,
        biteCards: List<BiteCardEntity>,
        nodeFts: List<NodeFtsEntity>,
        quizFts: List<QuizFtsEntity>
    ) {
        clearAll()
        deleteAllProcessedCommands()
        deleteAllOutboxItems()
        upsertAreas(areas)
        upsertNodes(nodes)
        upsertQuizzes(quizzes)
        upsertReviewStates(states)
        insertAttempts(attempts)
        upsertReaderQuestions(questions)
        upsertCaptureSlips(captureSlips)
        upsertBiteCards(biteCards)
        nodeFts.forEach { upsertNodeFts(it) }
        quizFts.forEach { upsertQuizFts(it) }
    }
}
