// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package dev.mobileawstools.plugin

import org.junit.Assert.assertTrue
import org.junit.Test

class PluginApiTest {

    @Test
    fun versionIsSemVer() {
        val semVer = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-[0-9A-Za-z.-]+)?""")
        assertTrue(PLUGIN_API_VERSION, semVer.matches(PLUGIN_API_VERSION))
    }
}
