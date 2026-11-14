package com.suna.player.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suna.player.ui.common.BarAction

/**
 * Picking whole playlists on the Playlists tab ("Edit" in its menu, or a long press), as in
 * Samsung Music. [all] is the list on screen, so "All" picks exactly what is shown.
 */
@Stable
class PlaylistSelection {
    var active by mutableStateOf(false)
        private set
    var ids by mutableStateOf(emptySet<Long>())
        private set
    var all by mutableStateOf(emptyList<Long>())
        private set

    val count: Int get() = ids.size
    val allSelected: Boolean get() = all.isNotEmpty() && ids.size == all.size

    /** The picked playlists, in the order they appear on screen. */
    val selected: List<Long> get() = all.filter { it in ids }

    fun start(first: Long? = null) {
        ids = setOfNotNull(first)
        active = true
    }

    fun adopt(playlists: List<Long>) {
        all = playlists
        if (ids.any { it !in playlists }) ids = ids.filterTo(HashSet()) { it in playlists }
    }

    fun toggle(id: Long) {
        ids = if (id in ids) ids - id else ids + id
    }

    fun toggleAll() {
        ids = if (allSelected) emptySet() else all.toSet()
    }

    fun clear() {
        active = false
        ids = emptySet()
    }
}

/** "2 selected" with close and select-all, over the top of the screen while picking. */
@Composable
fun BoxScope.PlaylistSelectionTopBar(selection: PlaylistSelection) {
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
                if (selection.count == 0) "Select playlists" else "${selection.count} selected",
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

/** Play, Add, Share, Delete and Rename for the picked playlists, in place of the mini player. */
@Composable
fun BoxScope.PlaylistSelectionBottomBar(
    selection: PlaylistSelection,
    onPlay: (List<Long>) -> Unit,
    onAdd: (List<Long>) -> Unit,
    onShare: (List<Long>) -> Unit,
    onDelete: (List<Long>) -> Unit,
    onRename: (Long) -> Unit,
) {
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
            BarAction(Icons.Rounded.Share, "Share", enabled) { onShare(selection.selected) }
            BarAction(Icons.Rounded.Delete, "Delete", enabled) { onDelete(selection.selected) }
            BarAction(Icons.Rounded.Edit, "Rename", selection.count == 1) { onRename(selection.selected.first()) }
        }
    }
}
