// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.lock

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.jmanera.devopsmobiletools.DevOpsMobileToolsApp
import java.time.Duration

/** App lock on or off, and how long the app may stay in the background before it locks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LockSettingsSection() {
    val lock = (LocalContext.current.applicationContext as DevOpsMobileToolsApp).lock
    val canAuthenticate = remember { lock.canAuthenticate() }
    var enabled by remember { mutableStateOf(lock.settings.enabled) }
    var timeout by remember { mutableStateOf(lock.settings.timeout) }

    Text("Security", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Lock with biometrics or the screen lock", Modifier.weight(1f))
        Switch(
            checked = enabled && canAuthenticate,
            enabled = canAuthenticate,
            onCheckedChange = {
                enabled = it
                lock.settings.enabled = it
            },
        )
    }
    if (!canAuthenticate) {
        Text(
            "Set up a screen lock in Android's settings to lock the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    } else if (enabled) {
        Text("Lock after the app is in the background for", style = MaterialTheme.typography.bodyMedium)
        FlowRow {
            for (choice in LockSettings.TIMEOUTS) {
                FilterChip(
                    selected = choice == timeout,
                    onClick = {
                        timeout = choice
                        lock.settings.timeout = choice
                    },
                    label = { Text(describe(choice)) },
                )
            }
        }
    }
}

private fun describe(d: Duration): String = when {
    d.isZero -> "Immediately"
    d.toHours() >= 1 -> "${d.toHours()} h"
    else -> "${d.toMinutes()} min"
}
