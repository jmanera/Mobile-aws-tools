// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.core.aws.sso

import dev.mobileawstools.plugin.AwsCredentials
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration

/**
 * Temporary credentials for the roles the user picks, from the SSO token in
 * [session]. Credentials stay in memory only, cached per role until
 * [RENEW_MARGIN] before they expire, and are fetched again after that.
 */
class RoleCredentialsProvider(
    private val session: SsoSession,
    private val portalFor: (region: String) -> SsoPortal,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val cache = mutableMapOf<SsoRole, AwsCredentials>()
    private val mutex = Mutex()

    /** Every role in every account assigned to the user. */
    suspend fun roles(): List<SsoRole> = withToken { portal, token ->
        portal.listAccounts(token).flatMap { portal.listRoles(token, it) }
    }

    suspend fun credentials(role: SsoRole): AwsCredentials = mutex.withLock {
        cache[role]?.takeIf { fresh(it) }?.let { return it }
        withToken { portal, token -> portal.roleCredentials(token, role) }.also { cache[role] = it }
    }

    /** Drops cached credentials, such as on sign-out. */
    suspend fun clear() = mutex.withLock { cache.clear() }

    private fun fresh(creds: AwsCredentials): Boolean {
        val expiresAt = creds.expiresAt ?: return false
        return expiresAt.isAfter(clock.instant().plus(RENEW_MARGIN))
    }

    /**
     * Runs [block] with a valid SSO access token. If the portal refuses it
     * (revoked before its expiry), refreshes it once and tries again.
     */
    private suspend fun <T> withToken(block: suspend (SsoPortal, String) -> T): T {
        val token = session.validToken()
        val portal = portalFor(token.region)
        return try {
            block(portal, token.accessToken)
        } catch (e: SsoUnauthorizedException) {
            block(portal, session.refresh().accessToken)
        }
    }

    companion object {
        val RENEW_MARGIN: Duration = Duration.ofMinutes(5)
    }
}
