package com.suna.player.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long>,
)

/**
 * Playlists, kept in one small private file. Every change runs in order on a single background
 * thread, after the initial load, so changes can never be lost or overwrite each other.
 */
class PlaylistStore(context: Context) {

    private val file = File(context.filesDir, "playlists.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    init {
        scope.launch { _playlists.value = read() }
    }

    fun create(name: String, songIds: List<Long> = emptyList()) = change { list ->
        val id = maxOf(System.currentTimeMillis(), (list.maxOfOrNull { it.id } ?: 0L) + 1)
        list + Playlist(id, cleanName(name), songIds.distinct())
    }

    fun rename(id: Long, name: String) = change { list ->
        list.map { if (it.id == id) it.copy(name = cleanName(name)) else it }
    }

    fun delete(id: Long) = change { list -> list.filterNot { it.id == id } }

    /** A song appears at most once in a playlist. */
    fun addSong(id: Long, songId: Long) = change { list ->
        list.map {
            if (it.id == id && songId !in it.songIds) it.copy(songIds = it.songIds + songId) else it
        }
    }

    fun removeSong(id: Long, songId: Long) = change { list ->
        list.map { if (it.id == id) it.copy(songIds = it.songIds - songId) else it }
    }

    private fun cleanName(name: String) = name.trim().ifEmpty { "New playlist" }

    private fun change(transform: (List<Playlist>) -> List<Playlist>) {
        scope.launch {
            val next = transform(_playlists.value)
            _playlists.value = next
            write(next)
        }
    }

    private fun read(): List<Playlist> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                val ids = o.getJSONArray("songs")
                Playlist(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    songIds = List(ids.length()) { ids.getLong(it) },
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun write(playlists: List<Playlist>) {
        val array = JSONArray()
        for (p in playlists) {
            array.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("songs", JSONArray(p.songIds)),
            )
        }
        file.writeAtomically(array.toString())
    }
}
