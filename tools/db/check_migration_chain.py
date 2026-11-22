#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Room 迁移链实测脚本
==================

用 Room 导出的 schema json 当基线，在 SQLite 里模拟跑一遍完整迁移链，
逐表逐列比对结果与目标 schema 是否一致。

三个场景都要过：
1. **v1 老库升上来** —— 从 v1 建库，一路迁到 v13
2. **全新安装** —— 直接按 v13 的 createSql 建库，结果必须与 schema 一致
3. **上一个版本老库升上来** —— 从 v12 建库，迁到 v13

迁移 SQL 不在这里抄 —— 直接从 AppDatabase.kt 里抠出来跑，
保证脚本测的就是 App 真正执行的那段 SQL。

用法：
    python tools/db/check_migration_chain.py
"""

import json
import os
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCHEMA_DIR = ROOT / "app" / "schemas" / "com.deepfocus.app.data.local.AppDatabase"
APP_DB = ROOT / "app" / "src" / "main" / "java" / "com" / "deepfocus" / "app" / "data" / "local" / "AppDatabase.kt"

# 迁移函数名 → (from, to)
MIGRATION_RE = re.compile(
    r"val\s+(MIGRATION_(\d+)_(\d+))\s*=\s*object\s*:\s*Migration\(\s*(\d+)\s*,\s*(\d+)\s*\)\s*\{",
    re.MULTILINE,
)


def load_schema(version: int) -> dict:
    path = SCHEMA_DIR / f"{version}.json"
    if not path.exists():
        raise SystemExit(f"缺少 schema：{path}")
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def extract_migrations() -> dict:
    """
    从 AppDatabase.kt 里抽出每个迁移函数体，按 SQL 语句切分。

    只认 `database.execSQL(...)` 的字面量参数 —— 迁移里没有带变量的
    execSQL（v12→13 之前都是纯 SQL），所以直接正则抠出来就能跑。
    """
    source = APP_DB.read_text(encoding="utf-8")
    migrations = {}

    for match in MIGRATION_RE.finditer(source):
        name = match.group(1)
        start = match.end()
        # 函数体结束：下一个 `val MIGRATION_` 或 companion object 结束
        nxt = source.find("val MIGRATION_", start)
        end = nxt if nxt != -1 else len(source)
        body = source[start:end]

        sqls = []
        for call in re.finditer(r"database\.execSQL\(\s*(?:\"\"\"|\")", body):
            quote = '"""' if body[call.end() - 3:call.end()] == '"""' else '"'
            text_start = call.end()
            text_end = body.find(quote, text_start)
            if text_end == -1:
                continue
            raw = body[text_start:text_end]
            if quote == '"""':
                # Kotlin 的 trimIndent()：去掉首尾空行，再去掉每行公共缩进
                lines = raw.split("\n")
                while lines and not lines[0].strip():
                    lines.pop(0)
                while lines and not lines[-1].strip():
                    lines.pop()
                indent = min(
                    (len(ln) - len(ln.lstrip()) for ln in lines if ln.strip()),
                    default=0,
                )
                raw = "\n".join(ln[indent:] if ln.strip() else "" for ln in lines)
            else:
                raw = raw.replace('\\"', '"').replace("\\n", "\n")
            sqls.append(raw.strip())
        migrations[name] = sqls

    return migrations


def build_db_from_schema(conn: sqlite3.Connection, version: int) -> None:
    """按 schema json 的 createSql 建库"""
    schema = load_schema(version)
    for entity in schema["database"]["entities"]:
        sql = entity["createSql"].replace("${TABLE_NAME}", entity["tableName"])
        conn.execute(sql)

    # v7 ~ v10 的历史库里，用户表的**物理名**是 `user_cat`，
    # 不是实体里的 `user_fish` —— 导出 schema 反映的是「实体现在叫什么」，
    # 不是「老库里实际叫什么」。MIGRATION_10_11 修的就是这个改名。
    # 模拟老库必须按真实物理名建，否则 MIGRATION_4_5 / 10_11 找不到表。
    if version <= 10:
        conn.execute("ALTER TABLE user_fish RENAME TO user_cat")

    # room_master_table：Room 用它存 identity_hash
    conn.execute(
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
    )
    conn.execute(
        "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
        (schema["database"]["identityHash"],),
    )
    conn.commit()


