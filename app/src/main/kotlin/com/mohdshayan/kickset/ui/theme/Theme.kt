package com.mohdshayan.kickset.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = LightAccent, onPrimary = LightOnAccent,
    primaryContainer = LightSelection, onPrimaryContainer = LightInk,
    secondary = LightAccent, onSecondary = LightOnAccent,
    secondaryContainer = LightSelection, onSecondaryContainer = LightInk,
    tertiary = LightAccent, onTertiary = LightOnAccent,
    background = LightBackground, onBackground = LightInk,
    surface = LightSurface, onSurface = LightInk,
    surfaceVariant = LightBackground, onSurfaceVariant = LightScale,
    surfaceTint = LightSurface,
    surfaceBright = LightSurface, surfaceDim = LightBackground,
    surfaceContainerLowest = LightSurface, surfaceContainerLow = LightSurface, surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurface, surfaceContainerHighest = LightSurface,
    inverseSurface = LightInk, inverseOnSurface = LightSurface, inversePrimary = DarkAccent,
    outline = LightScale, outlineVariant = LightRule,
    error = LightError, onError = LightOnAccent, errorContainer = LightErrorWash, onErrorContainer = LightError,
    scrim = LightInk,
)

private val DarkColors = darkColorScheme(
    primary = DarkAccent, onPrimary = DarkOnAccent,
    primaryContainer = DarkSelection, onPrimaryContainer = DarkInk,
    secondary = DarkAccent, onSecondary = DarkOnAccent,
    secondaryContainer = DarkSelection, onSecondaryContainer = DarkInk,
    tertiary = DarkAccent, onTertiary = DarkOnAccent,
    background = DarkBackground, onBackground = DarkInk,
    surface = DarkSurface, onSurface = DarkInk,
    surfaceVariant = DarkBackground, onSurfaceVariant = DarkScale,
    surfaceTint = DarkSurface,
    surfaceBright = DarkSurface, surfaceDim = DarkBackground,
    surfaceContainerLowest = DarkSurface, surfaceContainerLow = DarkSurface, surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurface, surfaceContainerHighest = DarkSurface,
    inverseSurface = DarkInk, inverseOnSurface = DarkSurface, inversePrimary = LightAccent,
    outline = DarkScale, outlineVariant = DarkRule,
    error = DarkError, onError = DarkOnAccent, errorContainer = DarkErrorWash, onErrorContainer = DarkError,
    scrim = DarkBackground,
)

/**
 * The app theme. Dynamic colour is off: layout blue is the identity. themeMode is SYSTEM, LIGHT or DARK
 * from Settings. System bar icons follow the resolved mode.
 */
@Composable
fun AppTheme(
    themeMode: String = "SYSTEM",
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
