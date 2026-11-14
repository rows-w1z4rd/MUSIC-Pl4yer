package com.suna.player.ui.library

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.suna.player.data.Playlist

/**
 * Asks for a playlist name, the way Samsung Music does: the suggested name is already selected,
 * so typing replaces it and Create keeps it. Used for both "create" and "rename".
 */
@Composable
fun PlaylistNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var field by remember {
        mutableStateOf(TextFieldValue(initialName, selection = TextRange(0, initialName.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val confirm = {
        if (field.text.isNotBlank()) {
            onConfirm(field.text)
            onDismiss()
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(colors.surfaceContainerHigh)
                .padding(top = 24.dp, bottom = 8.dp),
        ) {
            Text(
                title,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            BasicTextField(
                value = field,
                onValueChange = { field = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 18.sp, color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(top = 20.dp)
                    .fillMaxWidth()
                    .focusRequester(focus),
                decorationBox = { inner ->
                    Column {
                        Box(Modifier.padding(vertical = 6.dp)) {
                            if (field.text.isEmpty()) {
                                Text("Playlist name", fontSize = 18.sp, color = colors.onSurfaceVariant)
                            }
                            inner()
                        }
                        Box(Modifier.fillMaxWidth().height(1.5.dp).background(colors.primary))
                    }
                },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 18.dp, start = 8.dp, end = 8.dp).height(52.dp),
            ) {
                DialogButton("Cancel", enabled = true, onClick = onDismiss, modifier = Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(20.dp).background(colors.outlineVariant))
                DialogButton(confirmLabel, enabled = field.text.isNotBlank(), onClick = confirm, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** A plain, centred dialog button, like the ones at the bottom of Samsung's dialogs. */
@Composable
private fun DialogButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            label,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onPick: (Long) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                ListItem(
                    modifier = Modifier.clickable(onClick = onNew),
                    colors = transparent,
                    leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                    headlineContent = { Text("New playlist") },
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        ListItem(
                            modifier = Modifier.clickable {
                                onPick(playlist.id)
                                onDismiss()
                            },
                            colors = transparent,
                            leadingContent = {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                            },
                            headlineContent = {
                                Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DeletePlaylistDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete playlist?") },
        text = { Text("\"$name\" will be deleted. The tracks themselves stay on your phone.") },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
