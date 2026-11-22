#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""小工具：dump 设备端 UI 层级，打印带文字的节点及其 bounds。

用法：
    python tools/db/dump_ui.py [关键字过滤]

注意：PowerShell 里内联 python 里的正则 `[^"]` 会被引号规则吃掉，
所以这个脚本单独成文件。
"""
import re
import subprocess
import sys

ADB = r"adb"


def dump():
    subprocess.run([ADB, "shell", "uiautomator", "dump", "/sdcard/ui_dump.xml"],
                   capture_output=True)
    out = subprocess.run([ADB, "shell", "cat", "/sdcard/ui_dump.xml"],
                         capture_output=True)
    return out.stdout.decode("utf-8", "ignore")


def main():
    xml = dump()
    kw = sys.argv[1] if len(sys.argv) > 1 else ""
    for m in re.finditer(r"<node[^>]*>", xml):
        t = m.group(0)
        txt = re.search(r'text="([^"]*)"', t)
        cls = re.search(r'class="([^"]*)"', t)
        bnd = re.search(r'bounds="([^"]*)"', t)
        en = re.search(r'enabled="([^"]*)"', t)
        value = txt.group(1) if txt else ""
        if kw and kw not in value and (not cls or kw not in cls.group(1)):
            continue
        print(f"{value!r:34} {cls.group(1) if cls else '':28} "
              f"{bnd.group(1) if bnd else '':24} enabled={en.group(1) if en else ''}")


if __name__ == "__main__":
    main()
