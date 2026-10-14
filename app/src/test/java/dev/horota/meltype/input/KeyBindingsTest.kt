// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyBindingsTest {
    private val defaults = BindingOptions()
    private fun match(keyCode: Int, meta: Int = 0, sym: Boolean = false, options: BindingOptions = defaults) =
        KeyBindings.match(keyCode, meta, sym, options)

    @Test
    fun zenkakuHankakuToggles() {
        assertEquals(BindingAction.ToggleDirect, match(KeyEvent.KEYCODE_ZENKAKU_HANKAKU))
    }

    @Test
    fun ctrlZenkakuHankakuPauses() {
        assertEquals(BindingAction.TogglePause, match(KeyEvent.KEYCODE_ZENKAKU_HANKAKU, KeyEvent.META_CTRL_ON))
    }

    @Test
    fun eisuGoesDirect() {
        assertEquals(BindingAction.SetDirect(true), match(KeyEvent.KEYCODE_EISU))
    }

    @Test
    fun roleKeys() {
        assertEquals(BindingAction.RoleKey(0x1C, fallbackDirect = false), match(KeyEvent.KEYCODE_HENKAN))
        assertEquals(BindingAction.RoleKey(0x1D, fallbackDirect = true), match(KeyEvent.KEYCODE_MUHENKAN))
        assertEquals(BindingAction.RoleKey(0x15, fallbackDirect = false), match(KeyEvent.KEYCODE_KATAKANA_HIRAGANA))
    }

    @Test
    fun ctrlSpaceAndShiftSpaceToggle() {
        assertEquals(BindingAction.ToggleDirect, match(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON))
        assertEquals(BindingAction.ToggleDirect, match(KeyEvent.KEYCODE_SPACE, KeyEvent.META_SHIFT_ON))
    }

    @Test
    fun spaceTogglesCanBeTurnedOff() {
        val off = BindingOptions(shiftSpaceToggles = false, ctrlSpaceToggles = false)
        assertNull(match(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON, options = off))
        assertNull(match(KeyEvent.KEYCODE_SPACE, KeyEvent.META_SHIFT_ON, options = off))
    }

    @Test
    fun plainSpaceIsNotBound() {
        assertNull(match(KeyEvent.KEYCODE_SPACE))
    }

    @Test
    fun symK() {
        assertEquals(BindingAction.SendVk(0x76), match(KeyEvent.KEYCODE_K, sym = true))
    }

    @Test
    fun symE() {
        assertEquals(BindingAction.SendVk(0x79), match(KeyEvent.KEYCODE_E, sym = true))
    }

    @Test
    fun altLettersAreNotBound() {
        // Q25 の Alt+文字 は数字・記号なので、Meltype の割り当てに使わない
        assertNull(match(KeyEvent.KEYCODE_E, KeyEvent.META_ALT_ON))
        assertNull(match(KeyEvent.KEYCODE_K, KeyEvent.META_ALT_ON))
    }
}
