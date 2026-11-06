// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.core.aws.store

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * [AesGcmBox] whose key lives in Android Keystore: it never leaves the
 * secure hardware (or the keystore daemon on devices without it), so the
 * files it seals are useless if copied off the device.
 *
 * The key needs no user authentication yet; the biometric lock arrives in
 * phase 4.
 */
class KeystoreSecretBox(private val alias: String) : AesGcmBox(key = { keyFor(alias) }) {

    private companion object {
        const val PROVIDER = "AndroidKeyStore"

        @Synchronized
        fun keyFor(alias: String): SecretKey {
            val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
            (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
            val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
                init(spec)
                generateKey()
            }
        }
    }
}
