package com.cslearningos.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object WorkbenchColors {
    val Surface = Color(0xFF0B0E11)
    val SurfaceSoft = Color(0xFF181A20)
    val SurfaceCard = Color(0xFF1E2329)
    val SurfaceElevated = Color(0xFF2B3139)
    val Ink = Color(0xFFEAECEF)
    val InkStrong = Color.White
    val Muted = Color(0xFF929AA5)
    val Line = Color(0xFF2B3139)
    val LineStrong = Color(0xFF3F4854)
    val Accent = Color(0xFFFCD535)
    val AccentStrong = Color(0xFFF0B90B)
    val Success = Color(0xFF0ECB81)
    val Danger = Color(0xFFF6465D)
}

private val WorkbenchScheme = darkColorScheme(
    background = WorkbenchColors.Surface,
    surface = WorkbenchColors.SurfaceSoft,
    surfaceVariant = WorkbenchColors.SurfaceCard,
    primary = WorkbenchColors.Accent,
    onPrimary = WorkbenchColors.SurfaceSoft,
    secondary = WorkbenchColors.Success,
    error = WorkbenchColors.Danger,
    onBackground = WorkbenchColors.Ink,
    onSurface = WorkbenchColors.Ink,
    onSurfaceVariant = WorkbenchColors.Muted
)

private val WorkbenchTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
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
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.2.sp
    )
)

@Composable
fun WorkbenchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WorkbenchScheme,
        typography = WorkbenchTypography,
        content = content
    )
}
