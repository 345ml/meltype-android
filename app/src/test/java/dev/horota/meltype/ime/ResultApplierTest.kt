// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import dev.horota.meltype.core.CompositionView
import dev.horota.meltype.core.SessionResult
import dev.horota.meltype.core.TextEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultApplierTest {
    private val editor = FakeEditor()
    private val applier = ResultApplier(editor, editor)

    @Test
    fun commitOnly() {
        applier.apply(SessionResult(true, listOf(TextEdit(0, "abc"))))
        assertEquals(listOf("begin", "commit:abc", "hide", "end"), editor.ops)
    }

    @Test
    fun composingThenCommit() {
        applier.apply(SessionResult(true, emptyList(), CompositionView(text = "きょう", candidates = listOf("今日"))))
        assertTrue(applier.composing)
        editor.ops.clear()

        applier.apply(SessionResult(true, listOf(TextEdit(0, "今日")), null))

        assertEquals(listOf("begin", "composing:", "finish", "commit:今日", "hide", "end"), editor.ops)
        assertFalse(applier.composing)
    }

    @Test
    fun deleteWhenExpectMatches() {
        editor.before = "see api"
        applier.apply(SessionResult(true, listOf(TextEdit(3, "あぴ", "api"))))
        assertEquals(listOf("begin", "delete:3", "commit:あぴ", "hide", "end"), editor.ops)
    }

    @Test
    fun deleteSkippedWhenExpectMismatches() {
        // アプリ側で文字が変わっていたら、他人の文字を消さない
        editor.before = "see APX"
        applier.apply(SessionResult(true, listOf(TextEdit(3, "あぴ", "api"))))
        assertEquals(listOf("begin", "commit:あぴ", "hide", "end"), editor.ops)
    }

    @Test
    fun deleteWithoutExpect() {
        applier.apply(SessionResult(true, listOf(TextEdit(2, "x"))))
        assertEquals(listOf("begin", "delete:2", "commit:x", "hide", "end"), editor.ops)
    }

    @Test
    fun emptyViewTextShowsCandidatesWithoutComposing() {
        applier.apply(SessionResult(true, emptyList(), CompositionView(text = "", predictions = listOf("今日は"))))
        assertEquals(listOf("begin", "show:", "end"), editor.ops)
        assertFalse(applier.composing)
    }

    @Test
    fun resetForgetsComposing() {
        applier.apply(SessionResult(true, emptyList(), CompositionView(text = "か")))
        applier.reset()
        assertFalse(applier.composing)
    }
}
