// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools

import android.app.Application
import io.github.jmanera.devopsmobiletools.core.aws.sso.EncryptedClientRegistrationStore
import io.github.jmanera.devopsmobiletools.core.aws.sso.EncryptedSsoTokenStore
import io.github.jmanera.devopsmobiletools.core.aws.sso.RoleCredentialsProvider
import io.github.jmanera.devopsmobiletools.core.aws.sso.SdkSsoOidc
import io.github.jmanera.devopsmobiletools.core.aws.sso.SdkSsoPortal
import io.github.jmanera.devopsmobiletools.core.aws.sso.SsoRole
import io.github.jmanera.devopsmobiletools.core.aws.sso.SsoSession
import io.github.jmanera.devopsmobiletools.core.aws.store.KeystoreSecretBox

/** Holds what every screen shares: the SSO session and the role credentials it hands out. */
class DevOpsMobileToolsApp : Application() {

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
