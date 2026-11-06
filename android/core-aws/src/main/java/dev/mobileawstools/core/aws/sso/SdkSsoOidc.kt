// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.core.aws.sso

import aws.sdk.kotlin.services.ssooidc.SsoOidcClient
import aws.sdk.kotlin.services.ssooidc.model.AccessDeniedException
import aws.sdk.kotlin.services.ssooidc.model.AuthorizationPendingException
import aws.sdk.kotlin.services.ssooidc.model.ExpiredTokenException
import aws.sdk.kotlin.services.ssooidc.model.InvalidGrantException
import aws.sdk.kotlin.services.ssooidc.model.SlowDownException
import aws.sdk.kotlin.services.ssooidc.registerClient
import aws.sdk.kotlin.services.ssooidc.startDeviceAuthorization
import aws.sdk.kotlin.services.ssooidc.createToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * [SsoOidc] backed by the AWS SDK for Kotlin. These calls are unauthenticated.
 * Each runs on [Dispatchers.IO]: the SDK blocks on parts of the request
 * (Android rejects that on the main thread).
 */
class SdkSsoOidc(private val region: String) : SsoOidc {

    override suspend fun registerClient(clientName: String, scopes: List<String>): ClientRegistration =
        io { client().use { c ->
            val out = c.registerClient {
                this.clientName = clientName
                clientType = "public"
                this.scopes = scopes
            }
            ClientRegistration(
                clientId = requireNotNull(out.clientId) { "RegisterClient returned no clientId" },
                clientSecret = requireNotNull(out.clientSecret) { "RegisterClient returned no clientSecret" },
                expiresAt = Instant.ofEpochSecond(out.clientSecretExpiresAt),
            )
        } }

    override suspend fun startDeviceAuthorization(registration: ClientRegistration, startUrl: String): DeviceAuthorization =
        io { client().use { c ->
            val out = c.startDeviceAuthorization {
                clientId = registration.clientId
                clientSecret = registration.clientSecret
                this.startUrl = startUrl
            }
            DeviceAuthorization(
                deviceCode = requireNotNull(out.deviceCode) { "StartDeviceAuthorization returned no deviceCode" },
                userCode = requireNotNull(out.userCode) { "StartDeviceAuthorization returned no userCode" },
                verificationUri = out.verificationUri.orEmpty(),
                verificationUriComplete = out.verificationUriComplete ?: out.verificationUri.orEmpty(),
                expiresAt = Instant.now().plusSeconds(out.expiresIn.toLong()),
                intervalSeconds = out.interval,
            )
        } }

    override suspend fun pollToken(registration: ClientRegistration, deviceCode: String): TokenPoll =
        io { client().use { c ->
            try {
                val out = c.createToken {
                    clientId = registration.clientId
                    clientSecret = registration.clientSecret
                    grantType = DEVICE_CODE_GRANT
                    this.deviceCode = deviceCode
                }
                TokenPoll.Granted(
                    accessToken = requireNotNull(out.accessToken) { "CreateToken returned no accessToken" },
                    refreshToken = out.refreshToken,
                    expiresInSeconds = out.expiresIn,
                )
            } catch (e: AuthorizationPendingException) {
                TokenPoll.Pending
            } catch (e: SlowDownException) {
                TokenPoll.SlowDown
            } catch (e: ExpiredTokenException) {
                TokenPoll.Expired
            } catch (e: AccessDeniedException) {
                TokenPoll.Denied(e.error ?: "access_denied")
            } catch (e: InvalidGrantException) {
                TokenPoll.Denied(e.error ?: "invalid_grant")
            }
        } }

    private fun client() = SsoOidcClient { region = this@SdkSsoOidc.region }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private companion object {
        const val DEVICE_CODE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
    }
}
