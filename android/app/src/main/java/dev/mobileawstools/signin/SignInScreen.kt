// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.signin

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SignInScreen(vm: SignInViewModel = viewModel()) {
    val context = LocalContext.current
    val openBrowser: (String) -> Unit = { url ->
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Column(
        Modifier.safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Sign in with IAM Identity Center", style = MaterialTheme.typography.headlineSmall)

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
                RolesSection(vm)
            }
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun RolesSection(vm: SignInViewModel) {
    val roles = vm.roles
    Text("Roles", style = MaterialTheme.typography.titleMedium)
    if (roles.roles.isEmpty()) {
        Button(onClick = vm::loadRoles, modifier = Modifier.fillMaxWidth()) { Text("Load my accounts and roles") }
    }
    for (role in roles.roles) {
        OutlinedButton(onClick = { vm.credentialsFor(role) }, modifier = Modifier.fillMaxWidth()) {
            Text("${role.accountName} (${role.accountId}) · ${role.roleName}")
        }
    }
    val selected = roles.selected
    if (selected != null && roles.expiresAt != null) {
        val time = TIME.format(roles.expiresAt.atZone(ZoneId.systemDefault()))
        Text("Credentials for ${selected.roleName}: key ${roles.keyHint}, valid until $time.")
    }
    roles.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
