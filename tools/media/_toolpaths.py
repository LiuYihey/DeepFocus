# -*- coding: utf-8 -*-
"""定位工具脚本要用的外部可执行文件（ffmpeg / ffprobe）。

为什么不写死绝对路径
--------------------
写死 `C:\\Users\\<本机用户名>\\...` 有两个问题：

1. **泄隐私** —— 本机用户名会跟着代码进公开仓库。
2. **换机就断** —— 路径里的 WinGet 包哈希和版本号（`ffmpeg-8.1.2-full_build`）
   换个版本或换个机器就不存在了。

所以按「先查 PATH，查不到再扫 WinGet 包目录」的顺序找：
- winget 装的 ffmpeg 通常**不在 PATH** 里（winget 默认只加 shim），
  所以第二步不能省。
- 扫到多个版本时取字典序最大的那个（版本号递增，`8.1.2` > `7.1`）。

用法：
    import os, sys
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from _toolpaths import FFMPEG, FFPROBE
"""
import glob
import os
import shutil


def _find(name):
    """返回可执行文件路径；找不到就退回裸名字，让调用处报「找不到命令」。"""
    found = shutil.which(name)
    if found:
        return found

    local = os.environ.get("LOCALAPPDATA", "")
    if not local:
        return name

    pattern = os.path.join(
        local, "Microsoft", "WinGet", "Packages",
        "Gyan.FFmpeg_Microsoft.Winget.Source_*",
        "ffmpeg-*-full_build", "bin", name + ".exe")
    hits = sorted(glob.glob(pattern))
    return hits[-1] if hits else name


FFMPEG = _find("ffmpeg")
FFPROBE = _find("ffprobe")
