// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.core.aws.store

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM over a key supplied by [key]. Sealed layout:
 * one version byte, the 12-byte IV the cipher chose, then ciphertext and tag.
 */
open class AesGcmBox(private val key: () -> SecretKey) : SecretBox {

    override fun seal(plain: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // The provider picks a random IV; Android Keystore insists on it.
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(aad)
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "unexpected IV size ${iv.size}" }
        return byteArrayOf(VERSION) + iv + cipher.doFinal(plain)
    }

    override fun open(sealed: ByteArray, aad: ByteArray): ByteArray {
        if (sealed.size < 1 + IV_BYTES + TAG_BITS / 8 || sealed[0] != VERSION) {
            throw GeneralSecurityException("not a sealed value")
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 1, IV_BYTES))
        cipher.updateAAD(aad)
        return cipher.doFinal(sealed, 1 + IV_BYTES, sealed.size - 1 - IV_BYTES)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
