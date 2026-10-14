// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

/** 本家の userdict.txt (1 行に「読み[Tab]単語」。! と # で始まる行・空行は飛ばす) の検証。 */
object UserDictionaryText {
    /** 形式が違う行の番号 (1 始まり)。 */
    fun invalidLines(text: String): List<Int> =
        text.lines().mapIndexedNotNull { index, line ->
            if (line.isBlank() || line.startsWith("!") || line.startsWith("#")) return@mapIndexedNotNull null
            val parts = line.split('\t')
            val valid = parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()
            if (valid) null else index + 1
        }
}
