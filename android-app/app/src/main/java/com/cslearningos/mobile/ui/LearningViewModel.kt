package com.cslearningos.mobile.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cslearningos.mobile.R
import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.CaptureSlipStatus
import com.cslearningos.mobile.data.CaptureSlipType
import com.cslearningos.mobile.data.CaptureNodeDraft
import com.cslearningos.mobile.data.LearningDatabase
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.LearningRepository
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.ReaderQuestionEntity
import com.cslearningos.mobile.data.SearchResultEntity
import com.cslearningos.mobile.data.StarterContentImporter
import com.cslearningos.mobile.domain.ReviewRating
import com.cslearningos.mobile.ui.backup.BackupDocument
import com.cslearningos.mobile.ui.backup.BackupTransferCoordinator
import com.cslearningos.mobile.ui.backup.backupImportErrorKey
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class AppScreen {
    Home,
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

@OptIn(ExperimentalCoroutinesApi::class)
class LearningViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LearningRepository(
        LearningDatabase.create(application).learningDao()
    )
    private val _state = MutableStateFlow(LearningUiState())
    private val dueReviewNow = MutableStateFlow(System.currentTimeMillis())
    val state: StateFlow<LearningUiState> = _state.asStateFlow()
    private val aiPrefs = application.getSharedPreferences("ai-provider-settings", Context.MODE_PRIVATE)
    private val appPrefs = application.getSharedPreferences("app-settings", Context.MODE_PRIVATE)

    init {
        _state.update {
            it.copy(
                aiProviderSettings = loadAiProviderSettings(),
                systemLanguage = loadSystemLanguage(),
                appearanceMode = loadAppearanceMode()
            )
        }
        viewModelScope.launch {
            seedStarterContentIfNeeded()
        }
        viewModelScope.launch {
            repository.areas.collect { areas ->
                _state.update { current ->
                    current.copy(
                        areas = areas,
                        selectedLibraryAreaId = current.selectedLibraryAreaId?.takeIf { selectedId ->
                            areas.any { it.id == selectedId && it.deletedAt == null }
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.nodes.collect { nodes ->
                _state.update { current ->
                    current.copy(
                        nodes = nodes,
                        selectedNode = current.selectedNode?.let { selected ->
                            nodes.firstOrNull { it.id == selected.id } ?: selected
                        },
                        selectedLibraryAreaId = current.selectedLibraryAreaId?.takeIf { selectedAreaId ->
                            nodes.any { it.areaId == selectedAreaId && it.deletedAt == null && it.visibility != "trash" } ||
                                current.areas.any { it.id == selectedAreaId && it.deletedAt == null }
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.trashNodes.collect { nodes ->
                _state.update { it.copy(trashNodes = nodes) }
            }
        }
        viewModelScope.launch {
            repository.quizzes.collect { quizzes ->
                _state.update { it.copy(quizzes = quizzes) }
            }
        }
        viewModelScope.launch {
            dueReviewNow.flatMapLatest { now -> repository.dueQuizzes(now) }.collect { due ->
                _state.update { it.copy(dueQuizzes = due) }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(DueReviewRefreshIntervalMillis)
                refreshDueReviews()
            }
        }
        viewModelScope.launch {
            repository.openReaderQuestions.collect { questions ->
                _state.update { it.copy(readerQuestions = questions) }
            }
        }
        viewModelScope.launch {
            repository.inboxCaptureSlips.collect { slips ->
                _state.update { it.copy(captureSlips = slips) }
            }
        }
    }

    fun showHome() {
        refreshDueReviews()
        _state.update { it.copy(screen = AppScreen.Home, message = null) }
    }

    fun showLibrary() {
        _state.update {
            it.copy(
                screen = AppScreen.Library,
                selectedLibraryAreaId = null,
                libraryCheckedFilter = LibraryCheckedFilter.All,
                message = null
            )
        }
    }

    fun showCapture() {
        _state.update { it.copy(screen = AppScreen.Capture, message = null) }
    }

    fun showSearch() {
        _state.update { it.copy(screen = AppScreen.Search, message = null) }
    }

    fun showBackup() {
        _state.update { it.copy(screen = AppScreen.Backup, message = null) }
    }

    fun showMore() {
        _state.update { it.copy(screen = AppScreen.More, message = null) }
    }

    fun dismissNotice(noticeId: String) {
        _state.update { current ->
            current.copy(notices = current.notices.filterNot { it.id == noticeId })
        }
    }

    fun showAiServiceSettings() {
        _state.update {
            it.copy(
                screen = AppScreen.More,
                expandedMoreSection = MoreSectionId.Service,
                message = null,
                aiServiceStatus = AiServiceStatus(
                    kind = AiServiceStatusKind.Info,
                    title = uiText(R.string.ai_status_configure_service_title),
                    body = uiText(R.string.ai_status_configure_service_body)
                )
            )
        }
    }

    fun startNewNode(areaId: String? = state.value.selectedLibraryAreaId) {
        _state.update {
            it.copy(
                screen = AppScreen.Editor,
                editorAreaId = areaId,
                editorNodeId = null,
                editorSourceCaptureSlipId = null,
                editorTitle = "",
                editorBody = "",
                message = null
            )
        }
    }

    fun editNode(node: LearningNodeEntity) {
        _state.update {
            it.copy(
                screen = AppScreen.Editor,
                selectedNode = node,
                editorAreaId = node.areaId,
                editorNodeId = node.id,
                editorSourceCaptureSlipId = null,
                editorTitle = node.title,
                editorBody = node.markdownBody,
                message = null
            )
        }
    }

    fun openNode(node: LearningNodeEntity) {
        _state.update { it.copy(screen = AppScreen.Reader, selectedNode = node, message = null) }
        viewModelScope.launch {
            repository.markRead(node.id)
        }
    }

    fun openSearchResult(result: SearchResultEntity) {
        viewModelScope.launch {
            when (result.type) {
                "node" -> repository.getNode(result.id)?.let { openNode(it) }
                "quiz" -> repository.getQuiz(result.id)?.let { quiz ->
                    _state.update {
                        it.copy(
                            screen = AppScreen.Review,
                            selectedQuiz = quiz,
                            quizAnswerVisible = false,
                            message = null
                        )
                    }
                }
            }
        }
    }

    fun setEditorTitle(value: String) {
        _state.update { it.copy(editorTitle = value) }
    }

    fun setEditorBody(value: String) {
        _state.update { it.copy(editorBody = value) }
    }

    fun saveNode() {
        val snapshot = state.value
        if (snapshot.editorTitle.isBlank() && snapshot.editorBody.isBlank()) {
            _state.update { it.copy(message = uiText(R.string.message_add_title_or_markdown)) }
            return
        }
        viewModelScope.launch {
            val node = repository.saveNode(
                id = snapshot.editorNodeId,
                title = snapshot.editorTitle,
                markdownBody = snapshot.editorBody,
                areaId = snapshot.editorAreaId
            )
            snapshot.editorSourceCaptureSlipId?.let { slipId ->
                repository.markCaptureSlipConverted(slipId = slipId, nodeId = node.id)
            }
            _state.update {
                it.copy(
                    screen = AppScreen.Reader,
                    selectedNode = node,
                    editorAreaId = null,
                    editorSourceCaptureSlipId = null,
                    message = uiText(R.string.message_node_saved)
                )
            }
        }
    }

    fun cancelEditor() {
        _state.update {
            it.copy(
                screen = if (it.selectedNode == null) AppScreen.Home else AppScreen.Reader,
                editorAreaId = null,
                message = null
            )
        }
    }

    fun deleteSelectedNode() {
        val node = state.value.selectedNode ?: return
        viewModelScope.launch {
            repository.moveNodeToTrash(node.id)
            _state.update {
                it.copy(
                    screen = AppScreen.Library,
                    selectedNode = null,
                    editorAreaId = null,
                    editorNodeId = null,
                    editorSourceCaptureSlipId = null,
                    editorTitle = "",
                    editorBody = "",
                    message = uiText(R.string.message_node_moved_to_trashbin)
                )
            }
        }
    }

    fun restoreNode(node: LearningNodeEntity) {
        viewModelScope.launch {
            repository.restoreNodeFromTrash(node.id)
            _state.update { it.copy(message = uiText(R.string.message_node_restored)) }
        }
    }

    fun permanentlyDeleteNode(node: LearningNodeEntity) {
        viewModelScope.launch {
            repository.permanentlyDeleteNode(node.id)
            _state.update { it.copy(message = uiText(R.string.message_node_deleted_forever)) }
        }
    }

    fun clearStarterContent() {
        viewModelScope.launch {
            repository.clearStarterContent()
            _state.update { it.copy(message = uiText(R.string.message_starter_demo_removed)) }
        }
    }

    fun setSearchQuery(value: String) {
        _state.update {
            it.copy(
                searchQuery = value,
                searchResults = if (value.isBlank()) emptyList() else it.searchResults
            )
        }
    }

    fun runSearch() {
        val query = state.value.searchQuery
        viewModelScope.launch {
            _state.update {
                it.copy(
                    searchResults = repository.search(query),
                    message = if (query.isBlank()) uiText(R.string.message_enter_search_query) else null
                )
            }
        }
    }

    fun startQuizForSelectedNode() {
        _state.update {
            it.copy(
                screen = AppScreen.QuizEditor,
                quizPrompt = "",
                quizAnswer = "",
                quizExplanation = "",
                message = null
            )
        }
    }

    fun setQuizPrompt(value: String) {
        _state.update { it.copy(quizPrompt = value) }
    }

    fun setQuizAnswer(value: String) {
        _state.update { it.copy(quizAnswer = value) }
    }

    fun setQuizExplanation(value: String) {
        _state.update { it.copy(quizExplanation = value) }
    }

    fun setReaderQuestionDraft(value: String) {
        _state.update { it.copy(readerQuestionDraft = value, message = null) }
    }

    fun toggleReaderQuestionPanel() {
        _state.update { it.copy(readerQuestionPanelExpanded = !it.readerQuestionPanelExpanded, message = null) }
    }

    fun saveReaderQuestion() {
        val snapshot = state.value
        val node = snapshot.selectedNode ?: return
        if (snapshot.readerQuestionDraft.isBlank()) {
            _state.update { it.copy(message = uiText(R.string.message_write_unclear_point)) }
            return
        }
        viewModelScope.launch {
            repository.saveReaderQuestion(
                nodeId = node.id,
                body = snapshot.readerQuestionDraft
            )
            _state.update {
                it.copy(
                    readerQuestionDraft = "",
                    message = uiText(R.string.message_reader_question_saved)
                )
            }
        }
    }

    fun setCaptureDraft(value: String) {
        _state.update { it.copy(captureDraft = value, message = null) }
    }

    fun setCaptureTopicHint(value: String) {
        _state.update { it.copy(captureTopicHint = value, message = null) }
    }

    fun setCaptureSourceLabel(value: String) {
        _state.update { it.copy(captureSourceLabel = value, message = null) }
    }

    fun setCaptureType(value: CaptureSlipType) {
        _state.update { it.copy(captureType = value, message = null) }
    }

    fun saveCaptureSlip() {
        val snapshot = state.value
        if (snapshot.captureDraft.isBlank()) {
            _state.update { it.copy(message = uiText(R.string.message_write_capture_before_save)) }
            return
        }
        viewModelScope.launch {
            repository.saveCaptureSlip(
                body = snapshot.captureDraft,
                type = snapshot.captureType,
                topicHint = snapshot.captureTopicHint,
                sourceLabel = snapshot.captureSourceLabel
            )
            _state.update {
                it.copy(
                    captureDraft = "",
                    captureTopicHint = "",
                    captureSourceLabel = "",
                    message = uiText(R.string.message_capture_saved_to_inbox)
                )
            }
        }
    }

    fun saveCaptureSlipForAiDraft() {
        val snapshot = state.value
        if (snapshot.captureDraft.isBlank()) {
            _state.update { it.copy(message = uiText(R.string.message_write_capture_before_ai)) }
            return
        }
        viewModelScope.launch {
            val slip = repository.saveCaptureSlip(
                body = snapshot.captureDraft,
                type = snapshot.captureType,
                topicHint = snapshot.captureTopicHint,
                sourceLabel = snapshot.captureSourceLabel,
                status = if (snapshot.aiProviderSettings.isConfigured) CaptureSlipStatus.ai_queued else CaptureSlipStatus.inbox
            )
            _state.update {
                it.copy(
                    captureDraft = "",
                    captureTopicHint = "",
                    captureSourceLabel = "",
                    pendingAiDraftSlipId = if (snapshot.aiProviderSettings.isConfigured) slip.id else null,
                    aiServiceStatus = if (snapshot.aiProviderSettings.isConfigured) {
                        AiServiceStatus(
                            kind = AiServiceStatusKind.Info,
                            title = uiText(R.string.ai_status_preflight_title),
                            body = uiText(R.string.ai_status_preflight_saved_body)
                        )
                    } else {
                        it.aiServiceStatus
                    },
                    message = if (snapshot.aiProviderSettings.isConfigured) {
                        uiText(R.string.message_review_ai_preflight)
                    } else {
                        uiText(R.string.message_configure_ai_before_drafting)
                    }
                ).withNotice(
                    title = if (snapshot.aiProviderSettings.isConfigured) uiText(R.string.ai_notice_queued_title) else uiText(R.string.capture_notice_saved_title),
                    body = if (snapshot.aiProviderSettings.isConfigured) {
                        uiText(R.string.ai_notice_queued_body)
                    } else {
                        uiText(R.string.capture_notice_saved_body)
                    }
                )
            }
        }
    }

    fun prepareAiDraftForSlip(slip: CaptureSlipEntity) {
        val settings = state.value.aiProviderSettings
        val missing = settings.missingRequiredFields()
        if (missing.isNotEmpty()) {
            _state.update {
                it.copy(
                    screen = AppScreen.More,
                    expandedMoreSection = MoreSectionId.Service,
                    aiServiceStatus = AiServiceStatus(
                        kind = AiServiceStatusKind.Warning,
                        title = uiText(R.string.ai_status_setup_needed_title),
                        body = uiText(R.string.ai_status_setup_needed_body, UiText.Dynamic(localizedLabelList(missing)))
                    ),
                    message = uiText(R.string.message_configure_ai_before_drafting)
                )
            }
            return
        }

        viewModelScope.launch {
            repository.updateCaptureSlipStatus(slip.id, CaptureSlipStatus.ai_queued)
        }
        _state.update {
            it.copy(
                pendingAiDraftSlipId = slip.id,
                aiServiceStatus = AiServiceStatus(
                    kind = AiServiceStatusKind.Info,
                    title = uiText(R.string.ai_status_preflight_title),
                    body = uiText(R.string.ai_status_preflight_confirm_body, settings.provider)
                ),
                message = uiText(R.string.message_confirm_ai_draft)
            ).withNotice(
                title = uiText(R.string.ai_notice_queued_title),
                body = uiText(R.string.ai_notice_review_preflight_body)
            )
        }
    }

    fun cancelAiDraftPreflight() {
        _state.update {
            it.copy(
                pendingAiDraftSlipId = null,
                message = uiText(R.string.message_ai_draft_canceled)
            )
        }
    }

    fun confirmAiDraftPreflight() {
        val slip = state.value.pendingAiDraftSlipId
            ?.let { slipId -> state.value.captureSlips.firstOrNull { it.id == slipId } }
            ?: return
        draftCaptureSlipWithAi(slip)
    }

    fun archiveCaptureSlip(slip: CaptureSlipEntity) {
        viewModelScope.launch {
            repository.archiveCaptureSlip(slip.id)
            _state.update { it.copy(message = uiText(R.string.message_capture_archived)) }
        }
    }

    fun promoteCaptureSlipToNode(slip: CaptureSlipEntity) {
        val draft = CaptureNodeDraft.fromSlip(slip, existingNodes = state.value.nodes)
        _state.update {
            it.copy(
                screen = AppScreen.Editor,
                editorNodeId = null,
                editorSourceCaptureSlipId = slip.id,
                selectedNode = draft.suggestedNodeId?.let { nodeId -> state.value.nodes.firstOrNull { node -> node.id == nodeId } },
                editorTitle = draft.title,
                editorBody = draft.markdownBody,
                message = uiText(R.string.message_review_capture_draft)
            )
        }
    }

    fun draftCaptureSlipWithAi(slip: CaptureSlipEntity) {
        val snapshot = state.value
        val settings = snapshot.aiProviderSettings
        val missing = settings.missingRequiredFields()
        if (missing.isNotEmpty()) {
            _state.update {
                it.copy(
                    screen = AppScreen.More,
                    expandedMoreSection = MoreSectionId.Service,
                    aiServiceStatus = AiServiceStatus(
                        kind = AiServiceStatusKind.Warning,
                        title = uiText(R.string.ai_status_setup_needed_title),
                        body = uiText(R.string.ai_status_setup_needed_body, UiText.Dynamic(localizedLabelList(missing)))
                    ),
                    message = uiText(R.string.message_configure_ai_before_drafting)
                )
            }
            return
        }

        viewModelScope.launch {
            repository.updateCaptureSlipStatus(slip.id, CaptureSlipStatus.ai_drafting)
            _state.update {
                it.copy(
                    aiBusy = true,
                    pendingAiDraftSlipId = slip.id,
                    aiServiceStatus = AiServiceStatus(
                        kind = AiServiceStatusKind.Loading,
                        title = uiText(R.string.ai_status_drafting_title),
                        body = uiText(R.string.ai_status_drafting_body)
                    ),
                    message = uiText(R.string.message_generating_ai_draft)
                ).withNotice(
                    title = uiText(R.string.ai_notice_started_title),
                    body = uiText(R.string.ai_notice_started_body)
                )
            }
            runCatching {
                requestCaptureDraft(settings = settings, slip = slip, existingNodeTitles = snapshot.nodes.map { it.title })
            }.onSuccess { markdown ->
                repository.updateCaptureSlipStatus(slip.id, CaptureSlipStatus.ai_draft_ready)
                val fallbackTitle = slip.topicHint?.takeIf { it.isNotBlank() } ?: "Capture Draft"
                _state.update {
                    it.copy(
                        screen = AppScreen.Editor,
                        editorNodeId = null,
                        editorSourceCaptureSlipId = slip.id,
                        selectedNode = null,
                        editorTitle = titleFromAiMarkdown(markdown, fallbackTitle),
                        editorBody = markdown,
                        aiBusy = false,
                        pendingAiDraftSlipId = null,
                        aiServiceStatus = AiServiceStatus(
                            kind = AiServiceStatusKind.Success,
                            title = uiText(R.string.ai_status_ready_title),
                            body = uiText(R.string.ai_status_ready_body)
                        ),
                        message = uiText(R.string.message_ai_draft_ready)
                    ).withNotice(
                        title = uiText(R.string.ai_notice_ready_title),
                        body = uiText(R.string.ai_notice_ready_body)
                    )
                }
            }.onFailure { error ->
                repository.updateCaptureSlipStatus(slip.id, CaptureSlipStatus.ai_queued)
                _state.update {
                    it.copy(
                        aiBusy = false,
                        aiServiceStatus = AiServiceStatus(
                            kind = AiServiceStatusKind.Error,
                            title = uiText(R.string.ai_status_failed_title),
                            body = UiText.Dynamic(error.safeAiError())
                        ),
                        message = uiText(R.string.message_ai_draft_failed)
                    ).withNotice(
                        title = uiText(R.string.ai_status_failed_title),
                        body = UiText.Dynamic(error.safeAiError())
                    )
                }
            }
        }
    }

    fun resolveReaderQuestion(question: ReaderQuestionEntity) {
        viewModelScope.launch {
            repository.resolveReaderQuestion(question.id)
            _state.update { it.copy(message = uiText(R.string.message_question_resolved)) }
        }
    }

    fun saveQuiz() {
        val snapshot = state.value
        if (snapshot.quizPrompt.isBlank() || snapshot.quizAnswer.isBlank()) {
            _state.update { it.copy(message = uiText(R.string.message_question_answer_required)) }
            return
        }
        viewModelScope.launch {
            repository.saveManualQuiz(
                nodeId = snapshot.selectedNode?.id,
                prompt = snapshot.quizPrompt,
                answer = snapshot.quizAnswer,
                explanation = snapshot.quizExplanation
            )
            _state.update {
                it.copy(screen = AppScreen.Home, message = uiText(R.string.message_quiz_saved))
            }
        }
    }

    fun showReview() {
        refreshDueReviews()
        _state.update {
            it.copy(
                screen = AppScreen.Review,
                selectedQuiz = it.dueQuizzes.firstOrNull(),
                quizAnswerVisible = false,
                message = null
            )
        }
    }

    private fun refreshDueReviews(now: Long = System.currentTimeMillis()) {
        dueReviewNow.value = now
    }

    fun revealCurrentQuizAnswer() {
        _state.update { it.copy(quizAnswerVisible = true, message = null) }
    }

    fun answerCurrentQuiz(rating: ReviewRating) {
        val quiz = state.value.selectedQuiz ?: return
        viewModelScope.launch {
            repository.answerQuiz(quiz.id, rating)
            _state.update { current ->
                current.copy(
                    selectedQuiz = selectQuizAfterReview(
                        currentQuiz = quiz,
                        currentDueQuizzes = current.dueQuizzes,
                        rating = rating
                    ),
                    quizAnswerVisible = false,
                    message = uiText(R.string.message_review_saved)
                )
            }
            refreshDueReviews()
        }
    }

    suspend fun createBackupDocument(now: Long = System.currentTimeMillis()): BackupDocument =
        BackupTransferCoordinator.createBackupDocument(
            rawJson = repository.exportBackup(now),
            exportedAt = now
        )

    fun restoreBackupFromJson(rawJson: String) {
        viewModelScope.launch {
            runCatching { repository.restoreBackup(rawJson) }
                .onSuccess {
                    _state.update { it.copy(screen = AppScreen.Home, message = uiText(R.string.message_backup_restored)) }
                }
                .onFailure { error ->
                    showBackupError(error)
                }
        }
    }

    fun noteBackupShared() {
        _state.update { it.copy(message = uiText(R.string.message_backup_exported)) }
    }

    fun noteBackupSavedToDevice() {
        _state.update { it.copy(message = uiText(R.string.message_backup_saved_to_device)) }
    }

    fun showBackupError(error: Throwable, fallbackResId: Int = R.string.message_restore_failed) {
        val message = when (backupImportErrorKey(error)) {
            "invalid_json" -> uiText(R.string.message_backup_invalid_json)
            "unreadable_file" -> uiText(R.string.message_backup_unreadable_file)
            else -> error.message?.let(UiText::Dynamic) ?: uiText(fallbackResId)
        }
        _state.update { it.copy(message = message) }
    }

    fun setAiProvider(value: String) {
        updateAiSettings(savedField = uiText(R.string.more_provider_label)) { it.copy(provider = value) }
    }

    fun setAiApiKey(value: String) {
        updateAiSettings(savedField = uiText(R.string.more_api_key_label)) { it.copy(apiKey = value) }
    }

    fun setAiBaseUrl(value: String) {
        updateAiSettings(savedField = uiText(R.string.more_base_url_label)) { it.copy(baseUrl = value) }
    }

    fun setAiModel(value: String) {
        updateAiSettings(savedField = uiText(R.string.more_model_label)) { it.copy(model = value) }
    }

    fun setAiThinkingEnabled(value: Boolean) {
        updateAiSettings(savedField = uiText(R.string.more_connection_label)) { it.copy(thinkingEnabled = value) }
    }

    fun toggleAiKeyVisibility() {
        _state.update {
            it.copy(aiProviderSettings = it.aiProviderSettings.copy(apiKeyVisible = !it.aiProviderSettings.apiKeyVisible))
        }
    }

    fun validateAiSettings() {
        val settings = state.value.aiProviderSettings
        val missing = settings.missingRequiredFields()
        if (missing.isNotEmpty()) {
            _state.update {
                it.copy(
                    aiServiceStatus = AiServiceStatus(
                        kind = AiServiceStatusKind.Error,
                        title = uiText(R.string.ai_status_settings_incomplete_title),
                        body = uiText(R.string.ai_status_missing_fields_body, UiText.Dynamic(localizedLabelList(missing)))
                    ),
                    message = uiText(R.string.message_ai_settings_incomplete)
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    aiBusy = true,
                    aiServiceStatus = AiServiceStatus(
                        kind = AiServiceStatusKind.Loading,
                        title = uiText(R.string.ai_status_validating_title),
                        body = uiText(R.string.ai_status_validating_body, aiModelsUrl(settings.baseUrl))
                    ),
                    message = uiText(R.string.message_validating_ai_service)
                )
            }
            runCatching { fetchModelIds(settings) }
                .onSuccess { models ->
                    _state.update {
                        it.copy(
                            aiBusy = false,
                            availableAiModels = models,
                            aiServiceStatus = AiServiceStatus(
                                kind = AiServiceStatusKind.Success,
                                title = uiText(R.string.ai_status_validated_title),
                                body = if (models.isEmpty()) {
                                    uiText(R.string.ai_status_validated_empty_body)
                                } else {
                                    uiText(R.string.ai_status_validated_models_body, models.size, settings.model)
                                }
                            ),
                            message = uiText(R.string.message_ai_validation_succeeded)
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            aiBusy = false,
                            aiServiceStatus = AiServiceStatus(
                                kind = AiServiceStatusKind.Error,
                                title = uiText(R.string.ai_status_validation_failed_title),
                                body = UiText.Dynamic(error.safeAiError())
                            ),
                            message = uiText(R.string.message_ai_validation_failed)
                        )
                    }
                }
        }
    }

    fun saveAiSettings() {
        saveAiProviderSettings(state.value.aiProviderSettings)
        _state.update {
            it.copy(
                aiServiceStatus = AiServiceStatus(
                    kind = AiServiceStatusKind.Success,
                    title = uiText(R.string.ai_status_settings_saved_title),
                    body = uiText(R.string.ai_status_settings_saved_body)
                ),
                message = uiText(R.string.message_ai_settings_saved_locally)
            )
        }
    }

    fun pullAiModels() {
        val settings = state.value.aiProviderSettings
        val missing = settings.missingRequiredFields()
        if (missing.isNotEmpty()) {
            _state.update {
                it.copy(
                    aiServiceStatus = AiServiceStatus(
                        kind = AiServiceStatusKind.Warning,
                        title = uiText(R.string.ai_status_pull_unavailable_title),
                        body = uiText(R.string.ai_status_missing_fields_body, UiText.Dynamic(localizedLabelList(missing)))
                    ),
                    message = uiText(R.string.message_ai_settings_incomplete)
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    aiBusy = true,
                    aiServiceStatus = AiServiceStatus(
                        kind = AiServiceStatusKind.Loading,
                        title = uiText(R.string.ai_status_pull_title),
                        body = uiText(R.string.ai_status_pull_body, aiModelsUrl(settings.baseUrl))
                    ),
                    message = uiText(R.string.message_pulling_ai_models)
                )
            }
            runCatching { fetchModelIds(settings) }
                .onSuccess { models ->
                    _state.update {
                        it.copy(
                            aiBusy = false,
                            availableAiModels = models,
                            aiServiceStatus = AiServiceStatus(
                                kind = if (models.isEmpty()) AiServiceStatusKind.Warning else AiServiceStatusKind.Success,
                                title = if (models.isEmpty()) uiText(R.string.ai_status_pull_empty_title) else uiText(R.string.ai_status_pull_success_title),
                                body = if (models.isEmpty()) {
                                    uiText(R.string.ai_status_pull_empty_body)
                                } else {
                                    uiText(R.string.ai_status_pull_success_body)
                                }
                            ),
                            message = if (models.isEmpty()) uiText(R.string.message_no_model_ids) else uiText(R.string.message_ai_models_pulled)
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            aiBusy = false,
                            aiServiceStatus = AiServiceStatus(
                                kind = AiServiceStatusKind.Error,
                                title = uiText(R.string.ai_status_pull_failed_title),
                                body = UiText.Dynamic(error.safeAiError())
                            ),
                            message = uiText(R.string.message_model_pull_failed)
                        )
                    }
                }
        }
    }

    fun selectAiModel(modelId: String) {
        updateAiSettings(savedField = uiText(R.string.more_model_label)) { it.copy(model = modelId) }
    }

    fun toggleMoreSection(section: MoreSectionId) {
        _state.update {
            it.copy(
                expandedMoreSection = if (it.expandedMoreSection == section) null else section,
                message = null
            )
        }
    }

    fun toggleLibraryArea(area: String) {
        _state.update { current ->
            val nextCollapsed = if (area in current.collapsedLibraryAreas) {
                current.collapsedLibraryAreas - area
            } else {
                current.collapsedLibraryAreas + area
            }
            current.copy(collapsedLibraryAreas = nextCollapsed, message = null)
        }
    }

    fun openLibraryArea(areaId: String) {
        _state.update {
            it.copy(
                selectedLibraryAreaId = areaId,
                libraryCheckedFilter = LibraryCheckedFilter.All,
                message = null
            )
        }
    }

    fun closeLibraryArea() {
        _state.update {
            it.copy(
                selectedLibraryAreaId = null,
                libraryCheckedFilter = LibraryCheckedFilter.All,
                message = null
            )
        }
    }

    fun setLibraryCheckedFilter(filter: LibraryCheckedFilter) {
        _state.update { it.copy(libraryCheckedFilter = filter, message = null) }
    }

    fun createArea(name: String) {
        viewModelScope.launch {
            val area = repository.createArea(name)
            _state.update {
                it.copy(
                    screen = AppScreen.Library,
                    selectedLibraryAreaId = area.id,
                    message = uiText(R.string.message_area_created)
                )
            }
        }
    }

    fun renameArea(areaId: String, name: String) {
        viewModelScope.launch {
            repository.renameArea(areaId, name)
            _state.update { it.copy(message = uiText(R.string.message_area_renamed)) }
        }
    }

    fun deleteArea(areaId: String) {
        viewModelScope.launch {
            val deleted = repository.deleteAreaIfEmpty(areaId)
            _state.update {
                it.copy(
                    selectedLibraryAreaId = if (deleted && it.selectedLibraryAreaId == areaId) null else it.selectedLibraryAreaId,
                    message = if (deleted) uiText(R.string.message_area_deleted) else uiText(R.string.message_area_not_empty)
                )
            }
        }
    }

    fun moveNodeToArea(nodeId: String, areaId: String) {
        viewModelScope.launch {
            repository.moveNodeToArea(nodeId, areaId)
            _state.update { it.copy(message = uiText(R.string.message_node_moved_to_area)) }
        }
    }

    fun toggleNodeChecked(nodeId: String) {
        viewModelScope.launch {
            repository.toggleNodeChecked(nodeId)
            _state.update { it.copy(message = null) }
        }
    }

    fun setSystemLanguage(value: SystemLanguage) {
        appPrefs.edit().putString("systemLanguage", value.name).apply()
        _state.update { it.copy(systemLanguage = value, message = uiText(R.string.message_language_saved)) }
    }

    fun setAppearanceMode(value: AppearanceMode) {
        appPrefs.edit().putString("appearanceMode", value.name).apply()
        _state.update { it.copy(appearanceMode = value, message = uiText(R.string.message_appearance_saved)) }
    }

    private fun updateAiSettings(savedField: UiText, reducer: (AiProviderSettings) -> AiProviderSettings) {
        _state.update { current ->
            val next = reducer(current.aiProviderSettings)
            saveAiProviderSettings(next)
            current.copy(
                aiProviderSettings = next,
                aiServiceStatus = AiServiceStatus(
                    kind = AiServiceStatusKind.Info,
                    title = uiText(R.string.ai_status_autosaved_title, savedField),
                    body = uiText(R.string.ai_status_autosaved_body)
                ),
                message = null
            )
        }
    }

    private suspend fun fetchModelIds(settings: AiProviderSettings): List<String> =
        withContext(Dispatchers.IO) {
            val response = openAiGet(url = aiModelsUrl(settings.baseUrl), apiKey = settings.apiKey)
            parseOpenAiModelIds(response)
        }

    private suspend fun requestCaptureDraft(
        settings: AiProviderSettings,
        slip: CaptureSlipEntity,
        existingNodeTitles: List<String>
    ): String =
        withContext(Dispatchers.IO) {
            val prompt = buildCaptureAiDraftPrompt(slip = slip, existingNodes = existingNodeTitles)
            val payload = JSONObject()
                .put("model", settings.model)
                .put("temperature", 0.2)
                .put(
                    "messages",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("role", "system")
                                .put("content", "You create concise, editable Markdown learning-node drafts for a local-first study app.")
                        )
                        .put(JSONObject().put("role", "user").put("content", prompt))
                )
            val response = openAiPost(
                url = aiChatCompletionsUrl(settings.baseUrl),
                apiKey = settings.apiKey,
                payload = payload
            )
            parseOpenAiChatContent(response).ifBlank {
                throw IllegalStateException("The model returned an empty draft.")
            }
        }

    private fun openAiGet(url: String, apiKey: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = AiConnectTimeoutMillis
            readTimeout = AiReadTimeoutMillis
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
        }
        return connection.useResponse()
    }

    private fun openAiPost(url: String, apiKey: String, payload: JSONObject): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = AiConnectTimeoutMillis
            readTimeout = AiReadTimeoutMillis
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { stream ->
            stream.write(payload.toString().toByteArray(Charsets.UTF_8))
        }
        return connection.useResponse()
    }

    private fun HttpURLConnection.useResponse(): String =
        try {
            val responseBody = (if (responseCode in 200..299) inputStream else errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP $responseCode: ${responseBody.take(240)}")
            }
            responseBody
        } finally {
            disconnect()
        }

    private fun Throwable.safeAiError(): String =
        (message ?: javaClass.simpleName)
            .replace(Regex("sk-[A-Za-z0-9_-]+"), "sk-...")
            .take(260)

    private fun localizedLabelList(labelResIds: List<Int>): String =
        labelResIds.joinToString { labelResId -> localizedAppContext().getString(labelResId) }

    private fun localizedAppContext(): Context {
        val application = getApplication<Application>()
        val systemLanguageTag = application.resources.configuration.locales[0]?.toLanguageTag().orEmpty()
        return application.localizedAppContext(state.value.systemLanguage, systemLanguageTag)
    }

    private fun loadAiProviderSettings(): AiProviderSettings =
        AiProviderSettings(
            provider = aiPrefs.getString("provider", null) ?: "DeepSeek",
            apiKey = aiPrefs.getString("apiKey", null) ?: "",
            baseUrl = aiPrefs.getString("baseUrl", null) ?: "https://api.deepseek.com/v1",
            model = aiPrefs.getString("model", null) ?: "deepseek-v4-flash",
            thinkingEnabled = aiPrefs.getBoolean("thinkingEnabled", false)
        )

    private fun loadSystemLanguage(): SystemLanguage =
        appPrefs.getString("systemLanguage", null)
            ?.let { runCatching { SystemLanguage.valueOf(it) }.getOrNull() }
            ?: SystemLanguage.FollowSystem

    private fun loadAppearanceMode(): AppearanceMode =
        appPrefs.getString("appearanceMode", null)
            ?.let { runCatching { AppearanceMode.valueOf(it) }.getOrNull() }
            ?: AppearanceMode.FollowSystem

    private fun saveAiProviderSettings(settings: AiProviderSettings) {
        aiPrefs.edit()
            .putString("provider", settings.provider)
            .putString("apiKey", settings.apiKey)
            .putString("baseUrl", settings.baseUrl)
            .putString("model", settings.model)
            .putBoolean("thinkingEnabled", settings.thinkingEnabled)
            .apply()
    }

    private suspend fun seedStarterContentIfNeeded() {
        val prefs = getApplication<Application>()
            .getSharedPreferences("starter-content", Context.MODE_PRIVATE)
        if (prefs.getInt("seededVersion", 0) >= StarterContentSeedVersion) return

        runCatching {
            StarterContentImporter.fromAssets(getApplication<Application>().assets)
        }.onSuccess { pack ->
            repository.seedStarterContent(pack)
            prefs.edit().putInt("seededVersion", StarterContentSeedVersion).apply()
        }.onFailure { error ->
            _state.update { it.copy(message = error.message?.let(UiText::Dynamic) ?: uiText(R.string.message_starter_content_import_failed)) }
        }
    }

    private companion object {
        const val StarterContentSeedVersion = 2
        const val AiConnectTimeoutMillis = 15_000
        const val AiReadTimeoutMillis = 45_000
    }
}

private fun LearningUiState.withNotice(title: UiText, body: UiText): LearningUiState =
    copy(
        notices = (
            listOf(
                AppNotice(
                    id = "notice-${System.currentTimeMillis()}-${notices.size}",
                    title = title,
                    body = body
                )
            ) + notices
        ).take(6)
    )
