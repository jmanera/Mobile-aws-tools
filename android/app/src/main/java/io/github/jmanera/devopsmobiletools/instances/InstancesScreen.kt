// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.instances

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jmanera.devopsmobiletools.DevOpsMobileToolsApp
import io.github.jmanera.devopsmobiletools.errors.FailureAction
import io.github.jmanera.devopsmobiletools.plugin.Target
import io.github.jmanera.devopsmobiletools.plugins.ec2shell.Ec2ShellPlugin
import io.github.jmanera.devopsmobiletools.ui.Banner
import io.github.jmanera.devopsmobiletools.ui.Brand
import io.github.jmanera.devopsmobiletools.ui.BrandLogo
import io.github.jmanera.devopsmobiletools.ui.IconBadge
import io.github.jmanera.devopsmobiletools.ui.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstancesScreen(
    onOpen: (Target) -> Unit,
    onChangeRole: () -> Unit,
    vm: InstancesViewModel = viewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BrandLogo(32.dp)
                        Text("Instances")
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh, enabled = vm.state != InstancesState.Loading) {
                        Icon(Icons.Rounded.Refresh, "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Always shown, so the account and role can be changed while loading or after a failure.
            AssistChip(
                onClick = onChangeRole,
                label = {
                    Text(
                        vm.selection?.let { "${it.role.roleName} · ${it.role.accountName} · ${it.region}" }
                            ?: "Change account and role",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = { Icon(Icons.Rounded.Edit, "Change", Modifier.size(16.dp)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                border = null,
            )

            when (val state = vm.state) {
                InstancesState.Loading -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small))
                    Text(
                        "Looking for instances with the SSM Agent online…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is InstancesState.Failed -> Banner(state.failure.message) {
                    val modifier = Modifier.fillMaxWidth()
                    when (state.failure.action) {
                        FailureAction.SIGN_IN -> OutlinedButton(onClick = onChangeRole, modifier = modifier) {
                            Text("Sign in again")
                        }
                        FailureAction.CHANGE_ROLE -> OutlinedButton(onClick = onChangeRole, modifier = modifier) {
                            Text("Back to account and role")
                        }
                        FailureAction.RETRY -> OutlinedButton(onClick = vm::refresh, modifier = modifier) {
                            Text("Try again")
                        }
                    }
                }

                is InstancesState.Loaded -> PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = vm::refresh,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.targets.isEmpty()) {
                        EmptyState(vm.selection?.region)
                    } else {
                        InstanceList(vm, state.targets.size, onOpen)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstanceList(vm: InstancesViewModel, total: Int, onOpen: (Target) -> Unit) {
    val visible = vm.visible
    val sessions = (LocalContext.current.applicationContext as DevOpsMobileToolsApp).sessions
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            OutlinedTextField(
                vm.query, { vm.query = it },
                placeholder = { Text("Filter by name, id or IP") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) {
                        IconButton(onClick = { vm.query = "" }) { Icon(Icons.Rounded.Clear, "Clear the filter") }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                if (visible.size == total) countLabel(total) else "${visible.size} of ${countLabel(total)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
        if (visible.isEmpty()) {
            item {
                Text(
                    "No instance matches “${vm.query.trim()}”.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        items(visible, key = { it.id }) { t ->
            val session = sessions.sessions.firstOrNull { it.target.id == t.id }
            InstanceCard(t, session?.ended, onOpen)
        }
    }
}

private fun countLabel(n: Int) = if (n == 1) "1 instance online" else "$n instances online"

@Composable
private fun EmptyState(region: String?) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(Icons.Rounded.Search, size = 72.dp)
        Text(
            "No instances online in ${region ?: "this region"}.",
            style = MaterialTheme.typography.titleMedium,
        )
        Banner(
            "Only EC2 instances whose SSM Agent is online are listed: check that the instance is " +
                "running, has the AmazonSSMManagedInstanceCore policy on its role, and appears in " +
                "Systems Manager → Fleet Manager. Or pick another region.",
            isError = false,
        )
    }
}

/** [sessionEnded] is null when the instance has no session in the app. */
@Composable
private fun InstanceCard(target: Target, sessionEnded: Boolean?, onOpen: (Target) -> Unit) {
    Card(
        Modifier.fillMaxWidth().clip(CardDefaults.shape).clickable { onOpen(target) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PromptBadge(sessionEnded == false)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    target.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(
                    target.id.takeIf { it != target.name },
                    target.attributes[Ec2ShellPlugin.ATTR_IP_ADDRESS],
                )
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    target.attributes[Ec2ShellPlugin.ATTR_PLATFORM]?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraSmall)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    when (sessionEnded) {
                        false -> StatusPill("Session open", Brand.Success)
                        true -> StatusPill("Session ended", MaterialTheme.colorScheme.outline)
                        null -> Unit
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A tiny shell prompt, lit while a session is open. */
@Composable
private fun PromptBadge(live: Boolean) {
    Box(
        Modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(Brand.Navy),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            ">_",
            color = if (live) Brand.Cyan else Brand.Neutral,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
    }
}
