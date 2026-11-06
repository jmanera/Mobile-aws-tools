// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools

import android.app.Application
import io.github.jmanera.devopsmobiletools.core.aws.context.AwsContextProvider
import io.github.jmanera.devopsmobiletools.core.aws.context.FileSelectionStore
import io.github.jmanera.devopsmobiletools.core.aws.sso.EncryptedClientRegistrationStore
import io.github.jmanera.devopsmobiletools.core.aws.sso.EncryptedSsoTokenStore
import io.github.jmanera.devopsmobiletools.core.aws.sso.RoleCredentialsProvider
import io.github.jmanera.devopsmobiletools.core.aws.sso.SdkSsoOidc
import io.github.jmanera.devopsmobiletools.core.aws.sso.SdkSsoPortal
import io.github.jmanera.devopsmobiletools.core.aws.sso.SsoSession
import io.github.jmanera.devopsmobiletools.core.aws.store.KeystoreSecretBox
import io.github.jmanera.devopsmobiletools.plugins.ec2shell.Ec2ShellPlugin
import java.io.File

/** Holds what every screen shares: the SSO session, the picked role and region, their credentials, the plugins. */
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

    /** The picked role and region, remembered in a plain file: neither is secret. */
    val contexts by lazy {
        AwsContextProvider(session, credentials, FileSelectionStore(File(filesDir, "selection.properties")))
    }

    /** The built-in EC2 shell, the only plugin until the marketplace of phase 6. */
    val ec2Shell by lazy { Ec2ShellPlugin() }

    private companion object {
        const val KEY_ALIAS = "sso-store"
    }
}
