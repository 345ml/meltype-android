// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import dev.horota.meltype.core.CoreApi
import dev.horota.meltype.core.SessionResult

/** Core の偽物。呼ばれた操作を calls に記録し、next / roleResult / commitResult を返す。 */
class FakeCore : CoreApi {
    val calls = mutableListOf<String>()
    var next: SessionResult? = SessionResult(consumed = true)
    var roleResult: SessionResult? = null
    var commitResult: SessionResult? = SessionResult(consumed = true)

    override fun handleKey(vk: Int, ch: Int, modifiers: Int, before: String?, after: String?): SessionResult? {
        calls += "key:$vk:$ch:$modifiers:$before:$after"
        return next
    }
    override fun roleKey(vk: Int): SessionResult? { calls += "role:$vk"; return roleResult }
    override fun commit(): SessionResult? { calls += "commit"; return commitResult }
    override fun selectCandidate(index: Int): SessionResult? { calls += "select:$index"; return next }
    override fun setDirect(direct: Boolean) { calls += "direct:$direct" }
    override fun setCodeInput(enabled: Boolean) { calls += "code:$enabled" }
    override fun setCanDelete(canDelete: Boolean) { calls += "canDelete:$canDelete" }
    override fun setPrivate(enabled: Boolean) { calls += "private:$enabled" }
}
