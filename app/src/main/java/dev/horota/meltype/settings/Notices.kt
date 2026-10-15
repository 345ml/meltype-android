// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

/** 設定画面の「ライセンス」に出す、APK に入れたライセンス表記 (assets/notices/)。 */
object Notices {
    /** 出す順。app/build.gradle.kts の copyNotices で assets/notices/ に入れるファイル。 */
    val FILES = listOf(
        "THIRD-PARTY-NOTICES.md",
        "LICENSE",
        "UPSTREAM-THIRD-PARTY-NOTICES.md",
        "MOZC-LICENSE.txt",
        "MOZC-CREDITS.html",
        "DOTNET-LICENSE.txt",
        "DOTNET-THIRD-PARTY-NOTICES.txt",
    )

    /** read はファイル名 → 中身 (無ければ null)。無いファイルは飛ばす。 */
    fun compose(read: (String) -> String?): String = buildString {
        for (name in FILES) {
            val text = read(name) ?: continue
            append("== ").append(name).append(" ==\n").append(text.trimEnd()).append('\n')
        }
    }
}
