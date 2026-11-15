package com.suna.player.ui.common

import android.net.Uri
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse

/**
 * The cover stored in one track's own file. Android's albums lump together songs that share an
 * album name, or that have none and sit in the same folder, so an album's cover can belong to a
 * different song; this always shows the right one.
 */
data class TrackArt(val uri: Uri)

/** Loads a [TrackArt] through Android's thumbnail service, which reads the file's own picture. */
class TrackArtFetcher(private val data: TrackArt, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val w = options.size.width.pxOrElse { 512 }.coerceIn(64, 1024)
        val h = options.size.height.pxOrElse { 512 }.coerceIn(64, 1024)
        val bitmap = options.context.contentResolver.loadThumbnail(data.uri, Size(w, h), null)
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<TrackArt> {
        override fun create(data: TrackArt, options: Options, imageLoader: ImageLoader): Fetcher =
            TrackArtFetcher(data, options)
    }
}

/** Remembers loaded covers by track. */
class TrackArtKeyer : Keyer<TrackArt> {
    override fun key(data: TrackArt, options: Options): String = "track-art:${data.uri}"
}
