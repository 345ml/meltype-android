// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.input

import android.view.KeyCharacterMap
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyNormalizerTest {
    private fun key(keyCode: Int, char: Int, baseChar: Int = char, meta: Int = 0) =
        KeyNormalizer.normalize(KeyInput(keyCode, char, baseChar, meta))

    @Test
    fun lowercaseLetter() {
        assertEquals(NormalizedKey(0x41, 'a'.code, 0), key(KeyEvent.KEYCODE_A, 'a'.code))
    }

    @Test
    fun shiftedLetter() {
        assertEquals(
            NormalizedKey(0x41, 'A'.code, Modifiers.SHIFT),
            key(KeyEvent.KEYCODE_A, 'A'.code, 'a'.code, KeyEvent.META_SHIFT_ON),
        )
    }

    @Test
    fun ctrlLetterUsesBaseChar() {
        // Ctrl を押していると getUnicodeChar(metaState) は 0 になる
        assertEquals(
            NormalizedKey(0x43, 'c'.code, Modifiers.CONTROL),
            key(KeyEvent.KEYCODE_C, 0, 'c'.code, KeyEvent.META_CTRL_ON),
        )
    }

    @Test
    fun altLayerCharacterDropsAltModifier() {
        // Q25 (BlackBerry 配列): Alt+E が "2" になる。数字として渡し、Alt のショートカットにしない
        assertEquals(
            NormalizedKey(0x32, '2'.code, 0),
            key(KeyEvent.KEYCODE_E, '2'.code, 'e'.code, KeyEvent.META_ALT_ON),
        )
    }

    @Test
    fun altWithoutLayerCharacterKeepsAlt() {
        assertEquals(
            NormalizedKey(0x58, 'x'.code, Modifiers.ALT),
            key(KeyEvent.KEYCODE_X, 'x'.code, 'x'.code, KeyEvent.META_ALT_ON),
        )
    }

    @Test
    fun specialKeys() {
        assertEquals(NormalizedKey(0x0D, 0, 0), key(KeyEvent.KEYCODE_ENTER, '\n'.code))
        assertEquals(NormalizedKey(0x0D, 0, 0), key(KeyEvent.KEYCODE_DPAD_CENTER, 0))
        assertEquals(NormalizedKey(0x20, 0x20, 0), key(KeyEvent.KEYCODE_SPACE, ' '.code))
        assertEquals(NormalizedKey(0x08, 0, 0), key(KeyEvent.KEYCODE_DEL, 0))
        assertEquals(NormalizedKey(0x26, 0, 0), key(KeyEvent.KEYCODE_DPAD_UP, 0))
        assertEquals(NormalizedKey(0x76, 0, 0), key(KeyEvent.KEYCODE_F7, 0))
        assertEquals(NormalizedKey(0x79, 0, 0), key(KeyEvent.KEYCODE_F10, 0))
    }

    @Test
    fun symbols() {
        assertEquals(NormalizedKey(0xBC, ','.code, 0), key(KeyEvent.KEYCODE_COMMA, ','.code))
        assertEquals(NormalizedKey(0x07, '@'.code, Modifiers.SHIFT), key(KeyEvent.KEYCODE_2, '@'.code, '2'.code, KeyEvent.META_SHIFT_ON))
        assertEquals(NormalizedKey(0x07, 0xE9, 0), key(KeyEvent.KEYCODE_E, 0xE9))
    }

    @Test
    fun ignoredKeys() {
        assertNull(key(KeyEvent.KEYCODE_VOLUME_UP, 0))
        assertNull(key(KeyEvent.KEYCODE_GRAVE, '`'.code or KeyCharacterMap.COMBINING_ACCENT))
    }
}
