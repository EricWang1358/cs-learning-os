package com.cslearningos.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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

        composeRule.onNodeWithTag("compact-table-grid").assertExists()
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

    @Test
    fun expandedTable_doesNotComposeCompactGrid() {
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

        composeRule.onNodeWithContentDescription("Expand table").performClick()

        composeRule.onNodeWithTag("compact-table-grid").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Exit table view").assertExists()
    }

    @Test
    fun tableCellBoundary_drawsEverySharedEdgeOnce() {
        org.junit.Assert.assertEquals(TableCellBoundary(drawStartDivider = false, drawTopDivider = false), tableCellBoundary(0, 0))
        org.junit.Assert.assertEquals(TableCellBoundary(drawStartDivider = true, drawTopDivider = false), tableCellBoundary(0, 1))
        org.junit.Assert.assertEquals(TableCellBoundary(drawStartDivider = false, drawTopDivider = true), tableCellBoundary(1, 0))
        org.junit.Assert.assertEquals(TableCellBoundary(drawStartDivider = true, drawTopDivider = true), tableCellBoundary(1, 1))
    }
}
