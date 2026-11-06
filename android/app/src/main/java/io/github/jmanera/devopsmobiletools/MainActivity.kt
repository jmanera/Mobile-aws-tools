// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.biometric.BiometricPrompt
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.jmanera.devopsmobiletools.instances.InstancesScreen
import io.github.jmanera.devopsmobiletools.lock.AppLock
import io.github.jmanera.devopsmobiletools.lock.LockScreen
import io.github.jmanera.devopsmobiletools.plugin.Target
import io.github.jmanera.devopsmobiletools.session.SessionScreen
import io.github.jmanera.devopsmobiletools.signin.SignInScreen
import io.github.jmanera.devopsmobiletools.ui.BrandTheme

/**
 * Entry point of the app: sign-in, then instances, then a session, behind
 * the app lock. A FragmentActivity because BiometricPrompt needs one.
 */
class MainActivity : FragmentActivity() {

    private val lock get() = (application as DevOpsMobileToolsApp).lock

    // Created in onCreate, as BiometricPrompt requires.
    private lateinit var prompt: BiometricPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep tokens and terminal content out of screenshots and recents.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = lock.unlocked()
        })
        setContent {
            BrandTheme {
                Surface(Modifier.fillMaxSize()) {
                    // Outside the lock, so unlocking returns to the same screen.
                    val nav = rememberNavController()
                    // While locked nothing below is composed: no terminal keeps keyboard focus.
                    if (lock.locked) LockScreen(onUnlock = ::unlock) else AppNavigation(nav)
                }
            }
        }
    }

    private fun unlock() {
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.app_name))
                .setSubtitle("Unlock to see your sessions")
                .setAllowedAuthenticators(AppLock.AUTHENTICATORS)
                // Face unlock is passive: ask for a tap so unlocking is a deliberate act.
                .setConfirmationRequired(true)
                .build(),
        )
    }
}

@Composable
private fun AppNavigation(nav: NavHostController) {
    NavHost(nav, startDestination = SIGN_IN) {
        composable(SIGN_IN) {
            SignInScreen(onShowInstances = { nav.navigate(INSTANCES) })
        }
        composable(INSTANCES) {
            InstancesScreen(
                onOpen = { t -> nav.navigate("$SESSION/${Uri.encode(t.id)}?name=${Uri.encode(t.name)}") },
                onChangeRole = { if (!nav.popBackStack(SIGN_IN, inclusive = false)) nav.navigate(SIGN_IN) },
            )
        }
        composable(
            "$SESSION/{id}?name={name}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("name") { nullable = true }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val name = entry.arguments?.getString("name") ?: id
            SessionScreen(
                Target(id, name),
                onBack = { nav.popBackStack() },
                onChangeRole = { if (!nav.popBackStack(SIGN_IN, inclusive = false)) nav.navigate(SIGN_IN) },
            )
        }
    }
}

private const val SIGN_IN = "signin"
private const val INSTANCES = "instances"
private const val SESSION = "session"
