#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把桌宠动作缩略图拼成一张总览图，方便肉眼选片。"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(os.path.dirname(HERE))
SHEET_DIR = os.path.join(APP, "artwork", "pet_sheet")
GIF_DIR = os.path.join(APP, "artwork", "pet_gif")

try:
    from PIL import Image, ImageDraw
except ImportError:
    print("需要 Pillow：pip install pillow")
    sys.exit(1)

COLS, ROWS = 12, 10
CW, CH = 160, 90
PAD = 2
LABEL = 18


def main():
    names = sorted(n for n in os.listdir(GIF_DIR) if n.endswith(".gif"))
    total = len(names)
    per = COLS * ROWS
    out_paths = []
    for page in range((total + per - 1) // per):
        chunk = names[page * per:(page + 1) * per]
        sheet = Image.new("RGB", (COLS * (CW + PAD), ROWS * (CH + PAD + LABEL)),
                          (24, 24, 28))
        draw = ImageDraw.Draw(sheet)
        for i, n in enumerate(chunk):
            src = os.path.join(SHEET_DIR, f"{(page * per + i) + 1:03d}.png")
            if not os.path.exists(src):
                continue
            im = Image.open(src).convert("RGBA").resize((CW, CH), Image.LANCZOS)
            bg = Image.new("RGBA", (CW, CH), (245, 245, 245, 255))
            bg.alpha_composite(im)
            x = (i % COLS) * (CW + PAD)
            y = (i // COLS) * (CH + PAD + LABEL)
            sheet.paste(bg.convert("RGB"), (x, y))
            idx = page * per + i + 1
            draw.text((x + 3, y + CH + 2), f"{idx}", fill=(255, 90, 90))
        out = os.path.join(SHEET_DIR, f"sheet_{page + 1}.png")
        sheet.save(out)
        out_paths.append(out)
        print(f"{out}  ({len(chunk)} 张)")

    # 同时写一份编号 -> 文件名 的对照表
    with open(os.path.join(SHEET_DIR, "index.txt"), "w", encoding="utf-8") as f:
        for i, n in enumerate(names, 1):
            f.write(f"{i:03d}\t{n}\n")
    print(f"对照表: {os.path.join(SHEET_DIR, 'index.txt')}")


if __name__ == "__main__":
    main()
