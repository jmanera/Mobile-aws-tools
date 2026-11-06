// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class AppLockTest {

    private val now = Instant.parse("2026-11-06T20:00:00Z")
    private val fiveMinutes = Duration.ofMinutes(5)

    private fun lock(
        enabled: Boolean = true,
        canAuthenticate: Boolean = true,
        away: Duration? = null,
        timeout: Duration = fiveMinutes,
    ) = AppLock.shouldLock(enabled, canAuthenticate, away?.let { now.minus(it) }, now, timeout)

    @Test
    fun theAppStartsLocked() {
        assertTrue(lock(away = null))
    }

    @Test
    fun aShortTripToTheBackgroundDoesNotLock() {
        assertFalse(lock(away = Duration.ofMinutes(4)))
    }

    @Test
    fun theTimeoutLocks() {
        assertTrue(lock(away = fiveMinutes))
        assertTrue(lock(away = Duration.ofHours(2)))
    }

    @Test
    fun immediatelyLocksOnEveryReturn() {
        assertTrue(lock(away = Duration.ofSeconds(1), timeout = Duration.ZERO))
    }

    @Test
    fun turnedOffNeverLocks() {
        assertFalse(lock(enabled = false))
        assertFalse(lock(enabled = false, away = Duration.ofHours(2)))
    }

    @Test
    fun withoutAScreenLockTheAppCannotLock() {
        // Locking would shut the user out with no way to unlock.
        assertFalse(lock(canAuthenticate = false))
    }
}
