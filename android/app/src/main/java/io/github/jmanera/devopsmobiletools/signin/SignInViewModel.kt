// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.signin

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jmanera.devopsmobiletools.DevOpsMobileToolsApp
import io.github.jmanera.devopsmobiletools.core.aws.context.AwsRegions
import io.github.jmanera.devopsmobiletools.core.aws.context.Selection
import io.github.jmanera.devopsmobiletools.core.aws.sso.SdkSsoOidc
import io.github.jmanera.devopsmobiletools.core.aws.sso.SignInRequiredException
import io.github.jmanera.devopsmobiletools.core.aws.sso.SsoAccount
import io.github.jmanera.devopsmobiletools.core.aws.sso.SsoLogin
import io.github.jmanera.devopsmobiletools.core.aws.sso.SsoRole
import io.github.jmanera.devopsmobiletools.core.aws.sso.SsoToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

sealed interface SignInState {
    data object Idle : SignInState
    data object Restoring : SignInState
    data object Starting : SignInState
    data class WaitingForUser(val userCode: String, val verificationUri: String) : SignInState
    data class SignedIn(val expiresAt: Instant, val refreshable: Boolean) : SignInState
    data class Failed(val message: String) : SignInState
}

/**
 * The account, role and region picker below the token. [account], [role]
 * and [region] are what the pickers show; [active] is the saved selection
 * that plugins act with, once its credentials were obtained.
 */
data class PickerState(
    val loading: Boolean = false,
    val roles: List<SsoRole> = emptyList(),
    val account: SsoAccount? = null,
    val role: SsoRole? = null,
    val region: String,
    val active: Selection? = null,
    /** Shown instead of the key: enough to tell credentials apart, nothing usable. */
    val keyHint: String? = null,
    val expiresAt: Instant? = null,
    val error: String? = null,
) {
    val accounts: List<SsoAccount>
        get() = roles.map { SsoAccount(it.accountId, it.accountName) }.distinct()

    val accountRoles: List<SsoRole>
        get() = roles.filter { it.accountId == account?.id }

    /** The pickers show something other than the active selection. */
    val canApply: Boolean
        get() = role != null && Selection(role, region) != active
}

class SignInViewModel(app: Application) : AndroidViewModel(app) {

    // Not secret: where to sign in. Kept so the user types them once.
    private val prefs = app.getSharedPreferences("sign_in", Context.MODE_PRIVATE)

    var startUrl by mutableStateOf(prefs.getString(KEY_START_URL, "").orEmpty())
    var region by mutableStateOf(prefs.getString(KEY_REGION, "eu-west-1").orEmpty())

    private val stateHolder = mutableStateOf<SignInState>(SignInState.Idle)
    var state: SignInState
        get() = stateHolder.value
        private set(value) {
            // States carry no secrets: the user code is shown on screen anyway.
            Log.i(TAG, "State: $value")
            stateHolder.value = value
        }

    private val aws = app as DevOpsMobileToolsApp
    private val registrations = aws.registrations
    private val session = aws.session
    private val credentials = aws.credentials
    private val contexts = aws.contexts
    private var job: Job? = null

    var picker by mutableStateOf(emptyPicker())
        private set

    init {
        restore()
    }

    /** Picks up a token saved by an earlier run, refreshing it if it is about to expire. */
    private fun restore() = launchJob {
        state = SignInState.Restoring
        val saved = withContext(Dispatchers.IO) { session.current() }
        if (saved == null) {
            state = SignInState.Idle
            return@launchJob
        }
        show(withContext(Dispatchers.IO) { session.validToken() })
    }

    fun signIn(openBrowser: (String) -> Unit) {
        val url = startUrl.trim()
        val ssoRegion = region.trim()
        val login = try {
            SsoLogin(ssoRegion, url, SdkSsoOidc(ssoRegion), registrations)
        } catch (e: IllegalArgumentException) {
            state = SignInState.Failed(e.message.orEmpty())
            return
        }
        prefs.edit().putString(KEY_START_URL, url).putString(KEY_REGION, ssoRegion).apply()
        launchJob {
            state = SignInState.Starting
            val (registration, authorization) = login.start()
            state = SignInState.WaitingForUser(authorization.userCode, authorization.verificationUriComplete)
            openBrowser(authorization.verificationUriComplete)
            val token = login.awaitToken(registration, authorization)
            withContext(Dispatchers.IO) { session.signedIn(token) }
            show(token)
        }
    }

