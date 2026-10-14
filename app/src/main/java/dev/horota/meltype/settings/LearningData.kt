// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import java.io.File

/**
 * 学習データを消す。動いている Mozc のヘルパーは終了するときに学習を書き戻すので、
 * すぐには消さず印を置き、次に Core を開く前 (CoreBridge.open) に消す。
 * 消すのは本家 AppPaths の学習のファイルと Mozc の学習だけ。設定・ユーザー辞書・スニペットは残す。
 */
object LearningData {
    private const val MARKER = "clear-learning.pending"
    private val FILES = listOf("model.json", "conversions.json", "translations.json", "languages.json", "phrases.txt")
    private const val MOZC_DIR = "mozc"

    fun requestClear(dataDir: File) {
        dataDir.mkdirs()
        File(dataDir, MARKER).writeText("")
    }

    fun applyPendingClear(dataDir: File): Boolean {
        val marker = File(dataDir, MARKER)
        if (!marker.exists()) return false
        FILES.forEach { File(dataDir, it).delete() }
        File(dataDir, MOZC_DIR).deleteRecursively()
        marker.delete()
        return true
    }
}
