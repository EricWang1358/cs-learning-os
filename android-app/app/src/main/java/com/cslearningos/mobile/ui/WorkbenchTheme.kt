package com.cslearningos.mobile.ui

import android.app.Activity
import android.os.Build
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class WorkbenchPalette(
    val surface: Color,
    val surfaceSoft: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val ink: Color,
    val inkStrong: Color,
    val muted: Color,
    val line: Color,
    val lineStrong: Color,
    val accent: Color,
    val accentStrong: Color,
    val success: Color,
    val danger: Color
)

private val NightPalette = WorkbenchPalette(
    surface = Color(0xFF0B0E11),
    surfaceSoft = Color(0xFF181A20),
    surfaceCard = Color(0xFF1E2329),
    surfaceElevated = Color(0xFF2B3139),
    ink = Color(0xFFEAECEF),
    inkStrong = Color.White,
    muted = Color(0xFF929AA5),
    line = Color(0xFF2B3139),
    lineStrong = Color(0xFF3F4854),
    accent = Color(0xFFFCD535),
    accentStrong = Color(0xFFF0B90B),
    success = Color(0xFF0ECB81),
    danger = Color(0xFFF6465D)
)

private val DayPalette = WorkbenchPalette(
    surface = Color(0xFFF5F1E7),
    surfaceSoft = Color(0xFFFFFBF0),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFE8DDC4),
    ink = Color(0xFF25220C),
    inkStrong = Color(0xFF101008),
    muted = Color(0xFF756F5D),
    line = Color(0xFFD8CDAF),
    lineStrong = Color(0xFFC3B48E),
    accent = Color(0xFFB98700),
    accentStrong = Color(0xFF996500),
    success = Color(0xFF087D54),
    danger = Color(0xFFC33145)
)

object WorkbenchColors {
    private var current: WorkbenchPalette = NightPalette

    fun use(palette: WorkbenchPalette) {
        current = palette
    }

    val Surface: Color get() = current.surface
    val SurfaceSoft: Color get() = current.surfaceSoft
    val SurfaceCard: Color get() = current.surfaceCard
    val SurfaceElevated: Color get() = current.surfaceElevated
    val Ink: Color get() = current.ink
    val InkStrong: Color get() = current.inkStrong
    val Muted: Color get() = current.muted
    val Line: Color get() = current.line
    val LineStrong: Color get() = current.lineStrong
    val Accent: Color get() = current.accent
    val AccentStrong: Color get() = current.accentStrong
    val Success: Color get() = current.success
    val Danger: Color get() = current.danger
}

private val WorkbenchTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 23.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.2.sp
    )
)

@Composable
fun WorkbenchTheme(
    appearanceMode: AppearanceMode = AppearanceMode.FollowSystem,
    content: @Composable () -> Unit
) {
    val darkTheme = appearanceMode.usesDarkTheme(isSystemInDarkTheme())
    val palette = if (darkTheme) NightPalette else DayPalette
    WorkbenchColors.use(palette)
    SyncSystemBars(palette = palette, darkTheme = darkTheme)
    val scheme = if (!darkTheme) {
        lightColorScheme(
            background = palette.surface,
            surface = palette.surfaceSoft,
            surfaceVariant = palette.surfaceCard,
            primary = palette.accent,
            onPrimary = palette.inkStrong,
            secondary = palette.success,
            error = palette.danger,
            onBackground = palette.ink,
            onSurface = palette.ink,
            onSurfaceVariant = palette.muted
        )
    } else {
        darkColorScheme(
            background = palette.surface,
            surface = palette.surfaceSoft,
            surfaceVariant = palette.surfaceCard,
            primary = palette.accent,
            onPrimary = palette.surfaceSoft,
            secondary = palette.success,
            error = palette.danger,
            onBackground = palette.ink,
            onSurface = palette.ink,
            onSurfaceVariant = palette.muted
        )
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = WorkbenchTypography,
        content = content
    )
}

@Composable
private fun SyncSystemBars(palette: WorkbenchPalette, darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = palette.surface.toArgb()
        window.navigationBarColor = palette.surface.toArgb()

        var flags = view.systemUiVisibility
        flags = if (darkTheme) {
            flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        } else {
            flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags = if (darkTheme) {
                flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            } else {
                flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }

        view.systemUiVisibility = flags
    }
}
