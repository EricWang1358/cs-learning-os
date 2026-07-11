package com.cslearningos.mobile.feature.settings.ui

import com.cslearningos.mobile.feature.settings.data.AiSettingsSnapshot
import com.cslearningos.mobile.feature.settings.data.AppSettingsSnapshot
import com.cslearningos.mobile.feature.settings.data.SettingsSnapshot
import com.cslearningos.mobile.feature.settings.domain.AiSettingsValidationResult
import com.cslearningos.mobile.ui.AiServiceStatus
import com.cslearningos.mobile.ui.AiProviderSettings
import com.cslearningos.mobile.ui.AppearanceMode
import com.cslearningos.mobile.ui.SystemLanguage
import com.cslearningos.mobile.ui.UiText

data class SettingsUiState(
    val provider: String = DEFAULT_PROVIDER,
    val apiKey: String = DEFAULT_API_KEY,
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val thinkingEnabled: Boolean = DEFAULT_THINKING_ENABLED,
    val apiKeyVisible: Boolean = false,
    val availableModels: List<String> = emptyList(),
    val isBusy: Boolean = false,
    val systemLanguage: SystemLanguage = SystemLanguage.FollowSystem,
    val appearanceMode: AppearanceMode = AppearanceMode.FollowSystem,
    val validation: AiSettingsValidationResult = AiSettingsValidationResult(),
    val aiServiceStatus: AiServiceStatus = AiServiceStatus(),
    val message: UiText? = null
) {
    fun toSnapshot(): SettingsSnapshot =
        SettingsSnapshot(
            aiSettings = AiSettingsSnapshot(
                provider = provider,
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                thinkingEnabled = thinkingEnabled
            ),
            appSettings = AppSettingsSnapshot(
                systemLanguage = systemLanguage,
                appearanceMode = appearanceMode
            )
        )

    companion object {
        const val DEFAULT_PROVIDER = "DeepSeek"
        const val DEFAULT_API_KEY = ""
        const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1"
        const val DEFAULT_MODEL = "deepseek-v4-flash"
        const val DEFAULT_THINKING_ENABLED = false
    }
}

fun SettingsSnapshot.toUiState(
    validation: AiSettingsValidationResult = AiSettingsValidationResult()
): SettingsUiState =
    SettingsUiState(
        provider = aiSettings.provider,
        apiKey = aiSettings.apiKey,
        baseUrl = aiSettings.baseUrl,
        model = aiSettings.model,
        thinkingEnabled = aiSettings.thinkingEnabled,
        apiKeyVisible = false,
        systemLanguage = appSettings.systemLanguage,
        appearanceMode = appSettings.appearanceMode,
        validation = validation
    )

fun SettingsUiState.toAiProviderSettings(): AiProviderSettings =
    AiProviderSettings(
        provider = provider,
        apiKey = apiKey,
        baseUrl = baseUrl,
        model = model,
        thinkingEnabled = thinkingEnabled,
        apiKeyVisible = apiKeyVisible
    )
