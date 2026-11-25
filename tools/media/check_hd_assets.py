# -*- coding: utf-8 -*-
"""校验高清动作资源：分辨率、帧数、alpha、文件齐全"""
import os
from pathlib import Path

from PIL import Image, ImageSequence

ROOT = Path(__file__).resolve().parents[2]
DEST = ROOT / "app" / "src" / "main" / "assets" / "pet" / "anim" / "all"

files = sorted(DEST.glob("*.webp"))
anim = [p for p in files if not p.name.endswith("_still.webp")]
still = [p for p in files if p.name.endswith("_still.webp")]

print(f"动画 {len(anim)} 个，静态 {len(still)} 个")
print(f"合计 {sum(p.stat().st_size for p in files)/1024/1024:.1f} MB")

sizes = {}
frames = {}
bad_alpha = []
bad_size = []
for p in anim:
    im = Image.open(p)
    fr = list(ImageSequence.Iterator(im))
    sizes.setdefault(im.size, []).append(p.name)
    frames[p.name] = len(fr)
    a0 = None
    for f in fr[:3]:
        a = f.convert("RGBA")
        arr = a.getchannel("A")
        if arr.getextrema()[0] < 250:
            a0 = "has-alpha"
            break
    if a0 is None:
        bad_alpha.append(p.name)

print("\n分辨率分布:")
for s, names in sorted(sizes.items()):
    print(f"  {s[0]}x{s[1]}: {len(names)} 个")

print(f"\n帧数分布: min={min(frames.values())} max={max(frames.values())}")

if bad_alpha:
    print(f"\n没有 alpha 的 {len(bad_alpha)} 个（不该发生）:")
    for n in bad_alpha[:5]:
        print("   ", n)

# 静态兜底图分辨率
ssizes = {}
for p in still:
    ssizes.setdefault(Image.open(p).size, []).append(p.name)
print("\n静态兜底分辨率:")
for s, names in sorted(ssizes.items()):
    print(f"  {s[0]}x{s[1]}: {len(names)} 个")

# 编号连续性
idxs = sorted(int(p.name[:3]) for p in anim)
missing = [i for i in range(1, 110) if i not in idxs]
print(f"\n序号缺口: {missing if missing else '无'}")
