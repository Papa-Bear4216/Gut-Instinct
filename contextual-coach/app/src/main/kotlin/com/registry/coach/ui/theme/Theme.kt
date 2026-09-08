package com.registry.coach.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val DeepObsidian = Color(0xFF0D0B18)
val DarkSurface = Color(0xFF141224)
val DarkSurfaceElevated = Color(0xFF1E1B33)
val DarkBorder = Color(0xFF2E294C)
val AccentPurple = Color(0xFF9D8CFF)
val PrimaryPurple = Color(0xFF7C68EE)
val AccentEmerald = Color(0xFF00E5A3)
val MutedText = Color(0xFF9691B2)
val TextLight = Color(0xFFF3F1FB)

private val SecondGuessColorScheme = darkColorScheme(
    primary = AccentPurple,
    onPrimary = Color(0xFF1A0F54),
    primaryContainer = Color(0xFF38268A),
    onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = AccentEmerald,
    onSecondary = Color(0xFF003824),
    secondaryContainer = Color(0xFF005237),
    onSecondaryContainer = Color(0xFF70F8C3),
    background = DeepObsidian,
    onBackground = TextLight,
    surface = DarkSurface,
    onSurface = TextLight,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = MutedText,
    outline = DarkBorder,
    outlineVariant = Color(0xFF221F38)
)

@Composable
fun SecondGuessTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = SecondGuessColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = DeepObsidian.toArgb()
            window.navigationBarColor = DeepObsidian.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
