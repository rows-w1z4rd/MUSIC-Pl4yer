package com.suna.player.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.suna.player.data.Song
import com.suna.player.data.albumArtUri
import com.suna.player.ui.common.Artwork
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A long-pressed song: which one, the list it was in, and where its row sits on screen. */
data class SongMenuTarget(val song: Song, val list: List<Song>, val bounds: Rect)

/**
 * The long-press menu: the screen dims, the song's row lifts out of the list, and a compact
 * menu pops open next to it (below, or above when there is no room).
 */
@Composable
fun SongContextMenu(
    target: SongMenuTarget,
    isFavorite: Boolean,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenAlbum: () -> Unit,
    onShowDetails: () -> Unit,
    onShare: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 520f)) }

    // Closing plays the pop in reverse, then runs the chosen action.
    fun close(then: () -> Unit = {}) {
        scope.launch {
            shown.animateTo(0f, tween(140))
            onDismiss()
            then()
        }
    }
    BackHandler { close() }

    val statusTop = WindowInsets.statusBars.getTop(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val gap = with(density) { 10.dp.roundToPx() }
    val edge = with(density) { 12.dp.roundToPx() }
    val bounds = target.bounds
    var menuBelow = true

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
    ) {
        Layout(
            content = {
                LiftedRow(target.song, Modifier.graphicsLayer {
                    val lift = lerp(1f, 1.03f, shown.value)
                    scaleX = lift
                    scaleY = lift
                    shadowElevation = 18.dp.toPx() * shown.value
                    shape = RoundedCornerShape(16.dp)
                    clip = true
                })
                Menu(
                    modifier = Modifier.graphicsLayer {
                        val pop = lerp(0.82f, 1f, shown.value)
                        scaleX = pop
                        scaleY = pop
                        transformOrigin = TransformOrigin(0.1f, if (menuBelow) 0f else 1f)
                    },
                ) {
                    Item("Play next", Icons.AutoMirrored.Rounded.PlaylistPlay) { close(onPlayNext) }
                    Line()
                    Item("Add to queue", Icons.AutoMirrored.Rounded.QueueMusic) { close(onAddToQueue) }
                    Gap()
                    Item(
                        if (isFavorite) "Remove from favorites" else "Add to favorites",
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        tint = if (isFavorite) colors.primary else null,
                    ) { close(onToggleFavorite) }
                    Line()
                    Item("Add to playlist", Icons.AutoMirrored.Rounded.PlaylistAdd) { close(onAddToPlaylist) }
                    Line()
                    Item("Go to album", Icons.Rounded.Album) { close(onOpenAlbum) }
                    Line()
                    Item("Song details", Icons.Rounded.Info) { close(onShowDetails) }
                    Line()
                    Item("Share", Icons.Rounded.Share) { close(onShare) }
                    Gap()
                    Item("Select", Icons.Rounded.CheckCircleOutline) { close(onSelect) }
                    Gap()
                    Item("Delete from phone", Icons.Rounded.Delete, tint = colors.error) { close(onDelete) }
                }
            },
        ) { measurables, constraints ->
            val row = measurables[0].measure(
                Constraints.fixed(bounds.width.roundToInt().coerceAtLeast(1), bounds.height.roundToInt().coerceAtLeast(1)),
            )
            val menu = measurables[1].measure(Constraints(maxWidth = constraints.maxWidth - 2 * edge))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val home = bounds.top.roundToInt()
                val floor = constraints.maxHeight - navBottom - edge
                val ceiling = statusTop + edge
                val fitsBelow = home + row.height + gap + menu.height <= floor
                val fitsAbove = home - gap - menu.height >= ceiling
                menuBelow = fitsBelow || !fitsAbove
                // With room on neither side, the row glides up just enough to fit the menu below.
                val target = if (fitsBelow || fitsAbove) home else (floor - menu.height - gap - row.height).coerceAtLeast(ceiling)
                val rowY = lerp(home.toFloat(), target.toFloat(), shown.value.coerceIn(0f, 1f)).roundToInt()
                val menuY = if (menuBelow) rowY + row.height + gap else rowY - gap - menu.height
                val menuX = (bounds.left.roundToInt() + edge).coerceAtMost(constraints.maxWidth - menu.width - edge)
                row.place(bounds.left.roundToInt(), rowY)
                menu.place(menuX, menuY)
            }
        }
    }
}

@Composable
private fun LiftedRow(song: Song, modifier: Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp),
    ) {
        Artwork(albumArtUri(song.albumId), Modifier.size(48.dp))
        Column(Modifier.padding(horizontal = 14.dp)) {
            Text(song.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                song.artist,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Menu(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .width(258.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            // Taps inside the menu shouldn't fall through and close it.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) { content() }
}

@Composable
private fun Item(label: String, icon: ImageVector, tint: Color? = null, onClick: () -> Unit) {
    val color = tint ?: MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Text(label, fontSize = 16.sp, color = color, modifier = Modifier.weight(1f))
        Icon(icon, contentDescription = null, tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** A hairline between items of the same kind. */
@Composable
private fun Line() {
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

/** A thicker break between groups of items. */
@Composable
private fun Gap() {
    Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceContainerLow))
}
