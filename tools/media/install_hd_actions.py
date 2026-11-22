#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 dsh-pet 的 640x360 webm 转成高清 animated WebP，装进 assets。

为什么不用 GIF：
- preview/*.gif 只有 220x124，桌宠窗口 160dp 在 420dpi 屏上约 420px，
  源图放大约 2 倍必糊
- webm/*.webm 是同一批动画的 640x360 母版（VP9-alpha），
  像素量 8.4 倍，观感完全不一样

映射：webm 中文名 -> 动作 id（tools/media/webm_action_map.json），
由 build_hd_anim_map.py 生成并用 alpha 轮廓 IoU 验证过（110/114 命中，
109 个动作全覆盖）。

输出：
  app/src/main/assets/pet/anim/all/{序号:03d}_{id}.webp
  app/src/main/assets/pet/anim/all/{序号:03d}_{id}_still.webp

用法：
    python install_hd_actions.py            # 全量重转
    python install_hd_actions.py --dry-run   # 只报体积
"""
import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
WEBM_DIR = ROOT / "artwork" / "pet_webm"
DEST = ROOT / "app" / "src" / "main" / "assets" / "pet" / "anim" / "all"
CATALOG = (ROOT / "app" / "src" / "main" / "java" / "com" / "deepfocus" / "app"
           / "pet" / "PetAction.kt")
MAP_FILE = HERE / "webm_action_map.json"

FFMPEG = (r"C:\Users\<user>\AppData\Local\Microsoft\WinGet\Packages"
          r"\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
          r"\ffmpeg-8.1.2-full_build\bin\ffmpeg.exe")

# ── 编码参数 ──
# 分辨率：640 = 母版 webm 的原生尺寸，1:1 不缩不放。
#        原来的 GIF 只有 220 宽，被窗口一放大就糊 —— 这正是要修的问题。
WIDTH = 640
# 帧率：母版是 24fps / 10 秒 = 240 帧。
#      12fps 是桌宠小窗口下的顺滑上限，帧数是母版的 1/2。
FPS = 12
# 质量与调色板：256 色 = 母版调色板上限，渐变和边缘都不会出色带。
QUALITY = 75
COLORS = 256


def read_action_index():
    """从 PetAction.kt 读出 {id: 序号}"""
    src = CATALOG.read_text(encoding="utf-8")
    entries = re.findall(
        r'PetAction\(\s*"([^"]+)"\s*,\s*(\d+)\s*,\s*"[^"]*"\s*,\s*setOf\(', src)
    return {gid: int(idx) for gid, idx in entries}


def convert(webm: Path, dst: Path):
    """webm -> animated WebP（保留 alpha）"""
    vf = (f"fps={FPS},scale={WIDTH}:-2:flags=lanczos,split[a][b];"
          f"[a]palettegen=max_colors={COLORS}:stats_mode=diff[p];"
          f"[b][p]paletteuse=dither=sierra2_4a:alpha_threshold=200")
    r = subprocess.run(
        [FFMPEG, "-y", "-v", "error", "-vcodec", "libvpx-vp9", "-i", str(webm),
         "-vf", vf, "-loop", "0", "-an", "-c:v", "libwebp_anim",
         "-lossless", "0", "-q:v", str(QUALITY), "-compression_level", "6",
         "-preset", "picture",
         "-metadata", "comment=dsh-pet (MIT code; assets CC-by-nc, attribution required)",
         str(dst)],
        capture_output=True, text=True, encoding="utf-8", errors="ignore")
    if r.returncode != 0 or not dst.exists():
        print(f"   ffmpeg failed: {r.stderr[-300:]}")
        return False
    return True


def make_still(webm: Path, dst: Path):
    """从 webm 抽一帧做静态兜底（API 26/27 解不了动画）"""
    r = subprocess.run(
        [FFMPEG, "-y", "-v", "error", "-vcodec", "libvpx-vp9", "-i", str(webm),
         "-vf", "select=eq(n\\,30)", "-frames:v", "1",
         "-c:v", "libwebp", "-lossless", "0", "-q:v", "92", str(dst)],
        capture_output=True, text=True, encoding="utf-8", errors="ignore")
    return r.returncode == 0 and dst.exists()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--only", default=None, help="只转某个动作 id")
    args = ap.parse_args()

    if not MAP_FILE.exists():
        print(f"缺少映射表 {MAP_FILE}，先跑 build_hd_anim_map.py")
        return 1

    mapping = json.loads(MAP_FILE.read_text(encoding="utf-8"))
    index = read_action_index()
    DEST.mkdir(parents=True, exist_ok=True)

    # 反查：动作 id -> webm 文件名
    by_action = {gid: cn for cn, gid in mapping.items()}

    targets = []
    for gid, idx in sorted(index.items(), key=lambda kv: kv[1]):
        if args.only and gid != args.only:
            continue
        cn = by_action.get(gid)
        if cn is None:
            print(f"  跳过 {gid}：没有对应 webm")
            continue
        src = WEBM_DIR / f"{cn}.webm"
        if not src.exists():
            print(f"  跳过 {gid}：源文件不存在 {src.name}")
            continue
        targets.append((gid, idx, src))

    print(f"待转 {len(targets)} 个动作（{WIDTH}px / {FPS}fps / q{QUALITY} / {COLORS}色）")

    if args.dry_run:
        total = 0
        for gid, idx, src in targets:
            probe = Path(os.environ.get("TEMP", ".")) / f"_probe_{idx}.webp"
            if convert(src, probe):
                kb = probe.stat().st_size / 1024
                total += kb
                print(f"  {idx:03d}_{gid:44} {kb:7.0f} KB")
            probe.unlink(missing_ok=True)
        print(f"\n预估合计 {total/1024:.1f} MB")
        return 0

    ok = 0
    total = 0
    for gid, idx, src in targets:
        dst = DEST / f"{idx:03d}_{gid}.webp"
        if not convert(src, dst):
            continue
        make_still(src, DEST / f"{idx:03d}_{gid}_still.webp")
        total += dst.stat().st_size
        ok += 1
        if ok % 15 == 0:
            print(f"  已转 {ok}/{len(targets)}，累计 {total/1024/1024:.1f} MB")

    print(f"\n完成 {ok}/{len(targets)} 个，动画合计 {total/1024/1024:.2f} MB")
    print(f"-> {DEST}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
