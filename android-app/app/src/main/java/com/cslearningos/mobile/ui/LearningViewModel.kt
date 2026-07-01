package com.cslearningos.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cslearningos.mobile.data.LearningDatabase
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.LearningRepository
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.SearchResultEntity
import com.cslearningos.mobile.domain.ReviewRating
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen {
    Home,
    Reader,
    Editor,
    Search,
    QuizEditor,
    Review,
    Backup
}

data class LearningUiState(
    val screen: AppScreen = AppScreen.Home,
    val nodes: List<LearningNodeEntity> = emptyList(),
    val quizzes: List<QuizItemEntity> = emptyList(),
    val dueQuizzes: List<QuizItemEntity> = emptyList(),
    val selectedNode: LearningNodeEntity? = null,
    val selectedQuiz: QuizItemEntity? = null,
    val editorNodeId: String? = null,
    val editorTitle: String = "",
    val editorBody: String = "",
    val searchQuery: String = "",
    val searchResults: List<SearchResultEntity> = emptyList(),
    val quizPrompt: String = "",
    val quizAnswer: String = "",
    val quizExplanation: String = "",
    val quizAnswerVisible: Boolean = false,
    val backupText: String = "",
    val message: String = ""
)

class LearningViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LearningRepository(
        LearningDatabase.create(application).learningDao()
    )
    private val _state = MutableStateFlow(LearningUiState())
    val state: StateFlow<LearningUiState> = _state.asStateFlow()

    init {
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
            repository.dueQuizzes(System.currentTimeMillis()).collect { due ->
                _state.update { it.copy(dueQuizzes = due) }
            }
        }
    }

    fun showHome() {
        _state.update { it.copy(screen = AppScreen.Home, message = "") }
    }

    fun showSearch() {
        _state.update { it.copy(screen = AppScreen.Search, message = "") }
    }

    fun showBackup() {
        _state.update { it.copy(screen = AppScreen.Backup, message = "") }
    }

    fun startNewNode() {
        _state.update {
            it.copy(
                screen = AppScreen.Editor,
                editorNodeId = null,
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
            _state.update {
                it.copy(
                    screen = AppScreen.Reader,
                    selectedNode = node,
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
        _state.update {
            it.copy(
                screen = AppScreen.Review,
                selectedQuiz = it.dueQuizzes.firstOrNull(),
                quizAnswerVisible = false,
                message = ""
            )
        }
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
}
