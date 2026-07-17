package com.cslearningos.mobile.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cslearningos.mobile.R
import com.cslearningos.mobile.assistant.impl.AssistantEntryPolicy
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
import com.cslearningos.mobile.content.room.RoomContentCommandAdapter
import com.cslearningos.mobile.core.common.AndroidArchitectureConstants
import com.cslearningos.mobile.domain.ReviewRating
import com.cslearningos.mobile.feature.backup.data.LearningRepositoryBackupRepository
import com.cslearningos.mobile.feature.backup.domain.ExportBackupUseCase
import com.cslearningos.mobile.feature.backup.domain.RestoreBackupUseCase
import com.cslearningos.mobile.feature.backup.ui.resetTransientStateAfterRestore
import com.cslearningos.mobile.feature.assistant.data.OpenAiCompatibleKnowledgeAssistantService
import com.cslearningos.mobile.feature.assistant.domain.parseAssistantDraftPlacement
import com.cslearningos.mobile.feature.assistant.ui.AssistantAppBridge
import com.cslearningos.mobile.feature.assistant.ui.AssistantCoordinator
import com.cslearningos.mobile.feature.capture.domain.GenerateCaptureDraftUseCase
import com.cslearningos.mobile.feature.capture.domain.captureAssistantAreaOptions
import com.cslearningos.mobile.feature.settings.data.OpenAiCompatibleDraftService
import com.cslearningos.mobile.feature.settings.data.SettingsPreferencesStore
import com.cslearningos.mobile.feature.settings.data.safeAiError
import com.cslearningos.mobile.feature.settings.domain.ValidateAiSettingsUseCase
import com.cslearningos.mobile.feature.settings.ui.SettingsUiState
import com.cslearningos.mobile.feature.settings.ui.SettingsViewModel
import com.cslearningos.mobile.feature.settings.ui.toAiProviderSettings
import com.cslearningos.mobile.ui.backup.BackupDocument
import com.cslearningos.mobile.ui.backup.BackupTransferCoordinator
import com.cslearningos.mobile.ui.backup.backupImportErrorKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LearningViewModel private constructor(
    application: Application,
    private val database: LearningDatabase?,
    private val repository: LearningRepository,
    initialState: LearningUiState,
    private val startInitialObservers: Boolean,
    private val syncGateway: com.cslearningos.mobile.feature.sync.SyncGateway?
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, LearningDatabase.create(application))

    private constructor(application: Application, database: LearningDatabase) : this(
        application = application,
        database = database,
        repository = LearningRepository(
            database = database,
            contentCommands = RoomContentCommandAdapter(database)
        ),
        initialState = LearningUiState(),
        startInitialObservers = true,
        syncGateway = com.cslearningos.mobile.feature.sync.DefaultSyncGateway(
            context = application,
            dao = database.learningDao(),
            deviceName = android.os.Build.MODEL ?: "android"
        )
    )

    internal constructor(
        application: Application,
        repository: LearningRepository,
        initialState: LearningUiState,
        syncGateway: com.cslearningos.mobile.feature.sync.SyncGateway? = null
    ) : this(
        application = application,
        database = null,
        repository = repository,
        initialState = initialState,
        startInitialObservers = false,
        syncGateway = syncGateway
    )

    private val _state = MutableStateFlow(initialState)
    private val dueReviewNow = MutableStateFlow(System.currentTimeMillis())
    val state: StateFlow<LearningUiState> = _state.asStateFlow()
    private val validateAiSettingsUseCase = ValidateAiSettingsUseCase()
    private val aiDraftService = OpenAiCompatibleDraftService()
    private val generateCaptureDraftUseCase = GenerateCaptureDraftUseCase(aiDraftService)
    private val settingsViewModel = SettingsViewModel(
        store = SettingsPreferencesStore(
            aiPrefs = application.getSharedPreferences("ai-provider-settings", Context.MODE_PRIVATE),
            appPrefs = application.getSharedPreferences("app-settings", Context.MODE_PRIVATE)
        ),
        aiDraftService = aiDraftService,
        validateAiSettings = validateAiSettingsUseCase,
        missingFieldLabelResolver = ::localizedFieldLabelList,
        scope = viewModelScope
    )
    private val backupRepository = LearningRepositoryBackupRepository(repository)
    private val exportBackupUseCase = ExportBackupUseCase(backupRepository)
    private val restoreBackupUseCase = RestoreBackupUseCase(backupRepository)
    private val assistantCoordinator = AssistantCoordinator(
        repository = repository,
        service = OpenAiCompatibleKnowledgeAssistantService(),
        string = { resourceId -> localizedAppContext().getString(resourceId) },
        scope = viewModelScope
    )
    val assistantActions = AssistantAppBridge(
        coordinator = assistantCoordinator,
        currentSettings = { state.value.aiProviderSettings },
        updateState = { transform -> _state.update(transform) },
        scope = viewModelScope,
        onOpenNode = ::openNode,
        onOpenDailyReview = ::showReview,
        onShowAssistant = { request ->
            if (AssistantEntryPolicy.shouldReset(request)) {
                showAssistantFresh()
            } else {
                showAssistantPreservingConversation()
            }
        }
    )

    init {
        refreshSyncState()
        if (startInitialObservers) startInitialObservers()
    }

    fun pairSync(endpoint: String, token: String) {
        val gateway = syncGateway ?: return
        viewModelScope.launch {
            _state.update { it.copy(sync = it.sync.copy(busy = true, error = null)) }
            runCatching { gateway.pair(endpoint, token, deviceName()) }
                .onSuccess {
                    refreshSyncState(busy = false)
                    _state.update { it.copy(message = uiText(R.string.sync_pair_success)) }
                }
                .onFailure { error ->
                    refreshSyncState(busy = false, error = syncErrorResId(error))
                }
        }
    }

    fun unpairSync() {
        val gateway = syncGateway ?: return
        viewModelScope.launch {
            runCatching { gateway.unpair() }
            _state.update {
                it.copy(
                    sync = SyncUiState(),
                    message = uiText(R.string.sync_unpaired)
                )
            }
        }
    }

    fun pullSyncNow() {
        val gateway = syncGateway ?: return
        viewModelScope.launch {
            _state.update { it.copy(sync = it.sync.copy(busy = true, error = null)) }
            runCatching { gateway.pull() }
                .onSuccess { report ->
                    refreshSyncState(busy = false)
                    _state.update {
                        it.copy(
                            sync = it.sync.copy(lastPullReport = report),
                            message = uiText(R.string.sync_pull_success, report.totalApplied, report.conflicts)
                        )
                    }
                }
                .onFailure { error ->
                    refreshSyncState(busy = false, error = syncErrorResId(error))
                }
        }
    }

    fun uploadSyncNow() {
        val gateway = syncGateway ?: return
        viewModelScope.launch {
            _state.update { it.copy(sync = it.sync.copy(busy = true, error = null)) }
            runCatching { gateway.push() }
                .onSuccess { report ->
                    refreshSyncState(busy = false)
                    _state.update {
                        it.copy(
                            sync = it.sync.copy(lastPushReport = report),
                            message = uiText(R.string.sync_push_success, report.totalUploaded, report.rejected)
                        )
                    }
                }
                .onFailure { error ->
                    refreshSyncState(busy = false, error = syncErrorResId(error))
                }
        }
    }

    fun updateSyncScope(areas: Set<String>, includeDueReviews: Boolean) {
        val gateway = syncGateway ?: return
        gateway.updateScope(areas, includeDueReviews)
        refreshSyncState()
    }

    private fun refreshSyncState(busy: Boolean? = null, error: Int? = null) {
        val snapshot = syncGateway?.statusSnapshot()
        _state.update { current ->
            current.copy(
                sync = current.sync.copy(
                    isPaired = snapshot?.isPaired ?: false,
                    endpoint = snapshot?.endpoint.orEmpty(),
                    serverId = snapshot?.serverId.orEmpty(),
                    lastSyncAt = snapshot?.lastSyncAt ?: 0L,
                    scopeAreas = snapshot?.scopeAreas ?: emptySet(),
                    includeDueReviews = snapshot?.includeDueReviews ?: false,
                    serverScopes = snapshot?.serverScopes ?: emptySet(),
                    serverPolicyConfirmed = snapshot?.isServerConfirmed ?: false,
                    busy = busy ?: false,
                    error = error?.let { resId -> localizedAppContext().getString(resId) }
                )
            )
        }
    }

    private fun syncErrorResId(error: Throwable): Int = when (error) {
        is com.cslearningos.mobile.feature.sync.SyncException ->
            if (error.statusCode == 401 && error.message?.contains("not_allowed") == true) {
                R.string.sync_error_permission_changed
            } else if (error.statusCode == 401) {
                R.string.sync_error_pairing_failed
            } else {
                R.string.sync_error_server
            }
        else -> R.string.sync_error_unreachable
    }

    private fun deviceName(): String = android.os.Build.MODEL ?: "android"

    // ---- Sync package import (Phase 5) ----

    private var pendingPackagePlan: com.cslearningos.mobile.feature.sync.SyncPackageImporter.ImportPlan? = null

    fun onSharedPackage(bytes: ByteArray) {
        val dao = database?.learningDao() ?: return
        viewModelScope.launch {
            runCatching {
                val preview = com.cslearningos.mobile.feature.sync.SyncPackageImporter.parse(bytes)
                com.cslearningos.mobile.feature.sync.SyncPackageImporter.planImport(
                    dao,
                    preview,
                    System.currentTimeMillis()
                )
            }
                .onSuccess { plan ->
                    pendingPackagePlan = plan
                    _state.update {
                        it.copy(
                            screen = AppScreen.Backup,
                            pendingPackageImport = PackageImportUiState(
                                nodeCount = plan.preview.nodes.size,
                                quizCount = plan.preview.quizzes.size,
                                captureCount = plan.preview.captureSlips.size,
                                added = plan.added,
                                updated = plan.updated,
                                conflicted = plan.conflicted,
                                skipped = plan.skipped,
                                exportedAt = plan.preview.exportedAt
                            ),
                            message = null
                        )
                    }
                }
                .onFailure { error ->
                    pendingPackagePlan = null
                    _state.update {
                        it.copy(message = uiText(packageImportErrorResId(error)))
                    }
                }
        }
    }

    fun confirmPackageImport() {
        val dao = database?.learningDao() ?: return
        val plan = pendingPackagePlan ?: return
        viewModelScope.launch {
            runCatching {
                com.cslearningos.mobile.feature.sync.SyncPackageImporter.applyImport(dao, plan)
            }
                .onSuccess {
                    pendingPackagePlan = null
                    _state.update {
                        it.copy(
                            pendingPackageImport = null,
                            message = uiText(R.string.sync_package_applied, plan.applicable)
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(message = uiText(R.string.sync_package_error_invalid))
                    }
                }
        }
    }

    fun dismissPackageImport() {
        pendingPackagePlan = null
        _state.update { it.copy(pendingPackageImport = null) }
    }

    private fun packageImportErrorResId(error: Throwable): Int = when (error) {
        is com.cslearningos.mobile.feature.sync.SyncPackageException -> when (error.message) {
            "package_too_large" -> R.string.sync_package_error_too_large
            "version_unsupported" -> R.string.sync_package_error_wrong_version
            else -> R.string.sync_package_error_invalid
        }
        else -> R.string.sync_package_error_invalid
    }

    private fun startInitialObservers() {
        viewModelScope.launch {
            settingsViewModel.uiState.collect { settingsState ->
                _state.update { current ->
                    current.copy(
                        aiProviderSettings = settingsState.toAiProviderSettings(),
                        aiServiceStatus = settingsState.aiServiceStatus,
                        availableAiModels = settingsState.availableModels,
                        aiBusy = settingsState.isBusy,
                        systemLanguage = settingsState.systemLanguage,
                        appearanceMode = settingsState.appearanceMode,
                        message = if (settingsState.message != null || current.screen == AppScreen.More) {
                            settingsState.message
                        } else {
                            current.message
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            assistantCoordinator.state.collect { assistant ->
                _state.update { current -> current.copy(assistant = assistant) }
                assistant.pendingAutoOpenMessageId?.let { messageId ->
                    assistantActions.consumePendingAutoOpen(messageId)
                    when (assistant.messages.firstOrNull { it.id == messageId }?.action) {
                        is com.cslearningos.mobile.feature.assistant.ui.AssistantMessageAction.OpenEditableDraft -> assistantActions.openDraft(messageId)
                        is com.cslearningos.mobile.feature.assistant.ui.AssistantMessageAction.OpenEditableQuizDraft -> assistantActions.openQuizDraft(messageId)
                        is com.cslearningos.mobile.feature.assistant.ui.AssistantMessageAction.OpenNewQuizDraft -> assistantActions.openQuizDraft(messageId)
                        is com.cslearningos.mobile.feature.assistant.ui.AssistantMessageAction.OpenEditableCaptureDraft -> assistantActions.openCaptureDraft(messageId)
                        else -> Unit
                    }
                }
            }
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
                        selectedNode = current.selectedNode?.let { selected -> nodes.firstOrNull { it.id == selected.id } },
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
                delay(AndroidArchitectureConstants.DueReviewRefreshIntervalMillis)
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
        viewModelScope.launch {
            repository.archivedCaptureSlips.collect { slips ->
                _state.update { it.copy(archivedCaptureSlips = slips) }
            }
        }
    }

    fun showHome() {
        refreshDueReviews()
        _state.update { it.copy(screen = AppScreen.Home, message = null) }
    }

    fun showAssistant() {
        showAssistantPreservingConversation()
    }

    fun showAssistantFresh() {
        assistantActions.newChat()
        showAssistantPreservingConversation()
    }

    fun showAssistantPreservingConversation() {
        _state.update { it.copy(screen = AppScreen.Assistant, message = null) }
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

    fun showAssistantGuide() {
        _state.update { it.copy(screen = AppScreen.AssistantGuide, message = null) }
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
        _state.update { it.forNewNodeEditor(areaId) }
    }

    fun editNode(node: LearningNodeEntity) {
        _state.update { it.forExistingNodeEditor(node) }
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
        _state.update { it.withEditorTitle(value) }
    }

    fun setEditorBody(value: String) {
        _state.update { it.withEditorBody(value) }
    }

    fun setEditorAreaId(value: String) {
        _state.update { it.withEditorAreaId(value) }
    }

    fun saveNode() {
        val snapshot = state.value
        if (snapshot.editorTitle.isBlank() && snapshot.editorBody.isBlank()) {
            _state.update { it.copy(message = uiText(R.string.message_add_title_or_markdown)) }
            return
        }
        if (snapshot.editorNodeId == null && snapshot.editorAreaId == null) {
            _state.update { it.copy(message = uiText(R.string.message_choose_area_before_save)) }
            return
        }
        _state.update { it.withPendingNodeSave() }
        val commandSnapshot = state.value
        val pending = commandSnapshot.pendingNodeSave ?: return
        viewModelScope.launch {
            runCatching {
                repository.saveNodeFromEditor(commandSnapshot)
            }.onSuccess { node ->
                var applied = false
                _state.update { current ->
                    current.afterNodeSavedIfPendingMatches(pending, node).also {
                        applied = it !== current
                    }
                }
                if (!applied) return@onSuccess
                commandSnapshot.editorSourceCaptureSlipId?.let { slipId ->
                    repository.markCaptureSlipConverted(slipId = slipId, nodeId = node.id)
                }
                refreshDueReviews(node.updatedAt)
            }.onFailure {
                _state.update { it.withObjectSaveRejectedIfPendingMatches(pending) }
            }
        }
    }

    fun cancelEditor() {
        _state.update(LearningUiState::cancelNodeEditor)
    }

    fun reviseEditorDraftWithAssistant() {
        val snapshot = state.value
        val fallbackTitle = snapshot.editorTitle.ifBlank { "Working Draft" }
        assistantActions.reviseNodeDraft(
            nodeId = snapshot.editorNodeId,
            expectedRevision = snapshot.editorExpectedRevision,
            titleHint = fallbackTitle,
            markdown = snapshot.editorBody,
            areaId = snapshot.editorAreaId
        )
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
                    editorExpectedRevision = null,
                    editorSourceCaptureSlipId = null,
                    editorTitle = "",
                    editorBody = "",
                    pendingNodeSave = null,
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
        _state.update(LearningUiState::forNewQuizEditor)
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

    fun setQuizAreaId(value: String) {
        _state.update { it.copy(quizAreaId = value, message = null) }
    }

    fun editQuiz(quiz: QuizItemEntity) {
        _state.update { it.forExistingQuizEditorWithoutSelectedNode(quiz) }
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

    fun editCaptureSlip(slip: CaptureSlipEntity) {
        _state.update { it.forExistingCaptureEditor(slip) }
    }

    fun saveCaptureSlip() {
        val snapshot = state.value
        if (snapshot.captureDraft.isBlank()) {
            _state.update { it.copy(message = uiText(R.string.message_write_capture_before_save)) }
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.saveCaptureFromEditor(snapshot)
            }.onSuccess {
                _state.update { it.afterCaptureSaved() }
            }.onFailure {
                _state.update { it.withObjectSaveRejected() }
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
                id = snapshot.captureEditorId,
                expectedRevision = snapshot.captureExpectedRevision,
                body = snapshot.captureDraft,
                type = snapshot.captureType,
                topicHint = snapshot.captureTopicHint,
                sourceLabel = snapshot.captureSourceLabel,
                status = CaptureSlipStatus.inbox
            )
            _state.update {
                it.clearedCaptureEditor().copy(
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
        val missing = missingFieldNames(settings)
        if (missing.isNotEmpty()) {
            _state.update {
                it.copy(
                    screen = AppScreen.More,
                    expandedMoreSection = MoreSectionId.Service,
                    aiServiceStatus = AiServiceStatus(
                        kind = AiServiceStatusKind.Warning,
                        title = uiText(R.string.ai_status_setup_needed_title),
                        body = uiText(R.string.ai_status_setup_needed_body, UiText.Dynamic(localizedFieldLabelList(missing)))
                    ),
                    message = uiText(R.string.message_configure_ai_before_drafting)
                )
            }
            return
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
            val archived = repository.archiveCaptureSlip(slip.id)
            _state.update {
                it.copy(
                    message = if (archived != null) {
                        uiText(R.string.message_capture_archived)
                    } else {
                        uiText(R.string.message_assistant_source_unavailable)
                    }
                )
            }
        }
    }

    fun restoreCaptureSlip(slip: CaptureSlipEntity) {
        viewModelScope.launch {
            val restored = repository.restoreCaptureSlip(slip.id)
            _state.update {
                it.copy(
                    message = if (restored != null) {
                        uiText(R.string.message_capture_restored)
                    } else {
                        uiText(R.string.message_assistant_source_unavailable)
                    }
                )
            }
        }
    }

    fun permanentlyDeleteCaptureSlip(slip: CaptureSlipEntity) {
        viewModelScope.launch {
            val deleted = repository.permanentlyDeleteCaptureSlip(slip.id)
            _state.update {
                it.copy(
                    message = if (deleted != null) {
                        uiText(R.string.message_capture_deleted_forever)
                    } else {
                        uiText(R.string.message_assistant_source_unavailable)
                    }
                )
            }
        }
    }

    fun promoteCaptureSlipToNode(slip: CaptureSlipEntity) {
        val draft = CaptureNodeDraft.fromSlip(slip, existingNodes = state.value.nodes)
        _state.update { it.forCapturePromotionEditor(slip, draft) }
    }

    fun draftCaptureSlipWithAi(slip: CaptureSlipEntity) {
        val snapshot = state.value
        val settings = snapshot.aiProviderSettings
        val missing = missingFieldNames(settings)
        if (missing.isNotEmpty()) {
            _state.update {
                it.copy(
                    screen = AppScreen.More,
                    expandedMoreSection = MoreSectionId.Service,
                    aiServiceStatus = AiServiceStatus(
                        kind = AiServiceStatusKind.Warning,
                        title = uiText(R.string.ai_status_setup_needed_title),
                        body = uiText(R.string.ai_status_setup_needed_body, UiText.Dynamic(localizedFieldLabelList(missing)))
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
                generateCaptureDraftUseCase(
                    settings = settings,
                    slip = slip,
                    existingNodeTitles = snapshot.nodes.map { it.title },
                    areas = captureAssistantAreaOptions(snapshot.areas, snapshot.nodes)
                )
            }.onSuccess { markdown ->
                repository.updateCaptureSlipStatus(slip.id, CaptureSlipStatus.ai_draft_ready)
                val fallbackTitle = slip.topicHint?.takeIf { it.isNotBlank() } ?: "Capture Draft"
                val placement = parseAssistantDraftPlacement(markdown, captureAssistantAreaOptions(snapshot.areas, snapshot.nodes))
                val draft = assistantMarkdownDraft(placement.markdown, fallbackTitle)
                _state.update {
                    it.forAiCaptureDraftEditor(
                        slip = slip,
                        areaId = placement.areaId,
                        title = draft.title,
                        body = draft.body
                    ).copy(
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
        if (snapshot.quizEditorId == null && snapshot.quizNodeIdForSave() == null && snapshot.quizAreaIdForSave() == null) {
            _state.update { it.copy(message = uiText(R.string.message_choose_quiz_area_before_save)) }
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.saveQuizFromEditor(snapshot)
            }.onSuccess { quiz ->
                _state.update { it.afterQuizSaved(quiz) }
                refreshDueReviews(quiz.updatedAt)
            }.onFailure {
                _state.update { it.withObjectSaveRejected() }
            }
        }
    }

    fun showReview() {
        refreshDueReviews()
        _state.update { it.showReviewScreen() }
    }

    fun startReviewForArea(areaId: String?) {
        _state.update { it.startReviewSessionForArea(areaId) }
    }

    fun startReviewForQuiz(quiz: QuizItemEntity, areaId: String?) {
        _state.update { it.startReviewSessionForQuiz(quiz, areaId) }
    }

    fun selectReviewArea(areaId: String?) {
        _state.update { it.selectReviewSessionArea(areaId) }
    }

    fun startSelectedReview() {
        startReviewForArea(state.value.reviewAreaId)
    }

    private fun refreshDueReviews(now: Long = System.currentTimeMillis()) {
        dueReviewNow.value = now
    }

    fun revealCurrentQuizAnswer() {
        _state.update { it.revealReviewAnswer() }
    }

    fun answerCurrentQuiz(rating: ReviewRating) {
        val quiz = state.value.selectedQuiz ?: return
        viewModelScope.launch {
            repository.answerQuiz(quiz.id, rating)
            _state.update { current ->
                current.afterReviewAnswered(
                    quiz = quiz,
                    rating = rating,
                    savedMessage = reviewSavedMessage(rating)
                )
            }
            refreshDueReviews()
        }
    }

    private fun reviewSavedMessage(rating: ReviewRating): UiText =
        when (rating) {
            ReviewRating.Again -> uiText(R.string.message_review_saved_again)
            ReviewRating.Hard -> uiText(R.string.message_review_saved_hard)
            ReviewRating.Good -> uiText(R.string.message_review_saved_good)
        }

    fun retryReviewedQuiz() {
        _state.update { it.retryReviewedQuiz() }
    }

    fun navigateReviewPrompt(step: Int) {
        _state.update { it.navigateReviewPrompt(step) }
    }

    suspend fun createBackupDocument(now: Long = System.currentTimeMillis()): BackupDocument =
        BackupTransferCoordinator.createBackupDocument(
            rawJson = exportBackupUseCase(now),
            exportedAt = now
        )

    fun restoreBackupFromJson(rawJson: String) {
        viewModelScope.launch {
            runCatching { restoreBackupUseCase(rawJson) }
                .onSuccess {
                    _state.update { current ->
                        current.resetTransientStateAfterRestore().copy(
                            screen = AppScreen.Home,
                            message = uiText(R.string.message_backup_restored)
                        )
                    }
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
        _state.update { it.copy(message = null) }
        settingsViewModel.setProvider(value)
    }

    fun setAiApiKey(value: String) {
        _state.update { it.copy(message = null) }
        settingsViewModel.setApiKey(value)
    }

    fun setAiBaseUrl(value: String) {
        _state.update { it.copy(message = null) }
        settingsViewModel.setBaseUrl(value)
    }

    fun setAiModel(value: String) {
        _state.update { it.copy(message = null) }
        settingsViewModel.setModel(value)
    }

    fun setAiThinkingEnabled(value: Boolean) {
        _state.update { it.copy(message = null) }
        settingsViewModel.setThinkingEnabled(value)
    }

    fun toggleAiKeyVisibility() {
        settingsViewModel.toggleApiKeyVisibility()
    }

    fun validateAiSettings() {
        settingsViewModel.validateService()
    }

    fun saveAiSettings() {
        settingsViewModel.save()
    }

    fun pullAiModels() {
        settingsViewModel.pullModels()
    }

    fun selectAiModel(modelId: String) {
        _state.update { it.copy(message = null) }
        settingsViewModel.selectModel(modelId)
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
            current.copy(
                selectedLibraryAreaId = if (current.selectedLibraryAreaId == area) null else area,
                libraryCheckedFilter = LibraryCheckedFilter.All,
                message = null
            )
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
        settingsViewModel.setSystemLanguage(value)
    }

    fun setAppearanceMode(value: AppearanceMode) {
        settingsViewModel.setAppearanceMode(value)
    }

    private fun localizedFieldLabelList(fields: List<String>): String =
        fields.joinToString { field ->
            localizedAppContext().getString(
                when (field) {
                    "provider" -> R.string.more_provider_label
                    "apiKey" -> R.string.more_api_key_label
                    "baseUrl" -> R.string.more_base_url_label
                    "model" -> R.string.more_model_label
                    else -> R.string.more_connection_label
                }
            )
        }

    private fun missingFieldNames(settings: AiProviderSettings): List<String> =
        validateAiSettingsUseCase(
            provider = settings.provider,
            apiKey = settings.apiKey,
            baseUrl = settings.baseUrl,
            model = settings.model
        ).missingFields

    private fun localizedAppContext(): Context {
        val application = getApplication<Application>()
        val systemLanguageTag = application.resources.configuration.locales[0]?.toLanguageTag().orEmpty()
        return application.localizedAppContext(state.value.systemLanguage, systemLanguageTag)
    }

    private suspend fun seedStarterContentIfNeeded() {
        val prefs = getApplication<Application>()
            .getSharedPreferences("starter-content", Context.MODE_PRIVATE)
        if (prefs.getInt("seededVersion", 0) >= AndroidArchitectureConstants.StarterContentSeedVersion) return

        runCatching {
            StarterContentImporter.fromAssets(getApplication<Application>().assets)
        }.onSuccess { pack ->
            repository.seedStarterContent(pack)
            prefs.edit().putInt("seededVersion", AndroidArchitectureConstants.StarterContentSeedVersion).apply()
        }.onFailure { error ->
            _state.update { it.copy(message = error.message?.let(UiText::Dynamic) ?: uiText(R.string.message_starter_content_import_failed)) }
        }
    }
}
