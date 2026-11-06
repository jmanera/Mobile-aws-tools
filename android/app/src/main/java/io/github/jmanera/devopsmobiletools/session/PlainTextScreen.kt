// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.session

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/**
 * Stand-in for a terminal emulator until phase 3: decodes UTF-8 across chunk
 * boundaries, drops ANSI escape sequences and handles CR, LF and backspace.
 * Good enough to see a prompt and run `uname -a`; full-screen programs need
 * the real emulator from phase 3.
 */
class PlainTextScreen(private val maxChars: Int) {
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pending = ByteArray(0)
    private val text = StringBuilder()
    private var lineStart = 0
    private var escape = EscState.NONE

    private enum class EscState { NONE, ESC, CSI, OSC }

    @Synchronized
    fun clear() {
        text.clear(); lineStart = 0; escape = EscState.NONE; pending = ByteArray(0)
        decoder.reset()
    }

    @Synchronized
    fun append(bytes: ByteArray): String {
        val input = ByteBuffer.wrap(pending + bytes)
        val out = CharBuffer.allocate(input.remaining() + 1)
        decoder.decode(input, out, false)
        pending = ByteArray(input.remaining()).also { input.get(it) }
        out.flip()
        while (out.hasRemaining()) feed(out.get())
        if (text.length > maxChars) {
            val cut = text.length - maxChars
            text.delete(0, cut)
            lineStart = (lineStart - cut).coerceAtLeast(0)
        }
        return text.toString()
    }

    private fun feed(c: Char) {
        when (escape) {
            EscState.ESC -> {
                escape = when (c) {
                    '[' -> EscState.CSI
                    ']' -> EscState.OSC
                    else -> EscState.NONE
                }
                return
            }
            EscState.CSI -> {
                if (c in '@'..'~') escape = EscState.NONE
                return
            }
            EscState.OSC -> {
                if (c == '\u0007') escape = EscState.NONE
                else if (c == '\u001b') escape = EscState.ESC
                return
            }
            EscState.NONE -> Unit
        }
        when (c) {
            '\u001b' -> escape = EscState.ESC
            '\r' -> Unit // treat CR LF as LF; a bare CR is rare in shell output
            '\n' -> { text.append('\n'); lineStart = text.length }
            '\b' -> if (text.length > lineStart) text.setLength(text.length - 1)
            '\u0007' -> Unit
            else -> if (c >= ' ' || c == '\t') text.append(c)
        }
    }
}
