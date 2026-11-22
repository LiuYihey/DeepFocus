#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
食物模型 + 动作标签校验脚本
===========================

锁住三条容易悄悄跑偏的不变量：

1. **食物只有大白饭一种**
   - SeedData 只种一条
   - assets/foods 目录里只有「大白饭.png」
   - FoodCatalogEntity 不再有 rarity / affinityBonus / dropRate 三列
   - 喂食**涨一点好感度**（+1）：UserFishDao.incrementFeed 带 affinityBonus 参数

2. **没有「心情（mood）」数值维度**
   - FishStateEntity 里没有 mood 字段
   - FishMoodResolver 里没有 GLUM 档
   - FishMoodResolver.resolve 的参数里没有 mood
   - （注意：状态**词**是另一回事 —— `MoodStore` 里那个字符串，
     由模型通过 `set_mood` 工具产出，不是可正可负的数值。）

3. **吃的动作必须挂 FEED，而且要够多**
   - `chi-baifan` / `chi-wancan` 必须是 FEED
   - 2026-11-18 用户决定：「点击大白饭后大肥鱼吃饭的表情包只有一个吗，
     不可以有多个吃东西的表情包随机出现吗」
     → 所有「在吃东西」的动作都要挂 FEED，不能只有 2 个
   - `chi-token`（举硬币端详）**不是**吃东西，仍不能挂 FEED

用法：
    python tools/db/check_food_and_action_model.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "app" / "src" / "main" / "java" / "com" / "deepfocus" / "app"
FOODS_DIR = ROOT / "app" / "src" / "main" / "assets" / "foods"

SEED_DATA = SRC / "data" / "SeedData.kt"
FOOD_ENTITY = SRC / "data" / "local" / "entity" / "FoodCatalogEntity.kt"
FISH_STATE_ENTITY = SRC / "data" / "local" / "entity" / "FishStateEntity.kt"
MOOD_RESOLVER = SRC / "ai" / "FishMoodResolver.kt"
STATE_ENGINE = SRC / "ai" / "FishStateEngine.kt"
USER_FISH_DAO = SRC / "data" / "local" / "dao" / "UserFishDao.kt"
PET_ACTION = SRC / "pet" / "PetAction.kt"

# 必须挂 FEED 的动作（2026-11-18 用户要求「多个吃东西的表情包随机出现」）
ALLOWED_FEED = {
    "chi-baifan",          # 抱着一大碗白饭猛吃
    "chi-wancan",          # 端着饭碗认真吃
    "chi-wucan",           # 捧着午餐盒吃饭
    "chi-changshoumian",   # 抱着超长的手擀面拉扯着吃
    "chi-zongzi",          # 捧着一只粽子剥
    "chijiaozi",           # 捧着饺子蘸醋吃
    "chitangyuan",         # 举着一颗汤圆，小口咬开
    "chi-labazhou",        # 端着一碗粥呼噜呼噜喝
    "chi-xigua",           # 抱着半个西瓜啃
    "chi-tanghulu",        # 举着一串糖葫芦啃
    "chi-niangao",         # 捧着年糕大口咬
    "chi-qingtuan",        # 举着一枚青团小心咬
    "chi-chongyanggao",    # 小口啃重阳糕
    "chi-dazhaxie",        # 举着大虾和大蟹一起啃
    "chi-zaocan",          # 端着早茶慢慢吃
    "chi-bingqilin-ronghua",  # 抱着冰麒麟冰块
    "dakou-chi-lingshi",   # 抓起零食大口往嘴里塞
}

# 不能挂 FEED 的动作 —— 不是在吃东西
FORBIDDEN_FEED = {
    "chi-token": "举硬币端详，不是在吃东西",
}


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def parse_actions(src: str) -> list:
    """抠出 PetAction("id", 序号, "描述", setOf(...))"""
    out = []
    pattern = re.compile(
        r'PetAction\(\s*"([^"]+)"\s*,\s*(\d+)\s*,\s*"([^"]*)"\s*,\s*setOf\(([^)]*)\)\s*\)'
    )
    for m in pattern.finditer(src):
        tags = re.findall(r"PetActionTag\.(\w+)", m.group(4))
        out.append({"id": m.group(1), "index": int(m.group(2)), "desc": m.group(3), "tags": tags})
    return out


