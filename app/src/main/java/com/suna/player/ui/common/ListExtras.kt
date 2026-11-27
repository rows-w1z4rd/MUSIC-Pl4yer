package com.suna.player.ui.common

import com.suna.player.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.Image
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import com.suna.player.data.JumpColor
import androidx.compose.ui.semantics.onClick
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.suna.player.data.ListStyle
import com.suna.player.data.SliderMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What is playing, for marking it in lists: the track, whether it's playing, and where it came from. */
@Immutable
data class NowPlaying(val songId: Long? = null, val playing: Boolean = false, val source: String? = null)

val LocalNowPlaying = compositionLocalOf { NowPlaying() }
val LocalListStyle = compositionLocalOf { ListStyle.INSET }
val LocalSliderMode = compositionLocalOf { SliderMode.SCROLLING }
val LocalJumpColor = compositionLocalOf { JumpColor.THEME }

/**
 * Samsung Music's "now playing" mark: three little columns of dots that rise and fall while
 * the music plays, and rest when it's paused.
 */
@Composable
fun PlayingBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "playingBars")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Canvas(modifier.size(18.dp).semantics { contentDescription = if (playing) "Playing" else "Paused" }) {
        val dot = size.width / 9f
        val rest = floatArrayOf(2f, 3f, 1f)
        repeat(3) { col ->
            val n = if (playing) {
                val wave = kotlin.math.sin((phase + col * 0.33f) * 2f * Math.PI.toFloat())
                (2.5f + 1.5f * wave).roundToInt().coerceIn(1, 4)
            } else {
                rest[col].toInt()
            }
            val cx = dot * (1.5f + col * 3f)
            repeat(n) { row ->
                val cy = size.height - dot * (1f + row * 2.2f)
                drawCircle(color, radius = dot * 0.95f, center = Offset(cx, cy))
            }
        }
    }
}

/** The app icon's figure, cut out of the icon's empty margin, and how bright its colour gets. */
private class IconArt(val bitmap: ImageBitmap, val brightest: Int)

/** Loads the launcher icon's own art (ic_launcher_foreground) once, untouched, trimmed to his outline. */
@Composable
private fun rememberIconArt(): IconArt? {
    val res = LocalContext.current.resources
    return remember(res) {
        runCatching {
            val full = BitmapFactory.decodeResource(res, R.mipmap.ic_launcher_foreground) ?: return@runCatching null
            val w = full.width
            val h = full.height
            val px = IntArray(w * h)
            full.getPixels(px, 0, w, 0, 0, w, h)
            var left = w; var top = h; var right = -1; var bottom = -1; var brightest = 1
            for (y in 0 until h) for (x in 0 until w) {
                val c = px[y * w + x]
                if ((c ushr 24) > 8) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                    brightest = maxOf(brightest, (c shr 8) and 0xFF)
                }
            }
            if (right < 0) return@runCatching null
            IconArt(Bitmap.createBitmap(full, left, top, right - left + 1, bottom - top + 1).asImageBitmap(), brightest)
        }.getOrNull()
    }
}

/**
 * Recolours the icon art without touching its drawing: his light and shade (the icon's green
 * channel) carry over to [color], so the dark lines stay dark and the outline stays the same.
 */
