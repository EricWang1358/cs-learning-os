package com.cslearningos.mobile.ui

import android.app.Activity
import android.os.Build
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val success: Color,
    val danger: Color
)

/**
 * Branded Material 3 schemes used when wallpaper-based dynamic color is
 * unavailable (API < 31). The seed is the original workbench gold.
 */
private val BrandLightScheme = lightColorScheme(
    primary = Color(0xFF785A00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDF9E),
    onPrimaryContainer = Color(0xFF261A00),
    secondary = Color(0xFF6B5D3F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5E0BB),
    onSecondaryContainer = Color(0xFF241A04),
    tertiary = Color(0xFF4C6543),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCEEBC0),
    onTertiaryContainer = Color(0xFF0A2006),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F1),
    onBackground = Color(0xFF1F1B13),
    surface = Color(0xFFFFF8F1),
    onSurface = Color(0xFF1F1B13),
    surfaceVariant = Color(0xFFEDE1CF),
    onSurfaceVariant = Color(0xFF4D4639),
    outline = Color(0xFF7F7667),
    outlineVariant = Color(0xFFD0C5B4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF3E8),
    surfaceContainer = Color(0xFFF4EDDE),
    surfaceContainerHigh = Color(0xFFEFE8D8),
    surfaceContainerHighest = Color(0xFFE9E2D3)
)

private val BrandDarkScheme = darkColorScheme(
    primary = Color(0xFFFCD535),
    onPrimary = Color(0xFF3A3000),
    primaryContainer = Color(0xFF544600),
    onPrimaryContainer = Color(0xFFFFE98A),
    secondary = Color(0xFFD8C58D),
    onSecondary = Color(0xFF3B2F05),
    secondaryContainer = Color(0xFF534619),
    onSecondaryContainer = Color(0xFFF5E0BB),
    tertiary = Color(0xFFB2CFA5),
    onTertiary = Color(0xFF1F361A),
    tertiaryContainer = Color(0xFF354D2F),
    onTertiaryContainer = Color(0xFFCEEBC0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF16130B),
    onBackground = Color(0xFFEAE2D4),
    surface = Color(0xFF16130B),
    onSurface = Color(0xFFEAE2D4),
    surfaceVariant = Color(0xFF4C4639),
    onSurfaceVariant = Color(0xFFCFC5B4),
    outline = Color(0xFF989080),
    outlineVariant = Color(0xFF4C4639),
    surfaceContainerLowest = Color(0xFF110E07),
    surfaceContainerLow = Color(0xFF1F1B13),
    surfaceContainer = Color(0xFF231F17),
    surfaceContainerHigh = Color(0xFF2D2921),
    surfaceContainerHighest = Color(0xFF38342B)
)

/** Static semantic colors that dynamic schemes do not provide. */
private fun successColor(darkTheme: Boolean): Color =
    if (darkTheme) Color(0xFF6BD79B) else Color(0xFF2E7D52)

private fun ColorScheme.toWorkbenchPalette(darkTheme: Boolean): WorkbenchPalette =
    WorkbenchPalette(
        surface = surfaceContainerLowest,
        surfaceSoft = surfaceContainerLow,
        surfaceCard = surfaceContainer,
        surfaceElevated = surfaceContainerHighest,
        ink = onSurface,
        inkStrong = onSurface,
        muted = onSurfaceVariant,
        line = outlineVariant,
        lineStrong = outline,
        accent = primary,
        accentStrong = primary,
        onAccent = onPrimary,
        accentContainer = primaryContainer,
        onAccentContainer = onPrimaryContainer,
        success = successColor(darkTheme),
        danger = error
    )

object WorkbenchColors {
    private var current: WorkbenchPalette = BrandDarkScheme.toWorkbenchPalette(darkTheme = true)

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
    val OnAccent: Color get() = current.onAccent
    val AccentContainer: Color get() = current.accentContainer
    val OnAccentContainer: Color get() = current.onAccentContainer
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
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = appearanceMode.usesDarkTheme(isSystemInDarkTheme())
    val dynamicColorAvailable = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    val scheme = when {
        dynamicColorAvailable && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColorAvailable -> dynamicLightColorScheme(context)
        darkTheme -> BrandDarkScheme
        else -> BrandLightScheme
    }
    val palette = scheme.toWorkbenchPalette(darkTheme)
    WorkbenchColors.use(palette)
    SyncSystemBars(palette = palette, darkTheme = darkTheme)

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
