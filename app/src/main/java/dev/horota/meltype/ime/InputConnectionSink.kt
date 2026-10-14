// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.view.inputmethod.InputConnection

/** EditorSink の本物。InputConnection は入力欄が変わるたびに変わるので、毎回取り直す。 */
class InputConnectionSink(private val connection: () -> InputConnection?) : EditorSink {
    override fun beginBatch() {
        connection()?.beginBatchEdit()
    }

    override fun endBatch() {
        connection()?.endBatchEdit()
    }

    override fun textBeforeCursor(length: Int): String? = connection()?.getTextBeforeCursor(length, 0)?.toString()

    override fun textAfterCursor(length: Int): String? = connection()?.getTextAfterCursor(length, 0)?.toString()

    override fun deleteBefore(length: Int) {
        connection()?.deleteSurroundingText(length, 0)
    }

    override fun commit(text: String) {
        connection()?.commitText(text, 1)
    }

    override fun setComposing(text: String) {
        connection()?.setComposingText(text, 1)
    }

    override fun finishComposing() {
        connection()?.finishComposingText()
    }
}
