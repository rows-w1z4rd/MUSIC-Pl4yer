package com.suna.player.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.DpOffset
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.suna.player.data.Album
import com.suna.player.data.Artist
import com.suna.player.data.Folder
import com.suna.player.data.LibraryTab
import com.suna.player.data.Playlist
import com.suna.player.data.Song
import com.suna.player.data.SongPlays
import com.suna.player.data.SongSort
import com.suna.player.data.albumArtUri
import com.suna.player.data.sortedFor
import com.suna.player.data.toAlbums
import com.suna.player.data.toArtists
import com.suna.player.data.toFolders
import com.suna.player.ui.common.Artwork
import com.suna.player.ui.common.EmptyMessage
import com.suna.player.ui.common.LocalMiniPlayerSpace
import com.suna.player.ui.common.Panel
import com.suna.player.ui.common.SimpleRow
import com.suna.player.ui.common.SongList
import com.suna.player.ui.common.pressable
import com.suna.player.ui.common.songCount
import com.suna.player.ui.theme.SmallLabel
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    songs: List<Song>,
    loaded: Boolean,
    headerText: String,
    tabs: List<LibraryTab>,
    plays: Map<Long, SongPlays>,
    playlists: List<Playlist>,
    onPlay: (list: List<Song>, index: Int) -> Unit,
    onShuffle: (list: List<Song>) -> Unit,
    onPlayNext: (Song) -> Unit,
    onSongOptions: (Song) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onNewPlaylist: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartSelection: () -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val pager = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

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
    val listPadding = PaddingValues(top = 6.dp, bottom = bottomSpace + 8.dp)

    val icons: @Composable () -> Unit = {
        IconButton(onClick = {
            searching = !searching
            if (!searching) query = ""
        }) {
            Icon(
                if (searching) Icons.Rounded.Close else Icons.Rounded.Search,
                contentDescription = if (searching) "Close search" else "Search",
            )
        }
        Box {
            var menuOpen by remember { mutableStateOf(false) }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
            }
            val onSongPage = searching ||
                tabs.getOrNull(pager.currentPage) in setOf(LibraryTab.SONGS, LibraryTab.MOST_PLAYED, LibraryTab.RECENT)
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                offset = DpOffset(x = 0.dp, y = 4.dp),
            ) {
                if (onSongPage) {
                    MenuItem("Select") {
                        menuOpen = false
                        onStartSelection()
                    }
                }
                MenuItem("Settings") {
                    menuOpen = false
                    onOpenSettings()
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        val hasHeader = headerText.isNotBlank()
        if (hasHeader) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 20.dp, end = 4.dp, top = 16.dp),
            ) {
                Text(
                    headerText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                icons()
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = 8.dp,
                end = 4.dp,
                top = if (hasHeader) 4.dp else 10.dp,
                bottom = 8.dp,
            ),
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (searching) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("Search songs, artists, albums") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                    )
                } else {
                    TabBar(
                        tabs = tabs,
                        selected = pager.currentPage,
                        onSelect = { scope.launch { pager.animateScrollToPage(it) } },
                    )
                }
            }
            if (!hasHeader) icons()
        }

        Panel(Modifier.weight(1f)) {
            when {
                searching && query.isNotBlank() && results.isEmpty() ->
                    EmptyMessage("No results for \"${query.trim()}\"")
                searching -> SongList(
                    songs = results,
                    onPlay = { onPlay(results, it) },
                    onPlayNext = onPlayNext,
                    onOptions = onSongOptions,
                    contentPadding = listPadding,
                )
                !loaded -> Spacer(Modifier.fillMaxSize())
                else -> HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    when (tabs.getOrNull(page)) {
                        LibraryTab.SONGS -> SongsPage(
                            songs, plays, onPlay, onShuffle, onPlayNext, onSongOptions, listPadding,
                        )
                        LibraryTab.ALBUMS -> AlbumGrid(songs, onOpenAlbum, bottomSpace)
                        LibraryTab.ARTISTS -> ArtistList(songs, onOpenArtist, listPadding)
                        LibraryTab.PLAYLISTS ->
                            PlaylistList(playlists, onOpenPlaylist, onNewPlaylist, listPadding)
                        LibraryTab.FOLDERS -> FolderList(songs, onOpenFolder, listPadding)
                        LibraryTab.MOST_PLAYED -> StatsPage(
                            songs, plays, mostPlayed = true, onPlay, onPlayNext, onSongOptions, listPadding,
                        )
                        LibraryTab.RECENT -> StatsPage(
                            songs, plays, mostPlayed = false, onPlay, onPlayNext, onSongOptions, listPadding,
                        )
                        null -> Spacer(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

/** Workspace-style tabs, like a Linux status bar: the active one is filled with the accent. */
@Composable
private fun TabBar(tabs: List<LibraryTab>, selected: Int, onSelect: (Int) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(selected) { if (selected in tabs.indices) state.animateScrollToItem(selected) }
    val colors = MaterialTheme.colorScheme
    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        itemsIndexed(tabs, key = { _, tab -> tab.name }) { index, tab ->
            val active = index == selected
            val shape = RoundedCornerShape(8.dp)
            Text(
                text = tab.label,
                style = SmallLabel.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (active) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier
                    .clip(shape)
                    .background(if (active) colors.primaryContainer else Color.Transparent)
                    .border(
                        1.dp,
                        if (active) colors.primary.copy(alpha = 0.45f) else Color.Transparent,
                        shape,
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun SongsPage(
    songs: List<Song>,
    plays: Map<Long, SongPlays>,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onPlayNext: (Song) -> Unit,
    onSongOptions: (Song) -> Unit,
    padding: PaddingValues,
) {
    var sort by rememberSaveable { mutableStateOf(SongSort.TITLE) }
    val sorted = remember(songs, sort, if (sort == SongSort.PLAYS) plays else null) {
        songs.sortedFor(sort, plays)
    }
    if (songs.isEmpty()) {
        EmptyMessage("No music found on this phone")
        return
    }
    SongList(
        songs = sorted,
        onPlay = { onPlay(sorted, it) },
        onPlayNext = onPlayNext,
        onOptions = onSongOptions,
        contentPadding = padding,
        header = {
            ListToolbar(
                label = sort.label,
                count = songs.size,
                onLabel = { sort = SongSort.entries[(sort.ordinal + 1) % SongSort.entries.size] },
                onShuffle = { onShuffle(sorted) },
                onPlayAll = { onPlay(sorted, 0) },
            )
        },
    )
}

/** Sort toggle on the left, song count, then shuffle and play-all on the right. */
@Composable
fun ListToolbar(
    label: String?,
    count: Int,
    onLabel: (() -> Unit)?,
    onShuffle: () -> Unit,
    onPlayAll: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 10.dp, bottom = 4.dp),
    ) {
        if (label != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (onLabel != null) Modifier.clickable(onClick = onLabel) else Modifier)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Sort,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(label, style = SmallLabel, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text(
            "$count",
            style = SmallLabel,
            color = colors.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        SquareButton(onClick = onShuffle) {
            Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(20.dp))
        }
        SquareButton(onClick = onPlayAll, filled = true, modifier = Modifier.padding(start = 8.dp)) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play all", modifier = Modifier.size(22.dp))
        }
    }
}

/** A small rounded-square button; [filled] uses the accent. */
@Composable
fun SquareButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    size: Dp = 38.dp,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    androidx.compose.foundation.layout.Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (filled) colors.primary else colors.surfaceContainerHighest)
            .clickable(onClick = onClick),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides
                if (filled) colors.onPrimary else colors.onSurface,
            content = content,
        )
    }
}

@Composable
private fun StatsPage(
    songs: List<Song>,
    plays: Map<Long, SongPlays>,
    mostPlayed: Boolean,
    onPlay: (List<Song>, Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onSongOptions: (Song) -> Unit,
    padding: PaddingValues,
) {
    val list = remember(songs, plays, mostPlayed) {
        val played = songs.filter { it.id in plays }
        if (mostPlayed) {
            played.sortedByDescending { plays.getValue(it.id).playCount }
        } else {
            played.sortedByDescending { plays.getValue(it.id).lastPlayedAt }
        }
    }
    if (list.isEmpty()) {
        EmptyMessage("Songs you play will show up here")
        return
    }
    val now = remember(plays) { System.currentTimeMillis() }
    SongList(
        songs = list,
        onPlay = { onPlay(list, it) },
        onPlayNext = onPlayNext,
        onOptions = onSongOptions,
        contentPadding = padding,
        info = { song ->
            val p = plays[song.id] ?: return@SongList null
            if (mostPlayed) plays(p.playCount) else timeAgo(now - p.lastPlayedAt)
        },
    )
}

private fun timeAgo(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} h ago"
        minutes < 60 * 24 * 2 -> "Yesterday"
        else -> "${minutes / (60 * 24)} days ago"
    }
}

private fun plays(n: Int) = if (n == 1) "1 play" else "$n plays"

@Composable
private fun AlbumGrid(songs: List<Song>, onOpen: (Long) -> Unit, bottomSpace: Dp) {
    val albums: List<Album> = remember(songs) { songs.toAlbums() }
    if (albums.isEmpty()) {
        EmptyMessage("No albums")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = bottomSpace + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.id }) { album ->
            Column(Modifier.pressable { onOpen(album.id) }) {
                Artwork(
                    uri = albumArtUri(album.id),
                    corner = 12.dp,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                Text(
                    album.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
                )
                Text(
                    album.artist,
                    style = SmallLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ArtistList(songs: List<Song>, onOpen: (Long) -> Unit, padding: PaddingValues) {
    val artists: List<Artist> = remember(songs) { songs.toArtists() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        items(artists, key = { it.id }) { artist ->
            SimpleRow(artist.name, songCount(artist.songCount), onClick = { onOpen(artist.id) }) {
                Icon(Icons.Rounded.Person, contentDescription = null)
            }
        }
    }
}

@Composable
private fun FolderList(songs: List<Song>, onOpen: (String) -> Unit, padding: PaddingValues) {
    val folders: List<Folder> = remember(songs) { songs.toFolders() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        items(folders, key = { it.path }) { folder ->
            SimpleRow(
                title = folder.name,
                subtitle = "${songCount(folder.songCount)} · ${folder.path.ifEmpty { "/" }}",
                onClick = { onOpen(folder.path) },
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null)
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
                Icon(Icons.Rounded.Add, contentDescription = null)
            }
        }
        items(playlists, key = { it.id }) { playlist ->
            SimpleRow(playlist.name, songCount(playlist.songIds.size), onClick = { onOpen(playlist.id) }) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null)
            }
        }
        item(key = "end") { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
        modifier = Modifier.widthIn(min = 180.dp),
    )
}
