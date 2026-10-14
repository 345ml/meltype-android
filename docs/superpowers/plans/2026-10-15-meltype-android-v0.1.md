# Meltype for Android v0.1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 本家 Meltype の判定ロジック (C# `Meltype.Core`) と Mozc を Android arm64 で動かし、物理キーボード (Zinwa Q25 内蔵 / 外付け) で使える IME を作る。

**Architecture:** 本家を git submodule で取り込み、`Meltype.Mac.Native` (C ABI) を NativeAOT で `libMeltypeNative.so` に、`meltype_mozc_helper` を NDK で `libmozc_helper.so` (実行ファイル) にビルドする。Kotlin の `InputMethodService` が JNI (dlopen) で C ABI を呼び、結果の JSON を `InputConnection` に反映する。ロジックは Android に依存しない Kotlin のクラス (`KeyNormalizer` / `KeyBindings` / `EditorPolicy` / `ResultApplier` / `ImeController`) に置いて JVM でテストする。

**Tech Stack:** Kotlin 2.1.0, AGP 8.7.3, Gradle 8.11.1, kotlinx-serialization-json 1.7.3, JUnit 4.13.2, CMake (JNI, C), .NET 10 SDK (NativeAOT, linux-bionic-arm64), Bazel (bazelisk) + Android NDK r27b (27.1.12297006), Docker (linux/amd64 のビルド用イメージ)

**Spec:** `docs/superpowers/specs/2026-10-15-meltype-android-design.md`

## Global Constraints

- ライセンス: GPL-3.0-or-later。新しいソースファイルの先頭に `// SPDX-License-Identifier: GPL-3.0-or-later` を書く。
- 本家 (`upstream/`) のファイルは変更しない。本家の commit は `45e14f8` に固定する。
- 対象: Android 10 (minSdk 29) 以降、ABI は `arm64-v8a` のみ。compileSdk / targetSdk 35。
- パッケージ名 / applicationId: `dev.horota.meltype` (公開前に変えるならここだけ直す)。
- データの保存場所: `<filesDir>/Meltype` (環境変数 `MELTYPE_DATA_DIR`)。Mozc の学習: `<filesDir>/Meltype/mozc`。
- 入力した文字を外に送らない。ネットワークの権限 (`INTERNET`) を manifest に書かない。
- Kotlin で `Any` / `!!` を使わない (型を明示する)。
- ネイティブのビルド (.NET NativeAOT, Bazel) は Docker の linux/amd64 コンテナで行う (NativeAOT は OS をまたいだビルドができず、NDK の Linux 版は x86_64 のみのため)。Gradle は macOS のホストで動かす。
- C ABI に渡す文字列は UTF-8 の NUL 終端バイト列。JNI の `NewStringUTF` / `GetStringUTFChars` は使わない (絵文字が壊れる)。
- コミットメッセージの末尾に `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` を付ける。

## Review Focus

1. **絵文字 (サロゲートペア) を含む入力欄**: カーソルの前後に 😀 があっても落ちず、そのまま扱える → Task 4 の `emojiAroundCaretDoesNotBreak`。
2. **入力中に別の入力欄・アプリへ移る**: 未確定の文字が消えずに確定される → Task 9 の `finishInputCommitsPending`。
3. **Q25 の Alt+文字 で数字を打つ (`3ji` など)**: Alt の層の文字は Alt 修飾なしの文字として Core に渡り、ショートカット扱いにならない → Task 5 の `altLayerCharacterDropsAltModifier`。
4. **確定し直しのとき、アプリ側で文字が変わっていた**: `expect` と違えば消さずに書き足すだけにする (他人の文字を消さない) → Task 6 の `deleteSkippedWhenExpectMismatches`。
5. **Core が使ったキーの KeyUp**: 対応する KeyUp もアプリに渡さない (Backspace の長押しなどで、アプリが KeyUp だけ受けて誤動作しない) → Task 9 の `keyUpOfConsumedKeyIsConsumed`。

---

## ファイル構成

```
meltype-android/
  .gitignore
  .gitmodules
  LICENSE                                  GPL-3.0 の全文
  README.md
  THIRD-PARTY-NOTICES.md                   (Task 12)
  upstream/                                submodule: yksr-melt/Meltype @45e14f8
  docker/Dockerfile                        .NET 10 SDK + NDK r27b + bazelisk
  docker/run.sh                            コンテナの中でコマンドを実行する
  core-native/build.sh                     libMeltypeNative.so を作る
  mozc/build.sh                            libmozc_helper.so を作る
  build/jniLibs/arm64-v8a/                 ↑2 つの出力先 (git に入れない)
  settings.gradle.kts / build.gradle.kts / gradle.properties / gradlew*
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/cpp/CMakeLists.txt
  app/src/main/cpp/meltype_jni.c           JNI ↔ C ABI (dlopen)
  app/src/main/java/dev/horota/meltype/
    core/NativeCore.kt                     external fun 宣言
    core/CoreApi.kt                        Core の呼び出しの interface (テストで偽物に差し替える)
    core/CoreBridge.kt                     CoreApi の本物。読み込み・例外の吸収
    core/SessionResult.kt                  結果の JSON のモデルと parse
    input/KeyNormalizer.kt                 Android のキー → Windows VK
    input/KeyBindings.kt                   Meltype 独自のキー割り当て
    input/EditorPolicy.kt                  入力欄の種類ごとの動き
    ime/EditorSink.kt                      入力欄への書き込みの interface
    ime/ResultApplier.kt                   SessionResult → EditorSink
    ime/ImeController.kt                   キー入力の流れの全体 (Android 非依存)
    ime/InputConnectionSink.kt             EditorSink の本物
    ime/CandidateBarView.kt                候補バー
    ime/MeltypeImeService.kt               InputMethodService
    settings/MeltypePrefs.kt               SharedPreferences
    settings/CodeAppList.kt                コード入力アプリ一覧の文字列 ⇔ Set
    settings/LearningData.kt               学習データを消す
    settings/UserDictionaryText.kt         userdict.txt の検証
    settings/SettingsActivity.kt
    settings/UserDictionaryActivity.kt
  app/src/main/res/xml/method.xml
  app/src/main/res/values/strings.xml
  app/src/debug/AndroidManifest.xml        PlaygroundActivity (結合テスト用)
  app/src/debug/java/dev/horota/meltype/debug/PlaygroundActivity.kt
  app/src/test/java/dev/horota/meltype/... JVM の単体テスト
  app/src/androidTest/java/dev/horota/meltype/core/CoreSmokeTest.kt
  tools/keylogger/                         spike 用のキー記録アプリ (Gradle の別モジュール)
  tools/e2e.sh                             エミュレータ / 実機での結合テスト
  docs/q25-keys.md                         Q25 の実測キーコード (Task 3)
  docs/q25-checklist.md                    Q25 の実機チェックリスト (Task 12)
  .github/workflows/build.yml              (Task 12)
```

## 進め方とゲート

- **Task 1〜4 は spike を兼ねる。** Task 2 (NativeAOT) か Task 3 の Mozc 部分 (Task 2 の後半) か Task 4 (実機で変換) が通らなければ、そこで止めてユーザーに相談する (別案: Core を Kotlin に移植 / Mozc を同じプロセスに組み込む)。
- **Task 3 (Q25 のキー記録) はユーザーが実機で操作する。** 結果を `docs/q25-keys.md` に書き、Task 7 (KeyBindings) の Q25 の割り当てを確定してから Task 7 に入る。

---

### Task 1: リポジトリの土台と、ネイティブのビルド環境 (Docker)

**Files:**
- Create: `.gitignore`, `LICENSE`, `README.md`, `docker/Dockerfile`, `docker/run.sh`
- Create (submodule): `upstream/`

**Interfaces:**
- Produces: `docker/run.sh <command...>` — リポジトリを `/work` にマウントした linux/amd64 コンテナでコマンドを実行する。イメージ名 `meltype-build`。環境変数 `ANDROID_NDK_HOME=/opt/android-ndk-r27b`。

- [ ] **Step 1: submodule を追加して commit を固定する**

```bash
cd /Volumes/OWCExpress1M2/work/meltype-android
git submodule add https://github.com/yksr-melt/Meltype.git upstream
git -C upstream checkout 45e14f8
git -C upstream log -1 --format=%h
```
Expected: `45e14f8`

- [ ] **Step 2: `.gitignore` を書く**

```gitignore
# Gradle / Android
.gradle/
build/
app/build/
tools/keylogger/build/
local.properties
*.iml
.idea/
.cxx/
# 署名の鍵
*.jks
*.keystore
```

- [ ] **Step 3: `LICENSE` に GPL-3.0 の全文を入れる**

```bash
cp upstream/LICENSE LICENSE
head -3 LICENSE
```
Expected: `GNU GENERAL PUBLIC LICENSE` / `Version 3, 29 June 2007` が見える。

- [ ] **Step 4: `README.md` を書く**

```markdown
# Meltype for Android

[Meltype](https://github.com/yksr-melt/Meltype) (ローマ字入力から英語 / 日本語を自動で判定する IME) の Android 版です。
本家の判定ロジック (C#) と Mozc の変換エンジンを Android で動かします。

- 対象: Android 10 以降 (arm64)。物理キーボード (Zinwa Q25 の内蔵キーボード、Bluetooth / USB キーボード) で使います。
- 入力した文字は端末の外に送りません (ネットワークの権限を持ちません)。
- ライセンス: GPL-3.0-or-later。本家のコードは `upstream/` (git submodule) にあり、変更していません。

## ビルド

必要なもの: Docker、JDK 17、Android SDK (`ANDROID_HOME`)、NDK 27.1.12297006。

    git submodule update --init --recursive
    docker build --platform linux/amd64 -t meltype-build docker
    docker/run.sh core-native/build.sh
    docker/run.sh mozc/build.sh
    ./gradlew :app:assembleDebug
```

- [ ] **Step 5: `docker/Dockerfile` を書く**

```dockerfile
# SPDX-License-Identifier: GPL-3.0-or-later
# ネイティブのビルド用 (NativeAOT の Core と Mozc のヘルパー)。NDK の Linux 版は x86_64 だけなので linux/amd64 で動かす。
FROM --platform=linux/amd64 mcr.microsoft.com/dotnet/sdk:10.0-noble

RUN apt-get update \
 && apt-get install -y --no-install-recommends unzip curl git python3 patchelf file ca-certificates \
 && rm -rf /var/lib/apt/lists/*

ARG NDK_VERSION=r27b
RUN curl -fsSL -o /tmp/ndk.zip "https://dl.google.com/android/repository/android-ndk-${NDK_VERSION}-linux.zip" \
 && unzip -q /tmp/ndk.zip -d /opt \
 && rm /tmp/ndk.zip
ENV ANDROID_NDK_HOME=/opt/android-ndk-${NDK_VERSION}
ENV ANDROID_NDK_ROOT=/opt/android-ndk-${NDK_VERSION}

RUN curl -fsSL -o /usr/local/bin/bazel https://github.com/bazelbuild/bazelisk/releases/download/v1.25.0/bazelisk-linux-amd64 \
 && chmod +x /usr/local/bin/bazel
```

- [ ] **Step 6: `docker/run.sh` を書く**

```bash
#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# リポジトリを /work にマウントしたビルド用コンテナで、引数のコマンドを実行する。
# NuGet と Bazel のキャッシュは名前付きボリュームに残す (2 回目以降を速くする)。
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
exec docker run --rm --platform linux/amd64 \
    -v "$root":/work -w /work \
    -v meltype-nuget:/root/.nuget \
    -v meltype-bazel:/root/.cache/bazel \
    meltype-build "$@"
```

```bash
chmod +x docker/run.sh
```

- [ ] **Step 7: イメージを作って、中身を確かめる**

Docker Desktop が起動していなければ先に起動する (`open -a Docker`)。

```bash
docker build --platform linux/amd64 -t meltype-build docker
docker/run.sh bash -c 'dotnet --version && ls $ANDROID_NDK_HOME/toolchains/llvm/prebuilt && bazel --version'
```
Expected: `10.0.x`、`linux-x86_64`、`bazel 8.x` (または `bazel 7.x`) が出る。

- [ ] **Step 8: Commit**

```bash
git add .gitignore .gitmodules upstream LICENSE README.md docker
git commit -m "chore: scaffold repo with upstream submodule and native build image

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: ネイティブの成果物をビルドする (spike: NativeAOT と Mozc)

**Files:**
- Create: `core-native/build.sh`, `mozc/build.sh`

**Interfaces:**
- Consumes: `docker/run.sh` (Task 1)
- Produces:
  - `build/jniLibs/arm64-v8a/libMeltypeNative.so` — SONAME `libMeltypeNative.so`。`meltype_init_mozc`, `meltype_create`, `meltype_destroy`, `meltype_handle_key`, `meltype_role_key`, `meltype_commit`, `meltype_select_candidate`, `meltype_set_direct`, `meltype_set_code_input`, `meltype_set_can_delete`, `meltype_version`, `meltype_free` を export する。
  - `build/jniLibs/arm64-v8a/libmozc_helper.so` — 中身は Android arm64 の PIE 実行ファイル (`meltype_mozc_helper`)。
  - `build/notices/MOZC-LICENSE.txt`, `build/notices/MOZC-CREDITS.html`

- [ ] **Step 1: `core-native/build.sh` を書く**

```bash
#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# 本家の Meltype.Mac.Native (+ Meltype.Core) を Android arm64 (linux-bionic-arm64) 向けに NativeAOT でビルドする。
# docker/run.sh core-native/build.sh で実行する。出力: build/jniLibs/arm64-v8a/libMeltypeNative.so
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
toolchain="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"
out="$root/build/jniLibs/arm64-v8a"
publish="$root/build/core-native"

# NativeAOT は NDK の clang でリンクする (linux-bionic は公式には「未サポート」扱いなので DisableUnsupportedError が要る)。
export PATH="$toolchain/bin:$PATH"
# 本家の nuget.config は NuGet を使わない設定なので、nuget.org を指定する (本家 linux/build.sh と同じ)。
dotnet publish "$root/upstream/src/Meltype.Mac.Native/Meltype.Mac.Native.csproj" -c Release -r linux-bionic-arm64 \
    -p:PublishAot=true -p:NativeLib=Shared -p:StripSymbols=true \
    -p:DisableUnsupportedError=true -p:PublishAotUsingRuntimePack=true \
    -p:CppCompilerAndLinker=clang -p:ObjCopyName=llvm-objcopy \
    -p:SysRoot="$toolchain/sysroot" \
    --source https://api.nuget.org/v3/index.json -o "$publish"

