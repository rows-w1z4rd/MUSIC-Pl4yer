package com.suna.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.suna.player.MainActivity
import com.suna.player.R
import com.suna.player.data.albumArtUri
import com.suna.player.playback.PlaybackService
import com.suna.player.playback.QueueStore
import com.suna.player.playback.albumId

data class WidgetInfo(val title: String, val artist: String, val albumId: Long)

fun MediaItem.toWidgetInfo() = WidgetInfo(
    title = mediaMetadata.title?.toString() ?: "Unknown title",
    artist = mediaMetadata.artist?.toString() ?: "Unknown artist",
    albumId = albumId(),
)

/**
 * The home screen widget. It never polls: the playback service redraws it when the song or
 * the play state changes, and its buttons talk to the player only when pressed.
 */
class PlayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread {
            try {
                val saved = QueueStore(context).load()
                val item = saved?.items?.getOrNull(saved.index)
                WidgetRenderer.render(context, item?.toWidgetInfo(), isPlaying = false)
            } finally {
                pending.finish()
            }
        }.start()
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (val action = intent.action) {
            ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREVIOUS -> control(context, action)
            else -> super.onReceive(context, intent)
        }
    }

    /** Connects to the player for a moment, sends one command and lets go again. */
    private fun control(context: Context, action: String) {
        val pending = goAsync()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val main = Handler(Looper.getMainLooper())
        var finished = false
        fun done() {
            if (finished) return
            finished = true
            MediaController.releaseFuture(future)
            pending.finish()
        }
        future.addListener(
            {
                val c = try {
                    future.get()
                } catch (_: Exception) {
                    done()
                    return@addListener
                }
                val run = {
                    when (action) {
                        ACTION_PLAY_PAUSE -> if (c.isPlaying) {
                            c.pause()
                        } else {
                            if (c.playbackState == Player.STATE_IDLE) c.prepare()
                            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0L)
                            c.play()
                        }
                        ACTION_NEXT -> c.seekToNextMediaItem()
                        ACTION_PREVIOUS -> c.seekToPrevious()
                    }
                    // Give the command a moment to reach the service before disconnecting.
                    main.postDelayed(::done, 500)
                }
                if (c.mediaItemCount > 0) {
                    run()
                } else {
                    // The service was just started and is still loading the saved queue.
                    c.addListener(object : Player.Listener {
                        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                            if (c.mediaItemCount > 0) {
                                c.removeListener(this)
                                run()
                            }
                        }
                    })
                    main.postDelayed(::done, 3000)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.suna.player.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.suna.player.widget.NEXT"
        const val ACTION_PREVIOUS = "com.suna.player.widget.PREVIOUS"
    }
}

object WidgetRenderer {
    private var artAlbumId = Long.MIN_VALUE
    private var art: Bitmap? = null

    /** Call off the main thread: it may load the album art. */
    @Synchronized
    fun render(context: Context, info: WidgetInfo?, isPlaying: Boolean) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
        if (ids.isEmpty()) return

        val views = RemoteViews(context.packageName, R.layout.widget_player)
        views.setTextViewText(R.id.widget_title, info?.title ?: "SunaPlay")
        views.setTextViewText(R.id.widget_artist, info?.artist ?: "tap to open")

        val bitmap = info?.let { loadArt(context, it.albumId) }
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.widget_art, bitmap)
        } else {
            views.setImageViewResource(R.id.widget_art, R.mipmap.ic_launcher_foreground)
        }

        views.setImageViewResource(
            R.id.widget_play,
            if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )
        views.setContentDescription(R.id.widget_play, if (isPlaying) "Pause" else "Play")

        views.setOnClickPendingIntent(
            android.R.id.background,
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        views.setOnClickPendingIntent(R.id.widget_play, command(context, PlayerWidget.ACTION_PLAY_PAUSE, 1))
        views.setOnClickPendingIntent(R.id.widget_next, command(context, PlayerWidget.ACTION_NEXT, 2))
        views.setOnClickPendingIntent(R.id.widget_prev, command(context, PlayerWidget.ACTION_PREVIOUS, 3))

        manager.updateAppWidget(ids, views)
    }

    private fun command(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, PlayerWidget::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Small enough to stay well under the widget size limit; reused while the album repeats. */
    private fun loadArt(context: Context, albumId: Long): Bitmap? {
        if (albumId == artAlbumId) return art
        val loaded = if (albumId < 0) {
            null
        } else {
            try {
                context.contentResolver.loadThumbnail(albumArtUri(albumId), Size(320, 320), null)
            } catch (_: Exception) {
                null
            }
        }
        artAlbumId = albumId
        art = loaded
        return loaded
    }
}
