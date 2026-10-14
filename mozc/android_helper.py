# SPDX-License-Identifier: GPL-3.0-or-later
"""本家の Mozc 変換ヘルパーを Android 向けに書き換えたコピーを作る (本家のファイルは変えない)。

Mozc の Android 向けの設定 (oss_android) では、辞書データを実行ファイルに埋め込めない
(Mozc の Android 版は mozc.data をファイルから読む)。そこで EngineFactory::Create() の代わりに、
環境変数 MELTYPE_MOZC_DATA の mozc.data から Engine を作る。

    python3 android_helper.py <本家の meltype_mozc_helper.cc> <本家の BUILD.fragment> <出力 .cc> <出力 BUILD 断片>
"""
import sys

ENGINE_FACTORY_INCLUDE = '#include "engine/engine_factory.h"\n'
ENGINE_FACTORY_CALL = "  auto engine = mozc::EngineFactory::Create();\n"
FROM_FILE = """  // Meltype for Android: 辞書データ (mozc.data) をファイルから読む (Mozc の Android 版と同じ)。
  const char* data_path = std::getenv("MELTYPE_MOZC_DATA");
  if (data_path == nullptr) {
    std::cout << "ERROR MELTYPE_MOZC_DATA is not set" << std::endl;
    return 1;
  }
  auto data_manager = mozc::DataManager::CreateFromFile(data_path);
  if (!data_manager.ok()) {
    std::cout << "ERROR " << data_manager.status().message() << std::endl;
    return 1;
  }
  auto engine = mozc::Engine::CreateEngine(*std::move(data_manager));
"""


def replace_once(text: str, old: str, new: str, what: str) -> str:
    if text.count(old) != 1:
        sys.exit(f"本家の {what} が想定と違います (見つからない、または複数): {old.strip()}")
    return text.replace(old, new)


def main() -> None:
    source, fragment, out_source, out_fragment = sys.argv[1:5]
    with open(source, encoding="utf-8") as f:
        code = f.read()
    code = replace_once(code, ENGINE_FACTORY_INCLUDE,
                        '#include <cstdlib>\n\n#include "data_manager/data_manager.h"\n', "meltype_mozc_helper.cc")
    code = replace_once(code, ENGINE_FACTORY_CALL, FROM_FILE, "meltype_mozc_helper.cc")
    with open(out_source, "w", encoding="utf-8") as f:
        f.write(code)

    with open(fragment, encoding="utf-8") as f:
        build = f.read()
    build = replace_once(build, '"//engine:engine_factory",', '"//data_manager",', "BUILD.fragment")
    # src/meltype から参照するので、見えるようにする (converter の既定は private)
    build = replace_once(build, "mozc_cc_binary(\n", 'mozc_cc_binary(\n    visibility = ["//meltype:__pkg__"],\n',
                         "BUILD.fragment")
    with open(out_fragment, "w", encoding="utf-8") as f:
        f.write(build)


if __name__ == "__main__":
    main()
