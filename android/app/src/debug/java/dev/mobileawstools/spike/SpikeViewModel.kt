// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.spike

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mobileawstools.plugin.AwsContext
import dev.mobileawstools.plugin.AwsCredentials
import dev.mobileawstools.plugin.ConnectionListener
import dev.mobileawstools.plugin.ConnectionState
import dev.mobileawstools.plugin.Target
import dev.mobileawstools.plugin.TargetPlugin
import dev.mobileawstools.plugin.TerminalConnection
import dev.mobileawstools.plugins.ec2shell.Ec2ShellPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Phase 0 spike: start a Session Manager shell with fixed temporary
 * credentials and show its raw output. It reaches the shell only through the
 * plugin-api contract, as the app will.
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

    private val plugin: TargetPlugin = Ec2ShellPlugin()
    private var connection: TerminalConnection? = null
    private val screen = PlainTextScreen(maxChars = 64 * 1024)

    private val listener = object : ConnectionListener {
        override fun onOutput(data: ByteArray) {
            val text = screen.append(data)
            viewModelScope.launch { output = text }
        }

        override fun onStateChanged(state: ConnectionState, detail: String) {
            viewModelScope.launch {
                status = describe(state, detail)
                connected = state == ConnectionState.CONNECTED || state == ConnectionState.RECONNECTING
                if (state == ConnectionState.CLOSED || state == ConnectionState.FAILED) connection = null
            }
        }
    }

    fun connect() {
        if (connection != null) return
        val context = try {
            AwsContext(region.trim(), parseCredentials(credentialsJson))
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
        val target = Target(id = targetId.trim(), name = targetId.trim())
        viewModelScope.launch {
            try {
                connection = plugin.connect(target, context, listener) as TerminalConnection
            } catch (e: Exception) {
                status = "Failed: ${e.message}"
            }
        }
    }

    /**
     * Loads the credentials JSON from a file in the app's private storage and
     * deletes it, so long tokens do not have to be typed through `adb shell input`:
     *
     *     aws sts get-session-token | adb shell "run-as dev.mobileawstools.debug sh -c 'umask 077; mkdir -p files; cat > files/credentials.json'"
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
        val c = connection ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                c.write(bytes)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "Write failed: ${e.message}" }
            }
        }
    }

    fun disconnect() {
        val c = connection ?: return
        connection = null
        viewModelScope.launch(Dispatchers.IO) { runCatching { c.close() } }
    }

    override fun onCleared() {
        connection?.let { c -> Thread { runCatching { c.close() } }.start() }
        connection = null
    }

    /**
     * Accepts the output of `aws configure export-credentials --format process`
     * or of `aws sts get-session-token` / `assume-role` (`Credentials` object).
     */
    private fun parseCredentials(json: String): AwsCredentials {
        val root = JSONObject(json.trim())
        val c = root.optJSONObject("Credentials") ?: root
        val keyId = c.optString("AccessKeyId")
        val secret = c.optString("SecretAccessKey")
        require(keyId.isNotBlank() && secret.isNotBlank()) { "AccessKeyId and SecretAccessKey are required" }
        return AwsCredentials(keyId, secret, c.optString("SessionToken"))
    }

    private fun describe(state: ConnectionState, detail: String): String {
        val name = state.name.lowercase().replaceFirstChar { it.uppercase() }
        return if (detail.isBlank()) name else "$name: $detail"
    }

    private companion object {
        const val TAG = "spike"
    }
}
