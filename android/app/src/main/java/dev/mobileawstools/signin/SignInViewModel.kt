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
import dev.mobileawstools.core.aws.sso.InMemoryClientRegistrationStore
import dev.mobileawstools.core.aws.sso.SdkSsoOidc
import dev.mobileawstools.core.aws.sso.SsoLogin
import dev.mobileawstools.core.aws.sso.SsoToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant

sealed interface SignInState {
    data object Idle : SignInState
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

    var state by mutableStateOf<SignInState>(SignInState.Idle)
        private set

    /** Held in memory only until encrypted storage lands (#17). */
    private var token: SsoToken? = null
    private var job: Job? = null

    fun signIn(openBrowser: (String) -> Unit) {
        if (job?.isActive == true) return
        val url = startUrl.trim()
        val ssoRegion = region.trim()
        val login = try {
            SsoLogin(ssoRegion, url, SdkSsoOidc(ssoRegion), registrations)
        } catch (e: IllegalArgumentException) {
            state = SignInState.Failed(e.message.orEmpty())
            return
        }
        prefs.edit().putString(KEY_START_URL, url).putString(KEY_REGION, ssoRegion).apply()
        state = SignInState.Starting
        job = viewModelScope.launch {
            try {
                val (registration, authorization) = login.start()
                state = SignInState.WaitingForUser(authorization.userCode, authorization.verificationUriComplete)
                openBrowser(authorization.verificationUriComplete)
                val signedIn = login.awaitToken(registration, authorization)
                token = signedIn
                state = SignInState.SignedIn(signedIn.expiresAt, signedIn.refreshToken != null)
                Log.i(TAG, "Signed in: $signedIn")
            } catch (e: Exception) {
                // SDK messages name the failing operation, never the tokens.
                Log.w(TAG, "Sign-in failed: ${e.javaClass.simpleName}: ${e.message}")
                state = SignInState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        state = SignInState.Idle
    }

    private companion object {
        const val TAG = "signin"
        const val KEY_START_URL = "start_url"
        const val KEY_REGION = "region"

        // Survives the screen, not the process; persisted encrypted in #17.
        val registrations = InMemoryClientRegistrationStore()
    }
}
