#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
食物模型真机实测
================

在模拟器上验证「食物只有大白饭 / 喂食不涨好感度 / 饿只看 24 小时」三条。

跑法：
    python tools/db/check_food_model_on_device.py

依赖：
- 模拟器已连上（adb devices 能看到）
- App 已安装（debug 包即可）
- 模拟器要能撑住：内存紧的机器上用 `-gpu off` 启动，
  见 `python tools/db/start_emulator.py`

它做什么：
1. 装入背包几份大白饭
2. 记录喂食前的 affinityLevel / totalFeedCount / lastFedAt / 库存
3. 切到首页点「大白饭」，等喂食动画走完
4. 查库比对：好感度不变、投喂次数 +1、lastFedAt 被写入、库存 -1
5. 把 lastFedAt 改成 25 小时前 → 期望首页显示「饿坏了」
6. 把 lastFedAt 改成 1 小时前 → 期望首页显示饱腹数值、不再是「饿坏了」

两个必须记住的坑：

1. **注入 SQL 要走临时文件**（push + `sqlite3 db < file`）——
   直接塞进 adb shell 会被设备端 shell 把引号吃掉。
2. **用 `run-as` 而不是 `adb root`** ——
   root 会重启 adbd，模拟器里的 adbd 一断模拟器就退出。
   `run-as com.deepfocus.app` 直接进 App 沙盒，不需要 root。
