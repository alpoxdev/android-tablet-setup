package dev.paddeck.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD1FF),
    onPrimary = Color(0xFF00212E),
    secondary = Color(0xFFB9C8D6),
    background = Color(0xFF0E1013),
    onBackground = Color(0xFFE7EBEF),
    surface = Color(0xFF16191E),
    onSurface = Color(0xFFE7EBEF),
    surfaceVariant = Color(0xFF20252C),
    onSurfaceVariant = Color(0xFFC2CBD4),
)

private val Light = lightColorScheme(
    primary = Color(0xFF00658A),
    background = Color(0xFFF6F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EDF2),
)

@Composable
fun PadDeckTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
