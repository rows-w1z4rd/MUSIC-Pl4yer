package com.suna.player

import androidx.compose.ui.geometry.Rect
import com.suna.player.ui.library.SongMenuTarget
import com.suna.player.ui.library.SongContextMenu
import com.suna.player.ui.common.LocalSongLongPress
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.suna.player.data.Appearance
import com.suna.player.data.Song
import com.suna.player.ui.common.LocalMiniPlayerSpace
import com.suna.player.ui.common.shareSongs
import com.suna.player.ui.common.rememberDeleteSongs
import com.suna.player.ui.common.SelectionTopBar
import com.suna.player.ui.common.SelectionBottomBar
import com.suna.player.ui.common.SongSelection
import com.suna.player.ui.common.LocalSongSelection
import com.suna.player.ui.library.AddToPlaylistDialog
import com.suna.player.ui.library.DeletePlaylistDialog
import com.suna.player.ui.library.LibraryScreen
import com.suna.player.ui.library.PlaylistNameDialog
import com.suna.player.ui.library.SongDetailsSheet
import com.suna.player.ui.library.SongListScreen
import com.suna.player.ui.library.SongOptionsSheet
import com.suna.player.ui.player.MiniPlayerSpace
import com.suna.player.ui.player.PlayerSheet
import com.suna.player.ui.player.QueueScreen
import com.suna.player.ui.player.collapse
import com.suna.player.ui.settings.SettingsScreen
import com.suna.player.ui.settings.WallpaperEditor
import com.suna.player.ui.theme.AppBackground
import com.suna.player.ui.theme.SunaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val app get() = application as SunaApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearance by app.settings.appearance.collectAsStateWithLifecycle(initialValue = null)
            val current = appearance

            // The wallpaper picture is rebuilt only when the image or the blur setting changes.
            val wallpaper by produceState<ImageBitmap?>(
                initialValue = null,
                current?.hasWallpaper,
                current?.wallpaperStamp,
                current?.blur,
            ) {
                value = if (current != null && current.hasWallpaper) {
                    app.wallpapers.render(current.blur)
                } else {
                    null
                }
            }

            SunaTheme(current, wallpaper) {
                AppBackground(wallpaper = wallpaper, appearance = current) {
                    if (current != null) AppRoot(app, current, wallpaper)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        app.player.connect()
    }

    override fun onStop() {
        app.player.release()
        super.onStop()
    }
}

private object Routes {
    const val LIBRARY = "library"
    const val QUEUE = "queue"
    const val SETTINGS = "settings"
    const val WALLPAPER = "wallpaper"
    const val ALBUM = "album/{id}"
    const val ARTIST = "artist/{id}"
    const val PLAYLIST = "playlist/{id}"
    const val FOLDER = "folder/{path}"
}

/** Which song the options sheet is open for, and from which playlist (if any). */
private data class OptionsTarget(val song: Song, val playlistId: Long?)

/** A pending "new playlist" name prompt, with any songs to put in it. */
private data class NewPlaylistRequest(val songs: List<Song>)

@Composable
private fun AppRoot(app: SunaApp, appearance: Appearance, wallpaper: ImageBitmap?) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }

    if (!granted) {
        PermissionScreen(onAllow = { askPermission.launch(Manifest.permission.READ_MEDIA_AUDIO) })
    } else {
        LaunchedEffect(Unit) { app.library.start() }
        MainContent(app, appearance, wallpaper)
    }
}

