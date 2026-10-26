package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.Black,
    primaryContainer = CockpitSurfaceVariant,
    onPrimaryContainer = NeonCyan,
    secondary = TrafficSignRed,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2D1215),
    onSecondaryContainer = Color(0xFFFFB4AB),
    tertiary = NeonEmerald,
    onTertiary = Color.Black,
    background = AmoledBlack,
    onBackground = CockpitTextPrimary,
    surface = CockpitSurface,
    onSurface = CockpitTextPrimary,
    surfaceVariant = CockpitSurfaceVariant,
    onSurfaceVariant = CockpitTextSecondary,
    outline = CockpitCardBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    // True AMOLED Dark theme is prioritized for nighttime driving comfort
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
