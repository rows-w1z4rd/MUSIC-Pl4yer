package com.suna.player.widget

import com.suna.player.playback.playableUri
import android.net.Uri
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Size
import android.widget.RemoteViews
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.PlaybackPendingIntentBuilder
import com.suna.player.MainActivity
import com.suna.player.R
import com.suna.player.playback.PlaybackService
import com.suna.player.playback.QueueStore

data class WidgetInfo(val title: String, val artist: String, val trackUri: Uri?)

fun MediaItem.toWidgetInfo() = WidgetInfo(
    title = mediaMetadata.title?.toString() ?: "Unknown title",
    artist = mediaMetadata.artist?.toString() ?: "Unknown artist",
    trackUri = playableUri(),
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
                // While the player is running it has already told us what to show.
                val last = WidgetRenderer.last
                if (last != null) {
                    WidgetRenderer.render(context, last.first, last.second)
                } else {
                    val saved = QueueStore(context).load()
                    val item = saved?.items?.getOrNull(saved.index)
                    WidgetRenderer.render(context, item?.toWidgetInfo(), isPlaying = false)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
object WidgetRenderer {
    private var artTrack: Uri? = null
    private var art: Bitmap? = null

    /** What was last drawn, for widgets added while music is already playing. */
    @Volatile
    var last: Pair<WidgetInfo?, Boolean>? = null
        private set

    /** Call off the main thread: it may load the album art. */
    @Synchronized
    fun render(context: Context, info: WidgetInfo?, isPlaying: Boolean) {
        last = info to isPlaying
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
        if (ids.isEmpty()) return

        val views = RemoteViews(context.packageName, R.layout.widget_player)
        views.setTextViewText(R.id.widget_title, info?.title ?: context.getString(R.string.app_name))
        views.setTextViewText(R.id.widget_artist, info?.artist ?: "Tap to open")

        val bitmap = info?.let { loadArt(context, it.trackUri) }
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

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(android.R.id.background, openApp)
        // The buttons send the same media button intents as the notification, straight to the
        // playback service. Play starts it as a foreground service so it works from a cold start.
        views.setOnClickPendingIntent(
            R.id.widget_play,
            // With nothing to resume there is nothing to play yet: open the app instead.
            if (info == null) openApp else command(context, Player.COMMAND_PLAY_PAUSE, foreground = !isPlaying),
        )
        views.setOnClickPendingIntent(R.id.widget_next, command(context, Player.COMMAND_SEEK_TO_NEXT))
        views.setOnClickPendingIntent(R.id.widget_prev, command(context, Player.COMMAND_SEEK_TO_PREVIOUS))

        manager.updateAppWidget(ids, views)
    }

    private fun command(context: Context, command: Int, foreground: Boolean = false): PendingIntent =
        PlaybackPendingIntentBuilder(context, command, PlaybackService::class.java)
            .setStartAsForegroundService(foreground)
            .build()

    /** The track's own cover, small enough for the widget; reused while the track repeats. */
    private fun loadArt(context: Context, trackUri: Uri?): Bitmap? {
        if (trackUri == artTrack) return art
        val loaded = trackUri?.let {
            try {
                context.contentResolver.loadThumbnail(it, Size(320, 320), null)
            } catch (_: Exception) {
                null
            }
        }
        artTrack = trackUri
        art = loaded
        return loaded
    }
}
