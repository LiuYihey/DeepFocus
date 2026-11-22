#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把白底图转成透明底 PNG。

背景：assets/fish/bigfatfish.png 是真透明（角像素 A=0），
而新生成的 bigfatfish2.png 是白底不透明（24bppRgb）。
两个品种之间切换时，白底会在深色主题下变成一块白方。

做法：**四边洪水填充**，只把「从画布边缘连通的近白像素」置透明。

为什么不用全局阈值：大肥鱼身上有大量纯白（围裙、荷叶边、鲸鱼帽毛边），
全局阈值会把这些一起啃掉，围裙就破了。
洪水填充只动背景 —— 角色内部的白色被深色描边包着，连不到边缘，天然保住。

用法：
    python strip_white_bg.py <in.png> <out.png>
"""
import sys
from collections import deque

from PIL import Image


def strip_white(path_in, path_out, tolerance=14):
    """四边洪水填充去背景。

    tolerance: 判定「白」的容差。像素与参考白 (255,255,255) 的
               Chebyshev 距离小于它就算背景候选。
    """
    img = Image.open(path_in).convert("RGBA")
    w, h = img.size
    px = img.load()

    def is_white(x, y):
        r, g, b, _ = px[x, y]
        return (255 - r) <= tolerance and (255 - g) <= tolerance and (255 - b) <= tolerance

    # 从四边把所有近白像素作为起点，做连通扩散
    seen = bytearray(w * h)
    queue = deque()

    def push(x, y):
        if x < 0 or y < 0 or x >= w or y >= h:
            return
        i = y * w + x
        if seen[i]:
            return
        if not is_white(x, y):
            return
        seen[i] = 1
        queue.append((x, y))

    for x in range(w):
        push(x, 0)
        push(x, h - 1)
    for y in range(h):
        push(0, y)
        push(w - 1, y)

    # 扩散时把邻域也标成"背景"，但只有近白的才继续扩散
    # （跨过近白边缘后遇到角色像素就停，不会蔓延进角色内部）
    while queue:
        x, y = queue.popleft()
        r, g, b, _ = px[x, y]
        px[x, y] = (r, g, b, 0)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if nx < 0 or ny < 0 or nx >= w or ny >= h:
                continue
            i = ny * w + nx
            if seen[i]:
                continue
            if is_white(nx, ny):
                seen[i] = 1
                queue.append((nx, ny))

    # 主体边缘做一次轻微羽化：让描边不生硬
    img.save(path_out, "PNG")
    print(f"  OK {path_out}  {w}x{h}  角像素 alpha={px[1, 1][3]}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    strip_white(sys.argv[1], sys.argv[2])
