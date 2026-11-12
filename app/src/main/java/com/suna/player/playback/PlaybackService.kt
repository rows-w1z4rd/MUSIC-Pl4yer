package com.suna.player.playback

import androidx.media3.session.SessionResult
import androidx.media3.session.SessionCommand
import android.os.Bundle
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.suna.player.MainActivity
import com.suna.player.R
import com.suna.player.SunaApp
import com.suna.player.widget.WidgetRenderer
import com.suna.player.widget.toWidgetInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the player. Media3 keeps it in the foreground (with the media notification) only while
 * music is playing, and provides lock screen, Bluetooth and headset controls.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var store: QueueStore
    private val app get() = application as SunaApp

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val io = Dispatchers.IO.limitedParallelism(1) // keeps saves in order
    private var restoreDone = false

    override fun onCreate() {
        super.onCreate()
        store = QueueStore(this)

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.repeatMode = store.repeatMode
        player.shuffleModeEnabled = store.shuffle
        player.addListener(PlayerEvents())

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // The status bar and notification show an electric guitar instead of a play symbol.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_stat_guitar)
            },
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()

        scope.launch {
            app.settings.skipSilence.collect { player.skipSilenceEnabled = it }
        }
        scope.launch {
            app.settings.audioOffload.collect { setAudioOffload(it) }
        }

        restoreQueue()
    }

    /**
     * Puts the last queue back without preparing it: nothing is decoded and no notification
     * appears until the user actually presses play.
     */
    private fun restoreQueue() {
        scope.launch {
            val saved = withContext(io) { store.load() }
            if (saved != null && saved.items.isNotEmpty() && player.mediaItemCount == 0) {
                player.setMediaItems(
                    saved.items,
                    saved.index.coerceIn(0, saved.items.lastIndex),
                    saved.positionMs.coerceAtLeast(0),
                )
            }
            restoreDone = true
            // If the user started something before the restore finished, record that instead.
            if (player.mediaItemCount > 0) saveQueue()
            updateWidget()
        }
    }

    /**
     * With offload on, the phone's audio chip decodes while the screen is off and the CPU can
     * sleep. It is a setting because some phones skip or stutter with it.
     */
    private fun setAudioOffload(on: Boolean) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setAudioOffloadPreferences(
                AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(
                        if (on) {
                            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                        } else {
                            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                        },
                    )
                    .setIsGaplessSupportRequired(true)
                    .build(),
            )
            .build()
    }

    private fun saveQueue() {
        val snapshot = List(player.mediaItemCount) { player.getMediaItemAt(it) }
        scope.launch(io) { store.saveQueue(snapshot) }
    }

    private fun savePosition() {
        if (!restoreDone) return
        store.savePosition(player.currentMediaItemIndex, player.currentPosition.coerceAtLeast(0))
    }

    /** Counts a play for the statistics and the "most played" page. */
    private fun recordPlay(item: MediaItem, listenedMs: Long) {
        val songId = item.mediaId.toLongOrNull() ?: return
        scope.launch(Dispatchers.IO) { app.db.stats().recordPlay(songId, listenedMs) }
    }

    /** Refreshes home screen widgets, if there are any. */
    private fun updateWidget() {
        val info = player.currentMediaItem?.toWidgetInfo()
        val playing = player.isPlaying
        val context = applicationContext
        scope.launch(Dispatchers.IO) { WidgetRenderer.render(context, info, playing) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the app away stops everything unless music is actually playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        savePosition()
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        savePosition()
        val info = player.currentMediaItem?.toWidgetInfo()
        val context = applicationContext
        Thread { WidgetRenderer.render(context, info, isPlaying = false) }.start()
        session?.release()
        session = null
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    private inner class PlayerEvents : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                )
            ) {
                updateWidget()
            }
            if (!restoreDone) return
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) saveQueue()
            if (events.contains(Player.EVENT_REPEAT_MODE_CHANGED)) {
                if (changingRepeat) {
                    changingRepeat = false
                } else {
                    // The listener chose a repeat mode themselves: that ends a "repeat N times".
                    stopRepeats(restore = false)
                    store.repeatMode = player.repeatMode
                }
            }
            if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                store.shuffle = player.shuffleModeEnabled
            }
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                )
            ) {
                savePosition()
            }
        }

        /**
         * A song counts as played when it finishes, or when it is left after 30 seconds or more.
         */
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            val item = oldPosition.mediaItem ?: return
            val finished = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION
            val left = oldPosition.mediaItemIndex != newPosition.mediaItemIndex
            if (repeatsLeft > 0) {
                when {
                    // One more time through the same track.
                    finished && !left -> setRepeatsLeft(repeatsLeft - 1)
                    // Skipping to another track cancels the remaining repeats.
                    left -> stopRepeats(restore = true)
                }
            }
            if (finished || (left && oldPosition.positionMs >= MIN_COUNTED_MS)) {
                recordPlay(item, oldPosition.positionMs)
            }
        }

        /** The last song of the queue ends without a transition, so it is counted here. */
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                player.currentMediaItem?.let { recordPlay(it, player.duration.coerceAtLeast(0)) }
            }
        }

        /** A file that was deleted or cannot be decoded is skipped instead of stopping. */
        override fun onPlayerError(error: PlaybackException) {
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
            }
        }
    }

    // --- "Repeat this track N times", started by holding the play button ---

    private var repeatsLeft = 0
    private var repeatModeBefore = Player.REPEAT_MODE_OFF
    private var changingRepeat = false

    /** Plays the current track [times] more times, then goes back to the previous repeat mode. */
    private fun startRepeats(times: Int) {
        if (times <= 0) return stopRepeats(restore = true)
        if (repeatsLeft == 0) repeatModeBefore = player.repeatMode
        if (player.repeatMode != Player.REPEAT_MODE_ONE) {
            changingRepeat = true
            player.repeatMode = Player.REPEAT_MODE_ONE
        }
        setRepeatsLeft(times)
    }

    private fun setRepeatsLeft(n: Int) {
        repeatsLeft = n
        if (n == 0) restoreRepeatMode()
        session?.setSessionExtras(Bundle().apply { putInt(EXTRA_REPEATS_LEFT, n) })
    }

    private fun stopRepeats(restore: Boolean) {
        if (repeatsLeft == 0) return
        repeatsLeft = 0
        if (restore) restoreRepeatMode()
        session?.setSessionExtras(Bundle().apply { putInt(EXTRA_REPEATS_LEFT, 0) })
    }

    private fun restoreRepeatMode() {
        if (player.repeatMode != repeatModeBefore) {
            changingRepeat = true
            player.repeatMode = repeatModeBefore
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        /** Lets the app's own screens send the "repeat N times" command. */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(COMMAND_REPEAT_TIMES, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == COMMAND_REPEAT_TIMES) {
                startRepeats(args.getInt(EXTRA_TIMES))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        /** Items sent from the UI lose their playable address on the way; restore it here. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                item.buildUpon().setUri(item.playableUri()).build()
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        /**
         * Headphone buttons and the system can ask to resume after the app was closed:
         * hand back the saved queue and position.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                val saved = withContext(io) { store.load() }
                if (saved == null || saved.items.isEmpty()) {
                    result.setException(UnsupportedOperationException("Nothing to resume"))
                } else {
                    result.set(
                        MediaSession.MediaItemsWithStartPosition(
                            saved.items,
                            saved.index.coerceIn(0, saved.items.lastIndex),
                            saved.positionMs.coerceAtLeast(0),
                        ),
                    )
                }
            }
            return result
        }
    }

    companion object {
        const val COMMAND_REPEAT_TIMES = "com.suna.player.REPEAT_TIMES"
        const val EXTRA_TIMES = "times"
        const val EXTRA_REPEATS_LEFT = "repeats_left"

        const val MIN_COUNTED_MS = 30_000L
    }
}