def table_columns(conn: sqlite3.Connection, table: str) -> list:
    cur = conn.execute(f"PRAGMA table_info(`{table}`)")
    cols = []
    for cid, name, ctype, notnull, dflt, pk in cur.fetchall():
        cols.append(
            {
                "name": name,
                "type": ctype.upper(),
                "notNull": bool(notnull),
                "default": dflt,
                "pk": bool(pk),
            }
        )
    return cols


def snapshot(conn: sqlite3.Connection) -> dict:
    cur = conn.execute(
        "SELECT name FROM sqlite_master "
        "WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table'"
    )
    tables = [r[0] for r in cur.fetchall()]
    return {t: table_columns(conn, t) for t in sorted(tables)}


def expected_snapshot(version: int) -> dict:
    schema = load_schema(version)
    out = {}
    for entity in schema["database"]["entities"]:
        cols = []
        for f in entity["fields"]:
            cols.append(
                {
                    "name": f["columnName"],
                    "type": f["affinity"].upper(),
                    "notNull": f["notNull"],
                    "default": None,
                    "pk": f["columnName"] in entity["primaryKey"]["columnNames"],
                }
            )
        table = entity["tableName"]
        # v7 ~ v10 的历史库里，用户表的物理名是 user_cat（见 build_db_from_schema）。
        # 导出 schema 记的是实体现在叫什么，不是老库里实际叫什么。
        if version <= 10 and table == "user_fish":
            table = "user_cat"
        out[table] = cols
    return out


def compare(actual: dict, expected: dict, label: str) -> list:
    problems = []

    only_actual = set(actual) - set(expected)
    only_expected = set(expected) - set(actual)
    for t in sorted(only_actual):
        problems.append(f"[{label}] 多出表：{t}")
    for t in sorted(only_expected):
        problems.append(f"[{label}] 缺表：{t}")

    for t in sorted(set(actual) & set(expected)):
        a_cols = {c["name"]: c for c in actual[t]}
        e_cols = {c["name"]: c for c in expected[t]}
        for name in sorted(set(e_cols) - set(a_cols)):
            problems.append(f"[{label}] {t} 缺列：{name}")
        for name in sorted(set(a_cols) - set(e_cols)):
            problems.append(f"[{label}] {t} 多出列：{name}")
        for name in sorted(set(a_cols) & set(e_cols)):
            a, e = a_cols[name], e_cols[name]
            if a["type"] != e["type"]:
                problems.append(f"[{label}] {t}.{name} 类型不一致：{a['type']} vs {e['type']}")
            if a["notNull"] != e["notNull"]:
                problems.append(
                    f"[{label}] {t}.{name} notNull 不一致：{a['notNull']} vs {e['notNull']}"
                )
            if a["pk"] != e["pk"]:
                problems.append(f"[{label}] {t}.{name} 主键不一致：{a['pk']} vs {e['pk']}")

    return problems


def apply_migrations(conn: sqlite3.Connection, migrations: dict, from_v: int, to_v: int) -> None:
    for name in sorted(migrations, key=lambda n: (int(n.split("_")[1]), int(n.split("_")[2]))):
        f, t = (int(x) for x in name.split("_")[1:])
        if f != from_v or t != to_v:
            continue
        for sql in migrations[name]:
            conn.execute(sql)
    conn.commit()


def scenario_fresh_install(target: int) -> list:
    """场景 2：全新安装 —— 直接按目标 schema 建库，比对是否一致"""
    conn = sqlite3.connect(":memory:")
    build_db_from_schema(conn, target)
    problems = compare(snapshot(conn), expected_snapshot(target), "全新安装")
    conn.close()
    return problems