mkdir -p "$out"
cp -f "$publish/MeltypeNative.so" "$out/libMeltypeNative.so"
# Android は lib*.so の名前で読み込むので、SONAME も合わせる。
patchelf --set-soname libMeltypeNative.so "$out/libMeltypeNative.so"
echo "作成しました: $out/libMeltypeNative.so"
```

```bash
chmod +x core-native/build.sh
```

- [ ] **Step 2: ビルドして、export を確かめる**

```bash
docker/run.sh core-native/build.sh
docker/run.sh bash -c 'T=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin; f=build/jniLibs/arm64-v8a/libMeltypeNative.so; file $f; $T/llvm-readelf -d $f | grep SONAME; $T/llvm-nm -D --defined-only $f | grep -c " T meltype_"'
```
Expected: `ELF 64-bit LSB shared object, ARM aarch64`、`Library soname: [libMeltypeNative.so]`、`15` (本家の Exports.cs の関数の数)。

リンクで失敗したら: エラーに出た clang の引数を見る。`--target=aarch64-linux-android` が付いていなければ `-p:LinkerArg` ではなく、`core-native/Directory.Build.targets` を作って `<ItemGroup><LinkerArg Include="--target=aarch64-linux-android29" /></ItemGroup>` を足し、`dotnet publish` に `-p:CustomAfterMicrosoftCommonTargets=$root/core-native/Directory.Build.targets` を渡す。それでも通らなければ **止めてユーザーに相談する** (spike の失敗)。

- [ ] **Step 3: Commit (Core)**

```bash
git add core-native/build.sh
git commit -m "build: NativeAOT build of upstream core for linux-bionic-arm64

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 4: `mozc/build.sh` を書く**

```bash
#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# 本家の Mozc 変換ヘルパー (upstream/native/mozc/meltype_mozc_helper.cc) を Android arm64 向けにビルドする。
# docker/run.sh mozc/build.sh で実行する。本家 native/mozc/build-mozc-helper.sh の Android 版。
# 出力: build/jniLibs/arm64-v8a/libmozc_helper.so (中身は実行ファイル。Android はアプリの
#       ネイティブライブラリの置き場にある lib*.so なら実行できるので、この名前で APK に入れる)
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
helper="$root/upstream/native/mozc"
mozc="$root/build/mozc-src"
out="$root/build/jniLibs/arm64-v8a"
notices="$root/build/notices"
commit="$(tr -d '[:space:]' < "$helper/MOZC_COMMIT")"

if [[ ! -d "$mozc/.git" ]]; then
    mkdir -p "$mozc"
    git -C "$mozc" init -q
    git -C "$mozc" remote add origin https://github.com/google/mozc.git
    git -C "$mozc" fetch -q --depth 1 origin "$commit"
    git -C "$mozc" checkout -q FETCH_HEAD
    git -C "$mozc" submodule update -q --init --recursive --depth 1
fi
src="$mozc/src"

cp -f "$helper/meltype_mozc_helper.cc" "$src/converter/"
if ! grep -q 'name = "meltype_mozc_helper"' "$src/converter/BUILD.bazel"; then
    printf '\n%s\n' "$(cat "$helper/BUILD.fragment")" >> "$src/converter/BUILD.bazel"
fi

cd "$src"
bazel build //converter:meltype_mozc_helper --config oss_android --config release_build ${MOZC_BAZEL_FLAGS:-}

binary="$(find -L bazel-out -path '*arm64*' -name meltype_mozc_helper -type f | head -1)"
if [[ -z "$binary" ]]; then
    echo "arm64 の meltype_mozc_helper が見つかりません。MOZC_BAZEL_FLAGS で Android arm64 の platform を指定してください。" >&2
    exit 1
fi
mkdir -p "$out" "$notices"
cp -f "$binary" "$out/libmozc_helper.so"
chmod u+w,a+x "$out/libmozc_helper.so"
cp -f data/installer/credits_en.html "$notices/MOZC-CREDITS.html"
cp -f "$mozc/LICENSE" "$notices/MOZC-LICENSE.txt"
echo "作成しました: $out/libmozc_helper.so"
```

```bash
chmod +x mozc/build.sh
```

- [ ] **Step 5: Mozc の Android の platform の指定を確かめる**

```bash
docker/run.sh bash -c 'mkdir -p build && [ -d build/mozc-src/.git ] || (git init -q build/mozc-src && cd build/mozc-src && git remote add origin https://github.com/google/mozc.git && git fetch -q --depth 1 origin $(tr -d "[:space:]" < /work/upstream/native/mozc/MOZC_COMMIT) && git checkout -q FETCH_HEAD); grep -n "oss_android\|android_platforms\|platforms=" build/mozc-src/src/.bazelrc; ls build/mozc-src/docs | grep -i android'
```
Expected: `.bazelrc` の `build:oss_android` の行が見える。arm64 の platform を指定するフラグ (`--android_platforms=...` や `--platforms=...`) がそこに無ければ、`build/mozc-src/docs/build_mozc_for_android.md` に書かれている arm64 の指定を `MOZC_BAZEL_FLAGS` として使う。

- [ ] **Step 6: ビルドして、Android arm64 の実行ファイルか確かめる**

初回は Mozc の依存の取得とビルドで 1 時間ほどかかる (Rosetta のエミュレーションのため)。

```bash
docker/run.sh mozc/build.sh            # Step 5 で platform の指定が要ると分かったら: docker/run.sh env MOZC_BAZEL_FLAGS='<指定>' mozc/build.sh
docker/run.sh file build/jniLibs/arm64-v8a/libmozc_helper.so
```
Expected: `ELF 64-bit LSB pie executable, ARM aarch64, ... interpreter /system/bin/linker64`。

`interpreter /system/bin/linker64` が無い (Linux 用になっている) か、ビルドが通らなければ **止めてユーザーに相談する** (spike の失敗)。

- [ ] **Step 7: Commit (Mozc)**

```bash
git add mozc/build.sh
git commit -m "build: Android arm64 build of upstream Mozc helper

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Q25 のキーを記録するアプリ (spike、ユーザーが実機で操作)

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`)
- Create: `tools/keylogger/build.gradle.kts`, `tools/keylogger/src/main/AndroidManifest.xml`, `tools/keylogger/src/main/java/dev/horota/meltype/keylogger/KeyLoggerActivity.kt`
- Create: `docs/q25-keys.md`

**Interfaces:**
- Produces: Gradle のルート設定 (`:app` と `:tools:keylogger` を include)。`docs/q25-keys.md` に、Q25 の各キーの keyCode / metaState / unicodeChar の実測値と、Sym キーが KeyEvent として届くか、トラックパッドが DPAD として届くか。

- [ ] **Step 1: Gradle の wrapper を用意する**

```bash
brew install gradle   # 入っていなければ
cd /Volumes/OWCExpress1M2/work/meltype-android
gradle wrapper --gradle-version 8.11.1
./gradlew --version | head -3
```
Expected: `Gradle 8.11.1`

- [ ] **Step 2: ルートの Gradle 設定を書く**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "meltype-android"
include(":tools:keylogger")
```

`build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0" apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 3: `tools/keylogger/build.gradle.kts` を書く**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.horota.meltype.keylogger"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.horota.meltype.keylogger"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "spike"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
```

- [ ] **Step 4: manifest と Activity を書く**

`tools/keylogger/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="Meltype KeyLogger">
        <activity android:name=".KeyLoggerActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`tools/keylogger/src/main/java/dev/horota/meltype/keylogger/KeyLoggerActivity.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.keylogger

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * spike 用: 物理キーボードとトラックパッドが出すイベントを全部記録する。捨てる前提。
 * 入力欄を置かない (IME を通さず、Activity が KeyEvent をそのまま受けるため)。
 */
class KeyLoggerActivity : Activity() {
    private val log = StringBuilder()
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply { textSize = 11f; isFocusable = false }
        val share = Button(this).apply {
            text = "共有 (ログを送る)"
            isFocusable = false
            setOnClickListener {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, log.toString())
                }, "KeyLogger"))
            }
        }
        val clear = Button(this).apply {
            text = "クリア"
            isFocusable = false
            setOnClickListener { log.setLength(0); output.text = "" }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(context).apply { addView(share); addView(clear) })
            addView(ScrollView(context).apply { addView(output) })
        })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> "DOWN"
            KeyEvent.ACTION_UP -> "UP"
            else -> "OTHER"
        }
        val unicode = event.getUnicodeChar(event.metaState)
        val base = event.getUnicodeChar(0)
        append(
            "$action ${KeyEvent.keyCodeToString(event.keyCode)}(${event.keyCode}) " +
                "meta=0x${Integer.toHexString(event.metaState)} " +
                "char=${describe(unicode)} base=${describe(base)} " +
                "repeat=${event.repeatCount} device=${event.device?.name} source=0x${Integer.toHexString(event.source)}"
        )
        return true
    }

    override fun dispatchTrackballEvent(event: MotionEvent): Boolean {
        append("TRACKBALL x=${event.x} y=${event.y} action=${event.actionMasked}")
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        append("MOTION source=0x${Integer.toHexString(event.source)} action=${event.actionMasked} x=${event.x} y=${event.y}")
        return true
    }

    private fun describe(code: Int): String =
        if (code == 0) "0" else "U+%04X'%s'".format(code and 0x7FFFFFFF, String(Character.toChars(code and 0x7FFFFFFF)))

    private fun append(line: String) {
        log.appendLine(line)
        output.text = log.takeLast(4000)
    }
}
```

- [ ] **Step 5: settings.gradle.kts に `:tools:keylogger` が入っていることを確かめて、ビルドする**

```bash
./gradlew :tools:keylogger:assembleDebug
ls tools/keylogger/build/outputs/apk/debug/
```
Expected: `keylogger-debug.apk`

- [ ] **Step 6: ユーザーに Q25 で記録してもらう (人の作業)**

ユーザーに次をお願いする:
1. Q25 を USB でつなぎ、`./gradlew :tools:keylogger:installDebug` を実行する (または APK を送って入れる)。
2. 「Meltype KeyLogger」を開いて、次の順にキーを押す: `a` → `Shift+a` → `Alt+e` → `Alt+q` → `Sym` (押して離す) → `Sym+k` → `Sym+e` → `Shift+Space` → `Space` → `Enter` → `Backspace` → `$` (Q25 での打ち方で) → トラックパッドを上下左右に動かす → トラックパッドを押す → `Alt` を 2 回続けて押す。
3. 「共有」でログを送る (このセッションに貼り付ける)。

- [ ] **Step 7: 結果を `docs/q25-keys.md` にまとめる**

ログから次の表を作る (実測値で埋める。推測で埋めない):

```markdown
# Zinwa Q25 のキー (実測: 2026-10-xx, Android <版>)

| 押したキー | keyCode | metaState | char | base | 備考 |
|---|---|---|---|---|---|
| a | ... | ... | ... | ... | |
...

## 判断
- Sym は KeyEvent として届く / 届かない (システムが記号の一覧を出す)
- トラックパッドは DPAD (KEYCODE_DPAD_*) として届く / TRACKBALL として届く
- Alt+文字 の char は base と違う文字 (数字・記号) になる / ならない
- → Task 7 の Q25 の割り当て: (Sym+K / Sym+E を F7 / F10 にする、または使わない)
```

Sym が届かない、またはトラックパッドが DPAD として届かない場合は、その結果をユーザーに伝えて、Task 7 の該当部分をどうするか確認する。

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradlew gradlew.bat gradle tools/keylogger docs/q25-keys.md
git commit -m "spike: key logger app and measured Zinwa Q25 key codes

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: アプリの土台と Core の呼び出し (spike: 実機で変換)

**Files:**
- Modify: `settings.gradle.kts` (`include(":app")` を足す)
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`
- Create: `app/src/main/cpp/CMakeLists.txt`, `app/src/main/cpp/meltype_jni.c`
- Create: `app/src/main/java/dev/horota/meltype/core/NativeCore.kt`, `CoreApi.kt`, `CoreBridge.kt`, `SessionResult.kt`
- Test: `app/src/test/java/dev/horota/meltype/core/SessionResultTest.kt`
- Test: `app/src/androidTest/java/dev/horota/meltype/core/CoreSmokeTest.kt`

**Interfaces:**
- Consumes: `build/jniLibs/arm64-v8a/libMeltypeNative.so`, `libmozc_helper.so` (Task 2)
- Produces:
  ```kotlin
  // core/SessionResult.kt
  @Serializable data class TextEdit(val deleteBefore: Int = 0, val text: String = "", val expect: String? = null)
  @Serializable data class CompositionView(val text: String = "", val converting: Boolean = false,
      val selectedIndex: Int = -1, val selectedClause: Int = -1, val hint: String = "",
      val candidates: List<String> = emptyList(), val clauses: List<String> = emptyList(),
      val suggestion: String? = null, val meaning: String? = null, val notes: List<String?> = emptyList(),
      val predictions: List<String> = emptyList(), val selectedPrediction: Int = -1, val typed: String? = null)
  @Serializable data class SessionResult(val consumed: Boolean, val commits: List<TextEdit> = emptyList(), val view: CompositionView? = null)
  object SessionResultParser { fun parse(json: String): SessionResult? }   // 壊れた JSON なら null

  // core/CoreApi.kt
  interface CoreApi {
      fun handleKey(vk: Int, ch: Int, modifiers: Int, before: String?, after: String?): SessionResult?
      fun roleKey(vk: Int): SessionResult?
      fun commit(): SessionResult?
      fun selectCandidate(index: Int): SessionResult?
      fun setDirect(direct: Boolean)
      fun setCodeInput(enabled: Boolean)
      fun setCanDelete(canDelete: Boolean)
  }

  // core/CoreBridge.kt
  sealed interface CoreOpenResult {
      data class Ready(val core: CoreBridge, val mozcAvailable: Boolean) : CoreOpenResult
      data class Failed(val reason: String) : CoreOpenResult
  }
  class CoreBridge : CoreApi { companion object { fun open(dataDir: File, helper: File): CoreOpenResult } ; fun version(): String? }
  ```

- [ ] **Step 1: `settings.gradle.kts` に app を足す**

`include(":tools:keylogger")` の上に足す:
```kotlin
include(":app")
```

