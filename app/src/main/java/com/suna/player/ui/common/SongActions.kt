package com.suna.player.ui.common

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suna.player.data.Song

/** Opens the share sheet with the songs' audio files. */
fun shareSongs(context: Context, songs: List<Song>) {
    if (songs.isEmpty()) return
    val uris = ArrayList(songs.map { it.uri })
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    send.type = "audio/*"
    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The clip carries the read permission for every file, not just the first.
    send.clipData = ClipData.newRawUri(null, uris[0]).apply {
        uris.drop(1).forEach { addItem(ClipData.Item(it)) }
    }
    context.startActivity(Intent.createChooser(send, null))
}

/**
 * Deletes songs from the phone. Android shows its own confirmation first; the library
 * refreshes by itself once the files are gone.
 */
@Composable
fun rememberDeleteSongs(onDeleted: () -> Unit = {}): (List<Song>) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) onDeleted()
    }
    return { songs ->
        if (songs.isNotEmpty()) {
            val request = MediaStore.createDeleteRequest(context.contentResolver, songs.map { it.uri })
            launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }
    }
}

/** "3 selected" with close and select-all, shown over the top of the screen while picking. */
@Composable
fun BoxScope.SelectionTopBar(selection: SongSelection) {
    val colors = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = selection.active,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = selection::clear) {
                Icon(Icons.Rounded.Close, contentDescription = "Stop selecting")
            }
            Text(
                if (selection.count == 0) "Select songs" else "${selection.count} selected",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = selection::toggleAll)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("All", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
                Icon(
                    if (selection.allSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (selection.allSelected) "Unselect all" else "Select all",
                    tint = if (selection.allSelected) colors.primary else colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** Play, Add, Share and Delete for the picked songs, in place of the mini player. */
@Composable
fun BoxScope.SelectionBottomBar(
    selection: SongSelection,
    onPlay: (List<Song>) -> Unit,
    onAdd: (List<Song>) -> Unit,
    onDelete: (List<Song>) -> Unit,
) {
    val context = LocalContext.current
    AnimatedVisibility(
        visible = selection.active,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        val enabled = selection.count > 0
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .navigationBarsPadding()
                .padding(vertical = 6.dp),
        ) {
            BarAction(Icons.Rounded.PlayArrow, "Play", enabled) { onPlay(selection.selected) }
            BarAction(Icons.Rounded.Add, "Add", enabled) { onAdd(selection.selected) }
            BarAction(Icons.Rounded.Share, "Share", enabled) { shareSongs(context, selection.selected) }
            BarAction(Icons.Rounded.Delete, "Delete", enabled) { onDelete(selection.selected) }
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    }
}
