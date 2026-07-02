package com.cslearningos.mobile.feature.capture

import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.data.CaptureSlipStatus
import com.cslearningos.mobile.data.CaptureSlipType
import com.cslearningos.mobile.data.SyncStatus
import com.cslearningos.mobile.feature.capture.domain.GenerateCaptureDraftUseCase
import com.cslearningos.mobile.feature.settings.data.AiDraftService
import com.cslearningos.mobile.ui.AiProviderSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateCaptureDraftUseCaseTest {
    @Test
    fun generateCaptureDraftBuildsPromptFromSlipAndNodeTitles() = runTest {
        val service = RecordingAiDraftService(response = "# Draft")
        val useCase = GenerateCaptureDraftUseCase(service)
        val settings = AiProviderSettings(
            provider = "DeepSeek",
            apiKey = "sk-test",
            baseUrl = "https://api.deepseek.com/v1",
            model = "deepseek-v4-flash"
        )
        val slip = CaptureSlipEntity(
            id = "slip-1",
            body = "TLB miss",
            type = CaptureSlipType.unclear,
            topicHint = "Address translation",
            sourceLabel = "lecture",
            linkedNodeId = null,
            status = CaptureSlipStatus.ai_queued,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null
        )

        val draft = useCase(
            settings = settings,
            slip = slip,
            existingNodeTitles = listOf("Paging", "Virtual Memory")
        )

        assertEquals("# Draft", draft)
        assertEquals("https://api.deepseek.com/v1", service.lastBaseUrl)
        assertEquals("sk-test", service.lastApiKey)
        assertEquals("deepseek-v4-flash", service.lastModel)
        assertTrue(service.lastPrompt.contains("TLB miss"))
        assertTrue(service.lastPrompt.contains("Address translation"))
        assertTrue(service.lastPrompt.contains("lecture"))
        assertTrue(service.lastPrompt.contains("Paging"))
        assertTrue(service.lastPrompt.contains("Virtual Memory"))
    }
}

private class RecordingAiDraftService(
    private val response: String
) : AiDraftService {
    var lastBaseUrl: String? = null
    var lastApiKey: String? = null
    var lastModel: String? = null
    var lastPrompt: String = ""

    override suspend fun fetchModelIds(baseUrl: String, apiKey: String): List<String> = emptyList()

    override suspend fun requestDraft(baseUrl: String, apiKey: String, model: String, prompt: String): String {
        lastBaseUrl = baseUrl
        lastApiKey = apiKey
        lastModel = model
        lastPrompt = prompt
        return response
    }
}
