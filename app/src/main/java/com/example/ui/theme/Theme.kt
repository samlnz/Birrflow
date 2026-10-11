package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BlueFintech,
    onPrimary = Color.White,
    primaryContainer = SurfaceCardDark,
    onPrimaryContainer = SkyBlueTextPrimary,
    secondary = AmberAccent,
    onSecondary = Color.White,
    secondaryContainer = SurfaceDark,
    onSecondaryContainer = AmberLight,
    tertiary = CyanIncome,
    background = SkyBlueBg,
    surface = SurfaceDark,
    surfaceVariant = SurfaceCardDark,
    onBackground = SkyBlueTextPrimary,
    onSurface = SkyBlueTextPrimary,
    onSurfaceVariant = SkyBlueTextSecondary,
    outline = SurfaceBorderDark
)

private val LightColorScheme = lightColorScheme(
    primary = BlueFintech,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBAE6FD),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = AmberAccent,
    onSecondary = Color.White,
    tertiary = CyanIncome,
    background = SurfaceLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceCardLight,
    onBackground = Color(0xFF04203C),
    onSurface = Color(0xFF04203C),
    onSurfaceVariant = Color(0xFF2D537B),
    outline = SurfaceBorderLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to sleek modern dark fintech look matching preview
    dynamicColor: Boolean = false, // Keep consistent fintech brand identity
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
