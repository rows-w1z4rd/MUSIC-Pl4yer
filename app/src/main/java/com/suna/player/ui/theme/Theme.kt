package com.suna.player.ui.theme

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.suna.player.data.Appearance
import com.suna.player.data.ThemeMode

/** True while a wallpaper picture is drawn behind the app. */
val LocalHasWallpaper = staticCompositionLocalOf { false }

/**
 * Applies the user's appearance choices. With a wallpaper the app always uses light text on a
 * darkened picture, so text stays readable whatever image is chosen.
 */
@Composable
fun SunaTheme(appearance: Appearance?, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val mode = appearance?.themeMode ?: ThemeMode.BLACK
    val hasWallpaper = appearance?.hasWallpaper == true
    val dark = hasWallpaper || when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK -> true
    }

    var colors = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    if (mode == ThemeMode.BLACK && !hasWallpaper) colors = colors.pureBlack()
    appearance?.accent?.let { colors = colors.withAccent(Color(it)) }

    // Keep the status and navigation bar icons readable against the chosen theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val bars = WindowCompat.getInsetsController(window, view)
            bars.isAppearanceLightStatusBars = !dark
            bars.isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(colorScheme = colors, content = content)
}

/** The app's backdrop: the plain theme background, or the wallpaper with a dark veil over it. */
@Composable
fun AppBackground(wallpaper: ImageBitmap?, dim: Float, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim.coerceIn(0f, 0.9f))))
        }
        CompositionLocalProvider(
            LocalHasWallpaper provides (wallpaper != null),
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}

private fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    // Dark grey cards on true black, so only the cards light up pixels on an OLED screen.
    surfaceContainerLow = Color(0xFF141416),
    surfaceContainer = Color(0xFF1A1A1D),
    surfaceContainerHigh = Color(0xFF222226),
    surfaceContainerHighest = Color(0xFF2B2B30),
)

private fun ColorScheme.withAccent(accent: Color): ColorScheme {
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val container = accent.copy(alpha = 0.28f).compositeOver(surface)
    return copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = onSurface,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = container,
        onSecondaryContainer = onSurface,
        tertiary = accent,
        onTertiary = onAccent,
        surfaceTint = accent,
    )
}
