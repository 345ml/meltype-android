// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.debug

import android.app.Activity
import android.os.Bundle
import android.widget.EditText

/** tools/e2e.sh 用の入力欄 (debug ビルドだけ)。 */
class PlaygroundActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(EditText(this).apply {
            id = PLAYGROUND_ID
            contentDescription = "playground"
            requestFocus()
        })
    }

    private companion object {
        const val PLAYGROUND_ID = 0x7f0f0001
    }
}
