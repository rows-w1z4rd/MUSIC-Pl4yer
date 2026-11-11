package com.suna.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.suna.player.playback.PlayerUiState
import com.suna.player.ui.common.Artwork
import com.suna.player.ui.common.Panel
import com.suna.player.ui.common.ScreenTopBar
import com.suna.player.ui.common.songCount
import com.suna.player.ui.theme.SmallLabel

@Composable
fun QueueScreen(
    state: PlayerUiState,
    onBack: () -> Unit,
    onPlayIndex: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = state.currentIndex.coerceAtLeast(0),
    )
    val colors = MaterialTheme.colorScheme
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "Queue", subtitle = songCount(state.queue.size), onBack = onBack)
        Panel(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 6.dp, bottom = bottom + 8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(state.queue) { index, entry ->
                    val isCurrent = index == state.currentIndex
                    val upcomingPlayNext = entry.playNext && index > state.currentIndex
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isCurrent) colors.primaryContainer else Color.Transparent)
                            .clickable { onPlayIndex(index) }
                            .padding(start = 14.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                    ) {
                        Text(
                            "${index + 1}",
                            style = SmallLabel,
                            color = if (isCurrent) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                        Artwork(entry.artUri, Modifier.size(42.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(
                                entry.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isCurrent) colors.primary else colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (upcomingPlayNext) "${entry.artist} · Up next" else entry.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!isCurrent) {
                            IconButton(onClick = { onRemove(index) }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Remove ${entry.title} from queue",
                                    tint = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
