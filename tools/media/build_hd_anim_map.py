# -*- coding: utf-8 -*-
"""建立 webm(中文名) -> PetAction id 的最终映射，并用轮廓 IoU 验证是同一动画

上游两套素材是同批动画的两种格式：
  assets/preview/*.gif  109 个，220x124，拼音 id 文件名
  assets/webm/*.webm    114 个，640x360，中文名

匹配策略（从严到宽）：
  1. 中文名转拼音，与 GIF 文件名规范化后**完全相等**
  2. 去掉前缀（余额- / 点击回应-）后再比
  3. 一方是另一方的前缀（被吓一跳 -> beixiayitiao-zhamao）
  4. 人工表

最后用「alpha 轮廓 IoU」逐对验证，确认是同一段动画。
"""
import json
import os
import re
import subprocess
from pathlib import Path

import numpy as np
from PIL import Image

from pypinyin import lazy_pinyin

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
GIF_DIR = ROOT / "artwork" / "pet_gif"
WEBM_DIR = ROOT / "artwork" / "pet_webm"
FFMPEG = (r"C:\Users\<user>\AppData\Local\Microsoft\WinGet\Packages"
          r"\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
          r"\ffmpeg-8.1.2-full_build\bin\ffmpeg.exe")

# 需要剥离的语义前缀
STRIP_PREFIX = ["余额-", "点击回应-"]

# 人工指定（拼音对不上但语义明确）
MANUAL = {
    "吃重阳糕": "chi-chongyanggao",
    "玩游戏气急败坏": "wan-youxi-qijibaituai",
    "悠闲哼歌": "youxian-hengga",
    "吃饭·碗饭": "chi-wancan",
    "用鲸鱼尾巴拍打地面": "yong-jingyu-weiba-paidadi",
    "被吓一跳": "beixiayitiao-zhamao",
    "点击回应-傲娇生气": "dianji-huiying-aojiao-shengqi-ceshen-zhanshi",
}


def to_pinyin(cn: str) -> str:
    return "-".join(p for p in lazy_pinyin(cn) if p.strip())


def norm(s: str) -> str:
    return re.sub(r"[^a-z0-9]", "", s.lower())


def gif_alpha_masks(gif_path: Path, size=(160, 90)):
    """取 GIF 每一帧的 alpha 轮廓（统一缩放到 size）"""
    im = Image.open(gif_path)
    masks = []
    for frame in ImageSequence_frames(im):
        arr = np.array(frame.convert("RGBA").resize(size))
        masks.append(arr[:, :, 3] > 128)
    return masks


def ImageSequence_frames(im):
    from PIL import ImageSequence
    return list(ImageSequence.Iterator(im))


def webm_alpha_mask(webm_path: Path, tmp: Path, size=(160, 90)):
    """取 webm 第一帧的 alpha 轮廓"""
    if tmp.exists():
        tmp.unlink()
    subprocess.run(
        [FFMPEG, "-y", "-v", "error", "-vcodec", "libvpx-vp9", "-i", str(webm_path),
         "-vframes", "1", "-pix_fmt", "rgba", "-vf",
         f"scale={size[0]}:{size[1]}", str(tmp)],
        capture_output=True)
    if not tmp.exists():
        return None
    arr = np.array(Image.open(tmp).convert("RGBA"))
    return arr[:, :, 3] > 128


def iou(m1, m2):
    inter = np.logical_and(m1, m2).sum()
    union = np.logical_or(m1, m2).sum()
    return inter / union if union else 0.0


def main():
    gifs = sorted(n[:-4] for n in os.listdir(GIF_DIR) if n.endswith(".gif"))
    webms = sorted(p.stem for p in WEBM_DIR.glob("*.webm"))
    gif_set = set(gifs)

    mapping = {}
    unmatched = []
    for w in webms:
        hit = None
        cands = [w] + [w[len(p):] for p in STRIP_PREFIX if w.startswith(p)]
        for c in cands:
            p = to_pinyin(c)
            if norm(p) in {norm(g) for g in gifs}:
                hit = next(g for g in gifs if norm(g) == norm(p))
                break
        if hit is None:
            # 前缀包含
            for c in cands:
                np_ = norm(to_pinyin(c))
                if not np_:
                    continue
                cands2 = [g for g in gifs
                          if norm(g) and (norm(g).startswith(np_) or np_.startswith(norm(g)))]
                if len(cands2) == 1:
                    hit = cands2[0]
                    break
        if hit is None and w in MANUAL:
            hit = MANUAL[w]
        if hit and hit in gif_set:
            mapping[w] = hit
        else:
            unmatched.append(w)

    print(f"webm {len(webms)} 个，映射成功 {len(mapping)}，未匹配 {len(unmatched)}")
    print("未匹配（上游新增，GIF 侧没有对应）:", unmatched)

    used = set(mapping.values())
    print(f"GIF 覆盖 {len(used)}/{len(gifs)}")
    missing = [g for g in gifs if g not in used]
    print("未被覆盖的 GIF:", missing)

    # ---- IoU 验证 ----
    tmp = HERE / "_iou.png"
    print("\nIoU 验证（webm 首帧 vs GIF 最相似帧）:")
    low = []
    ious = []
    for w, gid in sorted(mapping.items(), key=lambda kv: kv[1]):
        wm_mask = webm_alpha_mask(WEBM_DIR / f"{w}.webm", tmp)
        if wm_mask is None:
            print(f"  {gid:42} webm 解码失败")
            continue
        masks = gif_alpha_masks(GIF_DIR / f"{gid}.gif")
        best = max((iou(wm_mask, m) for m in masks), default=0.0)
        ious.append(best)
        if best < 0.90:
            low.append((w, gid, best))
    ious = np.array(ious)
    print(f"  样本 {len(ious)}，IoU 中位数 {np.median(ious):.3f}，"
          f"最小 {ious.min():.3f}，最大 {ious.max():.3f}")
    if low:
        print(f"  IoU < 0.90 的 {len(low)} 对（需人工确认）:")
        for w, gid, b in low:
            print(f"    {w:22} -> {gid:40} IoU={b:.3f}")

    out = HERE / "webm_action_map.json"
    out.write_text(json.dumps(mapping, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n映射表 -> {out}")
    return mapping


if __name__ == "__main__":
    main()
