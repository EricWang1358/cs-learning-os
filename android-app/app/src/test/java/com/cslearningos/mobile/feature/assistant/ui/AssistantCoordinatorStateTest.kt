package com.cslearningos.mobile.feature.assistant.ui

import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.LearningRepository
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.QuizSource
import com.cslearningos.mobile.data.SyncStatus
import com.cslearningos.mobile.feature.assistant.data.KnowledgeAssistantService
import com.cslearningos.mobile.ui.AiProviderSettings
import java.lang.reflect.Proxy
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

    private object NoopAssistantService : KnowledgeAssistantService {
        override suspend fun streamReply(
            baseUrl: String,
            apiKey: String,
            model: String,
            systemPrompt: String,
            userPrompt: String,
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
            userPrompt: String,
            onDelta: suspend (String) -> Unit
        ) {
            onDelta(reply)
        }
    }
}
