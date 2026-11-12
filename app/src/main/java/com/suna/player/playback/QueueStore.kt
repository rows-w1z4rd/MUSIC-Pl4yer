package com.suna.player.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.suna.player.data.writeAtomically
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Remembers the queue and where playback stopped, so reopening the app continues from there. */
class QueueStore(context: Context) {

    class Saved(val items: List<MediaItem>, val index: Int, val positionMs: Long)

    private val file = File(context.filesDir, "queue.json")
    private val prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)

    var repeatMode: Int
        get() = prefs.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF)
        set(value) = prefs.edit().putInt(KEY_REPEAT, value).apply()

    var shuffle: Boolean
        get() = prefs.getBoolean(KEY_SHUFFLE, false)
        set(value) = prefs.edit().putBoolean(KEY_SHUFFLE, value).apply()

    fun savePosition(index: Int, positionMs: Long) {
        prefs.edit().putInt(KEY_INDEX, index).putLong(KEY_POSITION, positionMs).apply()
    }

    /** Call off the main thread with a snapshot of the queue. */
    fun saveQueue(items: List<MediaItem>) {
        val array = JSONArray()
        for (item in items) {
            val uri = item.playableUri() ?: continue
            val meta = item.mediaMetadata
            array.put(
                JSONObject()
                    .put("id", item.mediaId)
                    .put("uri", uri.toString())
                    .put("title", meta.title?.toString() ?: "")
                    .put("artist", meta.artist?.toString() ?: "")
                    .put("album", meta.albumTitle?.toString() ?: "")
                    .put("albumId", item.albumId())
                    .put("playNext", item.isPlayNext()),
            )
        }
        file.writeAtomically(array.toString())
    }

    /** Call off the main thread. */
    fun load(): Saved? {
        if (!file.exists()) return null
        return try {
            val array = JSONArray(file.readText())
            val items = List(array.length()) { i ->
                val o = array.getJSONObject(i)
                buildMediaItem(
                    mediaId = o.getString("id"),
                    uri = Uri.parse(o.getString("uri")),
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    album = o.optString("album"),
                    albumId = o.optLong("albumId", -1L),
                    playNext = o.optBoolean("playNext", false),
                )
            }
            Saved(items, prefs.getInt(KEY_INDEX, 0), prefs.getLong(KEY_POSITION, 0L))
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val KEY_INDEX = "index"
        const val KEY_POSITION = "position"
        const val KEY_REPEAT = "repeat"
        const val KEY_SHUFFLE = "shuffle"
    }
}
