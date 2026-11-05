// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.spike

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep credentials and terminal content out of screenshots and recents.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { SpikeScreen() }
            }
        }
    }
}

@Composable
private fun SpikeScreen(vm: SpikeViewModel = viewModel()) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(vm.status, style = MaterialTheme.typography.labelLarge)
        if (!vm.connected) ConnectForm(vm) else Terminal(vm, Modifier.weight(1f))
    }
}

@Composable
private fun ConnectForm(vm: SpikeViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(vm.region, { vm.region = it }, label = { Text("Region") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(vm.targetId, { vm.targetId = it }, label = { Text("Instance ID (i-…)") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            vm.credentialsJson, { vm.credentialsJson = it },
            label = { Text("Credentials JSON (aws configure export-credentials)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = vm::connect, modifier = Modifier.fillMaxWidth()) { Text("Start session") }
        if (vm.output.isNotEmpty()) {
            Text(vm.output.takeLast(2000), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Terminal(vm: SpikeViewModel, modifier: Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(vm.output) { scroll.scrollTo(scroll.maxValue) }
    Text(
        vm.output,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = modifier.fillMaxWidth().verticalScroll(scroll),
    )

    var line by remember { mutableStateOf("") }
    OutlinedTextField(
        line, { line = it },
        placeholder = { Text("command") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onSend = { vm.sendLine(line); line = "" }),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KeyButton("Enter") { vm.sendLine(line); line = "" }
        KeyButton("Ctrl+C") { vm.write(byteArrayOf(0x03)) }
        KeyButton("Ctrl+D") { vm.write(byteArrayOf(0x04)) }
        KeyButton("Tab") { vm.send(line + "\t"); line = "" }
        KeyButton("Esc") { vm.write(byteArrayOf(0x1b)) }
        KeyButton("↑") { vm.send("\u001b[A") }
        KeyButton("↓") { vm.send("\u001b[B") }
        KeyButton("Close") { vm.disconnect() }
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(label) }
}
