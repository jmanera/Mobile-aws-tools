// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.core.aws.store

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException

/**
 * A file whose contents are sealed with [box]. The file name is the
 * associated data, so contents copied to another file do not open.
 */
class SealedFile(private val file: File, private val box: SecretBox) {

    private val aad = file.name.toByteArray()

    /** The contents, or null if there are none or they no longer open (the file is then removed). */
    @Synchronized
    fun read(): ByteArray? {
        if (!file.exists()) return null
        return try {
            box.open(file.readBytes(), aad)
        } catch (e: GeneralSecurityException) {
            // Tampered with, or sealed with a key that is gone (app data restored
            // elsewhere, keystore reset). Nothing to recover: start over.
            file.delete()
            null
        } catch (e: IOException) {
            null
        }
    }

    /** Replaces the contents atomically: a crash leaves the old or the new value, never half of one. */
    @Synchronized
    fun write(plain: ByteArray) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(box.seal(plain, aad))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    @Synchronized
    fun delete() {
        file.delete()
    }
}