def scenario_upgrade(migrations: dict, from_v: int, target: int, seed_persona: bool) -> list:
    """场景 1 / 3：老库升上来"""
    conn = sqlite3.connect(":memory:")
    build_db_from_schema(conn, from_v)

    if seed_persona:
        seed_persona_data(conn, from_v)
    seed_state_data(conn, from_v)

    v = from_v
    while v < target:
        nxt = v + 1
        apply_migrations(conn, migrations, v, nxt)
        v = nxt

    problems = compare(snapshot(conn), expected_snapshot(target), f"v{from_v}→v{target}")

    # 数据搬运校验
    if from_v == 12 and seed_persona:
        problems += check_persona_moved(conn, "v12→v13")
    if from_v == 13:
        problems += check_v13_to_v14(conn)
    if from_v == 14:
        problems += check_v14_to_v15(conn)

    conn.close()
    return problems


def seed_state_data(conn, from_v: int) -> None:
    """灌 fish_state / food_catalog / food_inventory 的老数据

    每次升级都要灌 —— 迁移丢行是最高频的 bug，必须每次都验。
    """
    if from_v >= 13:
        # fish_state：按来源版本的结构灌老行，验证迁移搬数据不丢列
        # v13：id/satiety/mood/energy/isSleeping/sleepStartedAt/totalSleepMillis/
        #      lastProactiveAt/updatedAt
        # v14：mood 已删，多了 lastFedAt
        if from_v == 13:
            conn.execute(
                """INSERT OR REPLACE INTO fish_state
                   (id, satiety, mood, energy, isSleeping, sleepStartedAt,
                    totalSleepMillis, lastProactiveAt, updatedAt)
                   VALUES (1, 70, 55, 80, 0, 0, 0, 0, 1700000000000)"""
            )
        elif from_v == 14:
            # v14：验证 v14→v15 删 energy 列时其它列（含 lastFedAt）不丢
            conn.execute(
                """INSERT OR REPLACE INTO fish_state
                   (id, satiety, energy, isSleeping, sleepStartedAt,
                    totalSleepMillis, lastFedAt, lastProactiveAt, updatedAt)
                   VALUES (1, 70, 80, 0, 0, 0, 1700000000000, 0, 1700000000000)"""
            )
        else:
            # v15 起 energy 列已删（v14→v15 迁移删的），不能再往里插
            conn.execute(
                """INSERT OR REPLACE INTO fish_state
                   (id, satiety, isSleeping, sleepStartedAt,
                    totalSleepMillis, lastFedAt, lastProactiveAt, updatedAt)
                   VALUES (1, 70, 0, 0, 0, 1700000000000, 0, 1700000000000)"""
            )
        # food_catalog：老库里有 4 种食物，迁移后只该留大白饭
        # v14 起 rarity/affinityBonus/dropRate 已删（单一食物用不上）
        if from_v < 14:
            conn.execute(
                """INSERT OR REPLACE INTO food_catalog
                   (foodId, displayName, rarity, affinityBonus, dropRate, iconAsset)
                   VALUES ('大白饭', '大白饭', 'common', 1, 0.4, 'food_bigrice')"""
            )
            conn.execute(
                """INSERT OR REPLACE INTO food_catalog
                   (foodId, displayName, rarity, affinityBonus, dropRate, iconAsset)
                   VALUES ('冻干', '冻干', 'rare', 3, 0.2, 'food_fishjerky')"""
            )
        else:
            conn.execute(
                """INSERT OR REPLACE INTO food_catalog
                   (foodId, displayName, iconAsset)
                   VALUES ('大白饭', '大白饭', 'food_bigrice')"""
            )
            conn.execute(
                """INSERT OR REPLACE INTO food_catalog
                   (foodId, displayName, iconAsset)
                   VALUES ('冻干', '冻干', 'food_fishjerky')"""
            )
        conn.execute(
            """INSERT OR REPLACE INTO food_inventory (foodId, count, updatedAt)
               VALUES ('大白饭', 2, 1700000000000)"""
        )
        conn.execute(
            """INSERT OR REPLACE INTO food_inventory (foodId, count, updatedAt)
               VALUES ('冻干', 1, 1700000000000)"""
        )
    conn.commit()


