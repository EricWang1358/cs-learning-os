package com.cslearningos.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarkdownRendererTableTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun wideTable_expandsIntoDismissibleFullScreenView() {
        composeRule.setContent {
            MaterialTheme {
                MarkdownRenderer(
                    markdown = """
                        | One | Two | Three |
                        | --- | --- | --- |
                        | A | B | C |
                    """.trimIndent()
                )
            }
        }

        composeRule.onNodeWithContentDescription("Expand table").assertExists().performClick()

        composeRule.onNodeWithContentDescription("Exit table view").assertExists().performClick()

        composeRule.onNodeWithContentDescription("Exit table view").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Expand table").assertExists()
    }

    @Test
    fun smallTable_doesNotOfferFullScreenView() {
        composeRule.setContent {
            MaterialTheme {
                MarkdownRenderer(
                    markdown = """
                        | One | Two |
                        | --- | --- |
                        | A | B |
                    """.trimIndent()
                )
            }
        }

        composeRule.onNodeWithText("One").assertExists()
        composeRule.onNodeWithContentDescription("Expand table").assertDoesNotExist()
    }

    @Test
    fun fiveRowTable_offersFullScreenView() {
        composeRule.setContent {
            MaterialTheme {
                MarkdownRenderer(
                    markdown = """
                        | One | Two |
                        | --- | --- |
                        | A | 1 |
                        | B | 2 |
                        | C | 3 |
                        | D | 4 |
                        | E | 5 |
                    """.trimIndent()
                )
            }
        }

        composeRule.onNodeWithContentDescription("Expand table").assertExists()
    }

    @Test
    fun fourRowTwoColumnTable_doesNotOfferFullScreenView() {
        composeRule.setContent {
            MaterialTheme {
                MarkdownRenderer(
                    markdown = """
                        | One | Two |
                        | --- | --- |
                        | A | 1 |
                        | B | 2 |
                        | C | 3 |
                        | D | 4 |
                    """.trimIndent()
                )
            }
        }

        composeRule.onNodeWithContentDescription("Expand table").assertDoesNotExist()
    }
}
