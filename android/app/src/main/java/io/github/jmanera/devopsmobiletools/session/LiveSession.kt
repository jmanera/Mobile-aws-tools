// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.session

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.terminal.TerminalTransport
import io.github.jmanera.devopsmobiletools.DevOpsMobileToolsApp
import io.github.jmanera.devopsmobiletools.plugin.ConnectionListener
import io.github.jmanera.devopsmobiletools.plugin.ConnectionState
import io.github.jmanera.devopsmobiletools.plugin.Target
import io.github.jmanera.devopsmobiletools.plugin.TerminalConnection
import io.github.jmanera.devopsmobiletools.terminal.ExtraKey
import io.github.jmanera.devopsmobiletools.terminal.Modifier
import io.github.jmanera.devopsmobiletools.terminal.RemoteTerminal
import io.github.jmanera.devopsmobiletools.terminal.StickyModifiers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A shell on [target] with the selected role and region, shown in a
 * terminal emulator. Owned by [SessionManager], not by a screen: it keeps
 * running when the user leaves the screen or the app, until [disconnect] or
 * the remote side ends it. [scope] runs on the main thread.
 */
class LiveSession(
    private val aws: DevOpsMobileToolsApp,
    val target: Target,
    private val scope: CoroutineScope,
) {

    var status by mutableStateOf("Starting session…")
        private set
    var state by mutableStateOf(ConnectionState.CONNECTING)
        private set

    /** CTRL and ALT of the extra-keys row; they outlive reconnections. */
    val modifiers = StickyModifiers()

    /** Mirrors [modifiers] for Compose. */
    var modifierStates by mutableStateOf(modifierSnapshot())
        private set

    /** The current terminal; a reconnection starts a new one. */
    var terminal by mutableStateOf<RemoteTerminal?>(null)
        private set

    val ended: Boolean
        get() = state == ConnectionState.CLOSED || state == ConnectionState.FAILED

    @Volatile private var connection: TerminalConnection? = null

    /** Set by [disconnect]; a connection still being opened is closed as soon as it exists. */
    @Volatile private var closing = false

    /** The size the view last asked for; applied once the connection exists. */
    @Volatile private var size: Pair<Int, Int>? = null

    private val transport = object : TerminalTransport {
        // Called on the session's writer thread.
        override fun write(data: ByteArray, offset: Int, count: Int) {
            val c = connection ?: return
            try {
                c.write(data.copyOfRange(offset, offset + count))
            } catch (e: Exception) {
                scope.launch { status = "Write failed: ${e.message}" }
            }
        }

        override fun resize(columns: Int, rows: Int) {
            size = columns to rows
            connection?.let(::applySize)
        }

        override fun close() = disconnect()
    }

    init {
        modifiers.onChange = { modifierStates = modifierSnapshot() }
        connect()
    }

    private fun modifierSnapshot() = Modifier.entries.associateWith(modifiers::state)

    fun connect() {
        if (connection != null) return
        closing = false
        val t = RemoteTerminal(aws, transport, modifiers)
        terminal = t
        show(ConnectionState.CONNECTING, "")
        scope.launch {
            try {
                val context = withContext(Dispatchers.IO) { aws.contexts.context() }
                val c = aws.ec2Shell.connect(target, context, Listener(t)) as TerminalConnection
                if (ended) {
                    // Failed while connecting; the listener already told the terminal.
                    return@launch
                }
                if (closing) {
                    withContext(Dispatchers.IO) { runCatching { c.close() } }
                    return@launch
                }
                connection = c
                applySize(c)
                keepCredentialsFresh(c)
            } catch (e: Exception) {
                Log.w(TAG, "Connect failed: ${e.javaClass.simpleName}: ${e.message}")
                val reason = e.message ?: e.javaClass.simpleName
                show(ConnectionState.FAILED, reason)
                t.onClosed("Could not start the session: $reason")
            }
        }
    }

    /** A key of the extra-keys row. */
    fun press(key: ExtraKey) {
        terminal?.press(key)
    }

    fun lock(modifier: Modifier) = modifiers.lock(modifier)

    /** The device's network came back or changed; see [SessionManager]. */
    fun networkChanged() {
        val c = connection ?: return
        scope.launch(Dispatchers.IO) { runCatching { c.networkChanged() } }
    }

    /**
     * Hands [c] the role's renewed credentials while it is open, so it can
     * still resume after the first ones expire. The provider renews them
     * five minutes before expiry; checking every two minutes catches that.
     */
    private fun keepCredentialsFresh(c: TerminalConnection) {
        scope.launch(Dispatchers.IO) {
            var sent: String? = runCatching { aws.contexts.context().credentials.accessKeyId }.getOrNull()
            while (connection === c) {
                delay(CREDENTIALS_CHECK_MS)
                if (connection !== c) break
                try {
                    val creds = aws.contexts.context().credentials
                    if (creds.accessKeyId != sent) {
                        c.updateCredentials(creds)
                        sent = creds.accessKeyId
                        Log.i(TAG, "Renewed credentials handed to ${target.id}: $creds")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Signed out or offline: the session keeps its credentials until they expire.
                    Log.w(TAG, "Cannot renew credentials: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
    }

    fun disconnect() {
        closing = true
        val c = connection ?: return
        connection = null
        scope.launch(Dispatchers.IO) { runCatching { c.close() } }
    }

    /** The bridge starts sessions at 80x24; tell it the size the view really has. */
    private fun applySize(c: TerminalConnection) {
        val (cols, rows) = size ?: return
        scope.launch(Dispatchers.IO) {
            try {
                c.resize(cols, rows)
            } catch (e: Exception) {
                Log.w(TAG, "Resize failed: ${e.message}")
            }
        }
    }

    /** Events of one connection, for the terminal it was opened with. */
    private inner class Listener(private val t: RemoteTerminal) : ConnectionListener {
        override fun onOutput(data: ByteArray) = t.onOutput(data)

        override fun onStateChanged(state: ConnectionState, detail: String) {
            if (state == ConnectionState.CLOSED || state == ConnectionState.FAILED) {
                t.onClosed(endReason(state, detail))
            }
            scope.launch {
                if (terminal === t) show(state, detail)
            }
        }
    }

    private fun show(state: ConnectionState, detail: String) {
        this.state = state
        if (ended) connection = null
        status = when (state) {
            ConnectionState.CONNECTING -> "Starting session…"
            ConnectionState.CONNECTED -> "Connected · tap the terminal to type"
            ConnectionState.RECONNECTING -> "Connection lost · reconnecting…"
            ConnectionState.CLOSED, ConnectionState.FAILED -> endReason(state, detail)
        }
    }

    internal companion object {
        private const val TAG = "session"
        private const val CREDENTIALS_CHECK_MS = 2 * 60 * 1000L

        /** Why a session ended, in words for the user, from the bridge's detail. */
        fun endReason(state: ConnectionState, detail: String): String = when {
            state == ConnectionState.CLOSED && detail == "closed by user" -> "Session closed"
            detail.contains("no longer exists") || detail.contains("timed out") ->
                "The session ended on AWS (idle timeout, or ended by an administrator). Reconnect starts a new one."
            detail.contains("remote side") -> "The shell on the instance exited"
            detail.startsWith("cannot resume session") ->
                "The connection could not be resumed. Reconnect starts a new session."
            state == ConnectionState.FAILED -> if (detail.isBlank()) "Session failed" else "Session failed: $detail"
            else -> "Session closed"
        }
    }
}