def seed_persona_data(conn, from_v: int) -> None:
    """按来源版本的结构灌入人格数据

    - v11 / v12：老 `persona` 单表（v12→v13 会把它搬进 persona_state）
    - v13+：`persona_state` + `persona_cards` 两张表已经拆好了
    """
    if from_v >= 13:
        conn.execute(
            """INSERT OR REPLACE INTO persona_state
               (id, baseCardId, selectedTierThreshold, identity, traits, speechStyle,
                catchphrases, backstory, taboos, proactiveMotivation, temperature,
                userAlias, updatedAt)
               VALUES (1, NULL, 0, '我是老用户的鱼', '摸鱼,傲娇', '短句为主',
                       '呜哇,好耶', '住在手机里', '说它胖', '你摸鱼太久了', 0.88,
                       '鱼片', 1700000000000)"""
        )
        conn.execute(
            """INSERT OR REPLACE INTO persona_cards
               (id, displayName, tagline, identity, traits, speechStyle, catchphrases,
                backstory, taboos, proactiveMotivation, temperature, createdAt)
               VALUES ('custom_seed', '我自己的卡', '自用', '自定义身份', '傲娇',
                       '短句', '哼', '来历', '禁忌', '动机', 1.2, 1700000000000)"""
        )
    else:
        # v11 起 persona 表才有 proactiveMotivation；v12 的 persona 结构见 12.json
        conn.execute(
            """INSERT OR REPLACE INTO persona
               (id, name, identity, traits, speechStyle, catchphrases, backstory,
                taboos, userAlias, proactiveMotivation, temperature, enabled, updatedAt)
               VALUES (1, '大肥鱼', '我是老用户的鱼', '摸鱼,傲娇', '短句为主',
                       '呜哇,好耶', '住在手机里', '说它胖', '鱼片',
                       '你摸鱼太久了', 0.88, 1, 1700000000000)"""
        )
    conn.commit()


def check_persona_moved(conn, label: str) -> list:
    """v12→v13：老 persona 表的数据要搬进 persona_state"""
    problems = []
    row = conn.execute(
        "SELECT identity, traits, userAlias, temperature, baseCardId FROM persona_state"
    ).fetchone()
    if row is None:
        problems.append(f"[{label}] persona_state 没搬进旧人格")
        return problems
    if row[0] != "我是老用户的鱼":
        problems.append(f"[{label}] identity 没搬对：{row[0]!r}")
    if row[1] != "摸鱼,傲娇":
        problems.append(f"[{label}] traits 没搬对：{row[1]!r}")
    if row[2] != "鱼片":
        problems.append(f"[{label}] userAlias 没搬对：{row[2]!r}")
    if abs(row[3] - 0.88) > 1e-6:
        problems.append(f"[{label}] temperature 没搬对：{row[3]!r}")
    if row[4] is not None:
        problems.append(f"[{label}] baseCardId 应为 NULL（跟随内置卡）：{row[4]!r}")

    # 鱼名的真源是 user_fish.name，不在 persona_state 里
    cols = {c["name"] for c in snapshot(conn)["persona_state"]}
    if "fishName" in cols:
        problems.append(f"[{label}] persona_state 不该有 fishName 列（真源是 user_fish.name）")

    left = conn.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='persona'"
    ).fetchone()
    if left is not None:
        problems.append(f"[{label}] 旧 persona 表没删掉")
    return problems


