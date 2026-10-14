// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.input

import android.view.KeyCharacterMap
import android.view.KeyEvent

/** KeyEvent から取り出した値。char = getUnicodeChar(metaState)、baseChar = getUnicodeChar(0)。 */
data class KeyInput(val keyCode: Int, val char: Int, val baseChar: Int, val metaState: Int)

/** Core (meltype_handle_key) に渡す形。vk は Windows の仮想キーコード。 */
data class NormalizedKey(val vk: Int, val ch: Int, val modifiers: Int)

object Modifiers {
    const val SHIFT = 1
    const val CONTROL = 2
    const val ALT = 4
    const val META = 8
}

/** Android のキーを本家 Core が使う Windows の仮想キーコードに変える (本家 Linux 版 virtual_key と同じ規則)。 */
object KeyNormalizer {
    /** 文字を入力するキーで、特別な扱いのないもの (記号など)。Core は文字だけを見る。 */
    private const val OTHER_CHARACTER_KEY = 0x07
    private const val VK_SPACE = 0x20

    private val SPECIAL_KEYS = mapOf(
        KeyEvent.KEYCODE_ENTER to 0x0D,
        KeyEvent.KEYCODE_NUMPAD_ENTER to 0x0D,
        // トラックパッドを押したとき (Q25)。入力中でなければ Core が consumed=false を返し、アプリに渡る
        KeyEvent.KEYCODE_DPAD_CENTER to 0x0D,
        KeyEvent.KEYCODE_TAB to 0x09,
        KeyEvent.KEYCODE_SPACE to VK_SPACE,
        KeyEvent.KEYCODE_DEL to 0x08,
        KeyEvent.KEYCODE_FORWARD_DEL to 0x2E,
        KeyEvent.KEYCODE_ESCAPE to 0x1B,
        KeyEvent.KEYCODE_DPAD_LEFT to 0x25,
        KeyEvent.KEYCODE_DPAD_UP to 0x26,
        KeyEvent.KEYCODE_DPAD_RIGHT to 0x27,
        KeyEvent.KEYCODE_DPAD_DOWN to 0x28,
        KeyEvent.KEYCODE_MOVE_HOME to 0x24,
        KeyEvent.KEYCODE_MOVE_END to 0x23,
        KeyEvent.KEYCODE_PAGE_UP to 0x21,
        KeyEvent.KEYCODE_PAGE_DOWN to 0x22,
        KeyEvent.KEYCODE_F6 to 0x75,
        KeyEvent.KEYCODE_F7 to 0x76,
        KeyEvent.KEYCODE_F8 to 0x77,
        KeyEvent.KEYCODE_F9 to 0x78,
        KeyEvent.KEYCODE_F10 to 0x79,
    )

    private val SYMBOL_KEYS = mapOf(',' to 0xBC, '.' to 0xBE, '-' to 0xBD, '/' to 0xBF, '[' to 0xDB, ']' to 0xDD)

    fun normalize(key: KeyInput): NormalizedKey? {
        val modifiers = modifiersOf(key)
        SPECIAL_KEYS[key.keyCode]?.let { vk -> return NormalizedKey(vk, if (vk == VK_SPACE) VK_SPACE else 0, modifiers) }
        // Ctrl などで char が 0 のときは、修飾なしの文字で判断する
        val c = if (key.char != 0) key.char else key.baseChar
        if (c == 0 || (c and KeyCharacterMap.COMBINING_ACCENT) != 0) return null
        if (c < 0x20) return null
        val vk = when (c) {
            in 'A'.code..'Z'.code -> c
            in 'a'.code..'z'.code -> c - 0x20
            in '0'.code..'9'.code -> c
            else -> (if (c <= 0xFFFF) SYMBOL_KEYS[c.toChar()] else null) ?: OTHER_CHARACTER_KEY
        }
        return NormalizedKey(vk, c, modifiers)
    }

    private fun modifiersOf(key: KeyInput): Int {
        var modifiers = 0
        if (key.metaState and KeyEvent.META_SHIFT_ON != 0) modifiers = modifiers or Modifiers.SHIFT
        if (key.metaState and KeyEvent.META_CTRL_ON != 0) modifiers = modifiers or Modifiers.CONTROL
        // Alt で別の文字 (Q25 の数字・記号の層) になったときは、その文字を打っただけとして Alt を外す
        val altLayer = key.char != 0 && key.char != key.baseChar
        if (key.metaState and KeyEvent.META_ALT_ON != 0 && !altLayer) modifiers = modifiers or Modifiers.ALT
        if (key.metaState and KeyEvent.META_META_ON != 0) modifiers = modifiers or Modifiers.META
        return modifiers
    }
}
