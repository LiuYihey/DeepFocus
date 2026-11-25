# -*- coding: utf-8 -*-
"""探测 animated webp 的 alpha bbox 与尺寸分布

目的：量化「角色在画布里占多大」，
为「裁掉空白 → 等效提分辨率 → 显示不全/发糊」的改造提供依据。
"""
import json
import statistics
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "pet" / "anim" / "all"
OUT = ROOT / "tools" / "media" / "anim_bbox.json"


def frames_of(path: Path):
    """逐帧读取 animated webp"""
    with Image.open(path) as im:
        n = getattr(im, "n_frames", 1)
        for i in range(n):
            im.seek(i)
            yield im.convert("RGBA")


def union_bbox(path: Path):
    """所有帧 alpha 的并集包围盒（角色在整个动画里的活动范围）"""
    x0 = y0 = 10**9
    x1 = y1 = -1
    w = h = None
    nf = 0
    for f in frames_of(path):
        nf += 1
        if w is None:
            w, h = f.size
        a = f.getchannel("A")
        bb = a.getbbox()
        if bb:
            x0 = min(x0, bb[0]); y0 = min(y0, bb[1])
            x1 = max(x1, bb[2]); y1 = max(y1, bb[3])
    if x1 < 0:
        return None
    return {"size": [w, h], "bbox": [x0, y0, x1, y1], "frames": nf}


def main():
    files = sorted(p for p in ASSETS.glob("*.webp") if not p.stem.endswith("_still"))
    stats = []
    for p in files:
        try:
            r = union_bbox(p)
        except Exception as e:
            print("ERR", p.name, e)
            continue
        if not r:
            continue
        w, h = r["size"]
        x0, y0, x1, y1 = r["bbox"]
        bw, bh = x1 - x0, y1 - y0
        stats.append({
            "name": p.stem,
            "canvas": [w, h],
            "bbox": [x0, y0, x1, y1],
            "bbox_size": [bw, bh],
            "fill_w": round(bw / w, 3),
            "fill_h": round(bh / h, 3),
            "frames": r["frames"],
            "bytes": p.stat().st_size,
        })

    fw = [s["fill_w"] for s in stats]
    fh = [s["fill_h"] for s in stats]
    print(f"素材数: {len(stats)}")
    print(f"画布宽占比  中位 {statistics.median(fw):.3f}  最小 {min(fw):.3f}  最大 {max(fw):.3f}")
    print(f"画布高占比  中位 {statistics.median(fh):.3f}  最小 {min(fh):.3f}  最大 {max(fh):.3f}")

    # 裁到 bbox 后的等效分辨率提升
    ups = []
    for s in stats:
        bw, bh = s["bbox_size"]
        if bw and bh:
            # 裁切后把 bbox 放大到 640 宽 -> 相当于多少倍超采样
            ups.append(round(640 / bw, 2) if bw else 0)
    print(f"裁到 bbox 后放大到 640 宽所需倍率  中位 {statistics.median(ups):.2f}x")

    OUT.write_text(json.dumps(stats, ensure_ascii=False, indent=1), encoding="utf-8")
    print("->", OUT)

    # 抽几个看
    for s in stats[:6]:
        print(f"  {s['name'][:34]:34} canvas={s['canvas']} bbox={s['bbox']} "
              f"fill={s['fill_w']:.2f}x{s['fill_h']:.2f} frames={s['frames']}")


if __name__ == "__main__":
    main()
