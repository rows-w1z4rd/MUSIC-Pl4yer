package com.suna.player.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suna.player.data.Lyrics
import com.suna.player.playback.PlayerConnection
import kotlinx.coroutines.delay

/**
 * The lyrics, shown in place of the cover. Synced lyrics light up line by line and scroll with
 * the song, keeping the current line a little above the middle; tapping a line jumps there.
 * Scrolling by hand pauses the following for a few seconds. Lyrics without timings just scroll.
 */
@Composable
fun LyricsView(
    mediaId: String,
    loadLyrics: suspend (String) -> Lyrics?,
    player: PlayerConnection,
    playing: Boolean,
    bright: Color,
    dim: Color,
    sidePadding: Dp,
    modifier: Modifier = Modifier,
) {
    val result by produceState<Pair<String, Lyrics?>?>(null, mediaId) { value = mediaId to loadLyrics(mediaId) }
    val lyrics = result?.takeIf { it.first == mediaId }?.second

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            result?.first != mediaId -> CircularProgressIndicator(color = dim, strokeWidth = 2.dp)
            lyrics == null -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 40.dp)) {
                Text("No lyrics for this track", fontSize = 18.sp, color = bright, textAlign = TextAlign.Center)
                Text(
                    "Add them in Edit track info, or choose a lyrics folder with .lrc files in Settings.",
                    fontSize = 14.sp,
                    color = dim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            else -> Lines(lyrics, player, playing, bright, dim, sidePadding)
        }
    }
}

@Composable
private fun Lines(lyrics: Lyrics, player: PlayerConnection, playing: Boolean, bright: Color, dim: Color, sidePadding: Dp) {
    // A finer clock than the seek bar's, so lines change right on the beat.
    var position by remember { mutableLongStateOf(player.positionMs()) }
    LaunchedEffect(playing, lyrics) {
        position = player.positionMs()
        while (playing && lyrics.synced) {
            position = player.positionMs()
            delay(120)
        }
    }
    val current = lyrics.lineAt(position)
    val list = rememberLazyListState()
    var lastTouch by remember { mutableLongStateOf(0L) }
    var userScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !userScrolling) userScrolling = true
            if (!scrolling && userScrolling) {
                userScrolling = false
                lastTouch = System.currentTimeMillis()
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = constraints.maxHeight
        val density = LocalDensity.current
        val lead = with(density) { (height * 0.38f).toDp() }
        LaunchedEffect(current) {
            if (!lyrics.synced || current < 0) return@LaunchedEffect
            if (System.currentTimeMillis() - lastTouch < 3000) return@LaunchedEffect
            list.animateScrollToItem(current, scrollOffset = -(height * 0.30f).toInt())
        }
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(top = lead, bottom = lead),
            modifier = Modifier
                .fillMaxSize()
                // Lines fade out towards the top and bottom edges.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.14f to Color.Black,
                            0.86f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                val now = !lyrics.synced || index == current
                val color by animateColorAsState(if (now) bright else dim.copy(alpha = 0.55f), tween(250), label = "lyricLine")
                val time = line.timeMs
                Text(
                    line.text.ifBlank { "♪" },
                    fontSize = if (lyrics.synced) 24.sp else 20.sp,
                    lineHeight = if (lyrics.synced) 32.sp else 28.sp,
                    fontWeight = if (lyrics.synced) FontWeight.Bold else FontWeight.Medium,
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (time != null) {
                                Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {
                                    player.seekTo(time)
                                    position = time
                                    lastTouch = 0L
                                }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = sidePadding, vertical = if (lyrics.synced) 10.dp else 4.dp),
                )
            }
        }
    }
}
