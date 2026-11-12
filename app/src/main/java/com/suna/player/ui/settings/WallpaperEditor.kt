package com.suna.player.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suna.player.data.Appearance
import com.suna.player.data.LibraryTab
import com.suna.player.data.Palette
import com.suna.player.data.SettingsStore
import com.suna.player.data.Song
import com.suna.player.data.WallpaperStore
import com.suna.player.data.WallpaperEdges
import com.suna.player.data.albumArtUri
import com.suna.player.ui.common.Artwork
import com.suna.player.ui.common.Panel
import com.suna.player.ui.common.SongRow
import com.suna.player.ui.common.borderColor
import com.suna.player.ui.common.cardColor
import com.suna.player.ui.library.ListToolbar
import com.suna.player.ui.library.TabBar
import com.suna.player.ui.theme.AppBackground
import com.suna.player.ui.theme.SmallLabel
import com.suna.player.ui.theme.SunaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Full-screen preview of a wallpaper behind the real library, shown before anything is saved.
 * Pinch to zoom (out until the whole picture fits), drag to move, try blur and dim, then Set or
 * Cancel. Used both for a newly picked picture ([newPicture]) and to adjust the current one;
 * either way it starts from the whole picture, never from a cropped copy.
 */
@Composable
fun WallpaperEditor(
    newPicture: Boolean,
    appearance: Appearance,
    wallpapers: WallpaperStore,
    songs: List<Song>,
    onApply: suspend (scale: Float, x: Float, y: Float, blur: Float, dim: Float, edges: WallpaperEdges) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // A new picture starts filling the screen with the current blur and dim; adjusting starts
    // from what is saved.
    var scale by remember { mutableFloatStateOf(if (newPicture) 1f else appearance.wallpaperScale) }
    var x by remember { mutableFloatStateOf(if (newPicture) 0f else appearance.wallpaperX) }
    var y by remember { mutableFloatStateOf(if (newPicture) 0f else appearance.wallpaperY) }
    var blur by remember { mutableFloatStateOf(appearance.blur) }
    var dim by remember { mutableFloatStateOf(appearance.dim) }
    var edges by remember { mutableStateOf(appearance.wallpaperEdges) }
    var controls by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    // The same rendering the app uses, redone shortly after the blur slider stops moving.
    val picture by produceState<ImageBitmap?>(null, blur) {
        delay(if (value == null) 0 else 90)
        value = wallpapers.render(blur, fromDraft = newPicture)
    }
    // Width and height of the sharp picture, for "Fit".
    val pictureSize by produceState<Pair<Int, Int>?>(null) {
        value = wallpapers.render(0f, fromDraft = newPicture)?.let { it.width to it.height }
    }

    val cancel: () -> Unit = {
        if (!saving) {
            if (newPicture) scope.launch { wallpapers.discardDraft() }
            onClose()
        }
    }
    BackHandler(onBack = cancel)

    val look = appearance.copy(
        palette = if (newPicture) Palette.WALLPAPER else appearance.palette,
        accent = if (newPicture) null else appearance.accent,
        hasWallpaper = true,
        blur = blur,
        dim = dim,
        wallpaperScale = scale,
        wallpaperX = x,
        wallpaperY = y,
        wallpaperEdges = edges,
    )

    SunaTheme(look, picture) {
        AppBackground(wallpaper = picture, appearance = look) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val screenW = constraints.maxWidth.toFloat()
                val screenH = constraints.maxHeight.toFloat()

                LibraryMock(look, songs, showMiniBar = !controls)

                // On top of the mock so its buttons can't be pressed; takes every gesture.
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(
                                    SettingsStore.MIN_WALLPAPER_SCALE,
                                    SettingsStore.MAX_WALLPAPER_SCALE,
                                )
                                x = (x + pan.x / size.width).coerceIn(-1f, 1f)
                                y = (y + pan.y / size.height).coerceIn(-1f, 1f)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { controls = !controls })
                        },
                )

                AnimatedVisibility(
                    visible = controls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
                        Spacer(Modifier.weight(1f))
                        ControlPanel(
                            scale = scale,
                            blur = blur,
                            dim = dim,
                            edges = edges,
                            onEdges = { edges = it },
                            onFill = {
                                scale = 1f
                                x = 0f
                                y = 0f
                            },
                            onFit = {
                                pictureSize?.let { (w, h) ->
                                    // The picture is drawn cropped to fill the screen; this is
                                    // the zoom at which all of it shows instead.
                                    val crop = max(screenW / w, screenH / h)
                                    val fit = min(screenW / w, screenH / h)
                                    scale = (fit / crop).coerceIn(SettingsStore.MIN_WALLPAPER_SCALE, 1f)
                                    x = 0f
                                    y = 0f
                                }
                            },
                            onBlur = { blur = it },
                            onDim = { dim = it },
                            onCancel = cancel,
                            onSet = {
                                if (!saving) {
                                    saving = true
                                    scope.launch {
                                        onApply(scale, x, y, blur, dim, edges)
                                        onClose()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Dark glass panel at the bottom: Fill/Fit, blur, dim, Cancel and Set. */
@Composable
private fun ControlPanel(
    scale: Float,
    blur: Float,
    dim: Float,
    edges: WallpaperEdges,
    onEdges: (WallpaperEdges) -> Unit,
    onFill: () -> Unit,
    onFit: () -> Unit,
    onBlur: (Float) -> Unit,
    onDim: (Float) -> Unit,
    onCancel: () -> Unit,
    onSet: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.92f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            "Pinch to zoom · drag to move · tap the picture to hide this",
            style = SmallLabel,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 2.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onFill) { Text("Fill screen") }
            TextButton(onClick = onFit) { Text("Whole picture") }
            Spacer(Modifier.weight(1f))
            Text("${(scale * 100).roundToInt()}%", style = SmallLabel, color = Color.White.copy(alpha = 0.7f))
        }
        // What fills the screen around the picture when it is zoomed out.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Edges", fontSize = 14.sp, color = Color.White, modifier = Modifier.width(56.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .padding(3.dp),
            ) {
                WallpaperEdges.entries.forEach { option ->
                    val on = option == edges
                    Text(
                        option.label,
                        fontSize = 14.sp,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.75f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { onEdges(option) }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            if (scale >= 1f) {
                Text(
                    "shows when zoomed out",
                    style = SmallLabel,
                    color = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        PanelSlider("Blur", blur, 0f..1f, onBlur)
        PanelSlider("Dim", dim, 0f..0.9f, onDim)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel", color = Color.White)
            }
            Button(onClick = onSet, modifier = Modifier.weight(1f)) { Text("Set") }
        }
    }
}

@Composable
private fun PanelSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = Color.White, modifier = Modifier.width(44.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.weight(1f).height(36.dp),
        )
    }
}

/**
 * The library as it will look over the picture, built from the app's own parts: header, tabs,
 * the track panel with a few of your tracks, and the mini player. Nothing in it reacts.
 */
@Composable
private fun LibraryMock(look: Appearance, songs: List<Song>, showMiniBar: Boolean) {
    val tabs = look.tabs
    val first = tabs.indexOf(LibraryTab.SONGS).coerceAtLeast(0)
    val shown = remember(songs) { songs.sortedByDescending { it.dateAdded }.take(10) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            val hasHeader = look.headerText.isNotBlank()
            if (hasHeader) {
                Text(
                    look.headerText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 20.dp, end = 4.dp, top = 16.dp),
                )
            }
            Box(Modifier.fillMaxWidth().padding(top = if (hasHeader) 4.dp else 10.dp, bottom = 8.dp)) {
                TabBar(
                    tabs = tabs,
                    selected = first,
                    position = { first.toFloat() },
                    onSelect = {},
                    endClearance = if (hasHeader) 0.dp else 100.dp,
                    modifier = Modifier.align(Alignment.Center),
                )
                if (!hasHeader) {
                    // Same 48dp touch size as the real buttons, so the tabs sit at the same height.
                    Row(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
                        listOf(Icons.Rounded.Search, Icons.Rounded.MoreVert).forEach { icon ->
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = null)
                            }
                        }
                    }
                }
            }
            Panel(Modifier.weight(1f)) {
                Column {
                    ListToolbar(label = "Date added", count = songs.size, onLabel = {}, onShuffle = {}, onPlayAll = {})
                    shown.forEach { SongRow(it, onClick = {}, onPlayNext = {}, onOptions = {}) }
                }
            }
        }
        // Hidden under the controls, where it would only show through as a ghost.
        if (showMiniBar) shown.firstOrNull()?.let { MiniBarMock(it, Modifier.align(Alignment.BottomCenter)) }
    }
}

/** A still copy of the floating mini player. */
@Composable
private fun MiniBarMock(song: Song, modifier: Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .navigationBarsPadding()
            .padding(10.dp)
            .fillMaxWidth()
            .background(cardColor(), shape)
            .border(1.dp, borderColor(), shape)
            .padding(8.dp),
    ) {
        Artwork(albumArtUri(song.albumId), Modifier.size(48.dp), corner = 12.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(14.dp))
        Icon(Icons.Rounded.SkipNext, contentDescription = null)
        Spacer(Modifier.width(6.dp))
    }
}
