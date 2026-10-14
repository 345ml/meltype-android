// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.keylogger

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * spike 用: 物理キーボードとトラックパッドが出すイベントを全部記録する。捨てる前提。
 * 入力欄を置かない (IME を通さず、Activity が KeyEvent をそのまま受けるため)。
 */
class KeyLoggerActivity : Activity() {
    private val log = StringBuilder()
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply { textSize = 11f; isFocusable = false }
        val share = Button(this).apply {
            text = "共有 (ログを送る)"
            isFocusable = false
            setOnClickListener {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, log.toString())
                }, "KeyLogger"))
            }
        }
        val clear = Button(this).apply {
            text = "クリア"
            isFocusable = false
            setOnClickListener { log.setLength(0); output.text = "" }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(context).apply { addView(share); addView(clear) })
            addView(ScrollView(context).apply { addView(output) })
        })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> "DOWN"
            KeyEvent.ACTION_UP -> "UP"
            else -> "OTHER"
        }
        val unicode = event.getUnicodeChar(event.metaState)
        val base = event.getUnicodeChar(0)
        append(
            "$action ${KeyEvent.keyCodeToString(event.keyCode)}(${event.keyCode}) " +
                "meta=0x${Integer.toHexString(event.metaState)} " +
                "char=${describe(unicode)} base=${describe(base)} " +
                "repeat=${event.repeatCount} device=${event.device?.name} source=0x${Integer.toHexString(event.source)}"
        )
        return true
    }

    override fun dispatchTrackballEvent(event: MotionEvent): Boolean {
        append("TRACKBALL x=${event.x} y=${event.y} action=${event.actionMasked}")
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        append("MOTION source=0x${Integer.toHexString(event.source)} action=${event.actionMasked} x=${event.x} y=${event.y}")
        return true
    }

    private fun describe(code: Int): String =
        if (code == 0) "0" else "U+%04X'%s'".format(code and 0x7FFFFFFF, String(Character.toChars(code and 0x7FFFFFFF)))

    private fun append(line: String) {
        log.appendLine(line)
        output.text = log.takeLast(4000)
    }
}
