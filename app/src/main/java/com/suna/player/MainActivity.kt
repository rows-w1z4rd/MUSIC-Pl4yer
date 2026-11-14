package com.suna.player

import com.suna.player.ui.library.EditTrackScreen
import com.suna.player.ui.library.PlaylistSelectionTopBar
import com.suna.player.ui.library.PlaylistSelectionBottomBar
import com.suna.player.ui.library.PlaylistSelection
import com.suna.player.data.LibraryTab
import androidx.core.graphics.drawable.IconCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.pm.ShortcutInfoCompat
import android.widget.Toast
import android.media.audiofx.AudioEffect
import android.content.Intent
import android.content.Context
import kotlinx.coroutines.delay
import com.suna.player.ui.library.playlistCover
import com.suna.player.ui.library.SmartList
import com.suna.player.ui.library.SearchScreen
import com.suna.player.ui.library.PlaylistScreen
import com.suna.player.ui.library.AddTracksScreen
import com.suna.player.data.PlaylistStore
import androidx.activity.result.PickVisualMediaRequest
import com.suna.player.ui.common.NoticeHost
import com.suna.player.ui.common.PageSlide
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
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
        intent?.getStringExtra(EXTRA_OPEN_TAB)?.let { tab ->
            getSharedPreferences("ui", MODE_PRIVATE).edit().putString("last_tab", tab).apply()
        }
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
    const val WALLPAPER_NEW = "wallpaper/new"
    const val ALBUM = "album/{id}"
    const val ARTIST = "artist/{id}"
    const val PLAYLIST = "playlist/{id}"
    const val FOLDER = "folder/{path}"
    const val PLAYLIST_ADD = "playlist/{id}/add"
    const val SEARCH = "search"
    const val EDIT_TRACK = "edit/{id}"
    const val SMART = "smart/{name}"
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
    // Bounded, so a fast flick settles exactly at open or closed instead of carrying the
    // sheet past the top of the screen and springing back.
    val expansion = remember { Animatable(0f).apply { updateBounds(0f, 1f) } }
    // The pages are the backdrop the glass player bar frosts.
    val haze = rememberHazeState()
    val current = playerState.current
    val selection = remember { SongSelection() }
    val playlistSelection = remember { PlaylistSelection() }
    val picking = selection.active || playlistSelection.active
    val showPlayer = current != null && route != Routes.QUEUE && route != Routes.WALLPAPER &&
        route != Routes.WALLPAPER_NEW && !picking
    var deletingPlaylists by remember { mutableStateOf<List<Long>?>(null) }
    var renamingPlaylist by remember { mutableStateOf<Long?>(null) }
    val soundEffects = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val effectsIntent = remember {
        Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
    }
    val hasEffects = remember { effectsIntent.resolveActivity(context.packageManager) != null }
    val songsOf: (List<Long>) -> List<Song> = { ids ->
        ids.mapNotNull { id -> playlists.find { it.id == id } }.flatMap { it.songIds }.mapNotNull { songsById[it] }
    }
    val deleteSongs = rememberDeleteSongs(onDeleted = selection::clear)
    val playNext: (Song) -> Unit = { player.playNext(listOf(it)) }
    val openSongOptions: (Song) -> Unit = { optionsFor = OptionsTarget(it, playlistId = null) }

    LaunchedEffect(showPlayer) { if (!showPlayer) expansion.snapTo(0f) }
    // Picking songs also ends when leaving the page it started on.
    LaunchedEffect(route) {
        selection.clear()
        playlistSelection.clear()
    }

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalMiniPlayerSpace provides if (showPlayer || picking) MiniPlayerSpace else 0.dp,
            LocalSongSelection provides selection,
            LocalSongLongPress provides onLongPress,
        ) {
            // Pages slide side by side, never fading, and never overlap, so the wallpaper stays
            // still behind them.
            NavHost(
                navController = nav,
                startDestination = Routes.LIBRARY,
                modifier = Modifier.fillMaxSize().hazeSource(haze),
                enterTransition = { slideInHorizontally(PageSlide) { it } },
                exitTransition = { slideOutHorizontally(PageSlide) { -it / 4 } },
                popEnterTransition = { slideInHorizontally(PageSlide) { -it / 4 } },
                popExitTransition = { slideOutHorizontally(PageSlide) { it } },
            ) {
                composable(Routes.LIBRARY) {
                    AppBackground(wallpaper, appearance) {
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
                            onOpenSearch = { nav.navigate(Routes.SEARCH) { launchSingleTop = true } },
                            onOpenSmart = { nav.navigate("smart/${it.name}") },
                            playlistSelection = playlistSelection,
                            onAddShortcut = { tab -> addTabShortcut(context, tab) },
                            onSoundEffects = if (hasEffects) {
                                {
                                    soundEffects.launch(
                                        Intent(effectsIntent).putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
                composable(
                    Routes.ALBUM,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: -1L
                    val albumSongs = remember(songs, id) {
                        songs.filter { it.albumId == id }.sortedBy { it.track }
                    }
                    AppBackground(wallpaper, appearance) {
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
                }
                composable(
                    Routes.ARTIST,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: -1L
                    val artistSongs = remember(songs, id) { songs.filter { it.artistId == id } }
                    AppBackground(wallpaper, appearance) {
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
                }
                composable(
                    Routes.FOLDER,
                    arguments = listOf(navArgument("path") { type = NavType.StringType }),
                ) { entry ->
                    val path = entry.arguments?.getString("path").orEmpty()
                    val folderSongs = remember(songs, path) { songs.filter { it.folder == path } }
                    AppBackground(wallpaper, appearance) {
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

                    // While a row is being dragged, the screen shows this order; it is written to
                    // the database and dropped again once the database has caught up.
                    var dragOrder by remember { mutableStateOf<List<Long>?>(null) }
                    LaunchedEffect(playlist?.songIds) {
                        if (dragOrder == playlist?.songIds) dragOrder = null
                    }
                    val shownSongs = remember(dragOrder, playlistSongs, songsById) {
                        dragOrder?.mapNotNull { songsById[it] } ?: playlistSongs
                    }
                    LaunchedEffect(dragOrder) {
                        val order = dragOrder ?: return@LaunchedEffect
                        delay(350)
                        app.playlists.reorder(id, order)
                    }
                    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                        if (uri != null) app.playlists.setCover(id, uri)
                    }

                    AppBackground(wallpaper, appearance) {
                        if (playlist != null) {
                            PlaylistScreen(
                                playlist = playlist,
                                songs = shownSongs,
                                cover = playlistCover(playlist, songsById),
                                onBack = { nav.popBackStack() },
                                onPlay = { player.play(shownSongs, it) },
                                onShuffle = { player.shuffleAll(shownSongs) },
                                onPlayNext = playNext,
                                onSongOptions = { optionsFor = OptionsTarget(it, playlistId = id) },
                                onToggleFavorite = { app.playlists.setFavorite(id, !playlist.favorite) },
                                onAddTracks = { nav.navigate("playlist/$id/add") },
                                onEdit = { selection.start(shownSongs, null, playlistId = id) },
                                onRename = { renaming = true },
                                onChangeCover = {
                                    pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                onRemoveCover = if (playlist.cover != null) ({ app.playlists.setCover(id, null) }) else null,
                                onDelete = { deleting = true },
                                onMove = { from, to ->
                                    val order = (dragOrder ?: shownSongs.map { it.id }).toMutableList()
                                    if (from in order.indices && to in order.indices) {
                                        order.add(to, order.removeAt(from))
                                        dragOrder = order
                                    }
                                },
                            )
                        }
                    }

                    if (renaming && playlist != null) {
                        PlaylistNameDialog(
                            title = "Rename",
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
                composable(
                    Routes.PLAYLIST_ADD,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: -1L
                    val playlist = playlists.find { it.id == id }
                    AppBackground(wallpaper, appearance) {
                        AddTracksScreen(
                            songs = songs,
                            alreadyIn = playlist?.songIds?.toSet().orEmpty(),
                            onBack = { nav.popBackStack() },
                            onDone = { picked ->
                                picked.forEach { app.playlists.addSong(id, it.id) }
                                nav.popBackStack()
                            },
                        )
                    }
                }
                composable(
                    Routes.EDIT_TRACK,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: -1L
                    // Keeps the song it was opened for, even while the library reloads after saving.
                    val song = remember(id) { songsById[id] }
                    AppBackground(wallpaper, appearance) {
                        if (song != null) {
                            EditTrackScreen(
                                song = song,
                                editor = app.tagEditor,
                                onDone = { saved ->
                                    nav.popBackStack()
                                    if (saved) {
                                        player.notify("Track info saved")
                                        scope.launch { app.lyrics.forget(song.id) }
                                    }
                                },
                            )
                        }
                    }
                }
                composable(Routes.SEARCH) {
                    AppBackground(wallpaper, appearance) {
                        SearchScreen(
                            songs = songs,
                            onBack = { nav.popBackStack() },
                            onPlay = { list, index -> player.play(list, index) },
                            onPlayNext = playNext,
                            onSongOptions = openSongOptions,
                            onOpenAlbum = { nav.navigate("album/$it") },
                            onOpenArtist = { nav.navigate("artist/$it") },
                        )
                    }
                }
                composable(
                    Routes.SMART,
                    arguments = listOf(navArgument("name") { type = NavType.StringType }),
                ) { entry ->
                    val list = SmartList.entries.find { it.name == entry.arguments?.getString("name") } ?: SmartList.RECENTLY_ADDED
                    val listSongs = remember(songs, plays, list) { list.songsFor(songs, plays) }
                    AppBackground(wallpaper, appearance) {
                        SongListScreen(
                            title = list.label,
                            songs = listSongs,
                            onBack = { nav.popBackStack() },
                            onPlay = { player.play(listSongs, it) },
                            onShuffle = { player.shuffleAll(listSongs) },
                            onPlayNext = playNext,
                            onSongOptions = openSongOptions,
                        )
                    }
                }
                composable(Routes.QUEUE) {
                    AppBackground(wallpaper, appearance) {
                        QueueScreen(
                            state = playerState,
                            onBack = { nav.popBackStack() },
                            onPlayIndex = player::playQueueItem,
                            onRemove = player::removeFromQueue,
                        )
                    }
                }
                composable(Routes.SETTINGS) {
                    AppBackground(wallpaper, appearance) {
                        SettingsScreen(
                            appearance = appearance,
                            skipSilence = skipSilence,
                            wallpaper = wallpaper,
                            settings = app.settings,
                            wallpapers = app.wallpapers,
                            onBack = { nav.popBackStack() },
                            onWallpaperPreview = { newPicture ->
                                nav.navigate(if (newPicture) Routes.WALLPAPER_NEW else Routes.WALLPAPER) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                }
                for (newPicture in listOf(false, true)) {
                    composable(if (newPicture) Routes.WALLPAPER_NEW else Routes.WALLPAPER) {
                        val songs by app.library.songs.collectAsStateWithLifecycle()
                        WallpaperEditor(
                            newPicture = newPicture,
                            appearance = appearance,
                            wallpapers = app.wallpapers,
                            songs = songs,
                            onApply = { scale, x, y, blur, dim, edges ->
                                if (!newPicture || app.wallpapers.commitDraft()) {
                                    app.settings.applyWallpaper(newPicture, scale, x, y, blur, dim, edges)
                                }
                            },
                            onClose = { nav.popBackStack() },
                        )
                    }
                }
            }
        }

        // Like Samsung's own apps, a back swipe doesn't drag the page: it stays still while the
        // swipe is held (only the system's back arrow shows), and slides away once the finger
        // is let go. Declared after the NavHost, so it answers Back instead of the NavHost's
        // own finger-following handler; pages that handle Back themselves (inside Settings,
        // the wallpaper preview) are added later and still come first.
        BackHandler(enabled = route != null && route != Routes.LIBRARY) { nav.popBackStack() }

        // Declared after the NavHost so they get Back before page navigation does: Back first
        // closes the full screen player or ends picking songs, then leaves the page.
        BackHandler(enabled = showPlayer && expansion.targetValue > 0f) {
            scope.launch { expansion.collapse() }
        }
        BackHandler(enabled = selection.active) { selection.clear() }
        BackHandler(enabled = playlistSelection.active) { playlistSelection.clear() }

        if (showPlayer) {
            PlayerSheet(
                state = playerState,
                player = player,
                expansion = expansion,
                haze = haze,
                onMore = current.mediaId.toLongOrNull()
                    ?.let { songsById[it] }
                    ?.let { song -> { optionsFor = OptionsTarget(song, playlistId = null) } },
                onAddToPlaylist = current.mediaId.toLongOrNull()
                    ?.let { songsById[it] }
                    ?.let { song -> { addToPlaylist = listOf(song) } },
                isFavorite = current?.mediaId?.toLongOrNull() in favoriteSet,
                onToggleFavorite = current?.mediaId?.toLongOrNull()?.let { id -> { app.favorites.toggle(id) } },
                loadLyrics = { mediaId -> mediaId.toLongOrNull()?.let { songsById[it] }?.let { app.lyrics.load(it) } },
            )
        }

        // Short messages (such as "Playing 8 tracks") float just above the mini player.
        NoticeHost(
            messages = player.notices,
            aboveBottom = if (showPlayer) MiniPlayerSpace else 0.dp,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

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
            onRemove = { playlistId, picked ->
                app.playlists.removeSongs(playlistId, picked.map { it.id })
                selection.clear()
            },
        )

        PlaylistSelectionTopBar(playlistSelection)
        PlaylistSelectionBottomBar(
            selection = playlistSelection,
            onPlay = { ids ->
                player.play(songsOf(ids), 0)
                playlistSelection.clear()
            },
            onAdd = { ids -> addToPlaylist = songsOf(ids) },
            onShare = { ids -> shareSongs(context, songsOf(ids)) },
            onDelete = { ids -> deletingPlaylists = ids },
            onRename = { id -> renamingPlaylist = id },
        )
    }

    deletingPlaylists?.let { ids ->
        val names = ids.mapNotNull { id -> playlists.find { it.id == id }?.name }
        DeletePlaylistDialog(
            name = if (names.size == 1) names[0] else "${names.size} playlists",
            onConfirm = {
                ids.forEach { app.playlists.delete(it) }
                playlistSelection.clear()
            },
            onDismiss = { deletingPlaylists = null },
        )
    }

    renamingPlaylist?.let { id ->
        playlists.find { it.id == id }?.let { playlist ->
            PlaylistNameDialog(
                title = "Rename",
                initialName = playlist.name,
                confirmLabel = "Rename",
                onConfirm = {
                    app.playlists.rename(id, it)
                    playlistSelection.clear()
                },
                onDismiss = { renamingPlaylist = null },
            )
        }
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
            onEditInfo = {
                scope.launch { expansion.snapTo(0f) }
                nav.navigate("edit/${song.id}")
            },
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
        SongDetailsSheet(
            song = song,
            plays = plays[song.id],
            onDismiss = { detailsFor = null },
            onEdit = {
                detailsFor = null
                scope.launch { expansion.snapTo(0f) }
                nav.navigate("edit/${song.id}")
            },
        )
    }

    addToPlaylist?.let { picked ->
        AddToPlaylistDialog(
            playlists = playlists,
            onPick = { id ->
                picked.forEach { app.playlists.addSong(id, it.id) }
                selection.clear()
                playlistSelection.clear()
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
            title = "Create playlist",
            initialName = PlaylistStore.nextName(playlists),
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

/** Extra on the launch intent: which library tab to open on (from a home screen shortcut). */
private const val EXTRA_OPEN_TAB = "com.suna.player.OPEN_TAB"

/**
 * Asks the home screen to pin a shortcut that opens the app on [tab], as Samsung Music's
 * "Add shortcut on Home screen" does. The launcher shows its own confirmation.
 */
private fun addTabShortcut(context: Context, tab: LibraryTab) {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
        Toast.makeText(context, "Your home screen doesn't support shortcuts", Toast.LENGTH_SHORT).show()
        return
    }
    val open = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .putExtra(EXTRA_OPEN_TAB, tab.name)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    val shortcut = ShortcutInfoCompat.Builder(context, "tab-${tab.name.lowercase()}")
        .setShortLabel(tab.label)
        .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
        .setIntent(open)
        .build()
    ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
}
