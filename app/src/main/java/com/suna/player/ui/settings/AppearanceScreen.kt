package com.suna.player.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.suna.player.ui.common.LocalMiniPlayerSpace
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.suna.player.data.Appearance
import com.suna.player.data.SettingsStore
import com.suna.player.data.ThemeMode
import com.suna.player.data.WallpaperStore
import kotlinx.coroutines.launch

private val accentChoices = listOf(
    Color(0xFF4F8EF7),
    Color(0xFF7C5CFC),
    Color(0xFFE8618C),
    Color(0xFFE5534B),
    Color(0xFFF0883E),
    Color(0xFFE3B341),
    Color(0xFF3FB950),
    Color(0xFF2BB5A8),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    appearance: Appearance,
    settings: SettingsStore,
    wallpapers: WallpaperStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var importFailed by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = wallpapers.import(uri)
                importFailed = !ok
                if (ok) settings.setWallpaper(true)
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            SectionTitle("Theme")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = appearance.themeMode == mode,
                        onClick = { scope.launch { settings.setThemeMode(mode) } },
                        label = { Text(mode.label) },
                    )
                }
            }
            if (appearance.hasWallpaper) {
                Hint("With a wallpaper the app always uses light text so it stays readable.")
            }

            SectionTitle("Accent color")
            FilterChip(
                selected = appearance.accent == null,
                onClick = { scope.launch { settings.setAccent(null) } },
                label = { Text("Use the phone's colors") },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                accentChoices.forEach { color ->
                    val selected = appearance.accent == color.toArgb()
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (selected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { scope.launch { settings.setAccent(color.toArgb()) } },
                    )
                }
            }

            SectionTitle("Wallpaper")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }) {
                    Text(if (appearance.hasWallpaper) "Change picture" else "Choose picture")
                }
                if (appearance.hasWallpaper) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            settings.setWallpaper(false)
                            wallpapers.clear()
                        }
                    }) {
                        Text("Remove")
                    }
                }
            }
            if (importFailed) Hint("That picture could not be opened. Try another one.")

            if (appearance.hasWallpaper) {
                // Sliders move freely and save once when released.
                var blur by remember(appearance.blur) { mutableFloatStateOf(appearance.blur) }
                var dim by remember(appearance.dim) { mutableFloatStateOf(appearance.dim) }

                Text("Blur", Modifier.padding(top = 16.dp))
                Slider(
                    value = blur,
                    onValueChange = { blur = it },
                    onValueChangeFinished = { scope.launch { settings.setBlur(blur) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Darken")
                Slider(
                    value = dim,
                    onValueChange = { dim = it },
                    valueRange = 0f..0.9f,
                    onValueChangeFinished = { scope.launch { settings.setDim(dim) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Keep the last control clear of the floating mini player.
            Spacer(Modifier.height(LocalMiniPlayerSpace.current + 16.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}
