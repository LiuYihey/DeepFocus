#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""校验 StickerCatalog.kt 里写的表情包 id 与 assets/stickers/ 实际文件是否一一对应。

不一致会导致「模型选了个不存在的表情包 → 静默不显示」。
所以这个脚本要在每次改 Catalog 后跑一遍。

表情包与大表情已统一到 assets/stickers/（大表情命名 st_{情绪}_big），
所以这里只扫一个目录。

用法：python check_stickers.py
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(os.path.dirname(HERE))
CATALOG = os.path.join(
    APP, "app", "src", "main", "java", "com", "deepfocus", "app", "ai",
    "StickerCatalog.kt")
STICKER_DIR = os.path.join(APP, "app", "src", "main", "assets", "stickers")


def main():
    if not os.path.exists(CATALOG):
        print("找不到 StickerCatalog.kt")
        return 1

    src = open(CATALOG, encoding="utf-8").read()
    # 只取 ALL 列表里定义的 Sticker("id", ...)
    block = src.split("val ALL: List<Sticker> = listOf(", 1)
    if len(block) < 2:
        print("解析不出 ALL 列表")
        return 1
    body = block[1].split("\n    )", 1)[0]
    ids = re.findall(r'Sticker\(\s*"([^"]+)"', body)

    files = {os.path.splitext(n)[0] for n in os.listdir(STICKER_DIR)
             if n.endswith(".webp")}

    cat = set(ids)
    missing_file = sorted(cat - files)     # 清单里有但文件不存在
    unused_file = sorted(files - cat)      # 文件有但清单里没写

    print(f"清单 {len(ids)} 条，资产 {len(files)} 张")
    if missing_file:
        print(f"\n清单里有、文件缺失（{len(missing_file)}）—— 模型选到就会不显示：")
        for m in missing_file:
            print(f"  - {m}")
    if unused_file:
        print(f"\n文件存在但清单没写（{len(unused_file)}）—— 模型永远选不到：")
        for u in unused_file:
            print(f"  - {u}")
    if not missing_file and not unused_file:
        print("\n完全一致 ✓")

    # 描述不能为空
    descs = re.findall(r'Sticker\(\s*"([^"]+)"\s*,\s*"([^"]*)"', body)
    empty = [i for i, d in descs if not d.strip()]
    if empty:
        print(f"\n描述为空（{len(empty)}）—— 模型看不懂这张图：")
        for e in empty:
            print(f"  - {e}")

    return 1 if (missing_file or unused_file or empty) else 0


if __name__ == "__main__":
    sys.exit(main())
