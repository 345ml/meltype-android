// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.core

/** meltype_jni.c の関数。文字列は UTF-8 の NUL 終端 ByteArray でやり取りする。CoreBridge 以外から使わない。 */
internal object NativeCore {
    external fun load(): Boolean
    external fun initMozc(helper: ByteArray, profile: ByteArray?): Int
    external fun create(): Long
    external fun destroy(handle: Long)
    external fun handleKey(handle: Long, vk: Int, ch: Int, modifiers: Int, before: ByteArray?, after: ByteArray?): ByteArray?
    external fun roleKey(handle: Long, vk: Int): ByteArray?
    external fun commit(handle: Long): ByteArray?
    external fun selectCandidate(handle: Long, index: Int): ByteArray?
    external fun setDirect(handle: Long, on: Boolean)
    external fun setCodeInput(handle: Long, on: Boolean)
    external fun setCanDelete(handle: Long, on: Boolean)
    external fun setPrivate(handle: Long, on: Boolean)
    external fun forgetLastCommit(handle: Long)
    external fun version(): ByteArray?
}