def check_v13_to_v14(conn) -> list:
    """v13→v14：删 mood / 加 lastFedAt / 食物表砍列

    三件事都得对：
    1. fish_state 里不再有 mood，但其它列的数据要保住
    2. food_catalog 只剩 foodId / displayName / iconAsset 三列，
       且大白饭的行还在
    """
    problems = []

    # 1. fish_state
    cols = {c["name"]: c for c in snapshot(conn)["fish_state"]}
    if "mood" in cols:
        problems.append("[v13→v14] fish_state 不该再有 mood 列")
    if "lastFedAt" not in cols:
        problems.append("[v13→v14] fish_state 缺 lastFedAt 列")
    row = conn.execute(
        "SELECT satiety, energy, isSleeping, lastFedAt FROM fish_state WHERE id = 1"
    ).fetchone()
    if row is None:
        problems.append("[v13→v14] fish_state 行丢了")
    else:
        if row[0] != 70:
            problems.append(f"[v13→v14] satiety 没搬对：{row[0]!r}")
        if row[1] != 80:
            problems.append(f"[v13→v14] energy 没搬对：{row[1]!r}")
        if row[2] != 0:
            problems.append(f"[v13→v14] isSleeping 没搬对：{row[2]!r}")
        if row[3] != 0:
            problems.append(f"[v13→v14] lastFedAt 应为 0（从没喂过=饿）：{row[3]!r}")

    # 2. food_catalog
    fcols = {c["name"] for c in snapshot(conn)["food_catalog"]}
    for dead in ("rarity", "affinityBonus", "dropRate"):
        if dead in fcols:
            problems.append(f"[v13→v14] food_catalog 不该再有 {dead} 列")
    rice = conn.execute(
        "SELECT foodId, displayName, iconAsset FROM food_catalog WHERE foodId = '大白饭'"
    ).fetchone()
    if rice is None:
        problems.append("[v13→v14] 大白饭的食物目录行丢了")
    else:
        if rice[1] != "大白饭":
            problems.append(f"[v13→v14] 大白饭 displayName 没搬对：{rice[1]!r}")
        if rice[2] != "food_bigrice":
            problems.append(f"[v13→v14] 大白饭 iconAsset 没搬对：{rice[2]!r}")

    # 已下架的食物不该还留在目录里 —— 图删了，目录里留着就是空白格子
    stale = conn.execute(
        "SELECT foodId FROM food_catalog WHERE foodId != '大白饭'"
    ).fetchall()
    if stale:
        problems.append(
            "[v13→v14] 已下架食物还在目录里："
            + ", ".join(r[0] for r in stale)
        )

    # 背包里指向已删图片的存货也要清掉
    stale_inv = conn.execute(
        "SELECT foodId FROM food_inventory WHERE foodId != '大白饭'"
    ).fetchall()
    if stale_inv:
        problems.append(
            "[v13→v14] 已下架食物的存货没清："
            + ", ".join(r[0] for r in stale_inv)
        )

    return problems


def check_v14_to_v15(conn) -> list:
    """v14→v15：删 energy 列 + 删 quotes 表

    两件事都得对：
    1. fish_state 里不再有 energy，但其它列（含 lastFedAt）的数据要保住
    2. quotes 表整个消失
    """
    problems = []

    # 1. fish_state
    cols = {c["name"]: c for c in snapshot(conn)["fish_state"]}
    if "energy" in cols:
        problems.append("[v14→v15] fish_state 不该再有 energy 列")
    row = conn.execute(
        "SELECT satiety, isSleeping, lastFedAt, totalSleepMillis FROM fish_state WHERE id = 1"
    ).fetchone()
    if row is None:
        problems.append("[v14→v15] fish_state 行丢了")
    else:
        if row[0] != 70:
            problems.append(f"[v14→v15] satiety 没搬对：{row[0]!r}")
        if row[1] != 0:
            problems.append(f"[v14→v15] isSleeping 没搬对：{row[1]!r}")
        if row[2] != 1700000000000:
            problems.append(f"[v14→v15] lastFedAt 没搬对：{row[2]!r}")
        if row[3] != 0:
            problems.append(f"[v14→v15] totalSleepMillis 没搬对：{row[3]!r}")

    # 2. quotes 表
    tables = {t[0] for t in conn.execute(
        "SELECT name FROM sqlite_master WHERE type = 'table'").fetchall()}
    if "quotes" in tables:
        problems.append("[v14→v15] quotes 表没删掉")

    return problems


