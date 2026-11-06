// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.session

import androidx.compose.runtime.mutableStateListOf
import io.github.jmanera.devopsmobiletools.DevOpsMobileToolsApp
import io.github.jmanera.devopsmobiletools.plugin.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The sessions of the whole process, one per target. While any is live,
 * [SessionService] keeps the process in the foreground so Android does not
 * kill it or cut its network in the background.
 */
class SessionManager(private val aws: DevOpsMobileToolsApp) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val networks = NetworkWatcher(aws) {
        scope.launch { live.forEach(LiveSession::networkChanged) }
    }

    /** Open sessions, ended ones included until dismissed, so their terminal can be read. */
    val sessions = mutableStateListOf<LiveSession>()

    /** Sessions still connected or connecting. */
    val live: List<LiveSession>
        get() = sessions.filter { !it.ended }

    /** The session for [target], opening one if there is none. Main thread. */
    fun open(target: Target): LiveSession {
        sessions.firstOrNull { it.target.id == target.id }?.let { return it }
        return LiveSession(aws, target, scope).also {
            sessions += it
            networks.start()
            SessionService.start(aws)
        }
    }

    fun find(targetId: String): LiveSession? = sessions.firstOrNull { it.target.id == targetId }

    /** Ends [session] and forgets it. */
    fun close(session: LiveSession) {
        session.disconnect()
        sessions.remove(session)
    }

    /** Ends every session, such as from the notification. */
    fun closeAll() {
        sessions.toList().forEach(::close)
    }
}
