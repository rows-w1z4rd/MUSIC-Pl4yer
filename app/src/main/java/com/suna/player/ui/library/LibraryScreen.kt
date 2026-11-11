package com.suna.player.ui.library

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suna.player.R
import com.suna.player.data.Album
import com.suna.player.data.Artist
import com.suna.player.data.Playlist
import com.suna.player.data.Song
import com.suna.player.data.SongSort
import com.suna.player.data.albumArtUri
import com.suna.player.data.sortedFor
import com.suna.player.data.toAlbums
import com.suna.player.data.toArtists
import com.suna.player.ui.common.Artwork
import com.suna.player.ui.common.ContentCard
import com.suna.player.ui.common.LocalMiniPlayerSpace
import com.suna.player.ui.common.SongList
import com.suna.player.ui.common.pressable

private val tabs = listOf("Songs", "Albums", "Artists", "Playlists")

@Composable
fun LibraryScreen(
    songs: List<Song>,
    loaded: Boolean,
    onPlay: (list: List<Song>, index: Int) -> Unit,
    onShuffle: (list: List<Song>) -> Unit,
    onPlayNext: (Song) -> Unit,
    onSongOptions: (Song) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    playlists: List<Playlist>,
    onOpenPlaylist: (Long) -> Unit,
    onNewPlaylist: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SongSort.TITLE) }

    val sorted = remember(songs, sort) { songs.sortedFor(sort) }
    val albums = remember(songs) { songs.toAlbums() }
    val artists = remember(songs) { songs.toArtists() }
    val results = remember(songs, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            emptyList()
        } else {
            songs.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.artist.contains(q, ignoreCase = true) ||
                    it.album.contains(q, ignoreCase = true)
            }
        }
    }

    // Lists scroll under the floating mini player and the system navigation bar.
    val bottomSpace = LocalMiniPlayerSpace.current +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listPadding = PaddingValues(top = 4.dp, bottom = bottomSpace + 8.dp)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 24.dp, end = 8.dp, top = 28.dp, bottom = 12.dp),
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                searching = !searching
                if (!searching) query = ""
            }) {
                Icon(
                    if (searching) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = if (searching) "Close search" else "Search",
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Palette, contentDescription = "Appearance")
            }
        }

        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search songs, artists, albums") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        } else {
            TabStrip(selected = tab, onSelect = { tab = it })
        }

        ContentCard(Modifier.weight(1f)) {
            when {
                searching && query.isNotBlank() && results.isEmpty() ->
                    EmptyMessage("No matches for \"${query.trim()}\"")
                searching -> SongList(
                    songs = results,
                    onPlay = { onPlay(results, it) },
                    onPlayNext = onPlayNext,
                    onOptions = onSongOptions,
                    contentPadding = listPadding,
                )
                !loaded -> Box(Modifier.fillMaxSize())
                tab == 3 -> PlaylistList(playlists, onOpenPlaylist, onNewPlaylist, listPadding)
                songs.isEmpty() -> EmptyMessage("No music found on this phone yet")
                tab == 0 -> Column {
                    SongsToolbar(
                        sort = sort,
                        onCycleSort = {
                            sort = SongSort.entries[(sort.ordinal + 1) % SongSort.entries.size]
                        },
                        onShuffle = { onShuffle(sorted) },
                        onPlayAll = { onPlay(sorted, 0) },
                    )
                    SongList(
                        songs = sorted,
                        onPlay = { onPlay(sorted, it) },
                        onPlayNext = onPlayNext,
                        onOptions = onSongOptions,
                        contentPadding = listPadding,
                    )
                }
                tab == 1 -> AlbumGrid(albums, onOpenAlbum, bottomSpace)
                else -> ArtistList(artists, onOpenArtist, listPadding)
            }
        }
    }
}

/** Text-only tabs: the selected one is large and bright, the others small and dim. */
@Composable
private fun TabStrip(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val isSelected = selected == index
            val size by animateFloatAsState(
                targetValue = if (isSelected) 26f else 16f,
                animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
                label = "tabSize",
            )
            val base = MaterialTheme.colorScheme.onBackground
            Text(
                text = label,
                fontSize = size.sp,
                letterSpacing = if (isSelected) (-0.4).sp else 0.sp,
                color = if (isSelected) base else base.copy(alpha = 0.55f),
                modifier = Modifier
                    .alignByBaseline()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun SongsToolbar(
    sort: SongSort,
    onCycleSort: () -> Unit,
    onShuffle: () -> Unit,
    onPlayAll: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    ) {
        TextButton(
            onClick = onCycleSort,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Change sort order",
                modifier = Modifier.size(20.dp),
            )
            Text(sort.label, Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.weight(1f))
        FilledTonalIconButton(onClick = onShuffle) {
            Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle all songs")
        }
        FilledTonalIconButton(onClick = onPlayAll, modifier = Modifier.padding(start = 8.dp)) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play all songs")
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, onOpen: (Long) -> Unit, bottomSpace: androidx.compose.ui.unit.Dp) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = bottomSpace + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.id }) { album ->
            Column(Modifier.pressable { onOpen(album.id) }) {
                Artwork(
                    uri = albumArtUri(album.id),
                    corner = 24.dp,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                Text(
                    album.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
                )
                Text(
                    album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ArtistList(artists: List<Artist>, onOpen: (Long) -> Unit, padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        items(artists, key = { it.id }) { artist ->
            SimpleRow(
                title = artist.name,
                subtitle = if (artist.songCount == 1) "1 song" else "${artist.songCount} songs",
                onClick = { onOpen(artist.id) },
            ) {
                Icon(Icons.Filled.Person, contentDescription = null)
            }
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: List<Playlist>,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    padding: PaddingValues,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item(key = "new") {
            SimpleRow(title = "New playlist", subtitle = null, onClick = onNew) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
        items(playlists, key = { it.id }) { playlist ->
            val n = playlist.songIds.size
            SimpleRow(
                title = playlist.name,
                subtitle = if (n == 1) "1 song" else "$n songs",
                onClick = { onOpen(playlist.id) },
            ) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
            }
        }
    }
}

/** A list row with a round icon badge, a title and an optional second line. */
@Composable
private fun SimpleRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
        Column(Modifier.padding(start = 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
