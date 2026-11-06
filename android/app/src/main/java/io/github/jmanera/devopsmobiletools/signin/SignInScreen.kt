// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.signin

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jmanera.devopsmobiletools.R
import io.github.jmanera.devopsmobiletools.lock.LockSettingsSection
import io.github.jmanera.devopsmobiletools.core.aws.context.AwsRegion
import io.github.jmanera.devopsmobiletools.core.aws.context.AwsRegions
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SignInScreen(onShowInstances: () -> Unit, vm: SignInViewModel = viewModel()) {
    val context = LocalContext.current
    val openBrowser: (String) -> Unit = { url ->
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.tagline), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
        Text("Sign in with IAM Identity Center", style = MaterialTheme.typography.titleMedium)

        when (val state = vm.state) {
            SignInState.Idle, is SignInState.Failed -> {
                OutlinedTextField(
                    vm.startUrl, { vm.startUrl = it },
                    label = { Text("Start URL (https://….awsapps.com/start)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    vm.region, { vm.region = it },
                    label = { Text("Identity Center region") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state is SignInState.Failed) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = { vm.signIn(openBrowser) }, modifier = Modifier.fillMaxWidth()) { Text("Sign in") }
            }

            SignInState.Restoring -> Text("Checking the saved sign-in…")

            SignInState.Starting -> Text("Contacting IAM Identity Center…")

            is SignInState.WaitingForUser -> {
                Text("Approve this code in the browser:")
                Text(state.userCode, fontFamily = FontFamily.Monospace, fontSize = 40.sp)
                OutlinedButton(onClick = { openBrowser(state.verificationUri) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open the browser again")
                }
                OutlinedButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }

            is SignInState.SignedIn -> {
                val time = TIME.format(state.expiresAt.atZone(ZoneId.systemDefault()))
                Text("Signed in. The token expires at $time.")
                Text(if (state.refreshable) "It can be refreshed without signing in again." else "No refresh token was granted.")
                if (state.refreshable) {
                    OutlinedButton(onClick = vm::refreshNow, modifier = Modifier.fillMaxWidth()) { Text("Refresh now") }
                }
                OutlinedButton(onClick = vm::signOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
                PickerSection(vm, onShowInstances)
                LockSettingsSection()
            }
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun PickerSection(vm: SignInViewModel, onShowInstances: () -> Unit) {
    val p = vm.picker
    Text("Work as", style = MaterialTheme.typography.titleMedium)
    if (p.loading && p.roles.isEmpty()) Text("Loading your accounts and roles…")
    if (!p.loading && p.roles.isEmpty()) {
        OutlinedButton(onClick = vm::loadRoles, modifier = Modifier.fillMaxWidth()) { Text("Load my accounts and roles") }
    }
    if (p.roles.isNotEmpty()) {
        Picker(
            label = "Account",
            options = p.accounts,
            selected = p.account,
            describe = { "${it.name} (${it.id})" },
            onPick = vm::pickAccount,
        )
        Picker(
            label = "Role",
            options = p.accountRoles,
            selected = p.role,
            describe = { it.roleName },
            onPick = vm::pickRole,
            enabled = p.account != null,
        )
        Picker(
            label = "Region",
            options = AwsRegions.ALL,
            selected = AwsRegions.of(p.region),
            describe = AwsRegion::toString,
            onPick = { vm.pickRegion(it.code) },
        )
        Button(onClick = vm::apply, enabled = p.canApply && !p.loading, modifier = Modifier.fillMaxWidth()) {
            Text("Use this role and region")
        }
    }
    val active = p.active
    if (active != null && p.expiresAt != null) {
        val time = TIME.format(p.expiresAt.atZone(ZoneId.systemDefault()))
        Text(
            "Working as ${active.role.roleName} in ${active.role.accountName}, ${active.region}. " +
                "Credentials ${p.keyHint}, valid until $time and renewed automatically.",
        )
        if (!p.canApply) {
            Button(onClick = onShowInstances, modifier = Modifier.fillMaxWidth()) { Text("Show instances") }
        }
    }
    p.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Picker(
    label: String,
    options: List<T>,
    selected: T?,
    describe: (T) -> String,
    onPick: (T) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let(describe).orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(describe(option)) },
                    onClick = {
                        expanded = false
                        onPick(option)
                    },
                )
            }
        }
    }
}
