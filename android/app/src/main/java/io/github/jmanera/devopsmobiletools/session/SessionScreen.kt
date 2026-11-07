// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.session

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.jmanera.devopsmobiletools.DevOpsMobileToolsApp
import io.github.jmanera.devopsmobiletools.errors.FailureAction
import io.github.jmanera.devopsmobiletools.plugin.ConnectionState
import io.github.jmanera.devopsmobiletools.plugin.Target
import io.github.jmanera.devopsmobiletools.terminal.createTerminalView
import io.github.jmanera.devopsmobiletools.ui.Brand
import io.github.jmanera.devopsmobiletools.ui.StatusDot

/**
 * The session on [target], opened if there is none. Going back keeps it
 * running in the background; Disconnect ends it.
 */
@Composable
fun SessionScreen(target: Target, onBack: () -> Unit, onChangeRole: () -> Unit) {
    val app = LocalContext.current.applicationContext as DevOpsMobileToolsApp
    val vm = remember(target.id) { app.sessions.open(target) }
    RequestNotificationPermission()

    Column(Modifier.fillMaxSize().background(TerminalBackground)) {
        SessionBar(target, vm, onBack = onBack, onDisconnect = { app.sessions.close(vm); onBack() })

        Column(
            Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .imePadding(),
        ) {
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
                Column(
                    Modifier.fillMaxWidth().background(Brand.Navy).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val modifier = Modifier.fillMaxWidth().height(52.dp)
                    val colors = ButtonDefaults.buttonColors(containerColor = Brand.Cyan, contentColor = Brand.Navy)
                    when (vm.failure?.action) {
                        FailureAction.SIGN_IN -> Button(onClick = onChangeRole, modifier = modifier, colors = colors) {
                            Text("Sign in again")
                        }
                        FailureAction.CHANGE_ROLE -> Button(onClick = onChangeRole, modifier = modifier, colors = colors) {
                            Text("Change role")
                        }
                        else -> Button(onClick = vm::connect, modifier = modifier, colors = colors) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                            Text("Reconnect", Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Back to the instances, the target and its state, and the actions on the session. */
@Composable
private fun SessionBar(target: Target, vm: LiveSession, onBack: () -> Unit, onDisconnect: () -> Unit) {
    val stateColor = when (vm.state) {
        ConnectionState.CONNECTED -> Brand.Success
        ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> Brand.Warning
        ConnectionState.CLOSED, ConnectionState.FAILED -> EndedColor
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brand.Navy)
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Instances", tint = Color.White)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                target.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusDot(stateColor, 8.dp)
                Text(
                    vm.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = Brand.Neutral.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (vm.ended) {
            TextButton(onClick = vm::connect, colors = ButtonDefaults.textButtonColors(contentColor = Brand.Cyan)) {
                Text("Reconnect")
            }
        }
        TextButton(onClick = onDisconnect, colors = ButtonDefaults.textButtonColors(contentColor = EndedColor)) {
            Text(if (vm.ended) "Close" else "Disconnect")
        }
    }
}

private val TerminalBackground = Color.Black
private val EndedColor = Color(0xFFF87171)

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
