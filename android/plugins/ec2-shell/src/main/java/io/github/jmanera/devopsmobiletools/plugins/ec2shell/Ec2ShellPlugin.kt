// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.plugins.ec2shell

import android.util.Log
import io.github.jmanera.devopsmobiletools.plugin.AwsContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Interactive shell on an SSM-managed EC2 instance, through the Go bridge.
 *
 * Built in rather than installed, but written against plugin-api like any
 * other plugin so the contract is exercised from phase 1.
 */
class Ec2ShellPlugin : TargetPlugin {

    override val info = PluginInfo(
        id = "io.github.jmanera.devopsmobiletools.ec2shell",
        name = "EC2 shell",
        version = "0.1.0",
    )

    override val permissions = PERMISSIONS

    /** Listing managed instances arrives in phase 2; until then targets are entered by id. */
    override suspend fun discover(context: AwsContext): List<Target> = emptyList()

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
         * actions belong to the SSM Agent's instance role, not to the user;
         * DescribeInstanceInformation comes with discovery in phase 2.
         */
        val PERMISSIONS = listOf(
            IamPermission("ssm:StartSession"),
            IamPermission("ssm:ResumeSession"),
            IamPermission("ssm:TerminateSession"),
        )

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
