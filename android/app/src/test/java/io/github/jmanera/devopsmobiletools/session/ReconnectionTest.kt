// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.session

import io.github.jmanera.devopsmobiletools.plugin.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectionTest {

    @Test
    fun theFirstNetworkIsNotAChange() {
        val t = NetworkTracker()
        assertFalse(t.available(100))
        assertFalse("the same network again", t.available(100))
    }

    @Test
    fun aNetworkBackAfterALossIsAChange() {
        val t = NetworkTracker()
        t.available(100)
        t.lost(100)
        assertTrue(t.available(100))
    }

    @Test
    fun switchingNetworksIsAChange() {
        val t = NetworkTracker()
        t.available(100)
        assertTrue("Wi-Fi to mobile", t.available(200))
        t.lost(100) // the old one going away later changes nothing
        assertFalse(t.available(200))
    }

    @Test
    fun endReasonsSayWhatToDo() {
        assertEquals("Session closed", LiveSession.endReason(ConnectionState.CLOSED, "closed by user"))
        assertTrue(LiveSession.endReason(ConnectionState.CLOSED, "the session no longer exists on AWS").contains("Reconnect starts a new one"))
        assertTrue(LiveSession.endReason(ConnectionState.CLOSED, "session timed out").contains("idle timeout"))
        assertEquals("The shell on the instance exited", LiveSession.endReason(ConnectionState.CLOSED, "session ended by the remote side"))
        assertTrue(
            LiveSession.endReason(ConnectionState.FAILED, "cannot resume session: dial tcp: network is unreachable")
                .startsWith("The connection could not be resumed"),
        )
        assertEquals("Session failed: boom", LiveSession.endReason(ConnectionState.FAILED, "boom"))
    }
}
