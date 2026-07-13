package com.cslearningos.mobile.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.cslearningos.mobile.feature.assistant.data.AssistantConversationEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningRepositoryPolicyTest {
    @Test
    fun markReadOnlyUpdatesReadingTrace() {
        val node = LearningNodeEntity(
            id = "node-1",
            title = "Cache locality",
            markdownBody = "# Cache locality",
            createdAt = 100L,
            updatedAt = 200L,
            lastReadAt = null,
            revision = 7L,
            syncStatus = SyncStatus.clean,
            deletedAt = null
        )

        val updated = node.withReadTrace(now = 900L)

        assertEquals(900L, updated.lastReadAt)
        assertEquals(200L, updated.updatedAt)
        assertEquals(7L, updated.revision)
        assertEquals(SyncStatus.clean, updated.syncStatus)
        assertNull(updated.deletedAt)
    }

    @Test
    fun explicitMissingAreaCannotBeRecreatedWhileSavingANode() = runTest {
        val repository = LearningRepository(FakeLearningDao())

        val result = runCatching {
            repository.saveNode(
                id = null,
                title = "Area race",
                markdownBody = "# Area race",
                areaId = "deleted-area"
            )
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun savingMissingExplicitNodeIdFailsWithoutCreatingNode() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)

        val failure = runCatching {
            repository.saveNode(
                id = "missing-node",
                expectedRevision = null,
                title = "Must not create",
                markdownBody = "# Must not create",
                now = 2_000L
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(dao.nodes.isEmpty())
    }

    @Test
    fun savingNodeWithStaleExpectedRevisionFailsWithoutChangingNode() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        val original = LearningNodeEntity(
            id = "node-1",
            title = "Original node",
            markdownBody = "# Original node",
            createdAt = 1_000L,
            updatedAt = 1_000L,
            lastReadAt = null,
            revision = 4L,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            area = "systems",
            areaId = "systems",
            track = "memory"
        )
        dao.areas["systems"] = AreaEntity(
            id = "systems",
            slug = "systems",
            name = "Systems",
            order = 20,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            deletedAt = null
        )
        dao.nodes[original.id] = original

        val failure = runCatching {
            repository.saveNode(
                id = original.id,
                expectedRevision = 3L,
                title = "Updated node",
                markdownBody = "# Updated node",
                now = 2_000L
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(original, dao.nodes.getValue(original.id))
    }

    @Test
    fun savingTombstonedExplicitNodeFailsAndLeavesNodeTombstoned() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        val original = LearningNodeEntity(
            id = "node-1",
            title = "Deleted node",
            markdownBody = "# Deleted node",
            createdAt = 1_000L,
            updatedAt = 1_500L,
            lastReadAt = null,
            revision = 4L,
            syncStatus = SyncStatus.deleted,
            deletedAt = 1_500L,
            area = "systems",
            areaId = "systems",
            track = "memory"
        )
        dao.nodes[original.id] = original

        val failure = runCatching {
            repository.saveNode(
                id = original.id,
                expectedRevision = original.revision,
                title = "Resurrected node",
                markdownBody = "# Resurrected node",
                now = 2_000L
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(original, dao.nodes.getValue(original.id))
        assertEquals(1_500L, dao.nodes.getValue(original.id).deletedAt)
        assertEquals(SyncStatus.deleted, dao.nodes.getValue(original.id).syncStatus)
    }

    @Test
    fun backupRoundTripPreservesReaderQuestions() {
        val backup = LearningBackup(
            schemaVersion = BackupCodec.SchemaVersion,
            exportedAt = 1_000L,
            nodes = emptyList(),
            quizzes = emptyList(),
            reviewStates = emptyList(),
            attempts = emptyList(),
            readerQuestions = listOf(
                ReaderQuestionEntity(
                    id = "q-1",
                    nodeId = "node-1",
                    body = "Why does leaq not touch memory?",
                    createdAt = 700L,
                    resolvedAt = null,
                    syncStatus = SyncStatus.dirty,
                    deletedAt = null
                )
            )
        )

        val decoded = BackupCodec.decode(BackupCodec.encode(backup))

        assertEquals(1, decoded.readerQuestions.size)
        assertEquals("q-1", decoded.readerQuestions.single().id)
        assertEquals("node-1", decoded.readerQuestions.single().nodeId)
        assertEquals("Why does leaq not touch memory?", decoded.readerQuestions.single().body)
        assertNull(decoded.readerQuestions.single().resolvedAt)
    }

    @Test
    fun backupRoundTripPreservesCaptureSlips() {
        val backup = LearningBackup(
            schemaVersion = BackupCodec.SchemaVersion,
            exportedAt = 1_000L,
            areas = emptyList(),
            nodes = emptyList(),
            quizzes = emptyList(),
            reviewStates = emptyList(),
            attempts = emptyList(),
            readerQuestions = emptyList(),
            captureSlips = listOf(
                CaptureSlipEntity(
                    id = "slip-1",
                    body = "I want to learn virtual memory.",
                    type = CaptureSlipType.concept_seed,
                    topicHint = "Virtual memory",
                    sourceLabel = "video",
                    linkedNodeId = null,
                    status = CaptureSlipStatus.inbox,
                    createdAt = 700L,
                    updatedAt = 700L,
                    revision = 1L,
                    syncStatus = SyncStatus.dirty,
                    deletedAt = null
                )
            )
        )

        val decoded = BackupCodec.decode(BackupCodec.encode(backup))

        assertEquals(1, decoded.captureSlips.size)
        assertEquals("slip-1", decoded.captureSlips.single().id)
        assertEquals("I want to learn virtual memory.", decoded.captureSlips.single().body)
        assertEquals(CaptureSlipType.concept_seed, decoded.captureSlips.single().type)
        assertEquals(CaptureSlipStatus.inbox, decoded.captureSlips.single().status)
    }

    @Test
    fun saveNodeTombstonesMarkdownQuizWhenSourceBlockIsRemoved() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        val original = repository.saveNode(
            id = null,
            title = "Binary search",
            markdownBody = """
                # Binary search

                :::quiz id=mid-overflow
                question: Why avoid `(l + r) / 2`?
                answer: It can overflow before division.
                :::
            """.trimIndent(),
            now = 1_000L
        )

        repository.saveNode(
            id = original.id,
            title = original.title,
            markdownBody = "# Binary search\n\nUse `l + (r - l) / 2`.",
            now = 2_000L
        )

        val quiz = dao.quizzes.getValue("${original.id}:mid-overflow")
        assertEquals(2_000L, quiz.deletedAt)
        assertEquals(SyncStatus.deleted, quiz.syncStatus)
        assertTrue(dao.getActiveQuizzesForNode(original.id).isEmpty())
        assertTrue("Removed markdown quiz should leave the FTS index", dao.deletedQuizFtsIds.contains(quiz.id))
    }

    @Test
    fun saveNodeSyncsCollapsedAssistantQuizIntoReviewState() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        dao.areas["systems"] = AreaEntity(
            id = "systems",
            slug = "systems",
            name = "Systems",
            order = 1,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            deletedAt = null
        )

        val node = repository.saveNode(
            id = null,
            title = "Assistant quiz sync",
            markdownBody = """
                ## Review cards

                :::quizquestion: What creates the Review card? answer: Saving the node syncs Markdown quiz blocks. explanation: The repository creates both quiz_items and review_states.
                :::
            """.trimIndent(),
            areaId = "systems",
            now = 1_000L
        )

        val quiz = dao.getActiveQuizzesForNode(node.id).single()
        assertEquals("What creates the Review card?", quiz.prompt)
        assertEquals("Saving the node syncs Markdown quiz blocks.", quiz.answer)
        assertEquals("systems", quiz.area)
        assertEquals(1_000L, dao.reviewStates.getValue(quiz.id).dueAt)
    }

    @Test
    fun anonymousMarkdownQuizRemovalDoesNotMoveReviewIdentityToAnotherCard() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        val original = repository.saveNode(
            id = null,
            title = "Anonymous cards",
            markdownBody = """
                :::quiz
                question: First card?
                answer: First answer.
                :::

                :::quiz
                question: Second card?
                answer: Second answer.
                :::
            """.trimIndent(),
            now = 1_000L
        )
        val firstId = dao.quizzes.values.single { it.prompt == "First card?" }.id
        val secondId = dao.quizzes.values.single { it.prompt == "Second card?" }.id

        repository.saveNode(
            id = original.id,
            title = original.title,
            markdownBody = """
                :::quiz
                question: Second card?
                answer: Second answer.
                :::
            """.trimIndent(),
            now = 2_000L
        )

        assertEquals(2_000L, dao.quizzes.getValue(firstId).deletedAt)
        assertNull(dao.quizzes.getValue(secondId).deletedAt)
        assertEquals("Second card?", dao.quizzes.getValue(secondId).prompt)
    }

    @Test
    fun seedStarterContentUpsertsNodesQuizzesAndReviewState() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        val pack = StarterContentPackage(
            nodes = listOf(
                LearningNodeEntity(
                    id = "starter:node:algorithms/binary-search",
                    title = "Binary Search",
                    markdownBody = "# Binary Search",
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                    lastReadAt = null,
                    revision = 1L,
                    syncStatus = SyncStatus.clean,
                    deletedAt = null
                )
            ),
            quizzes = listOf(
                QuizItemEntity(
                    id = "starter:quiz:gdb",
                    nodeId = null,
                    prompt = "What does stepi do?",
                    answer = "One instruction.",
                    explanation = "",
                    source = QuizSource.manual,
                    sourceAnchor = "quizzes/gdb.md",
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                    revision = 1L,
                    syncStatus = SyncStatus.clean,
                    deletedAt = null
                )
            ),
            reviewStates = listOf(
                ReviewStateEntity(
                    quizId = "starter:quiz:gdb",
                    ease = 2.5,
                    intervalDays = 0,
                    dueAt = 1_000L,
                    lastResult = ReviewResult.again,
                    attemptCount = 0,
                    updatedAt = 1_000L
                )
            )
        )

        repository.seedStarterContent(pack)

        assertEquals("Binary Search", dao.nodes.getValue("starter:node:algorithms/binary-search").title)
        assertEquals("What does stepi do?", dao.quizzes.getValue("starter:quiz:gdb").prompt)
        assertEquals(1_000L, dao.reviewStates.getValue("starter:quiz:gdb").dueAt)
    }

    @Test
    fun convertedCaptureSlipLinksToSavedNodeAndLeavesInbox() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        val slip = CaptureSlipEntity(
            id = "slip-1",
            body = "What is a page fault?",
            type = CaptureSlipType.question,
            topicHint = "Virtual Memory",
            sourceLabel = null,
            linkedNodeId = null,
            status = CaptureSlipStatus.inbox,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            revision = 1L,
            syncStatus = SyncStatus.dirty,
            deletedAt = null
        )
        dao.captureSlips[slip.id] = slip

        repository.markCaptureSlipConverted(slipId = slip.id, nodeId = "node-1", now = 2_000L)

        val updated = dao.captureSlips.getValue(slip.id)
        assertEquals(CaptureSlipStatus.converted, updated.status)
        assertEquals("node-1", updated.linkedNodeId)
        assertEquals(2_000L, updated.updatedAt)
        assertEquals(2L, updated.revision)
    }

    @Test
    fun archiveRestoreAndPermanentDeleteCaptureSlipFollowVisibleArchiveLifecycle() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        val slip = CaptureSlipEntity(
            id = "slip-1",
            body = "What is a page fault?",
            type = CaptureSlipType.question,
            topicHint = "Virtual Memory",
            sourceLabel = null,
            linkedNodeId = null,
            status = CaptureSlipStatus.inbox,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null
        )
        dao.captureSlips[slip.id] = slip

        val archived = repository.archiveCaptureSlip(slip.id, now = 2_000L)
        assertEquals(CaptureSlipStatus.archived, archived?.status)
        assertEquals(2_000L, archived?.updatedAt)
        assertEquals(2L, archived?.revision)

        val restored = repository.restoreCaptureSlip(slip.id, now = 3_000L)
        assertEquals(CaptureSlipStatus.inbox, restored?.status)
        assertEquals(3_000L, restored?.updatedAt)
        assertEquals(3L, restored?.revision)

        repository.archiveCaptureSlip(slip.id, now = 4_000L)
        val deleted = repository.permanentlyDeleteCaptureSlip(slip.id, now = 5_000L)
        assertEquals(5_000L, deleted?.deletedAt)
        assertEquals(SyncStatus.deleted, deleted?.syncStatus)
        assertEquals(CaptureSlipStatus.archived, deleted?.status)
        assertEquals(5L, deleted?.revision)

        val afterRestoreAttempt = repository.restoreCaptureSlip(slip.id, now = 6_000L)
        assertEquals(null, afterRestoreAttempt)
        assertEquals(5_000L, dao.captureSlips.getValue(slip.id).deletedAt)
        assertEquals(SyncStatus.deleted, dao.captureSlips.getValue(slip.id).syncStatus)
    }

    @Test
    fun permanentDeleteCaptureSlipRequiresArchivedStatus() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        val slip = CaptureSlipEntity(
            id = "slip-1",
            body = "Keep me visible until explicitly archived",
            type = CaptureSlipType.concept_seed,
            topicHint = null,
            sourceLabel = null,
            linkedNodeId = null,
            status = CaptureSlipStatus.inbox,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null
        )
        dao.captureSlips[slip.id] = slip

        val deleted = repository.permanentlyDeleteCaptureSlip(slip.id, now = 2_000L)

        assertEquals(null, deleted)
        assertEquals(CaptureSlipStatus.inbox, dao.captureSlips.getValue(slip.id).status)
        assertEquals(null, dao.captureSlips.getValue(slip.id).deletedAt)
        assertEquals(1L, dao.captureSlips.getValue(slip.id).revision)
    }

    @Test
    fun savingExistingCaptureSlipPreservesWorkflowMetadataAndAdvancesRevision() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        val original = CaptureSlipEntity(
            id = "slip-1",
            body = "Original capture",
            type = CaptureSlipType.question,
            topicHint = "Virtual memory",
            sourceLabel = "lecture",
            linkedNodeId = "node-1",
            status = CaptureSlipStatus.ai_draft_ready,
            createdAt = 1_000L,
            updatedAt = 1_500L,
            revision = 4L,
            syncStatus = SyncStatus.clean,
            deletedAt = null
        )
        dao.captureSlips[original.id] = original

        val saved = repository.saveCaptureSlip(
            id = original.id,
            body = "  Updated capture  ",
            type = CaptureSlipType.concept_seed,
            topicHint = "  Memory management  ",
            sourceLabel = "  notes  ",
            now = 2_000L
        )

        assertEquals(1, dao.captureSlips.size)
        assertEquals(original.id, saved.id)
        assertEquals("Updated capture", saved.body)
        assertEquals(CaptureSlipType.concept_seed, saved.type)
        assertEquals("Memory management", saved.topicHint)
        assertEquals("notes", saved.sourceLabel)
        assertEquals(original.linkedNodeId, saved.linkedNodeId)
        assertEquals(original.status, saved.status)
        assertEquals(original.createdAt, saved.createdAt)
        assertEquals(2_000L, saved.updatedAt)
        assertEquals(5L, saved.revision)
        assertEquals(SyncStatus.dirty, saved.syncStatus)
    }

    @Test
    fun savingMissingExplicitCaptureSlipIdFailsWithoutCreatingASlip() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)

        val failure = runCatching {
            repository.saveCaptureSlip(
                id = "missing-slip",
                body = "Capture that cannot be updated",
                type = CaptureSlipType.question,
                topicHint = null,
                sourceLabel = null,
                now = 2_000L
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(dao.captureSlips.isEmpty())
    }

    @Test
    fun savingCaptureSlipWithStaleExpectedRevisionFailsWithoutChangingSlip() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        val original = CaptureSlipEntity(
            id = "slip-1",
            body = "Original capture",
            type = CaptureSlipType.question,
            topicHint = null,
            sourceLabel = null,
            linkedNodeId = null,
            status = CaptureSlipStatus.inbox,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            revision = 4L,
            syncStatus = SyncStatus.clean,
            deletedAt = null
        )
        dao.captureSlips[original.id] = original

        val failure = runCatching {
            repository.saveCaptureSlip(
                id = original.id,
                expectedRevision = 3L,
                body = "Updated capture",
                type = CaptureSlipType.question,
                topicHint = null,
                sourceLabel = null,
                now = 2_000L
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(original, dao.captureSlips.getValue(original.id))
    }

    @Test
    fun savingTombstonedExplicitCaptureSlipFailsAndLeavesSlipUnchanged() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        val original = CaptureSlipEntity(
            id = "slip-1",
            body = "Deleted capture",
            type = CaptureSlipType.question,
            topicHint = "Virtual memory",
            sourceLabel = "lecture",
            linkedNodeId = "node-1",
            status = CaptureSlipStatus.archived,
            createdAt = 1_000L,
            updatedAt = 1_500L,
            revision = 4L,
            syncStatus = SyncStatus.deleted,
            deletedAt = 1_500L
        )
        dao.captureSlips[original.id] = original

        val failure = runCatching {
            repository.saveCaptureSlip(
                id = original.id,
                expectedRevision = original.revision,
                body = "Resurrected capture",
                type = CaptureSlipType.mistake,
                topicHint = null,
                sourceLabel = null,
                now = 2_000L
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(original, dao.captureSlips.getValue(original.id))
    }

    @Test
    fun savingExistingQuizForItsOriginalNodePreservesThatNodesAreaAndTrack() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        dao.nodes["node-b"] = LearningNodeEntity(
            id = "node-b",
            title = "Node B",
            markdownBody = "# Node B",
            createdAt = 1L,
            updatedAt = 1L,
            lastReadAt = null,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            area = "algorithms",
            track = "trees"
        )
        dao.quizzes["quiz-b"] = QuizItemEntity(
            id = "quiz-b",
            nodeId = "node-b",
            prompt = "Original prompt",
            answer = "Original answer",
            explanation = "",
            source = QuizSource.manual,
            sourceAnchor = null,
            createdAt = 1L,
            updatedAt = 1L,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            area = "algorithms",
            track = "trees"
        )

        val saved = repository.saveManualQuiz(
            id = "quiz-b",
            nodeId = "node-b",
            prompt = "Updated prompt",
            answer = "Updated answer",
            explanation = "Updated explanation",
            now = 2L
        )

        assertEquals("node-b", saved.nodeId)
        assertEquals("algorithms", saved.area)
        assertEquals("trees", saved.track)
    }

    @Test
    fun savingStandaloneQuizWithExplicitAreaAddsItToThatReviewAreaAndCreatesDueState() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        dao.areas["systems"] = AreaEntity(
            id = "systems",
            slug = "systems",
            name = "Systems",
            order = 10,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            deletedAt = null
        )

        val saved = repository.saveManualQuiz(
            nodeId = null,
            areaId = "systems",
            prompt = "What does paging translate?",
            answer = "Virtual page numbers to physical frames.",
            explanation = "This is why virtual memory can isolate processes.",
            now = 2_000L
        )

        assertNull(saved.nodeId)
        assertEquals("systems", saved.area)
        assertEquals("general", saved.track)
        assertEquals(2_000L, dao.reviewStates.getValue(saved.id).dueAt)
    }

    @Test
    fun savingStandaloneQuizWithMissingExplicitAreaFailsWithoutCreatingQuiz() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)

        val failure = runCatching {
            repository.saveManualQuiz(
                nodeId = null,
                areaId = "missing-area",
                prompt = "Prompt",
                answer = "Answer",
                explanation = "",
                now = 2_000L
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(dao.quizzes.isEmpty())
        assertTrue(dao.reviewStates.isEmpty())
    }

    @Test
    fun savingMissingExplicitQuizIdFailsWithoutCreatingQuiz() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)

        val failure = runCatching {
            repository.saveManualQuiz(
                id = "missing-quiz",
                expectedRevision = 1L,
                nodeId = null,
                prompt = "Prompt",
                answer = "Answer",
                explanation = "",
                now = 2_000L
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(dao.quizzes.isEmpty())
    }

    @Test
    fun savingQuizWithStaleExpectedRevisionPreservesReviewStateAndQuiz() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        val original = QuizItemEntity(
            id = "quiz-1",
            nodeId = "node-1",
            prompt = "Original prompt",
            answer = "Original answer",
            explanation = "Original explanation",
            source = QuizSource.manual,
            sourceAnchor = null,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            revision = 4L,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            area = "systems",
            track = "memory"
        )
        val reviewState = ReviewStateEntity(
            quizId = original.id,
            ease = 2.6,
            intervalDays = 3,
            dueAt = 5_000L,
            lastResult = ReviewResult.good,
            attemptCount = 2,
            updatedAt = 4_000L
        )
        dao.quizzes[original.id] = original
        dao.reviewStates[reviewState.quizId] = reviewState

        val failure = runCatching {
            repository.saveManualQuiz(
                id = original.id,
                expectedRevision = 3L,
                nodeId = "node-1",
                prompt = "Updated prompt",
                answer = "Updated answer",
                explanation = "Updated explanation",
                now = 2_000L
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(original, dao.quizzes.getValue(original.id))
        assertEquals(reviewState, dao.reviewStates.getValue(reviewState.quizId))
    }

    @Test
    fun savingTombstonedExplicitQuizFailsAndPreservesReviewStateAndQuiz() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        val original = QuizItemEntity(
            id = "quiz-1",
            nodeId = "node-1",
            prompt = "Deleted prompt",
            answer = "Deleted answer",
            explanation = "Deleted explanation",
            source = QuizSource.manual,
            sourceAnchor = null,
            createdAt = 1_000L,
            updatedAt = 1_500L,
            revision = 4L,
            syncStatus = SyncStatus.deleted,
            deletedAt = 1_500L,
            area = "systems",
            track = "memory"
        )
        val reviewState = ReviewStateEntity(
            quizId = original.id,
            ease = 2.6,
            intervalDays = 3,
            dueAt = 5_000L,
            lastResult = ReviewResult.good,
            attemptCount = 2,
            updatedAt = 4_000L
        )
        dao.quizzes[original.id] = original
        dao.reviewStates[reviewState.quizId] = reviewState

        val failure = runCatching {
            repository.saveManualQuiz(
                id = original.id,
                expectedRevision = original.revision,
                nodeId = "node-1",
                prompt = "Resurrected prompt",
                answer = "Resurrected answer",
                explanation = "Resurrected explanation",
                now = 2_000L
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(original, dao.quizzes.getValue(original.id))
        assertEquals(reviewState, dao.reviewStates.getValue(reviewState.quizId))
    }

    @Test
    fun compatibilityFacadeAnswerQuizStillSchedulesReviewProgress() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        dao.reviewStates["quiz-1"] = ReviewStateEntity(
            quizId = "quiz-1",
            ease = 2.5,
            intervalDays = 0,
            dueAt = 1_000L,
            lastResult = ReviewResult.again,
            attemptCount = 0,
            updatedAt = 1_000L
        )

        repository.answerQuiz("quiz-1", rating = com.cslearningos.mobile.domain.ReviewRating.Good, now = 2_000L)

        val updated = dao.reviewStates.getValue("quiz-1")
        assertEquals(2.6, updated.ease, 0.0)
        assertEquals(1, updated.intervalDays)
        assertEquals(1, updated.attemptCount)
        assertEquals(ReviewResult.good, updated.lastResult)
        assertEquals(1, dao.reviewAttempts.size)
        assertEquals("quiz-1", dao.reviewAttempts.values.single().quizId)
    }

    @Test
    fun moveRestoreAndPermanentDeleteNodeFollowTrashbinLifecycle() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        val node = LearningNodeEntity(
            id = "node-1",
            title = "Virtual Memory",
            markdownBody = "# Virtual Memory",
            createdAt = 1_000L,
            updatedAt = 1_000L,
            lastReadAt = null,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            area = "cs-fundamentals",
            track = "memory",
            order = 40,
            summary = "Address translation",
            visibility = "support",
            isStarter = false
        )
        dao.nodes[node.id] = node
        dao.quizzes["quiz-1"] = QuizItemEntity(
            id = "quiz-1",
            nodeId = node.id,
            prompt = "What is a page table?",
            answer = "A mapping structure.",
            explanation = "",
            source = QuizSource.manual,
            sourceAnchor = null,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            area = "cs-fundamentals",
            track = "memory",
            visibility = "practice"
        )
        dao.reviewStates["quiz-1"] = ReviewStateEntity(
            quizId = "quiz-1",
            ease = 2.5,
            intervalDays = 0,
            dueAt = 1_000L,
            lastResult = ReviewResult.again,
            attemptCount = 0,
            updatedAt = 1_000L
        )
        dao.reviewAttempts["attempt-1"] = ReviewAttemptEntity(
            id = "attempt-1",
            quizId = "quiz-1",
            result = ReviewResult.hard,
            answeredAt = 1_500L,
            scheduledDueAt = 2_000L
        )

        repository.moveNodeToTrash(node.id, now = 2_000L)
        assertEquals("trash", dao.nodes.getValue(node.id).visibility)
        assertEquals("trash", dao.quizzes.getValue("quiz-1").visibility)
        assertTrue(dao.deletedNodeFtsIds.contains(node.id))
        assertTrue(dao.deletedQuizFtsIds.contains("quiz-1"))

        repository.restoreNodeFromTrash(node.id, now = 3_000L)
        assertEquals("support", dao.nodes.getValue(node.id).visibility)
        assertEquals("practice", dao.quizzes.getValue("quiz-1").visibility)
        assertEquals(3_000L, dao.nodes.getValue(node.id).updatedAt)

        repository.permanentlyDeleteNode(node.id, now = 4_000L)
        assertEquals(4_000L, dao.nodes.getValue(node.id).deletedAt)
        assertEquals(SyncStatus.deleted, dao.nodes.getValue(node.id).syncStatus)
        assertTrue("Permanent delete should remove orphaned review state", "quiz-1" !in dao.reviewStates)
        assertTrue("Permanent delete should remove orphaned review attempts", dao.reviewAttempts.values.none { it.quizId == "quiz-1" })
    }

    @Test
    fun createRenameMoveCheckAndDeleteEmptyAreaFollowFolderRules() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        dao.areas["systems"] = AreaEntity(
            id = "systems",
            slug = "systems",
            name = "Systems",
            order = 20,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            deletedAt = null
        )
        dao.nodes["node-1"] = LearningNodeEntity(
            id = "node-1",
            title = "Paging",
            markdownBody = "# Paging",
            createdAt = 1_000L,
            updatedAt = 1_000L,
            lastReadAt = null,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            area = "systems",
            areaId = "systems",
            track = "virtual-memory",
            order = 20,
            summary = "Summary",
            visibility = "support",
            isStarter = false
        )

        val created = repository.createArea("Compilers", now = 2_000L)
        repository.renameArea(created.id, "Compiler Lab", now = 3_000L)
        repository.moveNodeToArea(nodeId = "node-1", targetAreaId = created.id, now = 4_000L)
        repository.toggleNodeChecked(nodeId = "node-1", now = 5_000L)

        assertEquals("compilers", created.id)
        assertEquals("Compiler Lab", dao.areas.getValue(created.id).name)
        assertEquals(created.id, dao.nodes.getValue("node-1").areaId)
        assertEquals(created.slug, dao.nodes.getValue("node-1").area)
        assertTrue(dao.nodes.getValue("node-1").isChecked)
        assertEquals(false, repository.deleteAreaIfEmpty(created.id, now = 6_000L))

        repository.moveNodeToArea(nodeId = "node-1", targetAreaId = "systems", now = 7_000L)

        assertEquals(true, repository.deleteAreaIfEmpty(created.id, now = 8_000L))
        assertEquals(8_000L, dao.areas.getValue(created.id).deletedAt)
    }

    @Test
    fun restoreNodeFromTrashRevivesDeletedAreaInsteadOfLeavingNodeOrphaned() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        dao.areas["systems"] = AreaEntity(
            id = "systems",
            slug = "systems",
            name = "Systems",
            order = 20,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            deletedAt = 2_000L
        )
        dao.nodes["node-1"] = LearningNodeEntity(
            id = "node-1",
            title = "Paging",
            markdownBody = "# Paging",
            createdAt = 1_000L,
            updatedAt = 1_000L,
            lastReadAt = null,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            area = "systems",
            areaId = "systems",
            track = "virtual-memory",
            order = 20,
            summary = "Summary",
            visibility = "trash",
            isStarter = false
        )

        repository.restoreNodeFromTrash("node-1", now = 3_000L)

        assertNull(dao.areas.getValue("systems").deletedAt)
        assertEquals("support", dao.nodes.getValue("node-1").visibility)
    }

    @Test
    fun deleteAreaIfEmptyTreatsTrashNodesAsStillOccupyingThatArea() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        dao.areas["systems"] = AreaEntity(
            id = "systems",
            slug = "systems",
            name = "Systems",
            order = 20,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            deletedAt = null
        )
        dao.nodes["node-1"] = LearningNodeEntity(
            id = "node-1",
            title = "Paging",
            markdownBody = "# Paging",
            createdAt = 1_000L,
            updatedAt = 1_000L,
            lastReadAt = null,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            area = "systems",
            areaId = "systems",
            track = "virtual-memory",
            order = 20,
            summary = "Summary",
            visibility = "trash",
            isStarter = false
        )

        val deleted = repository.deleteAreaIfEmpty("systems", now = 2_000L)

        assertEquals(false, deleted)
        assertNull(dao.areas.getValue("systems").deletedAt)
    }

    @Test
    fun clearStarterContentDeletesOnlyStarterRecords() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        dao.nodes["starter"] = LearningNodeEntity(
            id = "starter",
            title = "Starter",
            markdownBody = "# Starter",
            createdAt = 1_000L,
            updatedAt = 1_000L,
            lastReadAt = null,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            isStarter = true
        )
        dao.nodes["user"] = dao.nodes.getValue("starter").copy(id = "user", title = "User", isStarter = false)
        dao.quizzes["starter-quiz"] = QuizItemEntity(
            id = "starter-quiz",
            nodeId = null,
            prompt = "Starter?",
            answer = "Starter answer.",
            explanation = "",
            source = QuizSource.manual,
            sourceAnchor = null,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            isStarter = true
        )
        dao.reviewStates["starter-quiz"] = ReviewStateEntity(
            quizId = "starter-quiz",
            ease = 2.5,
            intervalDays = 0,
            dueAt = 1_000L,
            lastResult = ReviewResult.again,
            attemptCount = 0,
            updatedAt = 1_000L
        )
        dao.reviewAttempts["starter-attempt"] = ReviewAttemptEntity(
            id = "starter-attempt",
            quizId = "starter-quiz",
            result = ReviewResult.good,
            answeredAt = 1_500L,
            scheduledDueAt = 3_000L
        )

        repository.clearStarterContent(now = 2_000L)

        assertEquals(2_000L, dao.nodes.getValue("starter").deletedAt)
        assertNull(dao.nodes.getValue("user").deletedAt)
        assertEquals(2_000L, dao.quizzes.getValue("starter-quiz").deletedAt)
        assertTrue("Starter review state should not remain in backup data", "starter-quiz" !in dao.reviewStates)
        assertTrue("Starter review attempts should not remain in backup data", dao.reviewAttempts.values.none { it.quizId == "starter-quiz" })
    }

    @Test
    fun readableMarkdownExportIncludesActiveLearningArtifacts() = runTest {
        val dao = FakeLearningDao()
        val repository = LearningRepository(dao)
        dao.areas["cs-fundamentals"] = AreaEntity(
            id = "cs-fundamentals",
            slug = "cs-fundamentals",
            name = "Operating Systems",
            order = 20,
            createdAt = 1_000L,
            updatedAt = 2_500L,
            deletedAt = null
        )
        dao.nodes["node-1"] = LearningNodeEntity(
            id = "node-1",
            title = "Virtual Memory",
            markdownBody = "# Virtual Memory\n\n## Core Idea\n\nAddress translation maps virtual addresses.",
            createdAt = 1_000L,
            updatedAt = 2_000L,
            lastReadAt = 3_000L,
            revision = 2L,
            syncStatus = SyncStatus.dirty,
            deletedAt = null,
            area = "cs-fundamentals",
            track = "memory",
            order = 40,
            summary = "Address translation",
            visibility = "support",
            isStarter = false
        )
        dao.readerQuestions["rq-1"] = ReaderQuestionEntity(
            id = "rq-1",
            nodeId = "node-1",
            body = "Why does a TLB miss walk the page table?",
            createdAt = 3_100L,
            resolvedAt = null,
            syncStatus = SyncStatus.dirty,
            deletedAt = null
        )
        dao.captureSlips["slip-1"] = CaptureSlipEntity(
            id = "slip-1",
            body = "Video said page fault but I confused it with TLB miss.",
            type = CaptureSlipType.mistake,
            topicHint = "Virtual Memory",
            sourceLabel = "lecture",
            linkedNodeId = null,
            status = CaptureSlipStatus.inbox,
            createdAt = 3_200L,
            updatedAt = 3_200L,
            revision = 1L,
            syncStatus = SyncStatus.dirty,
            deletedAt = null
        )
        dao.quizzes["quiz-1"] = QuizItemEntity(
            id = "quiz-1",
            nodeId = "node-1",
            prompt = "What does a page table map?",
            answer = "Virtual pages to physical frames.",
            explanation = "The MMU uses the page table after address translation lookup misses cached entries.",
            source = QuizSource.manual,
            sourceAnchor = null,
            createdAt = 3_300L,
            updatedAt = 3_300L,
            revision = 1L,
            syncStatus = SyncStatus.dirty,
            deletedAt = null,
            area = "cs-fundamentals",
            track = "memory"
        )

        val exported = repository.exportReadableMarkdown(now = 4_000L)

        assertTrue(exported.startsWith("# CS Learning OS Markdown Export"))
        assertTrue(exported.contains("## Virtual Memory"))
        assertTrue(exported.contains("Area: Operating Systems / Track: memory / Order: 40"))
        assertTrue(exported.contains("Why does a TLB miss walk the page table?"))
        assertTrue(exported.contains("Video said page fault"))
        assertTrue(exported.contains("What does a page table map?"))
        assertTrue(exported.contains("Virtual pages to physical frames."))
    }
}