- [ ] **Step 2: `app/build.gradle.kts` を書く**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "dev.horota.meltype"
    compileSdk = 35
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "dev.horota.meltype"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
    }

    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }
    // Task 2 の成果物 (libMeltypeNative.so, libmozc_helper.so) を APK に入れる
    sourceSets["main"].jniLibs.srcDirs("../build/jniLibs")
    // libmozc_helper.so を実行するには、ネイティブライブラリが APK から展開されている必要がある
    packaging { jniLibs { useLegacyPackaging = true } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
```

- [ ] **Step 3: manifest と strings を書く**

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="false"
        android:extractNativeLibs="true"
        android:label="@string/app_name">
    </application>
</manifest>
```

`app/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Meltype</string>
</resources>
```

- [ ] **Step 4: 結果の JSON の parse のテストを書く (失敗する)**

`app/src/test/java/dev/horota/meltype/core/SessionResultTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionResultTest {
    @Test
    fun parsesCommitsAndView() {
        // 本家 SessionResult.ToJson の形 (upstream/src/Meltype.Core/Composition/MeltypeSession.cs)
        val json = """{"consumed":true,"commits":[{"deleteBefore":3,"text":"今日","expect":"api"}],""" +
            """"view":{"text":"きょう","converting":true,"selectedIndex":0,"selectedClause":0,"hint":"Space で変換",""" +
            """"candidates":["今日","京"],"clauses":["今日"],"suggestion":null,"meaning":null,"notes":[null,"英訳"],""" +
            """"predictions":[],"selectedPrediction":-1,"typed":null}}"""

        val result = SessionResultParser.parse(json)

        requireNotNull(result)
        assertTrue(result.consumed)
        assertEquals(listOf(TextEdit(3, "今日", "api")), result.commits)
        val view = requireNotNull(result.view)
        assertEquals("きょう", view.text)
        assertEquals(listOf("今日", "京"), view.candidates)
        assertEquals(listOf(null, "英訳"), view.notes)
    }

    @Test
    fun parsesNullView() {
        val result = requireNotNull(SessionResultParser.parse("""{"consumed":false,"commits":[],"view":null}"""))
        assertFalse(result.consumed)
        assertNull(result.view)
    }

    @Test
    fun brokenJsonGivesNull() {
        assertNull(SessionResultParser.parse("{"))
    }
}
```

- [ ] **Step 5: 失敗を確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.horota.meltype.core.SessionResultTest'`
Expected: FAIL (`Unresolved reference: SessionResultParser` でコンパイルできない)

- [ ] **Step 6: `SessionResult.kt` を書く**

`app/src/main/java/dev/horota/meltype/core/SessionResult.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 入力欄への書き込み 1 回分。deleteBefore 文字をカーソルの前から消してから text を入れる。expect は消す文字 (分からなければ null)。 */
@Serializable
data class TextEdit(val deleteBefore: Int = 0, val text: String = "", val expect: String? = null)

/** 変換ボックスの中身 (本家 CompositionView)。 */
@Serializable
data class CompositionView(
    val text: String = "",
    val converting: Boolean = false,
    val selectedIndex: Int = -1,
    val selectedClause: Int = -1,
    val hint: String = "",
    val candidates: List<String> = emptyList(),
    val clauses: List<String> = emptyList(),
    val suggestion: String? = null,
    val meaning: String? = null,
    val notes: List<String?> = emptyList(),
    val predictions: List<String> = emptyList(),
    val selectedPrediction: Int = -1,
    val typed: String? = null,
)

/** キー 1 回の結果 (本家 SessionResult)。consumed が false ならキーをアプリに渡す (commits を入れた後で)。view が null なら変換ボックスを閉じる。 */
@Serializable
data class SessionResult(
    val consumed: Boolean,
    val commits: List<TextEdit> = emptyList(),
    val view: CompositionView? = null,
)

object SessionResultParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** 本家の SessionResult.ToJson の文字列を読む。読めなければ null。 */
    fun parse(text: String): SessionResult? =
        try {
            json.decodeFromString(SessionResult.serializer(), text)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
}
```

- [ ] **Step 7: テストが通ることを確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.horota.meltype.core.SessionResultTest'`
Expected: PASS (3 tests)

- [ ] **Step 8: JNI の橋渡しを書く**

`app/src/main/cpp/CMakeLists.txt`:
```cmake
cmake_minimum_required(VERSION 3.22)
project(meltype_jni C)
add_library(meltype_jni SHARED meltype_jni.c)
target_link_libraries(meltype_jni dl log)
```

`app/src/main/cpp/meltype_jni.c`:
```c
// SPDX-License-Identifier: GPL-3.0-or-later
// JNI の橋渡し: Kotlin (NativeCore) ↔ libMeltypeNative.so (本家 Meltype.Mac.Native の C ABI)。
// libMeltypeNative.so にはリンクせず dlopen する (読み込めないときに IME ごと落とさず、Kotlin 側でエラーにするため)。
// 文字列は Kotlin 側で UTF-8 の NUL 終端 ByteArray にして渡す (JNI の modified UTF-8 は絵文字を壊すため)。
#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <stdint.h>
#include <string.h>

#define TAG "Meltype"

typedef int (*init_mozc_fn)(const char *, const char *);
typedef void *(*create_fn)(void);
typedef void (*destroy_fn)(void *);
typedef char *(*handle_key_fn)(void *, int, int, int, const char *, const char *);
typedef char *(*session_int_fn)(void *, int);
typedef char *(*session_fn)(void *);
typedef void (*set_flag_fn)(void *, int);
typedef char *(*string_fn)(void);
typedef void (*free_fn)(void *);

static struct {
    init_mozc_fn init_mozc;
    create_fn create;
    destroy_fn destroy;
    handle_key_fn handle_key;
    session_int_fn role_key;
    session_fn commit;
    session_int_fn select_candidate;
    set_flag_fn set_direct;
    set_flag_fn set_code_input;
    set_flag_fn set_can_delete;
    string_fn version;
    free_fn free_text;
} core;

static int load_symbol(void *lib, void **slot, const char *name) {
    *slot = dlsym(lib, name);
    if (*slot == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "関数がありません: %s", name);
        return 0;
    }
    return 1;
}

JNIEXPORT jboolean JNICALL
Java_dev_horota_meltype_core_NativeCore_load(JNIEnv *env, jobject thiz) {
    void *lib = dlopen("libMeltypeNative.so", RTLD_NOW);
    if (lib == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "libMeltypeNative.so を読み込めません: %s", dlerror());
        return JNI_FALSE;
    }
    int ok = load_symbol(lib, (void **)&core.init_mozc, "meltype_init_mozc")
        && load_symbol(lib, (void **)&core.create, "meltype_create")
        && load_symbol(lib, (void **)&core.destroy, "meltype_destroy")
        && load_symbol(lib, (void **)&core.handle_key, "meltype_handle_key")
        && load_symbol(lib, (void **)&core.role_key, "meltype_role_key")
        && load_symbol(lib, (void **)&core.commit, "meltype_commit")
        && load_symbol(lib, (void **)&core.select_candidate, "meltype_select_candidate")
        && load_symbol(lib, (void **)&core.set_direct, "meltype_set_direct")
        && load_symbol(lib, (void **)&core.set_code_input, "meltype_set_code_input")
        && load_symbol(lib, (void **)&core.set_can_delete, "meltype_set_can_delete")
        && load_symbol(lib, (void **)&core.version, "meltype_version")
        && load_symbol(lib, (void **)&core.free_text, "meltype_free");
    return ok ? JNI_TRUE : JNI_FALSE;
}

/** ByteArray (NUL 終端の UTF-8) を C の文字列として借りる。null なら NULL。 */
static const char *borrow(JNIEnv *env, jbyteArray bytes) {
    return bytes == NULL ? NULL : (const char *)(*env)->GetByteArrayElements(env, bytes, NULL);
}

static void give_back(JNIEnv *env, jbyteArray bytes, const char *text) {
    if (bytes != NULL) (*env)->ReleaseByteArrayElements(env, bytes, (jbyte *)text, JNI_ABORT);
}

/** Core が返した文字列を ByteArray にして、meltype_free で解放する。NULL なら null。 */
static jbyteArray take(JNIEnv *env, char *text) {
    if (text == NULL) return NULL;
    jsize length = (jsize)strlen(text);
    jbyteArray out = (*env)->NewByteArray(env, length);
    if (out != NULL) (*env)->SetByteArrayRegion(env, out, 0, length, (const jbyte *)text);
    core.free_text(text);
    return out;
}

static void *session(jlong handle) { return (void *)(intptr_t)handle; }

JNIEXPORT jint JNICALL
Java_dev_horota_meltype_core_NativeCore_initMozc(JNIEnv *env, jobject thiz, jbyteArray helper, jbyteArray profile) {
    const char *h = borrow(env, helper);
    const char *p = borrow(env, profile);
    int result = core.init_mozc(h, p);
    give_back(env, helper, h);
    give_back(env, profile, p);
    return result;
}

JNIEXPORT jlong JNICALL
Java_dev_horota_meltype_core_NativeCore_create(JNIEnv *env, jobject thiz) {
    return (jlong)(intptr_t)core.create();
}

JNIEXPORT void JNICALL
Java_dev_horota_meltype_core_NativeCore_destroy(JNIEnv *env, jobject thiz, jlong handle) {
    core.destroy(session(handle));
}

JNIEXPORT jbyteArray JNICALL
Java_dev_horota_meltype_core_NativeCore_handleKey(JNIEnv *env, jobject thiz, jlong handle, jint vk, jint ch,
                                                   jint modifiers, jbyteArray before, jbyteArray after) {
    const char *b = borrow(env, before);
    const char *a = borrow(env, after);
    char *result = core.handle_key(session(handle), vk, ch, modifiers, b, a);
    give_back(env, before, b);
    give_back(env, after, a);
    return take(env, result);
}

JNIEXPORT jbyteArray JNICALL
Java_dev_horota_meltype_core_NativeCore_roleKey(JNIEnv *env, jobject thiz, jlong handle, jint vk) {
    return take(env, core.role_key(session(handle), vk));
}

JNIEXPORT jbyteArray JNICALL
Java_dev_horota_meltype_core_NativeCore_commit(JNIEnv *env, jobject thiz, jlong handle) {
    return take(env, core.commit(session(handle)));
}

JNIEXPORT jbyteArray JNICALL
Java_dev_horota_meltype_core_NativeCore_selectCandidate(JNIEnv *env, jobject thiz, jlong handle, jint index) {
    return take(env, core.select_candidate(session(handle), index));
}

JNIEXPORT void JNICALL
Java_dev_horota_meltype_core_NativeCore_setDirect(JNIEnv *env, jobject thiz, jlong handle, jboolean on) {
    core.set_direct(session(handle), on ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_dev_horota_meltype_core_NativeCore_setCodeInput(JNIEnv *env, jobject thiz, jlong handle, jboolean on) {
    core.set_code_input(session(handle), on ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_dev_horota_meltype_core_NativeCore_setCanDelete(JNIEnv *env, jobject thiz, jlong handle, jboolean on) {
    core.set_can_delete(session(handle), on ? 1 : 0);
}

JNIEXPORT jbyteArray JNICALL
Java_dev_horota_meltype_core_NativeCore_version(JNIEnv *env, jobject thiz) {
    return take(env, core.version());
}
```

- [ ] **Step 9: Kotlin 側の宣言と CoreApi を書く**

`app/src/main/java/dev/horota/meltype/core/NativeCore.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.core

/** meltype_jni.c の関数。文字列は UTF-8 の NUL 終端 ByteArray でやり取りする。CoreBridge 以外から使わない。 */
internal object NativeCore {
    external fun load(): Boolean
    external fun initMozc(helper: ByteArray, profile: ByteArray?): Int
    external fun create(): Long
    external fun destroy(handle: Long)
    external fun handleKey(handle: Long, vk: Int, ch: Int, modifiers: Int, before: ByteArray?, after: ByteArray?): ByteArray?
    external fun roleKey(handle: Long, vk: Int): ByteArray?
    external fun commit(handle: Long): ByteArray?
    external fun selectCandidate(handle: Long, index: Int): ByteArray?
    external fun setDirect(handle: Long, on: Boolean)
    external fun setCodeInput(handle: Long, on: Boolean)
    external fun setCanDelete(handle: Long, on: Boolean)
    external fun version(): ByteArray?
}
```

`app/src/main/java/dev/horota/meltype/core/CoreApi.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.core

/**
 * Meltype の入力の本体 (本家 MeltypeSession) の呼び出し。本物は CoreBridge、テストでは偽物を使う。
 * 結果が null のときは「Core が処理しなかった (キーはアプリに渡す)」。
 */
interface CoreApi {
    /** vk は Windows の仮想キーコード、ch は Unicode スカラー (無ければ 0)、modifiers は Shift=1 Ctrl=2 Alt=4 Meta=8。 */
    fun handleKey(vk: Int, ch: Int, modifiers: Int, before: String?, after: String?): SessionResult?
    /** 変換 (0x1C)・無変換 (0x1D)・かな (0x15)。入力中でなければ null。 */
    fun roleKey(vk: Int): SessionResult?
    fun commit(): SessionResult?
    fun selectCandidate(index: Int): SessionResult?
    fun setDirect(direct: Boolean)
    fun setCodeInput(enabled: Boolean)
    fun setCanDelete(canDelete: Boolean)
}
```

- [ ] **Step 10: `CoreBridge.kt` を書く**

`app/src/main/java/dev/horota/meltype/core/CoreBridge.kt`:
```kotlin
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
```

- [ ] **Step 11: 実機 / エミュレータでのスモークテストを書く**

`app/src/androidTest/java/dev/horota/meltype/core/CoreSmokeTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CoreSmokeTest {
    private lateinit var core: CoreBridge

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val opened = CoreBridge.open(
            File(context.filesDir, "Meltype-test"),
            File(context.applicationInfo.nativeLibraryDir, "libmozc_helper.so"),
        )
        assertTrue("Core を開けない: $opened", opened is CoreOpenResult.Ready)
        val ready = opened as CoreOpenResult.Ready
        assertTrue("Mozc が使えない", ready.mozcAvailable)
        core = ready.core
    }

    /** 入力欄の代わり。deleteBefore と text を順に反映する。 */
    private class Field {
        val text = StringBuilder()
        var composing = false
        fun apply(result: SessionResult?) {
            if (result == null) return
            for (edit in result.commits) {
                text.setLength(maxOf(0, text.length - edit.deleteBefore))
                text.append(edit.text)
            }
            composing = result.view != null
        }
    }

    @Test
    fun mixedSentenceConverts() {
        val field = Field()
        for (c in "kyouhagoogledekensaku") {
            val before = if (field.composing) null else field.text.toString()
            field.apply(core.handleKey(c.uppercaseChar().code, c.code, 0, before, ""))
        }
        field.apply(core.handleKey(0x20, 0x20, 0, null, null)) // Space: 変換
        field.apply(core.handleKey(0x0D, 0, 0, null, null))    // Enter: 確定
        assertEquals("今日はgoogleで検索", field.text.toString())
    }

    @Test
    fun emojiAroundCaretDoesNotBreak() {
        val result = core.handleKey('A'.code, 'a'.code, 0, "😀", "🎉")
        assertNotNull(result)
        core.commit()
    }
}
```

- [ ] **Step 12: arm64 のエミュレータ (または Q25) で動かす**

```bash
$ANDROID_HOME/emulator/emulator -list-avds          # arm64 の AVD を 1 つ選ぶ
$ANDROID_HOME/emulator/emulator -avd <AVD名> -no-snapshot &   # 実機なら USB でつなぐだけ
adb wait-for-device
./gradlew :app:connectedDebugAndroidTest
```
Expected: `CoreSmokeTest` の 2 つが PASS。

- `mixedSentenceConverts` が「今日はgoogleで検索」以外の文字で落ちたら (例: 「きょうはgoogleで検索」)、`adb shell run-as dev.horota.meltype cat files/Meltype-test/meltype.log` を見る。Mozc の起動に失敗しているなら Task 2 の Mozc 部分の問題。
- `setUp` で落ちたら (Core を開けない / Mozc が使えない) **止めてユーザーに相談する** (spike の失敗)。

- [ ] **Step 13: Commit**

```bash
git add settings.gradle.kts app
git commit -m "feat: load upstream core via JNI and convert with Mozc on device

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: KeyNormalizer (Android のキー → Windows の仮想キーコード)

**Files:**
- Create: `app/src/main/java/dev/horota/meltype/input/KeyNormalizer.kt`
- Test: `app/src/test/java/dev/horota/meltype/input/KeyNormalizerTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class KeyInput(val keyCode: Int, val char: Int, val baseChar: Int, val metaState: Int)
  // char = KeyEvent.getUnicodeChar(metaState), baseChar = KeyEvent.getUnicodeChar(0)
  data class NormalizedKey(val vk: Int, val ch: Int, val modifiers: Int)
  object Modifiers { const val SHIFT = 1; const val CONTROL = 2; const val ALT = 4; const val META = 8 }
  object KeyNormalizer { fun normalize(key: KeyInput): NormalizedKey? }  // Meltype が扱わないキーなら null
  ```

本家 Linux 版 (`upstream/linux/ibus-engine-meltype` の `SPECIAL_KEYS` / `virtual_key`) と同じ変換にする。

- [ ] **Step 1: テストを書く (失敗する)**

`app/src/test/java/dev/horota/meltype/input/KeyNormalizerTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.input

import android.view.KeyCharacterMap
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyNormalizerTest {
    private fun key(keyCode: Int, char: Int, baseChar: Int = char, meta: Int = 0) =
        KeyNormalizer.normalize(KeyInput(keyCode, char, baseChar, meta))

    @Test
    fun lowercaseLetter() {
        assertEquals(NormalizedKey(0x41, 'a'.code, 0), key(KeyEvent.KEYCODE_A, 'a'.code))
    }

    @Test
    fun shiftedLetter() {
        assertEquals(
            NormalizedKey(0x41, 'A'.code, Modifiers.SHIFT),
            key(KeyEvent.KEYCODE_A, 'A'.code, 'a'.code, KeyEvent.META_SHIFT_ON),
        )
    }

    @Test
    fun ctrlLetterUsesBaseChar() {
        // Ctrl を押していると getUnicodeChar(metaState) は 0 になる
        assertEquals(
            NormalizedKey(0x43, 'c'.code, Modifiers.CONTROL),
            key(KeyEvent.KEYCODE_C, 0, 'c'.code, KeyEvent.META_CTRL_ON),
        )
    }

    @Test
    fun altLayerCharacterDropsAltModifier() {
        // Q25 (BlackBerry 配列): Alt+E が "2" になる。数字として渡し、Alt のショートカットにしない
        assertEquals(
            NormalizedKey(0x32, '2'.code, 0),
            key(KeyEvent.KEYCODE_E, '2'.code, 'e'.code, KeyEvent.META_ALT_ON),
        )
    }

    @Test
    fun altWithoutLayerCharacterKeepsAlt() {
        assertEquals(
            NormalizedKey(0x58, 'x'.code, Modifiers.ALT),
            key(KeyEvent.KEYCODE_X, 'x'.code, 'x'.code, KeyEvent.META_ALT_ON),
        )
    }

    @Test
    fun specialKeys() {
        assertEquals(NormalizedKey(0x0D, 0, 0), key(KeyEvent.KEYCODE_ENTER, '\n'.code))
        assertEquals(NormalizedKey(0x0D, 0, 0), key(KeyEvent.KEYCODE_DPAD_CENTER, 0))
        assertEquals(NormalizedKey(0x20, 0x20, 0), key(KeyEvent.KEYCODE_SPACE, ' '.code))
        assertEquals(NormalizedKey(0x08, 0, 0), key(KeyEvent.KEYCODE_DEL, 0))
        assertEquals(NormalizedKey(0x26, 0, 0), key(KeyEvent.KEYCODE_DPAD_UP, 0))
        assertEquals(NormalizedKey(0x76, 0, 0), key(KeyEvent.KEYCODE_F7, 0))
        assertEquals(NormalizedKey(0x79, 0, 0), key(KeyEvent.KEYCODE_F10, 0))
    }

    @Test
    fun symbols() {
        assertEquals(NormalizedKey(0xBC, ','.code, 0), key(KeyEvent.KEYCODE_COMMA, ','.code))
        assertEquals(NormalizedKey(0x07, '@'.code, Modifiers.SHIFT), key(KeyEvent.KEYCODE_2, '@'.code, '2'.code, KeyEvent.META_SHIFT_ON))
        assertEquals(NormalizedKey(0x07, 0xE9, 0), key(KeyEvent.KEYCODE_E, 0xE9))
    }

    @Test
    fun ignoredKeys() {
        assertNull(key(KeyEvent.KEYCODE_VOLUME_UP, 0))
        assertNull(key(KeyEvent.KEYCODE_GRAVE, '`'.code or KeyCharacterMap.COMBINING_ACCENT))
    }
}
```

- [ ] **Step 2: 失敗を確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.horota.meltype.input.KeyNormalizerTest'`
Expected: FAIL (`Unresolved reference: KeyNormalizer`)

- [ ] **Step 3: `KeyNormalizer.kt` を書く**

```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.input

import android.view.KeyCharacterMap
import android.view.KeyEvent

/** KeyEvent から取り出した値。char = getUnicodeChar(metaState)、baseChar = getUnicodeChar(0)。 */
data class KeyInput(val keyCode: Int, val char: Int, val baseChar: Int, val metaState: Int)

/** Core (meltype_handle_key) に渡す形。vk は Windows の仮想キーコード。 */
data class NormalizedKey(val vk: Int, val ch: Int, val modifiers: Int)

object Modifiers {
    const val SHIFT = 1
    const val CONTROL = 2
    const val ALT = 4
    const val META = 8
}

/** Android のキーを本家 Core が使う Windows の仮想キーコードに変える (本家 Linux 版 virtual_key と同じ規則)。 */
object KeyNormalizer {
    /** 文字を入力するキーで、特別な扱いのないもの (記号など)。Core は文字だけを見る。 */
    private const val OTHER_CHARACTER_KEY = 0x07
    private const val VK_SPACE = 0x20

    private val SPECIAL_KEYS = mapOf(
        KeyEvent.KEYCODE_ENTER to 0x0D,
        KeyEvent.KEYCODE_NUMPAD_ENTER to 0x0D,
        // トラックパッドを押したとき (Q25)。入力中でなければ Core が consumed=false を返し、アプリに渡る
        KeyEvent.KEYCODE_DPAD_CENTER to 0x0D,
        KeyEvent.KEYCODE_TAB to 0x09,
        KeyEvent.KEYCODE_SPACE to VK_SPACE,
        KeyEvent.KEYCODE_DEL to 0x08,
        KeyEvent.KEYCODE_FORWARD_DEL to 0x2E,
        KeyEvent.KEYCODE_ESCAPE to 0x1B,
        KeyEvent.KEYCODE_DPAD_LEFT to 0x25,
        KeyEvent.KEYCODE_DPAD_UP to 0x26,
        KeyEvent.KEYCODE_DPAD_RIGHT to 0x27,
        KeyEvent.KEYCODE_DPAD_DOWN to 0x28,
        KeyEvent.KEYCODE_MOVE_HOME to 0x24,
        KeyEvent.KEYCODE_MOVE_END to 0x23,
        KeyEvent.KEYCODE_PAGE_UP to 0x21,
        KeyEvent.KEYCODE_PAGE_DOWN to 0x22,
        KeyEvent.KEYCODE_F6 to 0x75,
        KeyEvent.KEYCODE_F7 to 0x76,
        KeyEvent.KEYCODE_F8 to 0x77,
        KeyEvent.KEYCODE_F9 to 0x78,
        KeyEvent.KEYCODE_F10 to 0x79,
    )

    private val SYMBOL_KEYS = mapOf(',' to 0xBC, '.' to 0xBE, '-' to 0xBD, '/' to 0xBF, '[' to 0xDB, ']' to 0xDD)

    fun normalize(key: KeyInput): NormalizedKey? {
        val modifiers = modifiersOf(key)
        SPECIAL_KEYS[key.keyCode]?.let { vk -> return NormalizedKey(vk, if (vk == VK_SPACE) VK_SPACE else 0, modifiers) }
        // Ctrl などで char が 0 のときは、修飾なしの文字で判断する
        val c = if (key.char != 0) key.char else key.baseChar
        if (c == 0 || (c and KeyCharacterMap.COMBINING_ACCENT) != 0) return null
        if (c < 0x20) return null
        val vk = when (c) {
            in 'A'.code..'Z'.code -> c
            in 'a'.code..'z'.code -> c - 0x20
            in '0'.code..'9'.code -> c
            else -> (if (c <= 0xFFFF) SYMBOL_KEYS[c.toChar()] else null) ?: OTHER_CHARACTER_KEY
        }
        return NormalizedKey(vk, c, modifiers)
    }

    private fun modifiersOf(key: KeyInput): Int {
        var modifiers = 0
        if (key.metaState and KeyEvent.META_SHIFT_ON != 0) modifiers = modifiers or Modifiers.SHIFT
        if (key.metaState and KeyEvent.META_CTRL_ON != 0) modifiers = modifiers or Modifiers.CONTROL
        // Alt で別の文字 (Q25 の数字・記号の層) になったときは、その文字を打っただけとして Alt を外す
        val altLayer = key.char != 0 && key.char != key.baseChar
        if (key.metaState and KeyEvent.META_ALT_ON != 0 && !altLayer) modifiers = modifiers or Modifiers.ALT
        if (key.metaState and KeyEvent.META_META_ON != 0) modifiers = modifiers or Modifiers.META
        return modifiers
    }
}
```

注意: `shiftedLetter` は char ('A') と baseChar ('a') が違うが Alt を押していないので影響しない。`altLayer` は Alt を押しているときにだけ使う。

- [ ] **Step 4: テストが通ることを確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.horota.meltype.input.KeyNormalizerTest'`
Expected: PASS (8 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/horota/meltype/input/KeyNormalizer.kt app/src/test/java/dev/horota/meltype/input/KeyNormalizerTest.kt
git commit -m "feat: map Android key events to Windows virtual keys

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: ResultApplier (結果を入力欄に反映する)

**Files:**
- Create: `app/src/main/java/dev/horota/meltype/ime/EditorSink.kt`, `app/src/main/java/dev/horota/meltype/ime/ResultApplier.kt`
- Test: `app/src/test/java/dev/horota/meltype/ime/ResultApplierTest.kt`, `app/src/test/java/dev/horota/meltype/ime/FakeEditor.kt`

**Interfaces:**
- Consumes: `SessionResult`, `TextEdit`, `CompositionView` (Task 4)
- Produces:
  ```kotlin
  interface EditorSink {
      fun beginBatch(); fun endBatch()
      fun textBeforeCursor(length: Int): String?
      fun textAfterCursor(length: Int): String?
      fun deleteBefore(length: Int)
      fun commit(text: String)
      fun setComposing(text: String)     // "" で未確定の文字を消す
      fun finishComposing()
  }
  interface CandidateDisplay { fun show(view: CompositionView); fun hide() }
  class ResultApplier(sink: EditorSink, display: CandidateDisplay) {
      val composing: Boolean
      fun apply(result: SessionResult)
      fun reset()                          // 入力欄が変わったとき
  }
  // テスト用 (app/src/test): class FakeEditor : EditorSink, CandidateDisplay { val ops: MutableList<String>; var before: String }
  ```

- [ ] **Step 1: テスト用の偽物を書く**

`app/src/test/java/dev/horota/meltype/ime/FakeEditor.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import dev.horota.meltype.core.CompositionView

/** 入力欄と候補バーの偽物。呼ばれた操作を ops に記録する。 */
class FakeEditor : EditorSink, CandidateDisplay {
    val ops = mutableListOf<String>()
    var before: String = ""
    var after: String = ""

    override fun beginBatch() { ops += "begin" }
    override fun endBatch() { ops += "end" }
    override fun textBeforeCursor(length: Int): String = before.takeLast(length)
    override fun textAfterCursor(length: Int): String = after.take(length)
    override fun deleteBefore(length: Int) { ops += "delete:$length" }
    override fun commit(text: String) { ops += "commit:$text" }
    override fun setComposing(text: String) { ops += "composing:$text" }
    override fun finishComposing() { ops += "finish" }
    override fun show(view: CompositionView) { ops += "show:${view.candidates.joinToString("|")}" }
    override fun hide() { ops += "hide" }
}
```

- [ ] **Step 2: テストを書く (失敗する)**

`app/src/test/java/dev/horota/meltype/ime/ResultApplierTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import dev.horota.meltype.core.CompositionView
import dev.horota.meltype.core.SessionResult
import dev.horota.meltype.core.TextEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultApplierTest {
    private val editor = FakeEditor()
    private val applier = ResultApplier(editor, editor)

    @Test
    fun commitOnly() {
        applier.apply(SessionResult(true, listOf(TextEdit(0, "abc"))))
        assertEquals(listOf("begin", "commit:abc", "hide", "end"), editor.ops)
    }

    @Test
    fun composingThenCommit() {
        applier.apply(SessionResult(true, emptyList(), CompositionView(text = "きょう", candidates = listOf("今日"))))
        assertTrue(applier.composing)
        editor.ops.clear()

        applier.apply(SessionResult(true, listOf(TextEdit(0, "今日")), null))

        assertEquals(listOf("begin", "composing:", "finish", "commit:今日", "hide", "end"), editor.ops)
        assertFalse(applier.composing)
    }

    @Test
    fun deleteWhenExpectMatches() {
        editor.before = "see api"
        applier.apply(SessionResult(true, listOf(TextEdit(3, "あぴ", "api"))))
        assertEquals(listOf("begin", "delete:3", "commit:あぴ", "hide", "end"), editor.ops)
    }

    @Test
    fun deleteSkippedWhenExpectMismatches() {
        // アプリ側で文字が変わっていたら、他人の文字を消さない
        editor.before = "see APX"
        applier.apply(SessionResult(true, listOf(TextEdit(3, "あぴ", "api"))))
        assertEquals(listOf("begin", "commit:あぴ", "hide", "end"), editor.ops)
    }

    @Test
    fun deleteWithoutExpect() {
        applier.apply(SessionResult(true, listOf(TextEdit(2, "x"))))
        assertEquals(listOf("begin", "delete:2", "commit:x", "hide", "end"), editor.ops)
    }

    @Test
    fun emptyViewTextShowsCandidatesWithoutComposing() {
        applier.apply(SessionResult(true, emptyList(), CompositionView(text = "", predictions = listOf("今日は"))))
        assertEquals(listOf("begin", "show:", "end"), editor.ops)
        assertFalse(applier.composing)
    }

    @Test
    fun resetForgetsComposing() {
        applier.apply(SessionResult(true, emptyList(), CompositionView(text = "か")))
        applier.reset()
        assertFalse(applier.composing)
    }
}
```

- [ ] **Step 3: 失敗を確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.horota.meltype.ime.ResultApplierTest'`
Expected: FAIL (`Unresolved reference: ResultApplier`)

- [ ] **Step 4: `EditorSink.kt` と `ResultApplier.kt` を書く**

`app/src/main/java/dev/horota/meltype/ime/EditorSink.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import dev.horota.meltype.core.CompositionView

/** 入力欄への書き込み。本物は InputConnectionSink (InputConnection)。 */
interface EditorSink {
    fun beginBatch()
    fun endBatch()
    fun textBeforeCursor(length: Int): String?
    fun textAfterCursor(length: Int): String?
    fun deleteBefore(length: Int)
    fun commit(text: String)
    /** 未確定の文字を出す。"" なら消す。 */
    fun setComposing(text: String)
    fun finishComposing()
}

/** 候補バー。 */
interface CandidateDisplay {
    fun show(view: CompositionView)
    fun hide()
}
```

`app/src/main/java/dev/horota/meltype/ime/ResultApplier.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import dev.horota.meltype.core.SessionResult
import dev.horota.meltype.core.TextEdit

/** Core の結果 (SessionResult) を入力欄と候補バーに反映する (本家 Linux 版 _apply と同じ順序)。 */
class ResultApplier(private val sink: EditorSink, private val display: CandidateDisplay) {
    /** 入力欄に未確定の文字を出しているか。 */
    var composing: Boolean = false
        private set

    fun apply(result: SessionResult) {
        sink.beginBatch()
        try {
            if (result.commits.isNotEmpty()) clearComposing()
            for (edit in result.commits) {
                if (edit.deleteBefore > 0 && canDelete(edit)) sink.deleteBefore(edit.deleteBefore)
                if (edit.text.isNotEmpty()) sink.commit(edit.text)
            }
            val view = result.view
            if (view == null) {
                clearComposing()
                display.hide()
            } else {
                if (view.text.isNotEmpty()) {
                    sink.setComposing(view.text)
                    composing = true
                } else {
                    clearComposing()
                }
                display.show(view)
            }
        } finally {
            sink.endBatch()
        }
    }

    /** 入力欄が変わった (onStartInput) ときに呼ぶ。 */
    fun reset() {
        composing = false
    }

    private fun clearComposing() {
        if (!composing) return
        sink.setComposing("")
        sink.finishComposing()
        composing = false
    }

    /** expect があれば、カーソルの前の文字がそれと同じときだけ消す (アプリ側で変わっていたら消さない)。 */
    private fun canDelete(edit: TextEdit): Boolean {
        val expect = edit.expect ?: return true
        return sink.textBeforeCursor(edit.deleteBefore) == expect
    }
}
```

- [ ] **Step 5: テストが通ることを確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.horota.meltype.ime.ResultApplierTest'`
Expected: PASS (7 tests)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/horota/meltype/ime app/src/test/java/dev/horota/meltype/ime
git commit -m "feat: apply core session results to the editor

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: KeyBindings (切り替えキーなど Meltype 独自の割り当て)

**前提:** Task 3 の `docs/q25-keys.md` が書けていること。Sym が KeyEvent として届かなかった場合は、下の `symK` / `symE` のテストと実装を外す (候補バーで代わりにする)。Sym の keyCode が `KEYCODE_SYM` (63) 以外だった場合は、実測値を `Q25_SYM_KEYCODE` に書く。

**Files:**
- Create: `app/src/main/java/dev/horota/meltype/input/KeyBindings.kt`
- Test: `app/src/test/java/dev/horota/meltype/input/KeyBindingsTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  sealed interface BindingAction {
      data object ToggleDirect : BindingAction
      data class SetDirect(val direct: Boolean) : BindingAction
      data class RoleKey(val vk: Int, val fallbackDirect: Boolean) : BindingAction
      data class SendVk(val vk: Int) : BindingAction
      data object TogglePause : BindingAction
  }
  data class BindingOptions(val shiftSpaceToggles: Boolean = true, val ctrlSpaceToggles: Boolean = true)
  object KeyBindings {
      const val Q25_SYM_KEYCODE: Int
      fun match(keyCode: Int, metaState: Int, symHeld: Boolean, options: BindingOptions): BindingAction?
  }
  ```

- [ ] **Step 1: テストを書く (失敗する)**

`app/src/test/java/dev/horota/meltype/input/KeyBindingsTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyBindingsTest {
    private val defaults = BindingOptions()
    private fun match(keyCode: Int, meta: Int = 0, sym: Boolean = false, options: BindingOptions = defaults) =
        KeyBindings.match(keyCode, meta, sym, options)

    @Test
    fun zenkakuHankakuToggles() {
        assertEquals(BindingAction.ToggleDirect, match(KeyEvent.KEYCODE_ZENKAKU_HANKAKU))
    }

    @Test
    fun ctrlZenkakuHankakuPauses() {
        assertEquals(BindingAction.TogglePause, match(KeyEvent.KEYCODE_ZENKAKU_HANKAKU, KeyEvent.META_CTRL_ON))
    }

    @Test
    fun eisuGoesDirect() {
        assertEquals(BindingAction.SetDirect(true), match(KeyEvent.KEYCODE_EISU))
    }

    @Test
    fun roleKeys() {
        assertEquals(BindingAction.RoleKey(0x1C, fallbackDirect = false), match(KeyEvent.KEYCODE_HENKAN))
        assertEquals(BindingAction.RoleKey(0x1D, fallbackDirect = true), match(KeyEvent.KEYCODE_MUHENKAN))
        assertEquals(BindingAction.RoleKey(0x15, fallbackDirect = false), match(KeyEvent.KEYCODE_KATAKANA_HIRAGANA))
    }

    @Test
    fun ctrlSpaceAndShiftSpaceToggle() {
        assertEquals(BindingAction.ToggleDirect, match(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON))
        assertEquals(BindingAction.ToggleDirect, match(KeyEvent.KEYCODE_SPACE, KeyEvent.META_SHIFT_ON))
    }

    @Test
    fun spaceTogglesCanBeTurnedOff() {
        val off = BindingOptions(shiftSpaceToggles = false, ctrlSpaceToggles = false)
        assertNull(match(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON, options = off))
        assertNull(match(KeyEvent.KEYCODE_SPACE, KeyEvent.META_SHIFT_ON, options = off))
    }

    @Test
    fun plainSpaceIsNotBound() {
        assertNull(match(KeyEvent.KEYCODE_SPACE))
    }

    @Test
    fun symK() {
        assertEquals(BindingAction.SendVk(0x76), match(KeyEvent.KEYCODE_K, sym = true))
    }

    @Test
    fun symE() {
        assertEquals(BindingAction.SendVk(0x79), match(KeyEvent.KEYCODE_E, sym = true))
    }

    @Test
    fun altLettersAreNotBound() {
        // Q25 の Alt+文字 は数字・記号なので、Meltype の割り当てに使わない
        assertNull(match(KeyEvent.KEYCODE_E, KeyEvent.META_ALT_ON))
        assertNull(match(KeyEvent.KEYCODE_K, KeyEvent.META_ALT_ON))
    }
}
```

- [ ] **Step 2: 失敗を確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.horota.meltype.input.KeyBindingsTest'`
Expected: FAIL (`Unresolved reference: KeyBindings`)

- [ ] **Step 3: `KeyBindings.kt` を書く**

```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.input

import android.view.KeyEvent

/** Core に渡す前に Meltype が自分で処理するキー。 */
sealed interface BindingAction {
    /** 英数 (直接入力) ⇔ 日本語。 */
    data object ToggleDirect : BindingAction

    data class SetDirect(val direct: Boolean) : BindingAction

    /** 変換・無変換・かな。入力中なら Core (meltype_role_key) が処理し、そうでなければ SetDirect(fallbackDirect)。 */
    data class RoleKey(val vk: Int, val fallbackDirect: Boolean) : BindingAction

    /** 別のキー (F7 / F10) として Core に渡す。 */
    data class SendVk(val vk: Int) : BindingAction

    /** Meltype を一時停止する / 戻す (停止中はキーをすべてアプリに渡す)。 */
    data object TogglePause : BindingAction
}

data class BindingOptions(val shiftSpaceToggles: Boolean = true, val ctrlSpaceToggles: Boolean = true)

/**
 * 外付けキーボード (JIS / US) と Zinwa Q25 (BlackBerry 配列) の割り当て。両方を同時に有効にする (ぶつからない)。
 * Q25 の実測は docs/q25-keys.md。Alt+文字 は Q25 で数字・記号になるので使わない。
 */
object KeyBindings {
    /** Q25 の Sym キー (docs/q25-keys.md の実測値)。 */
    const val Q25_SYM_KEYCODE = KeyEvent.KEYCODE_SYM

    private const val VK_F7 = 0x76
    private const val VK_F10 = 0x79

    fun match(keyCode: Int, metaState: Int, symHeld: Boolean, options: BindingOptions): BindingAction? {
        val ctrl = metaState and KeyEvent.META_CTRL_ON != 0
        val shift = metaState and KeyEvent.META_SHIFT_ON != 0
        val alt = metaState and KeyEvent.META_ALT_ON != 0
        return when (keyCode) {
            KeyEvent.KEYCODE_ZENKAKU_HANKAKU -> if (ctrl) BindingAction.TogglePause else BindingAction.ToggleDirect
            KeyEvent.KEYCODE_EISU -> BindingAction.SetDirect(true)
            KeyEvent.KEYCODE_HENKAN -> BindingAction.RoleKey(0x1C, fallbackDirect = false)
            KeyEvent.KEYCODE_MUHENKAN -> BindingAction.RoleKey(0x1D, fallbackDirect = true)
            KeyEvent.KEYCODE_KATAKANA_HIRAGANA -> BindingAction.RoleKey(0x15, fallbackDirect = false)
            KeyEvent.KEYCODE_SPACE -> when {
                ctrl && !alt && options.ctrlSpaceToggles -> BindingAction.ToggleDirect
                shift && !ctrl && !alt && options.shiftSpaceToggles -> BindingAction.ToggleDirect
                else -> null
            }
            KeyEvent.KEYCODE_K -> if (symHeld) BindingAction.SendVk(VK_F7) else null
            KeyEvent.KEYCODE_E -> if (symHeld) BindingAction.SendVk(VK_F10) else null
            else -> null
        }
    }
}
```

- [ ] **Step 4: テストが通ることを確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.horota.meltype.input.KeyBindingsTest'`
Expected: PASS (10 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/horota/meltype/input/KeyBindings.kt app/src/test/java/dev/horota/meltype/input/KeyBindingsTest.kt
git commit -m "feat: key bindings for external keyboards and Zinwa Q25

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: EditorPolicy (入力欄の種類ごとの動き)

**Files:**
- Create: `app/src/main/java/dev/horota/meltype/input/EditorPolicy.kt`
- Test: `app/src/test/java/dev/horota/meltype/input/EditorPolicyTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class FieldPolicy(val bypass: Boolean, val startDirect: Boolean, val codeInput: Boolean)
  object EditorPolicy {
      val DEFAULT_CODE_APPS: Set<String>
      fun of(inputType: Int, packageName: String?, codeApps: Set<String>): FieldPolicy
  }
  ```

- [ ] **Step 1: テストを書く (失敗する)**

`app/src/test/java/dev/horota/meltype/input/EditorPolicyTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.input

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorPolicyTest {
    private val codeApps = setOf("com.termux")
    private fun of(inputType: Int, pkg: String? = "com.example") = EditorPolicy.of(inputType, pkg, codeApps)

    @Test
    fun plainText() {
        assertEquals(FieldPolicy(bypass = false, startDirect = false, codeInput = false), of(InputType.TYPE_CLASS_TEXT))
    }

    @Test
    fun passwordsBypass() {
        val bypass = FieldPolicy(bypass = true, startDirect = true, codeInput = false)
        assertEquals(bypass, of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertEquals(bypass, of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertEquals(bypass, of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertEquals(bypass, of(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
    }

    @Test
    fun urlEmailNumberStartDirect() {
        val direct = FieldPolicy(bypass = false, startDirect = true, codeInput = false)
        assertEquals(direct, of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI))
        assertEquals(direct, of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertEquals(direct, of(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS))
        assertEquals(direct, of(InputType.TYPE_CLASS_NUMBER))
        assertEquals(direct, of(InputType.TYPE_CLASS_PHONE))
        assertEquals(direct, of(InputType.TYPE_CLASS_DATETIME))
    }

    @Test
    fun codeAppsUseCodeInput() {
        assertEquals(
            FieldPolicy(bypass = false, startDirect = false, codeInput = true),
            of(InputType.TYPE_CLASS_TEXT, "com.termux"),
        )
    }

    @Test
    fun nullTypeBypassesExceptCodeApps() {
        // Termux は既定で TYPE_NULL。コード入力のアプリなら Meltype を通す
        assertEquals(FieldPolicy(bypass = true, startDirect = true, codeInput = false), of(InputType.TYPE_NULL))
        assertEquals(
            FieldPolicy(bypass = false, startDirect = false, codeInput = true),
            of(InputType.TYPE_NULL, "com.termux"),
        )
    }

    @Test
    fun defaultCodeAppsIncludeTermux() {
        assertEquals(true, "com.termux" in EditorPolicy.DEFAULT_CODE_APPS)
    }
}
```

- [ ] **Step 2: 失敗を確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.horota.meltype.input.EditorPolicyTest'`
Expected: FAIL (`Unresolved reference: EditorPolicy`)

- [ ] **Step 3: `EditorPolicy.kt` を書く**

```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.input

import android.text.InputType

/**
 * 入力欄ごとの動き。
 * bypass: Meltype を通さずキーをすべてアプリに渡す (学習もしない)。
 * startDirect: 英数 (直接入力) から始める。codeInput: 本家のコード入力モード (meltype_set_code_input)。
 */
data class FieldPolicy(val bypass: Boolean, val startDirect: Boolean, val codeInput: Boolean)

object EditorPolicy {
    /** 最初から入れておくコード入力のアプリ (設定画面で変えられる)。 */
    val DEFAULT_CODE_APPS: Set<String> = setOf(
        "com.termux",
        "com.foxdebug.acode",
    )

    private val BYPASS = FieldPolicy(bypass = true, startDirect = true, codeInput = false)

    fun of(inputType: Int, packageName: String?, codeApps: Set<String>): FieldPolicy {
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val codeApp = packageName != null && packageName in codeApps
        if (isPassword(inputClass, variation)) return BYPASS
        if (inputClass == InputType.TYPE_NULL) {
            return if (codeApp) FieldPolicy(bypass = false, startDirect = false, codeInput = true) else BYPASS
        }
        val startDirect = when (inputClass) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> true
            InputType.TYPE_CLASS_TEXT -> variation in DIRECT_TEXT_VARIATIONS
            else -> false
        }
        return FieldPolicy(bypass = false, startDirect = startDirect, codeInput = codeApp)
    }

    private val DIRECT_TEXT_VARIATIONS = setOf(
        InputType.TYPE_TEXT_VARIATION_URI,
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
    )

    private fun isPassword(inputClass: Int, variation: Int): Boolean = when (inputClass) {
        InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else -> false
    }
}
```

- [ ] **Step 4: テストが通ることを確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.horota.meltype.input.EditorPolicyTest'`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/horota/meltype/input/EditorPolicy.kt app/src/test/java/dev/horota/meltype/input/EditorPolicyTest.kt
git commit -m "feat: per-field policy for passwords, URLs and code apps

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: ImeController (キー入力の流れの全体)

**Files:**
- Create: `app/src/main/java/dev/horota/meltype/ime/ImeController.kt`
- Test: `app/src/test/java/dev/horota/meltype/ime/ImeControllerTest.kt`, `app/src/test/java/dev/horota/meltype/ime/FakeCore.kt`

**Interfaces:**
- Consumes: `CoreApi`, `SessionResult` (Task 4), `KeyInput`, `KeyNormalizer` (Task 5), `EditorSink`, `CandidateDisplay`, `ResultApplier`, `FakeEditor` (Task 6), `KeyBindings`, `BindingAction`, `BindingOptions` (Task 7), `FieldPolicy` (Task 8)
- Produces:
  ```kotlin
  enum class InputMode { JAPANESE, DIRECT, PAUSED, UNAVAILABLE }
  interface StatusDisplay { fun showMode(mode: InputMode) }
  class ImeController(core: CoreApi?, sink: EditorSink, display: CandidateDisplay, status: StatusDisplay) {
      var options: BindingOptions
      val mode: InputMode
      fun startInput(policy: FieldPolicy)
      fun finishInput()
      fun keyDown(key: KeyInput): Boolean     // true: Meltype が使った (アプリに渡さない)
      fun keyUp(keyCode: Int): Boolean
      fun selectCandidate(index: Int)
  }
  ```

- [ ] **Step 1: Core の偽物を書く**

`app/src/test/java/dev/horota/meltype/ime/FakeCore.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import dev.horota.meltype.core.CoreApi
import dev.horota.meltype.core.SessionResult

/** Core の偽物。呼ばれた操作を calls に記録し、next / roleResult / commitResult を返す。 */
class FakeCore : CoreApi {
    val calls = mutableListOf<String>()
    var next: SessionResult? = SessionResult(consumed = true)
    var roleResult: SessionResult? = null
    var commitResult: SessionResult? = SessionResult(consumed = true)

    override fun handleKey(vk: Int, ch: Int, modifiers: Int, before: String?, after: String?): SessionResult? {
        calls += "key:$vk:$ch:$modifiers:$before:$after"
        return next
    }
    override fun roleKey(vk: Int): SessionResult? { calls += "role:$vk"; return roleResult }
    override fun commit(): SessionResult? { calls += "commit"; return commitResult }
    override fun selectCandidate(index: Int): SessionResult? { calls += "select:$index"; return next }
    override fun setDirect(direct: Boolean) { calls += "direct:$direct" }
    override fun setCodeInput(enabled: Boolean) { calls += "code:$enabled" }
    override fun setCanDelete(canDelete: Boolean) { calls += "canDelete:$canDelete" }
}
```

- [ ] **Step 2: テストを書く (失敗する)**

`app/src/test/java/dev/horota/meltype/ime/ImeControllerTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.view.KeyEvent
import dev.horota.meltype.core.CompositionView
import dev.horota.meltype.core.SessionResult
import dev.horota.meltype.core.TextEdit
import dev.horota.meltype.input.FieldPolicy
import dev.horota.meltype.input.KeyBindings
import dev.horota.meltype.input.KeyInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeControllerTest {
    private val core = FakeCore()
    private val editor = FakeEditor()
    private val modes = mutableListOf<InputMode>()
    private val status = object : StatusDisplay { override fun showMode(mode: InputMode) { modes += mode } }
    private val controller = ImeController(core, editor, editor, status)
    private val text = FieldPolicy(bypass = false, startDirect = false, codeInput = false)

    private fun letter(c: Char) = KeyInput(KeyEvent.KEYCODE_A + (c - 'a'), c.code, c.code, 0)

    @Test
    fun startInputConfiguresCore() {
        controller.startInput(FieldPolicy(bypass = false, startDirect = true, codeInput = true))
        assertTrue(core.calls.containsAll(listOf("canDelete:true", "code:true", "direct:true")))
        assertEquals(InputMode.DIRECT, modes.last())
    }

    @Test
    fun letterGoesToCoreWithSurroundingText() {
        controller.startInput(text)
        editor.before = "hello "
        editor.after = "!"
        core.calls.clear()
        assertTrue(controller.keyDown(letter('k')))
        assertEquals(listOf("key:75:107:0:hello :!"), core.calls)
    }

    @Test
    fun surroundingTextIsNullWhileComposing() {
        controller.startInput(text)
        core.next = SessionResult(true, emptyList(), CompositionView(text = "k"))
        controller.keyDown(letter('k'))
        core.calls.clear()
        controller.keyDown(letter('y'))
        assertEquals(listOf("key:89:121:0:null:null"), core.calls)
    }

    @Test
    fun notConsumedKeyGoesToAppAfterCommits() {
        controller.startInput(text)
        core.next = SessionResult(false, listOf(TextEdit(0, "abc")), null)
        assertFalse(controller.keyDown(letter('a')))
        assertTrue("commit:abc" in editor.ops)
    }

    @Test
    fun passwordFieldBypassesCore() {
        controller.startInput(FieldPolicy(bypass = true, startDirect = true, codeInput = false))
        core.calls.clear()
        assertFalse(controller.keyDown(letter('a')))
        assertEquals(emptyList<String>(), core.calls)
    }

    @Test
    fun withoutCoreEverythingPassesThrough() {
        val noCore = ImeController(null, editor, editor, status)
        noCore.startInput(text)
        assertFalse(noCore.keyDown(letter('a')))
        assertEquals(InputMode.UNAVAILABLE, modes.last())
    }

    @Test
    fun keyUpOfConsumedKeyIsConsumed() {
        controller.startInput(text)
        controller.keyDown(letter('a'))
        assertTrue(controller.keyUp(KeyEvent.KEYCODE_A))
        assertFalse(controller.keyUp(KeyEvent.KEYCODE_A))
    }

    @Test
    fun keyUpOfPassedKeyIsNotConsumed() {
        controller.startInput(text)
        core.next = SessionResult(consumed = false)
        controller.keyDown(letter('a'))
        assertFalse(controller.keyUp(KeyEvent.KEYCODE_A))
    }

    @Test
    fun toggleCommitsPendingThenSwitches() {
        controller.startInput(text)
        core.calls.clear()
        assertTrue(controller.keyDown(KeyInput(KeyEvent.KEYCODE_ZENKAKU_HANKAKU, 0, 0, 0)))
        assertEquals(listOf("commit", "direct:true"), core.calls)
        assertEquals(InputMode.DIRECT, controller.mode)
    }

    @Test
    fun roleKeyUsesCoreWhileComposing() {
        controller.startInput(text)
        core.roleResult = SessionResult(true, emptyList(), CompositionView(text = "カ"))
        core.calls.clear()
        assertTrue(controller.keyDown(KeyInput(KeyEvent.KEYCODE_HENKAN, 0, 0, 0)))
        assertEquals(listOf("role:28"), core.calls)
    }

    @Test
    fun roleKeyFallsBackToSwitching() {
        controller.startInput(text)
        core.roleResult = null
        core.calls.clear()
        controller.keyDown(KeyInput(KeyEvent.KEYCODE_MUHENKAN, 0, 0, 0))
        assertEquals(listOf("role:29", "commit", "direct:true"), core.calls)
    }

    @Test
    fun symKSendsF7() {
        controller.startInput(text)
        controller.keyDown(KeyInput(KeyBindings.Q25_SYM_KEYCODE, 0, 0, 0))
        core.calls.clear()
        assertTrue(controller.keyDown(KeyInput(KeyEvent.KEYCODE_K, 'k'.code, 'k'.code, 0)))
        assertEquals(listOf("key:118:0:0:null:null"), core.calls)
        controller.keyUp(KeyBindings.Q25_SYM_KEYCODE)
        core.calls.clear()
        controller.keyDown(KeyInput(KeyEvent.KEYCODE_K, 'k'.code, 'k'.code, 0))
        assertEquals(listOf("key:75:107:0::"), core.calls)
    }

    @Test
    fun pauseLetsKeysThroughUntilResumed() {
        controller.startInput(text)
        val pause = KeyInput(KeyEvent.KEYCODE_ZENKAKU_HANKAKU, 0, 0, KeyEvent.META_CTRL_ON)
        assertTrue(controller.keyDown(pause))
        assertEquals(InputMode.PAUSED, controller.mode)
        core.calls.clear()
        assertFalse(controller.keyDown(letter('a')))
        assertEquals(emptyList<String>(), core.calls)
        controller.keyDown(pause)
        assertEquals(InputMode.JAPANESE, controller.mode)
    }

    @Test
    fun finishInputCommitsPending() {
        controller.startInput(text)
        core.commitResult = SessionResult(true, listOf(TextEdit(0, "今日")), null)
        controller.finishInput()
        assertTrue("commit:今日" in editor.ops)
    }

    @Test
    fun selectCandidateAppliesResult() {
        controller.startInput(text)
        core.next = SessionResult(true, listOf(TextEdit(0, "京")), null)
        controller.selectCandidate(1)
        assertTrue("select:1" in core.calls)
        assertTrue("commit:京" in editor.ops)
    }
}
```

- [ ] **Step 3: 失敗を確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.horota.meltype.ime.ImeControllerTest'`
Expected: FAIL (`Unresolved reference: ImeController`)

- [ ] **Step 4: `ImeController.kt` を書く**

```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import dev.horota.meltype.core.CoreApi
import dev.horota.meltype.input.BindingAction
import dev.horota.meltype.input.BindingOptions
import dev.horota.meltype.input.FieldPolicy
import dev.horota.meltype.input.KeyBindings
import dev.horota.meltype.input.KeyInput
import dev.horota.meltype.input.KeyNormalizer

enum class InputMode { JAPANESE, DIRECT, PAUSED, UNAVAILABLE }

/** 英 / 日の状態の表示 (候補バーの左端)。 */
interface StatusDisplay {
    fun showMode(mode: InputMode)
}

/**
 * キー入力の流れ: KeyBindings → KeyNormalizer → Core → ResultApplier。Android に依存しない (JVM でテストする)。
 * core が null なら (読み込めなかった) キーをすべてアプリに渡す。
 */
class ImeController(
    private val core: CoreApi?,
    private val sink: EditorSink,
    display: CandidateDisplay,
    private val status: StatusDisplay,
) {
    private companion object {
        const val SURROUNDING_LENGTH = 64
    }

    private val applier = ResultApplier(sink, display)
    private var policy = FieldPolicy(bypass = false, startDirect = false, codeInput = false)
    /** ユーザーが最後に選んだ英数 / 日本語 (入力欄をまたいで覚える)。 */
    private var userDirect = false
    private var direct = false
    private var paused = false
    private var symHeld = false
    /** Meltype が使った KeyDown。対応する KeyUp もアプリに渡さない。 */
    private val consumedDown = mutableSetOf<Int>()

    var options: BindingOptions = BindingOptions()

    val mode: InputMode
        get() = when {
            core == null -> InputMode.UNAVAILABLE
            paused -> InputMode.PAUSED
            direct -> InputMode.DIRECT
            else -> InputMode.JAPANESE
        }

    fun startInput(policy: FieldPolicy) {
        this.policy = policy
        applier.reset()
        consumedDown.clear()
        core?.setCanDelete(true)
        core?.setCodeInput(policy.codeInput)
        direct = policy.startDirect || userDirect
        core?.setDirect(direct)
        status.showMode(mode)
    }

    fun finishInput() {
        commitPending()
    }

    fun keyDown(key: KeyInput): Boolean {
        if (key.keyCode == KeyBindings.Q25_SYM_KEYCODE) symHeld = true
        val binding = KeyBindings.match(key.keyCode, key.metaState, symHeld, options)
        if (binding == BindingAction.TogglePause) return consume(key.keyCode) { togglePause() }
        val api = core ?: return false
        if (paused || policy.bypass) return false
        if (binding != null) return consume(key.keyCode) { handle(binding, api) }

        val normalized = KeyNormalizer.normalize(key) ?: return false
        val before = if (applier.composing) null else sink.textBeforeCursor(SURROUNDING_LENGTH)
        val after = if (applier.composing) null else sink.textAfterCursor(SURROUNDING_LENGTH)
        val result = api.handleKey(normalized.vk, normalized.ch, normalized.modifiers, before, after) ?: return false
        applier.apply(result)
        if (result.consumed) consumedDown += key.keyCode
        return result.consumed
    }

    fun keyUp(keyCode: Int): Boolean {
        if (keyCode == KeyBindings.Q25_SYM_KEYCODE) symHeld = false
        return consumedDown.remove(keyCode)
    }

    fun selectCandidate(index: Int) {
        core?.selectCandidate(index)?.let(applier::apply)
    }

    private inline fun consume(keyCode: Int, action: () -> Unit): Boolean {
        action()
        consumedDown += keyCode
        return true
    }

    private fun handle(binding: BindingAction, api: CoreApi) {
        when (binding) {
            BindingAction.ToggleDirect -> setDirect(!direct)
            is BindingAction.SetDirect -> setDirect(binding.direct)
            is BindingAction.RoleKey -> {
                val result = if (direct) null else api.roleKey(binding.vk)
                if (result != null) applier.apply(result) else setDirect(binding.fallbackDirect)
            }
            is BindingAction.SendVk -> api.handleKey(binding.vk, 0, 0, null, null)?.let(applier::apply)
            BindingAction.TogglePause -> togglePause()
        }
    }

    private fun setDirect(value: Boolean) {
        commitPending()
        direct = value
        userDirect = value
        core?.setDirect(value)
        status.showMode(mode)
    }

    private fun togglePause() {
        commitPending()
        paused = !paused
        status.showMode(mode)
    }

    private fun commitPending() {
        core?.commit()?.let(applier::apply)
    }
}
```

- [ ] **Step 5: テストが通ることを確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.horota.meltype.ime.ImeControllerTest'`
Expected: PASS (15 tests)

- [ ] **Step 6: 単体テストを全部動かす**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (全部)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/dev/horota/meltype/ime/ImeController.kt app/src/test/java/dev/horota/meltype/ime/ImeControllerTest.kt app/src/test/java/dev/horota/meltype/ime/FakeCore.kt
git commit -m "feat: IME controller wiring bindings, normalizer, core and applier

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: IME サービスと候補バー (端末で動かす)

**Files:**
- Create: `app/src/main/java/dev/horota/meltype/ime/InputConnectionSink.kt`, `CandidateBarView.kt`, `MeltypeImeService.kt`
- Create: `app/src/main/java/dev/horota/meltype/settings/MeltypePrefs.kt`
- Create: `app/src/main/res/xml/method.xml`
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`
- Create: `app/src/debug/AndroidManifest.xml`, `app/src/debug/java/dev/horota/meltype/debug/PlaygroundActivity.kt`
- Create: `tools/e2e.sh`

**Interfaces:**
- Consumes: `CoreBridge.open`, `CoreOpenResult` (Task 4), `KeyInput` (Task 5), `EditorSink`, `CandidateDisplay` (Task 6), `BindingOptions` (Task 7), `EditorPolicy` (Task 8), `ImeController`, `StatusDisplay`, `InputMode` (Task 9)
- Produces:
  ```kotlin
  class MeltypePrefs(context: Context) {
      var codeApps: Set<String>          // 既定は EditorPolicy.DEFAULT_CODE_APPS
      var shiftSpaceToggles: Boolean     // 既定 true
      var ctrlSpaceToggles: Boolean      // 既定 true
      val bindingOptions: BindingOptions
      companion object { fun dataDirectory(context: Context): File }  // <filesDir>/Meltype
  }
  ```

- [ ] **Step 1: `MeltypePrefs.kt` を書く**

```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import android.content.Context
import dev.horota.meltype.input.BindingOptions
import dev.horota.meltype.input.EditorPolicy
import java.io.File

/** Android 版だけの設定 (SharedPreferences)。判定・変換の細かい設定は本家と同じ config.json にある。 */
class MeltypePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("meltype", Context.MODE_PRIVATE)

    var codeApps: Set<String>
        get() = prefs.getStringSet(KEY_CODE_APPS, null)?.toSet() ?: EditorPolicy.DEFAULT_CODE_APPS
        set(value) = prefs.edit().putStringSet(KEY_CODE_APPS, value).apply()

    var shiftSpaceToggles: Boolean
        get() = prefs.getBoolean(KEY_SHIFT_SPACE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHIFT_SPACE, value).apply()

    var ctrlSpaceToggles: Boolean
        get() = prefs.getBoolean(KEY_CTRL_SPACE, true)
        set(value) = prefs.edit().putBoolean(KEY_CTRL_SPACE, value).apply()

    val bindingOptions: BindingOptions
        get() = BindingOptions(shiftSpaceToggles = shiftSpaceToggles, ctrlSpaceToggles = ctrlSpaceToggles)

    companion object {
        private const val KEY_CODE_APPS = "code_apps"
        private const val KEY_SHIFT_SPACE = "shift_space_toggles"
        private const val KEY_CTRL_SPACE = "ctrl_space_toggles"

        /** 本家の MELTYPE_DATA_DIR に渡す場所 (設定・学習・ユーザー辞書・ログ)。 */
        fun dataDirectory(context: Context): File = File(context.filesDir, "Meltype")
    }
}
```

- [ ] **Step 2: `InputConnectionSink.kt` を書く**

```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.view.inputmethod.InputConnection

/** EditorSink の本物。InputConnection は入力欄が変わるたびに変わるので、毎回取り直す。 */
class InputConnectionSink(private val connection: () -> InputConnection?) : EditorSink {
    override fun beginBatch() {
        connection()?.beginBatchEdit()
    }

    override fun endBatch() {
        connection()?.endBatchEdit()
    }

    override fun textBeforeCursor(length: Int): String? = connection()?.getTextBeforeCursor(length, 0)?.toString()

    override fun textAfterCursor(length: Int): String? = connection()?.getTextAfterCursor(length, 0)?.toString()

    override fun deleteBefore(length: Int) {
        connection()?.deleteSurroundingText(length, 0)
    }

    override fun commit(text: String) {
        connection()?.commitText(text, 1)
    }

    override fun setComposing(text: String) {
        connection()?.setComposingText(text, 1)
    }

    override fun finishComposing() {
        connection()?.finishComposingText()
    }
}
```

- [ ] **Step 3: `CandidateBarView.kt` を書く**

```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import dev.horota.meltype.core.CompositionView

/**
 * 1 行の候補バー (Q25 の 720×720 の画面でも邪魔にならない高さ)。左端に英 / 日の状態、右に候補。
 * 候補をタップすると onSelect(index)。
 */
class CandidateBarView(context: Context, private val onSelect: (Int) -> Unit) :
    LinearLayout(context), CandidateDisplay, StatusDisplay {

    private val modeLabel = label().apply { setTypeface(typeface, Typeface.BOLD) }
    private val warningLabel = label().apply { setTextColor(Color.rgb(0xC6, 0x28, 0x28)) }
    private val list = LinearLayout(context).apply { orientation = HORIZONTAL }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.rgb(0xF2, 0xF2, 0xF2))
        minimumHeight = dp(36)
        addView(modeLabel)
        addView(warningLabel)
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(list)
        })
    }

    override fun show(view: CompositionView) {
        list.removeAllViews()
        val items = view.candidates.ifEmpty { view.predictions }
        val selected = if (view.candidates.isNotEmpty()) view.selectedIndex else view.selectedPrediction
        items.forEachIndexed { index, text ->
            list.addView(label().apply {
                this.text = text
                if (index == selected) setBackgroundColor(Color.rgb(0x4C, 0xA0, 0xFF))
                if (view.candidates.isNotEmpty()) setOnClickListener { onSelect(index) }
            })
        }
    }

    override fun hide() {
        list.removeAllViews()
    }

    override fun showMode(mode: InputMode) {
        modeLabel.text = when (mode) {
            InputMode.JAPANESE -> "あ"
            InputMode.DIRECT -> "A"
            InputMode.PAUSED -> "⏸"
            InputMode.UNAVAILABLE -> "⚠"
        }
    }

    /** null で消す。 */
    fun setWarning(text: String?) {
        warningLabel.text = text.orEmpty()
    }

    private fun label() = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(Color.BLACK)
        setPadding(dp(10), dp(4), dp(10), dp(4))
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
```

- [ ] **Step 4: `MeltypeImeService.kt` を書く**

```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import dev.horota.meltype.core.CoreBridge
import dev.horota.meltype.core.CoreOpenResult
import dev.horota.meltype.input.EditorPolicy
import dev.horota.meltype.input.KeyInput
import dev.horota.meltype.settings.MeltypePrefs
import java.io.File

