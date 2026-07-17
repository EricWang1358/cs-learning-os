package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.CaptureSlipType
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.ReaderQuestionEntity
import com.cslearningos.mobile.data.SearchResultEntity
import com.cslearningos.mobile.core.common.AndroidArchitectureConstants
import com.cslearningos.mobile.assistant.domain.isValidProviderEndpoint
import com.cslearningos.mobile.feature.assistant.ui.AssistantUiState
import com.cslearningos.mobile.feature.sync.SyncPushReport
import com.cslearningos.mobile.feature.sync.SyncReport

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
    More,
    AssistantGuide
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
        get() = apiKey.isNotBlank() && isValidProviderEndpoint(baseUrl) && model.isNotBlank()
}

data class AppNotice(
    val id: String,
    val title: UiText,
    val body: UiText,
    val createdAt: Long = System.currentTimeMillis()
)

data class PendingNodeSave(
    val commandId: String,
    val nodeId: String,
    val fingerprint: String
)

enum class SyncReadiness {
    Unpaired,
    PendingServerPolicy,
    Ready,
    ReadOnly,
    UploadOnly
}

data class SyncUiState(
    val isPaired: Boolean = false,
    val endpoint: String = "",
    val serverId: String = "",
    val lastSyncAt: Long = 0,
    val scopeAreas: Set<String> = emptySet(),
    val includeDueReviews: Boolean = false,
    val serverScopes: Set<String> = emptySet(),
    val serverPolicyConfirmed: Boolean = false,
    val lastPullReport: SyncReport? = null,
    val lastPushReport: SyncPushReport? = null,
    val busy: Boolean = false,
    val error: String? = null
) {
    val readiness: SyncReadiness
        get() = when {
            !isPaired -> SyncReadiness.Unpaired
            !serverPolicyConfirmed -> SyncReadiness.PendingServerPolicy
            "sync:read" in serverScopes && "sync:push" in serverScopes -> SyncReadiness.Ready
            "sync:read" in serverScopes -> SyncReadiness.ReadOnly
            "sync:push" in serverScopes -> SyncReadiness.UploadOnly
            else -> SyncReadiness.PendingServerPolicy
        }
}

data class PackageImportUiState(
    val nodeCount: Int,
    val quizCount: Int,
    val captureCount: Int,
    val added: Int,
    val updated: Int,
    val conflicted: Int,
    val skipped: Int,
    val exportedAt: String
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
    val archivedCaptureSlips: List<CaptureSlipEntity> = emptyList(),
    val selectedNode: LearningNodeEntity? = null,
    val selectedQuiz: QuizItemEntity? = null,
    val reviewedQuiz: QuizItemEntity? = null,
    val reviewAreaId: String? = null,
    val reviewSetupVisible: Boolean = true,
    val selectedLibraryAreaId: String? = null,
    val libraryCheckedFilter: LibraryCheckedFilter = LibraryCheckedFilter.All,
    val editorNodeId: String? = null,
    val editorExpectedRevision: Long? = null,
    val editorAreaId: String? = null,
    val editorSourceCaptureSlipId: String? = null,
    val editorTitle: String = "",
    val editorBody: String = "",
    val pendingNodeSave: PendingNodeSave? = null,
    val searchQuery: String = "",
    val searchResults: List<SearchResultEntity> = emptyList(),
    val quizPrompt: String = "",
    val quizAnswer: String = "",
    val quizExplanation: String = "",
    val quizEditorId: String? = null,
    val quizExpectedRevision: Long? = null,
    val quizAreaId: String? = null,
    val readerQuestionDraft: String = "",
    val readerQuestionPanelExpanded: Boolean = false,
    val captureEditorId: String? = null,
    val captureExpectedRevision: Long? = null,
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
    val expandedMoreSection: MoreSectionId? = null,
    val quizAnswerVisible: Boolean = false,
    val sync: SyncUiState = SyncUiState(),
    val pendingPackageImport: PackageImportUiState? = null,
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
