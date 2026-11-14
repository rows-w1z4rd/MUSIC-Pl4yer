package com.suna.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.resume

/** What can be changed about a track. Empty text clears that field. */
data class TrackInfo(
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val year: String,
    /** The cover stored in the file (JPEG or PNG bytes), or null if it has none. */
    val cover: ByteArray?,
)

/**
 * Reads and writes the tags inside a music file, so a new title or cover shows in every app.
 *
 * Android only lets other apps' files be changed through the media store, after the user agrees,
 * so editing works on copies: [open] copies the file into the app's cache; [save] edits a second
 * copy and only then writes it over the real file. If that last write fails, the untouched copy
 * is written back, so a failed save never leaves a damaged song.
 */
class TagEditor(private val context: Context) {

    init {
        TagOptionSingleton.getInstance().isAndroid = true
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    private val dir get() = File(context.cacheDir, "tag-edit").apply { mkdirs() }

    /** A copy of the song's file, ready to read and edit. */
    class Draft internal constructor(val song: Song, internal val original: File, internal val path: String?)

    suspend fun open(song: Song): Pair<Draft, TrackInfo>? = withContext(Dispatchers.IO) {
        try {
            val (name, path) = fileName(song.uri)
            val ext = name.substringAfterLast('.', "mp3").lowercase()
            val original = File(dir, "${song.id}-original.$ext")
            context.contentResolver.openInputStream(song.uri)!!.use { input ->
                original.outputStream().use { input.copyTo(it) }
            }
            val tag = AudioFileIO.read(original).tag
            val info = TrackInfo(
                title = tag?.getFirst(FieldKey.TITLE).orEmpty().ifEmpty { song.title },
                artist = tag?.getFirst(FieldKey.ARTIST).orEmpty().ifEmpty { song.artist },
                album = tag?.getFirst(FieldKey.ALBUM).orEmpty().ifEmpty { song.album },
                genre = tag?.getFirst(FieldKey.GENRE).orEmpty(),
                year = tag?.getFirst(FieldKey.YEAR).orEmpty(),
                cover = tag?.firstArtwork?.binaryData,
            )
            Draft(song, original, path) to info
        } catch (_: Exception) {
            null
        }
    }

    /** Writes [info] into the song's file. The caller must already have write access to it. */
    suspend fun save(draft: Draft, info: TrackInfo): Boolean = withContext(Dispatchers.IO) {
        val edited = File(dir, "${draft.song.id}-edited.${draft.original.extension}")
        try {
            draft.original.copyTo(edited, overwrite = true)
            val audio = AudioFileIO.read(edited)
            val tag = audio.tagOrCreateAndSetDefault
            fun put(key: FieldKey, value: String) {
                if (value.isBlank()) tag.deleteField(key) else tag.setField(key, value.trim())
            }
            put(FieldKey.TITLE, info.title)
            put(FieldKey.ARTIST, info.artist)
            put(FieldKey.ALBUM, info.album)
            put(FieldKey.GENRE, info.genre)
            put(FieldKey.YEAR, info.year)
            tag.deleteArtworkField()
            info.cover?.let { bytes ->
                val art = ArtworkFactory.getNew()
                art.binaryData = bytes
                art.mimeType = if (bytes.isPng()) "image/png" else "image/jpeg"
                art.pictureType = 3 // front cover
                tag.setField(art)
            }
            audio.commit()
        } catch (_: Exception) {
            edited.delete()
            return@withContext false
        }
        // The real file is only touched now, once the edited copy is complete.
        val ok = writeOver(draft.song.uri, edited) || run {
            writeOver(draft.song.uri, draft.original) // put the untouched file back
            false
        }
        edited.delete()
        if (ok) {
            draft.original.delete()
            rescan(draft.path, draft.song.uri)
        }
        ok
    }

    fun discard(draft: Draft) {
        draft.original.delete()
    }

    private fun writeOver(uri: Uri, from: File): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
            from.inputStream().use { it.copyTo(out) }
        }
        true
    } catch (_: Exception) {
        false
    }

    /** Asks Android to read the file's new tags, so every app (and this one) shows them. */
    private suspend fun rescan(path: String?, uri: Uri) {
        if (path == null) {
            context.contentResolver.notifyChange(uri, null)
            return
        }
        suspendCancellableCoroutine { done ->
            MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ -> done.resume(Unit) }
        }
    }

    private fun fileName(uri: Uri): Pair<String, String?> {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATA),
            null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) return c.getString(0) to c.getString(1)
        }
        return "track.mp3" to null
    }

    private fun ByteArray.isPng() = size > 4 && this[0] == 0x89.toByte() && this[1] == 'P'.code.toByte()

    companion object {
        /** Reads a picked picture and shrinks it to a cover-sized JPEG (big covers bloat files). */
        suspend fun coverFrom(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
            try {
                val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val scale = minOf(1f, 1000f / maxOf(info.size.width, info.size.height))
                    d.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
                ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    bitmap.recycle()
                    out.toByteArray()
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