/** v0.1: 物理キーボード専用。画面キーボードは出さず、候補バーだけ出す。 */
class MeltypeImeService : InputMethodService() {
    private lateinit var prefs: MeltypePrefs
    private lateinit var bar: CandidateBarView
    private lateinit var controller: ImeController
    private var core: CoreBridge? = null

    override fun onCreate() {
        super.onCreate()
        prefs = MeltypePrefs(this)
        bar = CandidateBarView(this) { index -> controller.selectCandidate(index) }
        val opened = CoreBridge.open(
            MeltypePrefs.dataDirectory(this),
            File(applicationInfo.nativeLibraryDir, "libmozc_helper.so"),
        )
        when (opened) {
            is CoreOpenResult.Ready -> {
                core = opened.core
                bar.setWarning(if (opened.mozcAvailable) null else "漢字変換を使えません")
            }
            is CoreOpenResult.Failed -> bar.setWarning("Meltype を初期化できませんでした")
        }
        controller = ImeController(core, InputConnectionSink { currentInputConnection }, bar, bar)
    }

    override fun onCreateCandidatesView(): View {
        (bar.parent as? ViewGroup)?.removeView(bar)
        return bar
    }

    override fun onEvaluateInputViewShown(): Boolean = false

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        controller.options = prefs.bindingOptions
        controller.startInput(EditorPolicy.of(attribute.inputType, attribute.packageName, prefs.codeApps))
        setCandidatesViewShown(true)
    }

    override fun onFinishInput() {
        controller.finishInput()
        super.onFinishInput()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val key = KeyInput(keyCode, event.getUnicodeChar(event.metaState), event.getUnicodeChar(0), event.metaState)
        return controller.keyDown(key) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        controller.keyUp(keyCode) || super.onKeyUp(keyCode, event)
}
```

- [ ] **Step 5: `method.xml` と manifest と strings を書く**

`app/src/main/res/xml/method.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<input-method xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- 物理キーボードの配列 (JIS / US) は、この subtype ごとに Android の設定で選べる -->
    <subtype
        android:label="@string/subtype_ja"
        android:imeSubtypeLocale="ja_JP"
        android:languageTag="ja-JP"
        android:imeSubtypeMode="keyboard" />
