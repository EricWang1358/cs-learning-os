package com.cslearningos.mobile.feature.settings.ui

import com.cslearningos.mobile.R
import com.cslearningos.mobile.feature.settings.data.AiDraftService
import com.cslearningos.mobile.feature.settings.data.SettingsSnapshot
import com.cslearningos.mobile.feature.settings.data.SettingsStore
import com.cslearningos.mobile.feature.settings.data.safeAiError
import com.cslearningos.mobile.feature.settings.domain.AiSettingsValidationResult
import com.cslearningos.mobile.feature.settings.domain.ValidateAiSettingsUseCase
import com.cslearningos.mobile.ui.AiServiceStatus
import com.cslearningos.mobile.ui.AiServiceStatusKind
import com.cslearningos.mobile.ui.AppearanceMode
import com.cslearningos.mobile.ui.SystemLanguage
import com.cslearningos.mobile.ui.UiText
import com.cslearningos.mobile.ui.aiModelsUrl
import com.cslearningos.mobile.ui.uiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val store: SettingsStore,
    private val aiDraftService: AiDraftService,
    private val validateAiSettings: ValidateAiSettingsUseCase,
    private val missingFieldLabelResolver: (List<String>) -> String = { missingFields: List<String> ->
        missingFields.joinToString()
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(
        store.loadSettings().toValidatedUiState()
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setProvider(value: String) {
        updateEditableState { it.copy(provider = value) }
    }

    fun setApiKey(value: String) {
        if (value.isBlank()) store.clearApiKey()
        updateEditableState { it.copy(apiKey = value) }
    }

    fun setBaseUrl(value: String) {
        updateEditableState { it.copy(baseUrl = value) }
    }

    fun setModel(value: String) {
        updateEditableState { it.copy(model = value) }
    }

    fun selectModel(modelId: String) {
        setModel(modelId)
    }

    fun setThinkingEnabled(value: Boolean) {
        updateEditableState { it.copy(thinkingEnabled = value) }
    }

    fun toggleApiKeyVisibility() {
        _uiState.update { current ->
            current.copy(apiKeyVisible = !current.apiKeyVisible, message = null)
        }
    }

    fun setSystemLanguage(value: SystemLanguage) {
        updateAndPersist { it.copy(systemLanguage = value, message = uiText(R.string.message_language_saved)) }
    }

    fun setAppearanceMode(value: AppearanceMode) {
        updateAndPersist { it.copy(appearanceMode = value, message = uiText(R.string.message_appearance_saved)) }
    }

    fun save() {
        val failure = persistCurrentSnapshot()
        _uiState.update { current ->
            if (failure == null) {
                current.copy(
                    aiServiceStatus = AiServiceStatus(
                        kind = AiServiceStatusKind.Success,
                        title = uiText(R.string.ai_status_settings_saved_title),
                        body = uiText(R.string.ai_status_settings_saved_body)
                    ),
                    message = uiText(R.string.message_ai_settings_saved_locally)
                )
            } else {
                current.withSaveFailure(failure)
            }
        }
    }

    fun validateCurrentSettings(): AiSettingsValidationResult =
        validateState(uiState.value)

    fun validateService() {
        val state = uiState.value
        val validation = validateState(state)
        if (!validation.isValid) {
            _uiState.update { current ->
                current.copy(
                    validation = validation,
                    aiServiceStatus = AiServiceStatus(
                        kind = AiServiceStatusKind.Error,
                        title = uiText(R.string.ai_status_settings_incomplete_title),
                        body = uiText(
                            R.string.ai_status_missing_fields_body,
                            UiText.Dynamic(missingFieldLabelResolver(validation.missingFields))
                        )
                    ),
                    message = uiText(R.string.message_ai_settings_incomplete)
                )
            }
            return
        }

        scope.launch {
            val requestState = uiState.value
            _uiState.update { current ->
                current.copy(
                    isBusy = true,
                    validation = validation,
                    aiServiceStatus = AiServiceStatus(
                        kind = AiServiceStatusKind.Loading,
                        title = uiText(R.string.ai_status_validating_title),
                        body = uiText(R.string.ai_status_validating_body, aiModelsUrl(requestState.baseUrl))
                    ),
                    message = uiText(R.string.message_validating_ai_service)
                )
            }

            runCatching {
                withContext(ioDispatcher) {
                    aiDraftService.fetchModelIds(requestState.baseUrl, requestState.apiKey)
                }
            }.onSuccess { models ->
                _uiState.update { current ->
                    current.copy(
                        isBusy = false,
                        availableModels = models,
                        aiServiceStatus = AiServiceStatus(
                            kind = AiServiceStatusKind.Success,
                            title = uiText(R.string.ai_status_validated_title),
                            body = if (models.isEmpty()) {
                                uiText(R.string.ai_status_validated_empty_body)
                            } else {
                                uiText(R.string.ai_status_validated_models_body, models.size, requestState.model)
                            }
                        ),
                        message = uiText(R.string.message_ai_validation_succeeded)
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isBusy = false,
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

    fun pullModels() {
        val state = uiState.value
        val validation = validateState(state)
        if (!validation.isValid) {
            _uiState.update { current ->
                current.copy(
                    validation = validation,
                    aiServiceStatus = AiServiceStatus(
                        kind = AiServiceStatusKind.Warning,
                        title = uiText(R.string.ai_status_pull_unavailable_title),
                        body = uiText(
                            R.string.ai_status_missing_fields_body,
                            UiText.Dynamic(missingFieldLabelResolver(validation.missingFields))
                        )
                    ),
                    message = uiText(R.string.message_ai_settings_incomplete)
                )
            }
            return
        }

        scope.launch {
            val requestState = uiState.value
            _uiState.update { current ->
                current.copy(
                    isBusy = true,
                    validation = validation,
                    aiServiceStatus = AiServiceStatus(
                        kind = AiServiceStatusKind.Loading,
                        title = uiText(R.string.ai_status_pull_title),
                        body = uiText(R.string.ai_status_pull_body, aiModelsUrl(requestState.baseUrl))
                    ),
                    message = uiText(R.string.message_pulling_ai_models)
                )
            }

            runCatching {
                withContext(ioDispatcher) {
                    aiDraftService.fetchModelIds(requestState.baseUrl, requestState.apiKey)
                }
            }.onSuccess { models ->
                _uiState.update { current ->
                    current.copy(
                        isBusy = false,
                        availableModels = models,
                        aiServiceStatus = AiServiceStatus(
                            kind = if (models.isEmpty()) AiServiceStatusKind.Warning else AiServiceStatusKind.Success,
                            title = if (models.isEmpty()) {
                                uiText(R.string.ai_status_pull_empty_title)
                            } else {
                                uiText(R.string.ai_status_pull_success_title)
                            },
                            body = if (models.isEmpty()) {
                                uiText(R.string.ai_status_pull_empty_body)
                            } else {
                                uiText(R.string.ai_status_pull_success_body)
                            }
                        ),
                        message = if (models.isEmpty()) {
                            uiText(R.string.message_no_model_ids)
                        } else {
                            uiText(R.string.message_ai_models_pulled)
                        }
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isBusy = false,
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

    private fun updateEditableState(transform: (SettingsUiState) -> SettingsUiState) {
        updateAndPersist { current ->
            transform(current).copy(
                aiServiceStatus = AiServiceStatus(
                    kind = AiServiceStatusKind.Info,
                    title = uiText(R.string.ai_status_autosaved_title, uiText(R.string.more_connection_label)),
                    body = uiText(R.string.ai_status_autosaved_body)
                ),
                message = null
            )
        }
    }

    private fun updateAndPersist(transform: (SettingsUiState) -> SettingsUiState) {
        _uiState.update { current ->
            val updated = transform(current)
            val validated = updated.copy(validation = validateState(updated))
            runCatching { store.saveSettings(validated.toSnapshot()) }
                .exceptionOrNull()
                ?.let { failure -> validated.withSaveFailure(failure) }
                ?: validated
        }
    }

    private fun persistCurrentSnapshot(): Throwable? =
        runCatching { store.saveSettings(uiState.value.toSnapshot()) }.exceptionOrNull()

    private fun SettingsUiState.withSaveFailure(failure: Throwable): SettingsUiState =
        copy(
            aiServiceStatus = AiServiceStatus(
                kind = AiServiceStatusKind.Error,
                title = uiText(R.string.ai_status_settings_save_failed_title),
                body = UiText.Dynamic(failure.safeAiError())
            ),
            message = uiText(R.string.message_ai_settings_save_failed)
        )

    private fun validateState(state: SettingsUiState): AiSettingsValidationResult =
        validateAiSettings(
            provider = state.provider,
            apiKey = state.apiKey,
            baseUrl = state.baseUrl,
            model = state.model
        )

    private fun SettingsSnapshot.toValidatedUiState(): SettingsUiState {
        val uiState = toUiState()
        return uiState.copy(validation = validateState(uiState))
    }
}
