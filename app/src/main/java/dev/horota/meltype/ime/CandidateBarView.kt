// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import dev.horota.meltype.core.CompositionView

/**
 * 1 行の候補バー (Q25 の 720×720 の画面でも邪魔にならない高さ)。左端に英 / 日の状態、右に候補。
 * 候補をタップすると onSelect(index)。
 */
class CandidateBarView(context: Context, private val onSelect: (Int) -> Unit) :
    LinearLayout(context), CandidateDisplay, StatusDisplay {

    private val modeLabel = label().apply { setTypeface(typeface, Typeface.BOLD) }
    private val warningLabel = label().apply { setTextColor(Color.rgb(0xC6, 0x28, 0x28)) }
    /** 入力欄が未確定の文字を描かないときの読み (Termux など)。 */
    private val readingLabel = label().apply { paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG }
    private val list = LinearLayout(context).apply { orientation = HORIZONTAL }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.rgb(0xF2, 0xF2, 0xF2))
        minimumHeight = dp(36)
        addView(modeLabel)
        addView(warningLabel)
        addView(readingLabel)
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(list)
        })
    }

    override fun show(view: CompositionView, inlineReading: Boolean) {
        readingLabel.text = if (inlineReading) view.text else ""
        list.removeAllViews()
        val items = view.candidates.ifEmpty { view.predictions }
        val selected = if (view.candidates.isNotEmpty()) view.selectedIndex else view.selectedPrediction
        items.forEachIndexed { index, text ->
            list.addView(label().apply {
                this.text = text
                if (index == selected) setBackgroundColor(Color.rgb(0x4C, 0xA0, 0xFF))
                if (view.candidates.isNotEmpty()) setOnClickListener { onSelect(index) }
            })
        }
    }

    override fun hide() {
        readingLabel.text = ""
        list.removeAllViews()
    }

    override fun showMode(mode: InputMode) {
        modeLabel.text = when (mode) {
            InputMode.JAPANESE -> "あ"
            InputMode.DIRECT -> "A"
            InputMode.PAUSED -> "⏸"
            InputMode.UNAVAILABLE -> "⚠"
        }
    }

    /** null で消す。 */
    fun setWarning(text: String?) {
        warningLabel.text = text.orEmpty()
    }

    private fun label() = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(Color.BLACK)
        setPadding(dp(10), dp(4), dp(10), dp(4))
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
