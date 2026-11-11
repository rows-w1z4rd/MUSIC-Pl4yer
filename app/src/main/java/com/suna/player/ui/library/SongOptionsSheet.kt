package com.suna.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.suna.player.data.Song
import com.suna.player.data.albumArtUri
import com.suna.player.ui.common.Artwork

/** The menu behind the options button on every song. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsSheet(
    song: Song,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    /** Set only when the sheet was opened from inside a playlist. */
    onRemoveFromPlaylist: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Artwork(albumArtUri(song.albumId), Modifier.size(48.dp)) },
                headlineContent = {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Option(Icons.AutoMirrored.Filled.PlaylistPlay, "Play next") {
                onPlayNext()
                onDismiss()
            }
            Option(Icons.AutoMirrored.Filled.QueueMusic, "Add to queue") {
                onAddToQueue()
                onDismiss()
            }
            Option(Icons.Filled.Add, "Add to playlist") {
                onAddToPlaylist()
                onDismiss()
            }
            if (onRemoveFromPlaylist != null) {
                Option(Icons.Filled.Close, "Remove from this playlist") {
                    onRemoveFromPlaylist()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun Option(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(label) },
    )
}
