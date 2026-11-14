package com.suna.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
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
    val favorite: Boolean = false,
    /** A picture the user chose for it (a file in the app's storage), or null. */
    val cover: Uri? = null,
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
            lists.map {
                Playlist(
                    id = it.id,
                    name = it.name,
                    songIds = byPlaylist[it.id].orEmpty(),
                    favorite = it.favorite,
                    cover = it.cover?.let { path -> Uri.fromFile(File(path)) },
                )
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch { importOldFile() }
    }

    fun create(name: String, songIds: List<Long> = emptyList()) = scope.launch {
        val id = maxOf(System.currentTimeMillis(), (dao.maxId() ?: 0L) + 1)
        dao.create(id, cleanName(name), songIds)
    }

    fun rename(id: Long, name: String) = scope.launch { dao.rename(id, cleanName(name)) }

    fun delete(id: Long) = scope.launch {
        dao.cover(id)?.let { File(it).delete() }
        dao.delete(id)
    }

    fun setFavorite(id: Long, favorite: Boolean) = scope.launch { dao.setFavorite(id, favorite) }

    /** Puts the songs in exactly this order. */
    fun reorder(id: Long, songIds: List<Long>) = scope.launch { dao.reorder(id, songIds) }

    fun removeSongs(id: Long, songIds: Collection<Long>) = scope.launch {
        songIds.forEach { dao.removeSong(id, it) }
    }

    /**
     * Copies the picked picture into the app's storage (shrunk, so it stays small), so the cover
     * keeps working even if the original is moved or deleted. Each copy gets a new name, so the
     * screens show the new picture instead of a cached old one. Null removes the cover.
     */
    fun setCover(id: Long, picked: Uri?) = scope.launch {
        val old = dao.cover(id)
        val saved = picked?.let { copyCover(id, it) }
        if (picked != null && saved == null) return@launch // couldn't read it: keep the old one
        dao.setCover(id, saved)
        old?.let { File(it).delete() }
    }

    private fun copyCover(id: Long, uri: Uri): String? = try {
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val file = File(dir, "$id-${System.currentTimeMillis()}.jpg")
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = minOf(1f, COVER_SIDE.toFloat() / maxOf(info.size.width, info.size.height))
            d.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        file.absolutePath
    } catch (_: Exception) {
        null
    }

    /** A song appears at most once in a playlist. */
    fun addSong(id: Long, songId: Long) = scope.launch { dao.append(id, songId) }

    fun removeSong(id: Long, songId: Long) = scope.launch { dao.removeSong(id, songId) }

    private fun cleanName(name: String) = name.trim().ifEmpty { nextName(playlists.value) }

    companion object {
        private const val COVER_SIDE = 720

        /** Samsung-style default name: "Playlist 001", or the next number not yet taken. */
        fun nextName(existing: List<Playlist>): String {
            val taken = existing.mapNotNull { Regex("""Playlist (\d+)""").matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }.toSet()
            val n = generateSequence(1) { it + 1 }.first { it !in taken }
            return "Playlist %03d".format(n)
        }
    }

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

/** Hearted songs. */
class FavoritesStore(db: SunaDb) {
    private val dao = db.favorites()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** Song ids, newest first. */
    val ids: StateFlow<List<Long>> = dao.all().stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun set(songId: Long, favorite: Boolean) = scope.launch {
        if (favorite) dao.add(songId) else dao.remove(songId)
    }

    fun toggle(songId: Long) = set(songId, songId !in ids.value)
}
