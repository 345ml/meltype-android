// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import dev.horota.meltype.core.SessionResult
import dev.horota.meltype.core.TextEdit

/** Core の結果 (SessionResult) を入力欄と候補バーに反映する (本家 Linux 版 _apply と同じ順序)。 */
class ResultApplier(private val sink: EditorSink, private val display: CandidateDisplay) {
    /** 入力欄に未確定の文字を出しているか。 */
    var composing: Boolean = false
        private set

    /** 候補バーに読みも出すか (入力欄が未確定の文字を描かないとき)。 */
    var inlineReading: Boolean = false

    /** 前の takeSelfEdit の後に入力欄を書き換えたか (onUpdateSelection が自分の書き換えによるものかを見分ける)。 */
    private var selfEdit = false

    fun apply(result: SessionResult) {
        sink.beginBatch()
        try {
            if (result.commits.isNotEmpty()) clearComposing()
            for (edit in result.commits) {
                if (edit.deleteBefore > 0 && canDelete(edit)) {
                    sink.deleteBefore(edit.deleteBefore)
                    selfEdit = true
                }
                if (edit.text.isNotEmpty()) {
                    sink.commit(edit.text)
                    selfEdit = true
                }
            }
            val view = result.view
            if (view == null) {
                clearComposing()
                display.hide()
            } else {
                if (view.text.isNotEmpty()) {
                    sink.setComposing(view.text)
                    composing = true
                    selfEdit = true
                } else {
                    clearComposing()
                }
                display.show(view, inlineReading)
            }
        } finally {
            sink.endBatch()
        }
    }

    /** 前に呼んでから入力欄を書き換えたか。呼ぶと忘れる。 */
    fun takeSelfEdit(): Boolean {
        val edited = selfEdit
        selfEdit = false
        return edited
    }

    /** 入力欄が変わった (onStartInput) とき、未確定の文字をその場で確定したときに呼ぶ。 */
    fun reset() {
        composing = false
    }

    private fun clearComposing() {
        if (!composing) return
        sink.setComposing("")
        sink.finishComposing()
        composing = false
        selfEdit = true
    }

    /** expect があれば、カーソルの前の文字がそれと同じときだけ消す (アプリ側で変わっていたら消さない)。 */
    private fun canDelete(edit: TextEdit): Boolean {
        val expect = edit.expect ?: return true
        return sink.textBeforeCursor(edit.deleteBefore) == expect
    }
}
