// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.session

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log

/**
 * Calls [onChanged] when the device's default network comes back after a
 * loss or switches to another one (Wi-Fi to mobile, say), so open sessions
 * reconnect at once instead of waiting for their sockets to time out. The
 * network already there when watching starts does not count.
 */
class NetworkWatcher(context: Context, private val onChanged: () -> Unit) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var started = false

    private val tracker = NetworkTracker()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (tracker.available(network.networkHandle)) {
                Log.i(TAG, "Network changed; telling open sessions")
                onChanged()
            }
        }

        override fun onLost(network: Network) = tracker.lost(network.networkHandle)
    }

    /** Starts watching, once; the watcher lives as long as the process. */
    @Synchronized fun start() {
        if (started) return
        started = true
        try {
            connectivity.registerDefaultNetworkCallback(callback)
        } catch (e: RuntimeException) {
            // Sessions still resume when their sockets fail, only later.
            Log.w(TAG, "Cannot watch the network: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "network"
    }
}

/** Decides which default-network events are a change worth reconnecting for. */
internal class NetworkTracker {
    private var current: Long? = null
    private var seen = false

    /** A network became the default; true if it replaces a lost or different one. */
    @Synchronized fun available(handle: Long): Boolean {
        val changed = seen && current != handle
        current = handle
        seen = true
        return changed
    }

    @Synchronized fun lost(handle: Long) {
        if (current == handle) current = null
    }
}