</input-method>
```

`app/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Meltype</string>
    <string name="ime_name">Meltype</string>
    <string name="subtype_ja">日本語 (Meltype)</string>
</resources>
```

`app/src/main/AndroidManifest.xml` の `<application>` の中に足す:
```xml
        <service
            android:name=".ime.MeltypeImeService"
            android:exported="true"
            android:label="@string/ime_name"
            android:permission="android.permission.BIND_INPUT_METHOD">
            <intent-filter>
                <action android:name="android.view.InputMethod" />
            </intent-filter>
            <meta-data
                android:name="android.view.im"
                android:resource="@xml/method" />
        </service>
```

- [ ] **Step 6: 結合テスト用の入力欄 (debug だけ) を書く**

`app/src/debug/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity android:name=".debug.PlaygroundActivity" android:exported="true" />
    </application>
</manifest>
```

`app/src/debug/java/dev/horota/meltype/debug/PlaygroundActivity.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.debug

import android.app.Activity
import android.os.Bundle
import android.widget.EditText

/** tools/e2e.sh 用の入力欄 (debug ビルドだけ)。 */
class PlaygroundActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(EditText(this).apply {
            id = PLAYGROUND_ID
            contentDescription = "playground"
            requestFocus()
        })
    }

    private companion object {
        const val PLAYGROUND_ID = 0x7f0f0001
    }
}
```

- [ ] **Step 7: `tools/e2e.sh` を書く**

```bash
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
```

```bash
chmod +x tools/e2e.sh
```

- [ ] **Step 8: 単体テストとビルドを確かめる**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: 結合テストを動かす (エミュレータまたは Q25)**

```bash
tools/e2e.sh
```
Expected: `OK: 今日はgoogleで検索`

Mozc が落ちても入力が止まらないことを確かめる: `adb shell run-as dev.horota.meltype pkill -f libmozc_helper` の後に `adb shell input text abc` → 入力欄に文字が入る (その後の変換で本家 MozcConverter がヘルパーを起動し直す)。

あわせて目で確かめる: 入力欄の下 (または上) に候補バーが出て、左端が「あ」になっている。Space を押したところで候補が並ぶ。候補バーが出ない場合は、`onStartInput` の `setCandidatesViewShown(true)` の後に `showWindow(false)` を足して試す (物理キーボードだけのときに IME のウィンドウが出ない端末があるため)。

- [ ] **Step 10: Commit**

```bash
git add app/src/main app/src/debug tools/e2e.sh
git commit -m "feat: hardware-keyboard IME service with candidate bar

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: 設定画面 (案内・キー割り当て・コード入力アプリ・学習データ・ユーザー辞書)

