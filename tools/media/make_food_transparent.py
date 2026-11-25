# -*- coding: utf-8 -*-
"""把大白饭 PNG 的白底抠掉，产出透明背景的素材。

用户要求：「大白饭的图标可以去掉白色背景吗？」

难点
----
这碗饭本身是**米白色的**，跟白底几乎同色 —— 按阈值抠会把饭也抠掉。
但碗有一圈**棕色描边**，把碗和背景隔开了。

所以做法是**从画面四角往里漫水填充**（flood fill）：
碰到「够白」就继续往里走，碰到描边就停。
这样只有真正连着画布边缘的白底会被抠掉，碗里的饭被描边保护住。

边缘做 1px 羽化，避免锯齿白边。
"""
from collections import deque
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "app" / "src" / "main" / "assets" / "foods" / "大白饭.png"
BACKUP = ROOT / "artwork" / "foods_backup" / "大白饭_白底.png"

# 漫水填充的「够白」判定：min 通道 > 242 且通道差 < 14
WHITE_MIN = 242
WHITE_SPREAD = 14


def is_background_ish(rgb):
    r, g, b = rgb
    return min(r, g, b) >= WHITE_MIN and (max(r, g, b) - min(r, g, b)) <= WHITE_SPREAD


def cut_white(img: Image.Image):
    """返回 (RGB 图, alpha 遮罩)"""
    rgb = img.convert("RGB")
    w, h = rgb.size
    px = rgb.load()

    visited = [[False] * w for _ in range(h)]
    q = deque()

    # 从四条边所有「够白」的像素出发
    for x in range(w):
        for y in (0, h - 1):
            if not visited[y][x] and is_background_ish(px[x, y]):
                visited[y][x] = True
                q.append((x, y))
    for y in range(h):
        for x in (0, w - 1):
            if not visited[y][x] and is_background_ish(px[x, y]):
                visited[y][x] = True
                q.append((x, y))

    while q:
        x, y = q.popleft()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and not visited[ny][nx]:
                if is_background_ish(px[nx, ny]):
                    visited[ny][nx] = True
                    q.append((nx, ny))

    alpha = Image.new("L", (w, h), 255)
    ap = alpha.load()
    for y in range(h):
        row = visited[y]
        for x in range(w):
            if row[x]:
                ap[x, y] = 0

    # 羽化：与不透明区相邻的背景像素留 1/3 不透明度，压掉锯齿白边
    opaque = alpha.load()
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            if opaque[x, y] == 0:
                continue
            # 已经是半透明区就不动
            if opaque[x, y] != 255:
                continue
            if any(opaque[nx, ny] == 0
                   for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1))):
                opaque[x, y] = 170

    return rgb, alpha


def main():
    src = Image.open(SRC)
    print("原图:", src.size, src.mode)

    rgb, alpha = cut_white(src)
    out = rgb.convert("RGBA")
    out.putalpha(alpha)

    # 裁掉四周全透明的部分，让碗占满画布
    bbox = alpha.getbbox()
    if bbox:
        out = out.crop(bbox)
    print("抠白后:", out.size)

    BACKUP.parent.mkdir(parents=True, exist_ok=True)
    if not BACKUP.exists():
        BACKUP.write_bytes(SRC.read_bytes())
        print("备份原图:", BACKUP)

    out.save(SRC, optimize=True)
    print("写入:", SRC, f"{SRC.stat().st_size // 1024} KB")

    hist = out.getchannel("A").histogram()
    total = out.size[0] * out.size[1]
    print(f"全透明 {hist[0] / total:.1%}，半透明 {sum(hist[1:255]) / total:.1%}，全不透明 {hist[255] / total:.1%}")


if __name__ == "__main__":
    main()
