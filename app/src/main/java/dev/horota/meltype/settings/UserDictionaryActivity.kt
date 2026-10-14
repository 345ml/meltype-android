// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** 本家の userdict.txt (「読み[Tab]単語」) をそのまま編集する。反映は次に Meltype が起動したとき。 */
class UserDictionaryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = File(MeltypePrefs.dataDirectory(this), "userdict.txt")
        val editor = EditText(this).apply {
            setText(if (file.exists()) file.readText() else "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 10
        }
        val save = Button(this).apply {
            text = "保存"
            setOnClickListener {
                val text = editor.text.toString()
                val invalid = UserDictionaryText.invalidLines(text)
                if (invalid.isNotEmpty()) {
                    Toast.makeText(context, "形式が違う行: ${invalid.joinToString()} (読み[Tab]単語)", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                file.parentFile?.mkdirs()
                file.writeText(text)
                Toast.makeText(context, "保存しました。次に Meltype が起動したときに反映されます", Toast.LENGTH_LONG).show()
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            addView(TextView(context).apply { text = "1 行に「読み[Tab]単語」。# と ! で始まる行はコメント。" })
            addView(editor)
            addView(save)
        })
    }
}
