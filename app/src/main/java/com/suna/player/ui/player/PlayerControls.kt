package com.suna.player.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.media3.common.Player
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Icons for the player, drawn on a 24 x 24 grid with round, light strokes.

/** Draws in the 24-unit icon grid, whatever size the canvas is. */
private fun DrawScope.inGrid(block: DrawScope.() -> Unit) {
    val s = size.minDimension / 24f
    scale(s, s, pivot = Offset.Zero) { block() }
}

private fun DrawScope.line(color: Color, path: Path, width: Float = 1.8f) {
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * Shuffle: two arrows crossing. When shuffle is off they cross less and a slash is drawn
 * through them; turning it on swings them fully across and wipes the slash away.
 */
@Composable
fun ShuffleGlyph(on: Boolean, tint: Color, modifier: Modifier = Modifier) {
    val cross by animateFloatAsState(
        targetValue = if (on) 1f else 0.55f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "shuffleCross",
    )
    val slash by animateFloatAsState(
        targetValue = if (on) 0f else 1f,
        animationSpec = tween(220),
        label = "shuffleSlash",
    )
    Canvas(modifier.size(26.dp)) {
        inGrid {
            val aEnd = lerp(16.5f, 7f, cross)
            val bEnd = lerp(7.5f, 17f, cross)
            val a = Path().apply {
                moveTo(3.5f, 17f)
                lineTo(6.5f, 17f)
                cubicTo(11f, 17f, 12f, aEnd, 16.5f, aEnd)
                lineTo(20f, aEnd)
                moveTo(17.5f, aEnd - 2.5f)
                lineTo(20f, aEnd)
                lineTo(17.5f, aEnd + 2.5f)
            }
            val b = Path().apply {
                moveTo(3.5f, 7f)
                lineTo(6.5f, 7f)
                cubicTo(11f, 7f, 12f, bEnd, 16.5f, bEnd)
                lineTo(20f, bEnd)
                moveTo(17.5f, bEnd - 2.5f)
                lineTo(20f, bEnd)
                lineTo(17.5f, bEnd + 2.5f)
            }
            line(tint, a)
            line(tint, b)
            if (slash > 0f) {
                // Drawn from the top left down, so it wipes in and out.
                val end = Offset(lerp(3f, 21f, slash), lerp(3f, 21f, slash))
                drawLine(tint, Offset(3f, 3f), end, strokeWidth = 2f, cap = StrokeCap.Round)
            }
        }
    }
}

/**
 * Repeat, in three modes: off is an "A" over a straight arrow (play through once); repeat all
 * puts the "A" inside a loop of two arrows; repeat one puts a "1" inside the loop. The loop
 * spins a full turn on every change.
 */
@Composable
fun RepeatGlyph(mode: Int, tint: Color, modifier: Modifier = Modifier) {
    val spin = remember { Animatable(0f) }
    // Spin only on a real change, not when the player first appears.
    var shownMode by remember { mutableIntStateOf(mode) }
    LaunchedEffect(mode) {
        if (mode != shownMode) {
            shownMode = mode
            spin.animateTo(spin.targetValue + 360f, spring(dampingRatio = 0.7f, stiffness = 260f))
        }
    }
    val loop by animateFloatAsState(
        targetValue = if (mode == Player.REPEAT_MODE_OFF) 0f else 1f,
        animationSpec = tween(240),
        label = "repeatLoop",
    )
    val one by animateFloatAsState(
        targetValue = if (mode == Player.REPEAT_MODE_ONE) 1f else 0f,
        animationSpec = tween(220),
        label = "repeatOne",
    )
    Canvas(modifier.size(26.dp)) {
        inGrid {
            // Off: a large A over an arrow running straight through.
            if (loop < 1f) {
                val faded = tint.copy(alpha = tint.alpha * (1f - loop))
                val bigA = Path().apply {
                    moveTo(8.6f, 13f)
                    lineTo(12f, 4.8f)
                    lineTo(15.4f, 13f)
                    moveTo(9.8f, 10.2f)
                    lineTo(14.2f, 10.2f)
                }
                val arrow = Path().apply {
                    moveTo(4.5f, 18f)
                    lineTo(19.5f, 18f)
                    moveTo(17f, 15.5f)
                    lineTo(19.5f, 18f)
                    lineTo(17f, 20.5f)
                }
                line(faded, bigA)
                line(faded, arrow)
            }
            if (loop > 0f) {
                val shown = tint.copy(alpha = tint.alpha * loop)
                rotate(spin.value, pivot = Offset(12f, 12f)) {
                    val top = Path().apply {
                        moveTo(4.5f, 12.5f)
                        lineTo(4.5f, 10f)
                        quadraticTo(4.5f, 7f, 7.5f, 7f)
                        lineTo(19f, 7f)
                        moveTo(16.5f, 4.5f)
                        lineTo(19f, 7f)
                        lineTo(16.5f, 9.5f)
                    }
                    val bottom = Path().apply {
                        moveTo(19.5f, 11.5f)
                        lineTo(19.5f, 14f)
                        quadraticTo(19.5f, 17f, 16.5f, 17f)
                        lineTo(5f, 17f)
                        moveTo(7.5f, 14.5f)
                        lineTo(5f, 17f)
                        lineTo(7.5f, 19.5f)
                    }
                    line(shown, top)
                    line(shown, bottom)
                }
                // In the loop: a small A for "all", a 1 for "this track".
                if (one < 1f) {
                    scale(1f - one, 1f - one, pivot = Offset(12f, 12f)) {
                        val smallA = Path().apply {
                            moveTo(10.3f, 14.2f)
                            lineTo(12f, 9.8f)
                            lineTo(13.7f, 14.2f)
                            moveTo(10.9f, 12.7f)
                            lineTo(13.1f, 12.7f)
                        }
                        line(shown, smallA, width = 1.5f)
                    }
                }
                if (one > 0f) {
                    scale(one, one, pivot = Offset(12f, 12f)) {
                        val digit = Path().apply {
                            moveTo(10.8f, 10.6f)
                            lineTo(12.2f, 9.6f)
                            lineTo(12.2f, 14.4f)
                        }
                        line(shown, digit, width = 1.6f)
                    }
                }
            }
        }
    }
}

/**
 * A round button that also reacts to being held: [onHoldTick] runs once after a short wait,
 * then every [tickMs] while the finger stays down, with a light tick of haptic feedback each
 * time. A quick tap runs [onTap]; [onRelease] runs when a hold ends.
 */
@Composable
fun HoldButton(
    onTap: () -> Unit,
    onHoldTick: (count: Int) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    holdAfterMs: Long = 420,
    tickMs: Long = 600,
    onRelease: (count: Int) -> Unit = {},
    label: String,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val tap by rememberUpdatedState(onTap)
    val tick by rememberUpdatedState(onHoldTick)
    val release by rememberUpdatedState(onRelease)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick { tap(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    var count = 0
                    coroutineScope {
                        val holding = launch {
                            delay(holdAfterMs)
                            while (true) {
                                count++
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                tick(count)
                                delay(tickMs)
                            }
                        }
                        tryAwaitRelease()
                        holding.cancel()
                    }
                    if (count == 0) tap() else release(count)
                })
            },
    ) { content() }
}
