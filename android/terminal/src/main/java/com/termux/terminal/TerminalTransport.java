// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package com.termux.terminal;

/**
 * Where a {@link TerminalSession} sends what the user types and the terminal
 * size: a remote shell instead of a local process. Not part of upstream
 * Termux; see docs/adr/0002-vendor-termux-terminal.md.
 * <p>
 * Methods are called from a background thread (write) or the main thread
 * (resize, close) and must not block for long.
 */
public interface TerminalTransport {

    /** Sends bytes typed by the user to the remote side. */
    void write(byte[] data, int offset, int count);

    /** Tells the remote side the terminal now has this many columns and rows. */
    void resize(int columns, int rows);

    /** Ends the remote session; the session reports it with {@link TerminalSession#onRemoteClosed}. */
    void close();
}