    /** Uses the refresh token now, without waiting for the token to near its expiry. */
    fun refreshNow() = launchJob {
        show(withContext(Dispatchers.IO) { session.refresh() })
    }

    fun signOut() {
        job?.cancel()
        session.signOut()
        // The saved selection stays: signing in to the same portal brings it back.
        picker = emptyPicker()
        viewModelScope.launch { credentials.clear() }
        state = SignInState.Idle
    }

    /**
     * Lists the roles assigned to the user and puts the pickers on the saved
     * selection, if that role is still assigned. With nothing saved, a lone
     * account or role is picked for the user.
     */
    fun loadRoles() = launchPicker {
        picker = picker.copy(loading = true, error = null)
        val roles = withContext(Dispatchers.IO) { credentials.roles() }
        val saved = withContext(Dispatchers.IO) { contexts.selection() }
        picker = when {
            saved != null && saved.role in roles -> picker.copy(
                roles = roles,
                account = SsoAccount(saved.role.accountId, saved.role.accountName),
                role = saved.role,
                region = saved.region,
            )
            else -> {
                val base = picker.copy(roles = roles)
                val single = base.accounts.singleOrNull()
                base.copy(
                    account = single,
                    role = single?.let { a -> roles.filter { it.accountId == a.id }.singleOrNull() },
                    error = saved?.let { "${it.role.roleName} in ${it.role.accountName} is no longer assigned to you" },
                )
            }
        }
        if (saved != null && saved.role in roles) activate(saved)
    }

    fun pickAccount(account: SsoAccount) {
        val roles = picker.roles.filter { it.accountId == account.id }
        picker = picker.copy(account = account, role = roles.singleOrNull(), error = null)
    }

    fun pickRole(role: SsoRole) {
        picker = picker.copy(role = role, error = null)
    }

    fun pickRegion(region: String) {
        picker = picker.copy(region = region, error = null)
    }

    /** Saves what the pickers show as the selection, and gets credentials for it. */
    fun apply() = launchPicker {
        val role = picker.role ?: return@launchPicker
        val selection = Selection(role, picker.region)
        withContext(Dispatchers.IO) { contexts.select(selection) }
        activate(selection)
    }

    /** Credentials for the saved [selection]; asking again within their lifetime returns the cached ones. */
    private suspend fun activate(selection: Selection) {
        val context = withContext(Dispatchers.IO) { contexts.context() }
        Log.i(TAG, "Credentials for ${selection.role.accountId}/${selection.role.roleName} in ${context.region}: ${context.credentials}")
        picker = picker.copy(
            active = selection,
            keyHint = "…" + context.credentials.accessKeyId.takeLast(4),
            expiresAt = context.credentials.expiresAt,
            error = null,
        )
    }

    private fun launchPicker(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: SignInRequiredException) {
                session.signOut()
                picker = emptyPicker()
                state = SignInState.Failed("Please sign in again: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "Picker failed: ${e.javaClass.simpleName}: ${e.message}")
                picker = picker.copy(error = e.message ?: e.javaClass.simpleName)
            } finally {
                picker = picker.copy(loading = false)
            }
        }
    }

    /** Before anything is picked, plugins act in the Identity Center region, if it is in the list. */
    private fun emptyPicker() = PickerState(region = AwsRegions.of(region.trim())?.code ?: DEFAULT_REGION)

    fun cancel() {
        job?.cancel()
        state = SignInState.Idle
    }

    private fun show(token: SsoToken) {
        Log.i(TAG, "Token: $token")
        state = SignInState.SignedIn(token.expiresAt, token.refreshToken != null)
        if (picker.roles.isEmpty() && !picker.loading) loadRoles()
    }

    private fun launchJob(block: suspend () -> Unit) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            try {
                block()
            } catch (e: SignInRequiredException) {
                Log.i(TAG, "Sign-in required: ${e.message}")
                state = SignInState.Failed("Please sign in again: ${e.message}")
            } catch (e: Exception) {
                // SDK messages name the failing operation, never the tokens.
                Log.w(TAG, "Sign-in failed: ${e.javaClass.simpleName}: ${e.message}")
                state = SignInState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    override fun onCleared() {
        Log.i(TAG, "Cleared while $state")
    }

    private companion object {
        const val TAG = "signin"
        const val KEY_START_URL = "start_url"
        const val KEY_REGION = "region"
        const val DEFAULT_REGION = "eu-west-1"
    }
}