private fun recolour(color: Color, brightest: Int): ColorFilter {
    val k = 255f / brightest
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0f, color.red * k, 0f, 0f, 0f,
                0f, color.green * k, 0f, 0f, 0f,
                0f, color.blue * k, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

/** The jump button's figure: the app icon's art as it is, in the colour picked in Settings. */
@Composable
fun IconFigure(color: JumpColor, modifier: Modifier = Modifier) {
    val art = rememberIconArt() ?: return
    val filter = when (color) {
        JumpColor.ORIGINAL -> null
        JumpColor.WHITE -> recolour(Color.White, art.brightest)
        JumpColor.THEME -> recolour(MaterialTheme.colorScheme.primary, art.brightest)
    }
    Image(art.bitmap, contentDescription = null, colorFilter = filter, contentScale = ContentScale.Fit, modifier = modifier)
}

/**
 * A slider at the side of a long list: drag it to glide through the list. Faint while you
 * scroll, solid accent while held. Depending on the setting it shows as soon as you scroll, or
 * only after two seconds of scrolling. Also offers the app icon's figure, who brings the playing
 * track back into view after a while of scrolling, when it's somewhere else in the list.
 */
@Composable
fun BoxScope.ListScrollExtras(
    state: LazyListState,
    /** Space kept free at the bottom (the mini player). */
    bottomInset: Dp,
    /** Where the playing track is in the list (counting any header), or -1. */
    playingIndex: Int,
) {
    val mode = LocalSliderMode.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary

    // A "scrolling session" lasts while scrolls follow each other within a second and a half.
    var sessionStart by remember { mutableLongStateOf(0L) }
    var lastStop by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    // True while the figure is taking you to the playing track: that scroll isn't yours, so it
    // doesn't count as scrolling (no slider, and he doesn't come back).
    var jumping by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            val now = System.currentTimeMillis()
            if (scrolling && sessionStart == 0L && !jumping) sessionStart = now
            if (!scrolling) lastStop = now
        }
    }
    LaunchedEffect(state) {
        while (true) {
            val now = System.currentTimeMillis()
            if (sessionStart != 0L) {
                elapsed = now - sessionStart
                if (!state.isScrollInProgress && !dragging && now - lastStop > 1500) {
                    sessionStart = 0L
                    elapsed = 0L
                }
            }
            delay(200)
        }
    }
    val active = sessionStart != 0L
    val long = state.layoutInfo.totalItemsCount > state.layoutInfo.visibleItemsInfo.size * 2

    // --- The slider ---
    val showSlider = long && (dragging || (active && (mode == SliderMode.SCROLLING || elapsed >= 2000)))
    val sliderAlpha by animateFloatAsState(if (showSlider) 1f else 0f, tween(if (showSlider) 150 else 400), label = "slider")
    var trackHeight by remember { mutableFloatStateOf(1f) }
    val thumbHeight = with(density) { 48.dp.toPx() }
    val progress by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo.size
            val first = info.visibleItemsInfo.firstOrNull()
            if (total <= visible || first == null) {
                0f
            } else {
                val partial = -first.offset.toFloat() / first.size.coerceAtLeast(1)
                ((first.index + partial) / (total - visible).coerceAtLeast(1)).coerceIn(0f, 1f)
            }
        }
    }
    if (sliderAlpha > 0f) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, bottom = bottomInset + 8.dp)
                .fillMaxHeight()
                .width(28.dp)
                .alpha(sliderAlpha)
                .onSizeChanged { trackHeight = it.height.toFloat() }
                .pointerInput(state) {
                    fun jump(y: Float) {
                        val total = state.layoutInfo.totalItemsCount
                        val visible = state.layoutInfo.visibleItemsInfo.size
                        val f = ((y - thumbHeight / 2) / (trackHeight - thumbHeight).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        scope.launch { state.scrollToItem((f * (total - visible).coerceAtLeast(0)).roundToInt()) }
                    }
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true; jump(it.y) },
                        onDragEnd = { dragging = false; lastStop = System.currentTimeMillis() },
                        onDragCancel = { dragging = false; lastStop = System.currentTimeMillis() },
                    ) { change, _ ->
                        change.consume()
                        jump(change.position.y)
                    }
                },
        ) {
            val width by animateFloatAsState(if (dragging) 6f else 4f, label = "thumbWidth")
            Canvas(Modifier.align(Alignment.TopEnd).padding(end = 4.dp).width(8.dp).fillMaxHeight()) {
                val w = width.dp.toPx()
                val top = progress * (size.height - thumbHeight)
                drawRoundRect(
                    color = if (dragging) accent else Color.White.copy(alpha = 0.45f),
                    topLeft = Offset(size.width - w, top),
                    size = Size(w, thumbHeight),
                    cornerRadius = CornerRadius(w / 2, w / 2),
                )
            }
        }
    }

    // --- The app icon's figure: back to the playing track ---
    val playingVisible by remember(playingIndex) {
        derivedStateOf { state.layoutInfo.visibleItemsInfo.any { it.index == playingIndex } }
    }
    var showGuitar by remember { mutableStateOf(false) }
    LaunchedEffect(elapsed, playingVisible, playingIndex) {
        if (playingIndex < 0 || playingVisible) {
            showGuitar = false
        } else if (elapsed >= 2000 && !jumping) {
            showGuitar = true
        }
    }
    // It goes away a few seconds after scrolling stops.
    LaunchedEffect(showGuitar, active) {
        if (showGuitar && !active) {
            delay(4000)
            showGuitar = false
        }
    }
    val target by rememberUpdatedState(playingIndex)
    val goToPlaying: () -> Unit = {
        // Only the first tap counts. He's gone at once, and any more taps while he fades land on
        // him and do nothing, rather than on the row underneath.
        if (showGuitar && !jumping) {
            showGuitar = false
            jumping = true
            sessionStart = 0L
            elapsed = 0L
            scope.launch {
                try {
                    // Where the playing track is right now, not when he appeared.
                    val to = target
                    if (to >= 0) {
                        val from = state.firstVisibleItemIndex
                        if (kotlin.math.abs(to - from) > 24) {
                            state.scrollToItem((if (to > from) to - 12 else to + 12).coerceAtLeast(0))
                        }
                        state.animateScrollToItem(to, -with(density) { 120.dp.roundToPx() })
                    }
                } finally {
                    delay(400)
                    jumping = false
                }
            }
        }
    }
    val figureAlpha by animateFloatAsState(if (showGuitar) 1f else 0f, tween(if (showGuitar) 250 else 150), label = "figure")
    if (figureAlpha > 0f || jumping) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = bottomInset + 6.dp)
                .size(66.dp)
                // A round hit area around him; nothing of it is drawn.
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val r = size.width / 2f
                        if ((pos - Offset(r, size.height / 2f)).getDistance() <= r) goToPlaying()
                    }
                }
                .semantics {
                    contentDescription = "Go to the playing track"
                    onClick { goToPlaying(); true }
                },
        ) {
            IconFigure(LocalJumpColor.current, Modifier.alpha(figureAlpha).size(60.dp))
        }
    }
}
