# -*- coding: utf-8 -*-
"""构建首页专用「近景」素材：裁掉透明空白 + Lanczos 放大到统一画布

为什么需要它
------------
母版 webm 是 640x360，但角色只占画布宽约 40%（bbox 中位 254x278）。
直接在 640x360 的画布里显示角色，等于让角色在屏幕上永远只占三分之一宽。

上一轮的做法是在运行时给整个画布加 `scale(1.4f)` 放大 —— 这有两个硬伤：
1. **糊**：运行时双线性插值 1.5x（容器 960px / 素材 640px）再叠 1.4x，
   等效 2.1x 有损重采样，且是两次叠加
2. **显示不全**：`clipToBounds()` 把放大后超出的上下裁掉，
   而角色 bbox 高 264~359（画布 360），上下各被切掉约 72px

正解是**离线做**：裁掉透明空白，再用 ffmpeg 的 lanczos 放大到统一画布。
运行时按 1:1 显示，零插值。

输出
----
    app/src/main/assets/pet/anim/hero/{序号:03d}_{id}.webp
    app/src/main/assets/pet/anim/hero/{序号:03d}_{id}_still.webp

桌宠继续用 assets/pet/anim/all/（不改动），两套互不影响。
"""
import argparse
import json
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
BBOX_FILE = HERE / "anim_bbox.json"

FFMPEG = (r"C:\Users\<user>\AppData\Local\Microsoft\WinGet\Packages"
          r"\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
          r"\ffmpeg-8.1.2-full_build\bin\ffmpeg.exe")

# ── 目标尺寸 ──
# 归一化基准：**角色高度** = CHAR_HEIGHT 物理像素。
#
# 为什么是高度而不是短边/面积：
# 角色是「站着的一个人」，感知大小由**高度**决定。
# 按短边归一的话，站立动作（高>宽）角色高 640、躺姿动作（宽>高）角色高 337，
# 屏幕上还是差近 2 倍 —— 正是「有的 GIF 大、有的 GIF 小」。
#
# 运行时容器高度按 CHAR_HEIGHT 反算 dp，各动作角色高度完全一致。
CHAR_HEIGHT = 512

# 宽度上限：按高度归一后，横向伸展的动作宽度会 = 512 * (宽/高)。
# 超过这个值就放不进 360dp 宽的屏了（会被缩放，等于又变小了）。
# 这类动作在 catalog 层面隔离，不参与首页轮播。
MAX_WIDTH = 768

# 帧率：母版 24fps / 10 秒。首页展示用 8fps 够顺滑，
# 体积比 12fps 省三分之一（178MB vs 264MB）。
FPS = 8
QUALITY = 70
COLORS = 256

# 离线锐化：放大必然带来插值软化，补一道 unsharp 把边缘找回来。
# 参数 = 尺寸:半径:振幅。振幅 0.9 是保守值，再高会出白边。
UNSHARP = "unsharp=5:5:0.9:5:5:0.0"

# 裁切留白：webp 经 paletteuse(alpha_threshold=200) 后 alpha 会略微收缩，
# 实测 bbox 比 webm 原始 alpha 边界小几个像素，补 4px 保险。
PAD = 4


def read_action_index():
    src = CATALOG.read_text(encoding="utf-8")
    entries = re.findall(
        r'PetAction\(\s*"([^"]+)"\s*,\s*(\d+)\s*,\s*"[^"]*"\s*,\s*setOf\(', src)
    return {gid: int(idx) for gid, idx in entries}


def load_bbox():
    """{动作序号: (x0, y0, x1, y1)} —— 来自 probe_anim_bbox.py 的全帧 alpha 并集"""
    if not BBOX_FILE.exists():
        print(f"缺少 {BBOX_FILE}，先跑 probe_anim_bbox.py")
        return {}
    data = json.loads(BBOX_FILE.read_text(encoding="utf-8"))
    out = {}
    for item in data:
        m = re.match(r"^(\d+)_", item["name"])
        if m:
            out[int(m.group(1))] = item["bbox"]
    return out


def crop_for(bbox):
    """把 bbox 转成 ffmpeg crop 参数（带留白，且不越出画布）"""
    x0, y0, x1, y1 = bbox
    x0 = max(0, x0 - PAD)
    y0 = max(0, y0 - PAD)
    x1 = min(640, x1 + PAD)
    y1 = min(360, y1 + PAD)
    return x1 - x0, y1 - y0, x0, y0


