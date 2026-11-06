// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.plugin

/** Version of the plugin contract, following SemVer. */
const val PLUGIN_API_VERSION = "0.1.0"

/**
 * Whether a plugin built against [pluginApiVersion] can run on this contract.
 *
 * Same major version and a minor version no newer than ours. While the major
 * version is 0, minor versions may break the contract, so they must match.
 */
fun isCompatible(pluginApiVersion: String): Boolean {
    val ours = SemVer.parse(PLUGIN_API_VERSION) ?: return false
    val theirs = SemVer.parse(pluginApiVersion) ?: return false
    if (ours.major != theirs.major) return false
    return if (ours.major == 0) ours.minor == theirs.minor else theirs.minor <= ours.minor
}

internal data class SemVer(val major: Int, val minor: Int, val patch: Int) {
    companion object {
        private val pattern = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-[0-9A-Za-z.-]+)?""")

        fun parse(text: String): SemVer? {
            val m = pattern.matchEntire(text) ?: return null
            val (major, minor, patch) = m.destructured
            return SemVer(major.toInt(), minor.toInt(), patch.toInt())
        }
    }
}
