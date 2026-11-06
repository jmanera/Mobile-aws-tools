// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.core.aws.store

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.KeyGenerator

/** The same AES-GCM code the app runs, with a software key instead of Android Keystore. */
fun softwareBox(): SecretBox {
    val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    return AesGcmBox { key }
}

class SealedFileTest {

    @get:Rule val tmp = TemporaryFolder()

    private val box = softwareBox()
    private val secret = "aoaAAAAAGsecret-token-value".toByteArray()

    @Test
    fun roundTrips() {
        val file = SealedFile(File(tmp.root, "token.bin"), box)
        file.write(secret)
        assertArrayEquals(secret, file.read())
    }

    @Test
    fun neverWritesThePlainValue() {
        val f = File(tmp.root, "token.bin")
        SealedFile(f, box).write(secret)
        assertFalse(String(f.readBytes(), Charsets.ISO_8859_1).contains("secret-token-value"))
    }

    @Test
    fun missingFileReadsAsNull() {
        assertNull(SealedFile(File(tmp.root, "none.bin"), box).read())
    }

    @Test
    fun tamperedContentsAreDropped() {
        val f = File(tmp.root, "token.bin")
        SealedFile(f, box).write(secret)
        val bytes = f.readBytes().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        f.writeBytes(bytes)

        assertNull(SealedFile(f, box).read())
        assertFalse(f.exists())
    }

    @Test
    fun contentsCopiedToAnotherFileDoNotOpen() {
        val a = File(tmp.root, "a.bin")
        val b = File(tmp.root, "b.bin")
        SealedFile(a, box).write(secret)
        a.copyTo(b)
        assertNull(SealedFile(b, box).read())
    }

    @Test
    fun aDifferentKeyCannotOpenIt() {
        val f = File(tmp.root, "token.bin")
        SealedFile(f, box).write(secret)
        assertNull(SealedFile(f, softwareBox()).read())
    }
}