"""

import os
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PKG = "com.deepfocus.app"
DB = "databases/deepfocus.db"

ADB = os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk", "platform-tools", "adb.exe")

problems = []
step = 0


def adb(*args, binary=False):
    r = subprocess.run([ADB, *args], capture_output=True)
    if binary:
        return r.stdout
    return r.stdout.decode("utf-8", errors="replace")


def ensure_device() -> None:
    """确保设备在线

    模拟器资源吃紧时会掉线（`no devices/emulators found`）。
    掉线后 adb reconnect 常常能救回来；救不回来就等它重启。
    """
    for attempt in range(6):
        out = adb("devices")
        if "emulator-" in out or "\tdevice" in out:
            return
        subprocess.run([ADB, "reconnect"], capture_output=True)
        time.sleep(4 + attempt * 3)
    raise RuntimeError("设备一直连不上，先把模拟器拉起来再跑")


def adb_checked(*args, retries: int = 4) -> str:
    """跑一条 adb 命令，出错就重试

    模拟器偶尔会掉线（`connect failed: closed` / `no devices/emulators found`）——
    模拟器冷启动、ART JIT、宿主内存吃紧都会抖一下。
    """
    last = ""
    for attempt in range(retries):
        r = subprocess.run([ADB, *args], capture_output=True)
        out = r.stdout.decode("utf-8", errors="replace")
        err = r.stderr.decode("utf-8", errors="replace")
        if r.returncode == 0:
            return out
        last = err.strip() or out.strip()
        if any(k in last for k in ("closed", "no devices", "device offline", "failed to get feature set")):
            try:
                ensure_device()
            except RuntimeError:
                pass
            else:
                time.sleep(1)
                continue
        time.sleep(2 + attempt * 2)
    raise RuntimeError(f"adb {' '.join(args)} 失败：{last}")


def sql_cmd(stmt_file: str) -> str:
    """用 run-as 在 App 沙盒里跑 sqlite3

    **不用 `adb root`** —— root 会重启 adbd，
    模拟器里的 adbd 一断，模拟器就跟着退出（这机器上必崩）。
    `run-as <pkg>` 直接进 App 的沙盒，不需要 root，还更稳。

    sqlite3 用绝对路径 —— run-as 的 PATH 可能不含 /system/bin。
    """
    return (
        f"run-as {PKG} /system/bin/sqlite3 -separator '|' {DB} < {stmt_file}"
    )


def sql(stmt: str) -> str:
    """在设备上执行一条 SQL（走临时文件，避开 shell 引号问题）

    **写库前必须先 force-stop App** —— App 运行时 Room 持有 DB 连接，
    外面的 sqlite3 写不进去（还可能被 WAL 锁挡住），而且不报错。
    """
    global step
    step += 1
    # App 开着会锁库 —— 先停掉
    adb_checked("shell", "am", "force-stop", PKG)
    time.sleep(1.5)

    local = Path(__file__).parent / f"_tmp_qa_{step}.sql"
    remote = f"/data/local/tmp/_qa_{step}.sql"
    # write_bytes 而不是 write_text —— 后者在 Windows 上会把 \n 转成 CRLF
    local.write_bytes((stmt.strip() + "\n").encode("utf-8"))
    adb_checked("push", str(local), remote)
    out = adb_checked("shell", sql_cmd(remote))
    adb("shell", f"rm -f {remote}")
    local.unlink(missing_ok=True)
    return out.strip()


def query(stmt: str) -> str:
    """查一条 SQL，返回输出（多行用 | 分隔）

    **必须走临时文件** —— 不能写成 `sqlite3 db 'SELECT ... WHERE foodId='大白饭''`：
    设备端 shell 会把外层单引号提前闭合，SQL 变成 `foodId=大白饭`，
    报「no such column」后返回空 —— 而空字符串看起来像「查到了但没数据」。
    这个坑最坑的地方在于：它不报错，断言拿着空值还以为是真的。
    """
    global step
    step += 1
    local = Path(__file__).parent / f"_tmp_qa_{step}.sql"
    remote = f"/data/local/tmp/_qa_{step}.sql"
    local.write_bytes((stmt.strip() + "\n").encode("utf-8"))
    adb_checked("push", str(local), remote)
    out = adb_checked("shell", sql_cmd(remote))
    adb("shell", f"rm -f {remote}")
    local.unlink(missing_ok=True)
    return out.strip()


def checkpoint() -> None:
    """把 WAL 里的帧落回主库

    Room 用 WAL 模式，App 写的数据可能还在 -wal 文件里没 checkpoint。
    外部 sqlite3 能读到 WAL，但为了稳（尤其是后续 pull 排查时），主动 checkpoint 一次。
    """
    sql("PRAGMA wal_checkpoint(TRUNCATE);")


def reset_feed_limit() -> None:
    """清掉「今日已喂 N 次」的计数

    喂食有**每日 5 次上限**（FishRepository.MAX_FEED_PER_DAY），
    计数存在 SharedPreferences 里，不在数据库中。
    不清的话跑第二遍就会被静默拒绝 —— 点下去什么都不发生，
    还以为 App 坏了。

    做法：把 feed_count_today_* 的值改成 0。
    用推脚本文件的方式执行 —— 多层引号（adb shell → run-as → sh -c → sed）
    在设备端很容易被吃掉，写成文件最稳。

    注意文件名：SharedPreferences 的名字**不等于包名**，
    这里是 `deep_focus_feed_limit.xml`（见 FishRepository 里的 prefs 名字）。
    """
    script = (
        "#!/system/bin/sh\n"
        f"PREFS=/data/data/{PKG}/shared_prefs/deep_focus_feed_limit.xml\n"
        "[ -f \"$PREFS\" ] || exit 0\n"
        "sed -i 's/name=\"feed_count_today_[0-9]*\" value=\"[0-9]*\"/"
        "name=\"feed_count_today_1\" value=\"0\"/g' \"$PREFS\"\n"
        "grep -o 'feed_count_today_[0-9]*\" value=\"[0-9]*\"' \"$PREFS\" || true\n"
    )
    global step
    step += 1
    local = Path(__file__).parent / f"_tmp_qa_{step}.sh"
    remote = f"/data/local/tmp/_qa_{step}.sh"
    # 必须是 LF —— write_text 在 Windows 上会把 \n 转成 CRLF，
    # 设备端 sh 读到 `exit 0\r` 会报「bad number」
    local.write_bytes(script.encode("utf-8"))
    adb_checked("push", str(local), remote)
    out = adb_checked("shell", f"run-as {PKG} sh {remote}")
    adb("shell", f"rm -f {remote}")
    local.unlink(missing_ok=True)
    print(f"  [DB ] 今日喂食计数 → {out.strip() or '(无记录)'}")


def sql_expect(stmt: str, check_stmt: str, label: str) -> bool:
    """执行一条 UPDATE 并回读校验

    **必须回读** —— 外部 sqlite3 写进 WAL 后，
    App 下次启动时 Room 可能读到旧值（进程被杀时的 WAL 恢复）。
    不回读的话，断言失败会指向错误的结论。
    """
    sql(stmt)
    got = query(check_stmt).strip()
    print(f"  [DB ] {label} → {got}")
    return bool(got)


def query_int(stmt: str) -> int:
    """查一个整数；查不到行返回 0"""
    out = query(stmt)
    return int(out) if out else 0


def launch_app(wait=12):
    adb_checked("shell", "am", "force-stop", PKG)
    time.sleep(2)
    adb_checked("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(wait)


def dump_ui():
    for _ in range(15):
        adb_checked("shell", "uiautomator", "dump", "/sdcard/_qa.xml")
        xml = adb("exec-out", "cat", "/sdcard/_qa.xml", binary=True).decode("utf-8", errors="replace")
        if "text=" in xml:
            return xml
        time.sleep(1)
    return ""


def ui_texts(xml: str) -> list:
    return [t for t in re.findall(r'text="([^"]*)"', xml) if t]


def find_bounds(xml: str, text: str):
    """找一个文本节点的中心坐标

    **只认完全在屏幕内的节点** —— 被遮挡的节点坐标可能在屏幕边缘，
    点下去点不中（实测踩过：坐标 (540, 2072) 在 1080x2400 屏幕的最底部，
    那个元素实际被裁掉了，tap 落空，喂食没发生）。
    """
    for m in re.finditer(
        rf'<node[^>]*text="{re.escape(text)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
        xml,
    ):
        x1, y1, x2, y2 = (int(g) for g in m.groups())
        if x1 <= 0 or y1 <= 0:
            continue
        # 元素要完整在屏幕内，且留出点得着的余量
        if y2 > 2200 or x2 > 1060:
            continue
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def scroll_to_food(xml: str):
    """滚到「食物库存」区域，返回含「大白饭」的 xml"""
    for _ in range(6):
        if find_bounds(xml, "大白饭"):
            return xml
        adb_checked("shell", "input", "swipe", "540", "1400", "540", "500", "300")
        time.sleep(1.5)
        xml = dump_ui()
    return xml


def check(ok: bool, label: str, detail: str = ""):
    mark = "OK " if ok else "FAIL"
    print(f"  [{mark}] {label}" + (f" — {detail}" if detail else ""))
    if not ok:
        problems.append(label + (f" — {detail}" if detail else ""))


def main() -> int:
    if not Path(ADB).exists():
        raise SystemExit(f"找不到 adb：{ADB}")
    devices = adb("devices")
    if "device" not in devices:
        raise SystemExit(
            "没有连上的设备。\n"
            f"先跑：python tools/db/start_emulator.py\n"
            f"当前 adb devices：\n{devices}"
        )

    print("=== A. 背包里只有大白饭 ===")
    sql("INSERT OR REPLACE INTO food_inventory (foodId, count, updatedAt) "
        "VALUES ('大白饭', 3, 1700000000000);")
    catalog = query("SELECT foodId FROM food_catalog;")
    check(catalog == "大白饭", "食物目录只有大白饭", f"实际：{catalog!r}")
    stale = query("SELECT foodId FROM food_inventory WHERE foodId != '大白饭';")
    check(stale == "", "背包里没有已下架食物的存货", f"实际：{stale!r}")

    print("\n=== B. 喂食不涨好感度 ===")
    # 先停 App 把库存备好（App 开着写不进去），再启动去点
    sql("INSERT OR REPLACE INTO food_inventory (foodId, count, updatedAt) "
        "VALUES ('大白饭', 3, 1700000000000);")
    # 喂食有每日 5 次上限，计数在 SharedPreferences 里 —— 不清会被静默拒绝
    reset_feed_limit()
    before_stock = query_int("SELECT count FROM food_inventory WHERE foodId='大白饭';")
    before_aff = query("SELECT affinityLevel FROM user_fish;")
    before_feed = query("SELECT totalFeedCount FROM user_fish;")
    before_last = query("SELECT lastFedAt FROM fish_state;")

    launch_app()
    xml = scroll_to_food(dump_ui())
    pos = find_bounds(xml, "大白饭")
    if pos is None:
        check(False, "首页找到大白饭按钮", "没找到")
        return report()
    check(True, "首页找到大白饭按钮", str(pos))

    adb_checked("shell", "input", "tap", str(pos[0]), str(pos[1]))
    time.sleep(6)

    after_aff = query("SELECT affinityLevel FROM user_fish;")
    after_feed = query("SELECT totalFeedCount FROM user_fish;")
    after_last = query("SELECT lastFedAt FROM fish_state;")
    after_stock = query_int("SELECT count FROM food_inventory WHERE foodId='大白饭';")

    check(before_aff == after_aff, "好感度不变（喂食不涨好感度）", f"{before_aff} → {after_aff}")
    check(int(after_feed) == int(before_feed) + 1, "投喂次数 +1", f"{before_feed} → {after_feed}")
    check(after_last != before_last and after_last != "0", "lastFedAt 被写入", f"{before_last} → {after_last}")
    check(after_stock == before_stock - 1, "库存 -1", f"{before_stock} → {after_stock}")

    print("\n=== C. 饿 = 距上次喂食超 24 小时 ===")
    # 25 小时前喂过 → 哪怕饱腹度 100 也该说饿
    sql("UPDATE fish_state SET lastFedAt = strftime('%s','now')*1000 - 25*60*60*1000;")
    rows = query("SELECT count(*), lastFedAt FROM fish_state;").strip()
    print(f"  [DB ] fish_state 行数/lastFedAt → {rows}")
    launch_app()
    texts = ui_texts(dump_ui())
    check("饿坏了" in texts, "25 小时没喂 → 显示「饿坏了」", str(texts[:10]))
    checkpoint()
    satiety = query_int("SELECT satiety FROM fish_state;")
    check(satiety > 85, "但饱腹度其实很高（证明判定不看饱腹度）", f"satiety={satiety}")

    # 1 小时前喂过 → 不该说饿
    sql("UPDATE fish_state SET lastFedAt = strftime('%s','now')*1000 - 60*60*1000;")
    launch_app()
    texts = ui_texts(dump_ui())
    check("饿坏了" not in texts, "1 小时内喂过 → 不再显示「饿坏了」", str(texts[:8]))

    return report()


def report() -> int:
    print()
    if problems:
        print(f"❌ 食物模型真机实测有 {len(problems)} 个问题：")
        for p in problems:
            print("   -", p)
        return 1
    print("✅ 食物模型真机实测全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
