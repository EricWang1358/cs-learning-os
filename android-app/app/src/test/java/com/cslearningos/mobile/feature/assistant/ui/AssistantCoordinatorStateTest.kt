package com.cslearningos.mobile.feature.assistant.ui

import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.data.LearningRepository
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.QuizSource
import com.cslearningos.mobile.data.SyncStatus
import com.cslearningos.mobile.feature.assistant.data.KnowledgeAssistantService
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantCoordinatorStateTest {
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
        Proxy.newProxyInstance(
            LearningDao::class.java.classLoader,
            arrayOf(LearningDao::class.java)
        ) { _, method, _ ->
            when {
                Flow::class.java.isAssignableFrom(method.returnType) -> flowOf(emptyList<Any>())
                method.returnType == Boolean::class.javaPrimitiveType -> false
                method.returnType == Int::class.javaPrimitiveType -> 0
                method.returnType == Long::class.javaPrimitiveType -> 0L
                else -> null
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
}
