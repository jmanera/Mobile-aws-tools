// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.session

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jmanera.devopsmobiletools.terminal.ExtraKey
import io.github.jmanera.devopsmobiletools.terminal.StickyModifiers
import io.github.jmanera.devopsmobiletools.ui.Brand
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.jmanera.devopsmobiletools.terminal.R as TerminalR

private val KeysFont = FontFamily(Font(TerminalR.font.jetbrains_mono_nl_regular))
private val RowBackground = Brand.Navy
private val KeyBackground = Color(0xFF13355A)
private val KeyColor = Color(0xFFE0E0E0)
private val ActiveColor = Brand.Cyan

/**
 * Keys a phone keyboard lacks, on the brand's navy. Arrows and page
 * keys repeat while held; CTRL and ALT apply to the next key on a tap and
 * stay on after a long press, until tapped again.
 */
@Composable
fun ExtraKeysRow(
    modifierState: (io.github.jmanera.devopsmobiletools.terminal.Modifier) -> StickyModifiers.State,
    onKey: (ExtraKey) -> Unit,
    onLock: (io.github.jmanera.devopsmobiletools.terminal.Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(RowBackground).padding(horizontal = 2.dp, vertical = 4.dp)) {
        for (row in ExtraKey.ROWS) {
            Row(Modifier.fillMaxWidth().height(42.dp)) {
                for (key in row) {
                    val state = (key as? ExtraKey.Mod)?.let { modifierState(it.modifier) } ?: StickyModifiers.State.OFF
                    KeyCell(key, state, onKey, onLock, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun KeyCell(
    key: ExtraKey,
    state: StickyModifiers.State,
    onKey: (ExtraKey) -> Unit,
    onLock: (io.github.jmanera.devopsmobiletools.terminal.Modifier) -> Unit,
    modifier: Modifier,
) {
    val view = LocalView.current
    val press = {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onKey(key)
    }
    Box(
        modifier
            .fillMaxHeight()
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (state == StickyModifiers.State.LOCKED) ActiveColor.copy(alpha = 0.35f) else KeyBackground)
            .pointerInput(key) {
                detectTapGestures(
                    onPress = {
                        if (key.repeats) coroutineScope {
                            val repeat = launch {
                                press()
                                delay(REPEAT_DELAY_MS)
                                while (true) {
                                    onKey(key)
                                    delay(REPEAT_INTERVAL_MS)
                                }
                            }
                            tryAwaitRelease()
                            repeat.cancel()
                        }
                    },
                    onTap = { if (!key.repeats) press() },
                    onLongPress = {
                        if (key is ExtraKey.Mod) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onLock(key.modifier)
                        } else if (!key.repeats) {
                            press()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            key.label,
            color = if (state == StickyModifiers.State.OFF) KeyColor else ActiveColor,
            fontFamily = KeysFont,
            fontSize = 14.sp,
            textDecoration = if (state == StickyModifiers.State.LOCKED) TextDecoration.Underline else null,
        )
    }
}

private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 60L
