package com.cslearningos.mobile.feature.assistant.ui

import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.CaptureSlipStatus
import com.cslearningos.mobile.data.CaptureSlipType
import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.LearningRepository
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.QuizSource
import com.cslearningos.mobile.data.SyncStatus
import com.cslearningos.mobile.feature.assistant.data.AssistantConversationCodec
import com.cslearningos.mobile.feature.assistant.data.AssistantConversationEntity
import com.cslearningos.mobile.feature.assistant.data.KnowledgeAssistantService
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversation
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversationMessage
import com.cslearningos.mobile.feature.assistant.domain.AssistantConversationRole
import com.cslearningos.mobile.feature.assistant.domain.AssistantAgentInteraction
import com.cslearningos.mobile.feature.assistant.domain.KnowledgeAssistantChatMessage
import com.cslearningos.mobile.ui.AiProviderSettings
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantCoordinatorStateTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun objectNodeProposalCarriesCaptureSuggestionToFinalAssistantMessage() = runTest {
        val node = LearningNodeEntity(
            id = "node-1",
            title = "Graph traversal",
            markdownBody = "# Graph traversal\n\nOld body",
            createdAt = 1L,
            updatedAt = 2L,
            lastReadAt = null,
            revision = 3L,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            area = "algorithms",
            areaId = "algorithms",
            track = "graphs"
        )
        val service = ReplyingAssistantService(
            "<!-- cs-area: algorithms -->\n<!-- cs-capture: Compare BFS queue growth later. -->\n# Graph traversal\n\nBetter body."
        )
        val coordinator = AssistantCoordinator(
            repository = LearningRepository(assistantDao(node)),
            service = service,
            string = { it.toString() },
            scope = this
        )
        coordinator.reviseNode(node)

        val sent = coordinator.send(AiProviderSettings(baseUrl = "https://example.test", apiKey = "key", model = "model"))
        advanceUntilIdle()

        val reply = coordinator.state.value.messages.last()
        assertTrue(sent)
        assertTrue(reply.action is AssistantMessageAction.OpenEditableDraft)
        assertEquals("Compare BFS queue growth later.", reply.captureSuggestion)
    }

    @Test
    fun startingInterviewReviewClearsTypedEditTarget() = runTest {
        val coordinator = AssistantCoordinator(
            repository = LearningRepository(noopDao()),
            service = NoopAssistantService,
            string = { it.toString() },
            scope = backgroundScope
        )
        coordinator.reviseQuiz(
            QuizItemEntity(
                id = "quiz-1",
                nodeId = "node-1",
                prompt = "Prompt",
                answer = "Answer",
                explanation = "Explanation",
                source = QuizSource.manual,
                sourceAnchor = null,
                createdAt = 1L,
                updatedAt = 1L,
                revision = 2L,
                syncStatus = SyncStatus.clean,
                deletedAt = null,
                area = "systems",
                track = "memory"
            )
        )

        assertTrue(coordinator.state.value.editTarget != null)
        coordinator.startInterviewReview()

        assertNull(coordinator.state.value.editTarget)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun openingHistoryWhileStreamIsBusyCannotReplaceStateOrPersistIntoHistoryConversation() = runTest {
        val historyConversation = AssistantConversation(
            id = "history-b",
            messages = listOf(
                AssistantConversationMessage(
                    role = AssistantConversationRole.User,
                    body = "Open B"
                ),
                AssistantConversationMessage(
                    role = AssistantConversationRole.Assistant,
                    body = "Existing B reply"
                )
            )
        )
        val savedConversations = linkedMapOf("history-b" to historyConversation.toEntity(updatedAt = 1L))
        val service = HoldingAssistantService(reply = "Finished A")
        val coordinator = AssistantCoordinator(
            repository = LearningRepository(conversationDao(savedConversations)),
            service = service,
            string = { it.toString() },
            scope = this
        )
        advanceUntilIdle()

        coordinator.setInput("Stream A")
        assertTrue(coordinator.send(AiProviderSettings(baseUrl = "https://example.test", apiKey = "key", model = "model")))
        service.started.await()
        coordinator.openHistoryConversation("history-b")
        advanceUntilIdle()

        assertTrue(coordinator.state.value.isBusy)
        assertEquals("Stream A", coordinator.state.value.messages.first().body)

        service.release.complete(Unit)
        advanceUntilIdle()

        val finalState = coordinator.state.value
        val persistedHistory = AssistantConversationCodec.decode(savedConversations.getValue("history-b").messagesJson)
        assertEquals(listOf("Stream A", "Finished A"), finalState.messages.map { it.body })
        assertEquals(listOf("Open B", "Existing B reply"), persistedHistory.messages.map { it.body })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun captureDraftReplyClearsForcedCaptureEditContextAfterCreatingAction() = runTest {
        val slip = CaptureSlipEntity(
            id = "capture-1",
            body = "Forgot return statement",
            type = CaptureSlipType.mistake,
            topicHint = "Return statement",
            sourceLabel = "code review",
            status = CaptureSlipStatus.inbox,
            createdAt = 1L,
            updatedAt = 2L,
            revision = 3L,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            linkedNodeId = null
        )
        val service = ReplyingAssistantService(
            """
            <!-- cs-capture-body -->Remember to return the computed value.<!-- /cs-capture-body -->
            <!-- cs-capture-topic -->Return statement<!-- /cs-capture-topic -->
            <!-- cs-capture-source -->code review<!-- /cs-capture-source -->
            <!-- cs-capture-type: mistake -->
            """.trimIndent()
        )
        val coordinator = AssistantCoordinator(
            repository = LearningRepository(assistantDao(node = null)),
            service = service,
            string = { it.toString() },
            scope = this
        )

        coordinator.reviseCapture(slip)
        assertTrue(coordinator.send(AiProviderSettings(baseUrl = "https://example.test", apiKey = "key", model = "model")))
        advanceUntilIdle()

        val reply = coordinator.state.value.messages.last()
        assertTrue(reply.action is AssistantMessageAction.OpenEditableCaptureDraft)
        assertNull(coordinator.state.value.editTarget)
    }

    @Test
    fun reviseCaptureUsesFriendlyVisibleRequestText() = runTest {
        val slip = CaptureSlipEntity(
            id = "capture-1",
            body = "Forgot return statement",
            type = CaptureSlipType.mistake,
            topicHint = "Return statement",
            sourceLabel = "code review",
            status = CaptureSlipStatus.inbox,
            createdAt = 1L,
            updatedAt = 2L,
            revision = 3L,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            linkedNodeId = null
        )
        val coordinator = AssistantCoordinator(
            repository = LearningRepository(assistantDao(node = null)),
            service = NoopAssistantService,
            string = { it.toString() },
            scope = backgroundScope
        )

        coordinator.reviseCapture(slip)

        assertEquals("Improve this capture slip.", coordinator.state.value.input)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun newNodeDraftRequestShowsLocalConfirmationBeforeCallingModel() = runTest {
        val service = CountingAssistantService()
        val coordinator = AssistantCoordinator(
            repository = LearningRepository(assistantDao(node = null)),
            service = service,
            string = { it.toString() },
            scope = this
        )

        coordinator.setInput("create note about Codex workflow")
        assertTrue(coordinator.send(AiProviderSettings(baseUrl = "https://example.test", apiKey = "key", model = "model")))
        advanceUntilIdle()

        val reply = coordinator.state.value.messages.last()
        assertEquals(0, service.calls)
        assertTrue(reply.action is AssistantMessageAction.AgentInteraction)
        val interaction = (reply.action as AssistantMessageAction.AgentInteraction).interaction
        assertTrue(interaction is AssistantAgentInteraction.Confirm)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun structuredCaptureProtocolIsHiddenWhileStreaming() = runTest {
        val slip = CaptureSlipEntity(
            id = "capture-1",
            body = "Forgot return statement",
            type = CaptureSlipType.mistake,
            topicHint = "Return statement",
            sourceLabel = "code review",
            status = CaptureSlipStatus.inbox,
            createdAt = 1L,
            updatedAt = 2L,
            revision = 3L,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            linkedNodeId = null
        )
        val service = StreamingHoldingAssistantService(
            """
            <!-- cs-capture-body -->Remember to return the computed value.<!-- /cs-capture-body -->
            <!-- cs-capture-topic -->Return statement<!-- /cs-capture-topic -->
            <!-- cs-capture-source -->code review<!-- /cs-capture-source -->
            <!-- cs-capture-type: mistake -->
            """.trimIndent()
        )
        val coordinator = AssistantCoordinator(
            repository = LearningRepository(assistantDao(node = null)),
            service = service,
            string = { it.toString() },
            scope = this
        )

        coordinator.reviseCapture(slip)
        assertTrue(coordinator.send(AiProviderSettings(baseUrl = "https://example.test", apiKey = "key", model = "model")))
        service.started.await()

        val streamingReply = coordinator.state.value.messages.last()
        assertTrue(streamingReply.isStreaming)
        assertTrue("structured protocol leaked into UI", "cs-capture-body" !in streamingReply.body)

        service.release.complete(Unit)
        advanceUntilIdle()
    }

    private fun noopDao(): LearningDao =
        assistantDao(node = null)

    private fun assistantDao(node: LearningNodeEntity?): LearningDao =
        Proxy.newProxyInstance(
            LearningDao::class.java.classLoader,
            arrayOf(LearningDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "observeAreas" -> flowOf(
                    listOf(
                        AreaEntity(
                            id = "algorithms",
                            slug = "algorithms",
                            name = "Algorithms",
                            order = 10,
                            createdAt = 1L,
                            updatedAt = 1L,
                            deletedAt = null
                        )
                    )
                )

                "observeNodes" -> flowOf(listOfNotNull(node))
                "getNode" -> node.takeIf { args?.firstOrNull() == node?.id }
                "searchNodes", "searchQuizzes" -> emptyList<Any>()
                "latestAssistantConversation" -> null
                "upsertAssistantConversation" -> Unit
                else -> when {
                Flow::class.java.isAssignableFrom(method.returnType) -> flowOf(emptyList<Any>())
                method.returnType == Boolean::class.javaPrimitiveType -> false
                method.returnType == Int::class.javaPrimitiveType -> 0
                method.returnType == Long::class.javaPrimitiveType -> 0L
                else -> null
            }
            }
        } as LearningDao

    private fun conversationDao(savedConversations: MutableMap<String, AssistantConversationEntity>): LearningDao =
        Proxy.newProxyInstance(
            LearningDao::class.java.classLoader,
            arrayOf(LearningDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "observeAreas" -> flowOf(emptyList<AreaEntity>())
                "observeNodes" -> flowOf(emptyList<LearningNodeEntity>())
                "searchNodes", "searchQuizzes" -> emptyList<Any>()
                "latestAssistantConversation" -> null
                "recentAssistantConversations" -> savedConversations.values.toList()
                "getAssistantConversation" -> savedConversations[args?.firstOrNull() as? String]
                "deleteAssistantConversation" -> {
                    savedConversations.remove(args?.firstOrNull() as? String)
                    Unit
                }

                "upsertAssistantConversation" -> {
                    val entity = args?.firstOrNull() as AssistantConversationEntity
                    savedConversations[entity.id] = entity
                    Unit
                }

                else -> when {
                    Flow::class.java.isAssignableFrom(method.returnType) -> flowOf(emptyList<Any>())
                    method.returnType == Boolean::class.javaPrimitiveType -> false
                    method.returnType == Int::class.javaPrimitiveType -> 0
                    method.returnType == Long::class.javaPrimitiveType -> 0L
                    else -> null
                }
            }
        } as LearningDao

    private fun AssistantConversation.toEntity(updatedAt: Long): AssistantConversationEntity =
        AssistantConversationEntity(
            id = id,
            messagesJson = AssistantConversationCodec.encode(this),
            updatedAt = updatedAt
        )

    private object NoopAssistantService : KnowledgeAssistantService {
        override suspend fun streamReply(
            baseUrl: String,
            apiKey: String,
            model: String,
            systemPrompt: String,
            messages: List<KnowledgeAssistantChatMessage>,
            onDelta: suspend (String) -> Unit
        ) = Unit
    }

    private class ReplyingAssistantService(
        private val reply: String
    ) : KnowledgeAssistantService {
        override suspend fun streamReply(
            baseUrl: String,
            apiKey: String,
            model: String,
            systemPrompt: String,
            messages: List<KnowledgeAssistantChatMessage>,
            onDelta: suspend (String) -> Unit
        ) {
            onDelta(reply)
        }
    }

    private class CountingAssistantService : KnowledgeAssistantService {
        var calls = 0

        override suspend fun streamReply(
            baseUrl: String,
            apiKey: String,
            model: String,
            systemPrompt: String,
            messages: List<KnowledgeAssistantChatMessage>,
            onDelta: suspend (String) -> Unit
        ) {
            calls += 1
        }
    }

    private class HoldingAssistantService(
        private val reply: String
    ) : KnowledgeAssistantService {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun streamReply(
            baseUrl: String,
            apiKey: String,
            model: String,
            systemPrompt: String,
            messages: List<KnowledgeAssistantChatMessage>,
            onDelta: suspend (String) -> Unit
        ) {
            started.complete(Unit)
            release.await()
            onDelta(reply)
        }
    }

    private class StreamingHoldingAssistantService(
        private val reply: String
    ) : KnowledgeAssistantService {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun streamReply(
            baseUrl: String,
            apiKey: String,
            model: String,
            systemPrompt: String,
            messages: List<KnowledgeAssistantChatMessage>,
            onDelta: suspend (String) -> Unit
        ) {
            onDelta(reply)
            started.complete(Unit)
            release.await()
        }
    }
}
