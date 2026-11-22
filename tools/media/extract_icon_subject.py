#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 t2i 候选里抠出角色主体（去掉黑框 + 内外白底）。

t2i 出来的图标结构是：
    ┌────────────────────────────┐
    │  白边（画布外圈，约 30px）     │
    │   ┌──────────────────┐      │
    │   │ ███ 黑色圆角描边 ███ │    │  ← 一圈粗黑框（约 110px）
    │   │  白底 + 角色        │    │
    │   └──────────────────┘      │
    └────────────────────────────┘

自适应图标的前景层需要**只有角色**的 alpha，所以三层都得去掉：
外白边、黑色描边、框内白底。

难点：角色身上的**白色荷叶边 / 围裙**跟框内白底颜色一样，
按颜色删会把围裙吃掉。

解法（几何 + 连通域混合）
------------------------
1. **几何定位黑框**：沿画面四边向内扫描，
   第一段连续黑带就是框的外沿，由此得到框的内边界矩形。
   （框是封闭的矩形环，四条边的内边界拼起来就是「框内区域」。）
2. 框内区域 = 帧内容；框外的全删（含外白边）。
3. 框内白底：从**框内区域的四边**向内漫水填充白色。
   角色的白块被黑色描边包着，漫水填充碰到描边就停，不会误吃。
