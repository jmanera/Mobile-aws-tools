// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.plugin

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginApiTest {

    @Test
    fun versionIsSemVer() {
        assertNotNull(PLUGIN_API_VERSION, SemVer.parse(PLUGIN_API_VERSION))
    }

    @Test
    fun compatibilityWhileMajorIsZero() {
        assertTrue(isCompatible("0.1.0"))
        assertTrue(isCompatible("0.1.7"))
        assertTrue(isCompatible("0.1.0-alpha.1"))
        assertFalse(isCompatible("0.2.0"))
        assertFalse(isCompatible("0.0.9"))
        assertFalse(isCompatible("1.1.0"))
        assertFalse(isCompatible("0.1"))
        assertFalse(isCompatible(""))
    }

    @Test
    fun credentialsNeverPrintSecrets() {
        val creds = AwsCredentials("ASIAEXAMPLE", "secret-value", "token-value")
        val text = creds.toString() + AwsContext("eu-west-3", creds).toString()
        assertTrue(text, "ASIAEXAMPLE" in text)
        assertFalse(text, "secret-value" in text)
        assertFalse(text, "token-value" in text)
    }

    @Test(expected = IllegalArgumentException::class)
    fun credentialsRequireASecret() {
        AwsCredentials("ASIAEXAMPLE", " ", "token")
    }

    @Test(expected = IllegalArgumentException::class)
    fun contextRequiresARegion() {
        AwsContext("", AwsCredentials("ASIAEXAMPLE", "secret", "token"))
    }

    @Test
    fun iamPermissionAcceptsServiceActions() {
        IamPermission("ssm:StartSession")
        IamPermission("ssmmessages:*")
        IamPermission("ec2:DescribeInstances", "arn:aws:ec2:*:*:instance/*")
    }

    @Test(expected = IllegalArgumentException::class)
    fun iamPermissionRejectsMalformedActions() {
        IamPermission("StartSession")
    }

    @Test
    fun aPluginIsUsedOnlyThroughTheContract() = runBlocking {
        val plugin: TargetPlugin = EchoPlugin()
        val context = AwsContext("eu-west-3", AwsCredentials("ASIAEXAMPLE", "secret", "token"))
        val listener = RecordingListener()

        val target = plugin.discover(context).single()
        val connection = plugin.connect(target, context, listener) as TerminalConnection
        connection.write("hi".toByteArray())
        connection.close()

        assertTrue(isCompatible(plugin.info.apiVersion))
        assertEquals("hi", listener.output.toString())
        assertEquals(listOf(ConnectionState.CONNECTED, ConnectionState.CLOSED), listener.states)
    }

    /** Minimal plugin that echoes what it receives. */
    private class EchoPlugin : TargetPlugin {
        override val info = PluginInfo("dev.mobileawstools.echo", "Echo", "1.0.0")
        override val permissions = emptyList<IamPermission>()

        override suspend fun discover(context: AwsContext) = listOf(Target("echo-1", "Echo"))

        override suspend fun connect(target: Target, context: AwsContext, listener: ConnectionListener): Connection {
            listener.onStateChanged(ConnectionState.CONNECTED, "")
            return object : TerminalConnection {
                override fun write(data: ByteArray) = listener.onOutput(data)
                override fun resize(cols: Int, rows: Int) = Unit
                override fun close() = listener.onStateChanged(ConnectionState.CLOSED, "")
            }
        }
    }

    private class RecordingListener : ConnectionListener {
        val output = StringBuilder()
        val states = mutableListOf<ConnectionState>()
        override fun onOutput(data: ByteArray) {
            output.append(String(data))
        }
        override fun onStateChanged(state: ConnectionState, detail: String) {
            states += state
        }
    }
}
