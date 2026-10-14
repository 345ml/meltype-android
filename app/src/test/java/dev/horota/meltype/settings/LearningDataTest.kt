// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LearningDataTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun clearsLearningButKeepsUserFiles() {
        val dir = temp.newFolder("Meltype")
        val learning = listOf("model.json", "conversions.json", "translations.json", "languages.json", "phrases.txt")
        learning.forEach { File(dir, it).writeText("x") }
        File(dir, "mozc").mkdirs()
        File(dir, "mozc/user.db").writeText("x")
        val kept = listOf("config.json", "userdict.txt", "snippets.json")
        kept.forEach { File(dir, it).writeText("x") }

        LearningData.requestClear(dir)
        assertTrue(LearningData.applyPendingClear(dir))

        learning.forEach { assertFalse(it, File(dir, it).exists()) }
        assertFalse(File(dir, "mozc").exists())
        kept.forEach { assertTrue(it, File(dir, it).exists()) }
        assertFalse("2 回目は何もしない", LearningData.applyPendingClear(dir))
    }

    @Test
    fun nothingPending() {
        assertFalse(LearningData.applyPendingClear(temp.newFolder("Meltype")))
    }
}
