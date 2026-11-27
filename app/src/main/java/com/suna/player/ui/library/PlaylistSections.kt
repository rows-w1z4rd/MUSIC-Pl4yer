package com.suna.player.ui.library

import com.suna.player.ui.common.PlayingBars
import com.suna.player.ui.common.LocalNowPlaying
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suna.player.ui.common.Artwork
import com.suna.player.ui.theme.SmallLabel

/** How a section of the Playlists tab lays out its items. */
enum class SectionView(val label: String) {
    ROWS("Rows view"),
    GRID("Grid view"),
    SCROLL("Scrolling row"),
}

/** One thing shown in a section: a smart list or one of the user's playlists. */
class PlaylistTile(
    val key: Any,
    val title: String,
    val subtitle: String,
    val cover: Any?,
    val favorite: Boolean = false,
    /** Null when not picking; otherwise whether this one is picked. */
    val selected: Boolean? = null,
    /** False dims it and ignores taps (smart lists while picking playlists). */
    val enabled: Boolean = true,
    /** Its music is what's playing now. */
    val nowPlaying: Boolean = false,
    val onClick: () -> Unit,
    val onLongClick: () -> Unit = {},
)

/**
 * A section title with, on the right, an arrow that folds the section away and a ••• button
 * offering the three views.
 */
@Composable
fun SectionHeader(
    title: String,
    count: Int?,
    open: Boolean,
    onToggle: () -> Unit,
    view: SectionView,
    onView: (SectionView) -> Unit,
) {
    val grey = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    val turn by animateFloatAsState(if (open) 0f else -90f, label = "sectionArrow")
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 18.dp, end = 6.dp),
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = grey)
        if (count != null) {
            Text("  $count", fontSize = 15.sp, color = grey.copy(alpha = 0.5f))
        }
        Spacer(Modifier.weight(1f))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onToggle),
        ) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (open) "Collapse $title" else "Expand $title",
                tint = grey,
                modifier = Modifier.rotate(turn),
            )
        }
        Box {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { menuOpen = true },
            ) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = "$title view", tint = grey)
            }
            SortMenu(
                expanded = menuOpen,
                options = SectionView.entries.map { it.label },
                selected = view.ordinal,
                onPick = {
                    menuOpen = false
                    onView(SectionView.entries[it])
                },
                onDismiss = { menuOpen = false },
            )
        }
    }
}

/** Adds a section's items to the tab's list, laid out the way [view] says. */
fun LazyListScope.sectionItems(id: String, view: SectionView, tiles: List<PlaylistTile>) {
    when (view) {
        SectionView.ROWS -> items(tiles.size, key = { "$id-r-${tiles[it].key}" }) { TileRow(tiles[it]) }
        SectionView.GRID -> {
            val pairs = tiles.chunked(2)
            items(pairs.size, key = { "$id-g-${pairs[it].first().key}" }) { i ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    pairs[i].forEach { TileCard(it, Modifier.weight(1f)) }
                    if (pairs[i].size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        SectionView.SCROLL -> item(key = "$id-s") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                items(tiles, key = { it.key }) { TileCard(it, Modifier.width(150.dp).padding(4.dp)) }
            }
        }
    }
}

/** A list row: small cover, name, a heart if it's a favorite, and its size. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileRow(tile: PlaylistTile) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (tile.enabled) 1f else 0.4f)
            .background(if (tile.selected == true) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(enabled = tile.enabled, onClick = tile.onClick, onLongClick = tile.onLongClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        if (tile.selected != null) {
            PickMark(tile.selected, Modifier.padding(end = 14.dp))
        }
        Box(Modifier.size(58.dp)) {
            Artwork(tile.cover, Modifier.size(58.dp), corner = 16.dp)
            if (tile.nowPlaying) CoverBars(Modifier.size(58.dp), corner = 16.dp, barSize = 16.dp)
        }
        Text(
            tile.title,
            fontSize = 18.sp,
            color = if (tile.nowPlaying) colors.primary else Color.Unspecified,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 18.dp, end = 8.dp),
        )

        if (tile.favorite) {
            Icon(Icons.Rounded.Favorite, contentDescription = "Favorite", tint = colors.primary, modifier = Modifier.padding(end = 10.dp).size(18.dp))
        }
        if (tile.selected == null) Text(tile.subtitle, fontSize = 15.sp, color = colors.onSurfaceVariant)
    }
}

/** A big square cover with the name and size underneath, for the grid and the scrolling row. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileCard(tile: PlaylistTile, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .alpha(if (tile.enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(26.dp))
            .combinedClickable(enabled = tile.enabled, onClick = tile.onClick, onLongClick = tile.onLongClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Artwork(tile.cover, Modifier.fillMaxWidth().aspectRatio(1f), corner = 26.dp)
            if (tile.nowPlaying) CoverBars(Modifier.fillMaxWidth().aspectRatio(1f), corner = 26.dp, barSize = 26.dp)
            if (tile.selected != null) {
                PickMark(tile.selected, Modifier.align(Alignment.TopStart).padding(10.dp), onPicture = true)
            }
            if (tile.favorite) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = "Favorite",
                    tint = colors.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(20.dp),
                )
            }
        }
        Text(
            tile.title,
            fontSize = 16.sp,
            color = if (tile.nowPlaying) colors.primary else Color.Unspecified,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            tile.subtitle,
            style = SmallLabel,
            color = if (tile.nowPlaying) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun PickMark(selected: Boolean, modifier: Modifier, onPicture: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Icon(
        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
        contentDescription = if (selected) "Selected" else "Not selected",
        tint = if (selected) colors.primary else if (onPicture) Color.White else colors.onSurfaceVariant,
        modifier = modifier
            .size(26.dp)
            .then(if (onPicture) Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape) else Modifier),
    )
}

/**
 * The playing mark on a playlist's cover: the moving dots in its lower corner, over a soft shade
 * that fades in from the bottom, so the picture stays visible.
 */
@Composable
private fun CoverBars(modifier: Modifier, corner: androidx.compose.ui.unit.Dp, barSize: androidx.compose.ui.unit.Dp) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0.45f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.55f),
                ),
            ),
    ) {
        PlayingBars(
            LocalNowPlaying.current.playing,
            MaterialTheme.colorScheme.primary,
            Modifier.align(Alignment.BottomStart).padding(start = barSize * 0.45f, bottom = barSize * 0.45f).size(barSize),
        )
    }
}
