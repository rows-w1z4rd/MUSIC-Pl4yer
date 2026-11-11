package com.suna.player.ui.settings

import com.suna.player.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.suna.player.data.Appearance
import com.suna.player.data.LibraryTab
import com.suna.player.data.Palette
import com.suna.player.data.SettingsStore
import com.suna.player.data.WallpaperStore
import com.suna.player.ui.common.LocalMiniPlayerSpace
import com.suna.player.ui.common.Panel
import com.suna.player.ui.common.ScreenTopBar
import com.suna.player.ui.theme.AccentChoices
import com.suna.player.ui.theme.SmallLabel
import com.suna.player.ui.theme.colors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    appearance: Appearance,
    skipSilence: Boolean,
    settings: SettingsStore,
    wallpapers: WallpaperStore,
    onBack: () -> Unit,
    onAdjustWallpaper: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val bottom = LocalMiniPlayerSpace.current +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = "Settings", onBack = onBack)
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = bottom + 16.dp),
        ) {
            Section("Appearance") {
                Label("Theme")
                PalettePicker(appearance.palette) { scope.launch { settings.setPalette(it) } }
                if (appearance.hasWallpaper && appearance.palette.colors()?.dark == false) {
                    Hint("Light themes switch to black while a wallpaper is set, so text stays readable.")
                }

                Label("Accent color")
                AccentPicker(appearance.accent) { scope.launch { settings.setAccent(it) } }

                Label("Header text")
                var header by remember { mutableStateOf(appearance.headerText) }
                OutlinedTextField(
                    value = header,
                    onValueChange = {
                        header = it.take(40)
                        scope.launch { settings.setHeaderText(header) }
                    },
                    singleLine = true,
                    placeholder = { Text("Leave empty for no header") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section("Library tabs") {
                Hint("Choose which tabs show and in what order. Swipe sideways to switch between them.")
                TabManager(appearance.tabs) { scope.launch { settings.setTabs(it) } }
            }

            Section("Wallpaper") {
                WallpaperControls(appearance, settings, wallpapers, onAdjustWallpaper)
            }

            Section("Playback") {
                val offload by settings.audioOffload.collectAsStateWithLifecycle(initialValue = false)
                SwitchRow(
                    title = "Battery saver playback",
                    hint = "Lets the phone's audio chip play music while the screen is off. " +
                        "Turn it off if you hear skips or stutters.",
                    checked = offload,
                    onChange = { scope.launch { settings.setAudioOffload(it) } },
                )
                SwitchRow(
                    title = "Skip silence",
                    hint = "Skips quiet parts at the start and end of songs.",
                    checked = skipSilence,
                    onChange = { scope.launch { settings.setSkipSilence(it) } },
                )
            }

            Section("About") {
                val context = LocalContext.current
                val version = remember {
                    runCatching {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    }.getOrNull() ?: "?"
                }
                Text("${stringResource(R.string.app_name)} $version", style = MaterialTheme.typography.bodyLarge)
                Hint("Plays the music stored on your phone. No internet access, no tracking.")
                Hint("Catppuccin, Nord, Gruvbox, Rosé Pine and Tokyo Night are open-source color schemes.")
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Panel {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            content()
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Hint(hint)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Small previews of each palette: its background, a panel, text lines and the accent. */
@Composable
private fun PalettePicker(selected: Palette, onPick: (Palette) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        items(Palette.entries) { palette ->
            val c = palette.colors()
            val isSelected = palette == selected
            val shape = RoundedCornerShape(10.dp)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(92.dp).clickable { onPick(palette) },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(shape)
                        .background(c?.background ?: MaterialTheme.colorScheme.background)
                        .border(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape,
                        )
                        .padding(7.dp),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(c?.panel ?: MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            Modifier.width(36.dp).height(5.dp)
                                .background(c?.text ?: MaterialTheme.colorScheme.onSurface, RoundedCornerShape(3.dp)),
                        )
                        Box(
                            Modifier.width(26.dp).height(5.dp)
                                .background(c?.subtext ?: MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(3.dp)),
                        )
                        Box(
                            Modifier.width(16.dp).height(8.dp)
                                .background(c?.accent ?: MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                        )
                    }
                }
                Text(
                    palette.label,
                    style = SmallLabel,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun AccentPicker(selected: Int?, onPick: (Int?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        if (selected == null) 2.dp else 1.dp,
                        if (selected == null) colors.primary else colors.outlineVariant,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onPick(null) }
                    .padding(horizontal = 12.dp),
            ) {
                Text("Default", style = MaterialTheme.typography.labelLarge)
            }
        }
        items(AccentChoices) { color ->
            val isSelected = selected == color.toArgb()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color)
                    .clickable { onPick(color.toArgb()) },
            ) {
                if (isSelected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color(0xFF101014))
            }
        }
    }
}

/** Enabled tabs first in their order (with up/down), then the hidden ones. One must stay. */
@Composable
private fun TabManager(enabled: List<LibraryTab>, onChange: (List<LibraryTab>) -> Unit) {
    val hidden = LibraryTab.entries.filterNot { it in enabled }
    Column(Modifier.padding(top = 6.dp)) {
        enabled.forEachIndexed { index, tab ->
            TabRow(
                tab = tab,
                checked = true,
                canToggle = enabled.size > 1,
                onToggle = { onChange(enabled - tab) },
                onUp = if (index > 0) {
                    { onChange(enabled.toMutableList().apply { add(index - 1, removeAt(index)) }) }
                } else {
                    null
                },
                onDown = if (index < enabled.lastIndex) {
                    { onChange(enabled.toMutableList().apply { add(index + 1, removeAt(index)) }) }
                } else {
                    null
                },
            )
        }
        hidden.forEach { tab ->
            TabRow(tab, checked = false, canToggle = true, onToggle = { onChange(enabled + tab) }, onUp = null, onDown = null)
        }
    }
}

@Composable
private fun TabRow(
    tab: LibraryTab,
    checked: Boolean,
    canToggle: Boolean,
    onToggle: () -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = canToggle)
        Text(
            tab.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (checked) {
            IconButton(onClick = { onUp?.invoke() }, enabled = onUp != null) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move ${tab.label} up")
            }
            IconButton(onClick = { onDown?.invoke() }, enabled = onDown != null) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move ${tab.label} down")
            }
        }
    }
}

