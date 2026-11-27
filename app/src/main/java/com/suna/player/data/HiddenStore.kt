package com.suna.player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks the user chose to hide. They stay on the phone and in playlists, but leave the
 * library, search and the smart lists, and are never picked by shuffle or endless play.
 * Kept by the track's id in Android's media index, which stays the same across restarts.
 */
class HiddenStore(context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(load())
    val ids: StateFlow<Set<Long>> = _ids.asStateFlow()

    fun hide(songIds: Collection<Long>) = save(_ids.value + songIds)

    fun unhide(songId: Long) = save(_ids.value - songId)

    private fun load(): Set<Long> =
        prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    private fun save(ids: Set<Long>) {
        _ids.value = ids
        prefs.edit().putStringSet(KEY, ids.map { it.toString() }.toSet()).apply()
    }

    private companion object {
        const val KEY = "hidden_ids"
    }
}
