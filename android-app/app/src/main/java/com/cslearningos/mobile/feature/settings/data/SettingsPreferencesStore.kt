package com.cslearningos.mobile.feature.settings.data

import android.content.SharedPreferences
import com.cslearningos.mobile.assistant.domain.isValidProviderEndpoint
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

    fun clearApiKey()
}

class SettingsPreferencesStore(
    private val aiPrefs: SharedPreferences,
    private val appPrefs: SharedPreferences,
    private val apiKeyProtector: ApiKeyProtector = AndroidKeystoreApiKeyProtector()
) : SettingsStore {
    override fun loadSettings(): SettingsSnapshot =
        SettingsSnapshot(
            aiSettings = AiSettingsSnapshot(
                provider = aiPrefs.getString(KEY_PROVIDER, null) ?: DEFAULT_PROVIDER,
                apiKey = loadApiKey(),
                baseUrl = loadBaseUrl(),
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
        val apiKey = snapshot.aiSettings.apiKey
        val encryptedApiKey = apiKey.takeIf(String::isNotBlank)?.let(apiKeyProtector::encrypt)
        val baseUrl = safeBaseUrl(snapshot.aiSettings.baseUrl)
        val aiEditor = aiPrefs.edit()
            .putString(KEY_PROVIDER, snapshot.aiSettings.provider)
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_MODEL, snapshot.aiSettings.model)
            .putBoolean(KEY_THINKING_ENABLED, snapshot.aiSettings.thinkingEnabled)
        if (encryptedApiKey != null) {
            aiEditor.putString(KEY_API_KEY_ENCRYPTED, encryptedApiKey)
            aiEditor.remove(KEY_API_KEY)
        }
        aiEditor
            .apply()

        appPrefs.edit()
            .putString(KEY_SYSTEM_LANGUAGE, snapshot.appSettings.systemLanguage.name)
            .putString(KEY_APPEARANCE_MODE, snapshot.appSettings.appearanceMode.name)
            .apply()
    }

    override fun clearApiKey() {
        aiPrefs.edit()
            .remove(KEY_API_KEY_ENCRYPTED)
            .remove(KEY_API_KEY)
            .apply()
    }

    private fun loadApiKey(): String {
        val encryptedApiKey = aiPrefs.getString(KEY_API_KEY_ENCRYPTED, null)
        if (encryptedApiKey != null) return apiKeyProtector.decrypt(encryptedApiKey) ?: DEFAULT_API_KEY

        val legacyApiKey = aiPrefs.getString(KEY_API_KEY, null) ?: return DEFAULT_API_KEY
        val migratedApiKey = runCatching { apiKeyProtector.encrypt(legacyApiKey) }.getOrNull()
            ?: return DEFAULT_API_KEY
        aiPrefs.edit()
            .putString(KEY_API_KEY_ENCRYPTED, migratedApiKey)
            .remove(KEY_API_KEY)
            .apply()
        return legacyApiKey
    }

    private fun loadBaseUrl(): String {
        val storedBaseUrl = aiPrefs.getString(KEY_BASE_URL, null) ?: return DEFAULT_BASE_URL
        val safeBaseUrl = safeBaseUrl(storedBaseUrl)
        if (safeBaseUrl != storedBaseUrl) {
            aiPrefs.edit().putString(KEY_BASE_URL, safeBaseUrl).apply()
        }
        return safeBaseUrl
    }

    private fun safeBaseUrl(candidate: String): String =
        candidate.takeIf(::isValidProviderEndpoint)
            ?: aiPrefs.getString(KEY_BASE_URL, null)?.takeIf(::isValidProviderEndpoint)
            ?: DEFAULT_BASE_URL

    private inline fun <reified T : Enum<T>> readEnum(rawValue: String?, defaultValue: T): T =
        enumValues<T>().firstOrNull { it.name == rawValue } ?: defaultValue

    private companion object {
        const val KEY_PROVIDER = "provider"
        const val KEY_API_KEY = "apiKey"
        const val KEY_API_KEY_ENCRYPTED = "apiKeyEncrypted"
        const val KEY_BASE_URL = "baseUrl"
        const val KEY_MODEL = "model"
        const val KEY_THINKING_ENABLED = "thinkingEnabled"
        const val KEY_SYSTEM_LANGUAGE = "systemLanguage"
        const val KEY_APPEARANCE_MODE = "appearanceMode"
    }
}
