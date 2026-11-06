// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.terminal

import android.content.Context
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/**
 * Creates a [TerminalView] ready for a [RemoteTerminal]: JetBrains Mono,
 * keyboard on tap, the on-screen keyboard without suggestions, Ctrl and Alt
 * from the extra-keys row applied to what is typed, and pinch to change the
 * text size, reported to [onTextSizeChanged] so it can be remembered.
 */
fun createTerminalView(
    context: Context,
    modifiers: StickyModifiers,
    textSizeSp: Float = TextSizes.DEFAULT_SP,
    onTextSizeChanged: (Float) -> Unit = {},
): TerminalView {
    val view = TerminalView(context, null)
    val initial = TextSizes.clamp(textSizeSp)
    view.setTerminalViewClient(ViewClient(view, modifiers, initial, onTextSizeChanged))
    view.setTextSize(spToPx(context, initial))
    // The system monospace font varies by device and some barely tell i, l and 1 apart.
    view.setTypeface(context.resources.getFont(R.font.jetbrains_mono_nl_regular))
    view.setBackgroundColor(TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND])
    view.isFocusable = true
    view.isFocusableInTouchMode = true
    return view
}

/** Terminal text sizes in sp: what a pinch moves between. */
object TextSizes {
    const val DEFAULT_SP = 12f
    const val MIN_SP = 6f
    const val MAX_SP = 32f

    fun clamp(sp: Float) = sp.coerceIn(MIN_SP, MAX_SP)

    /** One step larger or smaller, within the limits. */
    fun step(sp: Float, larger: Boolean) = clamp(if (larger) sp + 1f else sp - 1f)
}

private fun spToPx(context: Context, sp: Float) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics).toInt()

/** Shows the on-screen keyboard for [view]. */
fun TerminalView.showKeyboard() {
    requestFocus()
    context.getSystemService(InputMethodManager::class.java)?.showSoftInput(this, 0)
}

private class ViewClient(
    private val view: TerminalView,
    private val modifiers: StickyModifiers,
    private var textSizeSp: Float,
    private val onTextSizeChanged: (Float) -> Unit,
) : TerminalViewClient {

    /**
     * [scale] accumulates over a pinch; each 10% changes the text by one
     * step and starts over, as in Termux. The new size reflows the emulator
     * and resizes the remote terminal.
     */
    override fun onScale(scale: Float): Float {
        if (scale > 0.9f && scale < 1.1f) return scale
        val next = TextSizes.step(textSizeSp, larger = scale > 1f)
        if (next != textSizeSp) {
            textSizeSp = next
            view.setTextSize(spToPx(view.context, next))
            onTextSizeChanged(next)
        }
        return 1f
    }

    override fun onSingleTapUp(e: MotionEvent) = view.showKeyboard()

    override fun shouldBackButtonBeMappedToEscape() = false

    // Visible-password input: no suggestions or autocorrect sending words the
    // shell never asked for, and fewer composing quirks than TYPE_NULL on some
    // keyboards (termux/termux-app#686).
    override fun shouldEnforceCharBasedInput() = true

    override fun shouldUseCtrlSpaceWorkaround() = false

    override fun isTerminalViewSelected() = true

    override fun copyModeChanged(copyMode: Boolean) = Unit

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession) = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent) = false

    override fun onLongPress(event: MotionEvent) = false

    override fun readControlKey() = modifiers.consume(Modifier.CTRL)

    override fun readAltKey() = modifiers.consume(Modifier.ALT)

    override fun readShiftKey() = false

    override fun readFnKey() = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession) = false

    // A new emulator brings its own palette; repaint the background in its colour.
    override fun onEmulatorSet() {
        val colors = view.currentSession?.emulator?.mColors?.mCurrentColors ?: return
        view.setBackgroundColor(colors[TextStyle.COLOR_INDEX_BACKGROUND])
    }

    override fun logError(tag: String, message: String) { Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
    override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "", e) }
}
