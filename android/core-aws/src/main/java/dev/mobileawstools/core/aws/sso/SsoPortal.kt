// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.core.aws.sso

import aws.sdk.kotlin.services.sso.SsoClient
import aws.sdk.kotlin.services.sso.getRoleCredentials
import aws.sdk.kotlin.services.sso.listAccountRoles
import aws.sdk.kotlin.services.sso.listAccounts
import aws.sdk.kotlin.services.sso.model.UnauthorizedException
import dev.mobileawstools.plugin.AwsCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

data class SsoAccount(val id: String, val name: String)

/** A role the user may assume in an account, as assigned in IAM Identity Center. */
data class SsoRole(val accountId: String, val accountName: String, val roleName: String)

/** The access token was refused: expired or revoked. */
class SsoUnauthorizedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The IAM Identity Center portal API: the accounts and roles assigned to the
 * user, and temporary credentials for one of them. Every call takes the SSO
 * access token and throws [SsoUnauthorizedException] when it is refused.
 */
interface SsoPortal {
    suspend fun listAccounts(accessToken: String): List<SsoAccount>

    suspend fun listRoles(accessToken: String, account: SsoAccount): List<SsoRole>

    suspend fun roleCredentials(accessToken: String, role: SsoRole): AwsCredentials
}

/** [SsoPortal] backed by the AWS SDK for Kotlin, on [Dispatchers.IO]. */
class SdkSsoPortal(private val region: String) : SsoPortal {

    override suspend fun listAccounts(accessToken: String): List<SsoAccount> = call { c ->
        val accounts = mutableListOf<SsoAccount>()
        var next: String? = null
        do {
            val out = c.listAccounts {
                this.accessToken = accessToken
                nextToken = next
            }
            out.accountList.orEmpty().mapNotNullTo(accounts) { a ->
                a.accountId?.let { SsoAccount(it, a.accountName ?: it) }
            }
            next = out.nextToken
        } while (next != null)
        accounts
    }

    override suspend fun listRoles(accessToken: String, account: SsoAccount): List<SsoRole> = call { c ->
        val roles = mutableListOf<SsoRole>()
        var next: String? = null
        do {
            val out = c.listAccountRoles {
                this.accessToken = accessToken
                accountId = account.id
                nextToken = next
            }
            out.roleList.orEmpty().mapNotNullTo(roles) { r ->
                r.roleName?.let { SsoRole(account.id, account.name, it) }
            }
            next = out.nextToken
        } while (next != null)
        roles
    }

    override suspend fun roleCredentials(accessToken: String, role: SsoRole): AwsCredentials = call { c ->
        val creds = c.getRoleCredentials {
            this.accessToken = accessToken
            accountId = role.accountId
            roleName = role.roleName
        }.roleCredentials ?: error("GetRoleCredentials returned no credentials")
        AwsCredentials(
            accessKeyId = requireNotNull(creds.accessKeyId) { "no accessKeyId" },
            secretAccessKey = requireNotNull(creds.secretAccessKey) { "no secretAccessKey" },
            sessionToken = requireNotNull(creds.sessionToken) { "no sessionToken" },
            expiresAt = Instant.ofEpochMilli(creds.expiration),
        )
    }

    private suspend fun <T> call(block: suspend (SsoClient) -> T): T = withContext(Dispatchers.IO) {
        try {
            SsoClient { region = this@SdkSsoPortal.region }.use { block(it) }
        } catch (e: UnauthorizedException) {
            throw SsoUnauthorizedException("IAM Identity Center refused the access token", e)
        }
    }
}
