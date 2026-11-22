#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""小工具：在设备端用 sqlite3 查 App 的库，并把结果打出来。

坑（踩过的）：
- SQL 里不能有单引号 —— 整条命令被单引号包着，单引号会提前闭合。
  所以含字符串值的 SQL 要用 `quote(...)` 或把引号换成 `char(39)`。
- App 运行时 Room 持锁，直接查可能读到旧快照；
  要写就先 `am force-stop`。

用法：
    python tools/db/query_db.py "SELECT * FROM user_fish;"
"""
import os
import subprocess
import sys

ADB = os.path.join(
    os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk", "platform-tools", "adb.exe")
PKG = "com.deepfocus.app"
DB = "databases/deepfocus.db"


def adb(*args, binary=False):
    r = subprocess.run([ADB, *args], capture_output=True)
    if binary:
        return r.stdout
    return r.stdout.decode("utf-8", "replace")


def sql(stmt: str) -> str:
    """执行一条 SQL。

    单引号的处理：SQL 里的单引号会跟外层包住整条命令的单引号打架
    （设备端最外层 shell 会把引号提前闭合，SQL 被拆成碎片，
    且**不报错**，只是返回空结果）。

    所以：
    - 默认用单引号包外层，SQL 内部禁止出现单引号
      （字符串值用 `char(码点,...)` 表达）；
    - SQL 里确实有单引号时，改用双引号包外层
      （设备端 shell 里双引号内的单引号是普通字符）。
    """
    if "'" in stmt:
        out = adb("shell", f'run-as {PKG} sqlite3 {DB} "{stmt}"')
    else:
        out = adb("shell", f"run-as {PKG} sqlite3 {DB} '{stmt}'")
    return out.strip()


def main():
    stmt = sys.argv[1]
    print(sql(stmt))
    print("--- stderr ---")
    r = subprocess.run([ADB, "shell", f"run-as {PKG} sqlite3 {DB} '{stmt}'"],
                       capture_output=True)
    print(r.stderr.decode("utf-8", "replace"))


if __name__ == "__main__":
    main()
