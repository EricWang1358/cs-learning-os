package com.cslearningos.mobile.ui

enum class MotionEmphasis {
    Expressive,
    Compact,
    Detail
}

data class ScreenMotionPolicy(
    val emphasis: MotionEmphasis,
    val pressMillis: Int = WorkbenchMotion.PressMillis,
    val stateMillis: Int = WorkbenchMotion.StateMillis
)

object WorkbenchMotion {
    const val PressMillis = 110
    const val StateMillis = 130
    const val DisclosureMillis = 170
    const val NavigationMillis = 210
}

fun screenMotionPolicy(screen: AppScreen): ScreenMotionPolicy =
    when (screenChromePolicy(screen).helpPlacement) {
        ScreenHelpPlacement.Top -> ScreenMotionPolicy(
            emphasis = MotionEmphasis.Expressive
        )

        ScreenHelpPlacement.InlineDetail -> ScreenMotionPolicy(
            emphasis = MotionEmphasis.Detail
        )

        ScreenHelpPlacement.AfterPrimaryActionsCollapsed -> ScreenMotionPolicy(
            emphasis = MotionEmphasis.Compact
        )
    }
