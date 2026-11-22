#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""收集「鲸鱼娘大肥鱼」社区参考资源。

来源： https://github.com/TreapGoGo/deepseek-whale-girl  （★818, MIT）
社区共同维护的鲸鱼娘二创资源库：角色参考图（三视图）、生成提示词、社区作品。

对 DeepFocus 最有价值的是三块：
  1. dafeiyu-chibi-character-sheet.png  —— Q 版三视图，i2i 的唯一形象来源
  2. dafeiyu-mature-character-sheet.png —— 成熟版三视图
  3. site/assets/workflows/comfyui-issue-2/*.json —— 固定 seed 的 ComfyUI 工作流
     同一 seed + 同一 workflow = 同一角色，这是「一致性靠参考图不靠文字」的落地方式

落地：artwork/refs/whale_girl/
用法：
    python tools/media/collect_whale_girl_refs.py
"""
import json
import os
import sys
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(APP, "artwork", "refs", "whale_girl")

REPO = "TreapGoGo/deepseek-whale-girl"
BRANCH = "main"
RAW = "https://raw.githubusercontent.com/%s/%s/%s" % (REPO, BRANCH, "%s")

FILES = [
    # 角色参考图
    "site/assets/dafeiyu-chibi-character-sheet.png",
    "site/assets/dafeiyu-mature-character-sheet.png",
    # ComfyUI 工作流（固定 seed = 一致性的关键）
    "site/assets/workflows/comfyui-issue-2/README.md",
    "site/assets/workflows/comfyui-issue-2/chibi-seed20260929.json",
    "site/assets/workflows/comfyui-issue-2/chibi-seed42.json",
    "site/assets/workflows/comfyui-issue-2/chibi-seed777.json",
    "site/assets/workflows/comfyui-issue-2/mature-seed20260929.json",
    "site/assets/workflows/comfyui-issue-2/mature-seed42.json",
    "site/assets/workflows/comfyui-issue-2/mature-seed777.json",
    "site/assets/workflows/comfyui-issue-2/upstream-readme.md",
    # 社区表情包（可作为表情包参考）
    "site/assets/gallery/README.md",
    # 仓库说明
    "README.md",
    "LICENSE",
]

UA = {"User-Agent": "deepfocus-asset-collect"}


def fetch(path, tries=4):
    url = RAW % path
    for a in range(tries):
        try:
            req = urllib.request.Request(url, headers=UA)
            return urllib.request.urlopen(req, timeout=180).read()
        except Exception as e:
            if a == tries - 1:
                print("  MISS %s (%s)" % (path, e))
                return None
            time.sleep(5 * (a + 1))
    return None


def main():
    os.makedirs(OUT, exist_ok=True)
    total = 0
    for p in FILES:
        dst = os.path.join(OUT, p.replace("/", "__"))
        if os.path.exists(dst) and os.path.getsize(dst) > 0:
            print("  skip %s" % p)
            continue
        data = fetch(p)
        if data is None:
            continue
        with open(dst, "wb") as fp:
            fp.write(data)
        total += len(data)
        print("  ok   %-64s %8d" % (p, len(data)))

    readme = os.path.join(OUT, "README.md")
    if os.path.exists(readme):
        print("\n--- 仓库 README 摘要 ---")
        with open(readme, encoding="utf-8") as fp:
            print(fp.read()[:1600])

    print("\n共 %d 字节 -> %s" % (total, OUT))


if __name__ == "__main__":
    main()
