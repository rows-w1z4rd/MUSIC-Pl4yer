package com.suna.player.ui.common

import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.suna.player.data.Song
import com.suna.player.data.albumArtUri
import com.suna.player.ui.theme.LocalHasWallpaper
import com.suna.player.ui.theme.LocalIsLight
import com.suna.player.ui.theme.SmallLabel

/**
 * How a page slides in or out, measured from Samsung Music: 300 ms, quick at first and
 * settling smoothly (an ease-out; about half the way after 70 ms, nearly there by 200 ms).
 */
val PageSlide = tween<IntOffset>(durationMillis = 300, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f))

/** Height the floating mini player covers at the bottom; lists add it as bottom padding. */
val LocalMiniPlayerSpace = compositionLocalOf { 0.dp }

val PanelShape = RoundedCornerShape(16.dp)

/** Panel color: the palette's panel, or a frosted veil over a wallpaper. */
@Composable
fun cardColor(): Color {
    val panel = MaterialTheme.colorScheme.surfaceContainerLow
    return if (LocalHasWallpaper.current) panel.copy(alpha = if (LocalIsLight.current) 0.7f else 0.58f) else panel
}

/** Hairline outline that gives panels their edge, like windows in a tiling desktop. */
@Composable
fun borderColor(): Color {
    val border = MaterialTheme.colorScheme.outlineVariant
    if (!LocalHasWallpaper.current) return border
    return if (LocalIsLight.current) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.10f)
}

/** A floating panel with a thin outline, separated from the screen edges by a small gap. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .clip(PanelShape)
            .background(cardColor())
            .border(1.dp, borderColor(), PanelShape),
    ) { content() }
}

/** Clickable that shrinks slightly while pressed, settling without any bounce. */
fun Modifier.pressable(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClick = onClick,
        )
}

@Composable
fun Artwork(uri: Uri?, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.45f),
        )
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Back arrow, title and optional actions; used by every screen below the library. */
@Composable
fun ScreenTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, style = SmallLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
    /** Small note on the right, such as a play count. */
    info: String? = null,
    /** Gets where the row is on screen, so a menu can open next to it. */
    onLongClick: ((Rect) -> Unit)? = null,
    /** Null when not picking songs; otherwise whether this one is picked. */
    selected: Boolean? = null,
    /** Shown at the end of the row while picking, such as a handle to drag it. */
    pickingTrailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val bounds = remember { arrayOf(Rect.Zero) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { bounds[0] = it.boundsInWindow() }
            .background(if (selected == true) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick?.let { { it(bounds[0]) } })
            .padding(start = 14.dp, end = 2.dp, top = 7.dp, bottom = 7.dp),
    ) {
        if (selected != null) {
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (selected) "Selected" else "Not selected",
                tint = if (selected) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.padding(end = 14.dp).size(24.dp),
            )
        }
        Artwork(albumArtUri(song.albumId), Modifier.size(48.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (info != null) {
            Text(
                info,
                style = SmallLabel,
                color = colors.primary,
                modifier = Modifier.padding(end = if (selected != null) 12.dp else 0.dp),
            )
        }
        if (selected == null) {
            HintIconButton("Play next", onClick = onPlayNext) {
                Icon(
                    Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = "Play ${song.title} next",
                    tint = colors.onSurfaceVariant,
                )
            }
            HintIconButton("More options", onClick = onOptions) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Options for ${song.title}",
                    tint = colors.onSurfaceVariant,
                )
            }
        } else if (pickingTrailing != null) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { pickingTrailing() }
        } else {
            // Keeps rows the same height as when the buttons are showing.
            Box(Modifier.size(48.dp))
        }
    }
}

@Composable
fun SongList(
    songs: List<Song>,
    onPlay: (index: Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onOptions: (Song) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    info: ((Song) -> String?)? = null,
    header: (@Composable () -> Unit)? = null,
    state: LazyListState = rememberLazyListState(),
    /** While picking, rows get a handle to drag them; called each time a row passes another. */
    onMove: ((from: Int, to: Int) -> Unit)? = null,
) {
    val selection = LocalSongSelection.current
    val longPress = LocalSongLongPress.current
    val selecting = selection?.active == true
    if (selecting) SideEffect { selection.adopt(songs) }

    // The row being dragged follows the finger; it swaps places with a neighbour once it has
    // moved past half of that neighbour.
    val current by rememberUpdatedState(songs)
    var dragged by remember { mutableStateOf<Long?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }

    LazyColumn(state = state, modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        if (header != null) item(key = "header") { header() }
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            var rowHeight by remember { mutableIntStateOf(1) }
            val handle: (@Composable () -> Unit)? = if (selecting && onMove != null) {
                {
                    Icon(
                        Icons.Rounded.UnfoldMore,
                        contentDescription = "Drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp).pointerInput(song.id) {
                            detectDragGestures(
                                onDragStart = { dragged = song.id; dragY = 0f },
                                onDragEnd = { dragged = null; dragY = 0f },
                                onDragCancel = { dragged = null; dragY = 0f },
                            ) { change, amount ->
                                change.consume()
                                dragY += amount.y
                                val at = current.indexOfFirst { it.id == song.id }
                                val step = rowHeight.toFloat()
                                if (dragY > step / 2 && at < current.lastIndex) {
                                    onMove(at, at + 1)
                                    dragY -= step
                                } else if (dragY < -step / 2 && at > 0) {
                                    onMove(at, at - 1)
                                    dragY += step
                                }
                            }
                        },
                    )
                }
            } else {
                null
            }
            val lifted = dragged == song.id
            SongRow(
                song = song,
                onClick = { if (selecting) selection.toggle(song) else onPlay(index) },
                onPlayNext = { onPlayNext(song) },
                onOptions = { onOptions(song) },
                info = info?.invoke(song),
                onLongClick = { rect ->
                    when {
                        selecting -> selection.toggle(song)
                        longPress != null -> longPress(song, songs, rect)
                        else -> selection?.start(songs, song)
                    }
                },
                selected = if (selecting) song.id in selection.ids else null,
                pickingTrailing = handle,
                modifier = Modifier
                    .onSizeChanged { rowHeight = it.height }
                    .zIndex(if (lifted) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (lifted) dragY else 0f
                        shadowElevation = if (lifted) 8.dp.toPx() else 0f
                    }
                    .then(if (lifted) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier),
            )
        }
    }
}

/** A list row with a square icon badge, a title and an optional second line. */
@Composable
fun SimpleRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides
                    MaterialTheme.colorScheme.onSurfaceVariant,
                content = icon,
            )
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = SmallLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = SmallLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun songCount(n: Int) = if (n == 1) "1 track" else "$n tracks"

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
