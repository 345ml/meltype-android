<img width="1200" height="480" alt="iXPsG" src="https://github.com/user-attachments/assets/a5a864a1-60e6-4cde-a528-beccdeb8e61f" />

# Meltype for Android

[Meltype](https://github.com/yksr-melt/Meltype) を Android の物理キーボードで使えるようにした、**非公式の移植版**です。

> **雪解けのように、半角/全角の壁を溶かす日本語入力。**
> ローマ字で打つだけ。日本語か英語かは Meltype が見分けて、その場で切り替えます！
>
> — [Meltype](https://github.com/yksr-melt/Meltype) の README より

```
kyouhagoogledekensaku    →  今日はgoogleで検索
ashitanomeetingwotsuika  →  明日のmeetingを追加
```

## 本家 Meltype について

Meltype は **雪代 / Yukishiro さん ([@yksr-melt](https://github.com/yksr-melt))** が作っている Windows 向けの日本語入力です (Mac / Linux 版もあります)。
ローマ字の打鍵から英語と日本語を自動で見分ける、という発想と、その判定ロジック、判定に使う辞書づくりは本家の仕事です (辞書の一部は JMdict などの公開データをもとにしています。くわしくは本家の [THIRD-PARTY-NOTICES.md](https://github.com/yksr-melt/Meltype/blob/main/THIRD-PARTY-NOTICES.md))。この Android 版は、その仕組みを Android で動かすための「つなぎ」を書いただけです。

- 本家: https://github.com/yksr-melt/Meltype
- 本家の使い方: https://github.com/yksr-melt/Meltype/blob/main/docs/USAGE.md
- 本家を応援する: https://github.com/sponsors/yksr-melt

このリポジトリは本家の作者とは関係なく、本家から承認を受けたものでもありません。
**Android 版の不具合は本家ではなく、このリポジトリの Issue に報告してください。**

本家のコードは `upstream/` に git submodule として取り込み、**一切変更せずに**使っています。Android で必要な関数 (学習を止める `meltype_set_private` など) は、`core-native/` に別のファイルとして足しています。

## なぜ作ったか

作者 ([@_horotter](https://x.com/_horotter)) は、BlackBerry のような物理 QWERTY キーボードの付いた Android 端末 **Zinwa Q25** を毎日使っています。
Q25 の小さなキーボードで 半角/全角 を切り替えながら日本語と英語を打つのはつらく、本家 Meltype の「切り替えなくていい」入力を Q25 でも使いたくて作りました。
そのため、Q25 での使い勝手を最優先にしています (キーの実測: [docs/q25-keys.md](docs/q25-keys.md))。

## 仕組み

| 部分 | 中身 |
|---|---|
| 英語 / 日本語の判定 | 本家の C# の Core (`upstream/src/Meltype.Core`) を .NET NativeAOT で Android 向けにビルド (`libMeltypeNative.so`) |
| かな漢字変換 | 本家が Mac / Linux 版で使っている [Mozc](https://github.com/google/mozc) を Android 向けにビルド (`libmozc_helper.so`, `libmozc_data.so`) |
| IME | Kotlin の `InputMethodService`。キーを Core に渡し、結果を入力欄と候補バーに反映 |

- AI モデルは使っていません。本家の判定ルールと辞書、Mozc の辞書と統計だけで動きます。
- すべて端末の中で動きます。**ネットワークの権限を持たない**ので、打った文字が外に出ることはありません。
- パスワード欄では Meltype を通しません。「学習しない」指定の入力欄 (シークレットモードなど) では、変換はしますが覚えません (本家 Windows 版と同じ考え方)。

## 使い方

対象: Android 10 以降 (arm64)。今の版は**物理キーボード専用**です (Zinwa Q25 の内蔵キーボード、Bluetooth / USB キーボード)。画面キーボードはありません。

1. [Releases](https://github.com/345ml/meltype-android/releases) から APK を入れる (下の「ビルド」で自分で作ることもできます)。
2. ランチャーの「Meltype」を開き、「はじめに」の 1〜3 を順に行う (有効にする → 選ぶ)。
3. ローマ字で打つと、英語はそのまま、日本語はかな漢字になります。Space で変換、Enter で確定。

画面の下に 1 行の候補バーが出ます。左端の「あ」が日本語 (自動判定あり)、「A」が直接入力 (何も変換しない) です。ふだんは「あ」のまま英語も日本語も打ってください。

| 操作 | 外付けキーボード | Zinwa Q25 |
|---|---|---|
| あ / A の切り替え | 半角/全角, Ctrl+Space | Shift+Space (設定でオフにできる) |
| 候補を選ぶ | ↑↓ / Space | トラックパッド / Space |
| 確定 | Enter | Enter / トラックパッドを押す |
| カタカナ / 英字にする | F7 / F10 | 候補バーの「カナ」「英数」 |
| 一時停止 | Ctrl+半角/全角 | (設定画面から) |
| 数字・記号 | | Alt+文字 (Q25 の刻印どおり) |

- JIS キーボードは、Android の設定の「物理キーボード」で配列を「日本語」にしてください。
- 本家の Windows 版にある「英数状態でもローマ字を検知」など、Windows 側で作られている機能はありません。
- 日本語と英語以外の言語には対応していません。他の言語は Gboard などに切り替えて使ってください。

## ビルド

必要なもの: Docker、JDK 17、Android SDK (`ANDROID_HOME`)、NDK 27.1.12297006。

    git submodule update --init --recursive
    docker build --platform linux/amd64 -t meltype-build docker
    docker/run.sh core-native/build.sh
    docker/run.sh mozc/build.sh
    ./gradlew :app:assembleDebug

テスト: `./gradlew :app:testDebugUnitTest` (JVM)、`tools/e2e.sh` (つないだ端末で実際に打って確かめる)。Q25 での確認項目は [docs/q25-checklist.md](docs/q25-checklist.md)。

## ライセンス

GPL-3.0-or-later (本家と同じ)。[LICENSE](LICENSE)

```
Meltype
Copyright (C) 2026 雪代 / Yukishiro (@yksr_melt / @yksr-melt)

Meltype for Android
Copyright (C) 2026 Keisuke Horota (@345ml / @_horotter)
```

同梱しているもの (本家 Meltype・Mozc・.NET ランタイム) とそのライセンスは [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) にまとめています。APK の設定画面の「ライセンス」でも読めます。

素晴らしい入力方式を GPL で公開してくださっている雪代さんに感謝します。
