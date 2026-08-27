package com.vircas.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VirCasColors = darkColorScheme(
    primary = Color(0xFF8B5CF6),
    secondary = Color(0xFF22D3EE),
    tertiary = Color(0xFFF59E0B),
    background = Color(0xFF080B12),
    surface = Color(0xFF111827),
    surfaceVariant = Color(0xFF182033),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC)
)

@Composable
fun VirCasTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = VirCasColors, content = content)
}
