// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.instances

import io.github.jmanera.devopsmobiletools.plugin.Target
import org.junit.Assert.assertEquals
import org.junit.Test

class InstanceFilterTest {

    private val web = Target("i-0aaa111", "web-1", mapOf("ipAddress" to "10.0.1.15", "platform" to "Ubuntu 24.04"))
    private val bastion = Target("i-0bbb222", "Bastion", mapOf("ipAddress" to "10.0.0.4"))
    private val all = listOf(web, bastion)

    @Test
    fun aBlankQueryKeepsEverything() {
        assertEquals(all, all.matching("  "))
    }

    @Test
    fun matchesTheNameIgnoringCase() {
        assertEquals(listOf(bastion), all.matching("bast"))
        assertEquals(listOf(web), all.matching("WEB"))
    }

    @Test
    fun matchesTheIdAndTheDetails() {
        assertEquals(listOf(bastion), all.matching("0bbb"))
        assertEquals(listOf(web), all.matching("10.0.1."))
        assertEquals(listOf(web), all.matching("ubuntu"))
    }

    @Test
    fun noMatchMeansNothing() {
        assertEquals(emptyList<Target>(), all.matching("db"))
    }
}
