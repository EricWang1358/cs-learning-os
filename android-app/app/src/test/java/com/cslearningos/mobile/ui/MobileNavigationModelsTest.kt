package com.cslearningos.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MobileNavigationModelsTest {
    @Test
    fun bottomTabsUseProductOrder() {
        val tabs = mobileBottomNavItems()

        assertEquals(listOf("Home", "Capture", "Library", "Review", "More"), tabs.map { it.label })
        assertEquals(listOf(AppScreen.Home, AppScreen.Capture, AppScreen.Library, AppScreen.Review, AppScreen.More), tabs.map { it.screen })
    }

    @Test
    fun bottomTabsExposeMobileAppIconSemantics() {
        val tabs = mobileBottomNavItems()

        assertEquals(
            listOf("Home dashboard", "Quick capture", "Knowledge library", "Due review", "Settings and data"),
            tabs.map { it.contentDescription }
        )
    }
}
