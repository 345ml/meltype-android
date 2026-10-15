// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.view.KeyEvent
import dev.horota.meltype.core.CoreApi
import dev.horota.meltype.input.BindingAction
import dev.horota.meltype.input.BindingOptions
import dev.horota.meltype.input.FieldPolicy
import dev.horota.meltype.input.KeyBindings
import dev.horota.meltype.input.KeyInput
import dev.horota.meltype.input.KeyNormalizer

enum class InputMode { JAPANESE, DIRECT, PAUSED, UNAVAILABLE }

/** 英 / 日の状態の表示 (候補バーの左端)。 */
interface StatusDisplay {
    fun showMode(mode: InputMode)
}

/**
 * キー入力の流れ: KeyBindings → KeyNormalizer → Core → ResultApplier。Android に依存しない (JVM でテストする)。
 * core が null なら (読み込めなかった) キーをすべてアプリに渡す。
 */
class ImeController(
    private val core: CoreApi?,
    private val sink: EditorSink,
    private val display: CandidateDisplay,
    private val status: StatusDisplay,
) {
    private companion object {
        const val SURROUNDING_LENGTH = 64
    }

    private val applier = ResultApplier(sink, display)
    private var policy = FieldPolicy(bypass = false, startDirect = false, codeInput = false)
    /** ユーザーが最後に選んだ英数 / 日本語 (入力欄をまたいで覚える)。 */
    private var userDirect = false
    private var direct = false
    private var paused = false
    private var symHeld = false
    /** Meltype が使った KeyDown。対応する KeyUp もアプリに渡さない。 */
    private val consumedDown = mutableSetOf<Int>()

    var options: BindingOptions = BindingOptions()

    val mode: InputMode
        get() = when {
            core == null -> InputMode.UNAVAILABLE
            paused -> InputMode.PAUSED
            direct -> InputMode.DIRECT
            else -> InputMode.JAPANESE
        }

    fun startInput(policy: FieldPolicy) {
        this.policy = policy
        applier.reset()
        applier.inlineReading = !policy.surroundingText
        consumedDown.clear()
        // Sym を押したまま入力欄が変わると KeyUp が届かないので、ここで忘れる
        symHeld = false
        // 周りの文字を返せない入力欄では、確定済みの文字を確かめて消せないので、確定し直しをさせない
        core?.setCanDelete(policy.surroundingText)
        core?.setCodeInput(policy.codeInput)
        core?.setPrivate(policy.noLearning)
        direct = policy.startDirect || userDirect
        core?.setDirect(direct)
        status.showMode(mode)
    }

    fun finishInput() {
        commitPending()
    }

    /** IME が終わるとき (onDestroy)。未確定の内容を確定する。 */
    fun shutdown() {
        commitPending()
    }

    /**
     * 入力欄のカーソルが動いた (onUpdateSelection)。candidatesStart / candidatesEnd は未確定の文字の範囲 (無ければ -1)。
     * 入力中にカーソルが未確定の文字の末尾以外へ動いたら (タップなど)、未確定の文字はその場で確定する。
     * 自分の書き換え以外でカーソルが動いたら (アプリに渡したキー・タップ)、前に確定した語を確定し直さない。
     */
    fun selectionChanged(newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        val selfEdit = applier.takeSelfEdit()
        val api = core ?: return
        if (applier.composing) {
            val caretAtCompositionEnd = candidatesStart >= 0 && newSelStart == newSelEnd && newSelEnd == candidatesEnd
            if (caretAtCompositionEnd) return
            // 入力欄の文字はそのまま確定し、Core の確定結果は入れ直さない (二重になるため)
            sink.finishComposing()
            applier.reset()
            api.commit()
            api.forgetLastCommit()
            display.hide()
        } else if (!selfEdit) {
            api.forgetLastCommit()
        }
    }

    fun keyDown(key: KeyInput): Boolean {
        if (key.keyCode == KeyBindings.Q25_SYM_KEYCODE) symHeld = true
        val sym = symHeld || key.metaState and KeyEvent.META_SYM_ON != 0
        val binding = KeyBindings.match(key.keyCode, key.metaState, sym, options)
        if (binding == BindingAction.TogglePause) return consume(key.keyCode) { togglePause() }
        val api = core ?: return false
        if (paused || policy.bypass) return false
        if (binding != null) return consume(key.keyCode) { handle(binding, api) }

        val normalized = KeyNormalizer.normalize(key) ?: return false
        // 入力中と、周りの文字を返せない入力欄では渡さない (Core が自分で覚えている行を使う)
        val readSurrounding = !applier.composing && policy.surroundingText
        val before = if (readSurrounding) sink.textBeforeCursor(SURROUNDING_LENGTH) else null
        val after = if (readSurrounding) sink.textAfterCursor(SURROUNDING_LENGTH) else null
        val result = api.handleKey(normalized.vk, normalized.ch, normalized.modifiers, before, after) ?: return false
        applier.apply(result)
        if (result.consumed) consumedDown += key.keyCode
        return result.consumed
    }

    fun keyUp(keyCode: Int): Boolean {
        if (keyCode == KeyBindings.Q25_SYM_KEYCODE) symHeld = false
        return consumedDown.remove(keyCode)
    }

    fun selectCandidate(index: Int) {
        core?.selectCandidate(index)?.let(applier::apply)
    }

    private inline fun consume(keyCode: Int, action: () -> Unit): Boolean {
        action()
        consumedDown += keyCode
        return true
    }

    private fun handle(binding: BindingAction, api: CoreApi) {
        when (binding) {
            BindingAction.ToggleDirect -> setDirect(!direct)
            is BindingAction.SetDirect -> setDirect(binding.direct)
            is BindingAction.RoleKey -> {
                val result = if (direct) null else api.roleKey(binding.vk)
                if (result != null) applier.apply(result) else setDirect(binding.fallbackDirect)
            }
            is BindingAction.SendVk -> api.handleKey(binding.vk, 0, 0, null, null)?.let(applier::apply)
            BindingAction.TogglePause -> togglePause()
        }
    }

    private fun setDirect(value: Boolean) {
        commitPending()
        direct = value
        userDirect = value
        core?.setDirect(value)
        status.showMode(mode)
    }

    private fun togglePause() {
        commitPending()
        paused = !paused
        status.showMode(mode)
    }

    private fun commitPending() {
        core?.commit()?.let(applier::apply)
    }
}
