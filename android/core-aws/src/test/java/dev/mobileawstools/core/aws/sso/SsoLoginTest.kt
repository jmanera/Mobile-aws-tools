// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.core.aws.sso

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class SsoLoginTest {

    private val start = Instant.parse("2026-11-06T10:00:00Z")
    private var now = start
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
    }
    private val sleeps = mutableListOf<Duration>()
    private val oidc = FakeOidc()
    private val store = InMemoryClientRegistrationStore()

    private fun login() = SsoLogin(
        region = "eu-west-3",
        startUrl = "https://example.awsapps.com/start",
        oidc = oidc,
        registrations = store,
        clock = clock,
        sleep = { d -> sleeps += d; now = now.plus(d) },
    )

    @Test
    fun registersOnceAndReusesTheRegistration() = runTest {
        login().start()
        login().start()
        assertEquals(1, oidc.registrations)
        assertEquals(SsoLogin.SCOPES, oidc.lastScopes)
    }

    @Test
    fun registersAgainWhenTheStoredOneIsAboutToExpire() = runTest {
        store.save("eu-west-3", ClientRegistration("old", "secret", start.plus(Duration.ofMinutes(30))))
        val (registration, _) = login().start()
        assertEquals(1, oidc.registrations)
        assertEquals("client-1", registration.clientId)
    }

    @Test
    fun pollsUntilGrantedAndKeepsTheRefreshToken() = runTest {
        oidc.polls += listOf(TokenPoll.Pending, TokenPoll.Pending, TokenPoll.Granted("access", "refresh", 3600))
        val (registration, auth) = login().start()
        val token = login().awaitToken(registration, auth)

        assertEquals("access", token.accessToken)
        assertEquals("refresh", token.refreshToken)
        assertEquals(now.plusSeconds(3600), token.expiresAt)
        assertEquals(listOf(Duration.ofSeconds(5), Duration.ofSeconds(5)), sleeps)
    }

    @Test
    fun slowDownAddsFiveSeconds() = runTest {
        oidc.polls += listOf(TokenPoll.SlowDown, TokenPoll.Pending, TokenPoll.Granted("access", null, 60))
        val (registration, auth) = login().start()
        val token = login().awaitToken(registration, auth)

        assertEquals(listOf(Duration.ofSeconds(10), Duration.ofSeconds(10)), sleeps)
        assertNull(token.refreshToken)
    }

    @Test
    fun keepsPollingWhileTheNetworkIsDown() = runTest {
        oidc.polls += listOf(
            TokenPoll.Unreachable("Unable to resolve host"),
            TokenPoll.Unreachable("Unable to resolve host"),
            TokenPoll.Granted("access", "refresh", 3600),
        )
        val (registration, auth) = login().start()
        assertEquals("access", login().awaitToken(registration, auth).accessToken)
        assertEquals(2, sleeps.size)
    }

    @Test
    fun deniedStopsPolling() = runTest {
        oidc.polls += TokenPoll.Denied("access_denied")
        val (registration, auth) = login().start()
        try {
            login().awaitToken(registration, auth)
            fail("expected SsoLoginException")
        } catch (e: SsoLoginException) {
            assertTrue(e.message!!.contains("access_denied"))
        }
    }

    @Test
    fun givesUpWhenTheCodeExpires() = runTest {
        repeat(200) { oidc.polls += TokenPoll.Pending }
        val (registration, auth) = login().start()
        try {
            login().awaitToken(registration, auth)
            fail("expected SsoLoginException")
        } catch (e: SsoLoginException) {
            assertFalse(now.isBefore(auth.expiresAt))
        }
    }

    @Test
    fun secretsStayOutOfToString() {
        val text = listOf(
            ClientRegistration("id", "client-secret", start),
            DeviceAuthorization("device-code", "ABCD-EFGH", "https://v", "https://v?c", start, 5),
            SsoToken("access-token", "refresh-token", start, "eu-west-3", "https://example.awsapps.com/start"),
            TokenPoll.Granted("access-token", "refresh-token", 60),
        ).joinToString()
        for (secret in listOf("client-secret", "device-code", "access-token", "refresh-token")) {
            assertFalse(text, secret in text)
        }
        assertTrue("ABCD-EFGH" in text)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAPlainHttpStartUrl() {
        SsoLogin("eu-west-3", "http://example.awsapps.com/start", oidc, store)
    }

    private inner class FakeOidc : SsoOidc {
        var registrations = 0
        var lastScopes: List<String> = emptyList()
        val polls = ArrayDeque<TokenPoll>()
        private var lastRegistration: ClientRegistration? = null

        override suspend fun registerClient(clientName: String, scopes: List<String>): ClientRegistration {
            registrations++
            lastScopes = scopes
            return ClientRegistration("client-$registrations", "secret", start.plus(Duration.ofDays(90)))
                .also { lastRegistration = it }
        }

        override suspend fun startDeviceAuthorization(registration: ClientRegistration, startUrl: String) =
            DeviceAuthorization("device", "ABCD-EFGH", "https://verify", "https://verify?code=ABCD-EFGH",
                now.plus(Duration.ofMinutes(10)), 5)

        override suspend fun pollToken(registration: ClientRegistration, deviceCode: String): TokenPoll {
            assertSame(store.load("eu-west-3"), registration)
            return polls.removeFirstOrNull() ?: TokenPoll.Pending
        }

        override suspend fun refreshToken(registration: ClientRegistration, refreshToken: String) =
            error("not used by SsoLogin")
    }
}
