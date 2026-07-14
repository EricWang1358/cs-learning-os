package com.cslearningos.mobile.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.cslearningos.mobile.appshell.state.AppShellViewModel
import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.data.LearningRepository
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.QuizSource
import com.cslearningos.mobile.data.SyncStatus
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReviewScreenSetupTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reviewSetup_startsCollapsedUntilAnAreaIsTapped() {
        val initialState = LearningUiState(
            screen = AppScreen.Review,
            reviewSetupVisible = true,
            areas = listOf(area(id = "systems", name = "Systems", order = 10)),
            quizzes = listOf(quiz(id = "q1", prompt = "Prompt q1", area = "systems")),
            dueQuizzes = listOf(quiz(id = "q1", prompt = "Prompt q1", area = "systems"))
        )
        val viewModel = LearningViewModel(
            application = ApplicationProvider.getApplicationContext(),
            repository = LearningRepository(emptyDao()),
            initialState = initialState
        )
        val shellViewModel = AppShellViewModel()

        composeRule.setContent {
            LearningOsApp(
                shellViewModel = shellViewModel,
                learningViewModel = viewModel
            )
        }

        composeRule.onNodeWithText("All Areas").assertExists()
        composeRule.onNodeWithText("Prompt q1").assertDoesNotExist()

        composeRule.onNodeWithText("All Areas").performClick()

        composeRule.onNodeWithText("Prompt q1").assertExists()
    }

    private fun emptyDao(): LearningDao =
        Proxy.newProxyInstance(
            LearningDao::class.java.classLoader,
            arrayOf(LearningDao::class.java)
        ) { _, method, _ ->
            when {
                Flow::class.java.isAssignableFrom(method.returnType) -> flowOf(emptyList<Any>())
                method.returnType == Boolean::class.javaPrimitiveType -> false
                method.returnType == Int::class.javaPrimitiveType -> 0
                method.returnType == Long::class.javaPrimitiveType -> 0L
                List::class.java.isAssignableFrom(method.returnType) -> emptyList<Any>()
                else -> null
            }
        } as LearningDao

    private fun area(id: String, name: String, order: Int) =
        AreaEntity(
            id = id,
            slug = id,
            name = name,
            order = order,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            deletedAt = null
        )

    private fun quiz(id: String, prompt: String, area: String) =
        QuizItemEntity(
            id = id,
            nodeId = null,
            prompt = prompt,
            answer = "Answer $id",
            explanation = "",
            source = QuizSource.manual,
            sourceAnchor = null,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            area = area
        )
}
