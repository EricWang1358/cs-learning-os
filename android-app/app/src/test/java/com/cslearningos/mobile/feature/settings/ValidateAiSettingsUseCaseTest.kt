package com.cslearningos.mobile.feature.settings

import com.cslearningos.mobile.feature.settings.domain.ValidateAiSettingsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateAiSettingsUseCaseTest {
    @Test
    fun validateAiSettingsReportsMissingFieldsInOrder() {
        val useCase = ValidateAiSettingsUseCase()

        val result = useCase(
            provider = "",
            apiKey = "",
            baseUrl = "https://api.deepseek.com/v1",
            model = ""
        )

        assertEquals(listOf("provider", "apiKey", "model"), result.missingFields)
        assertFalse(result.isValid)
    }

    @Test
    fun validateAiSettingsAcceptsConfiguredValues() {
        val useCase = ValidateAiSettingsUseCase()

        val result = useCase(
            provider = "DeepSeek",
            apiKey = "sk-test",
            baseUrl = "https://api.deepseek.com/v1",
            model = "deepseek-v4-flash"
        )

        assertTrue(result.isValid)
        assertTrue(result.missingFields.isEmpty())
    }
}
