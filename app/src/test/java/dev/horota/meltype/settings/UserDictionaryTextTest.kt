// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class UserDictionaryTextTest {
    @Test
    fun validLines() {
        assertEquals(emptyList<Int>(), UserDictionaryText.invalidLines("めるたいぷ\tMeltype\n# コメント\n!メモ\n\nきごうとう\t記号等"))
    }

    @Test
    fun reportsLinesWithoutTabOrWithEmptySide() {
        assertEquals(listOf(1, 2, 3), UserDictionaryText.invalidLines("めるたいぷ Meltype\n\tMeltype\nめるたいぷ\t"))
    }
}
