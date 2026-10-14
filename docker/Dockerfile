# SPDX-License-Identifier: GPL-3.0-or-later
# ネイティブのビルド用 (NativeAOT の Core と Mozc のヘルパー)。NDK の Linux 版は x86_64 だけなので linux/amd64 で動かす。
FROM --platform=linux/amd64 mcr.microsoft.com/dotnet/sdk:10.0-noble

RUN apt-get update \
 && apt-get install -y --no-install-recommends unzip curl git python3 patchelf file ca-certificates clang lld build-essential \
 && rm -rf /var/lib/apt/lists/*

ARG NDK_VERSION=r27b
RUN curl -fsSL -o /tmp/ndk.zip "https://dl.google.com/android/repository/android-ndk-${NDK_VERSION}-linux.zip" \
 && unzip -q /tmp/ndk.zip -d /opt \
 && rm /tmp/ndk.zip
ENV ANDROID_NDK_HOME=/opt/android-ndk-${NDK_VERSION}
ENV ANDROID_NDK_ROOT=/opt/android-ndk-${NDK_VERSION}

RUN curl -fsSL -o /usr/local/bin/bazel https://github.com/bazelbuild/bazelisk/releases/download/v1.25.0/bazelisk-linux-amd64 \
 && chmod +x /usr/local/bin/bazel
