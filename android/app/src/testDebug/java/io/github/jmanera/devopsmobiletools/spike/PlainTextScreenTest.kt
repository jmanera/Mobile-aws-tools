// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.spike

import org.junit.Assert.assertEquals
import org.junit.Test

class PlainTextScreenTest {

    private fun screen() = PlainTextScreen(maxChars = 1024)

    @Test
    fun stripsBracketedPasteAndColourSequences() {
        // What Amazon Linux's sh sends around a prompt and a coloured ls entry.
        val out = screen().append("\u001b[?2004hsh-5.2$ \u001b[01;34mdir\u001b[0m\r\n".toByteArray())
        assertEquals("sh-5.2$ dir\n", out)
    }

    @Test
    fun dropsOscWindowTitle() {
        val out = screen().append("\u001b]0;ssm-user@host\u0007prompt".toByteArray())
        assertEquals("prompt", out)
    }

    @Test
    fun decodesUtf8SplitAcrossChunks() {
        val s = screen()
        val bytes = "año".toByteArray()
        s.append(bytes.copyOfRange(0, 2)) // "a" plus the first byte of "ñ"
        assertEquals("año", s.append(bytes.copyOfRange(2, bytes.size)))
    }

    @Test
    fun backspaceDoesNotCrossLineStart() {
        val out = screen().append("ab\ncd\b\b\b".toByteArray())
        assertEquals("ab\n", out)
    }

    @Test
    fun keepsOnlyTheLastMaxChars() {
        val out = PlainTextScreen(maxChars = 4).append("abcdef".toByteArray())
        assertEquals("cdef", out)
    }

    @Test
    fun clearResetsAnUnfinishedEscape() {
        val s = screen()
        s.append("\u001b[".toByteArray())
        s.clear()
        assertEquals("ok", s.append("ok".toByteArray()))
    }
}