private class FakeLearningDao : LearningDao {
    val areas = linkedMapOf<String, AreaEntity>()
    val nodes = linkedMapOf<String, LearningNodeEntity>()
    val quizzes = linkedMapOf<String, QuizItemEntity>()
    val reviewStates = linkedMapOf<String, ReviewStateEntity>()
    val reviewAttempts = linkedMapOf<String, ReviewAttemptEntity>()
    val readerQuestions = linkedMapOf<String, ReaderQuestionEntity>()
    val captureSlips = linkedMapOf<String, CaptureSlipEntity>()
    val processedCommands = linkedMapOf<String, ProcessedCommandEntity>()
    val outbox = linkedMapOf<String, ReplicationOutboxEntity>()
    val deletedNodeFtsIds = mutableListOf<String>()
    val deletedQuizFtsIds = mutableListOf<String>()

    override fun observeAreas(): Flow<List<AreaEntity>> =
        flowOf(areas.values.filter { it.deletedAt == null }.sortedBy { it.order })
    override fun observeNodes(): Flow<List<LearningNodeEntity>> = flowOf(nodes.values.toList())
    override fun observeTrashNodes(): Flow<List<LearningNodeEntity>> =
        flowOf(nodes.values.filter { it.visibility == "trash" && it.deletedAt == null })
    override fun observeQuizzes(): Flow<List<QuizItemEntity>> = flowOf(quizzes.values.toList())
    override fun observeOpenReaderQuestions(): Flow<List<ReaderQuestionEntity>> =
        flowOf(readerQuestions.values.filter { it.deletedAt == null && it.resolvedAt == null })
    override fun observeInboxCaptureSlips(): Flow<List<CaptureSlipEntity>> =
        flowOf(
            captureSlips.values.filter {
                it.deletedAt == null && it.status in setOf(
                    CaptureSlipStatus.inbox,
                    CaptureSlipStatus.ai_queued,
                    CaptureSlipStatus.ai_drafting,
                    CaptureSlipStatus.ai_draft_ready
                )
            }
        )
    override fun observeArchivedCaptureSlips(): Flow<List<CaptureSlipEntity>> =
        flowOf(captureSlips.values.filter { it.deletedAt == null && it.status == CaptureSlipStatus.archived })
    override fun observeDueQuizzes(now: Long): Flow<List<QuizItemEntity>> = flowOf(emptyList())
    override suspend fun getArea(id: String): AreaEntity? = areas[id]?.takeIf { it.deletedAt == null }
    override suspend fun getAreaBySlug(slug: String): AreaEntity? = areas.values.firstOrNull { it.slug == slug && it.deletedAt == null }
    override suspend fun getNode(id: String): LearningNodeEntity? = nodes[id]
    override suspend fun getQuiz(id: String): QuizItemEntity? = quizzes[id]
    override suspend fun getReaderQuestion(id: String): ReaderQuestionEntity? = readerQuestions[id]
    override suspend fun getCaptureSlip(id: String): CaptureSlipEntity? = captureSlips[id]
    override suspend fun getActiveQuizzesForNode(nodeId: String): List<QuizItemEntity> =
        quizzes.values.filter { it.nodeId == nodeId && it.deletedAt == null }
    override suspend fun getActiveReaderQuestionsForNode(nodeId: String): List<ReaderQuestionEntity> =
        readerQuestions.values.filter { it.nodeId == nodeId && it.deletedAt == null }
    override suspend fun getReviewState(quizId: String): ReviewStateEntity? = reviewStates[quizId]
    override suspend fun getAllAreas(): List<AreaEntity> = areas.values.toList()
    override suspend fun getAllNodes(): List<LearningNodeEntity> = nodes.values.toList()
    override suspend fun getStarterNodes(): List<LearningNodeEntity> =
        nodes.values.filter { it.isStarter && it.deletedAt == null }
    override suspend fun getAllQuizzes(): List<QuizItemEntity> = quizzes.values.toList()
    override suspend fun getStarterQuizzes(): List<QuizItemEntity> =
        quizzes.values.filter { it.isStarter && it.deletedAt == null }
    override suspend fun getAllReviewStates(): List<ReviewStateEntity> = reviewStates.values.toList()
    override suspend fun getAllAttempts(): List<ReviewAttemptEntity> = reviewAttempts.values.toList()
    override suspend fun getAllReaderQuestions(): List<ReaderQuestionEntity> = readerQuestions.values.toList()
    override suspend fun getAllCaptureSlips(): List<CaptureSlipEntity> = captureSlips.values.toList()
    override suspend fun latestAssistantConversation(): AssistantConversationEntity? = null
    override suspend fun recentAssistantConversations(limit: Int): List<AssistantConversationEntity> = emptyList()
    override suspend fun getAssistantConversation(id: String): AssistantConversationEntity? = null
    override suspend fun deleteAssistantConversation(id: String) = Unit
    override suspend fun countActiveNodesInArea(areaId: String): Int =
        nodes.values.count { it.areaId == areaId && it.deletedAt == null }
    override suspend fun getProcessedCommand(commandId: String): ProcessedCommandEntity? =
        processedCommands[commandId]
    override suspend fun insertProcessedCommand(command: ProcessedCommandEntity) {
        check(command.commandId !in processedCommands)
        processedCommands[command.commandId] = command
    }
    override suspend fun insertOutbox(item: ReplicationOutboxEntity) {
        check(item.changeId !in outbox)
        check(outbox.values.none { it.commandId == item.commandId })
        outbox[item.changeId] = item
    }
    override suspend fun getOutboxForCommand(commandId: String): ReplicationOutboxEntity? =
        outbox.values.firstOrNull { it.commandId == commandId }
    override suspend fun upsertArea(area: AreaEntity) {
        areas[area.id] = area
    }
    override suspend fun upsertNode(node: LearningNodeEntity) {
        nodes[node.id] = node
    }
    override suspend fun upsertQuiz(quiz: QuizItemEntity) {
        quizzes[quiz.id] = quiz
    }
    override suspend fun upsertReaderQuestion(question: ReaderQuestionEntity) {
        readerQuestions[question.id] = question
    }
    override suspend fun upsertCaptureSlip(slip: CaptureSlipEntity) {
        captureSlips[slip.id] = slip
    }
    override suspend fun upsertAssistantConversation(conversation: AssistantConversationEntity) = Unit
    override suspend fun upsertReviewState(state: ReviewStateEntity) {
        reviewStates[state.quizId] = state
    }
    override suspend fun insertAttempt(attempt: ReviewAttemptEntity) {
        reviewAttempts[attempt.id] = attempt
    }
    override suspend fun upsertAreas(areas: List<AreaEntity>) {
        areas.forEach { this.areas[it.id] = it }
    }
    override suspend fun deleteAllAreas() = unsupported()
    override suspend fun upsertNodes(nodes: List<LearningNodeEntity>) {
        nodes.forEach { this.nodes[it.id] = it }
    }
    override suspend fun upsertQuizzes(quizzes: List<QuizItemEntity>) {
        quizzes.forEach { this.quizzes[it.id] = it }
    }
    override suspend fun upsertReviewStates(states: List<ReviewStateEntity>) {
        states.forEach { this.reviewStates[it.quizId] = it }
    }
    override suspend fun insertAttempts(attempts: List<ReviewAttemptEntity>) {
        attempts.forEach { reviewAttempts[it.id] = it }
    }
    override suspend fun upsertReaderQuestions(questions: List<ReaderQuestionEntity>) {
        questions.forEach { readerQuestions[it.id] = it }
    }
    override suspend fun upsertCaptureSlips(slips: List<CaptureSlipEntity>) {
        slips.forEach { captureSlips[it.id] = it }
    }
    override suspend fun upsertNodeFts(entity: NodeFtsEntity) = Unit
    override suspend fun upsertQuizFts(entity: QuizFtsEntity) = Unit
    override suspend fun deleteNodeFts(nodeId: String) {
        deletedNodeFtsIds += nodeId
    }
    override suspend fun deleteQuizFts(quizId: String) {
        deletedQuizFtsIds += quizId
    }
    override suspend fun deleteAllNodes() = unsupported()
    override suspend fun deleteAllQuizzes() = unsupported()
    override suspend fun deleteAllReviewStates() = unsupported()
    override suspend fun deleteAllAttempts() = unsupported()
    override suspend fun deleteReviewStateForQuiz(quizId: String) {
        reviewStates.remove(quizId)
    }
    override suspend fun deleteReviewAttemptsForQuiz(quizId: String) {
        reviewAttempts.entries.removeIf { it.value.quizId == quizId }
    }
    override suspend fun deleteAllReaderQuestions() = unsupported()
    override suspend fun deleteAllCaptureSlips() = unsupported()
    override suspend fun deleteAllNodeFts() = unsupported()
    override suspend fun deleteAllQuizFts() = unsupported()
    override suspend fun searchNodes(query: String): List<SearchResultEntity> = emptyList()
    override suspend fun searchQuizzes(query: String): List<SearchResultEntity> = emptyList()

    private fun unsupported(): Nothing = error("FakeLearningDao method is outside this test scope")
}
