package com.cslearningos.mobile.feature.settings.data

import android.content.SharedPreferences
import com.cslearningos.mobile.ui.AppearanceMode
import com.cslearningos.mobile.ui.SystemLanguage

private const val DEFAULT_PROVIDER = "DeepSeek"
private const val DEFAULT_API_KEY = ""
private const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1"
private const val DEFAULT_MODEL = "deepseek-v4-flash"
private const val DEFAULT_THINKING_ENABLED = false
private val DEFAULT_SYSTEM_LANGUAGE = SystemLanguage.FollowSystem
private val DEFAULT_APPEARANCE_MODE = AppearanceMode.FollowSystem

data class AiSettingsSnapshot(
    val provider: String = DEFAULT_PROVIDER,
    val apiKey: String = DEFAULT_API_KEY,
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val thinkingEnabled: Boolean = DEFAULT_THINKING_ENABLED
)

data class AppSettingsSnapshot(
    val systemLanguage: SystemLanguage = DEFAULT_SYSTEM_LANGUAGE,
    val appearanceMode: AppearanceMode = DEFAULT_APPEARANCE_MODE
)

data class SettingsSnapshot(
    val aiSettings: AiSettingsSnapshot = AiSettingsSnapshot(),
    val appSettings: AppSettingsSnapshot = AppSettingsSnapshot()
)

interface SettingsStore {
    fun loadSettings(): SettingsSnapshot

    fun saveSettings(snapshot: SettingsSnapshot)
}

class SettingsPreferencesStore(
    private val aiPrefs: SharedPreferences,
    private val appPrefs: SharedPreferences
) : SettingsStore {
    override fun loadSettings(): SettingsSnapshot =
        SettingsSnapshot(
            aiSettings = AiSettingsSnapshot(
                provider = aiPrefs.getString(KEY_PROVIDER, null) ?: DEFAULT_PROVIDER,
                apiKey = aiPrefs.getString(KEY_API_KEY, null) ?: DEFAULT_API_KEY,
                baseUrl = aiPrefs.getString(KEY_BASE_URL, null) ?: DEFAULT_BASE_URL,
                model = aiPrefs.getString(KEY_MODEL, null) ?: DEFAULT_MODEL,
                thinkingEnabled = aiPrefs.getBoolean(KEY_THINKING_ENABLED, DEFAULT_THINKING_ENABLED)
            ),
            appSettings = AppSettingsSnapshot(
                systemLanguage = readEnum(
                    rawValue = appPrefs.getString(KEY_SYSTEM_LANGUAGE, null),
                    defaultValue = DEFAULT_SYSTEM_LANGUAGE
                ),
                appearanceMode = readEnum(
                    rawValue = appPrefs.getString(KEY_APPEARANCE_MODE, null),
                    defaultValue = DEFAULT_APPEARANCE_MODE
                )
            )
        )

    override fun saveSettings(snapshot: SettingsSnapshot) {
        aiPrefs.edit()
            .putString(KEY_PROVIDER, snapshot.aiSettings.provider)
            .putString(KEY_API_KEY, snapshot.aiSettings.apiKey)
            .putString(KEY_BASE_URL, snapshot.aiSettings.baseUrl)
            .putString(KEY_MODEL, snapshot.aiSettings.model)
            .putBoolean(KEY_THINKING_ENABLED, snapshot.aiSettings.thinkingEnabled)
            .apply()

        appPrefs.edit()
            .putString(KEY_SYSTEM_LANGUAGE, snapshot.appSettings.systemLanguage.name)
            .putString(KEY_APPEARANCE_MODE, snapshot.appSettings.appearanceMode.name)
            .apply()
    }

    private inline fun <reified T : Enum<T>> readEnum(rawValue: String?, defaultValue: T): T =
        enumValues<T>().firstOrNull { it.name == rawValue } ?: defaultValue

    private companion object {
        const val KEY_PROVIDER = "provider"
        const val KEY_API_KEY = "apiKey"
        const val KEY_BASE_URL = "baseUrl"
        const val KEY_MODEL = "model"
        const val KEY_THINKING_ENABLED = "thinkingEnabled"
        const val KEY_SYSTEM_LANGUAGE = "systemLanguage"
        const val KEY_APPEARANCE_MODE = "appearanceMode"
    }
}
