#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 109 个 GIF 的代表帧渲染成分批图墙，用来逐个确认动作内容。

每张图 12 列 × 10 行 = 120 格，109 个动作分成 1 张图就够。
每格取 3 个时刻（首/中/末）拼成一条，横向看能看出动作过程。

用法：python make_action_sheets.py
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(os.path.dirname(HERE))
GIF_DIR = os.path.join(APP, "artwork", "pet_gif")
OUT_DIR = os.path.join(APP, "artwork", "action_sheets")
TMP = os.path.join(APP, "artwork", "action_tmp")

FFMPEG = (r"C:\Users\<user>\AppData\Local\Microsoft\WinGet\Packages"
          r"\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
          r"\ffmpeg-8.1.2-full_build\bin\ffmpeg.exe")

CW, CH = 200, 112         # 单帧尺寸
FRAMES = 2                # 每格几帧
GAP = 3
LABEL = 16
COLS = 4


def main():
    from PIL import Image, ImageDraw
    os.makedirs(OUT_DIR, exist_ok=True)
    os.makedirs(TMP, exist_ok=True)

    names = sorted(n for n in os.listdir(GIF_DIR) if n.endswith(".gif"))
    cell_w = CW * FRAMES + GAP * (FRAMES + 1)
    cell_h = CH + LABEL

    for page in range(0, len(names), COLS * 4):
        chunk = names[page:page + COLS * 4]
        rows = (len(chunk) + COLS - 1) // COLS
        sheet = Image.new("RGB", (COLS * (cell_w + 2), rows * (cell_h + 2)),
                          (26, 26, 30))
        draw = ImageDraw.Draw(sheet)
        for i, n in enumerate(chunk):
            idx = page + i + 1
            gif = os.path.join(GIF_DIR, n)
            for f in range(FRAMES):
                # 首 / 末
                frame_no = [0, 110][f]
                p = os.path.join(TMP, f"{idx:03d}_{f}.png")
                if not os.path.exists(p):
                    subprocess.run(
                        [FFMPEG, "-y", "-i", gif, "-vf",
                         f"select=eq(n\\,{frame_no}),scale={CW}:{CH}",
                         "-frames:v", "1", p], capture_output=True)
                if not os.path.exists(p):
                    continue
                im = Image.open(p).convert("RGBA")
                bg = Image.new("RGBA", (CW, CH), (242, 242, 242, 255))
                bg.alpha_composite(im)
                x = (i % COLS) * (cell_w + 2) + GAP + f * (CW + GAP)
                y = (i // COLS) * (cell_h + 2) + GAP
                sheet.paste(bg.convert("RGB"), (x, y))
            lx = (i % COLS) * (cell_w + 2) + GAP
            ly = (i // COLS) * (cell_h + 2) + GAP + CH
            draw.text((lx, ly), f"{idx:03d} {n[:-4][:30]}", fill=(255, 205, 120))
        out = os.path.join(OUT_DIR, f"sheet_{page // (COLS * 4) + 1}.png")
        sheet.save(out)
        print(out)


if __name__ == "__main__":
    main()
