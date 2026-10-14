// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.input

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorPolicyTest {
    private val codeApps = setOf("com.termux")
    private fun of(inputType: Int, pkg: String? = "com.example") = EditorPolicy.of(inputType, pkg, codeApps)

    @Test
    fun plainText() {
        assertEquals(FieldPolicy(bypass = false, startDirect = false, codeInput = false), of(InputType.TYPE_CLASS_TEXT))
    }

    @Test
    fun passwordsBypass() {
        val bypass = FieldPolicy(bypass = true, startDirect = true, codeInput = false)
        assertEquals(bypass, of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertEquals(bypass, of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertEquals(bypass, of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertEquals(bypass, of(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
    }

    @Test
    fun urlEmailNumberStartDirect() {
        val direct = FieldPolicy(bypass = false, startDirect = true, codeInput = false)
        assertEquals(direct, of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI))
        assertEquals(direct, of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertEquals(direct, of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS))
        assertEquals(direct, of(InputType.TYPE_CLASS_NUMBER))
        assertEquals(direct, of(InputType.TYPE_CLASS_PHONE))
        assertEquals(direct, of(InputType.TYPE_CLASS_DATETIME))
    }

    @Test
    fun codeAppsUseCodeInput() {
        assertEquals(
            FieldPolicy(bypass = false, startDirect = false, codeInput = true),
            of(InputType.TYPE_CLASS_TEXT, "com.termux"),
        )
    }

    @Test
    fun nullTypeBypassesExceptCodeApps() {
        // Termux は既定で TYPE_NULL。コード入力のアプリなら Meltype を通す
        assertEquals(FieldPolicy(bypass = true, startDirect = true, codeInput = false), of(InputType.TYPE_NULL))
        assertEquals(
            FieldPolicy(bypass = false, startDirect = false, codeInput = true),
            of(InputType.TYPE_NULL, "com.termux"),
        )
    }

    @Test
    fun defaultCodeAppsIncludeTermux() {
        assertEquals(true, "com.termux" in EditorPolicy.DEFAULT_CODE_APPS)
    }

    @Test
    fun noPersonalizedLearningKeepsMeltypeButStopsLearning() {
        // Chrome のシークレットタブなど。本家 Windows 版のシークレット / InPrivate と同じく、動かすが覚えない
        assertEquals(
            FieldPolicy(bypass = false, startDirect = false, codeInput = false, noLearning = true),
            EditorPolicy.of(InputType.TYPE_CLASS_TEXT, "com.android.chrome", codeApps, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING),
        )
    }

    @Test
    fun learningAllowedByDefault() {
        assertEquals(false, EditorPolicy.of(InputType.TYPE_CLASS_TEXT, "com.example", codeApps, EditorInfo.IME_ACTION_SEARCH).noLearning)
    }
}
