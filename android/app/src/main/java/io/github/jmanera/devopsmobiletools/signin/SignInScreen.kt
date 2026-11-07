// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.signin

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jmanera.devopsmobiletools.R
import io.github.jmanera.devopsmobiletools.core.aws.context.AwsRegion
import io.github.jmanera.devopsmobiletools.core.aws.context.AwsRegions
import io.github.jmanera.devopsmobiletools.lock.LockSettingsSection
import io.github.jmanera.devopsmobiletools.ui.Banner
import io.github.jmanera.devopsmobiletools.ui.Brand
import io.github.jmanera.devopsmobiletools.ui.BrandLogo
import io.github.jmanera.devopsmobiletools.ui.Progress
import io.github.jmanera.devopsmobiletools.ui.SectionCard
import io.github.jmanera.devopsmobiletools.ui.StatusPill
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SignInScreen(onShowInstances: () -> Unit, vm: SignInViewModel = viewModel()) {
    val context = LocalContext.current
    val openBrowser: (String) -> Unit = { url ->
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    val state = vm.state
    val step = when {
        state !is SignInState.SignedIn -> 1
        vm.picker.active != null && !vm.picker.canApply -> 3
        else -> 2
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Hero(step)
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state) {
                SignInState.Idle, is SignInState.Failed -> SignInCard(vm, state as? SignInState.Failed) { vm.signIn(openBrowser) }

                SignInState.Restoring -> SectionCard("Welcome back", Icons.Rounded.Lock) {
                    Progress("Checking the saved sign-in…")
                }

                SignInState.Starting -> SectionCard("Sign in with IAM Identity Center", Icons.Rounded.Lock) {
                    Progress("Contacting IAM Identity Center…")
                }

                is SignInState.WaitingForUser -> ApproveCard(
                    state,
                    onOpenBrowser = { openBrowser(state.verificationUri) },
                    onCancel = vm::cancel,
                )

                is SignInState.SignedIn -> {
                    PickerSection(vm, onShowInstances)
                    SignedInCard(state, onRefresh = vm::refreshNow, onSignOut = vm::signOut)
                    SectionCard("Security", Icons.Rounded.Lock, subtitle = "Keep your sessions private") {
                        LockSettingsSection()
                    }
                }
            }
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

/** The brand header, with where the user is in the three steps to a shell. */
@Composable
private fun Hero(step: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Brand.Navy, Color(0xFF0B4A9E), Brand.Blue)),
                RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            ),
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BrandLogo(56.dp, Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp)))
                Column {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                    Text(
                        stringResource(R.string.tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Brand.Cyan,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StepChip(1, "Sign in", step, Modifier.weight(1f))
                StepChip(2, "Work as", step, Modifier.weight(1f))
                StepChip(3, "Instances", step, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StepChip(number: Int, label: String, current: Int, modifier: Modifier = Modifier) {
    val done = number < current
    val active = number == current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(if (done || active) Brand.Cyan else Color.White.copy(alpha = 0.25f), CircleShape),
        )
        Text(
            "$number · $label",
            style = MaterialTheme.typography.labelMedium,
            color = if (active) Color.White else Color.White.copy(alpha = if (done) 0.85f else 0.55f),
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SignInCard(vm: SignInViewModel, failed: SignInState.Failed?, onSignIn: () -> Unit) {
    SectionCard(
        "Sign in with IAM Identity Center",
        Icons.Rounded.Lock,
        subtitle = "Approve the sign-in in your browser; no access keys needed",
    ) {
        OutlinedTextField(
            vm.startUrl, { vm.startUrl = it },
            label = { Text("AWS access portal URL") },
            placeholder = { Text("https://….awsapps.com/start") },
            leadingIcon = { Icon(Icons.Rounded.Home, null) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            vm.region, { vm.region = it },
            label = { Text("Identity Center region") },
            leadingIcon = { Icon(Icons.Rounded.Place, null) },
            supportingText = { Text("For example eu-west-1") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        failed?.let { Banner(it.message) }
        PrimaryButton("Sign in", Icons.AutoMirrored.Rounded.ArrowForward, onSignIn)
    }
}

@Composable
private fun ApproveCard(state: SignInState.WaitingForUser, onOpenBrowser: () -> Unit, onCancel: () -> Unit) {
    SectionCard(
        "Approve the sign-in",
        Icons.Rounded.CheckCircle,
        subtitle = "Check that the browser shows this code, then approve it",
    ) {
        Surface(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                state.userCode,
                fontFamily = FontFamily.Monospace,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        }
        Progress("Waiting for your approval…")
        OutlinedButton(onClick = onOpenBrowser, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
            Text("Open the browser again", Modifier.padding(start = 8.dp))
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun SignedInCard(state: SignInState.SignedIn, onRefresh: () -> Unit, onSignOut: () -> Unit) {
    val time = TIME.format(state.expiresAt.atZone(ZoneId.systemDefault()))
    SectionCard("IAM Identity Center", Icons.Rounded.AccountCircle, subtitle = "Token expires at $time") {
        StatusPill("Signed in", Brand.Success)
        Text(
            if (state.refreshable) "It can be refreshed without signing in again." else "No refresh token was granted.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.refreshable) {
                OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Text("Refresh now", Modifier.padding(start = 8.dp))
                }
            }
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ExitToApp, null, Modifier.size(18.dp))
                Text("Sign out", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun PickerSection(vm: SignInViewModel, onShowInstances: () -> Unit) {
    val p = vm.picker
    SectionCard("Work as", Icons.Rounded.Person, subtitle = "The account, role and region to work in") {
        if (p.loading && p.roles.isEmpty()) Progress("Loading your accounts and roles…")
        if (!p.loading && p.roles.isEmpty()) {
            OutlinedButton(onClick = vm::loadRoles, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Load my accounts and roles")
            }
        }
        if (p.roles.isNotEmpty()) {
            Picker(
                label = "Account",
                icon = Icons.Rounded.AccountBox,
                options = p.accounts,
                selected = p.account,
                describe = { "${it.name} (${it.id})" },
                onPick = vm::pickAccount,
            )
            Picker(
                label = "Role",
                icon = Icons.Rounded.Person,
                options = p.accountRoles,
                selected = p.role,
                describe = { it.roleName },
                onPick = vm::pickRole,
                enabled = p.account != null,
            )
            Picker(
                label = "Region",
                icon = Icons.Rounded.Place,
                options = AwsRegions.ALL,
                selected = AwsRegions.of(p.region),
                describe = AwsRegion::toString,
                onPick = { vm.pickRegion(it.code) },
            )
            if (p.canApply || p.active == null) {
                PrimaryButton("Use this role and region", Icons.Rounded.CheckCircle, vm::apply, enabled = p.canApply && !p.loading)
            }
            if (p.loading) Progress("One moment…")
        }
        val active = p.active
        if (active != null && p.expiresAt != null) {
            val time = TIME.format(p.expiresAt.atZone(ZoneId.systemDefault()))
            Surface(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Working as ${active.role.roleName} in ${active.role.accountName}, ${active.region}.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Credentials ${p.keyHint}, valid until $time and renewed automatically.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (!p.canApply) {
                PrimaryButton("Show instances", Icons.AutoMirrored.Rounded.List, onShowInstances)
            }
        }
        p.error?.let { Banner(it) }
    }
}

@Composable
private fun PrimaryButton(text: String, icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
        Icon(icon, null, Modifier.padding(start = 8.dp).size(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Picker(
    label: String,
    icon: ImageVector,
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
            leadingIcon = { Icon(icon, null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            shape = MaterialTheme.shapes.small,
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
