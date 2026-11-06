// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.plugins.ec2shell

import android.util.Log
import io.github.jmanera.devopsmobiletools.plugin.AwsContext
import io.github.jmanera.devopsmobiletools.plugin.AwsCredentials
import io.github.jmanera.devopsmobiletools.plugin.Connection
import io.github.jmanera.devopsmobiletools.plugin.ConnectionListener
import io.github.jmanera.devopsmobiletools.plugin.ConnectionState
import io.github.jmanera.devopsmobiletools.plugin.IamPermission
import io.github.jmanera.devopsmobiletools.plugin.PluginInfo
import io.github.jmanera.devopsmobiletools.plugin.Target
import io.github.jmanera.devopsmobiletools.plugin.TargetPlugin
import io.github.jmanera.devopsmobiletools.plugin.TerminalConnection
import io.github.jmanera.devopsmobiletools.ssmbridge.Listener
import io.github.jmanera.devopsmobiletools.ssmbridge.LogSink
import io.github.jmanera.devopsmobiletools.ssmbridge.Session
import io.github.jmanera.devopsmobiletools.ssmbridge.Ssmbridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Interactive shell on an SSM-managed EC2 instance, through the Go bridge.
 *
 * Built in rather than installed, but written against plugin-api like any
 * other plugin so the contract is exercised from phase 1.
 */
class Ec2ShellPlugin(
    private val directoryFor: (AwsContext) -> InstanceDirectory = ::SdkInstanceDirectory,
) : TargetPlugin {

    override val info = PluginInfo(
        id = "io.github.jmanera.devopsmobiletools.ec2shell",
        name = "EC2 shell",
        version = "0.3.0",
    )

    override val permissions = PERMISSIONS

    /**
     * EC2 instances with the SSM Agent online, named after their `Name` tag
     * and sorted by name. Without `tag:GetResources` they are listed by id.
     */
    override suspend fun discover(context: AwsContext): List<Target> = coroutineScope {
        val directory = directoryFor(context)
        val names = async {
            try {
                directory.nameTags()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Names are a convenience; a role without the permission still lists instances.
                Log.w(TAG, "No Name tags: ${e.javaClass.simpleName}: ${e.message}")
                emptyMap()
            }
        }
        val instances = directory.onlineInstances()
        targetsOf(instances, names.await())
    }

    override suspend fun connect(target: Target, context: AwsContext, listener: ConnectionListener): Connection {
        val cfg = Ssmbridge.newConfig().apply {
            region = context.region
            targetID = target.id
            accessKeyID = context.credentials.accessKeyId
            secretAccessKey = context.credentials.secretAccessKey
            sessionToken = context.credentials.sessionToken
            logLevel = Ssmbridge.LevelDebug
            cols = 80
            rows = 24
        }
        val session = withContext(Dispatchers.IO) { Ssmbridge.startShell(cfg, BridgeListener(listener), LogcatSink) }
        return ShellConnection(session)
    }

    private class ShellConnection(private val session: Session) : TerminalConnection {
        override fun write(data: ByteArray) = session.write(data)

        override fun resize(cols: Int, rows: Int) = session.resize(cols.toLong(), rows.toLong())

        override fun close() = session.close()

        override fun updateCredentials(credentials: AwsCredentials) =
            session.updateCredentials(credentials.accessKeyId, credentials.secretAccessKey, credentials.sessionToken)

        override fun networkChanged() = session.networkChanged()
    }

    private class BridgeListener(private val listener: ConnectionListener) : Listener {
        override fun onOutput(data: ByteArray) = listener.onOutput(data)

        override fun onStateChanged(state: Long, detail: String) = listener.onStateChanged(stateOf(state), detail)
    }

    private object LogcatSink : LogSink {
        // The bridge has already redacted tokens and keys.
        override fun log(level: Long, message: String) {
            Log.println(priorityOf(level), TAG, message)
        }
    }

    companion object {
        private const val TAG = "ssmbridge"

        /**
         * What the client side of a shell session needs. The ssmmessages
         * actions belong to the SSM Agent's instance role, not to the user.
         */
        val PERMISSIONS = listOf(
            IamPermission("ssm:StartSession"),
            IamPermission("ssm:ResumeSession"),
            IamPermission("ssm:TerminateSession"),
            IamPermission("ssm:DescribeInstanceInformation"),
            IamPermission("tag:GetResources"),
        )

        const val ATTR_PLATFORM = "platform"
        const val ATTR_AGENT_VERSION = "agentVersion"
        const val ATTR_IP_ADDRESS = "ipAddress"

        internal fun targetsOf(instances: List<ManagedInstance>, names: Map<String, String>): List<Target> =
            instances
                .distinctBy { it.id }
                .map { i ->
                    Target(
                        id = i.id,
                        name = names[i.id]?.takeIf { it.isNotBlank() } ?: i.id,
                        attributes = buildMap {
                            i.platform?.let { put(ATTR_PLATFORM, it) }
                            i.agentVersion?.let { put(ATTR_AGENT_VERSION, it) }
                            i.ipAddress?.let { put(ATTR_IP_ADDRESS, it) }
                        },
                    )
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Target::name).thenBy(Target::id))

        // The bridge's State and Level values are compile-time constants, so
        // these mappings do not load its native library (unit tests rely on it).
        internal fun stateOf(state: Long): ConnectionState = when (state) {
            Ssmbridge.StateConnecting -> ConnectionState.CONNECTING
            Ssmbridge.StateConnected -> ConnectionState.CONNECTED
            Ssmbridge.StateReconnecting -> ConnectionState.RECONNECTING
            Ssmbridge.StateClosed -> ConnectionState.CLOSED
            else -> ConnectionState.FAILED
        }

        private fun priorityOf(level: Long): Int = when (level) {
            Ssmbridge.LevelTrace -> Log.VERBOSE
            Ssmbridge.LevelDebug -> Log.DEBUG
            Ssmbridge.LevelInfo -> Log.INFO
            Ssmbridge.LevelWarn -> Log.WARN
            else -> Log.ERROR
        }
    }
}
