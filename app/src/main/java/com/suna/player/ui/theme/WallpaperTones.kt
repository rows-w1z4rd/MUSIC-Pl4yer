package com.suna.player.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The colors a theme takes from a wallpaper: its most striking hue, its overall cast, and how
 * bright it is (which decides light or dark in Automatic mode).
 */
data class WallpaperTones(
    /** The most colorful hue that covers a good part of the picture. */
    val vivid: Color,
    /** The picture's average color. */
    val cast: Color,
    /** 0 = black, 1 = white. */
    val brightness: Float,
    /** False for black-and-white or very washed-out pictures. */
    val hasColor: Boolean,
) {
    fun colors(light: Boolean): PaletteColors {
        val accent = if (hasColor) vivid.withLightness(if (light) 0.42f else 0.68f, minSaturation = 0.55f) else DefaultBlue(light)
        return if (light) {
            val base = lerp(Color.White, cast, 0.10f)
            PaletteColors(
                background = lerp(Color(0xFFF2F2F7), cast, 0.08f),
                panel = lerp(Color.White, cast, 0.04f),
                raised = lerp(base, Color.Black, 0.07f),
                border = lerp(base, Color.Black, 0.13f),
                text = Color(0xFF1C1C1E),
                subtext = Color(0xFF6E6E73),
                accent = accent,
                dark = false,
            )
        } else {
            PaletteColors(
                background = Color.Black,
                panel = lerp(Color.Black, cast, 0.10f),
                raised = lerp(Color(0xFF1A1A1C), cast, 0.16f),
                border = lerp(Color(0xFF242428), cast, 0.18f),
                text = Color(0xFFEDEDF0),
                subtext = Color(0xFF9A9AA2),
                accent = accent,
            )
        }
    }
}

/** Reads the picture's colors from a tiny copy of it; quick enough to run on each change. */
fun ImageBitmap.tones(): WallpaperTones {
    val source = asAndroidBitmap()
    val w = 48
    val h = max(1, (48f * source.height / source.width).roundToInt())
    val small = Bitmap.createScaledBitmap(source, w, h, true)
    val pixels = IntArray(w * h)
    small.getPixels(pixels, 0, w, 0, 0, w, h)

    // Hues are sorted into 24 buckets, each pixel counting more the more colorful it is.
    val bins = 24
    val weight = FloatArray(bins)
    val red = FloatArray(bins)
    val green = FloatArray(bins)
    val blue = FloatArray(bins)
    var sumR = 0f
    var sumG = 0f
    var sumB = 0f
    var sumLum = 0f
    var colorful = 0f
    val hsl = FloatArray(3)
    for (p in pixels) {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        sumR += r
        sumG += g
        sumB += b
        sumLum += (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
        ColorUtils.RGBToHSL(r, g, b, hsl)
        val strength = hsl[1] * (1f - abs(hsl[2] - 0.5f) * 2f)
        if (strength < 0.08f) continue
        val bin = ((hsl[0] / 360f) * bins).toInt().coerceIn(0, bins - 1)
        weight[bin] += strength
        red[bin] += r * strength
        green[bin] += g * strength
        blue[bin] += b * strength
        colorful += strength
    }
    val n = pixels.size.toFloat()
    val best = weight.indices.maxBy { weight[it] }
    val vivid = if (weight[best] > 0f) {
        Color(red[best] / weight[best] / 255f, green[best] / weight[best] / 255f, blue[best] / weight[best] / 255f)
    } else {
        Color.Gray
    }
    if (small != source) small.recycle()
    return WallpaperTones(
        vivid = vivid,
        cast = Color(sumR / n / 255f, sumG / n / 255f, sumB / n / 255f),
        brightness = sumLum / n,
        hasColor = colorful / n > 0.04f,
    )
}

/** The same hue at a set lightness, made at least a little saturated so it reads as a color. */
private fun Color.withLightness(lightness: Float, minSaturation: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.RGBToHSL((red * 255).roundToInt(), (green * 255).roundToInt(), (blue * 255).roundToInt(), hsl)
    hsl[1] = max(hsl[1], minSaturation)
    hsl[2] = lightness
    return Color(ColorUtils.HSLToColor(hsl))
}

/** The app's default accent: a clear blue, a touch deeper on light backgrounds. */
@Suppress("FunctionName")
fun DefaultBlue(light: Boolean) = if (light) Color(0xFF007AFF) else Color(0xFF0A84FF)
