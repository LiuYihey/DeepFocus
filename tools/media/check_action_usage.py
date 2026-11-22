#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""核查 109 个动作是否真的「用起来了」。

光有资源文件不算用起来 —— 关键是这个动作有没有可能被选中播放。
所以这里扫代码里所有「按标签选动作」的入口，算出实际可达的动作集合。

判定逻辑：
1. 从 PetAction.kt 提取 109 条动作及其标签
2. 从所有 Kotlin 源码里提取 setOf(...) 里用到的 PetActionTag
3. 硬编码的动作 id（onFestival / onLanded 这类）也算可达
4. 两者都够不到的 = 死资源

用法：python check_action_usage.py
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(os.path.dirname(HERE))
PET_DIR = os.path.join(APP, "app", "src", "main", "java", "com", "deepfocus", "app", "pet")
CATALOG = os.path.join(PET_DIR, "PetAction.kt")

# 所有 Kotlin 源码（pet + ui + ai）
SRC_ROOTS = [
    os.path.join(APP, "app", "src", "main", "java", "com", "deepfocus", "app"),
]


def main():
    src = open(CATALOG, encoding="utf-8").read()
    pattern = re.compile(
        r'PetAction\(\s*"([^"]+)"\s*,\s*(\d+)\s*,\s*"([^"]*)"\s*,\s*'
        r'setOf\(([^)]*)\)\s*\)'
    )
    entries = pattern.findall(src)
    actions = {}
    for gid, idx, desc, tags in entries:
        tagset = set(re.findall(r'PetActionTag\.(\w+)', tags))
        actions[gid] = tagset

    # 扫所有源码：哪些标签被用于「选动作」
    used_tags = set()
    hardcoded_ids = set()
    for root in SRC_ROOTS:
        for dirpath, _, files in os.walk(root):
            for f in files:
                if not f.endswith(".kt"):
                    continue
                p = os.path.join(dirpath, f)
                text = open(p, encoding="utf-8", errors="ignore").read()
                # setOf(PetActionTag.A, PetActionTag.B) —— 出现在 pick/onXxx 里
                for m in re.finditer(r'setOf\(([^)]*PetActionTag[^)]*)\)', text):
                    used_tags.update(re.findall(r'PetActionTag\.(\w+)', m.group(1)))
                # 单独一个标签参数
                for m in re.finditer(r'pick\(\s*setOf\(([^)]*)\)', text):
                    used_tags.update(re.findall(r'PetActionTag\.(\w+)', m.group(1)))
                # 硬编码的动作 id（byId("xxx")）
                for m in re.finditer(r'byId\(\s*"([^"]+)"', text):
                    hardcoded_ids.add(m.group(1))
                # 硬编码 id 列表
                for m in re.finditer(r'listOf\(\s*"([^"]+)"\s*,\s*"([^"]+)"', text):
                    hardcoded_ids.add(m.group(1))
                    hardcoded_ids.add(m.group(2))

    # 算出可达动作
    reachable = set()
    unreachable = []
    for gid, tagset in actions.items():
        hit = bool(tagset & used_tags) or gid in hardcoded_ids
        if hit:
            reachable.add(gid)
        else:
            unreachable.append((gid, tagset))

    print(f"动作总数: {len(actions)}")
    print(f"代码里用到的标签: {', '.join(sorted(used_tags))}")
    print(f"硬编码引用的动作: {len(hardcoded_ids)}")
    print(f"\n可达动作: {len(reachable)} / {len(actions)}")

    if unreachable:
        print(f"\n死资源（永远不会被播到）{len(unreachable)} 个：")
        by_tag = {}
        for gid, tagset in unreachable:
            for t in sorted(tagset):
                by_tag.setdefault(t, []).append(gid)
        for t in sorted(by_tag):
            print(f"  [{t}] {len(by_tag[t])} 个：{', '.join(sorted(by_tag[t])[:6])}"
                  + ("..." if len(by_tag[t]) > 6 else ""))

    # 反向：哪些标签定义了但没人用
    all_tags = set()
    for tagset in actions.values():
        all_tags.update(tagset)
    orphan_tags = sorted(all_tags - used_tags)
    if orphan_tags:
        print(f"\n定义了但没被任何代码用到的标签: {', '.join(orphan_tags)}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
