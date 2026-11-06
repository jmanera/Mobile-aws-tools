// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.core.aws.context

import io.github.jmanera.devopsmobiletools.core.aws.sso.InMemoryClientRegistrationStore
import io.github.jmanera.devopsmobiletools.core.aws.sso.InMemorySsoTokenStore
import io.github.jmanera.devopsmobiletools.core.aws.sso.RoleCredentialsProvider
import io.github.jmanera.devopsmobiletools.core.aws.sso.SignInRequiredException
import io.github.jmanera.devopsmobiletools.core.aws.sso.SsoAccount
import io.github.jmanera.devopsmobiletools.core.aws.sso.SsoPortal
import io.github.jmanera.devopsmobiletools.core.aws.sso.SsoRole
import io.github.jmanera.devopsmobiletools.core.aws.sso.SsoSession
import io.github.jmanera.devopsmobiletools.core.aws.sso.SsoToken
import io.github.jmanera.devopsmobiletools.plugin.AwsCredentials
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Duration
import java.time.Instant

class AwsContextProviderTest {

    @get:Rule val tmp = TemporaryFolder()

    private val tokens = InMemorySsoTokenStore()
    private val session = SsoSession(tokens, InMemoryClientRegistrationStore(), { error("no refresh expected") })
    private val portal = FakePortal()
    private val credentials = RoleCredentialsProvider(session, { portal })
    private val store = InMemorySelectionStore()
    private val provider = AwsContextProvider(session, credentials, store)

    private val dev = SsoRole("111111111111", "dev", "ReadOnly")
    private val prod = SsoRole("222222222222", "prod", "Operator")

    private fun signIn(startUrl: String = PORTAL) =
        session.signedIn(SsoToken("access", "refresh", Instant.now().plus(Duration.ofHours(1)), "eu-west-3", startUrl))

    @Test
    fun theContextUsesTheSelectedRoleAndRegion() = runTest {
        signIn()
        provider.select(Selection(prod, "eu-west-1"))

        val context = provider.context()

        assertEquals("eu-west-1", context.region)
        assertEquals("key-for-Operator", context.credentials.accessKeyId)
        assertEquals(listOf(prod), portal.rolesAsked)
    }

    @Test
    fun aNewSelectionReplacesTheOldOne() = runTest {
        signIn()
        provider.select(Selection(dev, "us-east-1"))
        provider.select(Selection(prod, "eu-west-3"))

        assertEquals(Selection(prod, "eu-west-3"), provider.selection())
        assertEquals("key-for-Operator", provider.context().credentials.accessKeyId)
    }

    @Test
    fun withoutASelectionThereIsNoContext() = runTest {
        signIn()
        try {
            provider.context()
            fail("expected SelectionRequiredException")
        } catch (e: SelectionRequiredException) {
            assertEquals(emptyList<SsoRole>(), portal.rolesAsked)
        }
    }

    @Test
    fun theSelectionSurvivesSigningOutAndInToTheSamePortal() {
        signIn()
        provider.select(Selection(dev, "eu-west-1"))
        session.signOut()
        assertNull(provider.selection())

        signIn()
        assertEquals(Selection(dev, "eu-west-1"), provider.selection())
    }

    @Test
    fun anotherPortalDoesNotGetThePreviousSelection() {
        signIn()
        provider.select(Selection(dev, "eu-west-1"))
        session.signOut()

        signIn("https://d-other.awsapps.com/start")
        assertNull(provider.selection())
    }

    @Test(expected = SignInRequiredException::class)
    fun selectingNeedsASignedInUser() {
        provider.select(Selection(dev, "eu-west-1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun anUnknownRegionIsRejected() {
        Selection(dev, "eu-nowhere-9")
    }

    @Test
    fun theFileStoreRoundTripsPerPortal() {
        val file = File(tmp.root, "selection.properties")
        FileSelectionStore(file).save(PORTAL, Selection(prod, "ap-southeast-2"))

        val reopened = FileSelectionStore(file)
        assertEquals(Selection(prod, "ap-southeast-2"), reopened.load(PORTAL))
        assertNull(reopened.load("https://d-other.awsapps.com/start"))
    }

    @Test
    fun aMissingOrDamagedFileMeansNoSelection() {
        val file = File(tmp.root, "selection.properties")
        assertNull(FileSelectionStore(file).load(PORTAL))

        file.writeText("startUrl=$PORTAL\naccountId=1\n")
        assertNull(FileSelectionStore(file).load(PORTAL))

        file.writeText("startUrl=$PORTAL\naccountId=1\naccountName=a\nroleName=r\nregion=xx-gone-1\n")
        assertNull(FileSelectionStore(file).load(PORTAL))
    }

    private class FakePortal : SsoPortal {
        val rolesAsked = mutableListOf<SsoRole>()

        override suspend fun listAccounts(accessToken: String) = emptyList<SsoAccount>()

        override suspend fun listRoles(accessToken: String, account: SsoAccount) = emptyList<SsoRole>()

        override suspend fun roleCredentials(accessToken: String, role: SsoRole): AwsCredentials {
            rolesAsked += role
            return AwsCredentials("key-for-${role.roleName}", "secret", "session", Instant.now().plus(Duration.ofHours(1)))
        }
    }

    private companion object {
        const val PORTAL = "https://d-1234567890.awsapps.com/start"
    }
}
