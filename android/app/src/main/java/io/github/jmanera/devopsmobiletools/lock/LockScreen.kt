// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jmanera.devopsmobiletools.DevOpsMobileToolsApp
import io.github.jmanera.devopsmobiletools.R
import io.github.jmanera.devopsmobiletools.ui.Brand
import io.github.jmanera.devopsmobiletools.ui.BrandLogo
import io.github.jmanera.devopsmobiletools.ui.StatusPill

/** Covers the app while locked; asks to unlock as soon as it shows. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val sessions = (LocalContext.current.applicationContext as DevOpsMobileToolsApp).sessions.live.size
    LaunchedEffect(Unit) { onUnlock() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Brand.Navy, Color(0xFF0B3D7A), Brand.Blue))),
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandLogo(88.dp)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Box(
                Modifier.size(56.dp).background(Color.White.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Text("Locked", style = MaterialTheme.typography.titleMedium, color = Color.White)
            if (sessions > 0) {
                StatusPill(
                    if (sessions == 1) "1 session keeps running" else "$sessions sessions keep running",
                    Brand.Cyan,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onUnlock,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand.Navy),
            ) {
                Text("Unlock", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
