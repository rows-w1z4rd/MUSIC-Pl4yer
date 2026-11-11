package com.suna.player.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val artistId: Long,
    val durationMs: Long,
    val track: Int,
    /** Seconds since 1970, as recorded by the phone's media index. */
    val dateAdded: Long,
    /** Folder on the phone, like "Music/Rap/". */
    val folder: String,
    val uri: Uri,
)

enum class SongSort(val label: String) {
    TITLE("Title"),
    DATE_ADDED("Date added"),
    ARTIST("Artist"),
    PLAYS("Most played"),
}

fun List<Song>.sortedFor(sort: SongSort, plays: Map<Long, SongPlays>): List<Song> = when (sort) {
    SongSort.TITLE -> this // already sorted by title when loaded
    SongSort.DATE_ADDED -> sortedByDescending { it.dateAdded }
    SongSort.ARTIST -> sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
    SongSort.PLAYS -> sortedByDescending { plays[it.id]?.playCount ?: 0 }
}

data class Folder(val path: String, val name: String, val songCount: Int)

fun List<Song>.toFolders(): List<Folder> =
    groupBy { it.folder }
        .map { (path, songs) ->
            Folder(path, path.trimEnd('/').substringAfterLast('/').ifEmpty { "Phone" }, songs.size)
        }
        .sortedBy { it.name.lowercase() }

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
)

data class Artist(
    val id: Long,
    val name: String,
    val songCount: Int,
)

/** Artwork address the UI image loader understands (loads a sized thumbnail). */
fun albumArtUri(albumId: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)

/** Artwork address the system notification and lock screen can open directly. */
fun notificationArtUri(albumId: Long): Uri =
    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

fun List<Song>.toAlbums(): List<Album> =
    groupBy { it.albumId }
        .map { (id, songs) ->
            Album(id, songs.first().album, songs.first().artist, songs.size)
        }
        .sortedBy { it.title.lowercase() }

fun List<Song>.toArtists(): List<Artist> =
    groupBy { it.artistId }
        .map { (id, songs) -> Artist(id, songs.first().artist, songs.size) }
        .sortedBy { it.name.lowercase() }
