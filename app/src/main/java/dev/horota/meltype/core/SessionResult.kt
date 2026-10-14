// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 入力欄への書き込み 1 回分。deleteBefore 文字をカーソルの前から消してから text を入れる。expect は消す文字 (分からなければ null)。 */
@Serializable
data class TextEdit(val deleteBefore: Int = 0, val text: String = "", val expect: String? = null)

/** 変換ボックスの中身 (本家 CompositionView)。 */
@Serializable
data class CompositionView(
    val text: String = "",
    val converting: Boolean = false,
    val selectedIndex: Int = -1,
    val selectedClause: Int = -1,
    val hint: String = "",
    val candidates: List<String> = emptyList(),
    val clauses: List<String> = emptyList(),
    val suggestion: String? = null,
    val meaning: String? = null,
    val notes: List<String?> = emptyList(),
    val predictions: List<String> = emptyList(),
    val selectedPrediction: Int = -1,
    val typed: String? = null,
)

/** キー 1 回の結果 (本家 SessionResult)。consumed が false ならキーをアプリに渡す (commits を入れた後で)。view が null なら変換ボックスを閉じる。 */
@Serializable
data class SessionResult(
    val consumed: Boolean,
    val commits: List<TextEdit> = emptyList(),
    val view: CompositionView? = null,
)

object SessionResultParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** 本家の SessionResult.ToJson の文字列を読む。読めなければ null。 */
    fun parse(text: String): SessionResult? =
        try {
            json.decodeFromString(SessionResult.serializer(), text)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
}
