package com.suna.player.ui.settings

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import com.suna.player.ui.common.PageSlide
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.SeekableTransitionState
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import com.suna.player.ui.theme.on
import com.suna.player.data.AccentHue
import com.suna.player.ui.theme.LocalWallpaperTones
import com.suna.player.ui.theme.LocalIsLight
import com.suna.player.ui.theme.WallpaperImage
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suna.player.R
import com.suna.player.data.Appearance
import com.suna.player.data.LibraryTab
import com.suna.player.data.Palette
import com.suna.player.data.SettingsStore
import com.suna.player.data.WallpaperStore
import com.suna.player.ui.common.LocalMiniPlayerSpace
import com.suna.player.ui.common.cardColor
import com.suna.player.ui.theme.colors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The settings pages. The main list opens each detail page in place. */
private enum class Page(val title: String) {
    MAIN("Settings"),
    THEME("Theme"),
    ACCENT("Accent color"),
    WALLPAPER("Wallpaper"),
    HEADER("Header text"),
    TABS("Library tabs"),
}

@Composable
fun SettingsScreen(
    appearance: Appearance,
    skipSilence: Boolean,
    wallpaper: ImageBitmap?,
    settings: SettingsStore,
    wallpapers: WallpaperStore,
    onBack: () -> Unit,
    onWallpaperPreview: (newPicture: Boolean) -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(Page.MAIN) }
    val save = rememberSave(settings)

    // A back swipe from a sub-page drags it to the right with the finger while the main page
    // comes in from the left; letting go finishes the slide, cancelling slides it back.
    val pages = remember { SeekableTransitionState(page) }
    var swiping by remember { mutableStateOf(false) }
    LaunchedEffect(page) { if (!swiping && pages.currentState != page) pages.animateTo(page) }
    PredictiveBackHandler(enabled = page != Page.MAIN) { progress ->
        swiping = true
        try {
            progress.collect { event -> pages.seekTo(event.progress, targetState = Page.MAIN) }
            pages.animateTo(Page.MAIN)
            page = Page.MAIN
        } catch (e: CancellationException) {
            withContext(NonCancellable) { pages.animateTo(pages.currentState) }
            throw e
        } finally {
            swiping = false
        }
    }

    rememberTransition(pages, label = "settingsPage").AnimatedContent(
        transitionSpec = {
            val forward = targetState != Page.MAIN
            slideInHorizontally(PageSlide) { if (forward) it else -it }
                .togetherWith(slideOutHorizontally(PageSlide) { if (forward) -it else it })
        },
    ) { shown ->
        SettingsPage(
            title = shown.title,
            backLabel = if (shown == Page.MAIN) null else "Settings",
            onBack = if (shown == Page.MAIN) onBack else ({ page = Page.MAIN }),
        ) {
            when (shown) {
                Page.MAIN -> MainPage(appearance, skipSilence, wallpaper, settings, save) { page = it }
                Page.THEME -> ThemePage(appearance, wallpaper != null, save)
                Page.ACCENT -> AccentPage(appearance, save)
                Page.WALLPAPER -> WallpaperPage(appearance, wallpaper, settings, wallpapers, onWallpaperPreview)
                Page.HEADER -> HeaderPage(appearance, save)
                Page.TABS -> TabsPage(appearance.tabs) { tabs -> save { setTabs(tabs) } }
            }
        }
    }
}

/** Saves a setting from a click handler. */
private typealias Save = (suspend SettingsStore.() -> Unit) -> Unit

@Composable
private fun rememberSave(settings: SettingsStore): Save {
    val scope = rememberCoroutineScope()
    return remember(settings, scope) { { block -> scope.launch { settings.block() } } }
}

// ---------------------------------------------------------------------------------------------
// Page frame and building blocks
// ---------------------------------------------------------------------------------------------

@Composable
private fun SettingsPage(
    title: String,
    backLabel: String?,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bottom = LocalMiniPlayerSpace.current +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(bottom = bottom + 24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 6.dp, top = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onBack)
                .heightIn(min = 44.dp)
                .padding(horizontal = 10.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBackIos,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            if (backLabel != null) {
                Text(backLabel, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
            }
        }
        Text(
            title,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 18.dp),
        )
        content()
    }
}

/** A titled group of rows on a rounded card, with an optional note underneath. */
@Composable
private fun Group(
    title: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 26.dp)) {
        if (title != null) {
            Text(
                title.uppercase(),
                fontSize = 12.sp,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, bottom = 7.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cardColor()),
            content = content,
        )
        if (footer != null) {
            Text(
                footer,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 7.dp),
            )
        }
    }
}

/** Hairline between rows, starting after the icon like iOS lists. */
@Composable
private fun RowDivider(start: Int = 58) {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        modifier = Modifier.padding(start = start.dp),
    )
}

