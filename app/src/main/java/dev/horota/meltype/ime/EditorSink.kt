// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import dev.horota.meltype.core.CompositionView

/** 入力欄への書き込み。本物は InputConnectionSink (InputConnection)。 */
interface EditorSink {
    fun beginBatch()
    fun endBatch()
    fun textBeforeCursor(length: Int): String?
    fun textAfterCursor(length: Int): String?
    fun deleteBefore(length: Int)
    fun commit(text: String)
    /** 未確定の文字を出す。"" なら消す。 */
    fun setComposing(text: String)
    fun finishComposing()
}

/** 候補バー。 */
interface CandidateDisplay {
    /** inlineReading: 入力欄が未確定の文字を描かないので、候補バーに読みも出す (Termux など)。 */
    fun show(view: CompositionView, inlineReading: Boolean)
    fun hide()
}
