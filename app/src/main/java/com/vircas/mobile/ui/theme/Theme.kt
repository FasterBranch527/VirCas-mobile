package com.vircas.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VirCasDarkColors = darkColorScheme(
    primary = Color(0xFF8B5CF6),
    secondary = Color(0xFF22D3EE),
    tertiary = Color(0xFFF59E0B),
    background = Color(0xFF080B12),
    surface = Color(0xFF111827),
    surfaceVariant = Color(0xFF182033),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC)
)

private val VirCasLightColors = lightColorScheme(
    primary = Color(0xFF6D28D9),
    secondary = Color(0xFF0E7490),
    tertiary = Color(0xFFB45309),
    background = Color(0xFFF5F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8ECF4),
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827)
)

@Composable
fun VirCasTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) VirCasDarkColors else VirCasLightColors,
        content = content
    )
}
