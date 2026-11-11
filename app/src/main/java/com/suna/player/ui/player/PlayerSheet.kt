package com.suna.player.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.suna.player.playback.PlayerConnection
import com.suna.player.playback.PlayerUiState
import com.suna.player.playback.QueueEntry
import com.suna.player.ui.common.Artwork
import com.suna.player.ui.common.borderColor
import com.suna.player.ui.common.formatTime
import com.suna.player.ui.theme.LocalHasWallpaper
import com.suna.player.ui.theme.SmallLabel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

private val MiniHeight = 64.dp
private val MiniMargin = 8.dp
private val MiniCorner = 16.dp
private val MiniArt = 44.dp
private val MiniArtStart = 10.dp

/** Space the collapsed player takes above the navigation bar: its height plus its margins. */
val MiniPlayerSpace = MiniHeight + MiniMargin * 2

private val TopBarHeight = 56.dp
private val ArtSidePadding = 28.dp

/** What the title, seek bar and buttons need under the artwork in the full player. */
private val ControlsHeight = 290.dp

/** A drag released faster than this flings the player open or shut. */
private val FlingSpeed = 400.dp

/** No bounce: the player glides into place and stops dead, like a well-damped drawer. */
private val SettleSpec = spring<Float>(dampingRatio = 1f, stiffness = 380f)

// The full player is always black, whatever the theme: it's the OLED "stage" for the artwork.
private val Stage = Color.Black
private val TextBright = Color(0xFFF5F5F7)
private val TextDim = Color(0xFF9A9AA0)

suspend fun Animatable<Float, AnimationVector1D>.expand() = animateTo(1f, SettleSpec)
suspend fun Animatable<Float, AnimationVector1D>.collapse() = animateTo(0f, SettleSpec)

/**
 * The player: a bar at the bottom that follows the finger up into the full screen player and
 * back down. [expansion] runs from 0 (bar) to 1 (full screen). The artwork travels from the
 * bar's thumbnail to its place in the full player, so the two read as one object.
 *
 * Everything that moves with the drag is read in layout or drawing, never in composition, so
 * dragging redraws the sheet instead of rebuilding it.
 */
