// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionResultTest {
    @Test
    fun parsesCommitsAndView() {
        // 本家 SessionResult.ToJson の形 (upstream/src/Meltype.Core/Composition/MeltypeSession.cs)
        val json = """{"consumed":true,"commits":[{"deleteBefore":3,"text":"今日","expect":"api"}],""" +
            """"view":{"text":"きょう","converting":true,"selectedIndex":0,"selectedClause":0,"hint":"Space で変換",""" +
            """"candidates":["今日","京"],"clauses":["今日"],"suggestion":null,"meaning":null,"notes":[null,"英訳"],""" +
            """"predictions":[],"selectedPrediction":-1,"typed":null}}"""

        val result = SessionResultParser.parse(json)

        requireNotNull(result)
        assertTrue(result.consumed)
        assertEquals(listOf(TextEdit(3, "今日", "api")), result.commits)
        val view = requireNotNull(result.view)
        assertEquals("きょう", view.text)
        assertEquals(listOf("今日", "京"), view.candidates)
        assertEquals(listOf(null, "英訳"), view.notes)
    }

    @Test
    fun parsesNullView() {
        val result = requireNotNull(SessionResultParser.parse("""{"consumed":false,"commits":[],"view":null}"""))
        assertFalse(result.consumed)
        assertNull(result.view)
    }

    @Test
    fun brokenJsonGivesNull() {
        assertNull(SessionResultParser.parse("{"))
    }
}
