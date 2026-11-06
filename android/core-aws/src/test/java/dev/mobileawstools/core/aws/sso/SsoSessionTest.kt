// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.core.aws.sso

import dev.mobileawstools.core.aws.store.softwareBox
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class SsoSessionTest {

    @get:Rule val tmp = TemporaryFolder()

    private var now = Instant.parse("2026-11-06T12:00:00Z")
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
    }
    private val box = softwareBox()
    private val tokens by lazy { EncryptedSsoTokenStore(tmp.root, box) }
    private val registrations by lazy { EncryptedClientRegistrationStore(tmp.root, box) }
    private val oidc = FakeOidc()
    private val session by lazy { SsoSession(tokens, registrations, { oidc }, clock) }

    private fun token(expiresIn: Duration, refresh: String? = "refresh-1") = SsoToken(
        accessToken = "access-1",
        refreshToken = refresh,
        expiresAt = now.plus(expiresIn),
        region = "eu-west-3",
        startUrl = "https://d-1234567890.awsapps.com/start",
    )

    private fun registered() =
        registrations.save("eu-west-3", ClientRegistration("client", "secret", now.plus(Duration.ofDays(90))))

    @Test
    fun theTokenSurvivesARestart() = runTest {
        session.signedIn(token(Duration.ofHours(1)))
        val restarted = SsoSession(EncryptedSsoTokenStore(tmp.root, box), registrations, { oidc }, clock)
        assertEquals("access-1", restarted.validToken().accessToken)
        assertEquals(0, oidc.refreshes)
    }

    @Test
    fun refreshesShortlyBeforeExpiryAndStoresTheNewToken() = runTest {
        registered()
        session.signedIn(token(Duration.ofMinutes(4)))
        oidc.next = TokenPoll.Granted("access-2", "refresh-2", 3600)

        val fresh = session.validToken()

        assertEquals("access-2", fresh.accessToken)
        assertEquals(now.plusSeconds(3600), fresh.expiresAt)
        assertEquals("refresh-1", oidc.lastRefreshToken)
        assertEquals("refresh-2", tokens.load()!!.refreshToken)
    }

    @Test
    fun keepsTheRefreshTokenWhenTheServiceDoesNotRotateIt() = runTest {
        registered()
        session.signedIn(token(Duration.ofMinutes(1)))
        oidc.next = TokenPoll.Granted("access-2", null, 3600)
        assertEquals("refresh-1", session.validToken().refreshToken)
    }

    @Test
    fun offlineKeepsAStillValidToken() = runTest {
        registered()
        val stored = token(Duration.ofMinutes(3))
        session.signedIn(stored)
        oidc.next = TokenPoll.Unreachable("Unable to resolve host")
        assertEquals(stored.accessToken, session.validToken().accessToken)
    }

    @Test
    fun aRejectedRefreshTokenSignsOut() = runTest {
        registered()
        session.signedIn(token(Duration.ofMinutes(1)))
        oidc.next = TokenPoll.Denied("invalid_grant")
        expectSignIn { session.validToken() }
        assertNull(tokens.load())
    }

    @Test
    fun anExpiredRegistrationSignsOut() = runTest {
        registrations.save("eu-west-3", ClientRegistration("client", "secret", now.minusSeconds(1)))
        session.signedIn(token(Duration.ofMinutes(1)))
        expectSignIn { session.validToken() }
        assertEquals(0, oidc.refreshes)
    }

    @Test
    fun noTokenMeansSignIn() = runTest {
        expectSignIn { session.validToken() }
    }

    @Test
    fun registrationsAreStoredPerRegionAndEncrypted() {
        registered()
        assertSame(null, registrations.load("eu-west-1"))
        assertEquals("secret", registrations.load("eu-west-3")!!.clientSecret)
        val onDisk = tmp.root.listFiles()!!.joinToString { String(it.readBytes(), Charsets.ISO_8859_1) }
        assertFalse(onDisk.contains("secret"))
    }

    @Test
    fun theTokenFileHoldsNoReadableField() {
        session.signedIn(token(Duration.ofHours(1)))
        val onDisk = String(tmp.root.resolve("sso-token.bin").readBytes(), Charsets.ISO_8859_1)
        for (plain in listOf("access-1", "refresh-1", "awsapps.com", "eu-west-3")) {
            assertFalse(plain, onDisk.contains(plain))
        }
    }

    @Test
    fun codecKeepsAMissingRefreshToken() {
        val t = token(Duration.ofHours(1), refresh = null)
        assertNull(SsoCodec.decodeToken(SsoCodec.encode(t))!!.refreshToken)
        assertNull(SsoCodec.decodeToken("garbage".toByteArray()))
    }

    private suspend fun expectSignIn(block: suspend () -> Unit) {
        try {
            block()
            fail("expected SignInRequiredException")
        } catch (e: SignInRequiredException) {
            // expected
        }
    }

    private class FakeOidc : SsoOidc {
        var next: TokenPoll = TokenPoll.Denied("unset")
        var refreshes = 0
        var lastRefreshToken: String? = null

        override suspend fun registerClient(clientName: String, scopes: List<String>) = error("not used")
        override suspend fun startDeviceAuthorization(registration: ClientRegistration, startUrl: String) = error("not used")
        override suspend fun pollToken(registration: ClientRegistration, deviceCode: String) = error("not used")

        override suspend fun refreshToken(registration: ClientRegistration, refreshToken: String): TokenPoll {
            refreshes++
            lastRefreshToken = refreshToken
            return next
        }
    }
}
