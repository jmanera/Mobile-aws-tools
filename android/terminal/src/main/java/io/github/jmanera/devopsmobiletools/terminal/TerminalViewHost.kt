// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.terminal

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/**
 * Creates a [TerminalView] ready for a [RemoteTerminal]: monospace text,
 * keyboard on tap, the on-screen keyboard without suggestions.
 */
fun createTerminalView(context: Context, textSizeSp: Float = DEFAULT_TEXT_SIZE_SP): TerminalView {
    val view = TerminalView(context, null)
    view.setTerminalViewClient(ViewClient(view))
    view.setTextSize(
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp, context.resources.displayMetrics).toInt(),
    )
    view.setTypeface(Typeface.MONOSPACE)
    view.isFocusable = true
    view.isFocusableInTouchMode = true
    return view
}

const val DEFAULT_TEXT_SIZE_SP = 12f

/** Shows the on-screen keyboard for [view]. */
fun TerminalView.showKeyboard() {
    requestFocus()
    context.getSystemService(InputMethodManager::class.java)?.showSoftInput(this, 0)
}

private class ViewClient(private val view: TerminalView) : TerminalViewClient {

    override fun onScale(scale: Float): Float = 1f

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

    override fun readControlKey() = false

    override fun readAltKey() = false

    override fun readShiftKey() = false

    override fun readFnKey() = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession) = false

    override fun onEmulatorSet() = Unit

    override fun logError(tag: String, message: String) { Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
    override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "", e) }
}
