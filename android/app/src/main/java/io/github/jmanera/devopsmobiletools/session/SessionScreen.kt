// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.session

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jmanera.devopsmobiletools.plugin.Target
import io.github.jmanera.devopsmobiletools.terminal.createTerminalView

@Composable
fun SessionScreen(target: Target, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val vm: SessionViewModel = viewModel(key = target.id) { SessionViewModel(app, target) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(target.name, style = MaterialTheme.typography.titleMedium)
                Text(vm.status, style = MaterialTheme.typography.labelMedium)
            }
            if (vm.ended) TextButton(onClick = vm::connect) { Text("Reconnect") }
            TextButton(onClick = onBack) { Text("Instances") }
        }

        val terminal = vm.terminal
        AndroidView(
            factory = { context ->
                val prefs = TerminalPrefs(context)
                createTerminalView(context, vm.modifiers, prefs.textSizeSp) { prefs.textSizeSp = it }
            },
            // Reading vm.terminal here re-runs update when a reconnection brings a new terminal.
            update = { view -> terminal?.attach(view) },
            onRelease = { view -> vm.terminal?.detach(view) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        if (!vm.ended) {
            ExtraKeysRow(
                modifierState = { vm.modifierStates.getValue(it) },
                onKey = vm::press,
                onLock = vm::lock,
            )
        } else Button(onClick = vm::connect, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Text("Reconnect")
        }
    }
}
