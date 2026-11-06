// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.core.aws.sso

import java.time.Clock
import java.time.Duration

/** There is no usable token: the user has to sign in again. */
class SignInRequiredException(message: String) : Exception(message)

/**
 * The signed-in user's SSO token: kept in [tokens], refreshed with the
 * refresh token before it expires, so the user signs in only when the
 * refresh token itself stops working.
 */
class SsoSession(
    private val tokens: SsoTokenStore,
    private val registrations: ClientRegistrationStore,
    private val oidcFor: (region: String) -> SsoOidc,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** The stored token as is, possibly expired. */
    fun current(): SsoToken? = tokens.load()

    fun signedIn(token: SsoToken) = tokens.save(token)

    fun signOut() = tokens.clear()

    /** A token valid for at least [REFRESH_MARGIN], refreshing it if needed. */
    suspend fun validToken(): SsoToken {
        val token = tokens.load() ?: throw SignInRequiredException("Not signed in")
        if (token.expiresAt.isAfter(clock.instant().plus(REFRESH_MARGIN))) return token
        return refresh(token)
    }

    /** Exchanges the refresh token for a new access token now, whatever the expiry. */
    suspend fun refresh(): SsoToken = refresh(tokens.load() ?: throw SignInRequiredException("Not signed in"))

    private suspend fun refresh(token: SsoToken): SsoToken {
        val refreshToken = token.refreshToken ?: throw signInAgain("The token cannot be refreshed")
        val registration = registrations.load(token.region)
            ?.takeIf { it.expiresAt.isAfter(clock.instant()) }
            ?: throw signInAgain("The app's registration with IAM Identity Center expired")

        return when (val result = oidcFor(token.region).refreshToken(registration, refreshToken)) {
            is TokenPoll.Granted -> SsoToken(
                accessToken = result.accessToken,
                // The service may rotate the refresh token; keep the old one if it does not.
                refreshToken = result.refreshToken ?: refreshToken,
                expiresAt = clock.instant().plusSeconds(result.expiresInSeconds.toLong()),
                region = token.region,
                startUrl = token.startUrl,
            ).also(tokens::save)
            // Offline: the current token is still good if it has not expired yet.
            is TokenPoll.Unreachable ->
                if (token.expiresAt.isAfter(clock.instant())) token
                else throw SsoLoginException("Cannot reach IAM Identity Center to refresh the token: ${result.reason}")
            is TokenPoll.Denied -> throw signInAgain("The refresh token was rejected: ${result.reason}")
            TokenPoll.Expired -> throw signInAgain("The refresh token expired")
            TokenPoll.Pending, TokenPoll.SlowDown ->
                throw SsoLoginException("Unexpected answer to a refresh: $result")
        }
    }

    private fun signInAgain(reason: String): SignInRequiredException {
        tokens.clear()
        return SignInRequiredException(reason)
    }

    companion object {
        /** Refresh this long before expiry, so a token handed out still works for a while. */
        val REFRESH_MARGIN: Duration = Duration.ofMinutes(5)
    }
}
