# Meltype for Android

[Meltype](https://github.com/yksr-melt/Meltype) (ローマ字入力から英語 / 日本語を自動で判定する IME) の Android 版です。
本家の判定ロジック (C#) と Mozc の変換エンジンを Android で動かします。

- 対象: Android 10 以降 (arm64)。物理キーボード (Zinwa Q25 の内蔵キーボード、Bluetooth / USB キーボード) で使います。
- 入力した文字は端末の外に送りません (ネットワークの権限を持ちません)。
- ライセンス: GPL-3.0-or-later。本家のコードは `upstream/` (git submodule) にあり、変更していません。

## 使い方

1. GitHub Releases から APK を入れる。
2. ランチャーの「Meltype」を開き、「はじめに」の 1〜3 を順に行う。
3. ローマ字で打つと、英語はそのまま、日本語はかな漢字に変換されます。Space で変換、Enter で確定。

| 操作 | 外付けキーボード | Zinwa Q25 |
|---|---|---|
| 英 / 日の切り替え | 半角/全角, Ctrl+Space | Shift+Space |
| 候補を選ぶ | ↑↓ / Space | トラックパッド / Space |
| 一時停止 | Ctrl+半角/全角 | (設定画面から) |

JIS キーボードは、Android の設定の「物理キーボード」で配列を「日本語」にしてください。

## ビルド

必要なもの: Docker、JDK 17、Android SDK (`ANDROID_HOME`)、NDK 27.1.12297006。

    git submodule update --init --recursive
    docker build --platform linux/amd64 -t meltype-build docker
    docker/run.sh core-native/build.sh
    docker/run.sh mozc/build.sh
    ./gradlew :app:assembleDebug
