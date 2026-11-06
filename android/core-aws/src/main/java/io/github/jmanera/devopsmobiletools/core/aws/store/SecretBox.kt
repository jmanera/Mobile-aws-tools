// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.core.aws.store

import java.security.GeneralSecurityException

/**
 * Authenticated encryption for small secrets at rest. [aad] binds a sealed
 * value to its purpose (such as the file it lives in), so a value moved
 * elsewhere does not open.
 */
interface SecretBox {
    fun seal(plain: ByteArray, aad: ByteArray): ByteArray

    /** Throws [GeneralSecurityException] if [sealed] was not produced by [seal] with the same [aad]. */
    fun open(sealed: ByteArray, aad: ByteArray): ByteArray
}
