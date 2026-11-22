#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 GitHub 拉取 dsh-pet 的大肥鱼桌宠 GIF 素材。

来源： https://github.com/PC2005-cloud/dsh-pet
素材目录： dsh-pet/assets/preview/*.gif  （109 个动作）
许可：代码 MIT；**素材禁止商用**；二创在公开渠道须署名。
      非商业用途 + 署名即可使用，商用需另议。

用法：
    python fetch_dsh_pet.py            # 下载全部缺失的 GIF
    python fetch_dsh_pet.py --list     # 只打印清单，不下载
"""
import argparse
import json
import os
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(APP, "artwork", "pet_gif")

TREE_API = ("https://api.github.com/repos/PC2005-cloud/dsh-pet/"
            "git/trees/main?recursive=1")
RAW_BASE = ("https://raw.githubusercontent.com/PC2005-cloud/dsh-pet/"
            "main/dsh-pet/assets/preview/")
PREFIX = "dsh-pet/assets/preview/"


def fetch_tree():
    req = urllib.request.Request(
        TREE_API, headers={"User-Agent": "deepfocus-asset-fetch"})
    with urllib.request.urlopen(req, timeout=60) as r:
        data = json.load(r)
    names = []
    for item in data.get("tree", []):
        p = item.get("path", "")
        if p.startswith(PREFIX) and p.endswith(".gif"):
            names.append(os.path.basename(p))
    return sorted(names)


def download(name):
    dst = os.path.join(OUT, name)
    if os.path.exists(dst) and os.path.getsize(dst) > 1024:
        return "skip"
    req = urllib.request.Request(
        RAW_BASE + name, headers={"User-Agent": "deepfocus-asset-fetch"})
    with urllib.request.urlopen(req, timeout=120) as r:
        data = r.read()
    with open(dst, "wb") as f:
        f.write(data)
    return "ok"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--list", action="store_true", help="只打印清单")
    args = ap.parse_args()

    os.makedirs(OUT, exist_ok=True)
    try:
        names = fetch_tree()
    except Exception as e:  # 网络不通就退回到本地已下载的清单
        print(f"拉取清单失败：{e}")
        names = sorted(n for n in os.listdir(OUT) if n.endswith(".gif"))
        print(f"改用本地已有 {len(names)} 个")

    print(f"共 {len(names)} 个 GIF -> {OUT}")
    if args.list:
        for n in names:
            print("  " + n)
        return

    ok = skip = fail = 0
    for i, n in enumerate(names, 1):
        try:
            r = download(n)
        except Exception as e:
            print(f"  [{i}/{len(names)}] FAIL {n}: {e}")
            fail += 1
            continue
        if r == "ok":
            ok += 1
        else:
            skip += 1
        if i % 10 == 0 or r == "ok":
            print(f"  [{i}/{len(names)}] {n} ({r})")

    total = sum(os.path.getsize(os.path.join(OUT, n))
                for n in os.listdir(OUT) if n.endswith(".gif"))
    print(f"\n下载 {ok}，跳过 {skip}，失败 {fail}")
    print(f"目录合计 {total / 1024 / 1024:.1f} MB")


if __name__ == "__main__":
    main()
