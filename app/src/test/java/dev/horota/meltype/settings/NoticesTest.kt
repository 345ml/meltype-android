// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticesTest {
    @Test
    fun composesFilesInOrderWithHeadings() {
        val files = mapOf("THIRD-PARTY-NOTICES.md" to "notices", "MOZC-LICENSE.txt" to "bsd")
        val text = Notices.compose { name -> files[name] }
        assertTrue(text.indexOf("notices") < text.indexOf("bsd"))
        assertTrue("== MOZC-LICENSE.txt ==" in text)
    }

    @Test
    fun missingFilesAreSkipped() {
        assertEquals("== LICENSE ==\ngpl\n", Notices.compose { name -> if (name == "LICENSE") "gpl" else null })
    }

    @Test
    fun everyBundledLicenseIsListed() {
        // APK に入れるライセンス (app/build.gradle.kts の copyNotices) と一致させる
        assertEquals(
            listOf(
                "THIRD-PARTY-NOTICES.md", "LICENSE", "UPSTREAM-THIRD-PARTY-NOTICES.md",
                "MOZC-LICENSE.txt", "MOZC-CREDITS.html", "DOTNET-LICENSE.txt", "DOTNET-THIRD-PARTY-NOTICES.txt",
            ),
            Notices.FILES,
        )
    }
}
