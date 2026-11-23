package com.lectoria.app.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Midnight {
    val Background = Color(0xFF040A1C)
    val Surface = Color(0xFF0A1633)
    val Container = Color(0xFF123A9E)
    val Primary = Color(0xFF2F6BFF)
    val Highlight = Color(0xFF79DDFF)
    val Text = Color(0xFFF3F6FF)
    val MutedText = Color(0xFFB3C0DC)
}

private val MidnightColors = darkColorScheme(
    primary = Midnight.Primary,
    onPrimary = Color.White,
    primaryContainer = Midnight.Container,
    onPrimaryContainer = Midnight.Text,
    secondary = Midnight.Highlight,
    onSecondary = Midnight.Background,
    secondaryContainer = Midnight.Surface,
    onSecondaryContainer = Midnight.Highlight,
    background = Midnight.Background,
    onBackground = Midnight.Text,
    surface = Midnight.Surface,
    onSurface = Midnight.Text,
    surfaceVariant = Midnight.Surface,
    onSurfaceVariant = Midnight.MutedText,
    surfaceContainer = Midnight.Surface,
    surfaceContainerHigh = Color(0xFF122044),
    outline = Color(0xFF647495),
    outlineVariant = Color(0xFF263657),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LectorTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = (-0.8).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 29.sp,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.2.sp,
    ),
)

@Composable
fun LectorIaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MidnightColors, typography = LectorTypography, content = content)
}
