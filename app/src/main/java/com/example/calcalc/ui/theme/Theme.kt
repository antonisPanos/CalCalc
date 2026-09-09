package com.example.calcalc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// The app is dark-mode only by design, so there is no light scheme and no dynamic color:
// the palette must stay identical on every device.
private val CalCalcColorScheme = darkColorScheme(
    primary = Green80,
    onPrimary = GreenDark,
    primaryContainer = GreenContainer,
    onPrimaryContainer = OnGreenContainer,
    secondary = Sage,
    onSecondary = GreenDark,
    secondaryContainer = SageContainer,
    onSecondaryContainer = OnSurfaceBright,
    tertiary = Amber,
    onTertiary = GreenDark,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = Amber,
    error = Red,
    onError = GreenDark,
    errorContainer = RedContainer,
    onErrorContainer = Red,
    background = Backdrop,
    onBackground = OnSurfaceBright,
    surface = Backdrop,
    onSurface = OnSurfaceBright,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = OnSurfaceMuted,
    surfaceContainerLowest = Backdrop,
    surfaceContainerLow = SurfaceDim,
    surfaceContainer = SurfaceDim,
    surfaceContainerHigh = SurfaceElevated,
    surfaceContainerHighest = SurfaceElevated,
    outline = OutlineDim,
    outlineVariant = SurfaceElevated,
)

@Composable
fun CalCalcTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CalCalcColorScheme,
        typography = Typography,
        content = content
    )
}
