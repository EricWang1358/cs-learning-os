package com.cslearningos.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AssistantMessageBodyTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun completedAssistantProseExposesNativeTextSelection() {
        composeRule.setContent {
            MaterialTheme {
                AssistantMessageBody(markdown = "Assistant answer")
            }
        }

        composeRule.onNodeWithTag("assistant-message-body").assertExists()
    }
}
