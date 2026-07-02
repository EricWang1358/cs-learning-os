package com.cslearningos.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactScreenChromeTest {
    @Test
    fun homeKeepsHeroChrome() {
        assertEquals(
            ScreenChromePolicy(
                style = ScreenChromeStyle.Hero,
                helpPlacement = ScreenHelpPlacement.Top,
                primaryFlow = ScreenPrimaryFlow.ContextFirst
            ),
            screenChromePolicy(AppScreen.Home)
        )
    }

    @Test
    fun backupLibraryAndMoreUseCompactPostActionHelp() {
        val expected = ScreenChromePolicy(
            style = ScreenChromeStyle.Compact,
            helpPlacement = ScreenHelpPlacement.AfterPrimaryActionsCollapsed,
            primaryFlow = ScreenPrimaryFlow.TaskFirst
        )

        assertEquals(expected, screenChromePolicy(AppScreen.Backup))
        assertEquals(expected, screenChromePolicy(AppScreen.Library))
        assertEquals(expected, screenChromePolicy(AppScreen.More))
    }

    @Test
    fun readerEditorAndQuizEditorKeepInlineDetailChrome() {
        val expected = ScreenChromePolicy(
            style = ScreenChromeStyle.Compact,
            helpPlacement = ScreenHelpPlacement.InlineDetail,
            primaryFlow = ScreenPrimaryFlow.TaskFirst
        )

        assertEquals(expected, screenChromePolicy(AppScreen.Reader))
        assertEquals(expected, screenChromePolicy(AppScreen.Editor))
        assertEquals(expected, screenChromePolicy(AppScreen.QuizEditor))
    }

    @Test
    fun nonHomeHelpStartsCollapsed() {
        assertFalse(screenHelpInitiallyExpanded(AppScreen.Capture))
        assertFalse(screenHelpInitiallyExpanded(AppScreen.Backup))
        assertFalse(screenHelpInitiallyExpanded(AppScreen.More))
    }

    @Test
    fun compactScreensUseQuickerMotionThanHome() {
        val home = screenMotionPolicy(AppScreen.Home)
        val capture = screenMotionPolicy(AppScreen.Capture)
        val reader = screenMotionPolicy(AppScreen.Reader)

        assertEquals(MotionEmphasis.Expressive, home.emphasis)
        assertEquals(MotionEmphasis.Compact, capture.emphasis)
        assertEquals(MotionEmphasis.Detail, reader.emphasis)
        assertTrue(capture.expandMillis < home.expandMillis)
        assertTrue(reader.expandMillis <= capture.expandMillis)
    }
}
