package com.cslearningos.mobile.ui

import com.cslearningos.mobile.R
import com.cslearningos.mobile.appshell.navigation.AppRoute
import com.cslearningos.mobile.appshell.navigation.selectedBottomTabFor
import com.cslearningos.mobile.appshell.navigation.toAppRoute
import com.cslearningos.mobile.appshell.navigation.toAppScreen
import org.junit.Assert.assertEquals
import org.junit.Test

class MobileNavigationModelsTest {
    @Test
    fun bottomTabsUseProductOrder() {
        val tabs = mobileBottomNavItems()

        assertEquals(
            listOf(R.string.nav_home_label, R.string.nav_capture_label, R.string.nav_library_label, R.string.nav_review_label, R.string.nav_more_label),
            tabs.map { it.labelResId }
        )
        assertEquals(listOf(AppScreen.Home, AppScreen.Capture, AppScreen.Library, AppScreen.Review, AppScreen.More), tabs.map { it.screen })
    }

    @Test
    fun bottomTabsExposeMobileAppIconSemantics() {
        val tabs = mobileBottomNavItems()

        assertEquals(
            listOf(
                R.string.nav_home_description,
                R.string.nav_capture_description,
                R.string.nav_library_description,
                R.string.nav_review_description,
                R.string.nav_more_description
            ),
            tabs.map { it.contentDescriptionResId }
        )
    }

    @Test
    fun detailScreensKeepTheirParentBottomTabSelected() {
        assertEquals(AppScreen.Library, selectedBottomTabFor(AppScreen.Reader))
        assertEquals(AppScreen.Library, selectedBottomTabFor(AppScreen.Editor))
        assertEquals(AppScreen.Library, selectedBottomTabFor(AppScreen.Search))
        assertEquals(AppScreen.Library, selectedBottomTabFor(AppScreen.QuizEditor))
        assertEquals(AppScreen.More, selectedBottomTabFor(AppScreen.Backup))
    }

    @Test
    fun detailRoutesKeepTheirParentBottomTabSelected() {
        assertEquals(AppRoute.Library, selectedBottomTabFor(AppRoute.Reader))
        assertEquals(AppRoute.Library, selectedBottomTabFor(AppRoute.Editor))
        assertEquals(AppRoute.Library, selectedBottomTabFor(AppRoute.Search))
        assertEquals(AppRoute.Library, selectedBottomTabFor(AppRoute.QuizEditor))
        assertEquals(AppRoute.More, selectedBottomTabFor(AppRoute.Backup))
    }

    @Test
    fun appRoutesRoundTripLegacyScreens() {
        AppScreen.entries.forEach { screen ->
            assertEquals(screen, screen.toAppRoute().toAppScreen())
        }
    }
}
