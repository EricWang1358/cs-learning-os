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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    val captureDraft: String = "",
    val captureTopicHint: String = "",
    val captureSourceLabel: String = "",
    val captureType: CaptureSlipType = CaptureSlipType.unclear,
    val aiProviderSettings: AiProviderSettings = AiProviderSettings(),
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

    init {
        _state.update { it.copy(aiProviderSettings = loadAiProviderSettings()) }
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
            repository.deleteNode(node.id)
            _state.update {
                it.copy(
                    screen = AppScreen.Home,
                    selectedNode = null,
                    editorNodeId = null,
                    editorSourceCaptureSlipId = null,
                    editorTitle = "",
                    editorBody = "",
                    message = "Node moved out of your active library"
                )
            }
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
                val remaining = current.dueQuizzes.filterNot { it.id == quiz.id }
                current.copy(
                    selectedQuiz = remaining.firstOrNull(),
                    quizAnswerVisible = false,
                    message = "Review saved"
                )
            }
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
        updateAiSettings { it.copy(provider = value) }
    }

    fun setAiApiKey(value: String) {
        updateAiSettings { it.copy(apiKey = value) }
    }

    fun setAiBaseUrl(value: String) {
        updateAiSettings { it.copy(baseUrl = value) }
    }

    fun setAiModel(value: String) {
        updateAiSettings { it.copy(model = value) }
    }

    fun setAiThinkingEnabled(value: Boolean) {
        updateAiSettings { it.copy(thinkingEnabled = value) }
    }

    fun toggleAiKeyVisibility() {
        _state.update {
            it.copy(aiProviderSettings = it.aiProviderSettings.copy(apiKeyVisible = !it.aiProviderSettings.apiKeyVisible))
        }
    }

    fun validateAiSettings() {
        val settings = state.value.aiProviderSettings
        _state.update {
            it.copy(
                message = if (settings.isConfigured) {
                    "AI provider saved locally. Network validation will be enabled with the AI draft feature."
                } else {
                    "Add provider, API key, base URL, and model before using AI expansion."
                }
            )
        }
    }

    fun pullAiModels() {
        _state.update {
            it.copy(message = "Model pulling is staged for the future AI adapter; no network request was sent.")
        }
    }

    private fun updateAiSettings(reducer: (AiProviderSettings) -> AiProviderSettings) {
        _state.update { current ->
            val next = reducer(current.aiProviderSettings)
            saveAiProviderSettings(next)
            current.copy(aiProviderSettings = next, message = "")
        }
    }

    private fun loadAiProviderSettings(): AiProviderSettings =
        AiProviderSettings(
            provider = aiPrefs.getString("provider", null) ?: "DeepSeek",
            apiKey = aiPrefs.getString("apiKey", null) ?: "",
            baseUrl = aiPrefs.getString("baseUrl", null) ?: "https://api.deepseek.com/v1",
            model = aiPrefs.getString("model", null) ?: "deepseek-v4-flash",
            thinkingEnabled = aiPrefs.getBoolean("thinkingEnabled", false)
        )

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
    }
}
