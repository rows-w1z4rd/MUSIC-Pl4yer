package com.suna.player.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.media3.common.util.BitmapLoader
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors

/**
 * Loads the pictures the lock screen and notification show. A track's artwork address is the
 * track itself, and for those it asks Android's thumbnail service, exactly as the app's screens
 * do: that finds the cover inside the file and also a cover.jpg or folder.jpg beside it, which
 * the player library alone would miss. Anything else goes to Media3's usual loader.
 */
class TrackBitmapLoader(context: Context) : BitmapLoader {
    private val resolver = context.applicationContext.contentResolver
    private val fallback = DataSourceBitmapLoader.Builder(context).build()
    private val executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = fallback.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = fallback.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        if (isTrack(uri)) {
            executor.submit<Bitmap> { resolver.loadThumbnail(uri, Size(512, 512), null) }
        } else {
            fallback.loadBitmap(uri)
        }

    private fun isTrack(uri: Uri) =
        uri.scheme == "content" && uri.authority == "media" && uri.pathSegments.contains("audio")
}
