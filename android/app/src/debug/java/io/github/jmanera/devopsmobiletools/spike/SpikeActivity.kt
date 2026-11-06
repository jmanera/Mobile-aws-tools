// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.spike

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import io.github.jmanera.devopsmobiletools.session.PlainTerminal
import io.github.jmanera.devopsmobiletools.ui.BrandTheme

class SpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep credentials and terminal content out of screenshots and recents.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            BrandTheme {
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
        if (!vm.connected) ConnectForm(vm) else PlainTerminal(vm.output, vm::sendLine, vm::send, vm::write, vm::disconnect)
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
        val filesDir = LocalContext.current.filesDir
        OutlinedButton(onClick = { vm.loadCredentials(File(filesDir, "credentials.json")) },
            modifier = Modifier.fillMaxWidth()) { Text("Load credentials file") }
        Button(onClick = vm::connect, modifier = Modifier.fillMaxWidth()) { Text("Start session") }
        Button(onClick = vm::connectWithSignedInRole, modifier = Modifier.fillMaxWidth()) {
            Text("Start session with the selected role and region")
        }
        if (vm.output.isNotEmpty()) {
            Text(vm.output.takeLast(2000), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}