def scale_target(cw: int, ch: int):
    """决定缩放目标尺寸（宽, 高）。

    规则：**按角色高度归一** —— 高度固定 CHAR_HEIGHT，宽度按比例。
    这样所有动作在屏幕上的**角色高度完全一致**，
    横向伸展的动作只是更宽，不会更矮更小。

    宽度超过 MAX_WIDTH 的（放不进屏）会在 catalog 层面隔离。
    """
    tw = CHAR_HEIGHT * cw / ch
    return round(tw), CHAR_HEIGHT


def convert(webm: Path, dst: Path, crop):
    """webm -> 近景 animated WebP（保留 alpha）

    归一化按**角色高度**统一：裁切后的角色区域等比缩放到
    「高度 = CHAR_HEIGHT」，宽度按各自比例。

    这样所有动作在屏幕上**角色高度完全一致** ——
    横向伸展的动作只是更宽，不会更矮更小。
    这就是「有的 GIF 大、有的 GIF 小」的根治。

    **画布不再 pad 成正方形** —— 保持实际比例，
    运行时用 adjustViewBounds 让高度撑满、宽度自适应。
    """
    cw, ch, cx, cy = crop
    tw, th = scale_target(cw, ch)
    vf = (
        f"crop={cw}:{ch}:{cx}:{cy},"
        f"scale={tw}:{th}:flags=lanczos,"
        f"{UNSHARP},"
        f"fps={FPS},split[a][b];"
        f"[a]palettegen=max_colors={COLORS}:stats_mode=diff[p];"
        f"[b][p]paletteuse=dither=sierra2_4a:alpha_threshold=200"
    )
    r = subprocess.run(
        [FFMPEG, "-y", "-v", "error", "-vcodec", "libvpx-vp9", "-i", str(webm),
         "-vf", vf, "-loop", "0", "-an", "-c:v", "libwebp_anim",
         "-lossless", "0", "-q:v", str(QUALITY), "-compression_level", "6",
         "-preset", "picture",
         "-metadata", "comment=dsh-pet (MIT code; assets CC-by-nc, attribution required)",
         str(dst)],
        capture_output=True, text=True, encoding="utf-8", errors="ignore")
    if r.returncode != 0 or not dst.exists():
        print(f"   ffmpeg failed: {r.stderr[-400:]}")
        return False
    return True


def make_still(webm: Path, dst: Path, crop):
    """静态兜底（API 26/27 解不了动画）"""
    cw, ch, cx, cy = crop
    tw, th = scale_target(cw, ch)
    vf = (f"crop={cw}:{ch}:{cx}:{cy},"
          f"scale={tw}:{th}:flags=lanczos,"
          f"{UNSHARP},"
          "select=eq(n\\,30)")
    r = subprocess.run(
        [FFMPEG, "-y", "-v", "error", "-vcodec", "libvpx-vp9", "-i", str(webm),
         "-vf", vf, "-frames:v", "1",
         "-c:v", "libwebp", "-lossless", "0", "-q:v", "92", str(dst)],
        capture_output=True, text=True, encoding="utf-8", errors="ignore")
    return r.returncode == 0 and dst.exists()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--only", default=None, help="只转某个动作 id")
    ap.add_argument("--limit", type=int, default=0, help="只转前 N 个（试水用）")
    args = ap.parse_args()

    mapping = json.loads(MAP_FILE.read_text(encoding="utf-8"))
    index = read_action_index()
    bboxes = load_bbox()
    if not bboxes:
        return 1
    by_action = {gid: cn for cn, gid in mapping.items()}

    targets = []
    for gid, idx in sorted(index.items(), key=lambda kv: kv[1]):
        if args.only and gid != args.only:
            continue
        if idx not in bboxes:
            continue
        cn = by_action.get(gid)
        if cn is None:
            continue
        src = WEBM_DIR / f"{cn}.webm"
        if not src.exists():
            continue
        targets.append((gid, idx, src))
    if args.limit:
        targets = targets[:args.limit]

    DEST.mkdir(parents=True, exist_ok=True)
    print(f"待转 {len(targets)} 个（角色高 {CHAR_HEIGHT}px / {FPS}fps / q{QUALITY}）")

    ok = 0
    total = 0
    for gid, idx, src in targets:
        crop = crop_for(bboxes[idx])
        dst = DEST / f"{idx:03d}_{gid}.webp"
        if not convert(src, dst, crop):
            continue
        make_still(src, DEST / f"{idx:03d}_{gid}_still.webp", crop)
        kb = dst.stat().st_size / 1024
        total += kb
        ok += 1
        print(f"  {idx:03d}_{gid:44} {kb:7.0f} KB  crop={crop}")

    print(f"\n完成 {ok}/{len(targets)}，合计 {total/1024:.1f} MB")
    print("->", DEST)
    return 0


if __name__ == "__main__":
    sys.exit(main())
