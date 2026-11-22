#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
人格卡状态机实测（模拟器 / root 设备）
========================================

在真机上跑一遍人格卡的核心规则，改完代码就能验证：

A. 内置卡随好感度自动切（用户不干预）
B. 用户切到自定义卡 -> 它生效
C. 跨档时强制切回内置卡
D. 覆盖层被继承 —— 跨档后用户改过的内容还在
E. 自定义卡留在包里

做法是**直接在设备端用 sqlite3 注入状态**，再观察 App 的反应 ——
比在 UI 上敲字可靠得多（Gboard 的自动补全会污染输入），
也不像 pull/push 数据库那样被 WAL 折腾。

前置条件：设备已 root（模拟器 `adb root` 即可），系统带 sqlite3。

用法：
    python tools/db/check_persona_state_machine.py
    python tools/db/check_persona_state_machine.py --serial emulator-5554
"""

import argparse
import os
import re
import subprocess
import sys
import time
from pathlib import Path

PKG = "com.deepfocus.app"
REMOTE_DB = f"/data/data/{PKG}/databases/deepfocus.db"
DEFAULT_ADB = os.path.expandvars(
    r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
)

# 注入用的自定义卡（故意跟内置卡明显不同，方便一眼认出）
CUSTOM_ID = "custom_qacheck"
CUSTOM_NAME = "QA自定义卡"
OVERLAY_MARK = "覆盖层标记QA"

# 内置卡门槛 -> 卡名（跟 assets/personas/builtin_cards.json 对齐）
BUILTIN_TIERS = [
    (0, "俏皮数码风"),
    (10, "活人小鱼"),
    (50, "傲娇米饭"),
    (100, "颜文字甜心"),
    (200, "拟人女仆"),
    (350, "黏人大肥鱼"),
]


class Adb:
    def __init__(self, serial=None):
        self.serial = serial
        self.adb = DEFAULT_ADB

    def _base(self):
        return [self.adb] + (["-s", self.serial] if self.serial else [])

    def run(self, *args, binary=False):
        r = subprocess.run(
            self._base() + list(args), stdout=subprocess.PIPE, stderr=subprocess.DEVNULL
        )
        return r.stdout if binary else r.stdout.decode("utf-8", "replace")

    def shell(self, *args):
        return self.run("shell", *args)

    def sql(self, statement: str) -> str:
        """在设备上跑一条 SQL，返回输出。

        不能把 SQL 直接塞进 shell 命令 —— 设备端 shell 会把引号吃掉，
        字符串字面量会被截断（`VALUES('a','b')` 变成 `VALUES(a,b)`）。
        所以走临时文件：adb push 到设备，再 `sqlite3 db < file`。

        **走 `run-as` 而不是 root** ——
        `adb root` 会重启 adbd，模拟器里的 adbd 一重启模拟器就退出。
        `run-as <pkg>` 能以 App 身份读自己的库，够用且安全。
        """
        tmp = Path(os.environ.get("TEMP", "/tmp")) / "ff_sql.sql"
        # 写文件必须用 write_bytes —— write_text 在 Windows 上转 CRLF，
        # 设备端 shell 会报「exit: bad number」
        tmp.write_bytes(statement.encode("utf-8"))
        self.run("push", str(tmp), "/data/local/tmp/ff_sql.sql")
        out = self.shell(
            f"run-as {PKG} sh -c \"sqlite3 -separator '|' databases/deepfocus.db "
            f"< /data/local/tmp/ff_sql.sql\""
        )
        return out.strip()

    def dump_ui(self) -> str:
        """UI 树；动画中会返回空 root，重试"""
        tmp = Path(os.environ.get("TEMP", "/tmp")) / "ff_ui.xml"
        for _ in range(15):
            with open(tmp, "wb") as f:
                subprocess.run(
                    self._base() + ["exec-out", "uiautomator", "dump", "/dev/tty"],
                    stdout=f,
                    stderr=subprocess.DEVNULL,
                )
            xml = tmp.read_text(encoding="utf-8", errors="replace")
            if "<node" in xml and 'bounds="[0,0][0,0]"' not in xml:
                return xml
            time.sleep(2)
        return ""

    def pick(self, xml: str, needle: str):
        for n in re.findall(r"<node[^>]*>", xml):
            t = re.search(r'text="([^"]*)"', n)
            d = re.search(r'content-desc="([^"]*)"', n)
            if not ((t and needle in t.group(1)) or (d and needle in d.group(1))):
                continue
            b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
            if b:
                return (
                    (int(b.group(1)) + int(b.group(3))) // 2,
                    (int(b.group(2)) + int(b.group(4))) // 2,
                )
        return None

    def tap(self, xml: str, needle: str) -> bool:
        p = self.pick(xml, needle)
        if not p:
            return False
        self.shell("input", "tap", str(p[0]), str(p[1]))
        return True

    def texts(self, xml: str):
        return [t for t in re.findall(r'text="([^"]*)"', xml) if t]


def wait_ready(adb: Adb, timeout=180):
    """等 App 可交互（模拟器冷启动可能 ANR，要点掉）"""
    t0 = time.time()
    while time.time() - t0 < timeout:
        if adb.shell("pidof", PKG).strip():
            xml = adb.dump_ui()
            if "isn't responding" in xml:
                print("    (点掉 ANR)")
                adb.tap(xml, "Wait")
                time.sleep(3)
                continue
            if "大肥鱼" in xml or "设置" in xml or "当前人格卡" in xml:
                return True
        time.sleep(5)
    return False


def restart(adb: Adb):
    adb.shell("am", "force-stop", PKG)
    time.sleep(3)
    adb.shell("am", "start", "-n", f"{PKG}/.MainActivity")
    wait_ready(adb)


def open_settings(adb: Adb) -> str:
    xml = adb.dump_ui()
    if "当前人格卡" in xml:
        return xml
    adb.tap(xml, "设置")
    time.sleep(6)
    return adb.dump_ui()


def check(label: str, ok: bool, detail: str = "") -> bool:
    print(f"  [{'OK ' if ok else 'FAIL'}] {label}{(' — ' + detail) if detail else ''}")
    return ok


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", default=None, help="adb 设备序列号")
    args = ap.parse_args()

    adb = Adb(args.serial)
    devices = adb.run("devices")
    if "device" not in devices:
        print(f"没有连上的设备：\n{devices}")
        return 2

    if "sqlite3" not in adb.shell("which sqlite3"):
        print("设备上没有 sqlite3 —— 模拟器自带，换个镜像再试")
        return 2
    if "not debuggable" in adb.shell(f"run-as {PKG} id"):
        print("App 不是 debuggable —— run-as 注入不可用")
        return 2

    failures = 0

    # ── A. 内置卡随好感度自动切 ──
    print("\n=== A. 内置卡随好感度自动切 ===")
    for affinity, expect in BUILTIN_TIERS:
        adb.shell("am", "force-stop", PKG)
        time.sleep(2)
        adb.sql(f"UPDATE user_fish SET affinityLevel={affinity} WHERE id=1")
        restart(adb)
        xml = open_settings(adb)
        texts = adb.texts(xml)
        ok = check(f"好感度 {affinity} -> {expect}", expect in texts)
        failures += 0 if ok else 1
        if not ok:
            print(f"        实际看到：{texts[:14]}")

    # ── B. 注入自定义卡并设为底稿 ──
    print("\n=== B. 用户切到自定义卡 -> 它生效 ===")
    adb.shell("am", "force-stop", PKG)
    time.sleep(2)
    adb.sql(
        "INSERT OR REPLACE INTO persona_cards "
        "(id,displayName,tagline,identity,traits,speechStyle,catchphrases,"
        "backstory,taboos,proactiveMotivation,temperature,createdAt) "
        f"VALUES('{CUSTOM_ID}','{CUSTOM_NAME}','注入用','我是注入的自定义卡','测试,注入',"
        "'说话像测试','qa,test','注入的来历','禁忌','注入的动机',1.5,12345)"
    )
    adb.sql(
        "UPDATE persona_state SET baseCardId='" + CUSTOM_ID + "', "
        "selectedTierThreshold=0, identity='" + OVERLAY_MARK + "' WHERE id=1"
    )
    adb.sql("UPDATE user_fish SET affinityLevel=0 WHERE id=1")
    restart(adb)
    xml = open_settings(adb)
    texts = adb.texts(xml)
    failures += 0 if check("设置页显示自定义卡名", CUSTOM_NAME in texts) else 1
    base = adb.sql("SELECT baseCardId FROM persona_state WHERE id=1")
    failures += 0 if check("底稿是自定义卡", base == CUSTOM_ID, base) else 1

    # ── C. 跨档强制切回内置卡 + 覆盖层继承 ──
    print("\n=== C. 跨档（0 -> 10）-> 强制切回内置卡，覆盖层继承 ===")
    adb.shell("am", "force-stop", PKG)
    time.sleep(2)
    adb.sql("UPDATE user_fish SET affinityLevel=10 WHERE id=1")
    restart(adb)
    xml = open_settings(adb)
    texts = adb.texts(xml)
    base = adb.sql("SELECT baseCardId FROM persona_state WHERE id=1")
    tier = adb.sql("SELECT selectedTierThreshold FROM persona_state WHERE id=1")
    identity = adb.sql("SELECT identity FROM persona_state WHERE id=1")
    cards = adb.sql(f"SELECT displayName FROM persona_cards WHERE id='{CUSTOM_ID}'")

    failures += 0 if check("底稿已切回内置卡", base in ("", "NULL"), f"baseCardId={base!r}") else 1
    failures += 0 if check("档位门槛更新为 10", tier == "10", tier) else 1
    failures += 0 if check("覆盖层被继承", identity == OVERLAY_MARK, identity) else 1
    failures += 0 if check("自定义卡仍留在包里", CUSTOM_NAME in cards, cards) else 1
    failures += 0 if check("设置页显示新的内置卡", dict(BUILTIN_TIERS)[10] in texts) else 1

    print()
    if failures:
        print(f"❌ {failures} 项不通过")
        return 1
    print("✅ 人格卡状态机全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
