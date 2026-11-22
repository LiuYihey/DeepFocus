#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
把本地 AI 配置灌进设备
======================

用户在线上 App 里是在「设置 → 大肥鱼 AI」手填 key（存在设备本机，
经 Android Keystore 加密）。但开发时反复手填太慢，
这个脚本把 `local.ai.properties` 里的配置直接写进设备的
`deep_focus_ai` SharedPreferences。

**密钥不出本机**：配置源是 gitignore 覆盖的 `local.ai.properties`，
脚本只把它写到设备沙盒里，不进 APK、不进 Git。

跑法：
    python tools/db/seed_ai_config.py            # 灌配置
    python tools/db/seed_ai_config.py --show     # 只看当前设备上的配置（key 打码）

两个坑（老规矩）：
1. **先 force-stop App** —— App 进程里缓存着 prefs，
   跑着的时候改文件会被它下一次 save() 覆盖回去。
2. **用 `run-as` 而不是 `adb root`** —— root 会重启 adbd，
   模拟器里的 adbd 一断，模拟器直接退出。
"""

import argparse
import base64
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PKG = "com.deepfocus.app"
PREFS_FILE = "shared_prefs/deep_focus_ai.xml"

ADB = os.path.join(
    os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk", "platform-tools", "adb.exe"
)

# SecureKeyStore 的明文降级前缀 —— App 读的时候认得
# （见 app/src/main/java/com/deepfocus/app/ai/SecureKeyStore.kt）
PLAIN_PREFIX = "plain:"


def adb(*args, binary=False, stdin=None):
    r = subprocess.run(
        [ADB, *args], capture_output=True, input=stdin
    )
    if binary:
        return r.stdout
    return r.stdout.decode("utf-8", errors="replace")


def load_props(path: Path) -> dict:
    props = {}
    if not path.exists():
        raise SystemExit(
            f"找不到 {path}\n"
            f"照着 local.ai.properties.example 建一个，把厂商/端点/模型/key 填进去。"
        )
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        props[k.strip()] = v.strip()
    return props


def build_prefs_xml(provider_id: str, base_url: str, model: str, api_key: str) -> bytes:
    """构造 SharedPreferences 的 XML

    key 用 `plain:` + base64 —— 这是 SecureKeyStore 的降级格式，
    App 读的时候能正常解出来（本机写入拿不到 Keystore 密钥，
    也没必要为本机调试去动 Keystore）。
    """
    encoded = base64.b64encode(api_key.encode("utf-8")).decode("ascii")
    return (
        "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
        "<map>\n"
        f"    <string name=\"provider_id\">{provider_id}</string>\n"
        f"    <string name=\"base_url\">{base_url}</string>\n"
        f"    <string name=\"api_key\">{PLAIN_PREFIX}{encoded}</string>\n"
        f"    <string name=\"model\">{model}</string>\n"
        "    <float name=\"temperature\" value=\"0.9\" />\n"
        "    <int name=\"max_tokens\" value=\"1024\" />\n"
        "    <boolean name=\"enabled\" value=\"true\" />\n"
        f"    <long name=\"updated_at\" value=\"{int(time.time() * 1000)}\" />\n"
        "</map>\n"
    ).encode("utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--show", action="store_true", help="只看设备上的配置")
    parser.add_argument(
        "--props", default=str(ROOT / "local.ai.properties"), help="配置源文件"
    )
    args = parser.parse_args()

    if "emulator-" not in adb("devices") and "\tdevice" not in adb("devices"):
        raise SystemExit("设备连不上，先把模拟器拉起来（python tools/db/start_emulator.py）")

    if args.show:
        print(read_device_prefs())
        return

    props = load_props(Path(args.props))
    provider_id = props.get("deepfocus.ai.providerId", "minimax-cn")
    base_url = props.get("deepfocus.ai.baseUrl", "https://api.minimax.cn/v1")
    model = props.get("deepfocus.ai.model", "MiniMax-M3.1-Flash")
    api_key = props.get("deepfocus.ai.apiKey", "")
    if not api_key:
        raise SystemExit(f"{args.props} 里没有 deepfocus.ai.apiKey")

    xml = build_prefs_xml(provider_id, base_url, model, api_key)

    # 坑 1：先杀进程，否则 App 手里的 prefs 缓存会把我们写的覆盖回去
    adb("shell", "am", "force-stop", PKG)
    time.sleep(1.5)

    # 坑 2：走 run-as 进沙盒（不用 adb root）
    #
    # 整条命令必须作为**一个**参数交给 adb shell，
    # 内部用单引号把 `sh -c` 的命令串包起来 ——
    # 否则 `&&` 和 `>` 会被**设备端最外层 shell** 拆开：
    # `sh -c "mkdir -p x && cat > f"` 到设备上变成
    # `sh -c "mkdir -p x" && cat > f`，重定向跑在外层 shell（shell 用户），
    # 文件根本写不进 App 沙盒，而且不报错。
    #
    # 内容走 stdin，避开设备端 shell 的引号/换行地狱。
    inner = f"mkdir -p shared_prefs && cat > {PREFS_FILE}"
    adb("shell", f"run-as {PKG} sh -c '{inner}'", stdin=xml)
    time.sleep(0.5)
    # SharedPreferences 每次读会写 .bak 备份，留下无所谓的垃圾
    adb("shell", "run-as", PKG, "rm", "-f", PREFS_FILE + ".bak")

    print(f"已灌入设备：provider={provider_id} baseUrl={base_url} model={model}")
    print(f"key 前 4 位 {api_key[:4]}… 后 4 位 {api_key[-4:]}（长度 {len(api_key)}）")
    print("现在启动 App 就能用了。")


def read_device_prefs() -> str:
    out = adb("shell", f"run-as {PKG} sh -c 'cat {PREFS_FILE}'")
    # 打码 key
    out = out.replace(PLAIN_PREFIX, PLAIN_PREFIX + "<打码>")
    return out or "(设备上没有这个文件)"


if __name__ == "__main__":
    main()
