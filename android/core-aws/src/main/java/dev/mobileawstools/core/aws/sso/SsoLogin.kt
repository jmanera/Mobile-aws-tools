// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.core.aws.sso

import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Duration

/**
 * Sign-in to IAM Identity Center with the OAuth device authorization flow:
 * [start] gives a code for the user to approve in a browser, then
 * [awaitToken] polls until they do.
 */
class SsoLogin(
    private val region: String,
    private val startUrl: String,
    private val oidc: SsoOidc,
    private val registrations: ClientRegistrationStore,
    private val clock: Clock = Clock.systemUTC(),
    private val sleep: suspend (Duration) -> Unit = { delay(it.toMillis()) },
) {
    init {
        require(region.isNotBlank()) { "region is required" }
        require(startUrl.startsWith("https://")) { "startUrl must be an https URL" }
    }

    suspend fun start(): Pair<ClientRegistration, DeviceAuthorization> {
        val registration = registration()
        return registration to oidc.startDeviceAuthorization(registration, startUrl)
    }

    suspend fun awaitToken(registration: ClientRegistration, authorization: DeviceAuthorization): SsoToken {
        var interval = Duration.ofSeconds(authorization.intervalSeconds.coerceAtLeast(1).toLong())
        while (true) {
            if (!clock.instant().isBefore(authorization.expiresAt)) throw SsoLoginException("The sign-in code expired")
            when (val poll = oidc.pollToken(registration, authorization.deviceCode)) {
                is TokenPoll.Granted -> return SsoToken(
                    accessToken = poll.accessToken,
                    refreshToken = poll.refreshToken,
                    expiresAt = clock.instant().plusSeconds(poll.expiresInSeconds.toLong()),
                    region = region,
                    startUrl = startUrl,
                )
                // Keep trying until the code expires; the network comes back with the app.
                TokenPoll.Pending, is TokenPoll.Unreachable -> Unit
                // RFC 8628: add 5 seconds to the interval on slow_down.
                TokenPoll.SlowDown -> interval = interval.plusSeconds(5)
                is TokenPoll.Denied -> throw SsoLoginException("Sign-in was denied: ${poll.reason}")
                TokenPoll.Expired -> throw SsoLoginException("The sign-in code expired")
            }
            sleep(interval)
        }
    }

    /** Reuses the stored registration unless it expires within [REGISTRATION_MARGIN]. */
    private suspend fun registration(): ClientRegistration {
        val stored = registrations.load(region)
        if (stored != null && stored.expiresAt.isAfter(clock.instant().plus(REGISTRATION_MARGIN))) return stored
        return oidc.registerClient(CLIENT_NAME, SCOPES).also { registrations.save(region, it) }
    }

    companion object {
        const val CLIENT_NAME = "mobile-aws-tools"

        /** sso:account:access makes CreateToken return a refresh token. */
        val SCOPES = listOf("sso:account:access")

        val REGISTRATION_MARGIN: Duration = Duration.ofHours(1)
    }
}