4. 黑色描边（角色自身的黑描边）保留 —— 它是角色的一部分。
"""
import os
import sys
from collections import deque

from PIL import Image, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CAND = os.path.join(ROOT, "artwork", "icon_src", "candidates")


def is_white(r, g, b):
    return r > 232 and g > 232 and b > 232


def is_dark(r, g, b):
    return r < 60 and g < 60 and b < 60


def find_frame_inner_rect(px, w, h):
    """从四边往内找第一段连续黑带，返回框内边界矩形 (l, t, r, b)。

    框是矩形环，所以：
    - 上边：从上往下第一段连续暗像素的底部 = 框内边界 top
    - 下边：从下往上第一段连续暗像素的顶部 = 框内边界 bottom
    - 左边 / 右边同理
    """
    def scan_vertical(x, forward=True):
        rng = range(h) if forward else range(h - 1, -1, -1)
        run = 0
        for y in rng:
            r, g, b = px[x, y][:3]
            if is_dark(r, g, b):
                run += 1
                if run >= 6:
                    # 连续 6px 暗色算「进了框」
                    return y
            else:
                run = 0
        return None

    def scan_horizontal(y, forward=True):
        rng = range(w) if forward else range(w - 1, -1, -1)
        run = 0
        for x in rng:
            r, g, b = px[x, y][:3]
            if is_dark(r, g, b):
                run += 1
                if run >= 6:
                    return x
            else:
                run = 0
        return None

    # 四条边各取中点附近做扫描（避开圆角）
    top = scan_vertical(w // 2, True)
    bottom = scan_vertical(w // 2, False)
    left = scan_horizontal(h // 2, True)
    right = scan_horizontal(h // 2, False)
    if None in (top, bottom, left, right):
        raise RuntimeError(f"没找到黑框：top={top} bottom={bottom} left={left} right={right}")
    return left, top, right, bottom


def flood_white(img, w, h, seed_mask):
    """从种子出发 4 邻域漫水填充白色像素"""
    px = img.load()
    seen = bytearray(w * h)
    q = deque()
    for i in range(w * h):
        if seed_mask[i]:
            seen[i] = 1
            q.append(i)
    out = bytearray(w * h)
    while q:
        idx = q.popleft()
        out[idx] = 1
        x, y = idx % w, idx // w
        r, g, b = px[x, y][:3]
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if 0 <= nx < w and 0 <= ny < h:
                ni = ny * w + nx
                if seen[ni] or out[ni]:
                    continue
                nr, ng, nb = px[nx, ny][:3]
                if is_white(nr, ng, nb):
                    seen[ni] = 1
                    out[ni] = 1
                    q.append(ni)
    return out


def find_frame_pixels(rgb, w, h, l, t, r, b):
    """找出黑框像素（含圆角）。

    判定依据：**厚度**。
    实测这张图的框：左右边各 106px 厚，上下边 32~55px 厚；
    而角色自己的描边只有 1~3px（偶尔 8~15px 是大块阴影）。
    差距有两个数量级，所以「连续暗色长度 ≥ 25px」能把两者分得很干净。

    做法：横向扫一遍找粗暗段，竖向扫一遍找粗暗段，两边都标记。
    （框是矩形环，四条边分别能被行扫描或列扫描覆盖；
      角色穿过框的地方会断开，但断开不影响其余部分。）
    """
    px = rgb.load()
    THICK = 25
    frame = bytearray(w * h)

    def scan(horizontal):
        n = w if horizontal else h
        other = h if horizontal else w
        for a in range(other):
            run_start = None
            for b in range(n):
                x, y = (b, a) if horizontal else (a, b)
                r, g, bl = px[x, y][:3]
                if is_dark(r, g, bl):
                    if run_start is None:
                        run_start = b
                else:
                    if run_start is not None and b - run_start >= THICK:
                        for k in range(run_start, b):
                            xx, yy = (k, a) if horizontal else (a, k)
                            frame[yy * w + xx] = 1
                    run_start = None
            if run_start is not None and n - run_start >= THICK:
                for k in range(run_start, n):
                    xx, yy = (k, a) if horizontal else (a, k)
                    frame[yy * w + xx] = 1

    scan(True)
    scan(False)
    return frame


def extract(src_path, out_path):
    im = Image.open(src_path).convert("RGBA")
    w, h = im.size
    rgb = im.convert("RGB")
    px = rgb.load()

    l, t, r, b = find_frame_inner_rect(px, w, h)
    print(f"   框内区域: x[{l},{r}] y[{t},{b}]")

    # 框内白底的种子 = 框内区域的四边
    seed = bytearray(w * h)
    for x in range(l, r + 1):
        for y in (t, b):
            if 0 <= x < w and 0 <= y < h:
                seed[y * w + x] = 1
    for y in range(t, b + 1):
        for x in (l, r):
            if 0 <= x < w and 0 <= y < h:
                seed[y * w + x] = 1

    white_bg = flood_white(rgb, w, h, seed)

    # 框的**圆角残留**：黑框是圆角矩形，直线扫描只能定位四条直边，
    # 四个圆角会漏掉一圈黑弧。
    #
    # 判定依据：**黑框只挨着白底，不挨着彩色像素**。
    # 角色自己的描边总是紧贴彩色区域（头发 / 皮肤 / 围裙）。
    # 所以找出所有暗色连通块，把「不与任何彩色像素相邻」的那些判为框。
    frame_pixels = find_frame_pixels(rgb, w, h, l, t, r, b)

    # 组装 alpha：
    # - 框外（含外白边 + 黑框）→ 透明
    # - 框内白底 + 框的圆角残留 → 透明
    # - 其余 → 保留
    out = im.copy()
    opx = out.load()
    for y in range(h):
        for x in range(w):
            inside = (l <= x <= r) and (t <= y <= b)
            if not inside or white_bg[y * w + x] or frame_pixels[y * w + x]:
                opx[x, y] = (0, 0, 0, 0)

    bbox = out.getchannel("A").getbbox()
    if bbox:
        out = out.crop(bbox)

    a = out.getchannel("A").filter(ImageFilter.GaussianBlur(0.5))
    a = a.point(lambda v: 255 if v > 210 else (0 if v < 45 else v))
    out.putalpha(a)

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    out.save(out_path)
    print(f"OK {out_path}  {out.size}")
    return out


def main():
    name = sys.argv[1] if len(sys.argv) > 1 else "d2"
    src = os.path.join(CAND, f"icon_{name}.jpg")
    out = os.path.join(ROOT, "artwork", "icon_src", "dafeiyu_icon.png")
    extract(src, out)

    im = Image.open(out).convert("RGBA")
    bg = Image.new("RGBA", im.size, (58, 64, 84, 255))
    bg.alpha_composite(im)
    preview = os.path.join(ROOT, "artwork", "icon_src", "dafeiyu_icon_preview.png")
    bg.convert("RGB").save(preview)
    print("预览:", preview)


if __name__ == "__main__":
    main()
