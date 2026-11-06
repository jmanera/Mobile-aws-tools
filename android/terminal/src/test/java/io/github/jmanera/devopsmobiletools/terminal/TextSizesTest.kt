// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TextSizesTest {

    @Test
    fun aPinchMovesOneStep() {
        assertEquals(13f, TextSizes.step(12f, larger = true))
        assertEquals(11f, TextSizes.step(12f, larger = false))
    }

    @Test
    fun sizesStayWithinTheLimits() {
        assertEquals(TextSizes.MAX_SP, TextSizes.step(TextSizes.MAX_SP, larger = true))
        assertEquals(TextSizes.MIN_SP, TextSizes.step(TextSizes.MIN_SP, larger = false))
        assertEquals(TextSizes.MIN_SP, TextSizes.clamp(1f))
        assertEquals(TextSizes.MAX_SP, TextSizes.clamp(100f))
    }
}
