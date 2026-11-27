package com.suna.player.ui.common

import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.translate
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

/** A smooth closed shape through [pts] (a Catmull-Rom curve), on a grid scaled by [k]. */
private fun smooth(pts: List<Pair<Float, Float>>, k: Float): Path = Path().apply {
    val n = pts.size
    fun p(i: Int) = pts[(i + n) % n].let { Offset(it.first * k, it.second * k) }
    moveTo(p(0).x, p(0).y)
    for (i in 0 until n) {
        val p0 = p(i - 1); val p1 = p(i); val p2 = p(i + 1); val p3 = p(i + 2)
        val c1 = p1 + (p2 - p0) / 6f
        val c2 = p2 - (p3 - p1) / 6f
        cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    close()
}

private val GuitarBody = listOf(
    30f to 104f, 25f to 100f, 22f to 90f, 16f to 90f, 11f to 99f, 10f to 112f, 13f to 124f, 9f to 136f, 10f to 150f,
    19f to 159f, 33f to 161f, 48f to 158f, 55f to 149f, 55f to 137f, 51f to 126f, 54f to 114f, 53f to 101f, 48f to 94f,
    42f to 99f, 35f to 104f,
)
private val GuitarGuard = listOf(
    30f to 103f, 26f to 103f, 22f to 107f, 19f to 115f, 21f to 124f, 17f to 134f, 19f to 145f, 27f to 150f, 34f to 148f,
    40f to 152f, 48f to 147f, 49f to 136f, 45f to 126f, 48f to 114f, 45f to 105f, 39f to 104f, 35f to 103f,
)
private val GuitarHead = listOf(
    29.6f to 30f, 29f to 18f, 30.5f to 8f, 34f to 2f, 40f to 0f, 45f to 3f, 45f to 8f, 41f to 11f, 37f to 15f,
    35.6f to 22f, 35.4f to 30f,
)

/**
 * The jump-back button: a detailed electric guitar (double-cut body, pickguard, three pickups,
 * knobs, frets and tuners), all in shades of the theme's accent so it follows the wallpaper's
 * colours, with a glossy body and a soft shadow.
 */
@Composable
fun RockGuitar(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // Drawn upright on a 64 x 164 grid, then tilted so the headstock points up and right.
        val k = size.minDimension / 150f
        val ink = Color(0xFF141210)
        val dark = lerp(color, ink, 0.72f)
        val gloss = lerp(color, ink, 0.4f)
        val guard = lerp(color, Color(0xFFFAF8F4), 0.82f)
        val metal = lerp(color, Color(0xFFEBEBEE), 0.7f)
        val wood = lerp(color, Color(0xFF5A3C1E), 0.35f)
        val board = lerp(color, Color(0xFF28190F), 0.6f)
        val body = smooth(GuitarBody, k)
        withTransform({
            rotate(38f, pivot = center)
            translate(center.x - 32f * k, center.y - 82f * k)
        }) {
            translate(1.2f * k, 2.4f * k) { drawPath(body, Color.Black.copy(alpha = 0.35f)) }
            drawPath(body, Brush.radialGradient(0f to gloss, 1f to dark, center = Offset(22f * k, 112f * k), radius = 42f * k))
            drawPath(smooth(GuitarGuard, k), guard)
            drawPath(smooth(GuitarHead, k), wood)
            drawRect(board, topLeft = Offset(29.6f * k, 28f * k), size = Size(5.8f * k, 78f * k))
            for (i in 1..14) {
                val f = (1f - Math.pow(0.88, i.toDouble()).toFloat()) / (1f - Math.pow(0.88, 15.0).toFloat()) * 0.95f
                val y = (28f + 78f * f) * k
                drawLine(metal.copy(alpha = 0.8f), Offset(29.6f * k, y), Offset(35.4f * k, y), strokeWidth = 0.35f * k)
            }
            listOf(6f, 11f, 16f, 21f, 26f, 31f).forEach { drawCircle(metal, 1.3f * k, Offset(27.7f * k, it * k)) }
            listOf(Triple(26f, 111f, 38f), Triple(26f, 120f, 38f), Triple(25.5f, 130f, 38.5f)).forEach { (x0, y0, x1) ->
                drawRoundRect(Color(0xFF232120), Offset(x0 * k, y0 * k), Size((x1 - x0) * k, 4.2f * k), CornerRadius(1.6f * k))
                repeat(6) { j ->
                    val px = x0 + 1.6f + j * (x1 - x0 - 3.2f) / 5f
                    drawCircle(metal, 0.45f * k, Offset(px * k, (y0 + 2.1f) * k))
                }
            }
            drawRoundRect(metal, Offset(24f * k, 141f * k), Size(16f * k, 4f * k), CornerRadius(0.8f * k))
            listOf(Offset(41f, 133f), Offset(44f, 139f), Offset(42f, 145f)).forEach { drawCircle(metal, 2.3f * k, it * k) }
            drawOval(metal, Offset(47.8f * k, 150.4f * k), Size(4.4f * k, 3.2f * k))
            repeat(6) { j ->
                val x = (30.6f + j * 0.95f) * k
                drawLine(Color.White.copy(alpha = 0.55f), Offset(x, 8f * k), Offset(x, 143f * k), strokeWidth = 0.2f * k)
            }
        }
    }
}

/**
 * A slider at the side of a long list: drag it to glide through the list. Faint while you
 * scroll, solid accent while held. Depending on the setting it shows as soon as you scroll, or
 * only after two seconds of scrolling. Also offers the guitar that brings the playing track
 * back into view after a while of scrolling, when it's somewhere else in the list.
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
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            val now = System.currentTimeMillis()
            if (scrolling && sessionStart == 0L) sessionStart = now
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

    // --- The guitar: back to the playing track ---
    val playingVisible by remember(playingIndex) {
        derivedStateOf { state.layoutInfo.visibleItemsInfo.any { it.index == playingIndex } }
    }
    var showGuitar by remember { mutableStateOf(false) }
    LaunchedEffect(elapsed, playingVisible, playingIndex) {
        if (playingIndex < 0 || playingVisible) {
            showGuitar = false
        } else if (elapsed >= 2000) {
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
    val guitarAlpha by animateFloatAsState(if (showGuitar) 1f else 0f, tween(250), label = "guitar")
    if (guitarAlpha > 0f) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = bottomInset + 10.dp)
                .size(60.dp)
                .alpha(guitarAlpha)
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    showGuitar = false
                    scope.launch { state.animateScrollToItem(playingIndex, -with(density) { 120.dp.roundToPx() }) }
                }
                .semantics { contentDescription = "Go to the playing track" },
        ) {
            RockGuitar(accent, Modifier.size(52.dp))
        }
    }
}
