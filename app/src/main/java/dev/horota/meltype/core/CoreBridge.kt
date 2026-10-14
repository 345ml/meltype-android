// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.core

import android.system.ErrnoException
import android.system.Os
import android.util.Log
import java.io.File

sealed interface CoreOpenResult {
    data class Ready(val core: CoreBridge, val mozcAvailable: Boolean) : CoreOpenResult
    data class Failed(val reason: String) : CoreOpenResult
}

/** CoreApi の本物。JNI の失敗や例外はここで吸収して null にする (IME を落とさない)。 */
class CoreBridge private constructor(private val handle: Long) : CoreApi {

    companion object {
        private const val TAG = "Meltype"
        private var loaded: Boolean? = null

        /**
         * Core を読み込んで入力の本体を作る。dataDir は設定・学習の保存場所、helper は libmozc_helper.so のパス。
         * Mozc が使えなくても Ready (ひらがなのまま確定できる) を返し、mozcAvailable を false にする。
         */
        @Synchronized
        fun open(dataDir: File, helper: File): CoreOpenResult {
            if (loaded == null) loaded = loadOnce(dataDir)
            if (loaded != true) return CoreOpenResult.Failed("libMeltypeNative.so を読み込めませんでした")
            val mozc = NativeCore.initMozc(helper.path.toCString(), File(dataDir, "mozc").path.toCString()) == 1
            if (!mozc) Log.w(TAG, "Mozc を使えません (漢字変換はひらがなのままになります)")
            val handle = NativeCore.create()
            if (handle == 0L) return CoreOpenResult.Failed("Meltype を初期化できませんでした")
            return CoreOpenResult.Ready(CoreBridge(handle), mozc)
        }

        private fun loadOnce(dataDir: File): Boolean =
            try {
                dataDir.mkdirs()
                // 本家 AppPaths は最初に使われたときに MELTYPE_DATA_DIR を読む。Core を読み込む前に設定する。
                Os.setenv("MELTYPE_DATA_DIR", dataDir.path, true)
                System.loadLibrary("MeltypeNative")
                System.loadLibrary("meltype_jni")
                NativeCore.load()
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "ネイティブライブラリを読み込めません", e)
                false
            } catch (e: ErrnoException) {
                Log.e(TAG, "MELTYPE_DATA_DIR を設定できません", e)
                false
            }
    }

    override fun handleKey(vk: Int, ch: Int, modifiers: Int, before: String?, after: String?): SessionResult? =
        call { NativeCore.handleKey(handle, vk, ch, modifiers, before?.toCString(), after?.toCString()) }

    override fun roleKey(vk: Int): SessionResult? = call { NativeCore.roleKey(handle, vk) }

    override fun commit(): SessionResult? = call { NativeCore.commit(handle) }

    override fun selectCandidate(index: Int): SessionResult? = call { NativeCore.selectCandidate(handle, index) }

    override fun setDirect(direct: Boolean) = NativeCore.setDirect(handle, direct)

    override fun setCodeInput(enabled: Boolean) = NativeCore.setCodeInput(handle, enabled)

    override fun setCanDelete(canDelete: Boolean) = NativeCore.setCanDelete(handle, canDelete)

    fun version(): String? = NativeCore.version()?.toString(Charsets.UTF_8)

    private inline fun call(block: () -> ByteArray?): SessionResult? {
        val bytes = try {
            block()
        } catch (e: RuntimeException) {
            Log.e(TAG, "Core の呼び出しで例外", e)
            null
        } ?: return null
        return SessionResultParser.parse(bytes.toString(Charsets.UTF_8))
    }
}

/** UTF-8 の NUL 終端バイト列 (C の文字列) にする。 */
private fun String.toCString(): ByteArray = (this + "\u0000").toByteArray(Charsets.UTF_8)
