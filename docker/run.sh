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
