package com.suna.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Keeps the user's wallpaper as a private copy and turns it into the picture shown behind the
 * app. The blur is computed once per setting on a small copy of the image, so showing it costs
 * the same as showing any still picture.
 */
class WallpaperStore(private val context: Context) {

    private val file = File(context.filesDir, "wallpaper.jpg")

    /** Copies the picked image into the app's storage. Returns false if it could not be read. */
    suspend fun import(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = decode(source, FULL_SIDE)
            val tmp = File(context.filesDir, "wallpaper.tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            file.delete()
            tmp.renameTo(file)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        Unit
    }

    /** The picture to draw behind the app for a blur setting between 0 and 1. */
    suspend fun render(blur: Float): ImageBitmap? = withContext(Dispatchers.Default) {
        if (!file.exists()) return@withContext null
        try {
            val source = ImageDecoder.createSource(file)
            val radius = (blur.coerceIn(0f, 1f) * MAX_RADIUS).roundToInt()
            val bitmap = if (radius <= 0) {
                decode(source, FULL_SIDE)
            } else {
                boxBlur(decode(source, BLUR_SIDE), radius)
            }
            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    private fun decode(source: ImageDecoder.Source, maxSide: Int): Bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val scale = min(1f, maxSide.toFloat() / max(w, h))
            decoder.setTargetSize(
                max(1, (w * scale).roundToInt()),
                max(1, (h * scale).roundToInt()),
            )
        }

    private companion object {
        const val FULL_SIDE = 1600
        const val BLUR_SIDE = 480
        const val MAX_RADIUS = 24
    }
}

/** Three box-blur passes, which is visually close to a gaussian blur. */
fun boxBlur(source: Bitmap, radius: Int): Bitmap {
    val w = source.width
    val h = source.height
    val a = IntArray(w * h)
    val b = IntArray(w * h)
    source.getPixels(a, 0, w, 0, 0, w, h)
    repeat(3) {
        for (y in 0 until h) blurLine(a, b, start = y * w, step = 1, n = w, radius = radius)
        for (x in 0 until w) blurLine(b, a, start = x, step = w, n = h, radius = radius)
    }
    return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888)
}

/** Averages one row or column with a sliding window; edges repeat the border pixel. */
internal fun blurLine(src: IntArray, dst: IntArray, start: Int, step: Int, n: Int, radius: Int) {
    val window = radius * 2 + 1
    var r = 0
    var g = 0
    var b = 0
    for (i in -radius..radius) {
        val p = src[start + i.coerceIn(0, n - 1) * step]
        r += (p shr 16) and 0xFF
        g += (p shr 8) and 0xFF
        b += p and 0xFF
    }
    for (i in 0 until n) {
        dst[start + i * step] =
            (0xFF shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
        val leaving = src[start + (i - radius).coerceIn(0, n - 1) * step]
        val entering = src[start + (i + radius + 1).coerceIn(0, n - 1) * step]
        r += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
        g += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
        b += (entering and 0xFF) - (leaving and 0xFF)
    }
}
