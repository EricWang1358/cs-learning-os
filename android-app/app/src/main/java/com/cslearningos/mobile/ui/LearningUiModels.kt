package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.CaptureSlipType
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.ReaderQuestionEntity
import com.cslearningos.mobile.data.SearchResultEntity
import com.cslearningos.mobile.core.common.AndroidArchitectureConstants
import com.cslearningos.mobile.feature.assistant.ui.AssistantUiState

enum class AppScreen {
    Home,
    Assistant,
    Capture,
    Library,
    Reader,
    Editor,
    Search,
    QuizEditor,
    Review,
    Backup,
    More
}

data class AiProviderSettings(
    val provider: String = "DeepSeek",
    val apiKey: String = "",
    val baseUrl: String = "https://api.deepseek.com/v1",
    val model: String = "deepseek-v4-flash",
    val thinkingEnabled: Boolean = false,
    val apiKeyVisible: Boolean = false
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()
}

data class AppNotice(
    val id: String,
    val title: UiText,
    val body: UiText,
    val createdAt: Long = System.currentTimeMillis()
)

data class LearningUiState(
    val screen: AppScreen = AppScreen.Home,
    val assistant: AssistantUiState = AssistantUiState(),
    val areas: List<AreaEntity> = emptyList(),
    val nodes: List<LearningNodeEntity> = emptyList(),
    val trashNodes: List<LearningNodeEntity> = emptyList(),
    val quizzes: List<QuizItemEntity> = emptyList(),
    val dueQuizzes: List<QuizItemEntity> = emptyList(),
    val readerQuestions: List<ReaderQuestionEntity> = emptyList(),
    val captureSlips: List<CaptureSlipEntity> = emptyList(),
    val selectedNode: LearningNodeEntity? = null,
    val selectedQuiz: QuizItemEntity? = null,
    val selectedLibraryAreaId: String? = null,
    val libraryCheckedFilter: LibraryCheckedFilter = LibraryCheckedFilter.All,
    val editorNodeId: String? = null,
    val editorAreaId: String? = null,
    val editorSourceCaptureSlipId: String? = null,
    val editorTitle: String = "",
    val editorBody: String = "",
    val searchQuery: String = "",
    val searchResults: List<SearchResultEntity> = emptyList(),
    val quizPrompt: String = "",
    val quizAnswer: String = "",
    val quizExplanation: String = "",
    val readerQuestionDraft: String = "",
    val readerQuestionPanelExpanded: Boolean = false,
    val captureDraft: String = "",
    val captureTopicHint: String = "",
    val captureSourceLabel: String = "",
    val captureType: CaptureSlipType = CaptureSlipType.unclear,
    val aiProviderSettings: AiProviderSettings = AiProviderSettings(),
    val aiServiceStatus: AiServiceStatus = AiServiceStatus(),
    val availableAiModels: List<String> = emptyList(),
    val aiBusy: Boolean = false,
    val pendingAiDraftSlipId: String? = null,
    val notices: List<AppNotice> = emptyList(),
    val collapsedLibraryAreas: Set<String> = emptySet(),
    val systemLanguage: SystemLanguage = SystemLanguage.FollowSystem,
    val appearanceMode: AppearanceMode = AppearanceMode.FollowSystem,
    val expandedMoreSection: MoreSectionId? = MoreSectionId.System,
    val quizAnswerVisible: Boolean = false,
    val message: UiText? = null
)

fun LearningUiState.withNotice(title: UiText, body: UiText): LearningUiState =
    copy(
        notices = (
            listOf(
                AppNotice(
                    id = "notice-${System.currentTimeMillis()}-${notices.size}",
                    title = title,
                    body = body
                )
            ) + notices
        ).take(AndroidArchitectureConstants.AppNoticeLimit)
    )
