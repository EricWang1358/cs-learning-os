package com.cslearningos.mobile.feature.settings.domain

import com.cslearningos.mobile.assistant.domain.isValidProviderEndpoint

data class AiSettingsValidationResult(
    val missingFields: List<String> = emptyList()
) {
    val isValid: Boolean
        get() = missingFields.isEmpty()
}

class ValidateAiSettingsUseCase {
    operator fun invoke(
        provider: String,
        apiKey: String,
        baseUrl: String,
        model: String
    ): AiSettingsValidationResult =
        AiSettingsValidationResult(
            missingFields = buildList {
                if (provider.isBlank()) add(FIELD_PROVIDER)
                if (apiKey.isBlank()) add(FIELD_API_KEY)
                if (!isValidProviderEndpoint(baseUrl)) add(FIELD_BASE_URL)
                if (model.isBlank()) add(FIELD_MODEL)
            }
        )

    companion object {
        const val FIELD_PROVIDER = "provider"
        const val FIELD_API_KEY = "apiKey"
        const val FIELD_BASE_URL = "baseUrl"
        const val FIELD_MODEL = "model"
    }
}
