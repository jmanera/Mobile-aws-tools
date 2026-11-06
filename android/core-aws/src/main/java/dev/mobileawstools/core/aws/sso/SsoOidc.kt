// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.core.aws.sso

/**
 * The three IAM Identity Center OIDC calls the device flow needs, behind an
 * interface so the flow can be tested without the network.
 */
interface SsoOidc {
    suspend fun registerClient(clientName: String, scopes: List<String>): ClientRegistration

    suspend fun startDeviceAuthorization(registration: ClientRegistration, startUrl: String): DeviceAuthorization

    /** One CreateToken call with the device code grant. */
    suspend fun pollToken(registration: ClientRegistration, deviceCode: String): TokenPoll

    /** CreateToken with the refresh token grant: [TokenPoll.Granted], [TokenPoll.Denied], [TokenPoll.Expired] or [TokenPoll.Unreachable]. */
    suspend fun refreshToken(registration: ClientRegistration, refreshToken: String): TokenPoll
}

/** Keeps client registrations between sign-ins, one per region. */
interface ClientRegistrationStore {
    fun load(region: String): ClientRegistration?

    fun save(region: String, registration: ClientRegistration)
}

/** Keeps registrations for the life of the process; for tests. */
class InMemoryClientRegistrationStore : ClientRegistrationStore {
    private val registrations = mutableMapOf<String, ClientRegistration>()

    @Synchronized
    override fun load(region: String) = registrations[region]

    @Synchronized
    override fun save(region: String, registration: ClientRegistration) {
        registrations[region] = registration
    }
}
