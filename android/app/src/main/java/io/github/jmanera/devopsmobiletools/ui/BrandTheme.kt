// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The brand palette (docs/brand). */
object Brand {
    val Blue = Color(0xFF0D6EFD)
    val Navy = Color(0xFF0A2540)
    val Cyan = Color(0xFF00B4FF)
    val SecondaryBlue = Color(0xFF3B82F6)
    val Neutral = Color(0xFFE5E7EB)
    val Light = Color(0xFFF8FAFC)

    /** The gradient of the adaptive icon's background, for hero areas. */
    val Gradient = Brush.linearGradient(listOf(Cyan, Blue, Navy))

    /** Status colours, readable on both themes. */
    val Success = Color(0xFF16A34A)
    val Warning = Color(0xFFF59E0B)
}

private val LightColors = lightColorScheme(
    primary = Brand.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9FF),
    onPrimaryContainer = Color(0xFF00296B),
    secondary = Brand.SecondaryBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EDFE),
    onSecondaryContainer = Brand.Navy,
    tertiary = Brand.Cyan,
    onTertiary = Brand.Navy,
    background = Brand.Light,
    onBackground = Brand.Navy,
    surface = Brand.Light,
    onSurface = Brand.Navy,
    surfaceVariant = Brand.Neutral,
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFEBEFF5),
    surfaceContainerHighest = Brand.Neutral,
    outline = Color(0xFF94A3B8),
    outlineVariant = Brand.Neutral,
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

private val DarkColors = darkColorScheme(
    primary = Brand.Cyan,
    onPrimary = Brand.Navy,
    primaryContainer = Color(0xFF0B3D6B),
    onPrimaryContainer = Color(0xFFCDEFFF),
    secondary = Brand.SecondaryBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF16406E),
    onSecondaryContainer = Brand.Light,
    tertiary = Brand.Cyan,
    onTertiary = Brand.Navy,
    background = Brand.Navy,
    onBackground = Brand.Light,
    surface = Brand.Navy,
    onSurface = Brand.Light,
    surfaceVariant = Color(0xFF13355A),
    onSurfaceVariant = Color(0xFFB6C4D6),
    surfaceContainerLowest = Color(0xFF071C31),
    surfaceContainerLow = Color(0xFF0C2846),
    surfaceContainer = Color(0xFF0E2D4D),
    surfaceContainerHigh = Color(0xFF13355A),
    surfaceContainerHighest = Color(0xFF1A4068),
    outline = Color(0xFF5B7491),
    outlineVariant = Color(0xFF1E4470),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFEE2E2),
)

private val BrandShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val BrandTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Material 3 with the brand colours, following the system's light or dark setting. */
@Composable
fun BrandTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = BrandShapes,
        typography = BrandTypography,
        content = content,
    )
}
