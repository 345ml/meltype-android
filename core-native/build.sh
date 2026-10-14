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
