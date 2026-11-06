// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.core.aws.sso

import dev.mobileawstools.plugin.AwsCredentials
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class RoleCredentialsProviderTest {

    private var now = Instant.parse("2026-11-06T12:00:00Z")
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
    }

    private val tokens = InMemorySsoTokenStore()
    private val registrations = InMemoryClientRegistrationStore()
    private val oidc = RefreshingOidc()
    private val session = SsoSession(tokens, registrations, { oidc }, clock)
    private val portal = FakePortal()
    private val provider = RoleCredentialsProvider(session, { portal }, clock)

    private val ssm = SsoRole("123456789012", "personal", "MobileAwsToolsSSM")

    private fun signIn(expiresIn: Duration = Duration.ofHours(1)) {
        registrations.save("eu-west-3", ClientRegistration("client", "secret", now.plus(Duration.ofDays(90))))
        session.signedIn(SsoToken("access-1", "refresh-1", now.plus(expiresIn), "eu-west-3", "https://d-1.awsapps.com/start"))
    }

    @Test
    fun listsEveryRoleOfEveryAccount() = runTest {
        signIn()
        portal.accounts = mapOf(
            SsoAccount("111111111111", "dev") to listOf("ReadOnly", "Admin"),
            SsoAccount("123456789012", "personal") to listOf("MobileAwsToolsSSM"),
        )
        assertEquals(
            listOf("dev/ReadOnly", "dev/Admin", "personal/MobileAwsToolsSSM"),
            provider.roles().map { "${it.accountName}/${it.roleName}" },
        )
        assertEquals(setOf("access-1"), portal.tokensSeen)
    }

    @Test
    fun cachesCredentialsUntilShortlyBeforeTheyExpire() = runTest {
        signIn(Duration.ofHours(8))
        val first = provider.credentials(ssm)
        now = now.plus(Duration.ofMinutes(54))
        assertSame(first, provider.credentials(ssm))
        assertEquals(1, portal.credentialCalls)

        now = now.plus(Duration.ofMinutes(2)) // 4 minutes left
        val renewed = provider.credentials(ssm)
        assertEquals(2, portal.credentialCalls)
        assertEquals(now.plus(Duration.ofHours(1)), renewed.expiresAt)
    }

    @Test
    fun anExpiringSsoTokenIsRefreshedFirst() = runTest {
        signIn(Duration.ofMinutes(2))
        provider.credentials(ssm)
        assertEquals(1, oidc.refreshes)
        assertEquals(setOf("access-2"), portal.tokensSeen)
    }

    @Test
    fun aRevokedAccessTokenIsRefreshedAndRetriedOnce() = runTest {
        signIn()
        portal.refuse = setOf("access-1")
        provider.credentials(ssm)
        assertEquals(1, oidc.refreshes)
        assertEquals(setOf("access-1", "access-2"), portal.tokensSeen)
    }

    @Test
    fun notSignedInMeansSignIn() = runTest {
        try {
            provider.credentials(ssm)
            fail("expected SignInRequiredException")
        } catch (e: SignInRequiredException) {
            assertEquals(0, portal.credentialCalls)
        }
    }

    @Test
    fun clearForgetsCachedCredentials() = runTest {
        signIn()
        provider.credentials(ssm)
        provider.clear()
        provider.credentials(ssm)
        assertEquals(2, portal.credentialCalls)
    }

    private inner class FakePortal : SsoPortal {
        var accounts: Map<SsoAccount, List<String>> = emptyMap()
        var refuse: Set<String> = emptySet()
        val tokensSeen = mutableSetOf<String>()
        var credentialCalls = 0

        private fun check(token: String) {
            tokensSeen += token
            if (token in refuse) throw SsoUnauthorizedException("revoked")
        }

        override suspend fun listAccounts(accessToken: String): List<SsoAccount> {
            check(accessToken)
            return accounts.keys.toList()
        }

        override suspend fun listRoles(accessToken: String, account: SsoAccount): List<SsoRole> {
            check(accessToken)
            return accounts.getValue(account).map { SsoRole(account.id, account.name, it) }
        }

        override suspend fun roleCredentials(accessToken: String, role: SsoRole): AwsCredentials {
            check(accessToken)
            credentialCalls++
            return AwsCredentials("ASIA$credentialCalls", "secret", "session", now.plus(Duration.ofHours(1)))
        }
    }

    private inner class RefreshingOidc : SsoOidc {
        var refreshes = 0

        override suspend fun registerClient(clientName: String, scopes: List<String>) = error("not used")
        override suspend fun startDeviceAuthorization(registration: ClientRegistration, startUrl: String) = error("not used")
        override suspend fun pollToken(registration: ClientRegistration, deviceCode: String) = error("not used")

        override suspend fun refreshToken(registration: ClientRegistration, refreshToken: String): TokenPoll {
            refreshes++
            return TokenPoll.Granted("access-${refreshes + 1}", null, 3600)
        }
    }
}
