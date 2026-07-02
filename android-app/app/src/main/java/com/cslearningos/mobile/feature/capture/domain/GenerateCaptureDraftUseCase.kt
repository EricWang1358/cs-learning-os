package com.cslearningos.mobile.feature.capture.domain

import com.cslearningos.mobile.data.CaptureSlipEntity
import com.cslearningos.mobile.feature.settings.data.AiDraftService
import com.cslearningos.mobile.ui.AiProviderSettings
import com.cslearningos.mobile.ui.buildCaptureAiDraftPrompt

class GenerateCaptureDraftUseCase(
    private val aiDraftService: AiDraftService
) {
    suspend operator fun invoke(
        settings: AiProviderSettings,
        slip: CaptureSlipEntity,
        existingNodeTitles: List<String>
    ): String {
        val draft = aiDraftService.requestDraft(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey,
            model = settings.model,
            prompt = buildCaptureAiDraftPrompt(slip = slip, existingNodes = existingNodeTitles)
        )
        return draft.trim().ifBlank {
            throw IllegalStateException("The model returned an empty draft.")
        }
    }
}
