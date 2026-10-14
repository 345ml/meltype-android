// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeAppListTest {
    @Test
    fun parsesOnePackagePerLine() {
        assertEquals(setOf("com.termux", "com.foxdebug.acode"), CodeAppList.parse(" com.termux \n\ncom.foxdebug.acode\n"))
    }

    @Test
    fun ignoresInvalidNames() {
        assertEquals(setOf("com.termux"), CodeAppList.parse("com.termux\nnot a package\n日本語"))
    }

    @Test
    fun formatsSorted() {
        assertEquals("a.b\nc.d", CodeAppList.format(setOf("c.d", "a.b")))
    }
}
