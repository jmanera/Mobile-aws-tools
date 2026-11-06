// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.session

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A shell on [target] with the selected role and region, shown in a
 * terminal emulator. The terminal outlives the view, so a rotation keeps
 * the screen; closing the screen closes the session.
 */
class SessionViewModel(app: Application, val target: Target) : AndroidViewModel(app) {

    private val aws = app as DevOpsMobileToolsApp

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

    /** The size the view last asked for; applied once the connection exists. */
    @Volatile private var size: Pair<Int, Int>? = null

    private val transport = object : TerminalTransport {
        // Called on the session's writer thread.
        override fun write(data: ByteArray, offset: Int, count: Int) {
            val c = connection ?: return
            try {
                c.write(data.copyOfRange(offset, offset + count))
            } catch (e: Exception) {
                viewModelScope.launch { status = "Write failed: ${e.message}" }
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
        val t = RemoteTerminal(getApplication(), transport, modifiers)
        terminal = t
        show(ConnectionState.CONNECTING, "")
        viewModelScope.launch {
            try {
                val context = withContext(Dispatchers.IO) { aws.contexts.context() }
                val c = aws.ec2Shell.connect(target, context, Listener(t)) as TerminalConnection
                if (ended) {
                    // Failed while connecting; the listener already told the terminal.
                    return@launch
                }
                connection = c
                applySize(c)
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

    fun disconnect() {
        val c = connection ?: return
        connection = null
        viewModelScope.launch(Dispatchers.IO) { runCatching { c.close() } }
    }

    /** The bridge starts sessions at 80x24; tell it the size the view really has. */
    private fun applySize(c: TerminalConnection) {
        val (cols, rows) = size ?: return
        viewModelScope.launch(Dispatchers.IO) {
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
                t.onClosed(if (state == ConnectionState.CLOSED) "Session closed" else "Session failed: $detail")
            }
            viewModelScope.launch {
                if (terminal === t) show(state, detail)
            }
        }
    }

    private fun show(state: ConnectionState, detail: String) {
        this.state = state
        if (ended) connection = null
        val name = when (state) {
            ConnectionState.CONNECTING -> "Starting session…"
            ConnectionState.CONNECTED -> "Connected · tap the terminal to type"
            ConnectionState.RECONNECTING -> "Reconnecting…"
            ConnectionState.CLOSED -> "Session closed"
            ConnectionState.FAILED -> "Session failed"
        }
        status = if (detail.isBlank()) name else "$name: $detail"
    }

    override fun onCleared() {
        // viewModelScope is already cancelled; close on a plain thread.
        connection?.let { c -> Thread { runCatching { c.close() } }.start() }
        connection = null
    }

    private companion object {
        const val TAG = "session"
    }
}
