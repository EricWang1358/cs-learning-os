package com.cslearningos.mobile.feature.settings.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cslearningos.mobile.feature.settings.data.AiDraftService
import com.cslearningos.mobile.feature.settings.data.AiSettingsSnapshot
import com.cslearningos.mobile.feature.settings.data.ApiKeyProtector
import com.cslearningos.mobile.feature.settings.data.AppSettingsSnapshot
import com.cslearningos.mobile.feature.settings.data.SettingsPreferencesStore
import com.cslearningos.mobile.feature.settings.data.SettingsSnapshot
import com.cslearningos.mobile.feature.settings.data.SettingsStore
import com.cslearningos.mobile.feature.settings.domain.ValidateAiSettingsUseCase
import com.cslearningos.mobile.ui.AppearanceMode
import com.cslearningos.mobile.ui.AiServiceStatusKind
import com.cslearningos.mobile.ui.SystemLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun initializesUiStateFromStoredSnapshot() {
        val viewModel = SettingsViewModel(
            store = FakeSettingsStore(
                initialSnapshot = SettingsSnapshot(
                    aiSettings = AiSettingsSnapshot(
                        provider = "OpenAI Compatible",
                        apiKey = "sk-test",
                        baseUrl = "https://example.com/v1",
                        model = "gpt-test",
                        thinkingEnabled = true
                    ),
                    appSettings = AppSettingsSnapshot(
                        systemLanguage = SystemLanguage.English,
                        appearanceMode = AppearanceMode.Night
                    )
                )
            ),
            aiDraftService = FakeAiDraftService(),
            validateAiSettings = ValidateAiSettingsUseCase(),
            scope = CoroutineScope(dispatcher)
        )

        val state = viewModel.uiState.value
        assertEquals("OpenAI Compatible", state.provider)
        assertEquals("sk-test", state.apiKey)
        assertEquals("https://example.com/v1", state.baseUrl)
        assertEquals("gpt-test", state.model)
        assertTrue(state.thinkingEnabled)
        assertEquals(SystemLanguage.English, state.systemLanguage)
        assertEquals(AppearanceMode.Night, state.appearanceMode)
        assertTrue(state.validation.isValid)
    }

    @Test
    fun savePersistsEditedValuesBackToStore() {
        val store = FakeSettingsStore()
        val viewModel = SettingsViewModel(
            store = store,
            aiDraftService = FakeAiDraftService(),
            validateAiSettings = ValidateAiSettingsUseCase(),
            scope = CoroutineScope(dispatcher)
        )

        viewModel.setProvider("OpenAI Compatible")
        viewModel.setApiKey("sk-updated")
        viewModel.setBaseUrl("https://api.openai.example/v1")
        viewModel.setModel("gpt-test")
        viewModel.setThinkingEnabled(true)
        viewModel.save()

        val savedSnapshot = store.savedSnapshot
        assertNotNull(savedSnapshot)
        assertEquals("OpenAI Compatible", savedSnapshot?.aiSettings?.provider)
        assertEquals("sk-updated", savedSnapshot?.aiSettings?.apiKey)
        assertEquals("https://api.openai.example/v1", savedSnapshot?.aiSettings?.baseUrl)
        assertEquals("gpt-test", savedSnapshot?.aiSettings?.model)
        assertEquals(true, savedSnapshot?.aiSettings?.thinkingEnabled)
    }

    @Test
    fun settingBlankApiKeyInvokesExplicitCredentialClear() {
        val store = FakeSettingsStore()
        val viewModel = SettingsViewModel(
            store = store,
            aiDraftService = FakeAiDraftService(),
            validateAiSettings = ValidateAiSettingsUseCase(),
            scope = CoroutineScope(dispatcher)
        )

        viewModel.setApiKey("")

        assertEquals(1, store.clearApiKeyCalls)
    }

    @Test
    fun settingBlankApiKeyClearsUnresolvedLegacyCredentialFromPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val aiPrefs = context.getSharedPreferences("settings-view-model-legacy-ai", Context.MODE_PRIVATE)
        val appPrefs = context.getSharedPreferences("settings-view-model-legacy-app", Context.MODE_PRIVATE)
        aiPrefs.edit().clear().putString("apiKey", "sk-legacy").commit()
        appPrefs.edit().clear().commit()
        val store = SettingsPreferencesStore(aiPrefs, appPrefs, FailsLegacyEncryptionProtector)
        val viewModel = SettingsViewModel(
            store = store,
            aiDraftService = FakeAiDraftService(),
            validateAiSettings = ValidateAiSettingsUseCase(),
            scope = CoroutineScope(dispatcher)
        )

        assertEquals("", viewModel.uiState.value.apiKey)
        viewModel.setApiKey("")

        assertFalse(aiPrefs.contains("apiKey"))
        assertFalse(aiPrefs.contains("apiKeyEncrypted"))
    }

    @Test
    fun validateCurrentSettingsReflectsEditedFields() {
        val viewModel = SettingsViewModel(
            store = FakeSettingsStore(),
            aiDraftService = FakeAiDraftService(),
            validateAiSettings = ValidateAiSettingsUseCase(),
            missingFieldLabelResolver = { missingFields: List<String> -> missingFields.joinToString() },
            scope = CoroutineScope(dispatcher)
        )

        viewModel.setApiKey("sk-test")
        viewModel.setProvider("")
        viewModel.setBaseUrl("")

        assertEquals(
            listOf("provider", "baseUrl"),
            viewModel.validateCurrentSettings().missingFields
        )
    }

    @Test
    fun validateServicePullsModelsAndPublishesSuccessStatus() = runTest {
        val asyncDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(asyncDispatcher)
        try {
            val viewModel = SettingsViewModel(
                store = FakeSettingsStore(
                    initialSnapshot = SettingsSnapshot(
                        aiSettings = AiSettingsSnapshot(
                            provider = "OpenAI Compatible",
                            apiKey = "sk-test",
                            baseUrl = "https://api.openai.example/v1",
                            model = "gpt-test",
                            thinkingEnabled = false
                        )
                    )
                ),
                aiDraftService = FakeAiDraftService(modelIds = listOf("gpt-test", "gpt-fast")),
                validateAiSettings = ValidateAiSettingsUseCase(),
                missingFieldLabelResolver = { missingFields: List<String> -> missingFields.joinToString() },
                ioDispatcher = asyncDispatcher,
                scope = CoroutineScope(asyncDispatcher)
            )

            viewModel.validateService()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf("gpt-test", "gpt-fast"), state.availableModels)
            assertEquals(AiServiceStatusKind.Success, state.aiServiceStatus.kind)
            assertTrue(state.message != null)
            assertTrue(!state.isBusy)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun updateSystemSettingsPersistsThroughStore() = runTest {
        val asyncDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(asyncDispatcher)
        try {
            val store = FakeSettingsStore()
            val viewModel = SettingsViewModel(
                store = store,
                aiDraftService = FakeAiDraftService(),
                validateAiSettings = ValidateAiSettingsUseCase(),
                missingFieldLabelResolver = { missingFields: List<String> -> missingFields.joinToString() },
                ioDispatcher = asyncDispatcher,
                scope = CoroutineScope(asyncDispatcher)
            )

            viewModel.setSystemLanguage(SystemLanguage.Chinese)
            viewModel.setAppearanceMode(AppearanceMode.Day)
            advanceUntilIdle()

            assertEquals(SystemLanguage.Chinese, viewModel.uiState.value.systemLanguage)
            assertEquals(AppearanceMode.Day, viewModel.uiState.value.appearanceMode)
            assertEquals(SystemLanguage.Chinese, store.savedSnapshot?.appSettings?.systemLanguage)
            assertEquals(AppearanceMode.Day, store.savedSnapshot?.appSettings?.appearanceMode)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeSettingsStore(
        initialSnapshot: SettingsSnapshot = SettingsSnapshot()
    ) : SettingsStore {
        private var snapshot = initialSnapshot

        var savedSnapshot: SettingsSnapshot? = null
        var clearApiKeyCalls = 0

        override fun loadSettings(): SettingsSnapshot = snapshot

        override fun saveSettings(snapshot: SettingsSnapshot) {
            this.snapshot = snapshot
            savedSnapshot = snapshot
        }

        override fun clearApiKey() {
            clearApiKeyCalls += 1
        }
    }

    private class FakeAiDraftService(
        private val modelIds: List<String> = emptyList()
    ) : AiDraftService {

        override suspend fun fetchModelIds(baseUrl: String, apiKey: String): List<String> = modelIds

        override suspend fun requestDraft(baseUrl: String, apiKey: String, model: String, prompt: String): String =
            "# Draft"
    }

    private object FailsLegacyEncryptionProtector : ApiKeyProtector {
        override fun encrypt(plainText: String): String {
            if (plainText == "sk-legacy") error("Keystore unavailable")
            return "test:$plainText"
        }

        override fun decrypt(envelope: String): String? = null
    }
}
