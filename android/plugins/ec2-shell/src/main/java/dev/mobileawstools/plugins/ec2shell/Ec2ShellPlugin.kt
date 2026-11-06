// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.plugins.ec2shell

import android.util.Log
import dev.mobileawstools.plugin.AwsContext
import dev.mobileawstools.plugin.Connection
import dev.mobileawstools.plugin.ConnectionListener
import dev.mobileawstools.plugin.ConnectionState
import dev.mobileawstools.plugin.IamPermission
import dev.mobileawstools.plugin.PluginInfo
import dev.mobileawstools.plugin.Target
import dev.mobileawstools.plugin.TargetPlugin
import dev.mobileawstools.plugin.TerminalConnection
import dev.mobileawstools.ssmbridge.Listener
import dev.mobileawstools.ssmbridge.LogSink
import dev.mobileawstools.ssmbridge.Session
import dev.mobileawstools.ssmbridge.Ssmbridge
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
        id = "dev.mobileawstools.ec2shell",
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
