package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SiteMindColorScheme = lightColorScheme(
    primary = SleekPrimary,
    secondary = SleekSecondary,
    tertiary = SleekTertiary,
    background = SleekBackground,
    surface = SleekSurface,
    surfaceVariant = SleekSurfaceVariant,
    onPrimary = SleekSurfaceVariant, // White text/surface on primary
    onSecondary = SleekOnTertiary,
    onBackground = SleekOnBackground,
    onSurface = SleekOnBackground,
    onSurfaceVariant = SleekOnSurface,
    outline = SleekBorder
)

@Composable
fun SiteMindTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = SiteMindColorScheme,
        typography = Typography,
        content = content
    )
}



