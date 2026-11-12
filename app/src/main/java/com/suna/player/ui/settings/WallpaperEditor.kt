package com.suna.player.ui.settings

import com.suna.player.ui.theme.LocalIsLight
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.suna.player.data.Appearance
import com.suna.player.data.SettingsStore
import com.suna.player.ui.theme.SmallLabel
import com.suna.player.ui.theme.WallpaperImage

/**
 * Pinch to zoom and drag to move the wallpaper. Zooming out below the screen size leaves the
 * theme's background color (black on OLED) around the picture.
 */
@Composable
fun WallpaperEditor(
    wallpaper: ImageBitmap?,
    appearance: Appearance,
    onSave: (scale: Float, x: Float, y: Float) -> Unit,
    onClose: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(appearance.wallpaperScale) }
    var x by remember { mutableFloatStateOf(appearance.wallpaperX) }
    var y by remember { mutableFloatStateOf(appearance.wallpaperY) }

    Box(
        Modifier
            .fillMaxSize()
            // Space the picture doesn't cover is true black (simply off on OLED), or white in
            // the light version.
            .background(if (LocalIsLight.current) Color.White else Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(
                        SettingsStore.MIN_WALLPAPER_SCALE,
                        SettingsStore.MAX_WALLPAPER_SCALE,
                    )
                    x = (x + pan.x / size.width).coerceIn(-1f, 1f)
                    y = (y + pan.y / size.height).coerceIn(-1f, 1f)
                }
            },
    ) {
        if (wallpaper != null) {
            WallpaperImage(wallpaper, scale, x, y)
            val veil = if (LocalIsLight.current) Color.White else Color.Black
            Box(Modifier.fillMaxSize().background(veil.copy(alpha = appearance.dim)))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
        ) {
            Text(
                "Pinch to zoom, drag to move",
                style = SmallLabel,
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Text(
                "${(scale * 100).roundToInt()}%",
                style = SmallLabel,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 6.dp),
            )
            Box(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = {
                    scale = 1f
                    x = 0f
                    y = 0f
                }) { Text("Reset") }
                Box(Modifier.weight(1f))
                OutlinedButton(onClick = onClose) { Text("Cancel") }
                Button(onClick = {
                    onSave(scale, x, y)
                    onClose()
                }) { Text("Save") }
            }
        }
    }
}
