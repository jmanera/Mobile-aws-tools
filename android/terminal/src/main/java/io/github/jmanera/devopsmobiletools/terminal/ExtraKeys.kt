// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.terminal

import android.view.KeyEvent
import com.termux.terminal.KeyHandler

/** A key of the extra-keys row: what a phone keyboard lacks. */
sealed interface ExtraKey {
    val label: String

    /** Repeats while held, like a hardware key. */
    val repeats: Boolean get() = false

    /** A key the emulator encodes by mode, such as arrows in vim's application cursor mode. */
    data class Special(override val label: String, val keyCode: Int, override val repeats: Boolean = false) : ExtraKey

    /** A character, combined with Ctrl or Alt like one typed on the keyboard. */
    data class Char(override val label: String, val codePoint: Int) : ExtraKey

    /** Ctrl or Alt, applied to the next key. */
    data class Mod(override val label: String, val modifier: Modifier) : ExtraKey

    companion object {
        val ESC = Special("ESC", KeyEvent.KEYCODE_ESCAPE)
        val TAB = Special("TAB", KeyEvent.KEYCODE_TAB)
        val CTRL = Mod("CTRL", Modifier.CTRL)
        val ALT = Mod("ALT", Modifier.ALT)
        val UP = Special("↑", KeyEvent.KEYCODE_DPAD_UP, repeats = true)
        val DOWN = Special("↓", KeyEvent.KEYCODE_DPAD_DOWN, repeats = true)
        val LEFT = Special("←", KeyEvent.KEYCODE_DPAD_LEFT, repeats = true)
        val RIGHT = Special("→", KeyEvent.KEYCODE_DPAD_RIGHT, repeats = true)
        val HOME = Special("HOME", KeyEvent.KEYCODE_MOVE_HOME)
        val END = Special("END", KeyEvent.KEYCODE_MOVE_END)
        val PGUP = Special("PGUP", KeyEvent.KEYCODE_PAGE_UP, repeats = true)
        val PGDN = Special("PGDN", KeyEvent.KEYCODE_PAGE_DOWN, repeats = true)

        /** Two rows, laid out like Termux's default so the arrows form a cross. */
        val ROWS: List<List<ExtraKey>> = listOf(
            listOf(ESC, Char("/", '/'.code), Char("-", '-'.code), Char("|", '|'.code), HOME, UP, END, PGUP),
            listOf(TAB, CTRL, ALT, Char("~", '~'.code), LEFT, DOWN, RIGHT, PGDN),
        )
    }
}

enum class Modifier { CTRL, ALT }

/**
 * Ctrl and Alt from the extra-keys row. A tap applies one to the next key
 * only; a long press locks it until tapped again. The view reads them for
 * keyboard input through [consume], which releases a one-shot modifier.
 */
class StickyModifiers {
    enum class State { OFF, ONCE, LOCKED }

    private val states = mutableMapOf(Modifier.CTRL to State.OFF, Modifier.ALT to State.OFF)

    /** Called on every change, such as to redraw the row. */
    var onChange: () -> Unit = {}

    fun state(modifier: Modifier): State = states.getValue(modifier)

    fun tap(modifier: Modifier) = set(modifier, if (state(modifier) == State.OFF) State.ONCE else State.OFF)

    fun lock(modifier: Modifier) = set(modifier, State.LOCKED)

    /** Whether [modifier] applies to the key being typed; a one-shot modifier is released. */
    fun consume(modifier: Modifier): Boolean = when (state(modifier)) {
        State.OFF -> false
        State.ONCE -> true.also { set(modifier, State.OFF) }
        State.LOCKED -> true
    }

    /** [consume] for both, as a [KeyHandler] modifier mask. */
    fun consumeMask(): Int {
        var mask = 0
        if (consume(Modifier.CTRL)) mask = mask or KeyHandler.KEYMOD_CTRL
        if (consume(Modifier.ALT)) mask = mask or KeyHandler.KEYMOD_ALT
        return mask
    }

    private fun set(modifier: Modifier, state: State) {
        if (states[modifier] == state) return
        states[modifier] = state
        onChange()
    }
}

/** The bytes a special key sends, given the emulator's modes; null if it sends nothing. */
internal fun ExtraKey.Special.code(keyMod: Int, cursorApplicationMode: Boolean, keypadApplicationMode: Boolean): String? =
    KeyHandler.getCode(keyCode, keyMod, cursorApplicationMode, keypadApplicationMode)
