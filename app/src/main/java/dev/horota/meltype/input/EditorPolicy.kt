// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.input

import android.text.InputType

/**
 * 入力欄ごとの動き。
 * bypass: Meltype を通さずキーをすべてアプリに渡す (学習もしない)。
 * startDirect: 英数 (直接入力) から始める。codeInput: 本家のコード入力モード (meltype_set_code_input)。
 */
data class FieldPolicy(val bypass: Boolean, val startDirect: Boolean, val codeInput: Boolean)

object EditorPolicy {
    /** 最初から入れておくコード入力のアプリ (設定画面で変えられる)。 */
    val DEFAULT_CODE_APPS: Set<String> = setOf(
        "com.termux",
        "com.foxdebug.acode",
    )

    private val BYPASS = FieldPolicy(bypass = true, startDirect = true, codeInput = false)

    fun of(inputType: Int, packageName: String?, codeApps: Set<String>): FieldPolicy {
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val codeApp = packageName != null && packageName in codeApps
        if (isPassword(inputClass, variation)) return BYPASS
        if (inputClass == InputType.TYPE_NULL) {
            return if (codeApp) FieldPolicy(bypass = false, startDirect = false, codeInput = true) else BYPASS
        }
        val startDirect = when (inputClass) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> true
            InputType.TYPE_CLASS_TEXT -> variation in DIRECT_TEXT_VARIATIONS
            else -> false
        }
        return FieldPolicy(bypass = false, startDirect = startDirect, codeInput = codeApp)
    }

    private val DIRECT_TEXT_VARIATIONS = setOf(
        InputType.TYPE_TEXT_VARIATION_URI,
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
    )

    private fun isPassword(inputClass: Int, variation: Int): Boolean = when (inputClass) {
        InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else -> false
    }
}
