// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.plugins.ec2shell

import io.github.jmanera.devopsmobiletools.plugin.AwsContext
import io.github.jmanera.devopsmobiletools.plugin.AwsCredentials
import io.github.jmanera.devopsmobiletools.plugin.ConnectionState
import io.github.jmanera.devopsmobiletools.plugin.isCompatible
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class Ec2ShellPluginTest {

    private val directory = FakeDirectory()
    private val contexts = mutableListOf<AwsContext>()
    private val plugin = Ec2ShellPlugin { contexts += it; directory }
    private val context = AwsContext("eu-west-3", AwsCredentials("AKIA", "secret", "token"))

    @Test
    fun builtAgainstTheCurrentContract() {
        assertTrue(isCompatible(plugin.info.apiVersion))
    }

    @Test
    fun declaresTheSessionManagerPermissions() {
        val actions = plugin.permissions.map { it.action }.toSet()
        assertEquals(
            setOf(
                "ssm:StartSession", "ssm:ResumeSession", "ssm:TerminateSession",
                "ssm:DescribeInstanceInformation", "tag:GetResources",
            ),
            actions,
        )
    }

    @Test
    fun listsOnlineInstancesNamedAfterTheirNameTag() = runTest {
        directory.instances = listOf(
            ManagedInstance("i-0bbb", "Amazon Linux 2023", "3.3.5700.0", "10.0.0.2"),
            ManagedInstance("i-0aaa", "Ubuntu 24.04", null, null),
            ManagedInstance("i-0ccc", null, null, null),
        )
        directory.names = mapOf("i-0bbb" to "web", "i-0aaa" to "Bastion", "i-0stopped" to "old")

        val targets = plugin.discover(context)

        assertEquals(listOf("Bastion", "i-0ccc", "web"), targets.map { it.name })
        assertEquals(listOf("i-0aaa", "i-0ccc", "i-0bbb"), targets.map { it.id })
        assertEquals(
            mapOf(
                Ec2ShellPlugin.ATTR_PLATFORM to "Amazon Linux 2023",
                Ec2ShellPlugin.ATTR_AGENT_VERSION to "3.3.5700.0",
                Ec2ShellPlugin.ATTR_IP_ADDRESS to "10.0.0.2",
            ),
            targets.last().attributes,
        )
        assertEquals(emptyMap<String, String>(), targets[1].attributes)
        assertSame(context, contexts.single())
    }

    @Test
    fun instancesWithTheSameNameAreOrderedById() {
        val targets = Ec2ShellPlugin.targetsOf(
            listOf(ManagedInstance("i-2", null, null, null), ManagedInstance("i-1", null, null, null)),
            mapOf("i-1" to "web", "i-2" to "web"),
        )
        assertEquals(listOf("i-1", "i-2"), targets.map { it.id })
    }

    @Test
    fun aBlankNameTagFallsBackToTheId() {
        val targets = Ec2ShellPlugin.targetsOf(listOf(ManagedInstance("i-1", null, null, null)), mapOf("i-1" to " "))
        assertEquals("i-1", targets.single().name)
    }

    @Test
    fun withoutPermissionForTagsInstancesAreListedById() = runTest {
        directory.instances = listOf(ManagedInstance("i-0aaa", null, null, null))
        directory.namesError = IllegalStateException("AccessDeniedException")

        assertEquals(listOf("i-0aaa"), plugin.discover(context).map { it.name })
    }

    @Test
    fun aFailureListingInstancesIsReported() = runTest {
        directory.instancesError = IllegalStateException("AccessDeniedException")
        try {
            plugin.discover(context)
            fail("expected the error")
        } catch (e: IllegalStateException) {
            assertEquals("AccessDeniedException", e.message)
        }
    }

    @Test
    fun noInstancesOnlineMeansNoTargets() = runTest {
        assertTrue(plugin.discover(context).isEmpty())
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

    private class FakeDirectory : InstanceDirectory {
        var instances = emptyList<ManagedInstance>()
        var names = emptyMap<String, String>()
        var instancesError: Exception? = null
        var namesError: Exception? = null

        override suspend fun onlineInstances() = instancesError?.let { throw it } ?: instances

        override suspend fun nameTags() = namesError?.let { throw it } ?: names
    }
}
