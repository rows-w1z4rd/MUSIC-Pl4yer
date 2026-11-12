package com.suna.player.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Long-pressing what's inside shows [text] in a small bubble: what a button does, or which
 * mode it is in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithHint(text: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        content = content,
    )
}

/** An icon button that says what it does (or which mode it's in) when long-pressed. */
@Composable
fun HintIconButton(
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    WithHint(hint) { IconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content) }
}