/** A small colored tile behind a white symbol, so rows can be found at a glance. */
@Composable
private fun IconTile(icon: ImageVector, tint: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(tint),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector?,
    tint: Color,
    title: String,
    value: String? = null,
    titleColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            IconTile(icon, tint)
            Spacer(Modifier.width(14.dp))
        }
        Text(title, fontSize = 16.sp, color = titleColor, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(
                value,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp).widthIn(max = 170.dp),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        } else if (onClick != null && icon != null) {
            Chevron()
        }
    }
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 4.dp).size(22.dp),
    )
}

@Composable
private fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

// Tile colors, picked to read well on black and on light themes.
private val Indigo = Color(0xFF5E5CE6)
private val Pink = Color(0xFFFF375F)
private val Blue = Color(0xFF0A84FF)
private val Grey = Color(0xFF8E8E93)
private val Orange = Color(0xFFFF9F0A)
private val Green = Color(0xFF30D158)
private val Teal = Color(0xFF40C8E0)
private val Red = Color(0xFFFF453A)
private val Purple = Color(0xFFBF5AF2)

// ---------------------------------------------------------------------------------------------
// Main list
// ---------------------------------------------------------------------------------------------

@Composable
private fun MainPage(
    appearance: Appearance,
    skipSilence: Boolean,
    wallpaper: ImageBitmap?,
    settings: SettingsStore,
    save: Save,
    open: (Page) -> Unit,
) {
    val offload by settings.audioOffload.collectAsStateWithLifecycle(initialValue = false)
    val endless by settings.endlessPlay.collectAsStateWithLifecycle(initialValue = true)
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"
    }

    Group(
        "Appearance",
        footer = "The theme is the background and overall look. The accent is the highlight " +
            "color on top of it: buttons, the progress bar, the heart and the selected tab.",
    ) {
        SettingRow(Icons.Rounded.Palette, Indigo, "Theme", appearance.palette.label, onClick = { open(Page.THEME) })
        RowDivider()
        SettingRow(
            Icons.Rounded.ColorLens,
            Pink,
            "Accent color",
            appearance.accent?.label ?: "Theme default",
            onClick = { open(Page.ACCENT) },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Chevron()
                }
            },
        )
        RowDivider()
        SettingRow(
            Icons.Rounded.Wallpaper,
            Blue,
            "Wallpaper",
            if (wallpaper != null) "On" else "None",
            onClick = { open(Page.WALLPAPER) },
        )
        RowDivider()
        SettingRow(
            Icons.Rounded.TextFields,
            Grey,
            "Header text",
            appearance.headerText.ifBlank { "None" },
            onClick = { open(Page.HEADER) },
        )
    }

    Group("Library") {
        SettingRow(
            Icons.Rounded.Tab,
            Orange,
            "Tabs",
            appearance.tabs.joinToString { it.label },
            onClick = { open(Page.TABS) },
        )
    }

    Group(
        "Playback",
        footer = "Endless play adds more tracks from your library when the queue runs out, " +
            "so the music doesn't stop. Battery saver lets the phone's audio chip play music " +
            "while the screen is off. Turn it off if you hear skips or stutters.",
    ) {
        SettingRow(Icons.Rounded.AllInclusive, Purple, "Endless play", trailing = {
            Toggle(endless) { on -> save { setEndlessPlay(on) } }
        })
        RowDivider()
        SettingRow(Icons.Rounded.BatterySaver, Green, "Battery saver", trailing = {
            Toggle(offload) { on -> save { setAudioOffload(on) } }
        })
        RowDivider()
        SettingRow(Icons.Rounded.FastForward, Teal, "Skip silence", trailing = {
            Toggle(skipSilence) { on -> save { setSkipSilence(on) } }
        })
    }

    Group(
        "About",
        footer = "Plays the music stored on your phone. No internet access, no tracking.",
    ) {
        SettingRow(Icons.Rounded.Info, Grey, stringResource(R.string.app_name), version)
    }
}

// ---------------------------------------------------------------------------------------------
// Theme
// ---------------------------------------------------------------------------------------------

