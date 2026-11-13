package com.suna.player.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suna.player.R
import com.suna.player.ui.theme.LocalIsLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * A short message in a rounded card with the app's icon, like the ones Samsung Music shows.
 * It sits just above the mini player ([aboveBottom] is the space the mini player takes) and
 * fades away after a couple of seconds. Android's own toasts can't be moved off the bottom
 * edge, which is why the app draws its own.
 */
@Composable
fun NoticeHost(messages: Flow<String>, aboveBottom: Dp, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    var serial by remember { mutableIntStateOf(0) }
    LaunchedEffect(messages) {
        messages.collect {
            text = it
            shown = true
            serial++
        }
    }
    // Each new message restarts the clock, even if it says the same thing as the last one.
    LaunchedEffect(serial) {
        if (serial > 0) {
            delay(2500)
            shown = false
        }
    }

    Box(modifier.navigationBarsPadding().padding(bottom = aboveBottom + 8.dp)) {
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 },
            exit = fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val light = LocalIsLight.current
            val shape = RoundedCornerShape(24.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (light) Color(0xFFF2F2F5) else Color(0xFF2C2C30))
                    .border(1.dp, if (light) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.06f), shape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                // The launcher artwork on its black disc, as the system shows app icons.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(Color.Black),
                ) {
                    Image(
                        painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp).scale(1.45f),
                    )
                }
                Text(
                    text,
                    fontSize = 15.sp,
                    color = if (light) Color(0xFF1C1C1E) else Color(0xFFF2F2F5),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}