@Composable
private fun WallpaperControls(
    appearance: Appearance,
    settings: SettingsStore,
    wallpapers: WallpaperStore,
    onAdjust: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var importFailed by remember { mutableStateOf(false) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = wallpapers.import(uri)
                importFailed = !ok
                if (ok) settings.setWallpaper(true)
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
        Button(onClick = {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) {
            Text(if (appearance.hasWallpaper) "Change" else "Choose picture")
        }
        if (appearance.hasWallpaper) {
            OutlinedButton(onClick = onAdjust) { Text("Position") }
            OutlinedButton(onClick = {
                scope.launch {
                    settings.setWallpaper(false)
                    wallpapers.clear()
                }
            }) { Text("Remove") }
        }
    }
    if (importFailed) Hint("Couldn't open that picture. Try another one.")

    if (appearance.hasWallpaper) {
        // Sliders move freely and save once when released.
        var blur by remember(appearance.blur) { mutableFloatStateOf(appearance.blur) }
        var dim by remember(appearance.dim) { mutableFloatStateOf(appearance.dim) }
        val sliderColors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest)

        Label("Blur")
        Slider(
            value = blur,
            onValueChange = { blur = it },
            onValueChangeFinished = { scope.launch { settings.setBlur(blur) } },
            colors = sliderColors,
        )
        Label("Dim")
        Slider(
            value = dim,
            onValueChange = { dim = it },
            valueRange = 0f..0.9f,
            onValueChangeFinished = { scope.launch { settings.setDim(dim) } },
            colors = sliderColors,
        )
    } else {
        Spacer(Modifier.height(4.dp))
    }
}
