// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.plugin

enum class ConnectionState { CONNECTING, CONNECTED, RECONNECTING, CLOSED, FAILED }

/** Receives what a connection produces. Called from a background thread. */
interface ConnectionListener {
    /** Raw bytes for a terminal, as the remote side sent them. */
    fun onOutput(data: ByteArray)

    /** [detail] explains the change, such as the reason for a failure; may be empty. */
    fun onStateChanged(state: ConnectionState, detail: String)
}

/** An open connection. Closing it twice is allowed. */
interface Connection {
    fun close()
}

/** A connection that behaves like a terminal: bytes in, bytes out, a size. */
interface TerminalConnection : Connection {
    fun write(data: ByteArray)

    fun resize(cols: Int, rows: Int)
}
