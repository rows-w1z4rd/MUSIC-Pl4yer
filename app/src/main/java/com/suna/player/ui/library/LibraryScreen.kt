package com.suna.player.ui.library

import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.snapshotFlow
import android.content.Context
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.util.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
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
    favorites: List<Long>,
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
    // The library reopens on the tab it was left on, even after the app was cleared.
    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    val startPage = remember(tabs) {
        tabs.indexOfFirst { it.name == uiPrefs.getString(LAST_TAB, null) }.coerceAtLeast(0)
    }
    val pager = rememberPagerState(initialPage = startPage, pageCount = { tabs.size })
    LaunchedEffect(pager, tabs) {
        snapshotFlow { pager.settledPage }.collect { page ->
            tabs.getOrNull(page)?.let { uiPrefs.edit().putString(LAST_TAB, it.name).apply() }
        }
    }
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
                tabs.getOrNull(pager.currentPage) in setOf(LibraryTab.SONGS, LibraryTab.FAVORITES, LibraryTab.MOST_PLAYED, LibraryTab.RECENT)
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

        if (searching) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = if (hasHeader) 4.dp else 10.dp, bottom = 8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search songs, artists, albums") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                if (!hasHeader) icons()
            }
        } else {
            // The tabs span the whole width so the active one sits in the middle of the
            // screen; the buttons float on the right, where the tabs fade out.
            Box(Modifier.fillMaxWidth().padding(top = if (hasHeader) 4.dp else 10.dp, bottom = 8.dp)) {
                TabBar(
                    tabs = tabs,
                    selected = pager.currentPage,
                    position = { pager.currentPage + pager.currentPageOffsetFraction },
                    onSelect = { scope.launch { pager.animateScrollToPage(it) } },
                    endClearance = if (hasHeader) 0.dp else 100.dp,
                    modifier = Modifier.align(Alignment.Center),
                )
                if (!hasHeader) {
                    Row(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) { icons() }
                }
            }
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
                        LibraryTab.FAVORITES -> FavoritesPage(
                            songs, favorites, onPlay, onShuffle, onPlayNext, onSongOptions, listPadding,
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

/** Where a tab's name sits inside the tab row, in pixels. */
private data class TabSpot(val left: Float, val right: Float, val top: Float, val bottom: Float)

/**
 * The library tabs, Samsung-style: the active tab's name always sits in the middle and the
 * row glides along with the pages as you swipe, its neighbors fading out at the sides.
 * An accent line under the name sends light up and outward, like a stage light.
 */
@Composable
private fun TabBar(
    tabs: List<LibraryTab>,
    selected: Int,
    /** The pager's position, including how far a swipe has got: 1.5 is halfway to tab 2. */
    position: () -> Float,
    onSelect: (Int) -> Unit,
    /** Room kept clear on the right for the buttons floating there. */
    endClearance: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val spots = remember(tabs) { mutableStateMapOf<Int, TabSpot>() }
    var viewport by remember { mutableIntStateOf(0) }
    val padX = 14.dp
    val padTop = 8.dp
    val padBottom = 12.dp

    // Where the row is, interpolated between tabs while a swipe is under way.
    fun spotAt(pos: Float): TabSpot? {
        val clamped = pos.coerceIn(0f, (tabs.size - 1).coerceAtLeast(0).toFloat())
        val from = spots[clamped.toInt()] ?: return null
        val to = spots[(clamped.toInt() + 1).coerceAtMost(tabs.lastIndex)] ?: from
        val f = clamped - clamped.toInt()
        return TabSpot(lerp(from.left, to.left, f), lerp(from.right, to.right, f), lerp(from.top, to.top, f), lerp(from.bottom, to.bottom, f))
    }

    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged { viewport = it.width }
            .clipToBounds()
            // Its own layer, so the edges can be faded without touching anything else.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = 36.dp.toPx()
                val end = size.width - endClearance.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        (fade / size.width) to Color.Black,
                        ((end - fade) / size.width).coerceAtLeast(0f) to Color.Black,
                        (end / size.width).coerceIn(0f, 1f) to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .graphicsLayer {
                    val spot = spotAt(position())
                    translationX = if (spot == null) 0f else viewport / 2f - (spot.left + spot.right) / 2f
                }
                .drawBehind {
                    val spot = spotAt(position()) ?: return@drawBehind
                    val accent = colors.primary
                    val lineHeight = 2.5.dp.toPx()
                    val lineTop = spot.bottom + 5.dp.toPx()
                    // The line is a little shorter than the name; its light fans out as it rises
                    // (left side like a backslash, right like a slash) and stops exactly at the
                    // name's edges, gone halfway up the word.
                    val glowTop = spot.top + (spot.bottom - spot.top) * 0.5f
                    val spread = (spot.right - spot.left) * 0.14f
                    val lineLeft = spot.left + spread
                    val lineRight = spot.right - spread
                    val layers = 6
                    val brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to accent.copy(alpha = 0.035f),
                        1f to accent.copy(alpha = 0.09f),
                        startY = glowTop,
                        endY = lineTop,
                    )
                    // Stacked, ever narrower beams: bright in the middle, soft at the sides.
                    repeat(layers) { k ->
                        val inset = spread * k / layers
                        val beam = Path().apply {
                            moveTo(lineLeft, lineTop)
                            lineTo(spot.left + inset, glowTop)
                            lineTo(spot.right - inset, glowTop)
                            lineTo(lineRight, lineTop)
                            close()
                        }
                        drawPath(beam, brush)
                    }
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(lineLeft, lineTop),
                        size = Size(lineRight - lineLeft, lineHeight),
                        cornerRadius = CornerRadius(lineHeight / 2),
                    )
                },
        ) {
            tabs.forEachIndexed { index, tab ->
                val active = index == selected
                val lit by animateFloatAsState(if (active) 1f else 0f, tween(220), label = "tabText")
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = lerp(colors.onSurfaceVariant, colors.onSurface, lit),
                    modifier = Modifier
                        .onPlaced { coords ->
                            val at = coords.positionInParent()
                            with(density) {
                                spots[index] = TabSpot(
                                    left = at.x + padX.toPx(),
                                    right = at.x + coords.size.width - padX.toPx(),
                                    top = at.y + padTop.toPx(),
                                    bottom = at.y + coords.size.height - padBottom.toPx(),
                                )
                            }
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(index) }
                        .padding(start = padX, end = padX, top = padTop, bottom = padBottom),
                )
            }
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
            if (label == null) songCount(count) else "$count",
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

/** Hearted songs, newest first. */
@Composable
private fun FavoritesPage(
    songs: List<Song>,
    favorites: List<Long>,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onPlayNext: (Song) -> Unit,
    onSongOptions: (Song) -> Unit,
    padding: PaddingValues,
) {
    val list = remember(songs, favorites) {
        val byId = songs.associateBy { it.id }
        favorites.mapNotNull { byId[it] }
    }
    if (list.isEmpty()) {
        EmptyMessage("Tap the heart on a song to keep it here")
        return
    }
    SongList(
        songs = list,
        onPlay = { onPlay(list, it) },
        onPlayNext = onPlayNext,
        onOptions = onSongOptions,
        contentPadding = padding,
        header = {
            ListToolbar(
                label = null,
                count = list.size,
                onLabel = null,
                onShuffle = { onShuffle(list) },
                onPlayAll = { onPlay(list, 0) },
            )
        },
    )
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

private const val LAST_TAB = "last_tab"
