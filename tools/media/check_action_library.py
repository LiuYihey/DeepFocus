#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""校验 PetAction.kt 的动作库与实际资源是否一致。

要查四件事：
1. 109 个动作的序号连续、没有重复
2. **进 APK 的**动作（`StageCatalog.SHIPPED_ACTIONS`）资源文件都在
   —— 2026-11-19 起隔离动作的素材已从 APK 删除，不再是「109 个都要在」
3. 每个动作都有描述（描述空了模型就选不对图）
4. 标签合法

序号错位是最容易犯的错 —— 序号决定文件名，序号错了整个动作就播不出来。

用法：python check_action_library.py
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(os.path.dirname(HERE))
PET = os.path.join(APP, "app", "src", "main", "java", "com", "deepfocus", "app", "pet")
CATALOG = os.path.join(PET, "PetAction.kt")
STAGE = os.path.join(PET, "StageCatalog.kt")
ANIM_DIR = os.path.join(APP, "app", "src", "main", "assets", "pet", "anim", "all")
GIF_DIR = os.path.join(APP, "artwork", "pet_gif")

# 代码里允许的标签
VALID_TAGS = {
    "feed", "pet", "play", "gift", "idle", "sleep", "festive",
    "mood", "trick", "work", "think", "thrown", "enter", "talk",
}


def main():
    if not os.path.exists(CATALOG):
        print("找不到 PetAction.kt")
        return 1

    src = open(CATALOG, encoding="utf-8").read()
    # PetAction("id", 序号, "描述", setOf(标签...))
    pattern = re.compile(
        r'PetAction\(\s*"([^"]+)"\s*,\s*(\d+)\s*,\s*"([^"]*)"\s*,\s*'
        r'setOf\(([^)]*)\)\s*\)'
    )
    entries = pattern.findall(src)

    if not entries:
        print("解析不出动作列表")
        return 1

    errors = []

    # 1. 序号连续 + 无重复
    indices = [int(e[1]) for e in entries]
    ids = [e[0] for e in entries]
    if len(set(indices)) != len(indices):
        dup = sorted({i for i in indices if indices.count(i) > 1})
        errors.append(f"序号重复：{dup}")
    if len(set(ids)) != len(ids):
        dup = sorted({i for i in ids if ids.count(i) > 1})
        errors.append(f"id 重复：{dup}")
    expected = list(range(1, len(entries) + 1))
    if indices != expected:
        missing = sorted(set(expected) - set(indices))
        errors.append(f"序号不连续，缺：{missing}")

    # 2. 资源存在 —— 只要求 SHIPPED_ACTIONS 里的动作有素材
    stage_src = open(STAGE, encoding="utf-8").read()
    m = re.search(r"val SHIPPED_ACTIONS: Set<String> = setOf\((.*?)\n    \)", stage_src, re.S)
    shipped = set(re.findall(r'"([^"]+)"', m.group(1))) if m else set(ids)
    files = set(os.listdir(ANIM_DIR)) if os.path.isdir(ANIM_DIR) else set()
    for gid, idx, desc, tags in entries:
        i = int(idx)
        stem = f"{i:03d}_{gid}"
        if gid in shipped:
            if f"{stem}.webp" not in files:
                errors.append(f"{stem}.webp 不存在（在 SHIPPED_ACTIONS 里）")
            if f"{stem}_still.webp" not in files:
                errors.append(f"{stem}_still.webp 不存在（在 SHIPPED_ACTIONS 里）")
        # 3. 描述非空
        if not desc.strip():
            errors.append(f"{stem} 描述为空")
        # 标签合法
        for t in re.findall(r'PetActionTag\.(\w+)', tags):
            if t.lower() not in VALID_TAGS:
                errors.append(f"{stem} 标签非法：{t}")

    # 4. GIF 源是否都在（源目录是权威）
    if os.path.isdir(GIF_DIR):
        gifs = {n[:-4] for n in os.listdir(GIF_DIR) if n.endswith(".gif")}
        no_gif = sorted(gifs - set(ids))
        if no_gif:
            errors.append(f"有 {len(no_gif)} 个 GIF 没进动作库：{no_gif[:5]}...")

    print(f"动作库 {len(entries)} 条，资源目录 {len(files)} 个文件")
    if errors:
        print(f"\n发现 {len(errors)} 个问题：")
        for e in errors[:40]:
            print(f"  - {e}")
        if len(errors) > 40:
            print(f"  ... 还有 {len(errors) - 40} 个")
        return 1
    print("全部一致 ✓")
    return 0


if __name__ == "__main__":
    sys.exit(main())