**Files:**
- Create: `app/src/main/java/dev/horota/meltype/settings/CodeAppList.kt`, `LearningData.kt`, `UserDictionaryText.kt`, `SettingsActivity.kt`, `UserDictionaryActivity.kt`
- Modify: `app/src/main/java/dev/horota/meltype/core/CoreBridge.kt` (`open` の最初で `LearningData.applyPendingClear` を呼ぶ)
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/method.xml`
- Test: `app/src/test/java/dev/horota/meltype/settings/CodeAppListTest.kt`, `LearningDataTest.kt`, `UserDictionaryTextTest.kt`

**Interfaces:**
- Consumes: `MeltypePrefs` (Task 10), `EditorPolicy.DEFAULT_CODE_APPS` (Task 8), `CoreBridge.open` (Task 4)
- Produces:
  ```kotlin
  object CodeAppList { fun parse(text: String): Set<String>; fun format(apps: Set<String>): String }
  object LearningData {
      fun requestClear(dataDir: File)              // 印を置く (次に Core を開くときに消す)
      fun applyPendingClear(dataDir: File): Boolean // 印があれば学習データを消して true
  }
  object UserDictionaryText { fun invalidLines(text: String): List<Int> }  // 1 始まりの行番号
  ```

学習データは次に Core を開くときに消す。Mozc のヘルパーは標準入力が閉じたときに学習を保存する (`upstream/native/mozc/meltype_mozc_helper.cc` の最後の `helper.Save()`) ので、動いている間に消しても書き戻されるため。

- [ ] **Step 1: テストを書く (失敗する)**

`app/src/test/java/dev/horota/meltype/settings/CodeAppListTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeAppListTest {
    @Test
    fun parsesOnePackagePerLine() {
        assertEquals(setOf("com.termux", "com.foxdebug.acode"), CodeAppList.parse(" com.termux \n\ncom.foxdebug.acode\n"))
    }

    @Test
    fun ignoresInvalidNames() {
        assertEquals(setOf("com.termux"), CodeAppList.parse("com.termux\nnot a package\n日本語"))
    }

    @Test
    fun formatsSorted() {
        assertEquals("a.b\nc.d", CodeAppList.format(setOf("c.d", "a.b")))
    }
}
```

`app/src/test/java/dev/horota/meltype/settings/LearningDataTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LearningDataTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun clearsLearningButKeepsUserFiles() {
        val dir = temp.newFolder("Meltype")
        val learning = listOf("model.json", "conversions.json", "translations.json", "languages.json", "phrases.txt")
        learning.forEach { File(dir, it).writeText("x") }
        File(dir, "mozc").mkdirs()
        File(dir, "mozc/user.db").writeText("x")
        val kept = listOf("config.json", "userdict.txt", "snippets.json")
        kept.forEach { File(dir, it).writeText("x") }

        LearningData.requestClear(dir)
        assertTrue(LearningData.applyPendingClear(dir))

        learning.forEach { assertFalse(it, File(dir, it).exists()) }
        assertFalse(File(dir, "mozc").exists())
        kept.forEach { assertTrue(it, File(dir, it).exists()) }
        assertFalse("2 回目は何もしない", LearningData.applyPendingClear(dir))
    }

    @Test
    fun nothingPending() {
        assertFalse(LearningData.applyPendingClear(temp.newFolder("Meltype")))
    }
}
```

`app/src/test/java/dev/horota/meltype/settings/UserDictionaryTextTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class UserDictionaryTextTest {
    @Test
    fun validLines() {
        assertEquals(emptyList<Int>(), UserDictionaryText.invalidLines("めるたいぷ\tMeltype\n# コメント\n!メモ\n\nきごうとう\t記号等"))
    }

    @Test
    fun reportsLinesWithoutTabOrWithEmptySide() {
        assertEquals(listOf(1, 2, 3), UserDictionaryText.invalidLines("めるたいぷ Meltype\n\tMeltype\nめるたいぷ\t"))
    }
}
```

- [ ] **Step 2: 失敗を確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.horota.meltype.settings.*'`
Expected: FAIL (`Unresolved reference: CodeAppList` など)

