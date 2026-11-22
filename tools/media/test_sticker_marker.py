#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验证 StickerMarker 的解析逻辑（对齐 Kotlin 实现的正则与规则）。

模型输出是不可控的，这层容错必须可靠 ——
标记没剥干净会漏到用户眼前，标记没识别会让表情包消失。

用法：python test_sticker_marker.py
"""
import re
import sys

# 与 Kotlin 侧一致
PATTERN = re.compile(
    r"[\[【\{]\s*(?:表情|sticker|表情包)\s*[:：]\s*([A-Za-z0-9_\-]+)\s*[\]】\}]",
    re.IGNORECASE
)
VALID = {"st_cheer_01", "st_excited_02", "st_bored_04", "st_sleeping_01"}


def parse(raw):
    chosen = None
    for m in PATTERN.finditer(raw):
        if m.group(1) in VALID:
            chosen = m.group(1)
    text = PATTERN.sub("", raw).strip()
    # 与 Kotlin 侧一致：删掉标记留下的多余空行
    text = re.sub(r"\n{3,}", "\n\n", text).strip()
    return text, chosen


CASES = [
    # (原始回复, 期望正文, 期望表情id)
    ("今天摸鱼摸了3小时\n[表情:st_sleeping_01]", "今天摸鱼摸了3小时", "st_sleeping_01"),
    ("加油！\n[表情：st_cheer_01]", "加油！", "st_cheer_01"),
    ("冲鸭【表情:st_cheer_01】", "冲鸭", "st_cheer_01"),
    ("[表情:st_excited_02] 太棒了", "太棒了", "st_excited_02"),
    ("{表情:st_bored_04}", "", "st_bored_04"),
    ("[sticker:st_cheer_01] 冲", "冲", "st_cheer_01"),
    ("[ 表情 : st_cheer_01 ] 冲", "冲", "st_cheer_01"),
    # 清单外 id：标记要剥掉，但不算选中
    ("你好\n[表情:st_nonexistent_99]", "你好", None),
    # 没有标记
    ("就是普通的一句话", "就是普通的一句话", None),
    # 多个标记取最后一个
    ("先这样\n[表情:st_cheer_01]\n改主意了\n[表情:st_excited_02]",
     "先这样\n\n改主意了", "st_excited_02"),
    # 标记在正中间
    ("前半段[表情:st_bored_04]后半段", "前半段后半段", "st_bored_04"),
]


def main():
    failed = 0
    for raw, want_text, want_id in CASES:
        text, got_id = parse(raw)
        ok = (text == want_text and got_id == want_id)
        if not ok:
            failed += 1
            print(f"FAIL  {raw!r}")
            print(f"      期望 text={want_text!r} id={want_id!r}")
            print(f"      实际 text={text!r} id={got_id!r}")
        else:
            print(f"ok    {raw[:34]!r} -> {got_id}")

    print(f"\n{len(CASES) - failed}/{len(CASES)} 通过")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
