package com.suna.player.ui.library

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suna.player.data.Song
import com.suna.player.data.toAlbums
import com.suna.player.data.toArtists
import com.suna.player.ui.common.LocalMiniPlayerSpace
import com.suna.player.ui.common.LocalSongLongPress
import com.suna.player.ui.common.Panel
import com.suna.player.ui.common.SimpleRow
import com.suna.player.ui.common.SongRow
import com.suna.player.ui.common.songCount
import org.json.JSONArray

/**
 * Search, as Samsung Music lays it out: a back arrow and a big search field (with voice input)
 * across the top, recent searches below until you type, then tracks, artists and albums.
 */
@Composable
fun SearchScreen(
    songs: List<Song>,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onSongOptions: (Song) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val recents = remember { RecentSearches(context) }
    var recentList by remember { mutableStateOf(recents.load()) }
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val saveQuery: () -> Unit = {
        if (query.isNotBlank()) recentList = recents.add(query)
    }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !heard.isNullOrBlank()) {
            query = heard
            recentList = recents.add(heard)
        }
    }
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a track, artist or album")
    }
    val canListen = remember { voiceIntent.resolveActivity(context.packageManager) != null }

    val q = query.trim()
    val tracks = remember(songs, q) {
        if (q.isEmpty()) emptyList() else songs.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }
    }
    val artists = remember(songs, q) { if (q.isEmpty()) emptyList() else songs.toArtists().filter { it.name.contains(q, true) } }
    val albums = remember(songs, q) { if (q.isEmpty()) emptyList() else songs.toAlbums().filter { it.title.contains(q, true) } }
    val longPress = LocalSongLongPress.current
    val bottom = LocalMiniPlayerSpace.current + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Box(Modifier.weight(1f).padding(start = 8.dp)) {
                if (query.isEmpty()) Text("Search", fontSize = 24.sp, color = colors.onSurfaceVariant)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 24.sp, color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { saveQuery() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Clear") }
            } else if (canListen) {
                IconButton(onClick = { voice.launch(voiceIntent) }) { Icon(Icons.Rounded.Mic, contentDescription = "Search by voice") }
            }
        }

        if (q.isEmpty()) {
            if (recentList.isEmpty()) {
                Text(
                    "Search your tracks, artists and albums",
                    color = colors.onSurface.copy(alpha = 0.8f),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            } else {
                Text(
                    "Recent searches",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 24.dp, top = 10.dp, bottom = 10.dp),
                )
                Panel(Modifier.weight(1f)) {
                    LazyColumn(contentPadding = PaddingValues(bottom = bottom + 8.dp)) {
                        items(recentList, key = { it }) { text ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { query = text }.padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                            ) {
                                Text(text, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                IconButton(onClick = { recentList = recents.remove(text) }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove \"$text\" from recent searches")
                                }
                            }
                        }
                    }
                }
            }
        } else if (tracks.isEmpty() && artists.isEmpty() && albums.isEmpty()) {
            Text("No results for \"$q\"", color = colors.onSurfaceVariant, fontSize = 15.sp, modifier = Modifier.padding(24.dp))
        } else {
            Panel(Modifier.weight(1f)) {
                LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = bottom + 8.dp)) {
                    if (tracks.isNotEmpty()) {
                        item(key = "h-tracks") { SectionTitle("Tracks", tracks.size) }
                        items(tracks, key = { "t${it.id}" }) { song ->
                            SongRow(
                                song = song,
                                onClick = { saveQuery(); onPlay(tracks, tracks.indexOf(song)) },
                                onPlayNext = { onPlayNext(song) },
                                onOptions = { onSongOptions(song) },
                                onLongClick = longPress?.let { press -> { rect -> press(song, tracks, rect) } },
                            )
                        }
                    }
                    if (artists.isNotEmpty()) {
                        item(key = "h-artists") { SectionTitle("Artists", artists.size) }
                        items(artists, key = { "a${it.id}" }) { artist ->
                            SimpleRow(artist.name, songCount(artist.songCount), onClick = { saveQuery(); onOpenArtist(artist.id) }) {
                                Icon(Icons.Rounded.Person, contentDescription = null)
                            }
                        }
                    }
                    if (albums.isNotEmpty()) {
                        item(key = "h-albums") { SectionTitle("Albums", albums.size) }
                        items(albums, key = { "b${it.id}" }) { album ->
                            SimpleRow(album.title, album.artist, onClick = { saveQuery(); onOpenAlbum(album.id) }) {
                                Icon(Icons.Rounded.Album, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 6.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("  $count", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The last ten searches, newest first, kept on the phone only. */
private class RecentSearches(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun load(): List<String> = try {
        val a = JSONArray(prefs.getString(KEY, "[]"))
        List(a.length()) { a.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }

    fun add(text: String): List<String> {
        val t = text.trim()
        return save(listOf(t) + load().filterNot { it.equals(t, ignoreCase = true) })
    }

    fun remove(text: String): List<String> = save(load() - text)

    private fun save(list: List<String>): List<String> {
        val kept = list.take(10)
        prefs.edit().putString(KEY, JSONArray(kept).toString()).apply()
        return kept
    }

    private companion object {
        const val KEY = "recent_searches"
    }
}