- [ ] **Step 3: 3 つのクラスを書く**

`app/src/main/java/dev/horota/meltype/settings/CodeAppList.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

/** 設定画面の「コード入力モードにするアプリ」(1 行に 1 つのパッケージ名)。 */
object CodeAppList {
    private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    fun parse(text: String): Set<String> =
        text.lines().map { it.trim() }.filter { PACKAGE_NAME.matches(it) }.toSet()

    fun format(apps: Set<String>): String = apps.sorted().joinToString("\n")
}
```

`app/src/main/java/dev/horota/meltype/settings/LearningData.kt`:
```kotlin
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
```

`app/src/main/java/dev/horota/meltype/settings/UserDictionaryText.kt`:
```kotlin
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
```

- [ ] **Step 4: テストが通ることを確かめる**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.horota.meltype.settings.*'`
Expected: PASS (7 tests)

- [ ] **Step 5: `CoreBridge.open` で消す印を処理する**

`app/src/main/java/dev/horota/meltype/core/CoreBridge.kt` の `open` の最初 (`if (loaded == null)` の前) に足す:
```kotlin
            if (LearningData.applyPendingClear(dataDir)) Log.i(TAG, "学習データを消しました")
```
import を足す:
```kotlin
import dev.horota.meltype.settings.LearningData
```

