package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TacticalColorScheme = darkColorScheme(
    primary = TacticalCyan,
    onPrimary = Color(0xFF00262C),
    primaryContainer = Color(0xFF004E5B),
    onPrimaryContainer = Color(0xFFB3F7FF),
    secondary = TacticalAmber,
    onSecondary = Color(0xFF332200),
    secondaryContainer = Color(0xFF5A3D00),
    onSecondaryContainer = TacticalAmberLight,
    tertiary = TacticalEmerald,
    background = TacticalNavyDark,
    onBackground = TacticalTextPrimary,
    surface = TacticalNavySurface,
    onSurface = TacticalTextPrimary,
    surfaceVariant = TacticalNavyCard,
    onSurfaceVariant = TacticalTextSecondary,
    outline = TacticalBorder,
    error = TacticalCoral
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TacticalColorScheme,
        typography = Typography,
        content = content
    )
}
