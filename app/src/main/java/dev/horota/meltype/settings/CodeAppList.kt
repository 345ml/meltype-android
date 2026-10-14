// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

/** 設定画面の「コード入力モードにするアプリ」(1 行に 1 つのパッケージ名)。 */
object CodeAppList {
    private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    fun parse(text: String): Set<String> =
        text.lines().map { it.trim() }.filter { PACKAGE_NAME.matches(it) }.toSet()

    fun format(apps: Set<String>): String = apps.sorted().joinToString("\n")
}
