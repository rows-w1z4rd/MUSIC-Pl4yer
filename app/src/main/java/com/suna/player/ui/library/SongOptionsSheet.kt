package com.suna.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
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
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    /** Set only when the sheet was opened from inside a playlist. */
    onRemoveFromPlaylist: (() -> Unit)?,
    onOpenAlbum: () -> Unit,
    onOpenArtist: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Artwork(albumArtUri(song.albumId), Modifier.size(52.dp)) },
                headlineContent = {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text("${song.artist} · ${song.album}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            fun then(action: () -> Unit): () -> Unit = {
                action()
                onDismiss()
            }
            if (isFavorite) {
                Option(Icons.Rounded.Favorite, "Remove from favorites", then(onToggleFavorite), MaterialTheme.colorScheme.primary)
            } else {
                Option(Icons.Rounded.FavoriteBorder, "Add to favorites", then(onToggleFavorite))
            }
            Option(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next", then(onPlayNext))
            Option(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue", then(onAddToQueue))
            Option(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist", then(onAddToPlaylist))
            if (onRemoveFromPlaylist != null) {
                Option(Icons.Rounded.RemoveCircleOutline, "Remove from this playlist", then(onRemoveFromPlaylist))
            }
            Option(Icons.Rounded.Album, "Go to album", then(onOpenAlbum))
            Option(Icons.Rounded.Person, "Go to artist", then(onOpenArtist))
            Option(Icons.Rounded.Share, "Share", then(onShare))
            Option(Icons.Rounded.Delete, "Delete from phone", then(onDelete), MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Option(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = Color.Unspecified) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(icon, contentDescription = null, tint = if (tint.isSpecified) tint else MaterialTheme.colorScheme.onSurfaceVariant)
        },
        headlineContent = { Text(label, color = if (tint.isSpecified) tint else Color.Unspecified) },
    )
}