@Composable
private fun MainContent(app: SunaApp, appearance: Appearance, wallpaper: ImageBitmap?) {
    val context = LocalContext.current
    val player = app.player
    val songs by app.library.songs.collectAsStateWithLifecycle()
    val loaded by app.library.loaded.collectAsStateWithLifecycle()
    val playlists by app.playlists.playlists.collectAsStateWithLifecycle()
    val plays by app.stats.plays.collectAsStateWithLifecycle()
    val favorites by app.favorites.ids.collectAsStateWithLifecycle()
    val favoriteSet = remember(favorites) { favorites.toHashSet() }
    val skipSilence by app.settings.skipSilence.collectAsStateWithLifecycle(initialValue = false)
    val playerState by player.state.collectAsStateWithLifecycle()
    val songsById = remember(songs) { songs.associateBy { it.id } }

    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val scope = rememberCoroutineScope()

    var optionsFor by remember { mutableStateOf<OptionsTarget?>(null) }
    var addToPlaylist by remember { mutableStateOf<List<Song>?>(null) }
    var detailsFor by remember { mutableStateOf<Song?>(null) }
    var pressed by remember { mutableStateOf<SongMenuTarget?>(null) }
    // Remembered, so handing it to every list doesn't make them all redraw.
    val onLongPress: (Song, List<Song>, Rect) -> Unit = remember {
        { song, list, rect -> pressed = SongMenuTarget(song, list, rect) }
    }
    var newPlaylist by remember { mutableStateOf<NewPlaylistRequest?>(null) }

    // 0 = the player is a bar at the bottom, 1 = it fills the screen.
    val expansion = remember { Animatable(0f) }
    val current = playerState.current
    val selection = remember { SongSelection() }
    val showPlayer = current != null && route != Routes.QUEUE && route != Routes.WALLPAPER && !selection.active
    val deleteSongs = rememberDeleteSongs(onDeleted = selection::clear)
    val playNext: (Song) -> Unit = { player.playNext(listOf(it)) }
    val openSongOptions: (Song) -> Unit = { optionsFor = OptionsTarget(it, playlistId = null) }

    LaunchedEffect(showPlayer) { if (!showPlayer) expansion.snapTo(0f) }
    // Picking songs also ends when leaving the page it started on.
    LaunchedEffect(route) { selection.clear() }

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalMiniPlayerSpace provides if (showPlayer || selection.active) MiniPlayerSpace else 0.dp,
            LocalSongSelection provides selection,
            LocalSongLongPress provides onLongPress,
        ) {
            NavHost(
                navController = nav,
                startDestination = Routes.LIBRARY,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        songs = songs,
                        loaded = loaded,
                        headerText = appearance.headerText,
                        tabs = appearance.tabs,
                        plays = plays,
                        favorites = favorites,
                        playlists = playlists,
                        onPlay = { list, index -> player.play(list, index) },
                        onShuffle = { player.shuffleAll(it) },
                        onPlayNext = playNext,
                        onSongOptions = openSongOptions,
                        onOpenAlbum = { nav.navigate("album/$it") },
                        onOpenArtist = { nav.navigate("artist/$it") },
                        onOpenFolder = { nav.navigate("folder/${Uri.encode(it)}") },
                        onOpenPlaylist = { nav.navigate("playlist/$it") },
                        onNewPlaylist = { newPlaylist = NewPlaylistRequest(emptyList()) },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                        onStartSelection = selection::startEmpty,
                    )
                }
                composable(
                    Routes.ALBUM,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: -1L
                    val albumSongs = remember(songs, id) {
                        songs.filter { it.albumId == id }.sortedBy { it.track }
                    }
                    SongListScreen(
                        title = albumSongs.firstOrNull()?.album ?: "Album",
                        songs = albumSongs,
                        onBack = { nav.popBackStack() },
                        onPlay = { player.play(albumSongs, it) },
                        onShuffle = { player.shuffleAll(albumSongs) },
                        onPlayNext = playNext,
                        onSongOptions = openSongOptions,
                    )
                }
                composable(
                    Routes.ARTIST,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: -1L
                    val artistSongs = remember(songs, id) { songs.filter { it.artistId == id } }
                    SongListScreen(
                        title = artistSongs.firstOrNull()?.artist ?: "Artist",
                        songs = artistSongs,
                        onBack = { nav.popBackStack() },
                        onPlay = { player.play(artistSongs, it) },
                        onShuffle = { player.shuffleAll(artistSongs) },
                        onPlayNext = playNext,
                        onSongOptions = openSongOptions,
                    )
                }
                composable(
                    Routes.FOLDER,
                    arguments = listOf(navArgument("path") { type = NavType.StringType }),
                ) { entry ->
                    val path = entry.arguments?.getString("path").orEmpty()
                    val folderSongs = remember(songs, path) { songs.filter { it.folder == path } }
                    SongListScreen(
                        title = path.trimEnd('/').substringAfterLast('/').ifEmpty { "Phone" },
                        songs = folderSongs,
                        onBack = { nav.popBackStack() },
                        onPlay = { player.play(folderSongs, it) },
                        onShuffle = { player.shuffleAll(folderSongs) },
                        onPlayNext = playNext,
                        onSongOptions = openSongOptions,
                    )
                }
                composable(
                    Routes.PLAYLIST,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: -1L
                    val playlist = playlists.find { it.id == id }
                    val playlistSongs = remember(playlist, songsById) {
                        playlist?.songIds?.mapNotNull { songsById[it] } ?: emptyList()
                    }
                    var renaming by remember { mutableStateOf(false) }
                    var deleting by remember { mutableStateOf(false) }

                    SongListScreen(
                        title = playlist?.name ?: "Playlist",
                        songs = playlistSongs,
                        onBack = { nav.popBackStack() },
                        onPlay = { player.play(playlistSongs, it) },
                        onShuffle = { player.shuffleAll(playlistSongs) },
                        onPlayNext = playNext,
                        onSongOptions = { optionsFor = OptionsTarget(it, playlistId = id) },
                        actions = {
                            IconButton(onClick = { renaming = true }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Rename playlist")
                            }
                            IconButton(onClick = { deleting = true }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete playlist")
                            }
                        },
                    )

                    if (renaming && playlist != null) {
                        PlaylistNameDialog(
                            title = "Rename playlist",
                            initialName = playlist.name,
                            confirmLabel = "Rename",
                            onConfirm = { app.playlists.rename(id, it) },
                            onDismiss = { renaming = false },
                        )
                    }
                    if (deleting && playlist != null) {
                        DeletePlaylistDialog(
                            name = playlist.name,
                            onConfirm = {
                                nav.popBackStack()
                                app.playlists.delete(id)
                            },
                            onDismiss = { deleting = false },
                        )
                    }
                }
                composable(Routes.QUEUE) {
                    QueueScreen(
                        state = playerState,
                        onBack = { nav.popBackStack() },
                        onPlayIndex = player::playQueueItem,
                        onRemove = player::removeFromQueue,
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        appearance = appearance,
                        skipSilence = skipSilence,
                        wallpaper = wallpaper,
                        settings = app.settings,
                        wallpapers = app.wallpapers,
                        onBack = { nav.popBackStack() },
                        onAdjustWallpaper = { nav.navigate(Routes.WALLPAPER) { launchSingleTop = true } },
                    )
                }
                composable(Routes.WALLPAPER) {
                    WallpaperEditor(
                        wallpaper = wallpaper,
                        appearance = appearance,
                        onSave = { scale, x, y ->
                            scope.launch { app.settings.setWallpaperPlacement(scale, x, y) }
                        },
                        onClose = { nav.popBackStack() },
                    )
                }
            }
        }

        // Declared after the NavHost so they get Back before page navigation does: Back first
        // closes the full screen player or ends picking songs, then leaves the page.
        BackHandler(enabled = showPlayer && expansion.targetValue > 0f) {
            scope.launch { expansion.collapse() }
        }
        BackHandler(enabled = selection.active) { selection.clear() }

        if (showPlayer) {
            PlayerSheet(
                state = playerState,
                player = player,
                expansion = expansion,
                onMore = current.mediaId.toLongOrNull()
                    ?.let { songsById[it] }
                    ?.let { song -> { optionsFor = OptionsTarget(song, playlistId = null) } },
                onAddToPlaylist = current.mediaId.toLongOrNull()
                    ?.let { songsById[it] }
                    ?.let { song -> { addToPlaylist = listOf(song) } },
                isFavorite = current?.mediaId?.toLongOrNull() in favoriteSet,
                onToggleFavorite = current?.mediaId?.toLongOrNull()?.let { id -> { app.favorites.toggle(id) } },
            )
        }

        pressed?.let { target ->
            val song = target.song
            SongContextMenu(
                target = target,
                isFavorite = song.id in favoriteSet,
                onPlayNext = { player.playNext(listOf(song)) },
                onAddToQueue = { player.addToQueue(listOf(song)) },
                onToggleFavorite = { app.favorites.toggle(song.id) },
                onAddToPlaylist = { addToPlaylist = listOf(song) },
                onOpenAlbum = { nav.navigate("album/${song.albumId}") },
                onShowDetails = { detailsFor = song },
                onShare = { shareSongs(context, listOf(song)) },
                onSelect = { selection.start(target.list, song) },
                onDelete = { deleteSongs(listOf(song)) },
                onDismiss = { pressed = null },
            )
        }

        SelectionTopBar(selection)
        SelectionBottomBar(
            selection = selection,
            onPlay = {
                player.play(it, 0)
                selection.clear()
            },
            onAdd = { addToPlaylist = it },
            onDelete = deleteSongs,
        )
    }

    optionsFor?.let { target ->
        val song = target.song
        val playlistId = target.playlistId
        SongOptionsSheet(
            song = song,
            isFavorite = song.id in favoriteSet,
            onToggleFavorite = { app.favorites.toggle(song.id) },
            onPlayNext = { player.playNext(listOf(song)) },
            onAddToQueue = { player.addToQueue(listOf(song)) },
            onAddToPlaylist = { addToPlaylist = listOf(song) },
            // Leaving for another page also lowers the full screen player, if it was up.
            onOpenAlbum = {
                scope.launch { expansion.snapTo(0f) }
                nav.navigate("album/${song.albumId}")
            },
            onOpenArtist = {
                scope.launch { expansion.snapTo(0f) }
                nav.navigate("artist/${song.artistId}")
            },
            onShowDetails = { detailsFor = song },
            onShare = { shareSongs(context, listOf(song)) },
            onDelete = { deleteSongs(listOf(song)) },
            onRemoveFromPlaylist = if (playlistId != null) {
                { app.playlists.removeSong(playlistId, song.id) }
            } else {
                null
            },
            onDismiss = { optionsFor = null },
        )
    }

    detailsFor?.let { song ->
        SongDetailsSheet(song = song, plays = plays[song.id], onDismiss = { detailsFor = null })
    }

    addToPlaylist?.let { picked ->
        AddToPlaylistDialog(
            playlists = playlists,
            onPick = { id ->
                picked.forEach { app.playlists.addSong(id, it.id) }
                selection.clear()
            },
            onNew = {
                addToPlaylist = null
                newPlaylist = NewPlaylistRequest(picked)
            },
            onDismiss = { addToPlaylist = null },
        )
    }

    newPlaylist?.let { request ->
        PlaylistNameDialog(
            title = "New playlist",
            initialName = "",
            confirmLabel = "Create",
            onConfirm = { name ->
                app.playlists.create(name, request.songs.map { it.id })
                selection.clear()
            },
            onDismiss = { newPlaylist = null },
        )
    }
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Text("Your music stays on your phone", style = MaterialTheme.typography.headlineSmall)
        Text(
            "MU51C needs permission to read the audio files on this phone. " +
                "It has no internet access.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
        Button(onClick = onAllow) { Text("Allow access to music") }
    }
}
