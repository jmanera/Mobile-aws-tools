// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.plugin

import java.time.Instant

/**
 * Temporary AWS credentials handed to a plugin. A plugin never sees the
 * user's SSO token, only credentials scoped to the permissions it declares.
 */
class AwsCredentials(
    val accessKeyId: String,
    val secretAccessKey: String,
    val sessionToken: String,
    /** When the credentials stop working; null when unknown. */
    val expiresAt: Instant? = null,
) {
    init {
        require(accessKeyId.isNotBlank()) { "accessKeyId is required" }
        require(secretAccessKey.isNotBlank()) { "secretAccessKey is required" }
    }

    // Never print the secret or the token, even in a crash report.
    override fun toString() = "AwsCredentials(accessKeyId=$accessKeyId, expiresAt=$expiresAt)"
}

/** Where and as whom a plugin acts. */
data class AwsContext(
    val region: String,
    val credentials: AwsCredentials,
) {
    init {
        require(region.isNotBlank()) { "region is required" }
    }
}
