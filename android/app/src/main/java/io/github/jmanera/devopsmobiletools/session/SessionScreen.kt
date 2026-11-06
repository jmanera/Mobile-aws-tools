// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.session

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.jmanera.devopsmobiletools.DevOpsMobileToolsApp
import io.github.jmanera.devopsmobiletools.errors.FailureAction
import io.github.jmanera.devopsmobiletools.plugin.Target
import io.github.jmanera.devopsmobiletools.terminal.createTerminalView

/**
 * The session on [target], opened if there is none. Going back keeps it
 * running in the background; Disconnect ends it.
 */
@Composable
fun SessionScreen(target: Target, onBack: () -> Unit, onChangeRole: () -> Unit) {
    val app = LocalContext.current.applicationContext as DevOpsMobileToolsApp
    val vm = remember(target.id) { app.sessions.open(target) }
    RequestNotificationPermission()

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
            TextButton(onClick = { app.sessions.close(vm); onBack() }) {
                Text(if (vm.ended) "Close" else "Disconnect")
            }
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
        } else {
            val modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            when (vm.failure?.action) {
                FailureAction.SIGN_IN -> Button(onClick = onChangeRole, modifier = modifier) { Text("Sign in again") }
                FailureAction.CHANGE_ROLE -> Button(onClick = onChangeRole, modifier = modifier) { Text("Change role") }
                else -> Button(onClick = vm::connect, modifier = modifier) { Text("Reconnect") }
            }
        }
    }
}

/**
 * Asks once for notifications (Android 13+), so the notification of open
 * sessions is visible. Sessions stay alive without it; it only hides them.
 */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
