#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把生成好的专注页视频安装进 App 的 assets/videos/。

**视频制作环节只剩这一段。**
登场 / 喂食 / 换装全部改用 dsh-pet 的 GIF 动作库（见 install_fish_animations.py），
不再需要视频文件。

目录约定（App 读取的）
    videos/focus/bigfatfish.mp4         专注页常服（循环播放 + 环境音）
    videos/focus/bigfatfish_winter.mp4  专注页冬装（可选，没有就退回常服版）

来源目录
    DeepFocus/artwork/videos/focus/{name}.mp4
    （用豆包/即梦按 docs/专注页视频Prompt.md 生成后放进来）

处理
    * 叠一层环境音（首尾各淡入淡出 0.3 秒，循环时不爆音）
    * 压成 H.264 baseline + yuv420p，兼容 Android MediaCodec
    * 9:16 → 1280 高（全屏 ZOOM 不能糊）

用法
    python install_videos.py
"""
import argparse
import os
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(os.path.dirname(HERE))          # DeepFocus/
SRC = os.path.join(APP, "artwork", "videos", "focus")
ASSETS = os.path.join(APP, "app", "src", "main", "assets", "videos", "focus")
AMBIENT = os.path.join(HERE, "ambience.mp3")

FFMPEG = (r"C:\Users\<user>\AppData\Local\Microsoft\WinGet\Packages"
          r"\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
          r"\ffmpeg-8.1.2-full_build\bin\ffmpeg.exe")
FFPROBE = (r"C:\Users\<user>\AppData\Local\Microsoft\WinGet\Packages"
           r"\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
           r"\ffprobe-8.1.2-full_build\bin\ffprobe.exe")

FADE = 0.3  # 环境音首尾淡入淡出（秒），避免循环播放时爆音

# 源文件 → 装到 assets 的名字
TARGETS = [
    ("bigfatfish.mp4", "bigfatfish.mp4"),
    ("bigfatfish_winter.mp4", "bigfatfish_winter.mp4"),
]


def sh(cmd):
    p = subprocess.run(cmd, capture_output=True, text=True)
    if p.returncode != 0:
        print(p.stderr[-1500:])
        raise SystemExit(1)
    return p


def duration(path):
    p = subprocess.run([FFPROBE, "-v", "error", "-show_entries", "format=duration",
                        "-of", "default=noprint_wrappers=1:nokey=1", path],
                       capture_output=True, text=True)
    try:
        return float(p.stdout.strip())
    except ValueError:
        return 0.0


def mux_ambient(src, dst):
    """给专注页叠环境音，首尾各淡入淡出 FADE 秒。视频流直接 copy。"""
    L = duration(src)
    if L <= 0:
        raise SystemExit(f"读不出时长：{src}")
    fade = min(FADE, L / 4)
    af = (f"[1:a]atrim=0:{L},asetpts=PTS-STARTPTS,"
          f"afade=t=in:st=0:d={fade},"
          f"afade=t=out:st={max(0.0, L - fade)}:d={fade}[aout]")
    sh([FFMPEG, "-y", "-i", src, "-stream_loop", "-1", "-i", AMBIENT,
        "-filter_complex", af, "-map", "0:v", "-map", "[aout]",
        "-c:v", "copy", "-c:a", "aac", "-b:a", "96k", "-ac", "2",
        "-shortest", "-movflags", "+faststart", dst])
    return dst


def compress(src, dst, height=1280, crf=28, preset="slow"):
    """压成 H.264 baseline + yuv420p。9:16 全屏 ZOOM，不能糊。"""
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    cmd = [FFMPEG, "-y", "-i", src, "-vf", f"scale=-2:{height}",
           "-c:v", "libx264", "-profile:v", "baseline", "-level", "3.1",
           "-preset", preset, "-crf", str(crf),
           "-pix_fmt", "yuv420p", "-movflags", "+faststart",
           "-c:a", "aac", "-b:a", "96k", "-ac", "2", dst]
    p = subprocess.run(cmd, capture_output=True, text=True)
    if p.returncode != 0:
        print("   ffmpeg failed:", p.stderr[-400:])
        return False
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default=None, help="只装某个，比如 bigfatfish")
    args = ap.parse_args()

    if not os.path.isdir(SRC):
        print(f"来源目录不存在：{SRC}")
        print("请先按 docs/专注页视频Prompt.md 生成视频，放进这个目录")
        return

    os.makedirs(ASSETS, exist_ok=True)
    installed, missing, total = [], [], 0

    for src_name, dst_name in TARGETS:
        if args.only and args.only != dst_name.replace(".mp4", ""):
            continue
        src = os.path.join(SRC, src_name)
        if not os.path.exists(src):
            missing.append(dst_name)
            continue

        work = src
        if os.path.exists(AMBIENT):
            print(f"--- {dst_name}：叠加环境音")
            ambient = os.path.join(SRC, src_name.replace(".mp4", "_ambient.mp4"))
            mux_ambient(src, ambient)
            work = ambient
        else:
            print(f"--- {dst_name}：没有环境音，跳过")

        tmp = os.path.join(SRC, src_name.replace(".mp4", ".compressed.mp4"))
        if not compress(work, tmp):
            continue
        dst = os.path.join(ASSETS, dst_name)
        shutil.copy2(tmp, dst)
        size = os.path.getsize(dst)
        total += size
        print(f"  OK videos/focus/{dst_name}  {size / 1024:.0f} KB  "
              f"{duration(dst):.1f}s")
        installed.append(dst)

    if missing:
        print(f"\n缺少 {len(missing)} 段：{', '.join(missing)}")
    print(f"\n安装 {len(installed)} 个文件，合计 {total / 1024 / 1024:.2f} MB")
    if not installed:
        print("提示：专注页没有视频时会退化成纯背景色，不会黑屏。")


if __name__ == "__main__":
    main()
