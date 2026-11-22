#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 assets/expressions/ 的大表情并入 assets/stickers/，统一成一个表情包池。

背景：App 里有三套素材 —— 表情包（stickers/，47 张带文字梗的小图）、
动作（pet/anim/all/，109 段动画）、大表情（expressions/，11 张无文字大立绘）。
产品决策：**表情包和大表情统一**，动作库是另一回事（桌宠 + 首页表演用）。

统一后：
- 资源全在 `assets/stickers/`，一个目录、一份清单、一条加载路径
- 命名沿用既有约定 `st_{情绪}_{NN}.webp`：
  带文字梗的用 `st_{情绪}_01..04`，无文字的大立绘用 `st_{情绪}_big`
- 清单条目带情绪字段，模型从同一张表里挑

用法：
    python tools/media/merge_expressions_into_stickers.py
"""
import os
import shutil

APP = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(APP, "app", "src", "main", "assets", "expressions")
DST = os.path.join(APP, "app", "src", "main", "assets", "stickers")


def main():
    if not os.path.isdir(SRC):
        print("expressions/ 不存在，无需合并")
        return

    names = sorted(n for n in os.listdir(SRC) if n.endswith(".webp"))
    moved = 0
    for n in names:
        emotion = os.path.splitext(n)[0]
        target_name = "st_%s_big.webp" % emotion
        target = os.path.join(DST, target_name)
        if os.path.exists(target):
            print("  已存在，跳过 %s" % target_name)
            continue
        shutil.move(os.path.join(SRC, n), target)
        moved += 1
        print("  %-24s -> %s" % (n, target_name))

    # 目录空了才删
    try:
        if not os.listdir(SRC):
            os.rmdir(SRC)
            print("已删除空目录 expressions/")
    except OSError as e:
        print("目录未删：", e)

    print("\n合并 %d 张" % moved)
    print("stickers/ 现有 %d 张" % len(os.listdir(DST)))


if __name__ == "__main__":
    main()
