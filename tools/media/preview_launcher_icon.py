#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""预览自适应图标在启动器里的效果。

模拟系统 mask：108dp 画布里只露出中央 72dp 的圆。
同时输出三个尺寸（48 / 72 / 192），看小图标下还认不认得出。
"""
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "app", "src", "main", "res")


def compose(size, out):
    bg = Image.open(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_background.png"))
    fg = Image.open(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_foreground.png"))
    bg = bg.convert("RGBA").resize((size, size), Image.LANCZOS)
    fg = fg.convert("RGBA").resize((size, size), Image.LANCZOS)

    canvas = bg.copy()
    canvas.alpha_composite(fg)

    # 系统 mask：中央 72/108 直径的圆
    mask = Image.new("L", (size, size), 0)
    d = int(size * 72 / 108)
    off = (size - d) // 2
    ImageDraw.Draw(mask).ellipse([off, off, off + d - 1, off + d - 1], fill=255)
    canvas.putalpha(mask)

    # 外面垫一张浅色，看清楚圆形范围
    padded = Image.new("RGB", (size, size), (235, 235, 238))
    padded.paste(canvas.convert("RGB"), (0, 0), canvas)
    return padded


def main():
    tiles = []
    for s in (192, 96, 48):
        tiles.append(compose(s, None))
    total_w = sum(t.width for t in tiles) + 20 * (len(tiles) - 1)
    h = max(t.height for t in tiles)
    sheet = Image.new("RGB", (total_w, h), (255, 255, 255))
    x = 0
    for t in tiles:
        sheet.paste(t, (x, 0))
        x += t.width + 20
    out = os.path.join(ROOT, "artwork", "icon_src", "launcher_preview.png")
    sheet.save(out)
    print("预览:", out)

    # 顺带把 legacy 方形版也看一眼
    legacy = Image.open(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher.png"))
    legacy.save(os.path.join(ROOT, "artwork", "icon_src", "launcher_legacy.png"))
    print("legacy:", os.path.join(ROOT, "artwork", "icon_src", "launcher_legacy.png"))


if __name__ == "__main__":
    main()
