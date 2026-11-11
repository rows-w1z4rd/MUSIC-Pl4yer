package com.suna.player.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.suna.player.data.Song
import com.suna.player.data.notificationArtUri

private const val EXTRA_PLAY_NEXT = "suna.playNext"
private const val EXTRA_ALBUM_ID = "suna.albumId"

fun buildMediaItem(
    mediaId: String,
    uri: Uri,
    title: String,
    artist: String,
    album: String,
    albumId: Long,
    playNext: Boolean,
): MediaItem =
    MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(uri)
        // The plain uri is dropped when an item travels from the UI to the service;
        // this copy survives the trip and the service restores the uri from it.
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(notificationArtUri(albumId))
                .setExtras(
                    Bundle().apply {
                        putBoolean(EXTRA_PLAY_NEXT, playNext)
                        putLong(EXTRA_ALBUM_ID, albumId)
                    },
                )
                .build(),
        )
        .build()

fun Song.toMediaItem(playNext: Boolean = false): MediaItem =
    buildMediaItem(id.toString(), uri, title, artist, album, albumId, playNext)

fun MediaItem.isPlayNext(): Boolean =
    mediaMetadata.extras?.getBoolean(EXTRA_PLAY_NEXT, false) ?: false

fun MediaItem.albumId(): Long =
    mediaMetadata.extras?.getLong(EXTRA_ALBUM_ID, -1L) ?: -1L

fun MediaItem.playableUri(): Uri? =
    localConfiguration?.uri ?: requestMetadata.mediaUri

fun MediaItem.withPlayNext(playNext: Boolean): MediaItem {
    val extras = Bundle(mediaMetadata.extras ?: Bundle.EMPTY).apply {
        putBoolean(EXTRA_PLAY_NEXT, playNext)
    }
    return buildUpon()
        .setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build())
        .build()
}
