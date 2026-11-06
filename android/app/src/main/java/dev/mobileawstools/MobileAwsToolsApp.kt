// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools

import android.app.Application
import dev.mobileawstools.core.aws.sso.EncryptedClientRegistrationStore
import dev.mobileawstools.core.aws.sso.EncryptedSsoTokenStore
import dev.mobileawstools.core.aws.sso.RoleCredentialsProvider
import dev.mobileawstools.core.aws.sso.SdkSsoOidc
import dev.mobileawstools.core.aws.sso.SdkSsoPortal
import dev.mobileawstools.core.aws.sso.SsoRole
import dev.mobileawstools.core.aws.sso.SsoSession
import dev.mobileawstools.core.aws.store.KeystoreSecretBox

/** Holds what every screen shares: the SSO session and the role credentials it hands out. */
class MobileAwsToolsApp : Application() {

    // Sealed with a Keystore key, in noBackupFilesDir: never in clear, never in a backup.
    private val box by lazy { KeystoreSecretBox(KEY_ALIAS) }

    val registrations by lazy { EncryptedClientRegistrationStore(noBackupFilesDir, box) }

    val session by lazy {
        SsoSession(
            tokens = EncryptedSsoTokenStore(noBackupFilesDir, box),
            registrations = registrations,
            oidcFor = ::SdkSsoOidc,
        )
    }

    /** One cache for the whole process, so every screen reuses the same credentials. */
    val credentials by lazy { RoleCredentialsProvider(session, ::SdkSsoPortal) }

    /** The role last picked on the sign-in screen; in memory only. */
    @Volatile var selectedRole: SsoRole? = null

    private companion object {
        const val KEY_ALIAS = "sso-store"
    }
}