@Composable
fun PlayerSheet(
    state: PlayerUiState,
    player: PlayerConnection,
    expansion: Animatable<Float, AnimationVector1D>,
    onOpenQueue: () -> Unit,
    onAddToPlaylist: (() -> Unit)?,
) {
    val current = state.current ?: return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val hasWallpaper = LocalHasWallpaper.current
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()

    val isOpen by remember { derivedStateOf { expansion.value > 0.5f } }
    val showMini by remember { derivedStateOf { expansion.value < 0.3f } }
    val showFull by remember { derivedStateOf { expansion.value > 0.001f } }
    DarkSystemBarsWhile(isOpen)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val px = with(density) { { dp: Dp -> dp.toPx() } }
        val collapsedTop = height - navBottom - px(MiniHeight + MiniMargin)

        // Where the artwork sits in the full player, and in the bar.
        val artSize = min(
            width - 2 * px(ArtSidePadding),
            height - statusTop - navBottom - px(TopBarHeight + 8.dp + ControlsHeight),
        ).coerceAtLeast(px(120.dp))
        val artLeft = (width - artSize) / 2
        val artTop = statusTop + px(TopBarHeight + 8.dp)
        val miniArtLeft = px(MiniMargin + MiniArtStart)
        val miniArtTop = px((MiniHeight - MiniArt) / 2)
        val miniScale = px(MiniArt) / artSize

        val dragStartedAt = remember { FloatArray(1) }
        val dragState = rememberDraggableState { delta ->
            // Started undispatched so each step lands at once, in order, and can never run
            // after (and cancel) the settle animation that starts when the finger lifts.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                expansion.snapTo((expansion.value - delta / collapsedTop).coerceIn(0f, 1f))
            }
        }
        val flingSpeed = px(FlingSpeed)

        val miniColor = if (hasWallpaper) colors.surfaceContainerHigh.copy(alpha = 0.92f) else colors.surfaceContainerHigh
        val outline = borderColor()

        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, (collapsedTop * (1f - expansion.value)).roundToInt()) }
                .graphicsLayer {
                    // A new outline each frame: the layer only redraws its clip when it changes.
                    shape = SheetShape(
                        progress = expansion.value,
                        inset = px(MiniMargin),
                        corner = px(MiniCorner),
                        miniHeight = px(MiniHeight),
                        fullHeight = height,
                    )
                    clip = true
                }
                .drawBehind { drawRect(lerp(miniColor, Stage, expansion.value)) }
                .drawWithContent {
                    drawContent()
                    val p = expansion.value
                    if (p < 0.2f) {
                        val inset = px(MiniMargin)
                        drawRoundRect(
                            color = outline.copy(alpha = outline.alpha * (1f - p / 0.2f)),
                            topLeft = Offset(inset, 0f),
                            size = Size(size.width - 2 * inset, px(MiniHeight)),
                            cornerRadius = CornerRadius(px(MiniCorner)),
                            style = Stroke(px(1.dp)),
                        )
                    }
                }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = { dragStartedAt[0] = expansion.value },
                    onDragStopped = { velocity ->
                        val from = dragStartedAt[0]
                        val target = when {
                            velocity < -flingSpeed -> 1f
                            velocity > flingSpeed -> 0f
                            // A short pull is enough to open, and a short pull to close.
                            from < 0.5f -> if (expansion.value > 0.18f) 1f else 0f
                            else -> if (expansion.value < 0.82f) 0f else 1f
                        }
                        expansion.animateTo(target, SettleSpec, initialVelocity = -velocity / collapsedTop)
                    },
                ),
        ) {
            if (showFull) {
                StageBackground(current, Modifier.graphicsLayer { alpha = expansion.value })
                FullPlayer(
                    state = state,
                    current = current,
                    player = player,
                    artSize = with(density) { artSize.toDp() },
                    sidePadding = with(density) { artLeft.toDp() },
                    modifier = Modifier.graphicsLayer {
                        val p = expansion.value
                        alpha = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
                        translationY = (1f - p) * px(48.dp)
                    },
                    onCollapse = { scope.launch { expansion.collapse() } },
                    onOpenQueue = onOpenQueue,
                    onAddToPlaylist = onAddToPlaylist,
                )
            }
            if (showMini) {
                MiniBar(
                    state = state,
                    current = current,
                    player = player,
                    modifier = Modifier.graphicsLayer {
                        alpha = (1f - expansion.value / 0.22f).coerceIn(0f, 1f)
                    },
                    onOpen = { scope.launch { expansion.expand() } },
                )
            }

            // The one artwork, flying between the bar and the full player.
            val pausedShrink by animateFloatAsState(
                targetValue = if (state.isPlaying) 1f else 0.9f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                label = "pausedShrink",
            )
            Box(
                Modifier
                    .size(with(density) { artSize.toDp() })
                    .graphicsLayer {
                        val p = expansion.value
                        val travel = lerp(miniScale, 1f, p)
                        val scale = travel * lerp(1f, pausedShrink, p)
                        val slot = artSize * travel
                        val shown = artSize * scale
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = lerp(miniArtLeft, artLeft, p) + (slot - shown) / 2
                        translationY = lerp(miniArtTop, artTop, p) + (slot - shown) / 2
                        // The corner is set before scaling, so divide to keep it the size we want.
                        shape = RoundedCornerShape(lerp(px(8.dp), px(18.dp), p) / scale)
                        clip = true
                        shadowElevation = px(20.dp) * p
                    },
            ) {
                Artwork(current.artUri, Modifier.fillMaxSize(), corner = 0.dp)
            }
        }
    }
}

/** The sheet's visible area: the floating bar when collapsed, the whole screen when open. */
private data class SheetShape(
    val progress: Float,
    val inset: Float,
    val corner: Float,
    val miniHeight: Float,
    val fullHeight: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = progress
        val side = inset * (1f - p)
        val radius = corner * (1f - p)
        return Outline.Rounded(
            RoundRect(
                left = side,
                top = 0f,
                right = size.width - side,
                bottom = lerp(miniHeight, fullHeight, p),
                cornerRadius = CornerRadius(radius),
            ),
        )
    }
}

/** Status and navigation bar icons turn light over the black player on light themes. */
@Composable
private fun DarkSystemBarsWhile(open: Boolean) {
    val view = LocalView.current
    val lightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    DisposableEffect(open, lightTheme) {
        val window = view.context.findActivity()?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        if (bars != null && lightTheme) {
            bars.isAppearanceLightStatusBars = !open
            bars.isAppearanceLightNavigationBars = !open
        }
        onDispose {
            if (bars != null && lightTheme) {
                bars.isAppearanceLightStatusBars = true
                bars.isAppearanceLightNavigationBars = true
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Reads the playback position twice a second, only while [active] and the app is on screen. */
@Composable
private fun rememberPosition(player: PlayerConnection, active: Boolean, key: Any?): Long {
    var position by remember { mutableLongStateOf(player.positionMs()) }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(active, key, lifecycle) {
        position = player.positionMs()
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                position = player.positionMs()
                delay(500)
            }
        }
    }
    return position
}

@Composable
private fun MiniBar(
    state: PlayerUiState,
    current: QueueEntry,
    player: PlayerConnection,
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val position = rememberPosition(player, active = state.isPlaying, key = current.mediaId)
    val fraction = if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    Box(
        modifier
            .padding(horizontal = MiniMargin)
            .fillMaxWidth()
            .height(MiniHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(start = MiniArtStart + MiniArt + 12.dp, end = 4.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    current.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    current.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = player::togglePlay) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(30.dp),
                )
            }
            IconButton(onClick = player::next) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next song")
            }
        }
        // A thin progress line along the bottom edge.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = MiniCorner)
                .fillMaxWidth(fraction)
                .height(2.dp)
                .background(colors.primary, RoundedCornerShape(1.dp)),
        )
    }
}

