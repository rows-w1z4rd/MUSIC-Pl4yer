package com.suna.player.ui.common

import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.suna.player.data.Song

/**
 * Picking several songs at once: started by long-pressing a song (or "Select" in the menu),
 * ended by the close button, Back, or an action. [scope] is the list the songs are picked
 * from, so "All" and the play order follow what is on screen.
 */
@Stable
class SongSelection {
    var active by mutableStateOf(false)
        private set
    var ids by mutableStateOf(emptySet<Long>())
        private set
    var scope by mutableStateOf(emptyList<Song>())
        private set

    /** Set when editing a playlist: the bar then offers Remove (from it) instead of Delete. */
    var playlistId by mutableStateOf<Long?>(null)
        private set

    val count: Int get() = ids.size
    val allSelected: Boolean get() = scope.isNotEmpty() && ids.size == scope.size

    /** The picked songs, in the order they appear on screen. */
    val selected: List<Song> get() = scope.filter { it.id in ids }

    fun start(songs: List<Song>, first: Song?, playlistId: Long? = null) {
        scope = songs
        ids = setOfNotNull(first?.id)
        this.playlistId = playlistId
        active = true
    }

    /** Started from the menu: the list on screen claims it with [adopt]. */
    fun startEmpty() = start(emptyList(), null)

    fun adopt(songs: List<Song>) {
        if (active && scope.isEmpty()) scope = songs
    }

    fun toggle(song: Song) {
        ids = if (song.id in ids) ids - song.id else ids + song.id
    }

    fun toggleAll() {
        ids = if (allSelected) emptySet() else scope.mapTo(HashSet()) { it.id }
    }

    fun clear() {
        active = false
        ids = emptySet()
        scope = emptyList()
        playlistId = null
    }
}

val LocalSongSelection = staticCompositionLocalOf<SongSelection?> { null }

/** What a long press on a song does: gets the song, its list, and the row's place on screen. */
val LocalSongLongPress = staticCompositionLocalOf<((Song, List<Song>, Rect) -> Unit)?> { null }
