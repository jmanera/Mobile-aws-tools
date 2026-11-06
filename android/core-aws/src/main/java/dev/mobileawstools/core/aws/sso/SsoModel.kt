// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.core.aws.sso

import java.time.Instant

/** This app's OIDC client registration in IAM Identity Center. Reusable until [expiresAt]. */
class ClientRegistration(
    val clientId: String,
    val clientSecret: String,
    val expiresAt: Instant,
) {
    override fun toString() = "ClientRegistration(clientId=$clientId, expiresAt=$expiresAt)"
}

/** A pending device authorization: the user approves [userCode] at [verificationUriComplete]. */
class DeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String,
    val expiresAt: Instant,
    val intervalSeconds: Int,
) {
    override fun toString() = "DeviceAuthorization(userCode=$userCode, expiresAt=$expiresAt)"
}

/** An IAM Identity Center token. [refreshToken] is null if the registration did not grant one. */
class SsoToken(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Instant,
    val region: String,
    val startUrl: String,
) {
    override fun toString() =
        "SsoToken(startUrl=$startUrl, region=$region, expiresAt=$expiresAt, refreshable=${refreshToken != null})"
}

/** One answer from CreateToken while the user has not finished in the browser. */
sealed interface TokenPoll {
    data class Granted(val accessToken: String, val refreshToken: String?, val expiresInSeconds: Int) : TokenPoll {
        override fun toString() = "Granted(expiresInSeconds=$expiresInSeconds, refreshable=${refreshToken != null})"
    }

    /** The user has not approved yet. */
    data object Pending : TokenPoll

    /** Polling too fast; wait longer between polls. */
    data object SlowDown : TokenPoll

    /** The user refused, or the request was rejected. */
    data class Denied(val reason: String) : TokenPoll

    /** The device code expired before the user approved. */
    data object Expired : TokenPoll

    /**
     * The call did not reach IAM Identity Center. Usual while the user is in
     * the browser: Android may cut the network of an app in the background.
     */
    data class Unreachable(val reason: String) : TokenPoll
}

class SsoLoginException(message: String, cause: Throwable? = null) : Exception(message, cause)
