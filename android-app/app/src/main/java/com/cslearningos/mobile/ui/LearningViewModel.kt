package com.cslearningos.mobile.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cslearningos.mobile.data.CaptureSlipEntity
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

data class LearningUiState(
    val screen: AppScreen = AppScreen.Home,
    val nodes: List<LearningNodeEntity> = emptyList(),
    val trashNodes: List<LearningNodeEntity> = emptyList(),
    val quizzes: List<QuizItemEntity> = emptyList(),
    val dueQuizzes: List<QuizItemEntity> = emptyList(),
    val readerQuestions: List<ReaderQuestionEntity> = emptyList(),
    val captureSlips: List<CaptureSlipEntity> = emptyList(),
    val selectedNode: LearningNodeEntity? = null,
    val selectedQuiz: QuizItemEntity? = null,
    val editorNodeId: String? = null,
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
    val systemLanguage: SystemLanguage = SystemLanguage.FollowSystem,
    val appearanceMode: AppearanceMode = AppearanceMode.FollowSystem,
    val expandedMoreSection: MoreSectionId? = MoreSectionId.System,
    val quizAnswerVisible: Boolean = false,
    val backupText: String = "",
    val message: String = ""
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
            repository.nodes.collect { nodes ->
                _state.update { current ->
                    current.copy(
                        nodes = nodes,
                        selectedNode = current.selectedNode?.let { selected ->
                            nodes.firstOrNull { it.id == selected.id } ?: selected
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
        _state.update { it.copy(screen = AppScreen.Home, message = "") }
    }

    fun showLibrary() {
        _state.update { it.copy(screen = AppScreen.Library, message = "") }
    }

    fun showCapture() {
        _state.update { it.copy(screen = AppScreen.Capture, message = "") }
    }

    fun showSearch() {
        _state.update { it.copy(screen = AppScreen.Search, message = "") }
    }

    fun showBackup() {
        _state.update { it.copy(screen = AppScreen.Backup, message = "") }
    }

    fun showMore() {
        _state.update { it.copy(screen = AppScreen.More, message = "") }
    }

    fun showAiServiceSettings() {
        _state.update {
            it.copy(
                screen = AppScreen.More,
                expandedMoreSection = MoreSectionId.Service,
                message = "",
                aiServiceStatus = AiServiceStatus(
                    kind = AiServiceStatusKind.Info,
                    title = "Configure AI service",
                    body = "Settings are saved automatically on this phone. Tap Save settings for confirmation, then Validate or Pull models."
                )
            )
        }
    }

    fun startNewNode() {
        _state.update {
            it.copy(
                screen = AppScreen.Editor,
                editorNodeId = null,
                editorSourceCaptureSlipId = null,
                editorTitle = "",
                editorBody = "",
                message = ""
            )
        }
    }

    fun editNode(node: LearningNodeEntity) {
        _state.update {
            it.copy(
                screen = AppScreen.Editor,
                selectedNode = node,
                editorNodeId = node.id,
                editorSourceCaptureSlipId = null,
                editorTitle = node.title,
                editorBody = node.markdownBody,
                message = ""
            )
        }
    }

    fun openNode(node: LearningNodeEntity) {
        _state.update { it.copy(screen = AppScreen.Reader, selectedNode = node, message = "") }
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
                            message = ""
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
            _state.update { it.copy(message = "Add a title or some Markdown before saving.") }
            return
        }
        viewModelScope.launch {
            val node = repository.saveNode(
                id = snapshot.editorNodeId,
                title = snapshot.editorTitle,
                markdownBody = snapshot.editorBody
            )
            snapshot.editorSourceCaptureSlipId?.let { slipId ->
                repository.markCaptureSlipConverted(slipId = slipId, nodeId = node.id)
            }
            _state.update {
                it.copy(
                    screen = AppScreen.Reader,
                    selectedNode = node,
                    editorSourceCaptureSlipId = null,
                    message = "Node saved"
                )
            }
        }
    }

    fun cancelEditor() {
        _state.update {
            it.copy(
                screen = if (it.selectedNode == null) AppScreen.Home else AppScreen.Reader,
                message = ""
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
                    editorNodeId = null,
                    editorSourceCaptureSlipId = null,
                    editorTitle = "",
                    editorBody = "",
                    message = "Node moved to Trashbin"
                )
            }
        }
    }

    fun restoreNode(node: LearningNodeEntity) {
        viewModelScope.launch {
            repository.restoreNodeFromTrash(node.id)
            _state.update { it.copy(message = "Node restored from Trashbin") }
        }
    }

    fun permanentlyDeleteNode(node: LearningNodeEntity) {
        viewModelScope.launch {
            repository.permanentlyDeleteNode(node.id)
            _state.update { it.copy(message = "Node deleted forever") }
        }
    }

    fun clearStarterContent() {
        viewModelScope.launch {
            repository.clearStarterContent()
            _state.update { it.copy(message = "Starter demo content removed") }
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
                    message = if (query.isBlank()) "Enter a search query" else ""
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
                message = ""
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
        _state.update { it.copy(readerQuestionDraft = value, message = "") }
    }

    fun toggleReaderQuestionPanel() {
        _state.update { it.copy(readerQuestionPanelExpanded = !it.readerQuestionPanelExpanded, message = "") }
    }

    fun saveReaderQuestion() {
        val snapshot = state.value
        val node = snapshot.selectedNode ?: return
        if (snapshot.readerQuestionDraft.isBlank()) {
            _state.update { it.copy(message = "Write the unclear point before saving it.") }
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
                    message = "Question saved for later"
                )
            }
        }
    }

    fun setCaptureDraft(value: String) {
        _state.update { it.copy(captureDraft = value, message = "") }
    }

    fun setCaptureTopicHint(value: String) {
        _state.update { it.copy(captureTopicHint = value, message = "") }
    }

    fun setCaptureSourceLabel(value: String) {
        _state.update { it.copy(captureSourceLabel = value, message = "") }
    }

    fun setCaptureType(value: CaptureSlipType) {
        _state.update { it.copy(captureType = value, message = "") }
    }

    fun saveCaptureSlip() {
        val snapshot = state.value
        if (snapshot.captureDraft.isBlank()) {
            _state.update { it.copy(message = "Write a quick note before saving the capture slip.") }
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
                    message = "Capture slip saved to inbox"
                )
            }
        }
    }

    fun saveCaptureSlipForAiDraft() {
        val snapshot = state.value
        if (snapshot.captureDraft.isBlank()) {
            _state.update { it.copy(message = "Write a quick note before saving it for AI drafting.") }
            return
        }
        viewModelScope.launch {
            val slip = repository.saveCaptureSlip(
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
                    pendingAiDraftSlipId = if (snapshot.aiProviderSettings.isConfigured) slip.id else null,
                    aiServiceStatus = if (snapshot.aiProviderSettings.isConfigured) {
                        AiServiceStatus(
                            kind = AiServiceStatusKind.Info,
                            title = "AI draft preflight",
                            body = "Slip saved. Confirm the target, optionally validate the provider, then generate an editable Markdown node draft."
                        )
                    } else {
                        it.aiServiceStatus
                    },
                    message = if (snapshot.aiProviderSettings.isConfigured) {
                        "Slip saved. Review AI draft preflight."
                    } else {
                        "Slip saved. Configure AI before drafting."
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
                        title = "AI setup needed",
                        body = "Add ${missing.joinToString()} before generating a capture draft."
                    ),
                    message = "Configure AI before drafting."
                )
            }
            return
        }

        _state.update {
            it.copy(
                pendingAiDraftSlipId = slip.id,
                aiServiceStatus = AiServiceStatus(
                    kind = AiServiceStatusKind.Info,
                    title = "AI draft preflight",
                    body = "This will send one capture slip to ${settings.provider} and open the result as an editable Markdown node draft. Nothing becomes a node until Save Markdown."
                ),
                message = "Confirm AI draft when ready."
            )
        }
    }

    fun cancelAiDraftPreflight() {
        _state.update {
            it.copy(
                pendingAiDraftSlipId = null,
                message = "AI draft canceled."
            )
        }
    }

    fun confirmAiDraftPreflight() {
        val slip = state.value.pendingAiDraftSlipId
            ?.let { slipId -> state.value.captureSlips.firstOrNull { it.id == slipId } }
            ?: return
        _state.update { it.copy(pendingAiDraftSlipId = null) }
        draftCaptureSlipWithAi(slip)
    }

    fun archiveCaptureSlip(slip: CaptureSlipEntity) {
        viewModelScope.launch {
            repository.archiveCaptureSlip(slip.id)
            _state.update { it.copy(message = "Capture slip archived") }
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
                message = "Review this capture draft before saving it as Markdown."
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
                        title = "AI setup needed",
                        body = "Add ${missing.joinToString()} before generating a capture draft."
                    ),
                    message = "Configure AI before drafting."
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
                        title = "Drafting from capture",
                        body = "Sending this slip to your configured OpenAI-compatible model. The result will open as an editable Markdown node draft."
                    ),
                    message = "Generating AI draft..."
                )
            }
            runCatching {
                requestCaptureDraft(settings = settings, slip = slip, existingNodeTitles = snapshot.nodes.map { it.title })
            }.onSuccess { markdown ->
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
                            title = "AI draft ready",
                            body = "Review the Markdown, edit anything suspicious, then Save Markdown to convert the slip into a node."
                        ),
                        message = "AI draft ready. Review before saving."
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        aiBusy = false,
                        aiServiceStatus = AiServiceStatus(
                            kind = AiServiceStatusKind.Error,
                            title = "AI draft failed",
                            body = error.safeAiError()
                        ),
                        message = "AI draft failed."
                    )
                }
            }
        }
    }

    fun resolveReaderQuestion(question: ReaderQuestionEntity) {
        viewModelScope.launch {
            repository.resolveReaderQuestion(question.id)
            _state.update { it.copy(message = "Question marked resolved") }
        }
    }

    fun saveQuiz() {
        val snapshot = state.value
        if (snapshot.quizPrompt.isBlank() || snapshot.quizAnswer.isBlank()) {
            _state.update { it.copy(message = "Question and answer are required.") }
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
                it.copy(screen = AppScreen.Home, message = "Quiz saved")
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
                message = ""
            )
        }
    }

    private fun refreshDueReviews(now: Long = System.currentTimeMillis()) {
        dueReviewNow.value = now
    }

    fun revealCurrentQuizAnswer() {
        _state.update { it.copy(quizAnswerVisible = true, message = "") }
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
                    message = "Review saved"
                )
            }
            refreshDueReviews()
        }
    }

    fun exportBackup() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    backupText = repository.exportBackup(),
                    message = "Backup exported"
                )
            }
        }
    }

    fun exportReadableMarkdown() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    screen = AppScreen.Backup,
                    backupText = repository.exportReadableMarkdown(),
                    message = "Readable Markdown/TXT export generated"
                )
            }
        }
    }

    fun setBackupText(value: String) {
        _state.update { it.copy(backupText = value) }
    }

    fun restoreBackup() {
        val raw = state.value.backupText
        viewModelScope.launch {
            runCatching { repository.restoreBackup(raw) }
                .onSuccess {
                    _state.update { it.copy(screen = AppScreen.Home, message = "Backup restored") }
                }
                .onFailure { error ->
                    _state.update { it.copy(message = error.message ?: "Restore failed") }
                }
        }
    }

    fun setAiProvider(value: String) {
        updateAiSettings(savedField = "Provider") { it.copy(provider = value) }
    }

    fun setAiApiKey(value: String) {
        updateAiSettings(savedField = "API key") { it.copy(apiKey = value) }
    }

    fun setAiBaseUrl(value: String) {
        updateAiSettings(savedField = "Base URL") { it.copy(baseUrl = value) }
    }

    fun setAiModel(value: String) {
        updateAiSettings(savedField = "Model") { it.copy(model = value) }
    }

    fun setAiThinkingEnabled(value: Boolean) {
        updateAiSettings(savedField = "Thinking") { it.copy(thinkingEnabled = value) }
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
                        title = "AI settings incomplete",
                        body = "Missing: ${missing.joinToString()}."
                    ),
                    message = "AI settings incomplete."
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
                        title = "Validating AI service",
                        body = "Calling ${aiModelsUrl(settings.baseUrl)} with your saved API key."
                    ),
                    message = "Validating AI service..."
                )
            }
            runCatching { fetchModelIds(settings) }
                .onSuccess { models ->
                    val modelNote = if (models.isEmpty()) {
                        "The endpoint responded, but did not return model IDs."
                    } else {
                        "Found ${models.size} model(s). Current model: ${settings.model}."
                    }
                    _state.update {
                        it.copy(
                            aiBusy = false,
                            availableAiModels = models,
                            aiServiceStatus = AiServiceStatus(
                                kind = AiServiceStatusKind.Success,
                                title = "AI service validated",
                                body = modelNote
                            ),
                            message = "AI validation succeeded."
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            aiBusy = false,
                            aiServiceStatus = AiServiceStatus(
                                kind = AiServiceStatusKind.Error,
                                title = "AI validation failed",
                                body = error.safeAiError()
                            ),
                            message = "AI validation failed."
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
                    title = "AI settings saved",
                    body = "Provider, API key, base URL, model, and thinking preference are saved locally on this phone."
                ),
                message = "AI settings saved locally."
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
                        title = "Cannot pull models yet",
                        body = "Missing: ${missing.joinToString()}."
                    ),
                    message = "AI settings incomplete."
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
                        title = "Pulling model list",
                        body = "Calling ${aiModelsUrl(settings.baseUrl)}. If your provider blocks model listing, type the model manually."
                    ),
                    message = "Pulling AI models..."
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
                                title = if (models.isEmpty()) "No models returned" else "Models pulled",
                                body = if (models.isEmpty()) {
                                    "The endpoint responded but returned no IDs. You can still type a model manually."
                                } else {
                                    "Tap a model below to use it for Capture Slip AI drafts."
                                }
                            ),
                            message = if (models.isEmpty()) "No model IDs returned." else "AI models pulled."
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            aiBusy = false,
                            aiServiceStatus = AiServiceStatus(
                                kind = AiServiceStatusKind.Error,
                                title = "Model pull failed",
                                body = error.safeAiError()
                            ),
                            message = "Model pull failed."
                        )
                    }
                }
        }
    }

    fun selectAiModel(modelId: String) {
        updateAiSettings(savedField = "Model") { it.copy(model = modelId) }
    }

    fun toggleMoreSection(section: MoreSectionId) {
        _state.update {
            it.copy(
                expandedMoreSection = if (it.expandedMoreSection == section) null else section,
                message = ""
            )
        }
    }

    fun setSystemLanguage(value: SystemLanguage) {
        appPrefs.edit().putString("systemLanguage", value.name).apply()
        _state.update { it.copy(systemLanguage = value, message = "Language preference saved locally") }
    }

    fun setAppearanceMode(value: AppearanceMode) {
        appPrefs.edit().putString("appearanceMode", value.name).apply()
        _state.update { it.copy(appearanceMode = value, message = "Display mode saved locally") }
    }

    private fun updateAiSettings(savedField: String, reducer: (AiProviderSettings) -> AiProviderSettings) {
        _state.update { current ->
            val next = reducer(current.aiProviderSettings)
            saveAiProviderSettings(next)
            current.copy(
                aiProviderSettings = next,
                aiServiceStatus = AiServiceStatus(
                    kind = AiServiceStatusKind.Info,
                    title = "$savedField saved automatically",
                    body = "Settings are stored locally as you type. Tap Validate to test the provider before drafting."
                ),
                message = ""
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
            _state.update { it.copy(message = error.message ?: "Starter content import failed") }
        }
    }

    private companion object {
        const val StarterContentSeedVersion = 1
        const val AiConnectTimeoutMillis = 15_000
        const val AiReadTimeoutMillis = 45_000
    }
}