/** The artwork, tiny and blurred, glowing behind the top of the full player. */
@Composable
private fun StageBackground(current: QueueEntry, modifier: Modifier) {
    val context = LocalContext.current
    // A tiny copy of the artwork is enough for a blur, and much cheaper to draw every frame.
    val tiny = remember(current.artUri) {
        ImageRequest.Builder(context).data(current.artUri).size(64).build()
    }
    Box(modifier.fillMaxSize()) {
        if (current.artUri != null) {
            AsyncImage(
                model = tiny,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .graphicsLayer { alpha = 0.6f }
                    .blur(60.dp),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Stage.copy(alpha = 0.25f),
                    0.45f to Stage.copy(alpha = 0.7f),
                    0.75f to Stage,
                ),
            ),
        )
    }
}

@Composable
private fun FullPlayer(
    state: PlayerUiState,
    current: QueueEntry,
    player: PlayerConnection,
    artSize: Dp,
    sidePadding: Dp,
    modifier: Modifier,
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    onAddToPlaylist: (() -> Unit)?,
) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    val position = rememberPosition(player, active = true, key = current.mediaId)
    var shownPosition by remember(position) { mutableLongStateOf(position) }
    val duration = state.durationMs

    CompositionLocalProvider(LocalContentColor provides TextBright) {
        Column(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 8.dp),
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Minimize player",
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Queue")
                }
            }

            // The artwork itself is drawn by the sheet, flying in from the bar.
            Spacer(Modifier.height(8.dp + artSize))

            Column(
                verticalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = sidePadding),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            current.title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            current.artist,
                            fontSize = 16.sp,
                            color = TextDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (onAddToPlaylist != null) {
                        IconButton(onClick = onAddToPlaylist) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Add to playlist")
                        }
                    }
                }

                Column {
                    val fraction = scrub
                        ?: if (duration > 0) (shownPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
                    SeekBar(
                        fraction = fraction,
                        enabled = duration > 0,
                        onScrub = { scrub = it },
                        onScrubEnd = {
                            scrub?.let {
                                val target = (it * duration).toLong()
                                player.seekTo(target)
                                shownPosition = target
                            }
                            scrub = null
                        },
                    )
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        val shown = scrub?.let { (it * duration).toLong() } ?: shownPosition
                        Text(formatTime(shown), style = SmallLabel, color = TextDim)
                        Text(formatTime(duration), style = SmallLabel, color = TextDim)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(onClick = player::shuffleUpcoming) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle up next", tint = TextDim)
                    }
                    IconButton(onClick = player::previous, modifier = Modifier.size(60.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous song", modifier = Modifier.size(40.dp))
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true),
                                onClick = player::togglePlay,
                            ),
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(64.dp),
                        )
                    }
                    IconButton(onClick = player::next, modifier = Modifier.size(60.dp)) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next song", modifier = Modifier.size(40.dp))
                    }
                    IconButton(onClick = player::cycleRepeat) {
                        Icon(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = when (state.repeatMode) {
                                Player.REPEAT_MODE_ONE -> "Repeating this song"
                                Player.REPEAT_MODE_ALL -> "Repeating the queue"
                                else -> "Repeat is off"
                            },
                            tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else TextDim,
                        )
                    }
                }
            }
        }
    }
}

/** A thin line with a dot, which thickens while held. Tap or drag anywhere along it. */
@Composable
private fun SeekBar(
    fraction: Float,
    enabled: Boolean,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
) {
    var held by remember { mutableStateOf(false) }
    val thickness by animateDpAsState(if (held) 6.dp else 3.dp, label = "seekThickness")
    val dot by animateDpAsState(if (held) 8.dp else 5.dp, label = "seekDot")

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onTap = {
                    onScrub((it.x / size.width).coerceIn(0f, 1f))
                    onScrubEnd()
                })
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        held = true
                        onScrub((it.x / size.width).coerceIn(0f, 1f))
                    },
                    onDragEnd = {
                        held = false
                        onScrubEnd()
                    },
                    onDragCancel = {
                        held = false
                        onScrubEnd()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onScrub((change.position.x / size.width).coerceIn(0f, 1f))
                    },
                )
            },
    ) {
        val h = thickness.toPx()
        val y = size.height / 2
        val radius = CornerRadius(h / 2)
        drawRoundRect(TextBright.copy(alpha = 0.22f), Offset(0f, y - h / 2), Size(size.width, h), radius)
        drawRoundRect(TextBright, Offset(0f, y - h / 2), Size(size.width * fraction, h), radius)
        drawCircle(TextBright, radius = dot.toPx(), center = Offset(size.width * fraction, y))
    }
}
