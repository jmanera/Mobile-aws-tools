// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.core.aws.sso

import dev.mobileawstools.core.aws.store.SealedFile
import dev.mobileawstools.core.aws.store.SecretBox
import java.io.File
import java.time.Instant
import java.util.Base64

/** Where the signed-in user's token is kept. */
interface SsoTokenStore {
    fun load(): SsoToken?

    fun save(token: SsoToken)

    fun clear()
}

class InMemorySsoTokenStore : SsoTokenStore {
    private var token: SsoToken? = null

    @Synchronized override fun load() = token

    @Synchronized override fun save(token: SsoToken) {
        this.token = token
    }

    @Synchronized override fun clear() {
        token = null
    }
}

/** The token in one [SealedFile] under [dir]. */
class EncryptedSsoTokenStore(dir: File, box: SecretBox) : SsoTokenStore {
    private val file = SealedFile(File(dir, "sso-token.bin"), box)

    override fun load() = file.read()?.let { SsoCodec.decodeToken(it) }

    override fun save(token: SsoToken) = file.write(SsoCodec.encode(token))

    override fun clear() = file.delete()
}

/** One [SealedFile] per region under [dir]; the client secret is as sensitive as a token. */
class EncryptedClientRegistrationStore(private val dir: File, private val box: SecretBox) : ClientRegistrationStore {

    override fun load(region: String) = file(region).read()?.let { SsoCodec.decodeRegistration(it) }

    override fun save(region: String, registration: ClientRegistration) =
        file(region).write(SsoCodec.encode(registration))

    private fun file(region: String): SealedFile {
        require(REGION.matches(region)) { "not a region: $region" }
        return SealedFile(File(dir, "sso-registration-$region.bin"), box)
    }

    private companion object {
        val REGION = Regex("[a-z]{2}(-[a-z]+)+-\\d")
    }
}

/**
 * Plain `key=base64(value)` lines: enough for a handful of strings, and free
 * of org.json, which unit tests on the JVM do not have.
 */
internal object SsoCodec {

    fun encode(token: SsoToken) = lines(
        "accessToken" to token.accessToken,
        "refreshToken" to token.refreshToken,
        "expiresAt" to token.expiresAt.toString(),
        "region" to token.region,
        "startUrl" to token.startUrl,
    )

    fun decodeToken(bytes: ByteArray): SsoToken? = parse(bytes)?.let { m ->
        SsoToken(
            accessToken = m["accessToken"] ?: return null,
            refreshToken = m["refreshToken"],
            expiresAt = m["expiresAt"]?.let(Instant::parse) ?: return null,
            region = m["region"] ?: return null,
            startUrl = m["startUrl"] ?: return null,
        )
    }

    fun encode(registration: ClientRegistration) = lines(
        "clientId" to registration.clientId,
        "clientSecret" to registration.clientSecret,
        "expiresAt" to registration.expiresAt.toString(),
    )

    fun decodeRegistration(bytes: ByteArray): ClientRegistration? = parse(bytes)?.let { m ->
        ClientRegistration(
            clientId = m["clientId"] ?: return null,
            clientSecret = m["clientSecret"] ?: return null,
            expiresAt = m["expiresAt"]?.let(Instant::parse) ?: return null,
        )
    }

    private fun lines(vararg fields: Pair<String, String?>): ByteArray =
        fields.filter { it.second != null }
            .joinToString("\n") { (k, v) -> "$k=" + Base64.getEncoder().encodeToString(v!!.toByteArray()) }
            .toByteArray()

    private fun parse(bytes: ByteArray): Map<String, String>? = try {
        String(bytes).lineSequence().filter { it.isNotBlank() }.associate { line ->
            val (k, v) = line.split('=', limit = 2)
            k to String(Base64.getDecoder().decode(v))
        }
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: IndexOutOfBoundsException) {
        null
    }
}
