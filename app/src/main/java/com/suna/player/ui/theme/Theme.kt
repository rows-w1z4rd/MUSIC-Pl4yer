package com.suna.player.ui.theme

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.suna.player.data.Appearance
import com.suna.player.data.Palette

/** True while a wallpaper picture is drawn behind the app. */
val LocalHasWallpaper = staticCompositionLocalOf { false }

/** Small monospace labels: counts, times, section names. Part of the app's terminal flavor. */
val MonoLabel = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 0.sp)

/** Accent choices offered on top of each palette's own accent. */
val AccentChoices = listOf(
    Color(0xFF7CE38B), // phosphor green
    Color(0xFFA594F9), // lavender
    Color(0xFFCBA6F7), // mauve
    Color(0xFF7AA2F7), // blue
    Color(0xFF88C0D0), // frost
    Color(0xFF94E2D5), // teal
    Color(0xFFF9E2AF), // yellow
    Color(0xFFFAB387), // peach
    Color(0xFFF38BA8), // red
    Color(0xFFEBBCBA), // rose
)

/** The handful of colors each theme is built from. */
class PaletteColors(
    val background: Color,
    /** Panels and cards. */
    val panel: Color,
    /** Buttons, chips and anything raised above a panel. */
    val raised: Color,
    /** Hairline outlines. */
    val border: Color,
    val text: Color,
    val subtext: Color,
    val accent: Color,
    val dark: Boolean = true,
)

fun Palette.colors(): PaletteColors? = when (this) {
    Palette.OLED -> PaletteColors(
        background = Color(0xFF000000), panel = Color(0xFF0C0C0E), raised = Color(0xFF18181C),
        border = Color(0xFF232329), text = Color(0xFFE4E4E8), subtext = Color(0xFF8B8B95),
        accent = Color(0xFF7CE38B),
    )
    Palette.PHOSPHOR -> PaletteColors(
        background = Color(0xFF000000), panel = Color(0xFF040A05), raised = Color(0xFF0B170D),
        border = Color(0xFF15301A), text = Color(0xFFB9F5C2), subtext = Color(0xFF5E9B68),
        accent = Color(0xFF4CFF72),
    )
    Palette.MOCHA -> PaletteColors(
        background = Color(0xFF11111B), panel = Color(0xFF1E1E2E), raised = Color(0xFF313244),
        border = Color(0xFF313244), text = Color(0xFFCDD6F4), subtext = Color(0xFFA6ADC8),
        accent = Color(0xFFCBA6F7),
    )
    Palette.NORD -> PaletteColors(
        background = Color(0xFF242933), panel = Color(0xFF2E3440), raised = Color(0xFF3B4252),
        border = Color(0xFF434C5E), text = Color(0xFFECEFF4), subtext = Color(0xFF9AA5B8),
        accent = Color(0xFF88C0D0),
    )
    Palette.GRUVBOX -> PaletteColors(
        background = Color(0xFF1D2021), panel = Color(0xFF282828), raised = Color(0xFF3C3836),
        border = Color(0xFF3C3836), text = Color(0xFFEBDBB2), subtext = Color(0xFFA89984),
        accent = Color(0xFFFABD2F),
    )
    Palette.ROSE_PINE -> PaletteColors(
        background = Color(0xFF191724), panel = Color(0xFF1F1D2E), raised = Color(0xFF26233A),
        border = Color(0xFF2A273F), text = Color(0xFFE0DEF4), subtext = Color(0xFF908CAA),
        accent = Color(0xFFEBBCBA),
    )
    Palette.TOKYO_NIGHT -> PaletteColors(
        background = Color(0xFF16161E), panel = Color(0xFF1A1B26), raised = Color(0xFF24283B),
        border = Color(0xFF292E42), text = Color(0xFFC0CAF5), subtext = Color(0xFF7982A9),
        accent = Color(0xFF7AA2F7),
    )
    Palette.GRAPHITE -> PaletteColors(
        background = Color(0xFF161616), panel = Color(0xFF1E1E1E), raised = Color(0xFF2A2A2A),
        border = Color(0xFF303030), text = Color(0xFFDCDDDE), subtext = Color(0xFF999999),
        accent = Color(0xFF8A7CF5),
    )
    Palette.LATTE -> PaletteColors(
        background = Color(0xFFDCE0E8), panel = Color(0xFFEFF1F5), raised = Color(0xFFE6E9EF),
        border = Color(0xFFCCD0DA), text = Color(0xFF4C4F69), subtext = Color(0xFF6C6F85),
        accent = Color(0xFF8839EF), dark = false,
    )
    Palette.SYSTEM -> null // built from the phone's Material You colors
}

