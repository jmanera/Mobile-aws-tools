// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.session

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jmanera.devopsmobiletools.plugin.ConnectionState
import io.github.jmanera.devopsmobiletools.plugin.Target

/** Shell output as plain text, kept scrolled to the bottom. */
@Composable
fun TerminalOutput(output: String, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(output) { scroll.scrollTo(scroll.maxValue) }
    Text(
        output,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = modifier.fillMaxWidth().verticalScroll(scroll),
    )
}

/**
 * Output, a command line and a row of keys a phone keyboard lacks. Used by
 * the debug spike; sessions in the app use the terminal emulator.
 */
@Composable
fun ColumnScope.PlainTerminal(
    output: String,
    onLine: (String) -> Unit,
    onText: (String) -> Unit,
    onBytes: (ByteArray) -> Unit,
    onClose: () -> Unit,
) {
    TerminalOutput(output, Modifier.weight(1f))

    var line by remember { mutableStateOf("") }
    OutlinedTextField(
        line, { line = it },
        placeholder = { Text("command") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onSend = { onLine(line); line = "" }),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KeyButton("Enter") { onLine(line); line = "" }
        KeyButton("Ctrl+C") { onBytes(byteArrayOf(0x03)) }
        KeyButton("Ctrl+D") { onBytes(byteArrayOf(0x04)) }
        KeyButton("Tab") { onText(line + "\t"); line = "" }
        KeyButton("Esc") { onBytes(byteArrayOf(0x1b)) }
        KeyButton("↑") { onText("\u001b[A") }
        KeyButton("↓") { onText("\u001b[B") }
        KeyButton("Close") { onClose() }
    }
}

@Composable
internal fun KeyButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(label) }
}
