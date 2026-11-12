package com.suna.player.ui.theme

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.suna.player.data.ThemeMode
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.material3.dynamicLightColorScheme
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.suna.player.data.Appearance
import com.suna.player.data.Palette

/** True while a wallpaper picture is drawn behind the app. */
val LocalHasWallpaper = staticCompositionLocalOf { false }

/** The current wallpaper's colors, when there is one. */
val LocalWallpaperTones = staticCompositionLocalOf<WallpaperTones?> { null }

/** True when the app is showing its light version. */
val LocalIsLight = staticCompositionLocalOf { false }

/** Small secondary text. Digits are all the same width so times and counts don't jitter. */
val SmallLabel = TextStyle(fontSize = 12.sp, letterSpacing = 0.1.sp, fontFeatureSettings = "tnum")

/** Accent choices offered on top of each palette's own accent. */
val AccentChoices = listOf(
    Color(0xFF0A84FF), // blue
    Color(0xFF7CE38B), // phosphor green
    Color(0xFFA594F9), // lavender
    Color(0xFFCBA6F7), // mauve
    Color(0xFF7AA2F7), // periwinkle
    Color(0xFF88C0D0), // frost
    Color(0xFF94E2D5), // teal
    Color(0xFFF9E2AF), // yellow
    Color(0xFFFAB387), // peach
    Color(0xFFF38BA8), // red
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

/**
 * A theme's colors in its light or dark version. Dark versions sit on pure black so unlit
 * pixels stay off on an OLED screen. Null means "build it from the phone's colors".
 */
fun Palette.colors(light: Boolean, tones: WallpaperTones?): PaletteColors? = when (this) {
    Palette.WALLPAPER -> tones?.colors(light) ?: Palette.OLED.colors(light, null)
    Palette.SYSTEM -> null
    else -> if (light) lightColors() else darkColors()?.onBlack()
}

private fun PaletteColors.onBlack() = PaletteColors(
    background = Color.Black,
    panel = lerp(Color.Black, panel, 0.55f),
    raised = lerp(Color.Black, raised, 0.8f),
    border = lerp(Color.Black, border, 0.8f),
    text = text,
    subtext = subtext,
    accent = accent,
)

private fun Palette.darkColors(): PaletteColors? = when (this) {
    Palette.OLED -> PaletteColors(
        background = Color(0xFF000000), panel = Color(0xFF111114), raised = Color(0xFF1E1E22),
        border = Color(0xFF2A2A30), text = Color(0xFFE8E8EC), subtext = Color(0xFF8E8E96),
        accent = DefaultBlue(light = false),
    )
    Palette.PHOSPHOR -> PaletteColors(
        background = Color(0xFF000000), panel = Color(0xFF071209), raised = Color(0xFF0E1F11),
        border = Color(0xFF1A3A20), text = Color(0xFFB9F5C2), subtext = Color(0xFF5E9B68),
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
    Palette.WALLPAPER, Palette.SYSTEM -> null
}

/** Light versions: each scheme's official light variant where it has one. */
private fun Palette.lightColors(): PaletteColors? = when (this) {
    Palette.OLED -> PaletteColors(
        background = Color(0xFFF2F2F7), panel = Color(0xFFFFFFFF), raised = Color(0xFFE5E5EA),
        border = Color(0xFFD8D8DE), text = Color(0xFF1C1C1E), subtext = Color(0xFF6E6E73),
        accent = DefaultBlue(light = true), dark = false,
    )
    Palette.PHOSPHOR -> PaletteColors(
        background = Color(0xFFEEF6EF), panel = Color(0xFFFFFFFF), raised = Color(0xFFDDEEE0),
        border = Color(0xFFC6DFCB), text = Color(0xFF10301A), subtext = Color(0xFF4F7A58),
        accent = Color(0xFF1F9D44), dark = false,
    )
    // Catppuccin Latte
    Palette.MOCHA -> PaletteColors(
        background = Color(0xFFE6E9EF), panel = Color(0xFFEFF1F5), raised = Color(0xFFDCE0E8),
        border = Color(0xFFCCD0DA), text = Color(0xFF4C4F69), subtext = Color(0xFF6C6F85),
        accent = Color(0xFF8839EF), dark = false,
    )
    // Nord Snow Storm
    Palette.NORD -> PaletteColors(
        background = Color(0xFFE5E9F0), panel = Color(0xFFF2F4F8), raised = Color(0xFFD8DEE9),
        border = Color(0xFFC9D0DC), text = Color(0xFF2E3440), subtext = Color(0xFF4C566A),
        accent = Color(0xFF5E81AC), dark = false,
    )
    // Gruvbox light
    Palette.GRUVBOX -> PaletteColors(
        background = Color(0xFFF2E5BC), panel = Color(0xFFFBF1C7), raised = Color(0xFFEBDBB2),
        border = Color(0xFFD5C4A1), text = Color(0xFF3C3836), subtext = Color(0xFF7C6F64),
        accent = Color(0xFFB57614), dark = false,
    )
    // Rosé Pine Dawn
    Palette.ROSE_PINE -> PaletteColors(
        background = Color(0xFFF2E9E1), panel = Color(0xFFFFFAF3), raised = Color(0xFFEAE1DA),
        border = Color(0xFFDFDAD9), text = Color(0xFF575279), subtext = Color(0xFF797593),
        accent = Color(0xFFD7827E), dark = false,
    )
    // Tokyo Night Day
    Palette.TOKYO_NIGHT -> PaletteColors(
        background = Color(0xFFE1E2E7), panel = Color(0xFFF0F0F4), raised = Color(0xFFD5D6DB),
        border = Color(0xFFC4C8DA), text = Color(0xFF3760BF), subtext = Color(0xFF6172B0),
        accent = Color(0xFF2E7DE9), dark = false,
    )
    Palette.GRAPHITE -> PaletteColors(
        background = Color(0xFFEDEDED), panel = Color(0xFFFAFAFA), raised = Color(0xFFE0E0E0),
        border = Color(0xFFD0D0D0), text = Color(0xFF222222), subtext = Color(0xFF6A6A6A),
        accent = Color(0xFF6B5CE7), dark = false,
    )
    Palette.WALLPAPER, Palette.SYSTEM -> null
}

/** Whether the app should be light, given the mode, the wallpaper and the phone's setting. */
@Composable
fun isLightMode(appearance: Appearance?, tones: WallpaperTones?): Boolean {
    val phoneDark = isSystemInDarkTheme()
    return when (appearance?.mode ?: ThemeMode.AUTO) {
        ThemeMode.LIGHT -> true
        ThemeMode.DARK -> false
        // A bright wallpaper gets the light version, a dark one the dark version.
        ThemeMode.AUTO -> if (appearance?.hasWallpaper == true && tones != null) tones.brightness > 0.55f else !phoneDark
    }
}

/**
 * Applies the user's theme, light or dark version, and accent. [wallpaper] feeds the
 * "From wallpaper" theme and Automatic mode.
 */
@Composable
fun SunaTheme(appearance: Appearance?, wallpaper: ImageBitmap?, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val palette = appearance?.palette ?: Palette.OLED
    val tones by produceState<WallpaperTones?>(null, wallpaper) {
        value = wallpaper?.let { withContext(Dispatchers.Default) { it.tones() } }
    }
    val light = isLightMode(appearance, tones)

    var colors: ColorScheme = palette.colors(light, tones)?.toScheme()
        ?: if (light) dynamicLightColorScheme(context) else dynamicDarkColorScheme(context).onBlack()
    appearance?.accent?.let { colors = colors.withAccent(Color(it), light) }

    // Keep the status and navigation bar icons readable against the chosen theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val bars = WindowCompat.getInsetsController(window, view)
            bars.isAppearanceLightStatusBars = light
            bars.isAppearanceLightNavigationBars = light
        }
    }

    CompositionLocalProvider(LocalWallpaperTones provides tones, LocalIsLight provides light) {
        MaterialTheme(
            colorScheme = colors,
            typography = SunaTypography,
            shapes = SunaShapes,
            content = content,
        )
    }
}

