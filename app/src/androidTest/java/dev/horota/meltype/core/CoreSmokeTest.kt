// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CoreSmokeTest {
    private lateinit var core: CoreBridge

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val opened = CoreBridge.open(
            File(context.filesDir, "Meltype-test"),
            File(context.applicationInfo.nativeLibraryDir, "libmozc_helper.so"),
        )
        assertTrue("Core を開けない: $opened", opened is CoreOpenResult.Ready)
        val ready = opened as CoreOpenResult.Ready
        assertTrue("Mozc が使えない", ready.mozcAvailable)
        core = ready.core
    }

    /** 入力欄の代わり。deleteBefore と text を順に反映する。 */
    private class Field {
        val text = StringBuilder()
        var composing = false
        fun apply(result: SessionResult?) {
            if (result == null) return
            for (edit in result.commits) {
                text.setLength(maxOf(0, text.length - edit.deleteBefore))
                text.append(edit.text)
            }
            composing = result.view != null
        }
    }

    @Test
    fun mixedSentenceConverts() {
        val field = Field()
        for (c in "kyouhagoogledekensaku") {
            val before = if (field.composing) null else field.text.toString()
            field.apply(core.handleKey(c.uppercaseChar().code, c.code, 0, before, ""))
        }
        field.apply(core.handleKey(0x20, 0x20, 0, null, null)) // Space: 変換
        field.apply(core.handleKey(0x0D, 0, 0, null, null))    // Enter: 確定
        assertEquals("今日はgoogleで検索", field.text.toString())
    }

    @Test
    fun emojiAroundCaretDoesNotBreak() {
        val result = core.handleKey('A'.code, 'a'.code, 0, "😀", "🎉")
        assertNotNull(result)
        core.commit()
    }
}
