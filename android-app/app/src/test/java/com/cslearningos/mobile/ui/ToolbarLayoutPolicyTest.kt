package com.cslearningos.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarLayoutPolicyTest {
    @Test
    fun usesTheFourMotionDurationsInAscendingOrder() {
        assertEquals(110, WorkbenchMotion.PressMillis)
        assertEquals(130, WorkbenchMotion.StateMillis)
        assertEquals(170, WorkbenchMotion.DisclosureMillis)
        assertEquals(210, WorkbenchMotion.NavigationMillis)
        assertTrue(WorkbenchMotion.PressMillis < WorkbenchMotion.StateMillis)
        assertTrue(WorkbenchMotion.StateMillis < WorkbenchMotion.DisclosureMillis)
        assertTrue(WorkbenchMotion.DisclosureMillis < WorkbenchMotion.NavigationMillis)
    }

    @Test
    fun keepsActionsOnOneRowWhenTheirReadableWidthsFit() {
        assertEquals(
            listOf(listOf(0, 1, 2)),
            toolbarRows(itemWidths = listOf(48, 56, 64), availableWidth = 200, gap = 8)
        )
    }

    @Test
    fun balancesOverflowIntoTwoEqualWidthColumnsBeforeAddingAnotherRow() {
        assertEquals(
            listOf(listOf(0, 1), listOf(2)),
            toolbarRows(itemWidths = listOf(80, 80, 80), availableWidth = 200, gap = 8)
        )
    }

    @Test
    fun promotesALongActionToItsOwnRowInsteadOfForcingUnreadableTruncation() {
        val rows = toolbarRows(itemWidths = listOf(72, 132), availableWidth = 200, gap = 8)

        assertEquals(listOf(listOf(0), listOf(1)), rows)
        assertTrue(rows.all { it.size == 1 })
    }
}