/**
 * Applies the user's palette and accent. With a wallpaper the app always uses a dark palette
 * over a darkened picture, so text stays readable whatever image is chosen.
 */
@Composable
fun SunaTheme(appearance: Appearance?, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val hasWallpaper = appearance?.hasWallpaper == true
    var palette = appearance?.palette ?: Palette.OLED
    if (hasWallpaper && palette.colors()?.dark == false) palette = Palette.OLED

    var colors: ColorScheme = palette.colors()?.toScheme() ?: run {
        val dark = hasWallpaper || isSystemInDarkTheme()
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    appearance?.accent?.let { colors = colors.withAccent(Color(it)) }
    val dark = colors.background.luminance() < 0.5f

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

    MaterialTheme(
        colorScheme = colors,
        typography = SunaTypography,
        shapes = SunaShapes,
        content = content,
    )
}

/** The app's backdrop: the plain theme background, or the placed wallpaper with a dark veil. */
@Composable
fun AppBackground(
    wallpaper: ImageBitmap?,
    appearance: Appearance?,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (wallpaper != null && appearance != null) {
            WallpaperImage(
                wallpaper = wallpaper,
                scale = appearance.wallpaperScale,
                x = appearance.wallpaperX,
                y = appearance.wallpaperY,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = appearance.dim.coerceIn(0f, 0.9f))),
            )
        }
        CompositionLocalProvider(
            LocalHasWallpaper provides (wallpaper != null),
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}

/** The wallpaper filling the screen, then zoomed and shifted; uncovered space shows the background. */
@Composable
fun WallpaperImage(wallpaper: ImageBitmap, scale: Float, x: Float, y: Float) {
    Image(
        bitmap = wallpaper,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = x * size.width
                translationY = y * size.height
            },
    )
}

private fun PaletteColors.toScheme(): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val onAccent = if (accent.luminance() > 0.45f) Color(0xFF101014) else Color.White
    val accentSoft = accent.copy(alpha = 0.16f).compositeOver(panel)
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = text,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accentSoft,
        onSecondaryContainer = text,
        tertiary = accent,
        onTertiary = onAccent,
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = raised,
        onSurfaceVariant = subtext,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = background,
        surfaceContainerLow = panel,
        surfaceContainer = panel,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = raised,
        surfaceBright = raised,
        surfaceDim = background,
        outline = subtext.copy(alpha = 0.6f).compositeOver(panel),
        outlineVariant = border,
        inverseSurface = text,
        inverseOnSurface = background,
    )
}

private fun ColorScheme.withAccent(accent: Color): ColorScheme {
    val onAccent = if (accent.luminance() > 0.45f) Color(0xFF101014) else Color.White
    val soft = accent.copy(alpha = 0.16f).compositeOver(surfaceContainerLow)
    return copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = soft,
        onPrimaryContainer = onSurface,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = soft,
        onSecondaryContainer = onSurface,
        tertiary = accent,
        onTertiary = onAccent,
    )
}

private val SunaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

private val SunaTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(letterSpacing = (-0.3).sp),
        titleLarge = t.titleLarge.copy(letterSpacing = (-0.2).sp),
        labelSmall = MonoLabel.copy(fontSize = 11.sp),
    )
}
