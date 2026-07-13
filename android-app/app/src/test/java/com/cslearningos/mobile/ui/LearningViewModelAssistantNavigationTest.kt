package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.LearningRepository
import com.cslearningos.mobile.feature.assistant.data.KnowledgeAssistantService
import com.cslearningos.mobile.feature.assistant.domain.KnowledgeAssistantChatMessage
import com.cslearningos.mobile.feature.assistant.ui.AssistantAppBridge
import com.cslearningos.mobile.feature.assistant.ui.AssistantCoordinator
import java.lang.reflect.Constructor
import java.lang.reflect.Proxy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningViewModelAssistantNavigationTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun showAssistantPreservesExistingAssistantConversation() = runTest {
        val (coordinator, stateFlow, viewModel) = buildHarness()

        viewModel.showAssistant()

        assertEquals(AppScreen.Assistant, stateFlow.value.screen)
        assertEquals(listOf("Explain paging", "Answer"), coordinator.state.value.messages.map { it.body })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun showAssistantFreshClearsExistingAssistantConversation() = runTest {
        val (coordinator, stateFlow, viewModel) = buildHarness()

        viewModel.showAssistantFresh()

        assertEquals(AppScreen.Assistant, stateFlow.value.screen)
        assertEquals(emptyList<String>(), coordinator.state.value.messages.map { it.body })
    }

    private fun allocateLearningViewModel(
        stateFlow: MutableStateFlow<LearningUiState>,
        assistantBridge: AssistantAppBridge
    ): LearningViewModel {
        val reflectionFactoryClass = Class.forName("sun.reflect.ReflectionFactory")
        val getReflectionFactory = reflectionFactoryClass.getDeclaredMethod("getReflectionFactory")
        val reflectionFactory = getReflectionFactory.invoke(null)
        val newConstructorForSerialization = reflectionFactoryClass.getDeclaredMethod(
            "newConstructorForSerialization",
            Class::class.java,
            Constructor::class.java
        )
        val objectConstructor = Any::class.java.getDeclaredConstructor()
        val serializationConstructor = newConstructorForSerialization.invoke(
            reflectionFactory,
            LearningViewModel::class.java,
            objectConstructor
        ) as Constructor<*>
        serializationConstructor.isAccessible = true
        val viewModel = serializationConstructor.newInstance() as LearningViewModel
        setField(viewModel, "_state", stateFlow)
        setField(viewModel, "assistantActions", assistantBridge)
        return viewModel
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun TestScope.buildHarness(): Triple<AssistantCoordinator, MutableStateFlow<LearningUiState>, LearningViewModel> {
        val coordinator = AssistantCoordinator(
            repository = LearningRepository(assistantDao()),
            service = ReplyingAssistantService("Answer"),
            string = { it.toString() },
            scope = this
        )
        coordinator.setInput("Explain paging")
        assertTrue(
            coordinator.send(
                AiProviderSettings(
                    baseUrl = "https://example.test",
                    apiKey = "key",
                    model = "model"
                )
            )
        )
        advanceUntilIdle()
        assertEquals(listOf("Explain paging", "Answer"), coordinator.state.value.messages.map { it.body })

        val stateFlow = MutableStateFlow(LearningUiState())
        val viewModel = allocateLearningViewModel(
            stateFlow = stateFlow,
            assistantBridge = AssistantAppBridge(
                coordinator = coordinator,
                currentSettings = { AiProviderSettings() },
                updateState = {},
                scope = this,
                onOpenNode = {},
                onOpenDailyReview = {},
                onShowAssistant = {}
            )
        )
        return Triple(coordinator, stateFlow, viewModel)
    }

    private fun assistantDao(): LearningDao =
        Proxy.newProxyInstance(
            LearningDao::class.java.classLoader,
            arrayOf(LearningDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "observeAreas" -> flowOf(
                    listOf(
                        AreaEntity(
                            id = "systems",
                            slug = "systems",
                            name = "Systems",
                            order = 10,
                            createdAt = 1L,
                            updatedAt = 1L,
                            deletedAt = null
                        )
                    )
                )

                "observeNodes" -> flowOf(emptyList<LearningNodeEntity>())
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

    private fun setField(target: Any, fieldName: String, value: Any) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
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
}
