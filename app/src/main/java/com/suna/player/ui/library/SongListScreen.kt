package com.suna.player.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.suna.player.data.Song
import com.suna.player.ui.common.EmptyMessage
import com.suna.player.ui.common.LocalMiniPlayerSpace
import com.suna.player.ui.common.Panel
import com.suna.player.ui.common.ScreenTopBar
import com.suna.player.ui.common.SongList
import com.suna.player.ui.common.songCount

/** A titled list of songs: used for one album, artist, folder or playlist. */
@Composable
fun SongListScreen(
    title: String,
    songs: List<Song>,
    onBack: () -> Unit,
    onPlay: (index: Int) -> Unit,
    onShuffle: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onSongOptions: (Song) -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val bottom = LocalMiniPlayerSpace.current +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = title, subtitle = songCount(songs.size), onBack = onBack, actions = actions)
        Panel(Modifier.weight(1f).padding(bottom = 0.dp)) {
            if (songs.isEmpty()) {
                EmptyMessage("No songs")
            } else {
                SongList(
                    songs = songs,
                    onPlay = onPlay,
                    onPlayNext = onPlayNext,
                    onOptions = onSongOptions,
                    contentPadding = PaddingValues(top = 6.dp, bottom = bottom + 8.dp),
                    header = {
                        ListToolbar(
                            label = null,
                            count = songs.size,
                            onLabel = null,
                            onShuffle = onShuffle,
                            onPlayAll = { onPlay(0) },
                        )
                    },
                )
            }
        }
    }
}
