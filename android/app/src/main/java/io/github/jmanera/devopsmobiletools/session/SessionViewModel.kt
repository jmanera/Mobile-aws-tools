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
import io.github.jmanera.devopsmobiletools.DevOpsMobileToolsApp
import io.github.jmanera.devopsmobiletools.plugin.ConnectionListener
import io.github.jmanera.devopsmobiletools.plugin.ConnectionState
import io.github.jmanera.devopsmobiletools.plugin.PermissionDeniedException
import io.github.jmanera.devopsmobiletools.plugin.Target
import io.github.jmanera.devopsmobiletools.plugin.TerminalConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A shell on [target] with the selected role and region. Output is shown as
 * plain text until the terminal emulator of phase 3. Closing the screen
 * closes the session.
 */
class SessionViewModel(app: Application, val target: Target) : AndroidViewModel(app) {

    private val aws = app as DevOpsMobileToolsApp

    var status by mutableStateOf("Starting session…")
        private set
    var output by mutableStateOf("")
        private set
    var state by mutableStateOf(ConnectionState.CONNECTING)
        private set

    /** Typing goes through only while connected or reconnecting. */
    val live: Boolean
        get() = state == ConnectionState.CONNECTED || state == ConnectionState.RECONNECTING

    private var connection: TerminalConnection? = null
    private val screen = PlainTextScreen(maxChars = 64 * 1024)

    private val listener = object : ConnectionListener {
        override fun onOutput(data: ByteArray) {
            val text = screen.append(data)
            viewModelScope.launch { output = text }
        }

        override fun onStateChanged(state: ConnectionState, detail: String) {
            viewModelScope.launch { show(state, detail) }
        }
    }

    init {
        connect()
    }

    fun connect() {
        if (connection != null) return
        screen.clear()
        output = ""
        show(ConnectionState.CONNECTING, "")
        viewModelScope.launch {
            try {
                val context = withContext(Dispatchers.IO) { aws.contexts.context() }
                connection = aws.ec2Shell.connect(target, context, listener) as TerminalConnection
            } catch (e: PermissionDeniedException) {
                show(ConnectionState.FAILED, e.message.orEmpty())
            } catch (e: Exception) {
                Log.w(TAG, "Connect failed: ${e.javaClass.simpleName}: ${e.message}")
                show(ConnectionState.FAILED, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun sendLine(text: String) = write((text + "\r").toByteArray(Charsets.UTF_8))

    fun send(text: String) = write(text.toByteArray(Charsets.UTF_8))

    fun write(bytes: ByteArray) {
        val c = connection ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                c.write(bytes)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "Write failed: ${e.message}" }
            }
        }
    }

    fun disconnect() {
        val c = connection ?: return
        connection = null
        viewModelScope.launch(Dispatchers.IO) { runCatching { c.close() } }
    }

    private fun show(state: ConnectionState, detail: String) {
        this.state = state
        if (state == ConnectionState.CLOSED || state == ConnectionState.FAILED) connection = null
        val name = when (state) {
            ConnectionState.CONNECTING -> "Starting session…"
            ConnectionState.CONNECTED -> "Connected"
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
