package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val XRayColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = DarkOnCyan,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = CyanLight,
    secondary = GoldHighlight,
    onSecondary = DarkOnCyan,
    tertiary = EmeraldOk,
    background = DeepNavy,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = CyanDim,
    error = AlertRed,
)

@Composable
fun XRayMirrorTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = XRayColorScheme,
            typography = Typography,
            content = content,
        )
    }
}
