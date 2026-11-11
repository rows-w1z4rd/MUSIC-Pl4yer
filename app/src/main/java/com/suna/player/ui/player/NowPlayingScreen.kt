package com.suna.player.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import com.suna.player.playback.PlayerConnection
import com.suna.player.playback.PlayerUiState
import com.suna.player.ui.common.Artwork
import com.suna.player.ui.common.formatTime
import com.suna.player.ui.theme.LocalHasWallpaper
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    player: PlayerConnection,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    /** Null when the current song is no longer in the library. */
    onAddToPlaylist: (() -> Unit)?,
) {
    val current = state.current

    // The position is read only while this screen is on screen, never in the background.
    var positionMs by remember { mutableLongStateOf(player.positionMs()) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                positionMs = player.positionMs()
                delay(500)
            }
        }
    }

    // A faint glow of the accent color rising from the bottom, on plain backgrounds only.
    val glow = if (LocalHasWallpaper.current) {
        Modifier
    } else {
        Modifier.background(
            Brush.verticalGradient(
                0.35f to Color.Transparent,
                1f to MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            ),
        )
    }

    Box(Modifier.fillMaxSize().then(glow)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Close player",
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Spacer(Modifier.weight(0.6f))
            // Like Samsung's player: the artwork grows while playing and settles back when paused.
            val artScale by animateFloatAsState(
                targetValue = if (state.isPlaying) 1f else 0.82f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
                label = "artScale",
            )
            Artwork(
                uri = current?.artUri,
                corner = 40.dp,
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .aspectRatio(1f)
                    .graphicsLayer {
                        scaleX = artScale
                        scaleY = artScale
                    },
            )
            Text(
                current?.title ?: "Nothing playing",
                style = MaterialTheme.typography.headlineSmall,
                letterSpacing = (-0.3).sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            )
            Text(
                current?.artist ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
                }
                if (onAddToPlaylist != null) {
                    IconButton(onClick = onAddToPlaylist) {
                        Icon(Icons.Filled.Add, contentDescription = "Add to playlist")
                    }
                }
            }

            val duration = state.durationMs
            val fraction = scrub
                ?: if (duration > 0) (positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
            Slider(
                value = fraction,
                onValueChange = { scrub = it },
                onValueChangeFinished = {
                    scrub?.let {
                        val target = (it * duration).toLong()
                        player.seekTo(target)
                        positionMs = target
                    }
                    scrub = null
                },
                enabled = duration > 0,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            ) {
                val shown = scrub?.let { (it * duration).toLong() } ?: positionMs
                Text(formatTime(shown), style = MaterialTheme.typography.labelMedium)
                Text(formatTime(duration), style = MaterialTheme.typography.labelMedium)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 36.dp),
            ) {
                IconButton(onClick = player::shuffleUpcoming) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle what plays next")
                }
                IconButton(onClick = player::previous, modifier = Modifier.size(56.dp)) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Previous song",
                        modifier = Modifier.size(36.dp),
                    )
                }
                FilledIconButton(onClick = player::togglePlay, modifier = Modifier.size(76.dp)) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = player::next, modifier = Modifier.size(56.dp)) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Next song",
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = player::cycleRepeat) {
                    val repeatOn = state.repeatMode != Player.REPEAT_MODE_OFF
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                            Icons.Filled.RepeatOne
                        } else {
                            Icons.Filled.Repeat
                        },
                        contentDescription = when (state.repeatMode) {
                            Player.REPEAT_MODE_ONE -> "Repeating this song"
                            Player.REPEAT_MODE_ALL -> "Repeating the queue"
                            else -> "Repeat is off"
                        },
                        tint = if (repeatOn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            LocalContentColor.current
                        },
                    )
                }
            }
        }
    }
}
