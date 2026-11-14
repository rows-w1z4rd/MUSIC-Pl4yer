package com.suna.player.ui.library

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suna.player.data.Playlist
import com.suna.player.data.Song
import com.suna.player.data.SongPlays
import com.suna.player.data.albumArtUri
import com.suna.player.playback.PlayerConnection
import com.suna.player.ui.common.Artwork
import com.suna.player.ui.common.HintIconButton
import com.suna.player.ui.common.Panel
import com.suna.player.ui.common.LocalMiniPlayerSpace
import com.suna.player.ui.common.SongList
import com.suna.player.ui.common.SongRow
import com.suna.player.ui.common.cardColor
import com.suna.player.ui.common.songCount
import com.suna.player.ui.theme.SmallLabel

/** The picture for a playlist: its own cover if one was chosen, else its first track's art. */
fun playlistCover(playlist: Playlist, songsById: Map<Long, Song>): Uri? =
    playlist.cover ?: playlist.songIds.firstNotNullOfOrNull { songsById[it] }?.let { albumArtUri(it.albumId) }

/**
 * One playlist, laid out like Samsung Music: the cover, name and track count on top, then a
 * rounded sheet with the tracks. Heart, add and a menu (edit, rename, cover, delete) up top.
 */
@Composable
fun PlaylistScreen(
    playlist: Playlist,
    songs: List<Song>,
    cover: Uri?,
    onBack: () -> Unit,
    onPlay: (index: Int) -> Unit,
    onShuffle: () -> Unit,
    onPlayNext: (Song) -> Unit,
    onSongOptions: (Song) -> Unit,
    onToggleFavorite: () -> Unit,
    onAddTracks: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onChangeCover: () -> Unit,
    onRemoveCover: (() -> Unit)?,
    onDelete: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val bottom = LocalMiniPlayerSpace.current +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    var headerHeight by remember { mutableIntStateOf(0) }
    val sheet = cardColor()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.weight(1f))
            HintIconButton(if (playlist.favorite) "Remove from favorites" else "Add to favorites", onClick = onToggleFavorite) {
                Icon(
                    if (playlist.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (playlist.favorite) "Remove from favorites" else "Add to favorites",
                    tint = if (playlist.favorite) colors.primary else colors.onSurface,
                )
            }
            HintIconButton("Add tracks", onClick = onAddTracks) {
                Icon(Icons.Rounded.Add, contentDescription = "Add tracks")
            }
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                HintIconButton("More options", onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(26.dp),
                    containerColor = colors.surfaceContainerHigh,
                    offset = DpOffset(x = 0.dp, y = (-44).dp),
                ) {
                    @Composable
                    fun item(label: String, action: () -> Unit) = DropdownMenuItem(
                        text = { Text(label, fontSize = 17.sp) },
                        onClick = { menuOpen = false; action() },
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
                    )
                    if (songs.isNotEmpty()) item("Edit", onEdit)
                    item("Rename", onRename)
                    item("Change cover image", onChangeCover)
                    if (onRemoveCover != null) item("Remove cover image", onRemoveCover)
                    item("Delete playlist", onDelete)
                }
            }
        }

        SongList(
            songs = songs,
            onPlay = onPlay,
            onPlayNext = onPlayNext,
            onOptions = onSongOptions,
            state = listState,
            onMove = onMove,
            contentPadding = PaddingValues(bottom = bottom + 8.dp),
            // The tracks sit on a rounded sheet that starts under the header and scrolls with it.
            modifier = Modifier.drawBehind {
                val r = 26.dp.toPx()
                val top = if (listState.firstVisibleItemIndex == 0) {
                    (headerHeight - listState.firstVisibleItemScrollOffset).toFloat()
                } else {
                    -r
                }
                drawRoundRect(sheet, topLeft = Offset(0f, top), size = Size(size.width, size.height - top + r), cornerRadius = CornerRadius(r))
            },
            header = {
                Column {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().onSizeChanged { headerHeight = it.height }.padding(top = 4.dp, bottom = 28.dp),
                    ) {
                        Artwork(cover, Modifier.size(170.dp), corner = 30.dp)
                        Text(
                            playlist.name,
                            fontSize = 22.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 18.dp, start = 24.dp, end = 24.dp),
                        )
                        Text(songCount(songs.size), fontSize = 14.sp, color = colors.onSurface.copy(alpha = 0.75f), modifier = Modifier.padding(top = 8.dp))
                    }
                    Box(Modifier.padding(top = 10.dp)) {
                        ListToolbar(
                            label = "Custom order",
                            count = songs.size,
                            onShuffle = onShuffle,
                            onPlayAll = { onPlay(PlayerConnection.PLAY_ALL) },
                        )
                    }
                    if (songs.isEmpty()) {
                        Text(
                            "No tracks yet. Tap + to add some.",
                            style = SmallLabel,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        )
                    }
                }
            },
        )
    }
}

