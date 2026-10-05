package com.gothwad.indogram.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity

private val DarkColorScheme = darkColorScheme(
    primary = IndoPrimary,
    secondary = IndoSecondary,
    tertiary = IndoAccent,
    background = IndoDarkBackground,
    surface = IndoSurfaceDark,
    surfaceVariant = IndoSurfaceCard,
    onPrimary = IndoDarkBackground,
    onSecondary = IndoDarkBackground,
    onBackground = IndoLightBackground,
    onSurface = IndoLightBackground
)

private val LightColorScheme = lightColorScheme(
    primary = IndoPrimaryLight,
    secondary = IndoSecondaryLight,
    tertiary = IndoAccent,
    background = IndoLightBackground,
    surface = IndoSurfaceLight,
    surfaceVariant = IndoSurfaceCardLight,
    onPrimary = IndoSurfaceLight,
    onSecondary = IndoSurfaceLight,
    onBackground = IndoDarkBackground,
    onSurface = IndoDarkBackground
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Allow dynamic colors on API 31+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            SideEffect {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
