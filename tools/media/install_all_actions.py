#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 dsh-pet 的全部 109 个大肥鱼 GIF 转码成 animated WebP。

为什么全要：
- 这批动作里藏着完整的「互动玩法」素材 ——
  雪人、风筝、孔明灯、烟花、陀螺、扑克、画笔、红包、火锅、
  贝壳、围巾、气球…… 每个动作自带道具和场景元素，
  可以组合出「喂食 / 摸头 / 逗猫棒 / 放风筝 / 抽陀螺」这类玩法。
- 只挑 27 个等于扔掉 3/4 的表现力。

输出：
  app/src/main/assets/pet/anim/all/{序号}_{拼音名}.webp   动画
  app/src/main/assets/pet/anim/all/{序号}_{拼音名}_still.webp  静态兜底

体积控制：109 个全转会很大，所以宽度降到 300（桌宠显示尺寸小，
桌宠窗口默认 160dp，300px 足够），质量 66。

用法：
    python install_all_actions.py
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)
from _toolpaths import FFMPEG  # noqa: E402

GIF_DIR = os.path.join(APP, "artwork", "pet_gif")
DEST = os.path.join(APP, "app", "src", "main", "assets", "pet", "anim", "all")

WIDTH = 260      # 桌宠窗口默认 160dp，260px 足够
QUALITY = 52
FPS = 8          # 原 GIF 是 8fps × 110 帧 ≈ 13 秒；
                 # 桌宠尺寸小，8fps 看着依然顺，省下的帧全是体积


def sh(cmd):
    p = subprocess.run(cmd, capture_output=True, text=True)
    if p.returncode != 0:
        print("   ffmpeg failed:", p.stderr[-300:])
        return False
    return True


def convert(src, dst):
    # 抽帧到 12fps：原 GIF 是 8fps × 110 帧 ≈ 13 秒，
    # 降到 12fps 后帧数减半，动画依然流畅但体积小得多。
    return sh([
        FFMPEG, "-y", "-i", src, "-vf",
        f"fps={FPS},scale={WIDTH}:-2:flags=lanczos,split[a][b];"
        f"[a]palettegen=max_colors=96[p];[b][p]paletteuse=dither=sierra2_4a",
        "-loop", "0", "-an", "-c:v", "libwebp_anim", "-lossless", "0",
        "-q:v", str(QUALITY), "-preset", "picture",
        "-metadata", "comment=dsh-pet (MIT code; assets CC-by-nc, attribution required)",
        dst,
    ])


def make_still(src, dst):
    return sh([
        FFMPEG, "-y", "-i", src, "-vf", "select=eq(n\\,30)",
        "-frames:v", "1", "-c:v", "libwebp", "-lossless", "0", "-q:v", "82", dst,
    ])


def main():
    if not os.path.isdir(GIF_DIR):
        print(f"找不到 GIF 目录：{GIF_DIR}")
        return 1
    os.makedirs(DEST, exist_ok=True)

    names = sorted(n for n in os.listdir(GIF_DIR) if n.endswith(".gif"))
    total = 0
    ok = 0
    for i, n in enumerate(names, 1):
        base = n[:-4]
        src = os.path.join(GIF_DIR, n)
        dst = os.path.join(DEST, f"{i:03d}_{base}.webp")
        if not convert(src, dst):
            continue
        make_still(src, os.path.join(DEST, f"{i:03d}_{base}_still.webp"))
        total += os.path.getsize(dst)
        ok += 1
        if i % 20 == 0:
            print(f"  [{i}/{len(names)}] 已转 {ok} 个，{total / 1024 / 1024:.1f} MB")

    print(f"\n完成 {ok}/{len(names)} 个，合计 {total / 1024 / 1024:.2f} MB")
    print(f"-> {DEST}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
