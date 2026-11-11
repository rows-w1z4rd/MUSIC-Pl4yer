package com.suna.player.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long>,
)

/**
 * Playlists, stored in the app's database. Changes run one at a time on a background thread,
 * so they can never overwrite each other.
 */
class PlaylistStore(private val context: Context, private val db: SunaDb) {

    private val dao = db.playlists()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    val playlists: StateFlow<List<Playlist>> =
        combine(dao.playlists(), dao.songs()) { lists, rows ->
            val byPlaylist = rows.groupBy({ it.playlistId }, { it.songId })
            lists.map { Playlist(it.id, it.name, byPlaylist[it.id].orEmpty()) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch { importOldFile() }
    }

    fun create(name: String, songIds: List<Long> = emptyList()) = scope.launch {
        val id = maxOf(System.currentTimeMillis(), (dao.maxId() ?: 0L) + 1)
        dao.create(id, cleanName(name), songIds)
    }

    fun rename(id: Long, name: String) = scope.launch { dao.rename(id, cleanName(name)) }

    fun delete(id: Long) = scope.launch { dao.delete(id) }

    /** A song appears at most once in a playlist. */
    fun addSong(id: Long, songId: Long) = scope.launch { dao.append(id, songId) }

    fun removeSong(id: Long, songId: Long) = scope.launch { dao.removeSong(id, songId) }

    private fun cleanName(name: String) = name.trim().ifEmpty { "New playlist" }

    /** Version 0.1.x kept playlists in a JSON file; move them into the database once. */
    private suspend fun importOldFile() {
        val file = File(context.filesDir, "playlists.json")
        if (!file.exists()) return
        try {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val ids = o.getJSONArray("songs")
                dao.create(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    songIds = List(ids.length()) { ids.getLong(it) },
                )
            }
            file.renameTo(File(context.filesDir, "playlists.json.imported"))
        } catch (_: Exception) {
            // Leave the file in place so nothing is lost; the import is retried next launch.
        }
    }
}

/** Play counts and last-played times, read by the library screens. */
class StatsStore(db: SunaDb) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Song id → its plays. Songs never played are absent. */
    val plays: StateFlow<Map<Long, SongPlays>> =
        db.stats().all()
            .map { rows -> rows.associateBy { it.songId } }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())
}
