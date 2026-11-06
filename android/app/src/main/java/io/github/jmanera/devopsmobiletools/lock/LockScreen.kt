// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jmanera.devopsmobiletools.DevOpsMobileToolsApp
import io.github.jmanera.devopsmobiletools.R

/** Covers the app while locked; asks to unlock as soon as it shows. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val sessions = (LocalContext.current.applicationContext as DevOpsMobileToolsApp).sessions.live.size
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text("Locked", style = MaterialTheme.typography.titleMedium)
        if (sessions > 0) {
            Text(
                if (sessions == 1) "1 session keeps running" else "$sessions sessions keep running",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(onClick = onUnlock) { Text("Unlock") }
    }
}
