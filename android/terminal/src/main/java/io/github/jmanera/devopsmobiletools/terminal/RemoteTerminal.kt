// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TerminalTransport
import com.termux.view.TerminalView

/**
 * A terminal for one remote session: the Termux emulator, fed through
 * [onOutput] and [onClosed], sending input and size changes to [transport].
 *
 * Outlives the view: keep it in a ViewModel and hand it to each new
 * [TerminalView] (after a rotation, say) with [attach].
 */
class RemoteTerminal(
    context: Context,
    transport: TerminalTransport,
    private val onFinished: () -> Unit = {},
) : TerminalSessionClient {

    private val appContext = context.applicationContext

    val session = TerminalSession(transport, TRANSCRIPT_ROWS, this)

    @Volatile private var view: TerminalView? = null

    /** Shows this terminal in [view], which draws it until [detach]. */
    fun attach(view: TerminalView) {
        this.view = view
        view.attachSession(session)
    }

    fun detach(view: TerminalView) {
        if (this.view === view) this.view = null
    }

    /** Output from the remote side; any thread. */
    fun onOutput(data: ByteArray) = session.onRemoteOutput(data, 0, data.size)

    /** The remote side ended; [reason] is shown in the terminal. Any thread. */
    fun onClosed(reason: String) = session.onRemoteClosed(reason)

    /** Text as the user would type it, through bracketed paste if the program asked for it. */
    fun paste(text: String) {
        if (session.isRunning) session.emulator?.paste(text)
    }

    override fun onTextChanged(changedSession: TerminalSession) {
        view?.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) = Unit

    override fun onSessionFinished(finishedSession: TerminalSession) = onFinished()

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val clipboard = appContext.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Terminal", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession) {
        val clipboard = appContext.getSystemService(ClipboardManager::class.java) ?: return
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(appContext)
        if (!text.isNullOrEmpty()) paste(text.toString())
    }

    override fun onBell(session: TerminalSession) = Unit

    override fun onColorsChanged(session: TerminalSession) {
        view?.invalidate()
    }

    override fun onTerminalCursorStateChange(state: Boolean) = Unit

    override fun getTerminalCursorStyle(): Int? = null

    override fun logError(tag: String, message: String) { Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
    override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "", e) }

    private companion object {
        /** Lines kept above the screen for scrolling back. */
        const val TRANSCRIPT_ROWS = 2000
    }
}
