#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# エミュレータ (arm64) または実機で、Meltype を IME にして実際にキーを送り、入力欄の文字を確かめる。
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
ime="dev.horota.meltype/.ime.MeltypeImeService"

"$root/gradlew" -p "$root" :app:installDebug
adb shell ime enable "$ime"
adb shell ime set "$ime"
adb shell am start -W -n dev.horota.meltype/.debug.PlaygroundActivity
sleep 2

adb shell input text kyouhagoogledekensaku
adb shell input keyevent KEYCODE_SPACE
sleep 1
adb shell input keyevent KEYCODE_ENTER
sleep 1

adb shell uiautomator dump /sdcard/meltype-ui.xml > /dev/null
expected="今日はgoogleで検索"
if adb shell cat /sdcard/meltype-ui.xml | grep -q "text=\"$expected\""; then
    echo "OK: $expected"
else
    echo "NG: 入力欄の中身が「$expected」ではありません" >&2
    adb shell cat /sdcard/meltype-ui.xml | grep -o 'text="[^"]*"' >&2
    exit 1
fi
