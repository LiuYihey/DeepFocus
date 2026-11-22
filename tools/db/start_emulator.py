#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
启动 DeepFocus 用的模拟器（FocusCatQA AVD）

用法：
    python tools/db/start_emulator.py          # 启动并等就绪
    python tools/db/start_emulator.py --root   # 启动并等就绪，然后 adb root

为什么要有这个脚本：
- 默认的 GPU 模式（swiftshader_indirect）在这台机器上会崩 ——
  表现为 RenderThread 反复新建、ColorBuffer 编号飙到 60+，模拟器直接退出。
  **`-gpu off` 才稳**。
- `adb root` 会重启 adbd，模拟器里的 adbd 一重启，模拟器就跟着退出 ——
  所以 root 要放到最后做，或者干脆用 `adb shell` 里的 su。
- 宿主内存紧张时模拟器会反复掉线，脚本负责等它真的就绪。

用法要点：
    adb root 之后如果连不上，先重新执行本脚本。
"""

import argparse
import os
import subprocess
import sys
import time
from pathlib import Path

AVD = "FocusCatQA"
SDK = Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk"
EMULATOR = SDK / "emulator" / "emulator.exe"
ADB = SDK / "platform-tools" / "adb.exe"


def adb(*args):
    return subprocess.run([str(ADB), *args], capture_output=True).stdout.decode(
        "utf-8", errors="replace"
    )


def device_ready() -> bool:
    out = adb("devices")
    lines = [l for l in out.splitlines() if "\tdevice" in l]
    return len(lines) > 0


def install_apk(apk: str) -> bool:
    """把 APK 装到设备上

    **不要用 `adb install`** —— 它会重启 adb server，
    模拟器里的 adbd 一断，模拟器跟着退出（这机器上尤其明显）。
    改成 push 到设备再 `pm install`，全程不重启 adb server。
    """
    apk_path = Path(apk)
    if not apk_path.exists():
        print(f"找不到 APK：{apk}")
        return False
    print(f"push APK…（{apk_path.name}）")
    subprocess.run(
        [str(ADB), "push", str(apk_path), "/data/local/tmp/app.apk"],
        capture_output=True,
    )
    print("pm install…")
    r = subprocess.run(
        [str(ADB), "shell", "pm", "install", "-r", "/data/local/tmp/app.apk"],
        capture_output=True,
    )
    out = (r.stdout + r.stderr).decode("utf-8", errors="replace")
    ok = "Success" in out
    print("装包成功" if ok else f"装包失败：{out.strip()}")
    return ok


def launch_emulator(extra_args=None) -> None:
    """启动模拟器，且**脱离当前进程的生命周期**

    用 WMI 的 Win32_Process.Create 起进程 ——
    直接 subprocess.Popen 的话，父进程一退出，子进程会被一起带走
    （TRAE 的 shell 命令结束时会清理整棵进程树，模拟器就这么没了）。
    WMI 创建的进程由系统托管，不挂在调用方的树里，能活下来。

    参数上不要加 `-gpu off` —— 那会让模拟器跑得极慢，
    App 一启动设备就 offline。默认 GPU 反而稳。
    """
    args = [str(EMULATOR), "-avd", AVD, "-no-boot-anim"]
    if extra_args:
        args.extend(extra_args)
    cmd = subprocess.list2cmdline(args)
    subprocess.run(
        [
            "powershell",
            "-NoProfile",
            "-Command",
            "Invoke-CimMethod -ClassName Win32_Process -MethodName Create "
            f"-Arguments @{{CommandLine='{cmd}'}} | Out-Null",
        ],
        capture_output=True,
    )


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", action="store_true", help="启动后执行 adb root")
    ap.add_argument("--install", metavar="APK", help="启动后装这个 APK")
    ap.add_argument("--timeout", type=int, default=300, help="等待就绪的秒数")
    args = ap.parse_args()

    if not EMULATOR.exists():
        print(f"找不到模拟器：{EMULATOR}")
        return 2

    if device_ready():
        print("设备已在线")
    else:
        print(f"启动 AVD {AVD}（-gpu off 才稳）…")
        launch_emulator()

    deadline = time.time() + args.timeout
    while time.time() < deadline:
        if device_ready():
            booted = adb("shell", "getprop", "sys.boot_completed").strip()
            if booted == "1":
                print("模拟器就绪")
                break
        time.sleep(5)
    else:
        print("超时：模拟器没起来")
        return 1

    if args.install:
        if not install_apk(args.install):
            return 1

    if args.root:
        print("adb root…")
        subprocess.run([str(ADB), "root"], capture_output=True)
        time.sleep(5)
        for _ in range(20):
            if device_ready():
                print("root 完成")
                return 0
            time.sleep(3)
        print("root 后没连上 —— 模拟器可能跟着 adbd 重启退出了，重新跑一次本脚本")
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