def main() -> int:
    problems = []

    # ===== 1. 食物只有大白饭 =====
    seed = read(SEED_DATA)
    food_entity = read(FOOD_ENTITY)

    # 1a. SeedData 只种一条
    seed_block = seed.split("getSeedFoods", 1)[-1].split("fun ", 1)[0]
    seeded = re.findall(r'foodId\s*=\s*"([^"]+)"', seed_block)
    if seeded != ["大白饭"]:
        problems.append(f"SeedData 的食物种子应该只有 ['大白饭']，实际 {seeded}")

    # 1b. assets/foods 只有大白饭
    if FOODS_DIR.exists():
        pngs = sorted(p.name for p in FOODS_DIR.glob("*.png"))
        if pngs != ["大白饭.png"]:
            problems.append(f"assets/foods 应该只剩 大白饭.png，实际 {pngs}")
    else:
        problems.append("assets/foods 目录不存在")

    # 1c. FoodCatalogEntity 不该再有那三列
    for dead in ("rarity", "affinityBonus", "dropRate"):
        if re.search(rf"\bval\s+{dead}\b", food_entity):
            problems.append(f"FoodCatalogEntity 不该再有 {dead} 字段（单一食物用不上）")

    # 1d. 喂食要涨一点好感度（2026-11-18 用户决定：「点击大白饭后饱腹度增加，
    #     同时好感度也可以慢慢增加」，并且要能解锁人格卡）
    #
    # 实现方式：incrementFeed() 只加投喂次数，好感度走 addAffinity(amount)，
    # 由调用方（FishRepository.feedFishWithFood）一起调。
    dao = read(USER_FISH_DAO)
    if "fun incrementFeed(" not in dao:
        problems.append("UserFishDao 里找不到 incrementFeed")
    if "fun addAffinity(" not in dao:
        problems.append("UserFishDao 里找不到 addAffinity")

    # 真正把关的地方在 Repository：喂食流程里必须同时调 addAffinity
    repo_file = SRC / "data" / "repository" / "FishRepository.kt"
    if repo_file.exists():
        repo = read(repo_file)
        feed_fn = re.search(r"fun feedFishWithFood\((.*?)\n    \}", repo, re.S)
        if feed_fn is None:
            problems.append("FishRepository 里找不到 feedFishWithFood")
        elif "addAffinity" not in feed_fn.group(1):
            problems.append(
                "feedFishWithFood 里没调 addAffinity —— 喂食要涨好感度"
                "（2026-11-18 用户决定）"
            )

    # ===== 2. 没有 mood 维度 =====
    fs = read(FISH_STATE_ENTITY)
    if re.search(r"\bval\s+mood\b", fs):
        problems.append("FishStateEntity 里不该有 mood 字段")

    resolver = read(MOOD_RESOLVER)
    if re.search(r"\bGLUM\b", resolver):
        problems.append("FishMoodResolver 里不该有 GLUM 档（mood 已删除）")
    # resolve 的参数列表里不该有 mood
    rm = re.search(r"fun resolve\((.*?)\)\s*:\s*FishMood", resolver, re.S)
    if rm is None:
        problems.append("FishMoodResolver 里找不到 resolve")
    elif re.search(r"\bmood\b", rm.group(1)):
        problems.append(f"resolve 的参数里不该有 mood：{rm.group(1).strip()[:80]}")

    # 2b. 「饿」的判定只有一份实现
    # 「距上次喂食超过 24 小时」这个判据如果散落在多处，
    # 改一处忘一处就会出现「UI 说饿、主动行为不喊饿」这类不一致。
    engine_src = read(STATE_ENGINE)
    impl_count = len(re.findall(r"fun isHungry\s*\(", engine_src))
    if impl_count != 1:
        problems.append(
            f"FishStateEngine 里应该有且只有 1 个 isHungry 实现，实际 {impl_count} 个"
        )
    if "HUNGRY_AFTER_MILLIS = 24 * 60 * 60 * 1000L" not in engine_src:
        problems.append("FishStateEngine 的 HUNGRY_AFTER_MILLIS 应该是 24 小时")

    # 2c. 别的地方不该自己再实现一遍判定
    for path in SRC.rglob("*.kt"):
        if path.name == "FishStateEngine.kt":
            continue
        text = read(path)
        if "lastFedAt <= 0L" in text:
            problems.append(
                f"{path.relative_to(ROOT)} 里自己判了 lastFedAt，"
                "应该走 FishStateEngine.isHungry()"
            )

    # ===== 3. FEED 标签 =====
    actions = parse_actions(read(PET_ACTION))
    if not actions:
        problems.append("从 PetAction.kt 里没解析出任何动作")

    feed_ids = {a["id"] for a in actions if "FEED" in a["tags"]}
    for aid in ALLOWED_FEED:
        if aid not in feed_ids:
            problems.append(f"{aid}（白饭类）必须挂 FEED")

    for aid, why in FORBIDDEN_FEED.items():
        if aid in feed_ids:
            problems.append(f"{aid} 不该挂 FEED —— {why}")

    extra = feed_ids - ALLOWED_FEED
    if extra:
        problems.append(f"FEED 标签上多了不该有的动作：{sorted(extra)}")

    # ===== 4. 喂食只从「能上台」的动作里挑 =====
    #
    # 喂食用发牌堆挑（一轮之内不重复），
    # 候选必须是能上台的动作 —— 不然首页上会出现「小一号」的角色，
    # 破坏「舞台上的角色高度一致」这条视觉纪律。
    stage_file = SRC / "pet" / "StageCatalog.kt"
    if not stage_file.exists():
        problems.append("找不到 StageCatalog.kt")
    else:
        stage_body = read(stage_file).split(
            "STAGE_ACTIONS: Set<String> = setOf(")[1]
        stage_ids = set(re.findall(r'"([^"]+)"', stage_body))
        stage_feed = feed_ids & stage_ids
        off_stage_feed = feed_ids - stage_ids
        if len(stage_feed) < 8:
            problems.append(
                f"能上台的吃的动作只有 {len(stage_feed)} 个，不够开一轮（至少 8 个）"
            )
        print(f"能上台的吃的动作 {len(stage_feed)} 个，上不了台的 {sorted(off_stage_feed)}")

        # onFeed 必须真的过滤了白名单
        engine = SRC / "pet" / "PetPlayEngine.kt"
        if engine.exists():
            eng = read(engine)
            on_feed = re.search(r"fun onFeed\(\):\s*PetAction\?\s*\{(.*?)\n    \}", eng, re.S)
            if on_feed is None:
                problems.append("PetPlayEngine 里找不到 onFeed")
            elif "StageCatalog.STAGE_ACTIONS" not in on_feed.group(1):
                problems.append(
                    "onFeed 没按 StageCatalog.STAGE_ACTIONS 过滤 —— "
                    "太宽的动作会在首页上显得小一号"
                )
            elif "ShuffleBag" not in eng and "feedBag" not in eng:
                problems.append("PetPlayEngine 里没有喂食的发牌堆 —— 喂食会重复")

    # ===== 输出 =====
    print(f"动作库 {len(actions)} 个，FEED 标签 {len(feed_ids)} 个：{sorted(feed_ids)}")
    print(f"食物目录：{seeded}")
    print()

    if problems:
        print(f"❌ {len(problems)} 个问题：")
        for p in problems:
            print("   -", p)
        return 1

    print(
        "✅ 食物与动作模型校验通过："
        "食物只有大白饭、无 mood 数值维度、吃的动作都挂 FEED、"
        "喂食只挑能上台的动作且用发牌堆"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
