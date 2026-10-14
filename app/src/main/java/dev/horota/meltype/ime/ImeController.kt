// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

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
    display: CandidateDisplay,
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
        consumedDown.clear()
        core?.setCanDelete(true)
        core?.setCodeInput(policy.codeInput)
        direct = policy.startDirect || userDirect
        core?.setDirect(direct)
        status.showMode(mode)
    }

    fun finishInput() {
        commitPending()
    }

    fun keyDown(key: KeyInput): Boolean {
        if (key.keyCode == KeyBindings.Q25_SYM_KEYCODE) symHeld = true
        val binding = KeyBindings.match(key.keyCode, key.metaState, symHeld, options)
        if (binding == BindingAction.TogglePause) return consume(key.keyCode) { togglePause() }
        val api = core ?: return false
        if (paused || policy.bypass) return false
        if (binding != null) return consume(key.keyCode) { handle(binding, api) }

        val normalized = KeyNormalizer.normalize(key) ?: return false
        val before = if (applier.composing) null else sink.textBeforeCursor(SURROUNDING_LENGTH)
        val after = if (applier.composing) null else sink.textAfterCursor(SURROUNDING_LENGTH)
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