/** Playlists the app keeps up to date by itself, shown as cards above the user's playlists. */
enum class SmartList(val label: String) {
    RECENTLY_ADDED("Recently added"),
    MOST_PLAYED("Most played"),
    RECENTLY_PLAYED("Recently played"),
    ;

    fun songsFor(songs: List<Song>, plays: Map<Long, SongPlays>): List<Song> = when (this) {
        // Added in the last three months, newest first.
        RECENTLY_ADDED -> {
            val since = System.currentTimeMillis() / 1000 - 90L * 24 * 3600
            songs.filter { it.dateAdded >= since }.sortedByDescending { it.dateAdded }
        }
        MOST_PLAYED -> songs.filter { it.id in plays }.sortedByDescending { plays.getValue(it.id).playCount }.take(100)
        RECENTLY_PLAYED -> songs.filter { it.id in plays }.sortedByDescending { plays.getValue(it.id).lastPlayedAt }.take(100)
    }
}

/**
 * Picking tracks to add to a playlist: tap to tick, a search to narrow the list, Done to add.
 * Tracks already in the playlist show ticked and can't be picked again.
 */
@Composable
fun AddTracksScreen(
    songs: List<Song>,
    alreadyIn: Set<Long>,
    onBack: () -> Unit,
    onDone: (List<Song>) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var picked by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(songs, query) {
        val q = query.trim()
        songs.sortedByDescending { it.dateAdded }.filter {
            q.isEmpty() || it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }
    }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text("Add tracks", fontSize = 22.sp)
                Text(if (picked.isEmpty()) "Tap tracks to pick them" else "${picked.size} selected", style = SmallLabel, color = colors.onSurfaceVariant)
            }
            TextButton(enabled = picked.isNotEmpty(), onClick = {
                val byId = songs.associateBy { it.id }
                onDone(picked.mapNotNull { byId[it] })
            }) { Text("Done", fontSize = 17.sp) }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
                .background(colors.surfaceContainerHigh, RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Box(Modifier.padding(start = 10.dp).weight(1f)) {
                if (query.isEmpty()) Text("Search tracks", fontSize = 16.sp, color = colors.onSurfaceVariant)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Panel(Modifier.weight(1f).padding(top = 6.dp)) { LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = bottom + 8.dp),
        ) {
            items(shown, key = { it.id }) { song ->
                val inPlaylist = song.id in alreadyIn
                SongRow(
                    song = song,
                    onClick = {
                        if (!inPlaylist) picked = if (song.id in picked) picked - song.id else picked + song.id
                    },
                    onPlayNext = {},
                    onOptions = {},
                    selected = inPlaylist || song.id in picked,
                    info = if (inPlaylist) "In playlist" else null,
                )
            }
        } }
    }
}

/** A playlist on the Playlists tab: its picture, name, a heart if it's a favorite, its size. */
@Composable
fun PlaylistRow(playlist: Playlist, cover: Uri?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Artwork(cover, Modifier.size(58.dp), corner = 16.dp)
        Text(
            playlist.name,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 18.dp, end = 8.dp),
        )
        if (playlist.favorite) {
            Icon(Icons.Rounded.Favorite, contentDescription = "Favorite", tint = colors.primary, modifier = Modifier.padding(end = 10.dp).size(18.dp))
        }
        Text(songCount(playlist.songIds.size), fontSize = 15.sp, color = colors.onSurfaceVariant)
    }
}

/** A big square card for a smart list, like Samsung's "Most played". */
@Composable
fun SmartListCard(list: SmartList, cover: Uri?, count: Int, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp),
    ) {
        Artwork(cover, Modifier.size(150.dp), corner = 26.dp)
        Text(list.label, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
        Text(songCount(count), style = SmallLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
    }
}
