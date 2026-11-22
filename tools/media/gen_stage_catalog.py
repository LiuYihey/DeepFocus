# -*- coding: utf-8 -*-
"""生成「舞台动作白名单」

背景
----
归一化后短边统一为 512，但**横向摊得太开的动作**（比如躺着的、挥手的）
短边会明显小于 512 —— 上台时看着就是"小一号"，跟其他动作不齐。

用户反馈：「有些 GIF 大有些 GIF 小，现在是大一下小一下的」。

解法
----
把这类动作**隔离**：不参与首页轮播，也不出现在桌宠的可选动作里。
只保留短边足够接近 512 的动作 —— 它们上台时角色高度一致。

产出
----
    app/src/main/java/com/deepfocus/app/pet/StageCatalog.kt
"""
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
BBOX = HERE / "anim_bbox.json"
CATALOG = (ROOT / "app" / "src" / "main" / "java" / "com" / "deepfocus" / "app"
           / "pet" / "PetAction.kt")
OUT = CATALOG.parent / "StageCatalog.kt"

# 归一化基准：角色高度
CHAR_HEIGHT = 512
# 宽度上限：超过就放不进 360dp 宽的屏
MAX_WIDTH = 768

# 能上舞台的标签。
#
# 2026-11-18 用户反馈：「点击大白饭后大肥鱼吃饭的表情包只有一个吗，
# 不可以有多个吃东西的表情包随机出现吗」
# → 吃的动作必须能上台、能被喂食抽到。
#
# 之前只有 {ENTER, TRICK, MOOD, PLAY}，
# 导致 **16 个 chi-* 吃的动作全部进不了白名单**
# （FEED 只有 2 个，FESTIVE 一个都没进）——
# 所以首页轮播永远看不到吃饭表情，喂食也只有 2 个动作可选。
#
# 加上 FEED / FESTIVE 后，宽度不合格的会被下面的 MAX_WIDTH 自动过滤。
STAGE_TAGS = {"ENTER", "TRICK", "MOOD", "PLAY", "FEED", "FESTIVE"}


def scale_target(cw: int, ch: int):
    """按角色高度归一：高度固定，宽度按比例"""
    return round(CHAR_HEIGHT * cw / ch), CHAR_HEIGHT


def main():
    src = CATALOG.read_text(encoding="utf-8")
    idx_of = {}
    for m in re.finditer(
            r'PetAction\(\s*"([^"]+)"\s*,\s*(\d+)\s*,\s*"[^"]*"\s*,\s*setOf\(([^)]*)\)',
            src):
        idx_of[int(m.group(2))] = (
            m.group(1), re.findall(r'PetActionTag\.(\w+)', m.group(3)))

    bbox = json.loads(BBOX.read_text(encoding="utf-8"))

    keep = []
    drop = []
    for item in bbox:
        idx = int(re.match(r"^(\d+)_", item["name"]).group(1))
        if idx not in idx_of:
            continue
        gid, tags = idx_of[idx]
        if not (set(tags) & STAGE_TAGS):
            continue
        x0, y0, x1, y1 = item["bbox"]
        cw, ch = x1 - x0, y1 - y0
        tw, th = scale_target(cw, ch)
        # 宽度超出上限 = 横向摊得太开，放不进屏
        if tw > MAX_WIDTH:
            drop.append((idx, gid, tw))
        else:
            keep.append((idx, gid, tw))

    print(f"可上台 {len(keep)} 个，隔离 {len(drop)} 个")
    for idx, gid, w in sorted(drop, key=lambda r: -r[2]):
        print(f"  隔离 {idx:03d} {gid:44} 归一化宽 {w}")

    lines = [
        "package com.deepfocus.app.pet",
        "",
        "/**",
        " * 舞台动作白名单 —— 首页轮播专用。",
        " *",
        " * 只收录「角色高度与其他动作一致」的动作。",
        " * 横向摊得太开的动作（躺着的、挥手的）归一化后短边明显偏小，",
        " * 上台会显得小一号 —— 这类动作已隔离，不参与轮播。",
        " *",
        " * 由 tools/media/gen_stage_catalog.py 生成，请勿手改。",
        " */",
        "object StageCatalog {",
        "",
        "    /** 可上台的动作 id（按出现顺序） */",
        "    val STAGE_ACTIONS: Set<String> = setOf(",
    ]
    for idx, gid, r in keep:
        lines.append(f'        "{gid}",')
    lines += [
        "    )",
        "}",
        "",
    ]
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("->", OUT)


if __name__ == "__main__":
    main()
