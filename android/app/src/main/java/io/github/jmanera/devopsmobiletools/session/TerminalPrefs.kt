// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.session

import android.content.Context
import io.github.jmanera.devopsmobiletools.terminal.TextSizes

/** Terminal preferences kept across sessions; nothing secret. */
class TerminalPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("terminal", Context.MODE_PRIVATE)

    var textSizeSp: Float
        get() = TextSizes.clamp(prefs.getFloat(KEY_TEXT_SIZE, TextSizes.DEFAULT_SP))
        set(value) = prefs.edit().putFloat(KEY_TEXT_SIZE, value).apply()

    private companion object {
        const val KEY_TEXT_SIZE = "text_size_sp"
    }
}
