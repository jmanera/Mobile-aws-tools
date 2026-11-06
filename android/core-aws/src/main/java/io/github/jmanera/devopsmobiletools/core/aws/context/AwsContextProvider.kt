// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.core.aws.context

import io.github.jmanera.devopsmobiletools.core.aws.sso.RoleCredentialsProvider
import io.github.jmanera.devopsmobiletools.core.aws.sso.SignInRequiredException
import io.github.jmanera.devopsmobiletools.core.aws.sso.SsoSession
import io.github.jmanera.devopsmobiletools.plugin.AwsContext

/** Signed in, but no role and region picked yet. */
class SelectionRequiredException : Exception("Pick an account, a role and a region first")

/**
 * The [AwsContext] every plugin receives: the region and the credentials of
 * the role the user picked, remembered across restarts in [store].
 */
class AwsContextProvider(
    private val session: SsoSession,
    private val credentials: RoleCredentialsProvider,
    private val store: SelectionStore,
) {
    /** The remembered selection for the signed-in portal; null if none or signed out. */
    fun selection(): Selection? = session.current()?.let { store.load(it.startUrl) }

    fun select(selection: Selection) {
        val token = session.current() ?: throw SignInRequiredException("Not signed in")
        store.save(token.startUrl, selection)
    }

    /** Credentials for the selected role, cached and renewed by [credentials]. */
    suspend fun context(): AwsContext {
        val selection = selection() ?: throw SelectionRequiredException()
        return AwsContext(selection.region, credentials.credentials(selection.role))
    }
}
