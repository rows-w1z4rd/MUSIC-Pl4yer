package com.suna.player.ui.common

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

/**
 * The jump-back button: a pointed rock guitar with a lightning bolt cut through its body, in the
 * theme's accent (so it follows the wallpaper's colours) with a soft shine, over a faint glow
 * that keeps it readable on top of the list.
 */
@Composable
fun RockGuitar(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // Drawn on a 100 x 100 square, headstock to the top right.
        val u = size.minDimension / 100f
        fun Path.at(x: Float, y: Float) = moveTo(x * u, y * u)
        fun Path.to(x: Float, y: Float) = lineTo(x * u, y * u)
        fun Path.curve(cx: Float, cy: Float, x: Float, y: Float) = quadraticTo(cx * u, cy * u, x * u, y * u)
        val body = Path().apply {
            at(52f, 44f)
            curve(46f, 40f, 42f, 30f)   // up to the top horn
            curve(36f, 46f, 30f, 50f)
            curve(16f, 58f, 4f, 70f)    // the long wing
            curve(22f, 72f, 28f, 76f)
            curve(30f, 88f, 36f, 98f)   // the bottom point
            curve(44f, 82f, 50f, 72f)
            curve(60f, 66f, 70f, 62f)   // the right point
            curve(60f, 56f, 58f, 50f)
            close()
        }
        val neck = Path().apply { at(53f, 45f); to(82f, 14f); to(85f, 17f); to(56f, 48f); close() }
        val head = Path().apply {
            at(80f, 16f); to(82f, 8f); curve(86f, 4f, 92f, 2f); to(96f, 6f); curve(92f, 10f, 88f, 14f); to(86f, 19f); close()
        }
        val bolt = Path().apply {
            at(22f, 66f); to(40f, 58f); to(34f, 66f); to(46f, 64f); to(28f, 80f); to(32f, 70f); to(20f, 72f); close()
        }
        val guitar = Path.combine(
            PathOperation.Difference,
            Path.combine(PathOperation.Union, Path.combine(PathOperation.Union, body, neck), head),
            bolt,
        )
        drawCircle(
            Brush.radialGradient(
                0f to Color.Black.copy(alpha = 0.45f),
                1f to Color.Transparent,
                center = center,
                radius = size.minDimension * 0.62f,
            ),
            radius = size.minDimension * 0.62f,
        )
        translate(u * 1.2f, u * 1.8f) { drawPath(guitar, Color.Black.copy(alpha = 0.4f)) }
        drawPath(
            guitar,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.5f).compositeOver(color),
                0.5f to color,
                1f to Color.Black.copy(alpha = 0.3f).compositeOver(color),
                start = Offset(size.width, 0f),
                end = Offset(0f, size.height),
            ),
        )
    }
}

/**
 * A slider at the side of a long list: drag it to glide through the list. Faint while you
 * scroll, solid accent while held. Depending on the setting it shows as soon as you scroll, or
 * only after five seconds of scrolling. Also offers the guitar that brings the playing track
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
    val showSlider = long && (dragging || (active && (mode == SliderMode.SCROLLING || elapsed >= 5000)))
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
        } else if (elapsed >= 5000) {
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
                .size(52.dp)
                .alpha(guitarAlpha)
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    showGuitar = false
                    scope.launch { state.animateScrollToItem(playingIndex, -with(density) { 120.dp.roundToPx() }) }
                }
                .semantics { contentDescription = "Go to the playing track" },
        ) {
            RockGuitar(accent, Modifier.size(40.dp))
        }
    }
}
