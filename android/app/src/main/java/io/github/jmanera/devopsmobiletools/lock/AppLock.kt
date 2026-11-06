// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.lock

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Hides the app behind the device's biometrics or screen lock when it starts
 * and when it comes back after [LockSettings.timeout] in the background.
 * Open sessions keep running while locked: the lock only hides them.
 *
 * It guards what is on screen and what the user can do with the signed-in
 * role; secrets at rest are already sealed with Android Keystore.
 */
class AppLock(private val context: Context, private val clock: Clock = Clock.systemUTC()) {

    val settings = LockSettings(context)

    /** Whether the lock screen covers the app. */
    var locked by mutableStateOf(false)
        private set

    private var backgroundSince: Instant? = null

    /** Starts at process start: the app opens locked if the lock is on and possible. */
    fun start() {
        locked = shouldLock(settings.enabled, canAuthenticate(), backgroundSince = null, now = clock.instant(), settings.timeout)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                backgroundSince = clock.instant()
            }

            override fun onStart(owner: LifecycleOwner) {
                val since = backgroundSince ?: return
                if (shouldLock(settings.enabled, canAuthenticate(), since, clock.instant(), settings.timeout)) {
                    Log.i(TAG, "Locking after ${Duration.between(since, clock.instant()).seconds} s in the background")
                    locked = true
                }
                backgroundSince = null
            }
        })
    }

    fun unlocked() {
        locked = false
    }

    /** Whether the device has biometrics or a screen lock the prompt can use. */
    fun canAuthenticate(): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    companion object {
        private const val TAG = "lock"

        /**
         * Biometrics, or the device PIN, pattern or password as a fallback.
         * Weak biometrics are enough for a screen gate, and BIOMETRIC_STRONG
         * with DEVICE_CREDENTIAL is not supported before Android 11.
         */
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

        /**
         * Whether to lock now. [backgroundSince] is null at process start,
         * which always locks. Without a way to authenticate the app cannot
         * lock, or the user would be shut out.
         */
        fun shouldLock(
            enabled: Boolean,
            canAuthenticate: Boolean,
            backgroundSince: Instant?,
            now: Instant,
            timeout: Duration,
        ): Boolean {
            if (!enabled || !canAuthenticate) return false
            if (backgroundSince == null) return true
            return Duration.between(backgroundSince, now) >= timeout
        }
    }
}

/** Lock preferences; nothing secret. */
class LockSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("lock", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var timeout: Duration
        get() = Duration.ofSeconds(prefs.getLong(KEY_TIMEOUT_S, DEFAULT_TIMEOUT.seconds))
        set(value) = prefs.edit().putLong(KEY_TIMEOUT_S, value.seconds).apply()

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TIMEOUT_S = "timeout_s"

        val DEFAULT_TIMEOUT: Duration = Duration.ofMinutes(5)

        /** Choices offered in the settings. */
        val TIMEOUTS: List<Duration> = listOf(
            Duration.ZERO,
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofHours(1),
        )
    }
}
