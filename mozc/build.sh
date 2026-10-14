#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# 本家の Mozc 変換ヘルパー (upstream/native/mozc/meltype_mozc_helper.cc) を Android arm64 向けにビルドする。
# docker/run.sh mozc/build.sh で実行する。本家 native/mozc/build-mozc-helper.sh の Android 版。
# 出力: build/jniLibs/arm64-v8a/libmozc_helper.so (中身は実行ファイル。Android はアプリの
#       ネイティブライブラリの置き場にある lib*.so なら実行できるので、この名前で APK に入れる)
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
helper="$root/upstream/native/mozc"
# Mozc のソースの場所。docker/run.sh では名前付きボリューム (/mozc)。
mozc="${MOZC_SRC:-$root/build/mozc-src}"
out="$root/build/jniLibs/arm64-v8a"
notices="$root/build/notices"
commit="$(tr -d '[:space:]' < "$helper/MOZC_COMMIT")"

if [[ ! -d "$mozc/.git" ]]; then
    mkdir -p "$mozc"
    git -C "$mozc" init -q
    git -C "$mozc" remote add origin https://github.com/google/mozc.git
    git -C "$mozc" fetch -q --depth 1 origin "$commit"
    git -C "$mozc" checkout -q FETCH_HEAD
fi
git -C "$mozc" submodule update -q --init --recursive --depth 1
src="$mozc/src"

# Mozc が使う NDK (MODULE.bazel の third_party/ndk/android-ndk-r29) を取ってくる
if [[ ! -d "$src/third_party/ndk" ]]; then
    (cd "$src" && python3 build_tools/update_deps.py)
fi

# Android 向けに書き換えたヘルパー (辞書データをファイルから読む) を Mozc の converter パッケージに入れる
python3 "$root/mozc/android_helper.py" "$helper/meltype_mozc_helper.cc" "$helper/BUILD.fragment" \
    "$src/converter/meltype_mozc_helper.cc" /tmp/meltype-fragment
git -C "$mozc" checkout -q -- src/converter/BUILD.bazel
printf '\n%s\n' "$(cat /tmp/meltype-fragment)" >> "$src/converter/BUILD.bazel"
mkdir -p "$src/meltype"
cp -f "$root/mozc/BUILD.bazel.in" "$src/meltype/BUILD.bazel"

cd "$src"
# 辞書データ (mozc.data) はホスト向けの設定で作る (形式は CPU に依存しない。oss_android の設定ではビルド用の道具が作れない)
bazel build //data_manager/oss:mozc_dataset_for_oss --config release_build ${MOZC_BAZEL_FLAGS:-}
# bazel-bin は最後のビルドの設定を指すので、すぐに取っておく
cp -f bazel-bin/data_manager/oss/mozc.data /tmp/meltype-mozc.data
# ヘルパーは Android 向けの設定で、対象だけ arm64 に切り替えてビルドする。16KB のページサイズ (Android 15 以降の要件) にも合わせる。
android=(--config oss_android --config release_build --linkopt=-Wl,-z,max-page-size=16384 ${MOZC_BAZEL_FLAGS:-})
bazel build //meltype:meltype_mozc_helper.arm64 "${android[@]}"
binary="$(bazel cquery //meltype:meltype_mozc_helper.arm64 "${android[@]}" --output=files 2> /dev/null | head -1)"

mkdir -p "$out" "$notices"
cp -f "$binary" "$out/libmozc_helper.so"
chmod u+w,a+x "$out/libmozc_helper.so"
# 辞書データも lib*.so の名前で入れる (APK から展開されてファイルとして読めるようにするため)
cp -f /tmp/meltype-mozc.data "$out/libmozc_data.so"
chmod u+w "$out/libmozc_data.so"
cp -f data/installer/credits_en.html "$notices/MOZC-CREDITS.html"
cp -f "$mozc/LICENSE" "$notices/MOZC-LICENSE.txt"
echo "作成しました: $out/libmozc_helper.so"
