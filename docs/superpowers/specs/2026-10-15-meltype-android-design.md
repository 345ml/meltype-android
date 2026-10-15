# Meltype for Android 設計書 (v0.1)

- 日付: 2026-10-15
- 状態: 承認待ち (spec review)
- 本家: https://github.com/yksr-melt/Meltype (調査時点の commit `45e14f8`, Core 1.2.1)

## 1. 目的と前提

### 目的
Windows 向け IME「Meltype」(ローマ字入力から英語 / 日本語を自動で判定し、半角 / 全角の切り替えをなくす) を Android で使えるようにする。

### ユーザーが決めたこと
- 本家とは別のリポジトリで、OSS (GPL-3.0-or-later) として公開する。
- かな漢字変換の品質は本家と同じにする → 変換エンジンは Mozc。
- 判定ロジック (本家の C# `Meltype.Core`) は書き直さず、NativeAOT でビルドしてそのまま使う。
- 一番の利用端末は **Zinwa Q25** (BlackBerry Classic の筐体に QWERTY キーボードとトラックパッドが内蔵された Android 機)。
- v0.1 は **物理キーボード全般** (Q25 内蔵と、Bluetooth / USB の外付け) に絞る。画面キーボードは v0.2。

### 前提 (確認が必要なもの)
- Q25 は MediaTek Helio G99 (arm64)、Android 13 または 14、画面は 720×720。
- 本家の `src/Meltype.Mac.Native` (C ABI。Mac 版と Linux 版が使っている) は、Mozc モード (`meltype_init_mozc`) を持っている。このプロジェクトは本家のコードを変えずに、これを Android arm64 向けにビルドして使う。
- データの保存場所は、本家の環境変数 `MELTYPE_DATA_DIR` で切り替えられる。

### 成功の基準 (v0.1)
1. Q25 の内蔵キーボードで `kyouhagoogledekensaku` と打つと「今日はgoogleで検索」になる。
2. 外付けの JIS / US キーボードでも同じように動く。
3. トラックパッド (または矢印キー) で候補を選べる。
4. パスワード欄では直接入力になり、学習もしない。
5. 入力した文字を外に送らない (本家と同じ)。

## 2. 全体の構成

```
┌──────────────────────────── Android アプリ (APK) ────────────────────────────┐
│  [Kotlin] MeltypeImeService (InputMethodService)                             │
│     ├─ HardwareKeyHandler   物理キーボードの KeyEvent                        │
│     ├─ KeyNormalizer        Android keycode → Windows VK + 文字 + 修飾キー   │
│     ├─ KeyBindings          切り替えキーなど Meltype 独自の割り当て          │
│     ├─ CandidateBar         候補バー (1 行)                                  │
│     └─ ResultApplier        SessionResult(JSON) → InputConnection            │
│                │ JNI                                                         │
│  [C] libmeltype_jni.so      JNI ↔ C ABI の薄い橋渡し                         │
│                │                                                             │
│  [C# NativeAOT] libMeltypeNative.so                                          │
│     本家 src/Meltype.Mac.Native + Meltype.Core を linux-bionic-arm64 でビルド│
│                │ 標準入出力 (本家 MozcConverter.cs)                          │
│  [C++ NDK] libmozc_helper.so (実体は実行ファイル)                            │
│     本家 native/mozc/meltype_mozc_helper.cc を Android arm64 でビルド        │
└──────────────────────────────────────────────────────────────────────────────┘
```

- 本家は git submodule (`upstream/`) として取り込み、commit を固定する。本家のファイルは変えない。
- Mozc のヘルパーは別のプロセスとして動かす。Android では `nativeLibraryDir` にある実行ファイルなら起動できるので、`libmozc_helper.so` という名前で APK に入れ、`meltype_init_mozc` にそのパスを渡す。
- データの保存場所は `MELTYPE_DATA_DIR=<filesDir>/Meltype`。JNI で Core を読み込む前に `setenv` で設定する。Mozc の学習データは `<filesDir>/Meltype/mozc`。
- 対象: Android 10 (API 29) 以降、arm64-v8a のみ。

### リポジトリの構成
```
meltype-android/
  upstream/            git submodule: yksr-melt/Meltype (固定)
  core-native/         NativeAOT ビルドのスクリプト (upstream の Meltype.Mac.Native を参照)
  mozc/                Mozc ヘルパーの Android ビルドスクリプト (Bazel + NDK)
  app/                 Android アプリ (Kotlin, Gradle)
    src/main/cpp/      JNI の橋渡し (C)
  tools/keylogger/     spike 用: キーコードを記録するアプリ (捨てる前提)
  docs/
  LICENSE              GPL-3.0-or-later
  THIRD-PARTY-NOTICES.md
```

## 3. 部品

| 部品 | 役割 | 使うもの / 依存先 |
|---|---|---|
| `MeltypeImeService` | IME の入口。入力欄の開始 / 終了、キー入力を受けて各部品に回す | Android `InputMethodService` |
| `HardwareKeyHandler` | `onKeyDown` / `onKeyUp` を受け、KeyBindings → KeyNormalizer → Core の順に処理 | KeyBindings, KeyNormalizer, CoreBridge |
| `KeyNormalizer` | Android の `KeyEvent` を (Windows VK, Unicode 文字, 修飾キー) に変換。`KeyCharacterMap` で Alt+文字 などの実際の文字を得る | なし (純粋な関数。JVM でテストする) |
| `KeyBindings` | Meltype 独自のキー割り当て (下の表)。該当すれば Core を呼ばずに処理する | 設定 |
| `CoreBridge` | JNI で C ABI を呼ぶ。ハンドルの寿命を管理し、返ってきた文字列を `meltype_free` で解放する | libmeltype_jni.so |
| `ResultApplier` | `SessionResult` の JSON (`Consumed`, `Commits`, `View`) を `InputConnection` の操作に変える | `InputConnection` (テストでは偽物に差し替える) |
| `CandidateBar` | 未確定中の候補を 1 行で表示。タップ、トラックパッド、矢印キーで選ぶ | `View` (JSON の候補) |
| `EditorPolicy` | 入力欄の種類とアプリに合わせて、直接入力 / コード入力を決める | `EditorInfo`, 設定 |
| `SettingsActivity` | 最初の案内と最小限の設定 | 本家の `config.json` |

C ABI の呼び方:
- 起動時: `setenv(MELTYPE_DATA_DIR)` → `meltype_init_mozc(helperPath, NULL)`
- 入力欄ごと: `meltype_create` / `meltype_destroy`
- キー: `meltype_handle_key(handle, vk, ch, modifiers, before, after)`。`before` / `after` はカーソルの前後 64 文字ずつ (`getTextBeforeCursor` / `getTextAfterCursor`)
- 変換 / 無変換 / かなキー: `meltype_role_key`
- フォーカスが外れたとき: `meltype_commit`
- 候補を選んだとき: `meltype_select_candidate`
- 入力欄の種類: `meltype_set_direct`, `meltype_set_code_input`, `meltype_set_can_delete` (Android の InputConnection は確定済みの文字を消せるので 1)

## 4. キー入力の流れ

```
KeyEvent
  → KeyBindings に当てはまる? ── yes → 切り替えなどを処理して終わり
  → KeyNormalizer で (vk, ch, modifiers) に変換
  → CoreBridge.handleKey(...) → JSON
  → ResultApplier
       Commits         → deleteSurroundingText / commitText
       View            → setComposingText + CandidateBar の更新
       Consumed=false  → super.onKeyDown でアプリに渡す
```

`ResultApplier` は `beginBatchEdit` / `endBatchEdit` で 1 回の操作にまとめる。

### キーの割り当て
Q25 (BlackBerry 配列) と外付けキーボードの両方の割り当てを同時に有効にする。ぶつかるものはない。

| 動作 | 外付け (JIS / US) | Q25 (BlackBerry 配列) |
|---|---|---|
| 英 / 日の切り替え (`meltype_set_direct`) | 半角/全角, Ctrl+Space | Shift+Space |
| カタカナに変換 | F7 (Core が処理) | 候補バーの「カナ」ボタン |
| 英数に変換 | F10 (Core が処理) | 候補バーの「英数」ボタン |
| 変換 / 無変換 / かな | `meltype_role_key` | なし |
| 候補を選ぶ | ↑↓←→ / Space | トラックパッド (DPAD) / Space |
| 一時停止 (Meltype を通さない) | Ctrl+半角/全角 | なし (設定画面から) |

- Alt+文字 で数字や記号を打つ Q25 の動きは、`KeyCharacterMap` が返す文字をそのまま Core に渡して保つ。BlackBerry 配列では Alt+E などが数字になるので、Alt+文字 は Meltype の割り当てに使わない (`3ji` → 3時 のような入力を壊さないため)。
- Q25 の Sym は右 Alt (`KEYCODE_ALT_RIGHT`) として届き、Key Mapper などのアプリが Sym+文字 を使っていることがあるため、Meltype は割り当てない。F7 / F10 は候補バーのボタンで代わりにする (入力中だけ出す)。
- 実測したキーコードは `docs/q25-keys.md`。

### 入力欄ごとの動き (`EditorPolicy`)
- パスワード欄 (`TYPE_TEXT_VARIATION_*PASSWORD`): いつも直接入力。学習しない (Core を通さずにアプリに渡す)。
- URL / メール / 数字 / 電話番号の欄: 直接入力から始める。
- コード入力のアプリ: `EditorInfo.packageName` が一覧にあれば `meltype_set_code_input(1)`。最初の一覧は Termux (`com.termux`) と、主なコードエディタ。設定画面で追加できる。

## 5. 画面

- **候補バー**: 物理キーボードを使っている間は画面キーボードを出さず (`onEvaluateInputViewShown` = false)、候補バーだけを出す。720×720 の画面でも邪魔にならないよう 1 行で細くする。英 / 日の状態を左端に小さく出す。
- **設定画面 (v0.1 の最小限)**:
  - 最初の案内: IME を有効にする → 選ぶ → (JIS キーボードなら) Android の「物理キーボード」で配列を日本語にする
  - 切り替えキーの割り当て
  - コード入力モードにするアプリの一覧
  - 学習データを消す
  - ユーザー辞書の編集 (本家の `userdict.txt` を使う)
  - 細かい設定は本家と同じ `config.json` に保存する

## 6. エラー時の動き

| 起きたこと | 動き |
|---|---|
| Mozc のヘルパーが起動しない / 落ちる | 入力は止めない。ひらがなのまま確定できるようにし、本家の `MozcConverter` の仕組みで起動し直す |
| Core の初期化に失敗 (`meltype_create` が NULL など) | IME はキーをそのままアプリに渡すだけにする。候補バーに「⚠ Meltype を初期化できませんでした」と出す |
| Core の中で例外 | 本家の `Run()` が捕まえる。Kotlin 側でも JNI の呼び出しを try で囲み、失敗したらキーをアプリに渡す |
| ログ | 本家と同じ `meltype.log` / `crash.log` を `<filesDir>/Meltype` に出す。外には送らない |

## 7. テスト

- **Core**: 本家のテストに任せる。このリポジトリでは書かない。
- **JVM の単体テスト (Kotlin)**:
  - `KeyNormalizer`: Android のキーコード → Windows VK。Alt+文字、Shift、トラックパッドの DPAD
  - `KeyBindings`: 外付けと Q25 の割り当て。ぶつからないこと
  - `ResultApplier`: JSON → InputConnection の操作 (偽物の InputConnection で確かめる)
  - `EditorPolicy`: 入力欄の種類 / アプリごとの判定
- **結合テスト (エミュレータ arm64 / 実機)**:
  - 本物の `libMeltypeNative.so` を読み込み、`kyouhagoogledekensaku` → 「今日はgoogleで検索」
  - Mozc のヘルパーを起動して変換できる。ヘルパーを kill しても入力が止まらない
- **Q25 の実機チェックリスト** (`docs/q25-checklist.md`): Alt / Sym / トラックパッド、切り替えキー、パスワード欄、Termux、外付けキーボードとの併用

## 8. ビルドと配布

- GitHub Actions:
  1. `core-native`: .NET 10 の NativeAOT で `upstream/src/Meltype.Mac.Native` を `linux-bionic-arm64` 向けに `libMeltypeNative.so` にする
  2. `mozc`: Bazel + Android NDK で `meltype_mozc_helper` を arm64 向けに作る (時間がかかるのでキャッシュする)
  3. `app`: Gradle で、1 と 2 の成果物を `jniLibs/arm64-v8a` に入れて APK を作る
- 配布: GitHub Releases に署名済みの APK を置く。F-Droid / Play ストアは v0.1 の対象外。
- ライセンス: GPL-3.0-or-later。Mozc (BSD-3-Clause) と、本家が同梱しているもののライセンスを `THIRD-PARTY-NOTICES.md` に書く。
- 本家の更新: submodule の commit を手動で上げ、結合テストと Q25 のチェックリストを通してからリリースする。

## 9. 最初にやる spike (動作確認)

実装に入る前に、次の 3 つを確かめる。作ったものは捨てる前提。

1. **NativeAOT**: `Meltype.Mac.Native` を `linux-bionic-arm64` でビルドし、Android の実機またはエミュレータで `meltype_create` → `meltype_handle_key` が動くか。
2. **Mozc ヘルパー**: `meltype_mozc_helper.cc` を NDK でビルドし、アプリの `nativeLibraryDir` から起動して変換できるか。
3. **Q25 のキーコード**: `tools/keylogger` を Q25 に入れて、全キー (Alt, Sym, Shift, トラックパッド, Alt+文字) の keycode / metaState / `KeyCharacterMap` の文字を記録する。

1 か 2 がダメなら、そこで止めて相談する (別の案: Core を Kotlin に移植する / Mozc を同じプロセスのライブラリとして組み込む)。3 の結果で §4 の Q25 列を確定する。

## 10. v0.1 の範囲

- **入るもの**: 物理キーボード (Q25 と外付け)、候補バー、トラックパッド / 矢印キーで候補を選ぶ、英 / 日の自動判定、Mozc での変換、学習、最小限の設定、パスワード欄 / コード入力の扱い
- **入らないもの**: 画面キーボード (v0.2)、x86_64、ストアでの配布、本家への変更
