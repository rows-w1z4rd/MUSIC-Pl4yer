package com.suna.player.ui.library

import com.suna.player.ui.common.TrackArt
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Process
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import com.suna.player.data.Song
import com.suna.player.data.TagEditor
import com.suna.player.data.TrackInfo
import com.suna.player.data.albumArtUri
import com.suna.player.ui.common.Artwork
import com.suna.player.ui.common.Panel
import com.suna.player.ui.theme.SmallLabel
import kotlinx.coroutines.launch

/**
 * Changing a track's title, artist, album, genre, year and cover, like Huawei Music's editor.
 * The changes are written into the music file, so every app sees them. Android asks once per
 * file before the app may change it.
 */
@Composable
fun EditTrackScreen(
    song: Song,
    editor: TagEditor,
    onDone: (saved: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<TagEditor.Draft?>(null) }
    var failed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var lyrics by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf<ByteArray?>(null) }
    var original by remember { mutableStateOf<TrackInfo?>(null) }

    LaunchedEffect(song.id) {
        val opened = editor.open(song)
        if (opened == null) {
            failed = true
        } else {
            draft = opened.first
            val info = opened.second
            original = info
            title = info.title; artist = info.artist; album = info.album
            genre = info.genre; year = info.year; lyrics = info.lyrics; cover = info.cover
        }
    }
    val leave = { saved: Boolean ->
        draft?.let { if (!saved) editor.discard(it) }
        onDone(saved)
    }
    BackHandler(enabled = !saving) { leave(false) }

    val info = TrackInfo(title, artist, album, genre, year, lyrics, cover)
    val changed = original != null && (
        info.title != original!!.title || info.artist != original!!.artist || info.album != original!!.album ||
            info.genre != original!!.genre || info.year != original!!.year || info.lyrics != original!!.lyrics ||
            !(info.cover contentEquals original!!.cover)
        )

    val write: () -> Unit = {
        val d = draft
        if (d != null) {
            saving = true
            scope.launch {
                val ok = editor.save(d, info)
                if (ok) {
                    // Show the new cover everywhere instead of the cached old one.
                    SingletonImageLoader.get(context).memoryCache?.clear()
                    SingletonImageLoader.get(context).diskCache?.clear()
                }
                saving = false
                if (ok) leave(true) else failed = true
            }
        }
    }
    // Other apps' music files need the user's OK before they can be changed.
    val askToWrite = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) write()
    }
    val save: () -> Unit = {
        val allowed = context.checkUriPermission(
            song.uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        ) == PackageManager.PERMISSION_GRANTED
        if (allowed) {
            write()
        } else {
            val request = MediaStore.createWriteRequest(context.contentResolver, listOf(song.uri))
            askToWrite.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }
    }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { TagEditor.coverFrom(context, uri)?.let { cover = it } }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
            IconButton(onClick = { if (!saving) leave(false) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("Edit track info", fontSize = 22.sp, modifier = Modifier.weight(1f).padding(start = 4.dp))
            if (saving) {
                CircularProgressIndicator(Modifier.padding(end = 16.dp).size(24.dp), strokeWidth = 2.5.dp)
            } else {
                TextButton(enabled = changed && title.isNotBlank(), onClick = save) { Text("Save", fontSize = 17.sp) }
            }
        }

        when {
            failed && draft == null -> Message("This file can't be edited. It may be a format the app can't write, or it's been moved.")
            draft == null -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp),
            ) {
                // The cover: tap to pick a new picture.
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier.padding(top = 8.dp).size(180.dp).clip(RoundedCornerShape(28.dp)).clickable {
                        pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                ) {
                    val bitmap = remember(cover) { cover?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
                    if (bitmap != null) {
                        Image(bitmap, contentDescription = "Cover", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Artwork(TrackArt(song.uri), Modifier.fillMaxSize(), corner = 28.dp)
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(10.dp).size(40.dp).clip(RoundedCornerShape(20.dp)).background(colors.primary),
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = "Change cover", tint = colors.onPrimary, modifier = Modifier.size(22.dp))
                    }
                }
                Row(Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text("Change cover")
                    }
                    if (cover != null) TextButton(onClick = { cover = null }) { Text("Remove cover") }
                }

                Panel(Modifier.padding(top = 8.dp)) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Field("Title", title) { title = it }
                        Field("Artist", artist) { artist = it }
                        Field("Album", album) { album = it }
                        Field("Genre", genre) { genre = it }
                        Field("Year", year, numbers = true) { year = it.filter(Char::isDigit).take(4) }
                    }
                }
                Panel(Modifier.padding(top = 12.dp)) {
                    LyricsField(lyrics) { lyrics = it }
                }
                if (failed) {
                    Message("Couldn't save the changes. The file was left as it was.")
                } else {
                    Text(
                        "Changes are saved into the music file itself, so other apps show them too.",
                        style = SmallLabel,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** A labelled text field on the panel, like a settings row you can type in. */
@Composable
private fun Field(label: String, value: String, numbers: Boolean = false, onChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 17.sp, color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = if (numbers) KeyboardCapitalization.None else KeyboardCapitalization.Words,
                keyboardType = if (numbers) KeyboardType.Number else KeyboardType.Text,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            decorationBox = { inner ->
                Column {
                    Box(Modifier.padding(vertical = 4.dp)) {
                        if (value.isEmpty()) Text("Not set", fontSize = 17.sp, color = colors.onSurfaceVariant)
                        inner()
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
                }
            },
        )
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.error,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
    )
}

/**
 * Lyrics, typed or pasted. Lines like "[01:23.45]words" (LRC) make them follow the song in the
 * player; plain text is shown as it is.
 */
@Composable
private fun LyricsField(value: String, onChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Text("Lyrics", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            minLines = 4,
            maxLines = 14,
            textStyle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            "Paste lyrics here. Lines starting with a time, like [01:23.45], follow the song.",
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
    }
}
