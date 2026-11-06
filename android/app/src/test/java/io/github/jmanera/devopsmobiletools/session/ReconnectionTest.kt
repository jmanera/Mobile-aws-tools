// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.session

import io.github.jmanera.devopsmobiletools.errors.FailureAction
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

    private fun end(state: ConnectionState, detail: String) = LiveSession.endFailure(state, detail)

    @Test
    fun endReasonsSayWhatToDo() {
        assertEquals("Session closed", end(ConnectionState.CLOSED, "closed by user").message)
        assertTrue(end(ConnectionState.CLOSED, "the session no longer exists on AWS").message.contains("Reconnect starts a new one"))
        assertTrue(end(ConnectionState.CLOSED, "session timed out").message.contains("idle timeout"))
        assertTrue(end(ConnectionState.FAILED, "timed out waiting for the agent handshake").message.contains("handshake"))
        assertEquals("The shell on the instance exited", end(ConnectionState.CLOSED, "session ended by the remote side").message)
        assertTrue(
            end(ConnectionState.FAILED, "cannot resume session: dial tcp: network is unreachable")
                .message.startsWith("The connection could not be resumed"),
        )
        assertEquals("boom", end(ConnectionState.FAILED, "boom").message)
    }

    @Test
    fun aResumeRefusedForPermissionsAsksToChangeRole() {
        val f = end(
            ConnectionState.FAILED,
            "cannot resume session: api error AccessDeniedException: User: x is not authorized to perform: ssm:ResumeSession on resource: s",
        )
        assertEquals(FailureAction.CHANGE_ROLE, f.action)
    }
}
