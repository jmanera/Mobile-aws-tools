// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.signin

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mobileawstools.core.aws.sso.EncryptedClientRegistrationStore
import dev.mobileawstools.core.aws.sso.EncryptedSsoTokenStore
import dev.mobileawstools.core.aws.sso.SdkSsoOidc
import dev.mobileawstools.core.aws.sso.SignInRequiredException
import dev.mobileawstools.core.aws.sso.SsoLogin
import dev.mobileawstools.core.aws.sso.SsoSession
import dev.mobileawstools.core.aws.sso.SsoToken
import dev.mobileawstools.core.aws.store.KeystoreSecretBox
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

    // Sealed with a Keystore key, in noBackupFilesDir: never in clear, never in a backup.
    private val box = KeystoreSecretBox(KEY_ALIAS)
    private val registrations = EncryptedClientRegistrationStore(app.noBackupFilesDir, box)
    private val session = SsoSession(
        tokens = EncryptedSsoTokenStore(app.noBackupFilesDir, box),
        registrations = registrations,
        oidcFor = ::SdkSsoOidc,
    )
    private var job: Job? = null

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
        state = SignInState.Idle
    }

    fun cancel() {
        job?.cancel()
        state = SignInState.Idle
    }

    private fun show(token: SsoToken) {
        Log.i(TAG, "Token: $token")
        state = SignInState.SignedIn(token.expiresAt, token.refreshToken != null)
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
        const val KEY_ALIAS = "sso-store"
    }
}
