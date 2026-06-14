package com.example.smsaggregator.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkScheme = darkColorScheme(
    surface           = Dark_Surface,
    background        = Dark_Surface,
    surfaceVariant    = Dark_SurfVar,
    onSurface         = Dark_OnSurface,
    onSurfaceVariant  = Dark_OnSurfVar,
    outline           = Dark_Outline,
    outlineVariant    = Dark_OutlineVar,
    primary           = Dark_Primary,
    onPrimary         = Dark_OnPrimary,
    primaryContainer  = Dark_PrimContainer,
    onPrimaryContainer= Dark_OnPrimCont,
    error             = Dark_Error,
    onBackground      = Dark_OnSurface
)

private val LightScheme = lightColorScheme(
    surface           = Light_Surface,
    background        = Light_Surface,
    surfaceVariant    = Light_SurfVar,
    onSurface         = Light_OnSurface,
    onSurfaceVariant  = Light_OnSurfVar,
    outline           = Light_Outline,
    outlineVariant    = Light_OutlineVar,
    primary           = Light_Primary,
    onPrimary         = Light_OnPrimary,
    primaryContainer  = Light_PrimContainer,
    onPrimaryContainer= Light_OnPrimCont,
    error             = Light_Error,
    onBackground      = Light_OnSurface
)

@Composable
fun SmsAggregatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkScheme else LightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colors, content = content)
}