def scenario_upgrade_without_persona(migrations: dict, from_v: int, target: int) -> list:
    """老库里根本没有 persona 行（用户没改过人格）时也不该炸"""
    conn = sqlite3.connect(":memory:")
    build_db_from_schema(conn, from_v)
    seed_state_data(conn, from_v)
    v = from_v
    while v < target:
        apply_migrations(conn, migrations, v, v + 1)
        v += 1
    problems = compare(snapshot(conn), expected_snapshot(target), f"v{from_v}→v{target}(无 persona 行)")
    count = conn.execute("SELECT COUNT(*) FROM persona_state").fetchone()[0]
    if count != 0:
        problems.append(f"[v{from_v}→v{target}] 无 persona 行时不该凭空造状态行：{count} 行")
    conn.close()
    return problems


def main() -> int:
    if not APP_DB.exists():
        raise SystemExit(f"找不到 {APP_DB}")

    migrations = extract_migrations()
    if not migrations:
        raise SystemExit("没从 AppDatabase.kt 里解析出任何迁移")

    print(f"解析到 {len(migrations)} 个迁移：{', '.join(sorted(migrations))}")

    available = sorted(
        int(p.stem) for p in SCHEMA_DIR.glob("*.json") if p.stem.isdigit()
    )
    target = available[-1]
    oldest = available[0]
    print(f"目标 schema 版本：v{target}（可用 schema：v{oldest} ~ v{target}）\n")

    all_problems = []

    # 场景 1：最老的可用老库一路升上来
    print(f"▶ 场景 1：v{oldest} 老库一路升到 v{target}")
    p1 = scenario_upgrade(migrations, oldest, target, seed_persona=False)
    all_problems += p1
    print("   " + ("OK" if not p1 else f"{len(p1)} 个问题"))

    # 场景 2：全新安装
    print(f"▶ 场景 2：全新安装（按 v{target} 的 createSql 建库）")
    p2 = scenario_fresh_install(target)
    all_problems += p2
    print("   " + ("OK" if not p2 else f"{len(p2)} 个问题"))

    # 场景 3：上一个版本老库升上来（带 persona 数据）
    print(f"▶ 场景 3：v{target - 1} 老库升到 v{target}（带人格数据）")
    p3 = scenario_upgrade(migrations, target - 1, target, seed_persona=True)
    all_problems += p3
    print("   " + ("OK" if not p3 else f"{len(p3)} 个问题"))

    # 场景 4：上一版本老库没有 persona 行
    print(f"▶ 场景 4：v{target - 1} 老库没有 persona 行")
    p4 = scenario_upgrade_without_persona(migrations, target - 1, target)
    all_problems += p4
    print("   " + ("OK" if not p4 else f"{len(p4)} 个问题"))

    # 场景 5：逐版本链式验证（每一对相邻版本单独跑一遍）
    print("▶ 场景 5：逐版本链式验证")
    for v in available[:-1]:
        p = scenario_upgrade(migrations, v, v + 1, seed_persona=False)
        all_problems += p
        print(f"   v{v}→v{v + 1}: " + ("OK" if not p else f"{len(p)} 个问题"))

    print()
    if all_problems:
        print(f"❌ 迁移链有 {len(all_problems)} 个问题：")
        for p in all_problems:
            print("   -", p)
        return 1

    print(
        f"✅ 迁移链全通：v{oldest}→v{target}、全新安装、"
        f"v{target - 1}→v{target}（含/不含 persona 行）、逐版本链式验证全过"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
