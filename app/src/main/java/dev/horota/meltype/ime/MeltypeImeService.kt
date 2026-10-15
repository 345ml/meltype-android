// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import dev.horota.meltype.core.CoreBridge
import dev.horota.meltype.core.CoreOpenResult
import dev.horota.meltype.input.EditorPolicy
import dev.horota.meltype.input.KeyInput
import dev.horota.meltype.settings.MeltypePrefs
import java.io.File

/** v0.1: 物理キーボード専用。画面キーボードは出さず、候補バーだけ出す。 */
class MeltypeImeService : InputMethodService() {
    private lateinit var prefs: MeltypePrefs
    private lateinit var bar: CandidateBarView
    private lateinit var controller: ImeController
    private var core: CoreBridge? = null

    override fun onCreate() {
        super.onCreate()
        prefs = MeltypePrefs(this)
        bar = CandidateBarView(this) { index -> controller.selectCandidate(index) }
        val opened = CoreBridge.open(
            MeltypePrefs.dataDirectory(this),
            File(applicationInfo.nativeLibraryDir, "libmozc_helper.so"),
        )
        when (opened) {
            is CoreOpenResult.Ready -> {
                core = opened.core
                bar.setWarning(if (opened.mozcAvailable) null else "漢字変換を使えません")
            }
            is CoreOpenResult.Failed -> bar.setWarning("Meltype を初期化できませんでした")
        }
        controller = ImeController(core, InputConnectionSink { currentInputConnection }, bar, bar)
    }

    override fun onCreateCandidatesView(): View {
        (bar.parent as? ViewGroup)?.removeView(bar)
        return bar
    }

    override fun onEvaluateInputViewShown(): Boolean = false

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        controller.options = prefs.bindingOptions
        controller.startInput(EditorPolicy.of(attribute.inputType, attribute.packageName, prefs.codeApps, attribute.imeOptions))
        setCandidatesViewShown(true)
    }

    override fun onFinishInput() {
        controller.finishInput()
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        controller.selectionChanged(newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    override fun onDestroy() {
        controller.shutdown()
        core?.close()
        core = null
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val key = KeyInput(keyCode, event.getUnicodeChar(event.metaState), event.getUnicodeChar(0), event.metaState)
        return controller.keyDown(key) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        controller.keyUp(keyCode) || super.onKeyUp(keyCode, event)
}
