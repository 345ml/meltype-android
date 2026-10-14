// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import dev.horota.meltype.core.CompositionView

/** 入力欄と候補バーの偽物。呼ばれた操作を ops に記録する。 */
class FakeEditor : EditorSink, CandidateDisplay {
    val ops = mutableListOf<String>()
    var before: String = ""
    var after: String = ""

    override fun beginBatch() { ops += "begin" }
    override fun endBatch() { ops += "end" }
    override fun textBeforeCursor(length: Int): String = before.takeLast(length)
    override fun textAfterCursor(length: Int): String = after.take(length)
    override fun deleteBefore(length: Int) { ops += "delete:$length" }
    override fun commit(text: String) { ops += "commit:$text" }
    override fun setComposing(text: String) { ops += "composing:$text" }
    override fun finishComposing() { ops += "finish" }
    override fun show(view: CompositionView) { ops += "show:${view.candidates.joinToString("|")}" }
    override fun hide() { ops += "hide" }
}
