package com.rork.plcpanelstudio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ScadaColorScheme = darkColorScheme(
    primary = SignalOrange,
    onPrimary = Ink,
    primaryContainer = SignalOrangeDim,
    onPrimaryContainer = TextHi,
    secondary = SignalTeal,
    onSecondary = Ink,
    secondaryContainer = SignalTealDim,
    onSecondaryContainer = TextHi,
    tertiary = SignalAmber,
    onTertiary = Ink,
    background = Ink,
    onBackground = TextHi,
    surface = Surface1,
    onSurface = TextHi,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMid,
    surfaceContainer = Surface2,
    surfaceContainerHigh = SurfaceRaised,
    surfaceContainerHighest = SurfaceRaised,
    surfaceContainerLow = Surface1,
    surfaceContainerLowest = Ink,
    error = SignalRed,
    onError = TextHi,
    outline = Line,
    outlineVariant = LineBright,
    scrim = Ink
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ScadaColorScheme,
        typography = AppTypography,
        content = content
    )
}
