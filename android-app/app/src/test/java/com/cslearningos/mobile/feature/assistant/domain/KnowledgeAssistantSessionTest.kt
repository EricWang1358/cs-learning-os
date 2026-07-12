package com.cslearningos.mobile.feature.assistant.domain

import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.LearningRepository
import com.cslearningos.mobile.data.SyncStatus
import com.cslearningos.mobile.feature.assistant.data.KnowledgeAssistantService
import com.cslearningos.mobile.ui.AiProviderSettings
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeAssistantSessionTest {
    @Test
    fun newNodePromptWithNoAreasAsksUserToCreateAnAreaBeforeDrafting() {
        val prompt = buildKnowledgeAssistantSystemPrompt(
            mode = AssistantRequestMode.Draft,
            context = emptyList(),
            areas = emptyList(),
            objectTarget = AssistantEditTarget.Node(
                id = null,
                revision = 0L,
                titleHint = "First note",
                markdown = "",
                areaId = null
            )
        )

        assertTrue(prompt.contains("ask the user to create an Area first"))
        assertTrue(prompt.contains("emit no draft"))
        assertTrue(prompt.contains("emit no directives"))
    }

    @Test
    fun quizPromptIncludesLinkedActiveNodeContextWithoutStoringItOnTheQuizTarget() = runTest {
        val node = LearningNodeEntity(
            id = "node-1",
            title = "Virtual Memory Page Faults",
            markdownBody = "# Virtual Memory\n\nA page fault traps into the OS.",
            createdAt = 1L,
            updatedAt = 2L,
            lastReadAt = null,
            revision = 3L,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            area = "systems",
            areaId = "systems",
            track = "memory"
        )
        val service = CapturingAssistantService()
        val session = KnowledgeAssistantSession(
            repository = LearningRepository(assistantDao(node)),
            service = service
        )

        session.streamReply(
            settings = AiProviderSettings(baseUrl = "https://example.test", apiKey = "key", model = "model"),
            mode = AssistantRequestMode.Draft,
            history = emptyList(),
            message = "Improve this quiz.",
            context = emptyList(),
            areas = emptyList(),
            objectTarget = AssistantEditTarget.Quiz(
                id = "quiz-1",
                revision = 5L,
                nodeId = node.id,
                prompt = "What is a page fault?",
                answer = "A trap into the OS.",
                explanation = "It occurs when the page is not resident."
            ),
            onDelta = {}
        )

        assertTrue(service.systemPrompt.contains("Linked active node context:"))
        assertTrue(service.systemPrompt.contains("Title: Virtual Memory Page Faults"))
        assertTrue(service.systemPrompt.contains("Current Area: systems"))
        assertTrue(service.systemPrompt.contains("# Virtual Memory\n\nA page fault traps into the OS."))
    }

    private fun assistantDao(node: LearningNodeEntity): LearningDao =
        Proxy.newProxyInstance(
            LearningDao::class.java.classLoader,
            arrayOf(LearningDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getNode" -> node.takeIf { args?.firstOrNull() == node.id }
                "getArea" -> AreaEntity(
                    id = "systems",
                    slug = "systems",
                    name = "Systems",
                    order = 1,
                    createdAt = 1L,
                    updatedAt = 1L,
                    deletedAt = null
                ).takeIf { args?.firstOrNull() == "systems" }

                "searchNodes", "searchQuizzes" -> emptyList<Any>()
                else -> when {
                    Flow::class.java.isAssignableFrom(method.returnType) -> flowOf(emptyList<Any>())
                    method.returnType == Boolean::class.javaPrimitiveType -> false
                    method.returnType == Int::class.javaPrimitiveType -> 0
                    method.returnType == Long::class.javaPrimitiveType -> 0L
                    else -> null
                }
            }
        } as LearningDao

    private class CapturingAssistantService : KnowledgeAssistantService {
        lateinit var systemPrompt: String

        override suspend fun streamReply(
            baseUrl: String,
            apiKey: String,
            model: String,
            systemPrompt: String,
            messages: List<KnowledgeAssistantChatMessage>,
            onDelta: suspend (String) -> Unit
        ) {
            this.systemPrompt = systemPrompt
        }
    }
}
