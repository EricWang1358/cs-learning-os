package com.cslearningos.mobile.ui

enum class MotionEmphasis {
    Expressive,
    Compact,
    Detail
}

data class ScreenMotionPolicy(
    val emphasis: MotionEmphasis,
    val expandMillis: Int,
    val fadeMillis: Int,
    val pressMillis: Int,
    val revealStaggerMillis: Int
)

object WorkbenchMotion {
    const val HomeExpandMillis = 260
    const val HomeFadeMillis = 180
    const val CompactExpandMillis = 190
    const val CompactFadeMillis = 140
    const val DetailExpandMillis = 170
    const val DetailFadeMillis = 120
    const val PressMillis = 110
    const val HomeRevealStaggerMillis = 70
    const val CompactRevealStaggerMillis = 45
    const val DetailRevealStaggerMillis = 35
}

fun screenMotionPolicy(screen: AppScreen): ScreenMotionPolicy =
    when (screenChromePolicy(screen).helpPlacement) {
        ScreenHelpPlacement.Top -> ScreenMotionPolicy(
            emphasis = MotionEmphasis.Expressive,
            expandMillis = WorkbenchMotion.HomeExpandMillis,
            fadeMillis = WorkbenchMotion.HomeFadeMillis,
            pressMillis = WorkbenchMotion.PressMillis,
            revealStaggerMillis = WorkbenchMotion.HomeRevealStaggerMillis
        )

        ScreenHelpPlacement.InlineDetail -> ScreenMotionPolicy(
            emphasis = MotionEmphasis.Detail,
            expandMillis = WorkbenchMotion.DetailExpandMillis,
            fadeMillis = WorkbenchMotion.DetailFadeMillis,
            pressMillis = WorkbenchMotion.PressMillis,
            revealStaggerMillis = WorkbenchMotion.DetailRevealStaggerMillis
        )

        ScreenHelpPlacement.AfterPrimaryActionsCollapsed -> ScreenMotionPolicy(
            emphasis = MotionEmphasis.Compact,
            expandMillis = WorkbenchMotion.CompactExpandMillis,
            fadeMillis = WorkbenchMotion.CompactFadeMillis,
            pressMillis = WorkbenchMotion.PressMillis,
            revealStaggerMillis = WorkbenchMotion.CompactRevealStaggerMillis
        )
    }
