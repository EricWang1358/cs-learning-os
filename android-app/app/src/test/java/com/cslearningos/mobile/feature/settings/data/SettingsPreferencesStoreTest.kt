package com.cslearningos.mobile.feature.settings.data

import androidx.test.core.app.ApplicationProvider
import com.cslearningos.mobile.ui.AppearanceMode
import com.cslearningos.mobile.ui.SystemLanguage
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsPreferencesStoreTest {
    private lateinit var aiPrefs: android.content.SharedPreferences
    private lateinit var appPrefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        aiPrefs = context.getSharedPreferences("settings-store-ai-test", android.content.Context.MODE_PRIVATE)
        appPrefs = context.getSharedPreferences("settings-store-app-test", android.content.Context.MODE_PRIVATE)
        aiPrefs.edit().clear().commit()
        appPrefs.edit().clear().commit()
    }

    @Test
    fun saveStoresEncryptedApiKeyWithoutPlaintext() {
        val store = newStore()

        store.saveSettings(snapshot(apiKey = "sk-secret"))

        assertNull(aiPrefs.getString("apiKey", null))
        assertFalse(aiPrefs.all.values.contains("sk-secret"))
        assertEquals("fake:c2stc2VjcmV0", aiPrefs.getString("apiKeyEncrypted", null))
    }

    @Test
    fun loadDecryptsEncryptedApiKey() {
        aiPrefs.edit().putString("apiKeyEncrypted", "fake:c2stc2VjcmV0").commit()

        val loaded = newStore().loadSettings()

        assertEquals("sk-secret", loaded.aiSettings.apiKey)
    }

    @Test
    fun loadMigratesLegacyPlaintextOnlyAfterEncryptingIt() {
        aiPrefs.edit().putString("apiKey", "sk-legacy").commit()

        val loaded = newStore().loadSettings()

        assertEquals("sk-legacy", loaded.aiSettings.apiKey)
        assertEquals("fake:c2stbGVnYWN5", aiPrefs.getString("apiKeyEncrypted", null))
        assertFalse(aiPrefs.contains("apiKey"))
    }

    @Test
    fun loadLeavesLegacyPlaintextWhenMigrationEncryptionFails() {
        aiPrefs.edit().putString("apiKey", "sk-legacy").commit()
        val store = SettingsPreferencesStore(aiPrefs, appPrefs, FailingApiKeyProtector)

        val loaded = store.loadSettings()

        assertEquals("", loaded.aiSettings.apiKey)
        assertEquals("sk-legacy", aiPrefs.getString("apiKey", null))
        assertFalse(aiPrefs.contains("apiKeyEncrypted"))
    }

    @Test
    fun savePreservesUnresolvedLegacyApiKeyUntilNonblankEncryptionSucceeds() {
        aiPrefs.edit().putString("apiKey", "sk-legacy").commit()
        val unresolvedStore = SettingsPreferencesStore(aiPrefs, appPrefs, FailsLegacyEncryptionProtector)

        assertEquals("", unresolvedStore.loadSettings().aiSettings.apiKey)
        unresolvedStore.saveSettings(
            snapshot(apiKey = "").copy(
                aiSettings = snapshot(apiKey = "").aiSettings.copy(provider = "Updated Provider")
            )
        )

        assertEquals("sk-legacy", aiPrefs.getString("apiKey", null))
        assertFalse(aiPrefs.contains("apiKeyEncrypted"))

        newStore().saveSettings(snapshot(apiKey = "sk-reentered"))

        assertFalse(aiPrefs.contains("apiKey"))
        assertEquals("fake:c2stcmVlbnRlcmVk", aiPrefs.getString("apiKeyEncrypted", null))
    }

    @Test
    fun loadRejectsMalformedEncryptedApiKeyWithoutFallingBackToLegacyPlaintext() {
        aiPrefs.edit()
            .putString("apiKeyEncrypted", "malformed")
            .putString("apiKey", "sk-legacy")
            .commit()

        val loaded = newStore().loadSettings()

        assertEquals("", loaded.aiSettings.apiKey)
        assertEquals("sk-legacy", aiPrefs.getString("apiKey", null))
    }

    @Test
    fun saveDoesNotPersistAnHttpBaseUrl() {
        newStore().saveSettings(snapshot(apiKey = "sk-secret", baseUrl = "http://provider.test/v1"))

        assertEquals("https://api.deepseek.com/v1", aiPrefs.getString("baseUrl", null))
    }

    @Test
    fun saveKeepsPreviousHttpsBaseUrlWhenGivenMalformedBaseUrl() {
        aiPrefs.edit().putString("baseUrl", "https://previous.test/v1").commit()

        newStore().saveSettings(snapshot(apiKey = "sk-secret", baseUrl = "not-a-url"))

        assertEquals("https://previous.test/v1", aiPrefs.getString("baseUrl", null))
    }

    @Test
    fun saveDoesNotPersistAHostlessHttpsBaseUrl() {
        newStore().saveSettings(snapshot(apiKey = "sk-secret", baseUrl = "https:///v1"))

        assertEquals("https://api.deepseek.com/v1", aiPrefs.getString("baseUrl", null))
    }

    @Test
    fun loadReplacesPreviouslyStoredMalformedBaseUrl() {
        aiPrefs.edit().putString("baseUrl", "not-a-url").commit()

        val loaded = newStore().loadSettings()

        assertEquals("https://api.deepseek.com/v1", loaded.aiSettings.baseUrl)
        assertEquals("https://api.deepseek.com/v1", aiPrefs.getString("baseUrl", null))
    }

    private fun newStore(): SettingsPreferencesStore =
        SettingsPreferencesStore(aiPrefs, appPrefs, FakeApiKeyProtector)

    private fun snapshot(
        apiKey: String,
        baseUrl: String = "https://provider.test/v1"
    ): SettingsSnapshot =
        SettingsSnapshot(
            aiSettings = AiSettingsSnapshot(
                provider = "Provider",
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = "model",
                thinkingEnabled = true
            ),
            appSettings = AppSettingsSnapshot(
                systemLanguage = SystemLanguage.FollowSystem,
                appearanceMode = AppearanceMode.FollowSystem
            )
        )

    private object FakeApiKeyProtector : ApiKeyProtector {
        override fun encrypt(plainText: String): String =
            "fake:${Base64.getEncoder().encodeToString(plainText.toByteArray())}"

        override fun decrypt(envelope: String): String? =
            envelope.takeIf { it.startsWith("fake:") }
                ?.removePrefix("fake:")
                ?.let { encoded -> runCatching { Base64.getDecoder().decode(encoded).toString(Charsets.UTF_8) }.getOrNull() }
    }

    private object FailingApiKeyProtector : ApiKeyProtector {
        override fun encrypt(plainText: String): String = error("Keystore unavailable")

        override fun decrypt(envelope: String): String? = null
    }

    private object FailsLegacyEncryptionProtector : ApiKeyProtector {
        override fun encrypt(plainText: String): String {
            if (plainText == "sk-legacy") error("Keystore unavailable")
            return FakeApiKeyProtector.encrypt(plainText)
        }

        override fun decrypt(envelope: String): String? = FakeApiKeyProtector.decrypt(envelope)
    }
}
