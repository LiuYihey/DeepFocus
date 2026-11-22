#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把本地表情包拼成带文件名的图墙，用来写准确的语义描述。"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(os.path.dirname(HERE))
STICKER_DIR = os.path.join(APP, "app", "src", "main", "assets", "stickers")
OUT = os.path.join(APP, "artwork", "sticker_sheet.png")


def main():
    from PIL import Image, ImageDraw
    names = sorted(n for n in os.listdir(STICKER_DIR) if n.endswith(".webp"))
    cols = 8
    cw, ch, label = 150, 150, 16
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * (cw + 3), rows * (ch + label + 3)), (30, 30, 34))
    draw = ImageDraw.Draw(sheet)
    for i, n in enumerate(names):
        im = Image.open(os.path.join(STICKER_DIR, n)).convert("RGBA")
        im.thumbnail((cw, ch), Image.LANCZOS)
        cell = Image.new("RGBA", (cw, ch), (240, 240, 240, 255))
        cell.alpha_composite(im, ((cw - im.width) // 2, (ch - im.height) // 2))
        x = (i % cols) * (cw + 3)
        y = (i // cols) * (ch + label + 3)
        sheet.paste(cell.convert("RGB"), (x, y))
        draw.text((x + 2, y + ch + 2), n.replace(".webp", ""), fill=(255, 210, 120))
    sheet.save(OUT)
    print(OUT, len(names))


if __name__ == "__main__":
    main()
