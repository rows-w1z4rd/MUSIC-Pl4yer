package com.suna.player.ui.theme

import com.suna.player.data.AccentHue
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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
    /** When set, the background fades from this color at the top into [background]. */
    val glow: Color? = null,
)

/** The theme's colors. From wallpaper is light or dark depending on the picture. */
fun Palette.colors(tones: WallpaperTones?): PaletteColors = when (this) {
    Palette.BLUE -> PaletteColors(
        background = Color(0xFF050C1E), glow = Color(0xFF0F2A66),
        panel = Color(0xFF0B1834), raised = Color(0xFF13244B), border = Color(0xFF1B305E),
        text = Color(0xFFEAF1FF), subtext = Color(0xFF8DA2C9), accent = Color(0xFF4DA3FF),
    )
    // Like Samsung's black: true black with soft grey cards.
    Palette.BLACK -> PaletteColors(
        background = Color.Black,
        panel = Color(0xFF121214), raised = Color(0xFF1F1F22), border = Color(0xFF2A2A2E),
        text = Color(0xFFFAFAFA), subtext = Color(0xFF8E8E93), accent = Color(0xFF3E91FF),
    )
    // Spotify-style: black with a deep green glow at the top and a fresh green accent.
    Palette.GREEN -> PaletteColors(
        background = Color.Black, glow = Color(0xFF0B3D22),
        panel = Color(0xFF0B1510), raised = Color(0xFF132219), border = Color(0xFF1C3125),
        text = Color(0xFFF0FAF3), subtext = Color(0xFF8DA696), accent = Color(0xFF1ED760),
    )
    Palette.WHITE -> PaletteColors(
        background = Color(0xFFF2F2F7),
        panel = Color.White, raised = Color(0xFFE9E9EE), border = Color(0xFFDCDCE1),
        text = Color(0xFF111113), subtext = Color(0xFF6E6E73), accent = Color(0xFF007AFF),
        dark = false,
    )
    Palette.WALLPAPER -> tones?.let { it.colors(light = it.brightness > 0.55f) } ?: Palette.BLUE.colors(null)
}

/** An accent in the shade that suits the theme: brighter on dark, deeper on white. */
fun AccentHue.on(light: Boolean): Color = when (this) {
    AccentHue.MONO -> if (light) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    AccentHue.BLUE -> if (light) Color(0xFF0A66E0) else Color(0xFF3E91FF)
    AccentHue.SKY -> if (light) Color(0xFF0284C7) else Color(0xFF5AC8FA)
    AccentHue.GREEN -> if (light) Color(0xFF15803D) else Color(0xFF1ED760)
    AccentHue.MINT -> if (light) Color(0xFF0F9D7A) else Color(0xFF63E6BE)
    AccentHue.PURPLE -> if (light) Color(0xFF6D28D9) else Color(0xFFA78BFA)
    AccentHue.PINK -> if (light) Color(0xFFDB2777) else Color(0xFFFF6FAE)
    AccentHue.RED -> if (light) Color(0xFFDC2626) else Color(0xFFFF5A5F)
    AccentHue.ORANGE -> if (light) Color(0xFFEA580C) else Color(0xFFFF9F43)
    AccentHue.GOLD -> if (light) Color(0xFFB45309) else Color(0xFFFFD43B)
    // Reads best on White and bright wallpapers; on dark themes it gives black buttons with white icons.
    AccentHue.BLACK -> Color.Black
}

/** The theme's backdrop when there is no wallpaper: flat, or with a glow fading in from the top. */
val LocalBackdrop = staticCompositionLocalOf<Brush?> { null }

/** Applies the user's theme and accent. [wallpaper] feeds the From wallpaper theme. */
@Composable
fun SunaTheme(appearance: Appearance?, wallpaper: ImageBitmap?, content: @Composable () -> Unit) {
    val palette = appearance?.palette ?: Palette.BLUE
    val tones by produceState<WallpaperTones?>(null, wallpaper) {
        value = wallpaper?.let { withContext(Dispatchers.Default) { it.tones() } }
    }
    val c = palette.colors(tones)
    val light = !c.dark

    var colors: ColorScheme = c.toScheme()
    appearance?.accent?.let { colors = colors.withAccent(it.on(light)) }
    val backdrop = c.glow?.let { Brush.verticalGradient(0f to it, 0.45f to c.background) }

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

    CompositionLocalProvider(
        LocalWallpaperTones provides tones,
        LocalIsLight provides light,
        LocalBackdrop provides backdrop,
    ) {
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
    val backdrop = LocalBackdrop.current
    Box(
        Modifier
            .fillMaxSize()
            .background(base)
            .then(if (wallpaper == null && backdrop != null) Modifier.background(backdrop) else Modifier),
    ) {
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
        labelSmall = SmallLabel.copy(fontSize = 11.sp),
    )
}
