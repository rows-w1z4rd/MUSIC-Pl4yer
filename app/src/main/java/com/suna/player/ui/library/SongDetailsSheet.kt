package com.suna.player.ui.library

import android.content.Context
import android.provider.MediaStore
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suna.player.data.Song
import com.suna.player.data.SongPlays
import com.suna.player.data.albumArtUri
import com.suna.player.ui.common.Artwork
import com.suna.player.ui.common.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the phone's media index knows about the file itself. */
private data class FileFacts(val format: String?, val kbps: Int?, val bytes: Long?, val fileName: String?)

/** Everything known about a song: the file, where it lives, and how often it was played. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailsSheet(song: Song, plays: SongPlays?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val facts by produceState<FileFacts?>(null, song.id) {
        value = withContext(Dispatchers.IO) { readFileFacts(context, song) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surfaceContainerLow) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Artwork(albumArtUri(song.albumId), Modifier.size(132.dp), corner = 16.dp)
            Text(
                song.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp),
            )
            Text(song.artist, fontSize = 15.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))

            Group("Track") {
                Fact("Album", song.album.ifBlank { "Unknown album" })
                Fact("Artist", song.artist)
                Fact("Length", formatTime(song.durationMs))
                if (song.track > 0) Fact("Track", (song.track % 1000).toString())
            }

            Group("File") {
                Fact("Format", facts?.format ?: "…")
                facts?.kbps?.let { Fact("Quality", "$it kbps") }
                facts?.bytes?.let { Fact("Size", Formatter.formatShortFileSize(context, it)) }
                Fact("Folder", song.folder.trimEnd('/').ifEmpty { "Phone storage" })
                facts?.fileName?.let { Fact("File name", it) }
                Fact("Added", DateUtils.formatDateTime(context, song.dateAdded * 1000, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR))
            }

            Group("Listening") {
                Fact("Plays", plays?.playCount?.toString() ?: "Not played yet")
                plays?.let {
                    Fact(
                        "Last played",
                        DateUtils.getRelativeTimeSpanString(it.lastPlayedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                    )
                }
            }
        }
    }
}

private fun readFileFacts(context: Context, song: Song): FileFacts {
    val projection = arrayOf(
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.BITRATE,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DISPLAY_NAME,
    )
    return runCatching {
        context.contentResolver.query(song.uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val mime = c.getString(0)
            val bitrate = if (c.isNull(1)) null else c.getLong(1)
            FileFacts(
                format = mime?.let(::formatName),
                kbps = bitrate?.takeIf { it > 0 }?.let { (it / 1000).toInt() },
                bytes = if (c.isNull(2)) null else c.getLong(2),
                fileName = c.getString(3),
            )
        }
    }.getOrNull() ?: FileFacts(null, null, null, null)
}

/** "audio/mpeg" → "MP3", and so on for the common kinds. */
private fun formatName(mime: String): String = when (mime.lowercase()) {
    "audio/mpeg", "audio/mp3" -> "MP3"
    "audio/flac", "audio/x-flac" -> "FLAC"
    "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac" -> "AAC (M4A)"
    "audio/ogg", "application/ogg" -> "Ogg"
    "audio/opus" -> "Opus"
    "audio/wav", "audio/x-wav" -> "WAV"
    else -> mime.substringAfter('/').uppercase()
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
        Text(
            title.uppercase(),
            fontSize = 12.sp,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 14.dp, bottom = 7.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)),
        ) { content() }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                fontSize = 15.sp,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 16.dp),
            )
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
