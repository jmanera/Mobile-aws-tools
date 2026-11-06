// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.instances

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jmanera.devopsmobiletools.DevOpsMobileToolsApp
import io.github.jmanera.devopsmobiletools.errors.FailureAction
import io.github.jmanera.devopsmobiletools.plugin.Target
import io.github.jmanera.devopsmobiletools.plugins.ec2shell.Ec2ShellPlugin

@Composable
fun InstancesScreen(
    onOpen: (Target) -> Unit,
    onChangeRole: () -> Unit,
    vm: InstancesViewModel = viewModel(),
) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Instances", style = MaterialTheme.typography.headlineSmall)
                vm.selection?.let {
                    Text(
                        "${it.role.roleName} · ${it.role.accountName} · ${it.region}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            TextButton(onClick = onChangeRole) { Text("Change") }
            TextButton(onClick = vm::refresh, enabled = vm.state != InstancesState.Loading) { Text("Refresh") }
        }

        when (val state = vm.state) {
            InstancesState.Loading -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Looking for instances with the SSM Agent online…")
            }

            is InstancesState.Failed -> {
                Text(state.failure.message, color = MaterialTheme.colorScheme.error)
                when (state.failure.action) {
                    FailureAction.SIGN_IN -> OutlinedButton(onClick = onChangeRole, modifier = Modifier.fillMaxWidth()) {
                        Text("Sign in again")
                    }
                    FailureAction.CHANGE_ROLE -> OutlinedButton(onClick = onChangeRole, modifier = Modifier.fillMaxWidth()) {
                        Text("Back to account and role")
                    }
                    FailureAction.RETRY -> OutlinedButton(onClick = vm::refresh, modifier = Modifier.fillMaxWidth()) {
                        Text("Try again")
                    }
                }
            }

            is InstancesState.Loaded -> if (state.targets.isEmpty()) {
                Text("No instances online in ${vm.selection?.region ?: "this region"}.")
                Text(
                    "Only EC2 instances whose SSM Agent is online are listed: check that the instance is " +
                        "running, has the AmazonSSMManagedInstanceCore policy on its role, and appears in " +
                        "Systems Manager → Fleet Manager. Or pick another region.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                OutlinedTextField(
                    vm.query, { vm.query = it },
                    label = { Text("Filter by name, id or IP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val visible = vm.visible
                if (visible.isEmpty()) Text("No instance matches “${vm.query.trim()}”.")
                val sessions = (LocalContext.current.applicationContext as DevOpsMobileToolsApp).sessions
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visible, key = { it.id }) { t ->
                        val session = sessions.sessions.firstOrNull { it.target.id == t.id }
                        InstanceCard(t, session?.let { if (it.ended) "Session ended" else "Session open" }, onOpen)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstanceCard(target: Target, sessionLabel: String?, onOpen: (Target) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onOpen(target) }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(target.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                sessionLabel?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            val details = listOfNotNull(
                target.id.takeIf { it != target.name },
                target.attributes[Ec2ShellPlugin.ATTR_PLATFORM],
                target.attributes[Ec2ShellPlugin.ATTR_IP_ADDRESS],
            )
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
