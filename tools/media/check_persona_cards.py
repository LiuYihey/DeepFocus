#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
内置人格卡包校验脚本
====================

检查 assets/personas/builtin_cards.json：

1. 结构完整 —— 每张卡的字段齐全、无空值
2. 阈值单调 —— affinityThreshold 严格递增，且第一张为 0
3. 档位对齐 —— 阈值与 FishMoodResolver.kt 里的好感度常量一一对应
4. 署名齐全 —— 每张卡都有来源仓库与许可（正文是别人写的，必须标出处）
5. 温度合理 —— 落在 0~2 之间

用法：
    python tools/media/check_persona_cards.py
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CARDS_JSON = ROOT / "app" / "src" / "main" / "assets" / "personas" / "builtin_cards.json"
RESOLVER = (
    ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "deepfocus"
    / "app"
    / "ai"
    / "FishMoodResolver.kt"
)

REQUIRED_TEXT_FIELDS = [
    "id",
    "displayName",
    "tagline",
    "sourceRepo",
    "sourceLicense",
    "identity",
    "traits",
    "speechStyle",
    "catchphrases",
    "backstory",
    "taboos",
    "proactiveMotivation",
]


def load_cards() -> list:
    with open(CARDS_JSON, encoding="utf-8") as f:
        data = json.load(f)
    return data["cards"] if isinstance(data, dict) and "cards" in data else data


def resolver_thresholds() -> list:
    """从 FishMoodResolver.kt 里抠出好感度常量，避免两处各写一份"""
    src = RESOLVER.read_text(encoding="utf-8")
    out = []
    for m in re.finditer(r"const val AFFINITY_(\w+)\s*=\s*(\d+)", src):
        out.append(int(m.group(2)))
    return sorted(out)


def main() -> int:
    if not CARDS_JSON.exists():
        raise SystemExit(f"找不到 {CARDS_JSON}")

    cards = load_cards()
    problems = []

    print(f"内置人格卡 {len(cards)} 张\n")

    # 1. 结构完整
    for c in cards:
        for f in REQUIRED_TEXT_FIELDS:
            if f not in c:
                problems.append(f"{c.get('id', '?')}: 缺字段 {f}")
            elif not str(c[f]).strip():
                problems.append(f"{c.get('id', '?')}: 字段 {f} 是空的")
        if "temperature" not in c:
            problems.append(f"{c.get('id', '?')}: 缺字段 temperature")
        elif not 0 <= float(c["temperature"]) <= 2:
            problems.append(f"{c.get('id', '?')}: temperature 越界 {c['temperature']}")

    # 2. id 唯一 + 阈值单调
    ids = [c["id"] for c in cards]
    for i, cid in enumerate(ids):
        if ids.count(cid) > 1:
            problems.append(f"id 重复：{cid}")

    thresholds = [c["affinityThreshold"] for c in cards]
    if thresholds != sorted(thresholds):
        problems.append(f"阈值不是升序：{thresholds}")
    if len(set(thresholds)) != len(thresholds):
        problems.append(f"阈值有重复：{thresholds}")
    if thresholds and thresholds[0] != 0:
        problems.append(f"第一张卡的阈值应该是 0（初始卡），实际 {thresholds[0]}")

    # 3. 档位对齐：阈值 = {0} ∪ FishMoodResolver 的常量
    expected = sorted(set([0] + resolver_thresholds()))
    if sorted(thresholds) != expected:
        problems.append(f"阈值与 FishMoodResolver 的档位对不上：{sorted(thresholds)} vs {expected}")

    # 4. 署名：来源必须是 GitHub 仓库地址或 owner/repo
    for c in cards:
        repo = c.get("sourceRepo", "")
        if not re.match(r"^(https?://github\.com/)?[\w.-]+/[\w.-]+", repo):
            problems.append(f"{c.get('id', '?')}: sourceRepo 不是仓库地址：{repo!r}")

    # 输出清单
    for c in sorted(cards, key=lambda x: x["affinityThreshold"]):
        mark = "★" if c["affinityThreshold"] == 0 else " "
        print(
            f" {mark} 门槛 {c['affinityThreshold']:>3}  {c['displayName']:<14} "
            f"{c.get('sourceRepo', '')} ({c.get('sourceLicense', '')})"
        )

    print()
    if problems:
        print(f"❌ {len(problems)} 个问题：")
        for p in problems:
            print("   -", p)
        return 1

    print(f"✅ 内置人格卡包校验通过：{len(cards)} 张，字段齐全，阈值与 6 档里程碑对齐，署名完整")
    return 0


if __name__ == "__main__":
    sys.exit(main())
