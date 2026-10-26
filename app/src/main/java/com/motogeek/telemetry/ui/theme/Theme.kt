package com.motogeek.telemetry.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = RacingRed,
    secondary = TelemetryCyan,
    background = DarkCharcoalBg,
    surface = DrawerSurfaceBg,
    error = CrimsonHeaderRed,
    tertiary = ApexAmber,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    outline = GlassmorphicBorder
)

@Composable
fun MotoGeekTelemetryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
