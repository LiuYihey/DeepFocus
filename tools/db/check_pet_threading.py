#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""桌宠线程纪律静态检查

桌宠踩过的坑（2026-11-18）：

    渲染循环挂在 `Dispatchers.Default`（后台线程）上，
    但每帧都要调 `WindowManager.updateViewLayout()` ——
    这个方法内部 `checkThread()`，后台线程调会抛
    `CalledFromWrongThreadException`，**直接崩 App**。

    而且崩的地方有**两处**，症状还不一样：
    1. 贴边隐藏态那处没 try/catch → 桌宠贴边静置 12 秒后 **App 崩掉**
    2. 每帧 `updatePosition()` 那处被 `catch` 吞掉 → **桌宠在屏幕上不动**
       （物理在跑，窗口不跟 —— 看着像「桌宠坏了」）

线程模型（改代码前先记住这张表）：

| 入口 | 跑在哪个线程 | 约束 |
|---|---|---|
| `tick()` / `advance()` | **主线程**（renderScope = Dispatchers.Main） | 里面可以随便改 View |
| `setupTouch()` 的手势回调 | **主线程**（平台保证） | 同上 |
| `playAction` / `showBubble` / `hideBubble` / `setPose` / `setSleeping` / `moveTo` / `flipFacing` | **调用方不定**（Service 后台协程 / 用户点击） | 必须自己切回主线程（`onMain { }`） |
| `showSafely` / `hideSafely` | 状态轮询（后台线程） | 内部 `onMain { show/hide }`，整块加/移窗口 |

所以这里检查的是**这几条契约**，不是「每个 View 赋值都要包 onMain」——
手势回调和 tick 里直接改 View 是对的。
"""
import re
import sys
from pathlib import Path

ROOT = Path(r".")
PET = ROOT / "app" / "src" / "main" / "java" / "com" / "deepfocus" / "app" / "pet"
SERVICE = PET / "DesktopPetService.kt"
CONTROLLER = PET / "PetWindowController.kt"

problems = []


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


def line_of(text: str, pos: int) -> int:
    return text[:pos].count("\n") + 1


def check(cond: bool, msg: str) -> None:
    if not cond:
        problems.append(msg)


def in_try_block(text: str, pos: int) -> bool:
    """这个位置是否落在某个 `try { ... } catch` 块里

    做法：从调用点往外扫花括号，遇到 `try {` 就看它有没有配对的 `catch`。
    不用「往前找最近的 try」那种启发式 —— 那个会被嵌套的
    `catch` 块或更外层的 try 骗到。
    """
    depth = 0
    i = pos
    while i > 0:
        ch = text[i]
        if ch == "}":
            depth += 1
        elif ch == "{":
            if depth == 0:
                # 找到一个外层块的开头 —— 看它是不是 try
                head = text[max(0, i - 60): i]
                if "try" in head:
                    # 从这个 { 往后找配对的 catch
                    d = 1
                    j = i + 1
                    while j < len(text) and d > 0:
                        if text[j] == "{":
                            d += 1
                        elif text[j] == "}":
                            d -= 1
                        j += 1
                    tail = text[j: j + 80]
                    return "catch" in tail
                return False
            depth -= 1
        i -= 1
    return False


def main() -> int:
    svc = read(SERVICE)
    ctl = read(CONTROLLER)

    # ── 1. 渲染循环必须在主线程 ──
    m = re.search(r"renderJob\s*=\s*(\w+)\.launch\s*\{", svc)
    check(m is not None, "找不到渲染循环的 launch")
    if m:
        scope = m.group(1)
        check(
            scope == "renderScope",
            f"渲染循环挂在 `{scope}.launch` 上 —— 必须在主线程（renderScope）",
        )
    check(
        "renderScope" in svc and "Dispatchers.Main" in svc,
        "没有用 Dispatchers.Main 专门开渲染作用域",
    )
    check(
        "renderJob = serviceScope.launch" not in svc,
        "渲染循环又挂回 serviceScope（后台线程）了 —— 会崩 App",
    )

    # ── 2. updateViewLayout 每处都要兜底 ──
    calls = list(re.finditer(r"windowManager\.updateViewLayout\(", ctl))
    check(len(calls) > 0, "居然没有 updateViewLayout 调用？脚本可能过时了")
    for c in calls:
        check(
            in_try_block(ctl, c.start()),
            f"第 {line_of(ctl, c.start())} 行的 updateViewLayout 没有 try/catch 兜底"
            f"（窗口被移除时会抛异常；后台线程调还会直接崩 App）",
        )

    # ── 3. tick() 必须有主线程守卫 ──
    tick = re.search(r"fun tick\(deltaMs: Long\)\s*\{(.*?)\n    \}", ctl, re.S)
    check(tick is not None, "找不到 tick()")
    if tick:
        body = tick.group(1)
        check(
            "Looper.myLooper()" in body or "mainHandler" in body,
            "tick() 没有主线程守卫 —— 后台线程调会崩 App",
        )

    # ── 4. 外部可调的公开方法必须切回主线程 ──
    # 这些方法的调用方线程不定（Service 后台协程 / 用户点击回调）
    PUBLIC_MUST_MARSHAL = [
        "playAction",
        "showBubble",
        "hideBubble",
        "setPose",
        "setSleeping",
        "moveTo",
        "flipFacing",
        "showSafely",
        "hideSafely",
    ]
    for name in PUBLIC_MUST_MARSHAL:
        fm = re.search(rf"fun {name}\((.*?)\n    \}}", ctl, re.S)
        if fm is None:
            problems.append(f"找不到公开方法 {name}() —— 脚本可能过时了")
            continue
        body = fm.group(0)
        check(
            "onMain" in body,
            f"公开方法 {name}() 没有走 onMain {{ }} —— 调用方可能是后台线程",
        )

    # ── 5. onMain 本身要在主线程上执行 ──
    on_main = re.search(r"private inline fun onMain\(.*?\n    \}", ctl, re.S)
    check(on_main is not None, "找不到 onMain 助手函数")
    if on_main:
        body = on_main.group(0)
        check(
            "Looper.myLooper()" in body,
            "onMain 没有判断当前是不是主线程 —— 可能死循环或者不切线程",
        )

    print(f"检查了：\n  {SERVICE.name}\n  {CONTROLLER.name}")
    print()
    if problems:
        print(f"❌ {len(problems)} 个问题：")
        for p in problems:
            print("   -", p)
        return 1
    print(
        "✅ 桌宠线程纪律通过：渲染循环在主线程、updateViewLayout 都有兜底、"
        "tick 有主线程守卫、公开方法都走 onMain"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