- [ ] **Step 6: `SettingsActivity.kt` を書く**

```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** v0.1 の最小限の設定。細かい設定は本家と同じ config.json (<filesDir>/Meltype) にある。 */
class SettingsActivity : Activity() {
    private lateinit var prefs: MeltypePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = MeltypePrefs(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        root.addView(heading("はじめに"))
        root.addView(button("1. Meltype を有効にする") { open(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        root.addView(button("2. Meltype を選ぶ") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() })
        root.addView(button("3. 物理キーボードの配列 (JIS キーボードなら「日本語」)") {
            open(Intent(Settings.ACTION_HARD_KEYBOARD_SETTINGS))
        })

        root.addView(heading("英 / 日の切り替え"))
        root.addView(TextView(this).apply { text = "半角/全角キーはいつでも使えます。Ctrl+半角/全角 で一時停止します。" })
        root.addView(switch("Shift+Space で切り替える (Q25 向け)", prefs.shiftSpaceToggles) { prefs.shiftSpaceToggles = it })
        root.addView(switch("Ctrl+Space で切り替える", prefs.ctrlSpaceToggles) { prefs.ctrlSpaceToggles = it })

        root.addView(heading("コード入力モードにするアプリ (1 行に 1 つ)"))
        val codeApps = EditText(this).apply { setText(CodeAppList.format(prefs.codeApps)); minLines = 3 }
        root.addView(codeApps)
        root.addView(button("保存") {
            prefs.codeApps = CodeAppList.parse(codeApps.text.toString())
            codeApps.setText(CodeAppList.format(prefs.codeApps))
            Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show()
        })

        root.addView(heading("辞書と学習"))
        root.addView(button("ユーザー辞書を編集する") { startActivity(Intent(this, UserDictionaryActivity::class.java)) })
        root.addView(button("学習データを消す") { confirmClearLearning() })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun confirmClearLearning() {
        AlertDialog.Builder(this)
            .setMessage("覚えた変換・英語 / 日本語の判定を消します (ユーザー辞書と設定は残ります)。Meltype を再起動します。")
            .setPositiveButton("消す") { _, _ ->
                LearningData.requestClear(MeltypePrefs.dataDirectory(this))
                // 同じプロセスの IME ごと終了する。次に IME が起動したとき (CoreBridge.open) に消える。
                Process.killProcess(Process.myPid())
            }
            .setNegativeButton("やめる", null)
            .show()
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
    }

    private fun heading(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setPadding(0, 32, 0, 8)
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun switch(text: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        this.text = text
        isChecked = checked
        setOnCheckedChangeListener { _, value -> onChange(value) }
    }
}
```

- [ ] **Step 7: `UserDictionaryActivity.kt` を書く**

```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.settings

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** 本家の userdict.txt (「読み[Tab]単語」) をそのまま編集する。反映は次に Meltype が起動したとき。 */
class UserDictionaryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = File(MeltypePrefs.dataDirectory(this), "userdict.txt")
        val editor = EditText(this).apply {
            setText(if (file.exists()) file.readText() else "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 10
        }
        val save = Button(this).apply {
            text = "保存"
            setOnClickListener {
                val text = editor.text.toString()
                val invalid = UserDictionaryText.invalidLines(text)
                if (invalid.isNotEmpty()) {
                    Toast.makeText(context, "形式が違う行: ${invalid.joinToString()} (読み[Tab]単語)", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                file.parentFile?.mkdirs()
                file.writeText(text)
                Toast.makeText(context, "保存しました。次に Meltype が起動したときに反映されます", Toast.LENGTH_LONG).show()
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            addView(TextView(context).apply { text = "1 行に「読み[Tab]単語」。# と ! で始まる行はコメント。" })
            addView(editor)
            addView(save)
        })
    }
}
```

- [ ] **Step 8: manifest と method.xml に設定画面をつなぐ**

`app/src/main/AndroidManifest.xml` の `<application>` の中に足す:
```xml
        <activity
            android:name=".settings.SettingsActivity"
            android:exported="true"
            android:label="@string/app_name">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <activity android:name=".settings.UserDictionaryActivity" android:exported="false" />
```

`app/src/main/res/xml/method.xml` の `<input-method` に属性を足す:
```xml
<input-method xmlns:android="http://schemas.android.com/apk/res/android"
    android:settingsActivity="dev.horota.meltype.settings.SettingsActivity">
```

- [ ] **Step 9: テストとビルドを確かめる**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug && tools/e2e.sh`
Expected: BUILD SUCCESSFUL、`OK: 今日はgoogleで検索`

目で確かめる: ランチャーの「Meltype」から設定画面が開く。「学習データを消す」→「消す」で設定画面が閉じ、もう一度入力すると動く (`adb shell run-as dev.horota.meltype ls files/Meltype` で `model.json` と `mozc` が消えている)。

- [ ] **Step 10: Commit**

```bash
git add app/src
git commit -m "feat: settings screen, user dictionary editor and learning reset

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: CI・リリース・ライセンス表記・Q25 チェックリスト

**Files:**
- Create: `.github/workflows/build.yml`, `THIRD-PARTY-NOTICES.md`, `docs/q25-checklist.md`
- Modify: `app/build.gradle.kts` (リリースの署名), `README.md` (使い方)

**Interfaces:**
- Consumes: `docker/Dockerfile`, `core-native/build.sh`, `mozc/build.sh` (Task 1, 2)、`build/notices/*` (Task 2)
- Produces: `v*` のタグを push すると、GitHub Releases に署名済みの `meltype-<版>.apk` が置かれる。

- [ ] **Step 1: リリースの署名を環境変数から読む**

`app/build.gradle.kts` の `android { }` の中、`defaultConfig` の後に足す:
```kotlin
    signingConfigs {
        create("release") {
            val keystore = System.getenv("MELTYPE_KEYSTORE")
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("MELTYPE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MELTYPE_KEY_ALIAS")
                keyPassword = System.getenv("MELTYPE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("MELTYPE_KEYSTORE") != null) signingConfig = signingConfigs.getByName("release")
        }
    }
```

- [ ] **Step 2: `.github/workflows/build.yml` を書く**

```yaml
# SPDX-License-Identifier: GPL-3.0-or-later
name: build

on:
  push:
    branches: [main]
    tags: ["v*"]
  pull_request:

jobs:
  native:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive
      - name: Build image
        run: docker build --platform linux/amd64 -t meltype-build docker
      - name: Cache Bazel
        uses: actions/cache@v4
        with:
          path: build/bazel-cache
          key: bazel-${{ hashFiles('upstream/native/mozc/MOZC_COMMIT') }}
      - name: Core (NativeAOT)
        run: docker/run.sh core-native/build.sh
      - name: Mozc helper
        run: docker/run.sh env MOZC_BAZEL_FLAGS="--disk_cache=/work/build/bazel-cache ${MOZC_BAZEL_FLAGS:-}" mozc/build.sh
      - uses: actions/upload-artifact@v4
        with:
          name: native
          path: |
            build/jniLibs
            build/notices

  app:
    needs: native
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive
      - uses: actions/download-artifact@v4
        with:
          name: native
          path: build
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - uses: android-actions/setup-android@v3
      - run: sdkmanager "ndk;27.1.12297006"
      - name: Unit tests
        run: ./gradlew :app:testDebugUnitTest
      - name: Decode keystore
        if: startsWith(github.ref, 'refs/tags/v')
        run: echo "${{ secrets.MELTYPE_KEYSTORE_BASE64 }}" | base64 -d > "$RUNNER_TEMP/release.jks"
      - name: Build APK
        env:
          MELTYPE_KEYSTORE: ${{ startsWith(github.ref, 'refs/tags/v') && format('{0}/release.jks', runner.temp) || '' }}
          MELTYPE_KEYSTORE_PASSWORD: ${{ secrets.MELTYPE_KEYSTORE_PASSWORD }}
          MELTYPE_KEY_ALIAS: ${{ secrets.MELTYPE_KEY_ALIAS }}
          MELTYPE_KEY_PASSWORD: ${{ secrets.MELTYPE_KEY_PASSWORD }}
        run: |
          if [ -z "$MELTYPE_KEYSTORE" ]; then unset MELTYPE_KEYSTORE; fi
          ./gradlew :app:assembleRelease
      - name: Release
        if: startsWith(github.ref, 'refs/tags/v')
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          cp app/build/outputs/apk/release/app-release.apk "meltype-${GITHUB_REF_NAME}.apk"
          gh release create "$GITHUB_REF_NAME" "meltype-${GITHUB_REF_NAME}.apk" --draft --title "Meltype for Android $GITHUB_REF_NAME"
```

注意: `docker/run.sh` は `meltype-bazel` ボリュームを使うが、CI では `--disk_cache` でリポジトリの中 (`build/bazel-cache`) に置いてキャッシュする。Task 2 で `MOZC_BAZEL_FLAGS` に platform の指定が要った場合は、この `Mozc helper` の行の `${MOZC_BAZEL_FLAGS:-}` をその指定に置き換える。

- [ ] **Step 3: `THIRD-PARTY-NOTICES.md` を書く**

```markdown
# Third-party notices

Meltype for Android は GPL-3.0-or-later です。次のものを含みます。

## Meltype (本家)
- https://github.com/yksr-melt/Meltype — Copyright (C) 2026 雪代 / Yukishiro — GPL-3.0-or-later
- 本家が同梱しているもの (辞書のデータなど) のライセンスは `upstream/THIRD-PARTY-NOTICES.md` を見てください。APK にも同じ内容を含みます。

## Mozc
- https://github.com/google/mozc — Copyright Google LLC — BSD-3-Clause
- ライセンス全文: `build/notices/MOZC-LICENSE.txt` (ビルド時に Mozc のソースから取り出す)
- Mozc が使っているもののクレジット: `build/notices/MOZC-CREDITS.html`

## .NET ランタイム (NativeAOT)
- https://github.com/dotnet/runtime — Copyright .NET Foundation and Contributors — MIT
```

- [ ] **Step 4: `docs/q25-checklist.md` を書く**

```markdown
# Zinwa Q25 実機チェックリスト (リリース前に毎回)

版: ______ 日付: ______ Android: ______

- [ ] 設定画面の「はじめに」1〜3 で、Meltype を有効にして選べる
- [ ] メモアプリで `kyouhagoogledekensaku` → Space → Enter で「今日はgoogleで検索」
- [ ] 入力中にトラックパッドを上下に動かすと候補が移り、押すと確定する
- [ ] 候補バーの候補をタップして確定できる
- [ ] Shift+Space で「あ」⇔「A」が切り替わる
- [ ] Alt+文字 で数字・記号が打てる (`3ji` を Alt+E… で打って「3時」になる)
- [ ] Sym+K / Sym+E で入力中の文字がカタカナ / 英数になる (docs/q25-keys.md で Sym を使うと決めた場合)
- [ ] パスワード欄では変換されない (候補バーが出ない、学習しない)
- [ ] URL 欄では「A」から始まる
- [ ] Termux で英語のコマンドがそのまま打てて、`# めも` のようなコメントでは日本語になる
- [ ] 入力中にホームに戻っても、打った文字が消えずに確定される
- [ ] 外付けキーボード (Bluetooth) をつないでも同じように動く。JIS 配列なら半角/全角キーで切り替わる
- [ ] 機内モードでも動く (通信しない)
```

- [ ] **Step 5: `README.md` に使い方を足す**

`## ビルド` の前に足す:
```markdown
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
```

- [ ] **Step 6: CI を手元で一部確かめる**

```bash
./gradlew :app:testDebugUnitTest :app:assembleRelease
ls app/build/outputs/apk/release/
```
Expected: BUILD SUCCESSFUL、`app-release-unsigned.apk` (手元では署名の環境変数がないので署名なし)

- [ ] **Step 7: Commit**

```bash
git add .github THIRD-PARTY-NOTICES.md docs/q25-checklist.md README.md app/build.gradle.kts
git commit -m "ci: native + app build, signed release, notices and Q25 checklist

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
