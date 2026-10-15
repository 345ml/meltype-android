#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# エミュレータ (arm64) または実機で、Meltype を IME にして実際にキーを送り、入力欄の文字を確かめる。
# 終わったら (失敗しても) 元の IME に戻す。Meltype は入れたままにする。
# 端末が複数つながっているときは ANDROID_SERIAL で選ぶ。
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
ime="dev.horota.meltype/.ime.MeltypeImeService"
expected="今日はgoogleで検索"

"$root/gradlew" -p "$root" :app:installDebug

original="$(adb shell settings get secure default_input_method | tr -d '\r')"
restore() {
    if [[ -n "$original" && "$original" != "null" ]]; then
        adb shell ime set "$original" > /dev/null || true
        echo "IME を元に戻しました: $original"
    fi
}
trap restore EXIT

adb shell ime enable "$ime"
adb shell ime set "$ime"
adb shell am start -W -n dev.horota.meltype/.debug.PlaygroundActivity > /dev/null
sleep 2

field_text() {
    adb shell uiautomator dump /sdcard/meltype-ui.xml > /dev/null
    adb shell cat /sdcard/meltype-ui.xml | grep -o 'text="[^"]*"[^>]*content-desc="playground"' | head -1 | sed 's/^text="\([^"]*\)".*/\1/'
}

clear_field() {
    adb shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_A
    adb shell input keyevent KEYCODE_DEL
}

# Mozc のヘルパーは裏で起動し、準備ができるまでの変換はひらがなのまま (本家 MozcConverter の作り)。何回か試す。
for attempt in 1 2 3 4 5; do
    clear_field
    adb shell input text kyouhagoogledekensaku
    adb shell input keyevent KEYCODE_SPACE
    sleep 1
    adb shell input keyevent KEYCODE_ENTER
    sleep 1
    actual="$(field_text)"
    if [[ "$actual" == "$expected" ]]; then
        echo "OK: $expected ($attempt 回目)"
        exit 0
    fi
    echo "$attempt 回目: 「$actual」 (Mozc の準備を待ちます)"
    sleep 3
done
echo "NG: 入力欄の中身が「$expected」になりませんでした" >&2
exit 1
