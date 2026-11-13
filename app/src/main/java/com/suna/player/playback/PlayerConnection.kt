package com.suna.player.playback

import androidx.media3.session.SessionCommand
import android.os.Bundle
import android.widget.Toast
import java.text.NumberFormat
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.suna.player.data.Song
import com.suna.player.data.albumArtUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QueueEntry(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artUri: Uri?,
    val playNext: Boolean,
)

data class PlayerUiState(
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
    /** Extra plays left of the current track, from holding the play button. */
    val repeatsLeft: Int = 0,
) {
    val current: QueueEntry? get() = queue.getOrNull(currentIndex)
}

/**
 * The UI's handle on the playback service. Connected while the app is on screen and released
 * when it is not, so nothing stays bound in the background.
 */
class PlayerConnection(private val context: Context) {

    companion object {
        /** Start index meaning "the play button above a list": see [play]. */
        const val PLAY_ALL = -1
    }

    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player, rebuildQueue = events.contains(Player.EVENT_TIMELINE_CHANGED))
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val pending = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                    publish(controller, rebuildQueue = false)
                }
            })
            .buildAsync()
        future = pending
        pending.addListener(
            {
                if (future !== pending) return@addListener
                try {
                    val c = pending.get()
                    controller = c
                    c.addListener(listener)
                    publish(c, rebuildQueue = true)
                } catch (_: Exception) {
                    future = null
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun release() {
        controller?.removeListener(listener)
        controller = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }

    private fun publish(player: Player, rebuildQueue: Boolean) {
        val queue = if (rebuildQueue) {
            List(player.mediaItemCount) { player.getMediaItemAt(it).toEntry() }
        } else {
            _state.value.queue
        }
        _state.value = PlayerUiState(
            queue = queue,
            currentIndex = if (queue.isEmpty()) -1 else player.currentMediaItemIndex,
            isPlaying = player.isPlaying,
            durationMs = player.duration.coerceAtLeast(0),
            repeatMode = player.repeatMode,
            shuffle = player.shuffleModeEnabled,
            repeatsLeft = (player as? MediaController)?.sessionExtras
                ?.getInt(PlaybackService.EXTRA_REPEATS_LEFT) ?: 0,
        )
    }

    // --- Starting playback ---

    /**
     * Plays [songs] from [startIndex]. With [PLAY_ALL] (the play button above a list) it works
     * like Samsung's: with shuffle on it starts on a random track, so every press brings a new
     * song, and with shuffle off it starts at the top; a short message says how many tracks.
     */
    fun play(songs: List<Song>, startIndex: Int) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        val start = when {
            startIndex != PLAY_ALL -> startIndex.coerceIn(0, songs.lastIndex)
            c.shuffleModeEnabled -> songs.indices.random()
            else -> 0
        }
        c.setMediaItems(songs.map { it.toMediaItem() }, start, 0L)
        c.prepare()
        c.play()
        if (startIndex == PLAY_ALL) {
            val count = NumberFormat.getIntegerInstance().format(songs.size)
            val what = if (songs.size == 1) "1 track" else "$count tracks"
            val message = if (c.shuffleModeEnabled) "Playing $what in shuffle" else "Playing $what"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun shuffleAll(songs: List<Song>) = play(songs.shuffled(), 0)

    // --- Queue ---

    fun playNext(songs: List<Song>) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        if (c.mediaItemCount == 0) {
            play(songs, 0)
            return
        }
        val index = playNextInsertIndex(c.currentMediaItemIndex, c.mediaItemCount) {
            c.getMediaItemAt(it).isPlayNext()
        }
        c.addMediaItems(index, songs.map { it.toMediaItem(playNext = true) })
    }

    fun addToQueue(songs: List<Song>) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        if (c.mediaItemCount == 0) {
            play(songs, 0)
            return
        }
        c.addMediaItems(songs.map { it.toMediaItem() })
    }

    fun playQueueItem(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekTo(index, 0L)
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        val range = 0 until c.mediaItemCount
        if (from in range && to in range && from != to) c.moveMediaItem(from, to)
    }

    /** Reshuffles everything that comes after the current song. */
    fun shuffleUpcoming() {
        val c = controller ?: return
        val from = c.currentMediaItemIndex + 1
        val count = c.mediaItemCount
        if (count - from < 2) return
        val shuffled = (from until count).map { c.getMediaItemAt(it).withPlayNext(false) }.shuffled()
        c.replaceMediaItems(from, count, shuffled)
    }

    // --- Transport ---

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
            return
        }
        when (c.playbackState) {
            // A restored queue, or one stopped by an error, has to be prepared first.
            Player.STATE_IDLE -> c.prepare()
            // The queue finished: start again from the top.
            Player.STATE_ENDED -> c.seekTo(0, 0L)
            else -> Unit
        }
        c.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun positionMs(): Long = controller?.currentPosition?.coerceAtLeast(0) ?: 0L

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    /** Plays the current track [times] more times (1 to 3), starting playback if paused. */
    fun repeatCurrent(times: Int) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        c.sendCustomCommand(
            SessionCommand(PlaybackService.COMMAND_REPEAT_TIMES, Bundle.EMPTY),
            Bundle().apply { putInt(PlaybackService.EXTRA_TIMES, times) },
        )
        if (!c.isPlaying) togglePlay()
    }

    /** Jumps forward or back within the track, for holding the next and previous buttons. */
    fun nudge(deltaMs: Long) {
        val c = controller ?: return
        val duration = c.duration.takeIf { it > 0 } ?: return
        c.seekTo((c.currentPosition + deltaMs).coerceIn(0, duration - 500))
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}

private fun MediaItem.toEntry(): QueueEntry {
    val albumId = albumId()
    return QueueEntry(
        mediaId = mediaId,
        title = mediaMetadata.title?.toString() ?: "Unknown title",
        artist = mediaMetadata.artist?.toString() ?: "Unknown artist",
        artUri = if (albumId >= 0) albumArtUri(albumId) else null,
        playNext = isPlayNext(),
    )
}
