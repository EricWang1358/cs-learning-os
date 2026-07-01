package com.cslearningos.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderMobileModelsTest {
    @Test
    fun nonHomeScreensUseCompactBrandOnPhone() {
        assertFalse(useCompactPortraitBrand(AppScreen.Home))
        assertTrue(useCompactPortraitBrand(AppScreen.Reader))
        assertTrue(useCompactPortraitBrand(AppScreen.Search))
    }

    @Test
    fun readerQuestionButtonShowsCountAndToggleState() {
        assertEquals("Q", readerQuestionButtonLabel(openQuestionCount = 0, expanded = false))
        assertEquals("Q (3)", readerQuestionButtonLabel(openQuestionCount = 3, expanded = false))
        assertEquals("Hide Q (3)", readerQuestionButtonLabel(openQuestionCount = 3, expanded = true))
    }
}
