package com.suna.player.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.suna.player.playback.PlayerConnection
import com.suna.player.playback.PlayerUiState
import com.suna.player.playback.QueueEntry
import com.suna.player.ui.common.Artwork
import com.suna.player.ui.common.borderColor
import com.suna.player.ui.common.formatTime
import com.suna.player.ui.theme.LocalHasWallpaper
import com.suna.player.ui.theme.MonoLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val MiniHeight = 64.dp
private val MiniMargin = 8.dp

/** Space the collapsed player takes above the navigation bar: its height plus its margins. */
val MiniPlayerSpace = MiniHeight + MiniMargin * 2

/** How the sheet settles: critically damped, so it never bounces past the edge. */
private val SettleSpec = spring<Float>(dampingRatio = 1f, stiffness = 420f)

suspend fun Animatable<Float, AnimationVector1D>.expand() = animateTo(1f, SettleSpec)
suspend fun Animatable<Float, AnimationVector1D>.collapse() = animateTo(0f, SettleSpec)

/**
 * The player: a bar at the bottom that can be dragged up into the full screen player and
 * back down, following the finger. [expansion] runs from 0 (bar) to 1 (full screen).
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
    val navBottom = WindowInsets.navigationBars.getBottom(density)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullHeight = constraints.maxHeight.toFloat()
        val collapsedTop = with(density) {
            fullHeight - navBottom - (MiniHeight + MiniMargin).toPx()
        }
        val p = expansion.value
        val top = collapsedTop * (1f - p)

        val dragState = rememberDraggableState { delta ->
            scope.launch { expansion.snapTo((expansion.value - delta / collapsedTop).coerceIn(0f, 1f)) }
        }

        val miniColor = if (hasWallpaper) {
            colors.surfaceContainerHigh.copy(alpha = 0.86f)
        } else {
            colors.surfaceContainerHigh
        }
        val shape = RoundedCornerShape(lerp(14.dp, 0.dp, p))

        Box(
            Modifier
                .offset { IntOffset(0, top.roundToInt()) }
                .padding(horizontal = lerp(MiniMargin, 0.dp, p))
                .fillMaxWidth()
                .height(with(density) { fullHeight.toDp() })
                .clip(shape)
                .background(lerp(miniColor, colors.background, p))
                .border(1.dp, borderColor().copy(alpha = (1f - p) * borderColor().alpha), shape)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        val target = when {
                            velocity < -900f -> 1f
                            velocity > 900f -> 0f
                            expansion.value > 0.5f -> 1f
                            else -> 0f
                        }
                        expansion.animateTo(target, SettleSpec, initialVelocity = -velocity / collapsedTop)
                    },
                ),
        ) {
            if (p > 0.01f) {
                FullPlayer(
                    state = state,
                    current = current,
                    player = player,
                    modifier = Modifier.graphicsLayer {
                        alpha = ((p - 0.2f) / 0.8f).coerceIn(0f, 1f)
                    },
                    onCollapse = { scope.launch { expansion.collapse() } },
                    onOpenQueue = onOpenQueue,
                    onAddToPlaylist = onAddToPlaylist,
                )
            }
            if (p < 0.3f) {
                MiniBar(
                    state = state,
                    current = current,
                    player = player,
                    modifier = Modifier.graphicsLayer { alpha = (1f - p / 0.3f).coerceIn(0f, 1f) },
                    onOpen = { scope.launch { expansion.expand() } },
                )
            }
        }
    }
}

/** Reads the playback position twice a second, only while [active] and the app is on screen. */
@Composable
private fun rememberPosition(player: PlayerConnection, active: Boolean, key: Any?): Long {
    var position by remember { mutableLongStateOf(player.positionMs()) }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(active, key, lifecycle) {
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
            modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 4.dp),
        ) {
            Artwork(current.artUri, Modifier.size(44.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
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
            IconButton(onClick = player::previous) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous song")
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
                .padding(horizontal = 14.dp)
                .fillMaxWidth(fraction)
                .height(2.dp)
                .background(colors.primary, RoundedCornerShape(1.dp)),
        )
    }
}

@Composable
private fun FullPlayer(
    state: PlayerUiState,
    current: QueueEntry,
    player: PlayerConnection,
    modifier: Modifier,
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    onAddToPlaylist: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    var scrub by remember { mutableStateOf<Float?>(null) }
    val position = rememberPosition(player, active = true, key = current.mediaId)
    var shownPosition by remember(position) { mutableLongStateOf(position) }

    Box(modifier.fillMaxSize()) {
        // The artwork, heavily blurred, tints the whole screen.
        AsyncImage(
            model = current.artUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.45f }
                .blur(80.dp),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to colors.background.copy(alpha = 0.35f),
                    0.55f to colors.background.copy(alpha = 0.75f),
                    1f to colors.background,
                ),
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 22.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Minimize player",
                        modifier = Modifier.size(30.dp),
                    )
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("now playing", style = MonoLabel, color = colors.onSurfaceVariant)
                    Text(
                        "${state.currentIndex + 1} / ${state.queue.size}",
                        style = MonoLabel,
                        color = colors.primary,
                    )
                }
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Queue")
                }
            }

            Spacer(Modifier.weight(0.5f))
            // Grows while playing and settles back when paused.
            val artScale by animateFloatAsState(
                targetValue = if (state.isPlaying) 1f else 0.86f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
                label = "artScale",
            )
            Artwork(
                uri = current.artUri,
                corner = 18.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer {
                        scaleX = artScale
                        scaleY = artScale
                    },
            )
            Spacer(Modifier.weight(0.6f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        current.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        current.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
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

            val duration = state.durationMs
            val fraction = scrub
                ?: if (duration > 0) (shownPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
            Slider(
                value = fraction,
                onValueChange = { scrub = it },
                onValueChangeFinished = {
                    scrub?.let {
                        val target = (it * duration).toLong()
                        player.seekTo(target)
                        shownPosition = target
                    }
                    scrub = null
                },
                enabled = duration > 0,
                colors = SliderDefaults.colors(inactiveTrackColor = colors.surfaceContainerHighest),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val shown = scrub?.let { (it * duration).toLong() } ?: shownPosition
                Text(formatTime(shown), style = MonoLabel, color = colors.onSurfaceVariant)
                Text(formatTime(duration), style = MonoLabel, color = colors.onSurfaceVariant)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 28.dp),
            ) {
                IconButton(onClick = player::shuffleUpcoming) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle what plays next")
                }
                IconButton(onClick = player::previous, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous song", modifier = Modifier.size(36.dp))
                }
                // A rounded square rather than a circle: part of SunaPlay's own look.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.primary)
                        .clickable(onClick = player::togglePlay),
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = colors.onPrimary,
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = player::next, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next song", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = player::cycleRepeat) {
                    val repeatOn = state.repeatMode != Player.REPEAT_MODE_OFF
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = when (state.repeatMode) {
                            Player.REPEAT_MODE_ONE -> "Repeating this song"
                            Player.REPEAT_MODE_ALL -> "Repeating the queue"
                            else -> "Repeat is off"
                        },
                        tint = if (repeatOn) colors.primary else LocalContentColor.current,
                    )
                }
            }
        }
    }
}

