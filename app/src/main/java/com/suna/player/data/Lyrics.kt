package com.suna.player.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import java.io.File

/** One line of lyrics; [timeMs] is when it is sung, or null for lyrics without timings. */
data class LyricLine(val timeMs: Long?, val text: String)

/** A song's lyrics. [synced] lyrics carry a time per line, so they can follow the music. */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean) {
    /** The line being sung at [positionMs], or -1 before the first one. */
    fun lineAt(positionMs: Long): Int {
        if (!synced) return -1
        var lo = 0
        var hi = lines.lastIndex
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if ((lines[mid].timeMs ?: 0) <= positionMs) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }

    companion object {
        private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
        private val offsetTag = Regex("""^\[offset:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE)
        private val infoTag = Regex("""^\[[a-zA-Z]+:.*]$""")

        /**
         * Reads lyrics text. LRC style ("[01:23.45]words", several stamps per line allowed,
         * with an optional [offset:ms]) becomes synced lyrics; anything else is shown as is.
         */
        fun parse(text: String): Lyrics? {
            val raw = text.replace("\r\n", "\n").replace('\r', '\n').lines()
            var offset = 0L
            val timed = ArrayList<LyricLine>()
            val plain = ArrayList<LyricLine>()
            for (line in raw) {
                val trimmed = line.trim()
                offsetTag.find(trimmed)?.let { offset = it.groupValues[1].toLongOrNull() ?: 0L }
                val stamps = stamp.findAll(trimmed).toList()
                if (stamps.isNotEmpty() && stamps.first().range.first == 0) {
                    val words = trimmed.substring(stamps.last().range.last + 1).trim()
                    for (s in stamps) {
                        val (m, sec, frac) = s.destructured
                        val fracMs = when (frac.length) {
                            0 -> 0L
                            1 -> frac.toLong() * 100
                            2 -> frac.toLong() * 10
                            else -> frac.take(3).toLong()
                        }
                        val ms = m.toLong() * 60_000 + sec.toLong() * 1000 + fracMs - offset
                        timed += LyricLine(ms.coerceAtLeast(0), words)
                    }
                } else if (!infoTag.matches(trimmed)) {
                    plain += LyricLine(null, trimmed)
                }
            }
            return when {
                timed.any { it.text.isNotBlank() } -> Lyrics(timed.sortedBy { it.timeMs }, synced = true)
                plain.any { it.text.isNotBlank() } -> Lyrics(plain.dropWhile { it.text.isBlank() }.dropLastWhile { it.text.isBlank() }, synced = false)
                else -> null
            }
        }
    }
}

/**
 * Finds a song's lyrics, offline only: first a matching .lrc file in the lyrics folder the user
 * picked in Settings (Android only lets the app read text files in a folder the user chose),
 * then the lyrics stored inside the music file. Results are kept for the session.
 */
class LyricsRepository(private val context: Context, private val settings: SettingsStore) {

    private val cache = HashMap<Long, Lyrics?>()
    private val lock = Mutex()
    private var folderIndex: Map<String, Uri>? = null
    private var indexedFolder: String? = null

    init {
        TagOptionSingleton.getInstance().isAndroid = true
    }

    suspend fun load(song: Song): Lyrics? = lock.withLock {
        val folder = settings.lyricsFolder.first()
        if (folder != indexedFolder) {
            // A different lyrics folder: look everything up again.
            cache.clear()
            folderIndex = null
            indexedFolder = folder
        }
        if (cache.containsKey(song.id)) return cache[song.id]
        val found = withContext(Dispatchers.IO) { folder?.let { fromFolder(song, it) } ?: fromFile(song) }
        cache[song.id] = found
        found
    }

    /** Forgets what was found for a song, after its lyrics were edited. */
    suspend fun forget(songId: Long) = lock.withLock { cache.remove(songId) }

    private fun fromFolder(song: Song, folder: String): Lyrics? {
        if (folderIndex == null) {
            folderIndex = runCatching { indexFolder(Uri.parse(folder)) }.getOrDefault(emptyMap())
        }
        val index = folderIndex ?: return null
        val names = listOfNotNull(
            fileBaseName(song.uri),
            "${song.artist} - ${song.title}",
            song.title,
        ).map { it.lowercase().trim() }
        val match = names.firstNotNullOfOrNull { index[it] } ?: return null
        return runCatching {
            context.contentResolver.openInputStream(match)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()?.let(Lyrics::parse)
    }

    /** Every .lrc in the folder and the folders inside it, by name without ".lrc". */
    private fun indexFolder(tree: Uri): Map<String, Uri> {
        val out = HashMap<String, Uri>()
        val pending = ArrayDeque<String>().apply { add(DocumentsContract.getTreeDocumentId(tree)) }
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        var visited = 0
        while (pending.isNotEmpty() && visited < 2000) {
            visited++
            val parent = pending.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            context.contentResolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1) ?: continue
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending += id
                    } else if (name.endsWith(".lrc", ignoreCase = true)) {
                        out[name.dropLast(4).lowercase().trim()] = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    }
                }
            }
        }
        return out
    }

    private fun fromFile(song: Song): Lyrics? {
        val path = filePath(song.uri) ?: return null
        val text = runCatching {
            AudioFileIO.read(File(path)).tag?.getFirst(FieldKey.LYRICS)
        }.getOrNull()
        return text?.takeIf { it.isNotBlank() }?.let(Lyrics::parse)
    }

    private fun fileBaseName(uri: Uri): String? =
        query(uri, MediaStore.MediaColumns.DISPLAY_NAME)?.substringBeforeLast('.')

    private fun filePath(uri: Uri): String? = query(uri, MediaStore.MediaColumns.DATA)

    private fun query(uri: Uri, column: String): String? =
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
