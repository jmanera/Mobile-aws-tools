// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.spike

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mobileawstools.ssmbridge.Listener
import dev.mobileawstools.ssmbridge.LogSink
import dev.mobileawstools.ssmbridge.Session
import dev.mobileawstools.ssmbridge.Ssmbridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Phase 0 spike: start a Session Manager shell with fixed temporary
 * credentials through the Go bridge and show its raw output.
 *
 * There is no terminal emulator yet (phase 3): escape sequences are stripped
 * and output is shown as plain text.
 */
class SpikeViewModel : ViewModel() {

    var region by mutableStateOf("eu-west-1")
    var targetId by mutableStateOf("")
    var credentialsJson by mutableStateOf("")

    var status by mutableStateOf("Idle")
        private set
    var output by mutableStateOf("")
        private set
    var connected by mutableStateOf(false)
        private set

    private var session: Session? = null
    private val screen = PlainTextScreen(maxChars = 64 * 1024)

    private val listener = object : Listener {
        override fun onOutput(data: ByteArray) {
            val text = screen.append(data)
            viewModelScope.launch { output = text }
        }

        override fun onStateChanged(state: Long, detail: String) {
            viewModelScope.launch {
                status = describe(state, detail)
                connected = state == Ssmbridge.StateConnected || state == Ssmbridge.StateReconnecting
                if (state == Ssmbridge.StateClosed || state == Ssmbridge.StateFailed) session = null
            }
        }
    }

    private val logSink = object : LogSink {
        override fun log(level: Long, message: String) {
            // The bridge has already redacted tokens and keys.
            Log.println(priorityOf(level), TAG, message)
        }
    }

    fun connect() {
        if (session != null) return
        val creds = try {
            parseCredentials(credentialsJson)
        } catch (e: Exception) {
            // org.json messages quote the input, which would put the secret key on screen.
            val reason = if (e is IllegalArgumentException) e.message else "malformed or truncated JSON"
            status = "Invalid credentials JSON (${credentialsJson.length} chars): $reason"
            Log.w(TAG, status)
            return
        }
        status = "Starting session…"
        screen.clear()
        output = ""
        val cfg = Ssmbridge.newConfig().apply {
            region = this@SpikeViewModel.region.trim()
            targetID = targetId.trim()
            accessKeyID = creds.accessKeyId
            secretAccessKey = creds.secretAccessKey
            sessionToken = creds.sessionToken
            logLevel = Ssmbridge.LevelDebug
            cols = 80
            rows = 24
        }
        viewModelScope.launch {
            try {
                session = withContext(Dispatchers.IO) { Ssmbridge.startShell(cfg, listener, logSink) }
            } catch (e: Exception) {
                status = "Failed: ${e.message}"
            }
        }
    }

    /**
     * Loads the credentials JSON from a file in the app's private storage and
     * deletes it, so long tokens do not have to be typed through `adb shell input`:
     *
     *     aws sts get-session-token | adb shell "run-as dev.mobileawstools.spike sh -c 'umask 077; mkdir -p files; cat > files/credentials.json'"
     */
    fun loadCredentials(file: File) {
        if (!file.exists()) {
            status = "No credentials file at ${file.path}"
            return
        }
        credentialsJson = file.readText()
        file.delete()
        status = "Credentials loaded (${credentialsJson.length} chars)"
        Log.i(TAG, status)
    }

    fun send(text: String) = write(text.toByteArray(Charsets.UTF_8))

    fun sendLine(text: String) = send(text + "\r")

    fun write(bytes: ByteArray) {
        val s = session ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                s.write(bytes)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "Write failed: ${e.message}" }
            }
        }
    }

    fun disconnect() {
        val s = session ?: return
        session = null
        viewModelScope.launch(Dispatchers.IO) { runCatching { s.close() } }
    }

    override fun onCleared() {
        session?.let { s -> Thread { runCatching { s.close() } }.start() }
        session = null
    }

    private data class Creds(val accessKeyId: String, val secretAccessKey: String, val sessionToken: String)

    /**
     * Accepts the output of `aws configure export-credentials --format process`
     * or of `aws sts get-session-token` / `assume-role` (`Credentials` object).
     */
    private fun parseCredentials(json: String): Creds {
        val root = JSONObject(json.trim())
        val c = root.optJSONObject("Credentials") ?: root
        val keyId = c.optString("AccessKeyId")
        val secret = c.optString("SecretAccessKey")
        require(keyId.isNotBlank() && secret.isNotBlank()) { "AccessKeyId and SecretAccessKey are required" }
        return Creds(keyId, secret, c.optString("SessionToken"))
    }

    private fun describe(state: Long, detail: String): String {
        val name = when (state) {
            Ssmbridge.StateConnecting -> "Connecting"
            Ssmbridge.StateConnected -> "Connected"
            Ssmbridge.StateReconnecting -> "Reconnecting"
            Ssmbridge.StateClosed -> "Closed"
            Ssmbridge.StateFailed -> "Failed"
            else -> "State $state"
        }
        return if (detail.isBlank()) name else "$name: $detail"
    }

    private fun priorityOf(level: Long): Int = when (level) {
        Ssmbridge.LevelTrace -> Log.VERBOSE
        Ssmbridge.LevelDebug -> Log.DEBUG
        Ssmbridge.LevelInfo -> Log.INFO
        Ssmbridge.LevelWarn -> Log.WARN
        else -> Log.ERROR
    }

    private companion object {
        const val TAG = "ssmbridge"
    }
}
