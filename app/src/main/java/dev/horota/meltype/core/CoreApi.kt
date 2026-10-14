// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.core

/**
 * Meltype の入力の本体 (本家 MeltypeSession) の呼び出し。本物は CoreBridge、テストでは偽物を使う。
 * 結果が null のときは「Core が処理しなかった (キーはアプリに渡す)」。
 */
interface CoreApi {
    /** vk は Windows の仮想キーコード、ch は Unicode スカラー (無ければ 0)、modifiers は Shift=1 Ctrl=2 Alt=4 Meta=8。 */
    fun handleKey(vk: Int, ch: Int, modifiers: Int, before: String?, after: String?): SessionResult?
    /** 変換 (0x1C)・無変換 (0x1D)・かな (0x15)。入力中でなければ null。 */
    fun roleKey(vk: Int): SessionResult?
    fun commit(): SessionResult?
    fun selectCandidate(index: Int): SessionResult?
    fun setDirect(direct: Boolean)
    fun setCodeInput(enabled: Boolean)
    fun setCanDelete(canDelete: Boolean)
    /** 入力を覚えないように求める入力欄 (シークレットタブなど)。入力は動かして、学習だけ止める。 */
    fun setPrivate(enabled: Boolean)
}
