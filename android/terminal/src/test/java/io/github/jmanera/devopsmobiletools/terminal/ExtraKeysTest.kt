// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.terminal

import com.termux.terminal.KeyHandler
import io.github.jmanera.devopsmobiletools.terminal.StickyModifiers.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraKeysTest {

    private val mods = StickyModifiers()

    @Test
    fun aTappedModifierAppliesToTheNextKeyOnly() {
        mods.tap(Modifier.CTRL)
        assertEquals(State.ONCE, mods.state(Modifier.CTRL))
        assertTrue(mods.consume(Modifier.CTRL))
        assertFalse(mods.consume(Modifier.CTRL))
        assertEquals(State.OFF, mods.state(Modifier.CTRL))
    }

    @Test
    fun tappingAgainCancelsIt() {
        mods.tap(Modifier.ALT)
        mods.tap(Modifier.ALT)
        assertFalse(mods.consume(Modifier.ALT))
    }

    @Test
    fun aLockedModifierStaysUntilTapped() {
        mods.lock(Modifier.CTRL)
        assertTrue(mods.consume(Modifier.CTRL))
        assertTrue(mods.consume(Modifier.CTRL))
        mods.tap(Modifier.CTRL)
        assertEquals(State.OFF, mods.state(Modifier.CTRL))
    }

    @Test
    fun theMaskCombinesBothAndReleasesOneShots() {
        mods.tap(Modifier.CTRL)
        mods.lock(Modifier.ALT)
        assertEquals(KeyHandler.KEYMOD_CTRL or KeyHandler.KEYMOD_ALT, mods.consumeMask())
        assertEquals(KeyHandler.KEYMOD_ALT, mods.consumeMask())
    }

    @Test
    fun changesAreReported() {
        var changes = 0
        mods.onChange = { changes++ }
        mods.tap(Modifier.CTRL)
        mods.consume(Modifier.CTRL)
        mods.consume(Modifier.CTRL) // already off: no change
        assertEquals(2, changes)
    }

    @Test
    fun arrowsFollowTheCursorKeysMode() {
        assertEquals("\u001b[A", ExtraKey.UP.code(0, cursorApplicationMode = false, keypadApplicationMode = false))
        // vim, less and htop switch to application cursor keys.
        assertEquals("\u001bOA", ExtraKey.UP.code(0, cursorApplicationMode = true, keypadApplicationMode = false))
    }

    @Test
    fun modifiersReachSpecialKeys() {
        assertEquals("\u001b[1;5D", ExtraKey.LEFT.code(KeyHandler.KEYMOD_CTRL, false, false))
        assertEquals("\u001b[5~", ExtraKey.PGUP.code(0, false, false))
        assertEquals("\u001b", ExtraKey.ESC.code(0, false, false))
        assertEquals("\t", ExtraKey.TAB.code(0, false, false))
    }

    @Test
    fun theRowsHoldEveryKeyTheIssueAsksFor() {
        val labels = ExtraKey.ROWS.flatten().map { it.label }.toSet()
        for (label in listOf("ESC", "TAB", "CTRL", "ALT", "↑", "↓", "←", "→", "HOME", "END", "PGUP", "PGDN", "|", "~", "/", "-")) {
            assertTrue(label, label in labels)
        }
    }
}