/**
 * The app's backdrop: the plain theme background, or the placed wallpaper under a veil that
 * is black in the dark version and white in the light one, so text stays readable.
 */
@Composable
fun AppBackground(
    wallpaper: ImageBitmap?,
    appearance: Appearance?,
    content: @Composable () -> Unit,
) {
    val light = LocalIsLight.current
    val veil = if (light) Color.White else Color.Black
    // Space a zoomed-out picture doesn't cover is pure black (or white in the light version).
    val base = if (wallpaper != null) veil else MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize().background(base)) {
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
                    .background(veil.copy(alpha = appearance.dim.coerceIn(0f, 0.9f))),
            )
        }
        CompositionLocalProvider(
            LocalHasWallpaper provides (wallpaper != null),
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}

/** The wallpaper filling the screen, then zoomed and shifted; uncovered space shows black. */
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

/** The phone's Material You colors, moved onto pure black like every other theme. */
private fun ColorScheme.onBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = lerp(Color.Black, surfaceContainerLow, 0.55f),
    surfaceContainer = lerp(Color.Black, surfaceContainer, 0.6f),
    surfaceContainerHigh = lerp(Color.Black, surfaceContainerHigh, 0.8f),
    surfaceContainerHighest = lerp(Color.Black, surfaceContainerHighest, 0.85f),
    outlineVariant = lerp(Color.Black, outlineVariant, 0.6f),
)

private fun ColorScheme.withAccent(chosen: Color, light: Boolean): ColorScheme {
    // Pastel accents are deepened on light backgrounds so they stay readable.
    val accent = if (light && chosen.luminance() > 0.35f) lerp(chosen, Color.Black, 0.35f) else chosen
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
        labelSmall = SmallLabel.copy(fontSize = 11.sp),
    )
}
