// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The brand palette (docs/brand). */
object Brand {
    val Blue = Color(0xFF0D6EFD)
    val Navy = Color(0xFF0A2540)
    val Cyan = Color(0xFF00B4FF)
    val SecondaryBlue = Color(0xFF3B82F6)
    val Neutral = Color(0xFFE5E7EB)
    val Light = Color(0xFFF8FAFC)
}

private val LightColors = lightColorScheme(
    primary = Brand.Blue,
    onPrimary = Color.White,
    secondary = Brand.SecondaryBlue,
    onSecondary = Color.White,
    tertiary = Brand.Cyan,
    background = Brand.Light,
    onBackground = Brand.Navy,
    surface = Brand.Light,
    onSurface = Brand.Navy,
    surfaceVariant = Brand.Neutral,
    onSurfaceVariant = Brand.Navy,
)

private val DarkColors = darkColorScheme(
    primary = Brand.Cyan,
    onPrimary = Brand.Navy,
    secondary = Brand.SecondaryBlue,
    onSecondary = Color.White,
    tertiary = Brand.Cyan,
    background = Brand.Navy,
    onBackground = Brand.Light,
    surface = Brand.Navy,
    onSurface = Brand.Light,
    surfaceVariant = Color(0xFF13355A),
    onSurfaceVariant = Brand.Neutral,
)

/** Material 3 with the brand colours, following the system's light or dark setting. */
@Composable
fun BrandTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
