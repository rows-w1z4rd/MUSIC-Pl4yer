package com.suna.player.ui.library

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suna.player.data.Song
import com.suna.player.data.albumArtUri
import com.suna.player.ui.common.Artwork

/**
 * The menu behind the options button on every song: four big buttons for the everyday
 * actions, then the rest in grouped rows, with deleting kept apart at the bottom.
 */
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
    onShowDetails: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var searchOpen by remember { mutableStateOf(false) }
    fun then(action: () -> Unit): () -> Unit = {
        action()
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surfaceContainerLow) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            ) {
                Artwork(albumArtUri(song.albumId), Modifier.size(56.dp), corner = 10.dp)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(
                        song.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" · "),
                        fontSize = 14.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                // Liking keeps the sheet open, so the heart filling in is visible.
                Tile(
                    icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    label = if (isFavorite) "Liked" else "Like",
                    pop = isFavorite,
                    onClick = onToggleFavorite,
                    modifier = Modifier.weight(1f),
                )
                Tile(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next", onClick = then(onPlayNext), modifier = Modifier.weight(1f))
                Tile(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue", onClick = then(onAddToQueue), modifier = Modifier.weight(1f))
                Tile(Icons.AutoMirrored.Rounded.PlaylistAdd, "Playlist", onClick = then(onAddToPlaylist), modifier = Modifier.weight(1f))
            }

            Group {
                Option(Icons.Rounded.Album, "Go to album", then(onOpenAlbum))
                Divider()
                Option(Icons.Rounded.Person, "Go to artist", then(onOpenArtist))
                Divider()
                Option(Icons.Rounded.Info, "Track details", then(onShowDetails))
                Divider()
                Option(
                    Icons.Rounded.TravelExplore,
                    "Search online",
                    onClick = { searchOpen = !searchOpen },
                    trailing = if (searchOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                )
                AnimatedVisibility(
                    visible = searchOpen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        Option(Icons.Rounded.Search, "Google", then { searchOnline(context, song, youtube = false) }, indent = true)
                        Option(Icons.Rounded.SmartDisplay, "YouTube", then { searchOnline(context, song, youtube = true) }, indent = true)
                    }
                }
                Divider()
                Option(Icons.Rounded.Share, "Share", then(onShare))
                if (onRemoveFromPlaylist != null) {
                    Divider()
                    Option(Icons.Rounded.RemoveCircleOutline, "Remove from this playlist", then(onRemoveFromPlaylist))
                }
            }

            Group {
                Option(Icons.Rounded.Delete, "Delete from phone", then(onDelete), tint = colors.error)
            }
        }
    }
}

/**
 * Hands the song to Google or YouTube, which do the searching. The app itself never goes
 * online: Android opens the other app with the search already typed in.
 */
fun searchOnline(context: Context, song: Song, youtube: Boolean) {
    val query = listOfNotNull(song.title, song.artist.takeUnless { it == "Unknown artist" })
        .joinToString(" ")
    val web = if (youtube) {
        "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
    } else {
        "https://www.google.com/search?q=${Uri.encode(query)}"
    }
    val first = if (youtube) {
        Intent(Intent.ACTION_VIEW, Uri.parse(web))
    } else {
        Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
    }
    try {
        context.startActivity(first)
    } catch (_: ActivityNotFoundException) {
        // No search app answered: open the search page in whatever browser there is.
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web))) }
    }
}

@Composable
private fun Tile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pop: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val bounce by animateFloatAsState(
        targetValue = if (pop) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "tilePop",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    val s = 1f + 0.72f * bounce * (1f - bounce)
                    scaleX = s
                    scaleY = s
                },
        )
        Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)),
        content = content,
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 52.dp),
    )
}

@Composable
private fun Option(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified,
    trailing: ImageVector? = null,
    indent: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val textColor = if (tint == Color.Unspecified) colors.onSurface else tint
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(start = if (indent) 32.dp else 16.dp, end = 16.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (tint == Color.Unspecified) colors.onSurfaceVariant else tint,
            modifier = Modifier.size(22.dp),
        )
        Text(label, fontSize = 16.sp, color = textColor, modifier = Modifier.padding(start = 14.dp).weight(1f))
        if (trailing != null) {
            Icon(trailing, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.width(22.dp))
        }
    }
}