@Composable
private fun ThemePage(appearance: Appearance, hasWallpaper: Boolean, save: Save) {
    val choices = Palette.entries.filter { it != Palette.WALLPAPER || hasWallpaper }
    Column(Modifier.padding(horizontal = 16.dp)) {
        choices.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                pair.forEach { palette ->
                    ThemeCard(
                        palette = palette,
                        selected = palette == appearance.palette,
                        modifier = Modifier.weight(1f),
                        onClick = { save { setPalette(palette) } },
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(
            if (hasWallpaper) {
                "Black keeps unlit pixels off on your OLED screen. From wallpaper takes its colors " +
                    "from your picture, and goes light or dark to match it."
            } else {
                "Black keeps unlit pixels off on your OLED screen. Set a wallpaper to get a theme " +
                    "made from its colors."
            },
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

/** A miniature of the app in that theme: a few song rows and the player bar. */
@Composable
private fun ThemeCard(palette: Palette, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val c = palette.colors(LocalWallpaperTones.current)
    val background = c.background
    val panel = c.panel
    val raised = c.raised
    val text = c.text
    val subtext = c.subtext
    val accent = c.accent
    val shape = RoundedCornerShape(18.dp)

    Column(modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.95f)
                .clip(shape)
                .background(background)
                .then(
                    if (c.glow != null) {
                        Modifier.background(Brush.verticalGradient(0f to c.glow, 0.6f to background))
                    } else {
                        Modifier
                    },
                )
                .border(if (selected) 2.5.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, shape)
                .padding(10.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(panel)
                    .padding(8.dp),
            ) {
                repeat(3) { i ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (i == 0) accent else raised),
                        )
                        Column(Modifier.padding(start = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(Modifier.width(if (i == 1) 46.dp else 58.dp).height(4.dp).background(text, CircleShape))
                            Box(Modifier.width(32.dp).height(3.dp).background(subtext, CircleShape))
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(raised)
                    .padding(horizontal = 6.dp),
            ) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(accent))
                Box(Modifier.padding(start = 6.dp).width(40.dp).height(4.dp).background(text, CircleShape))
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.padding(end = 4.dp).size(16.dp),
                )
            }
            Text(
                palette.label,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Accent color
// ---------------------------------------------------------------------------------------------

@Composable
private fun AccentPage(appearance: Appearance, save: Save) {
    val light = LocalIsLight.current
    val themeAccent = appearance.palette.colors(LocalWallpaperTones.current).accent

    Group(
        footer = "The accent is the highlight color: buttons, the progress bar, the heart and the " +
            "selected tab. Each color is shaded to suit your theme.",
    ) {
        SettingRow(
            icon = null,
            tint = Color.Transparent,
            title = "Theme default",
            onClick = { save { setAccent(null) } },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(themeAccent))
                    Box(Modifier.width(32.dp), contentAlignment = Alignment.CenterEnd) {
                        if (appearance.accent == null) {
                            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
        )
    }

    Column(Modifier.padding(horizontal = 16.dp)) {
        AccentHue.entries.chunked(5).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            ) {
                row.forEach { hue ->
                    val color = hue.on(light)
                    val selected = appearance.accent == hue
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(60.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { save { setAccent(hue) } }
                            .padding(vertical = 4.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(50.dp)
                                .border(2.5.dp, if (selected) color else Color.Transparent, CircleShape)
                                .padding(5.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = if (color.luminance() > 0.45f) Color(0xFF101014) else Color.White,
                                )
                            }
                        }
                        Text(
                            hue.label,
                            fontSize = 12.sp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Wallpaper
// ---------------------------------------------------------------------------------------------

@Composable
private fun WallpaperPage(
    appearance: Appearance,
    wallpaper: ImageBitmap?,
    settings: SettingsStore,
    wallpapers: WallpaperStore,
    onPreview: (newPicture: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var importFailed by remember { mutableStateOf(false) }
    // A picked picture opens in the preview first; nothing changes until it is set there.
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = wallpapers.importDraft(uri)
                importFailed = !ok
                if (ok) onPreview(true)
            }
        }
    }
    val onAdjust = { onPreview(false) }
    val choose = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    // A phone-shaped preview of the wallpaper as it sits behind the app.
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(150.dp)
                .aspectRatio(9f / 19.5f)
                .clip(RoundedCornerShape(22.dp))
                .background(if (LocalIsLight.current) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Black)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                .clickable(onClick = if (wallpaper != null) onAdjust else choose),
        ) {
            if (wallpaper != null) {
                WallpaperImage(
                    wallpaper,
                    appearance.wallpaperScale,
                    appearance.wallpaperX,
                    appearance.wallpaperY,
                    edges = appearance.wallpaperEdges,
                )
                val veil = if (LocalIsLight.current) Color.White else Color.Black
                Box(Modifier.fillMaxSize().background(veil.copy(alpha = appearance.dim)))
            } else {
                Text("No wallpaper", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    Group(footer = if (importFailed) "Couldn't open that picture. Try another one." else null) {
        SettingRow(
            null,
            Color.Transparent,
            if (wallpaper != null) "Choose another picture" else "Choose a picture",
            titleColor = MaterialTheme.colorScheme.primary,
            onClick = choose,
        )
        if (wallpaper != null) {
            RowDivider(start = 14)
            SettingRow(
                null,
                Color.Transparent,
                "Adjust position and size",
                titleColor = MaterialTheme.colorScheme.primary,
                onClick = onAdjust,
            )
            RowDivider(start = 14)
            SettingRow(
                null,
                Color.Transparent,
                "Remove wallpaper",
                titleColor = Red,
                onClick = {
                    scope.launch {
                        settings.setWallpaper(false)
                        if (appearance.palette == Palette.WALLPAPER) settings.setPalette(Palette.BLUE)
                        wallpapers.clear()
                    }
                },
            )
        }
    }

    if (wallpaper != null) {
        // Sliders move freely and save once when released.
        var blur by remember(appearance.blur) { mutableFloatStateOf(appearance.blur) }
        var dim by remember(appearance.dim) { mutableFloatStateOf(appearance.dim) }
        Group("Look") {
            SliderRow("Blur", blur, 0f..1f, onChange = { blur = it }, onDone = { scope.launch { settings.setBlur(blur) } })
            RowDivider(start = 14)
            SliderRow("Dim", dim, 0f..0.9f, onChange = { dim = it }, onDone = { scope.launch { settings.setDim(dim) } })
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp)) {
        Row {
            Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(
                "${((value - range.start) / (range.endInclusive - range.start) * 100).roundToInt()}%",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            onValueChangeFinished = onDone,
            colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Header text
// ---------------------------------------------------------------------------------------------

@Composable
private fun HeaderPage(appearance: Appearance, save: Save) {
    var text by remember { mutableStateOf(appearance.headerText) }
    val colors = MaterialTheme.colorScheme

    Group(footer = "Shown above the tabs in your library. Leave it empty to show nothing.") {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 14.dp),
        ) {
            if (text.isEmpty()) {
                Text("Your text", fontSize = 16.sp, color = colors.onSurfaceVariant.copy(alpha = 0.7f))
            }
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it.take(40)
                    val value = text
                    save { setHeaderText(value) }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface, fontSize = 16.sp),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Library tabs
// ---------------------------------------------------------------------------------------------

/**
 * Shown tabs are reordered by dragging their handle and hidden with the red minus; hidden
 * tabs come back with the green plus. At least one tab always stays.
 */
@Composable
private fun TabsPage(enabled: List<LibraryTab>, onChange: (List<LibraryTab>) -> Unit) {
    val rowHeight = 54.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    val order = remember(enabled) { mutableStateListOf(*enabled.toTypedArray()) }
    var dragging by remember { mutableStateOf<LibraryTab?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val hidden = LibraryTab.entries.filterNot { it in order }

    Group("Shown", footer = "Drag the handle to change the order. In the library, swipe sideways to switch tabs.") {
        order.forEachIndexed { index, tab ->
            // Keyed by tab, so a row keeps its drag gesture while it moves through the list.
            key(tab) {
                val isDragged = dragging == tab
                Column(
                    Modifier
                        .graphicsLayer {
                            translationY = if (isDragged) dragOffset else 0f
                            shadowElevation = if (isDragged) 16f else 0f
                        }
                        .background(if (isDragged) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().height(rowHeight).padding(start = 8.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable(enabled = order.size > 1) {
                                    order.remove(tab)
                                    onChange(order.toList())
                                },
                        ) {
                            Icon(
                                Icons.Rounded.RemoveCircle,
                                contentDescription = "Hide ${tab.label}",
                                tint = if (order.size > 1) Red else Red.copy(alpha = 0.3f),
                            )
                        }
                        Text(tab.label, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 6.dp))
                        Icon(
                            Icons.Rounded.DragHandle,
                            contentDescription = "Drag to reorder ${tab.label}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(52.dp)
                                .pointerInput(tab) {
                                    detectDragGestures(
                                        onDragStart = {
                                            dragging = tab
                                            dragOffset = 0f
                                        },
                                        onDragEnd = {
                                            dragging = null
                                            dragOffset = 0f
                                            onChange(order.toList())
                                        },
                                        onDragCancel = {
                                            dragging = null
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            val from = order.indexOf(tab)
                                            // Swap with a neighbor once the row is dragged halfway over it.
                                            if (dragOffset > rowPx / 2 && from < order.lastIndex) {
                                                order.add(from + 1, order.removeAt(from))
                                                dragOffset -= rowPx
                                            } else if (dragOffset < -rowPx / 2 && from > 0) {
                                                order.add(from - 1, order.removeAt(from))
                                                dragOffset += rowPx
                                            }
                                        },
                                    )
                                }
                                .padding(14.dp),
                        )
                    }
                    if (index < order.lastIndex && !isDragged) RowDivider(start = 54)
                }
            }
        }
    }

    if (hidden.isNotEmpty()) {
        Group("Hidden") {
            hidden.forEachIndexed { index, tab ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .clickable {
                            order.add(tab)
                            onChange(order.toList())
                        }
                        .padding(start = 8.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.AddCircle, contentDescription = "Show ${tab.label}", tint = Green)
                    }
                    Text(
                        tab.label,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                if (index < hidden.lastIndex) RowDivider(start = 54)
            }
        }
    }
}
