// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.view.KeyEvent
import dev.horota.meltype.core.CompositionView
import dev.horota.meltype.core.SessionResult
import dev.horota.meltype.core.TextEdit
import dev.horota.meltype.input.FieldPolicy
import dev.horota.meltype.input.KeyBindings
import dev.horota.meltype.input.KeyInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeControllerTest {
    private val core = FakeCore()
    private val editor = FakeEditor()
    private val modes = mutableListOf<InputMode>()
    private val status = object : StatusDisplay { override fun showMode(mode: InputMode) { modes += mode } }
    private val controller = ImeController(core, editor, editor, status)
    private val text = FieldPolicy(bypass = false, startDirect = false, codeInput = false)

    private fun letter(c: Char) = KeyInput(KeyEvent.KEYCODE_A + (c - 'a'), c.code, c.code, 0)

    @Test
    fun startInputConfiguresCore() {
        controller.startInput(FieldPolicy(bypass = false, startDirect = true, codeInput = true))
        assertTrue(core.calls.containsAll(listOf("canDelete:true", "code:true", "direct:true")))
        assertEquals(InputMode.DIRECT, modes.last())
    }

    @Test
    fun letterGoesToCoreWithSurroundingText() {
        controller.startInput(text)
        editor.before = "hello "
        editor.after = "!"
        core.calls.clear()
        assertTrue(controller.keyDown(letter('k')))
        assertEquals(listOf("key:75:107:0:hello :!"), core.calls)
    }

    @Test
    fun surroundingTextIsNullWhileComposing() {
        controller.startInput(text)
        core.next = SessionResult(true, emptyList(), CompositionView(text = "k"))
        controller.keyDown(letter('k'))
        core.calls.clear()
        controller.keyDown(letter('y'))
        assertEquals(listOf("key:89:121:0:null:null"), core.calls)
    }

    @Test
    fun notConsumedKeyGoesToAppAfterCommits() {
        controller.startInput(text)
        core.next = SessionResult(false, listOf(TextEdit(0, "abc")), null)
        assertFalse(controller.keyDown(letter('a')))
        assertTrue("commit:abc" in editor.ops)
    }

    @Test
    fun passwordFieldBypassesCore() {
        controller.startInput(FieldPolicy(bypass = true, startDirect = true, codeInput = false))
        core.calls.clear()
        assertFalse(controller.keyDown(letter('a')))
        assertEquals(emptyList<String>(), core.calls)
    }

    @Test
    fun withoutCoreEverythingPassesThrough() {
        val noCore = ImeController(null, editor, editor, status)
        noCore.startInput(text)
        assertFalse(noCore.keyDown(letter('a')))
        assertEquals(InputMode.UNAVAILABLE, modes.last())
    }

    @Test
    fun keyUpOfConsumedKeyIsConsumed() {
        controller.startInput(text)
        controller.keyDown(letter('a'))
        assertTrue(controller.keyUp(KeyEvent.KEYCODE_A))
        assertFalse(controller.keyUp(KeyEvent.KEYCODE_A))
    }

    @Test
    fun keyUpOfPassedKeyIsNotConsumed() {
        controller.startInput(text)
        core.next = SessionResult(consumed = false)
        controller.keyDown(letter('a'))
        assertFalse(controller.keyUp(KeyEvent.KEYCODE_A))
    }

    @Test
    fun toggleCommitsPendingThenSwitches() {
        controller.startInput(text)
        core.calls.clear()
        assertTrue(controller.keyDown(KeyInput(KeyEvent.KEYCODE_ZENKAKU_HANKAKU, 0, 0, 0)))
        assertEquals(listOf("commit", "direct:true"), core.calls)
        assertEquals(InputMode.DIRECT, controller.mode)
    }

    @Test
    fun roleKeyUsesCoreWhileComposing() {
        controller.startInput(text)
        core.roleResult = SessionResult(true, emptyList(), CompositionView(text = "カ"))
        core.calls.clear()
        assertTrue(controller.keyDown(KeyInput(KeyEvent.KEYCODE_HENKAN, 0, 0, 0)))
        assertEquals(listOf("role:28"), core.calls)
    }

    @Test
    fun roleKeyFallsBackToSwitching() {
        controller.startInput(text)
        core.roleResult = null
        core.calls.clear()
        controller.keyDown(KeyInput(KeyEvent.KEYCODE_MUHENKAN, 0, 0, 0))
        assertEquals(listOf("role:29", "commit", "direct:true"), core.calls)
    }

    @Test
    fun symKSendsF7() {
        controller.startInput(text)
        controller.keyDown(KeyInput(KeyBindings.Q25_SYM_KEYCODE, 0, 0, 0))
        core.calls.clear()
        assertTrue(controller.keyDown(KeyInput(KeyEvent.KEYCODE_K, 'k'.code, 'k'.code, 0)))
        assertEquals(listOf("key:118:0:0:null:null"), core.calls)
        controller.keyUp(KeyBindings.Q25_SYM_KEYCODE)
        core.calls.clear()
        controller.keyDown(KeyInput(KeyEvent.KEYCODE_K, 'k'.code, 'k'.code, 0))
        assertEquals(listOf("key:75:107:0::"), core.calls)
    }

    @Test
    fun pauseLetsKeysThroughUntilResumed() {
        controller.startInput(text)
        val pause = KeyInput(KeyEvent.KEYCODE_ZENKAKU_HANKAKU, 0, 0, KeyEvent.META_CTRL_ON)
        assertTrue(controller.keyDown(pause))
        assertEquals(InputMode.PAUSED, controller.mode)
        core.calls.clear()
        assertFalse(controller.keyDown(letter('a')))
        assertEquals(emptyList<String>(), core.calls)
        controller.keyDown(pause)
        assertEquals(InputMode.JAPANESE, controller.mode)
    }

    @Test
    fun finishInputCommitsPending() {
        controller.startInput(text)
        core.commitResult = SessionResult(true, listOf(TextEdit(0, "今日")), null)
        controller.finishInput()
        assertTrue("commit:今日" in editor.ops)
    }

    @Test
    fun selectCandidateAppliesResult() {
        controller.startInput(text)
        core.next = SessionResult(true, listOf(TextEdit(0, "京")), null)
        controller.selectCandidate(1)
        assertTrue("select:1" in core.calls)
        assertTrue("commit:京" in editor.ops)
    }
}
