// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.plugins.ec2shell

import dev.mobileawstools.plugin.ConnectionState
import dev.mobileawstools.plugin.isCompatible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Ec2ShellPluginTest {

    private val plugin = Ec2ShellPlugin()

    @Test
    fun builtAgainstTheCurrentContract() {
        assertTrue(isCompatible(plugin.info.apiVersion))
    }

    @Test
    fun declaresTheSessionManagerPermissions() {
        val actions = plugin.permissions.map { it.action }.toSet()
        assertTrue(actions.containsAll(setOf("ssm:StartSession", "ssm:ResumeSession", "ssm:TerminateSession")))
        assertTrue(actions.all { it.startsWith("ssm:") || it.startsWith("ssmmessages:") })
    }

    @Test
    fun mapsEveryBridgeState() {
        assertEquals(ConnectionState.CONNECTING, Ec2ShellPlugin.stateOf(1))
        assertEquals(ConnectionState.CONNECTED, Ec2ShellPlugin.stateOf(2))
        assertEquals(ConnectionState.RECONNECTING, Ec2ShellPlugin.stateOf(3))
        assertEquals(ConnectionState.CLOSED, Ec2ShellPlugin.stateOf(4))
        assertEquals(ConnectionState.FAILED, Ec2ShellPlugin.stateOf(5))
        assertEquals(ConnectionState.FAILED, Ec2ShellPlugin.stateOf(99))
    }
}
