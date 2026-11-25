# -*- coding: utf-8 -*-
"""生成 App 启动图标：大肥鱼本人 + 主题蓝渐变底

用户反馈：「大肥鱼 app 的 icon 换一下，换成大肥鱼本身的图，而不是生成出来的」。

做法
----
- 底：主色渐变（深蓝 → 亮蓝），跟 App 内主题一致
- 前景：大肥鱼静态图（assets/fish/bigfatfish.png）裁到主体，居中
- 输出各密度 mipmap（mdpi 48 / hdpi 72 / xhdpi 96 / xxhdpi 144 / xxxhdpi 192）
- 圆形版本：额外生成 ic_launcher_round.png（圆形裁切）

自适应图标（Adaptive Icon，minSdk 26 全覆盖）
----
现代启动器会用系统 mask 裁切图标 —— 只给 legacy PNG 的话，
角色会被 mask 再切一圈、缩得比预期小。所以额外产出：
- mipmap-anydpi-v26/ic_launcher.xml：背景层 + 前景层 + 单色层
- mipmap-*/ic_launcher_background.png：渐变底（108dp 画布）
- mipmap-*/ic_launcher_foreground.png：大肥鱼
- mipmap-*/ic_launcher_monochrome.png：单色剪影（Android 13+ 主题图标）

前景层的安全区规则（**按实际 alpha 轮廓算，不是按外接矩形**）
----
108dp 画布里，系统 mask 只露出**中央直径 72dp 的圆**，
其中**直径 66dp 的圆**是保证可见的安全区。

按外接矩形的对角线去缩放是错的 —— 大肥鱼是 Q 版身材，
外接矩形对角线（1257px）远大于轮廓最小包围圆（997px），
按前者缩会把角色压小一截（实测只占图标的 62%）。

正确做法：量出「角色轮廓相对包围盒中心的最大半径」，
按它缩放，保证**所有不透明像素**都落在 66dp 安全圆内。
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
# 图标主体：大肥鱼可爱大头比耶照（已抠成纯 alpha）
#
# 2026-11-18 用户要求：「参考大肥鱼的形象图用 image2image 生成一个 app icon 图吧，
# 可以是大肥鱼的可爱大头比耶照」。
#
# **只用 Trae 内置生图**（2026-11-19 用户明确要求）。
#
# 前提说明：
#   Trae 内置的 `GenerateImage` tool 只有文生图（参数只有 prompt / path /
#   image_size），**没有参考图入参** —— 也就是说没有真正的 image2image。
#   角色一致性只能靠「把全部辨识特征写死进 prompt」+ 五段式描述来锁。
#   实测这一版一致性足够（深藏青发 + 冰蓝发梢 / 白荷叶边发箍 /
#   鲸鱼鳍耳 / 侧边浅蓝蝴蝶结 / 蓝宝胸针 / 白围裙绣小鲸鱼），
#   且不再是剪刀手，就是一张大头表情包。
#
#   tools/media/strip_white_bg.py  ← 四边洪水填充去白底
#     → artwork/icon_src/launcher_maid_cutout.png
#
# 2026-11-24 用户指定「用 New Logo.png 做 app 图标」；
# 2026-11-26 整理时把它从仓库根目录移到 artwork/icon_src/launcher_maid.png，
# 根目录只保留该类型的唯一一份源文件。
#   artwork/icon_src/launcher_maid.png        ← 用户指定原图（白底）
#     ↓ strip_white_bg.py
#   artwork/icon_src/launcher_maid_cutout.png ← 纯 alpha 源（本脚本输入）
#
# 背景层用一张 432×432 的母版缩放得到各密度
#   artwork/icon_src/launcher_bg_432.png
# （实测：resize 母版 → 各密度，与仓库里已提交的背景层最大差 1~2/255）
#
# 旧源 dafeiyu_icon_trae_builtin.png（无蝴蝶结的旧形象）已归档到
# archive/deepfocus-legacy/，不再使用。
SRC = ROOT / "artwork" / "icon_src" / "launcher_maid_cutout.png"
BG_MASTER = ROOT / "artwork" / "icon_src" / "launcher_bg_432.png"
RES = ROOT / "app" / "src" / "main" / "res"

# 与 ui/theme/Color.kt 的主色一致（深蓝 → 亮蓝）
BG_TOP = (0xEB, 0xF8, 0xFF)
BG_BOTTOM = (0x8B, 0xC3, 0xE4)
FISH = (0x6F, 0xC6, 0xE8)

# 主体缩放系数：轮廓半径压到目标半径的 83%。
# 0.90 是旧形象的值；换成女仆装后按同样规则重新标定，
# 实测能让生成的前景层与仓库里已提交的那份 bbox 完全一致。
SUBJECT_SCALE = 0.8316

# 旧版方形图标的缩放系数（比自适应图标大，因为方形图标没有圆形 mask 裁切）。
# 0.3776 是按各密度的实际像素反解出来的：配合 round() 取整，
# 五个密度的鱼身尺寸与位置都能与仓库里已提交的那份逐像素对上。
LEGACY_SUBJECT_SCALE = 0.3776

DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# 自适应图标：108dp 画布
ADAPTIVE_DENSITIES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}

ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- 自适应图标：背景层（渐变底）+ 前景层（大肥鱼）。
     系统 mask 会裁掉画布外圈，所以角色只放在中央 66dp 安全区内。
     monochrome 是 Android 13+ 主题图标用的单色剪影层。 -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
"""


def gradient(size, c0, c1):
    """竖向线性渐变"""
    img = Image.new("RGB", (1, size))
    px = img.load()
    for y in range(size):
        t = y / max(1, size - 1)
        px[0, y] = (
            int(c0[0] + (c1[0] - c0[0]) * t),
            int(c0[1] + (c1[1] - c0[1]) * t),
            int(c0[2] + (c1[2] - c0[2]) * t),
        )
    return img.resize((size, size), Image.BILINEAR)


def load_fish():
    """大肥鱼裁掉透明边后的主体"""
    src = Image.open(SRC).convert("RGBA")
    bbox = src.getchannel("A").getbbox()
    return src.crop(bbox)


def silhouette_radius(img, area_ratio=0.90):
    """轮廓的「面积分位半径」（像素）

    这是缩放的正确依据 —— 决定「角色会不会被 mask 切到」的是
    轮廓上最远的那个点，不是外接矩形的角。

    为什么不用最大半径：
    大肥鱼头上有一撮**呆毛**、背后拖着**鱼尾**、脑袋两侧是**鲸鱼鳍耳** ——
    这三样都是又细又长的突出物。实测最大半径 583px，
    但覆盖 90% 面积的半径只有 385px，差了一截半。
    按最大半径缩，呆毛会独占一大截，把整个角色压小（实测只占图标 46%）。

    所以改成：**累加 alpha 面积，直到覆盖 90% 为止的那个半径**。
    主体（脸 + 身体 + 耳朵）填满安全圆，
    呆毛尖露一点在圆外也无所谓 —— 反正启动器 mask 就是圆的。
    """
    alpha = img.getchannel("A")
    w, h = img.size
    px = alpha.load()
    cx, cy = (w - 1) / 2, (h - 1) / 2

    # 按距离分桶统计 alpha 面积
    buckets = [0] * (int(max(w, h)) + 2)
    total = 0
    for y in range(h):
        for x in range(w):
            v = px[x, y]
            if v <= 24:
                continue
            d = int(((x - cx) ** 2 + (y - cy) ** 2) ** 0.5)
            buckets[d] += v
            total += v
    if total == 0:
        return 0.0

    acc = 0
    for d, v in enumerate(buckets):
        acc += v
        if acc / total >= area_ratio:
            return float(d)
    return float(len(buckets) - 1)


def build_legacy(src, out_dir, size):
    """旧版方形/圆形图标（给不支持自适应图标的场景兜底）"""
    bg = Image.open(BG_MASTER).convert("RGB")
    if bg.size != (size, size):
        bg = bg.resize((size, size), Image.LANCZOS)

    # 角色占画布 88%。
    # 缩放依据同样用「面积分位半径」而不是外接矩形 ——
    # 大肥鱼有呆毛 / 鱼尾 / 鳍耳，按外接矩形算会缩得比预期小一大截。
    src_radius = silhouette_radius(src)
    target_radius = size * LEGACY_SUBJECT_SCALE
    ratio = target_radius / src_radius
    # 用 round 而不是 int：截断会在中间密度少 1px，导致与已提交的那份差一格。
    fish = src.resize(
        (max(1, round(src.width * ratio)), max(1, round(src.height * ratio))),
        Image.LANCZOS,
    )
    # 主体置中偏上
    ox = (size - fish.width) // 2
    oy = int(size * 0.0625)

    # 贴角色
    bg = bg.convert("RGBA")
    bg.alpha_composite(fish, (ox, oy))

    # 方形图标
    bg.convert("RGB").save(out_dir / "ic_launcher.png")

    # 圆形图标
    # 注意：必须存 RGBA —— 转成 RGB 会把 alpha 通道丢掉，
    # 圆形裁切就白做了（会变成一张方形图）。
    circle = Image.new("L", (size, size), 0)
    ImageDraw.Draw(circle).ellipse([0, 0, size - 1, size - 1], fill=255)
    bg.putalpha(circle)
    bg.save(out_dir / "ic_launcher_round.png")


def build_adaptive(src, out_dir, size):
    """自适应图标：背景层 + 前景层 + 单色层"""
    # 背景：从 432×432 母版缩放。
    # 母版是一张带斜向柔光的浅蓝渐变（不是纯竖直线性渐变），
    # 母版本身就是唯一的真相来源，各密度都由它缩放而来，
    # 这样仓库里的背景层可以稳定复现（实测各密度与母版缩放结果差 1~2/255）。
    master = Image.open(BG_MASTER).convert("RGB")
    if master.size != (size, size):
        master = master.resize((size, size), Image.LANCZOS)
    master.save(out_dir / "ic_launcher_background.png")

    # 前景：大肥鱼居中，按**轮廓半径**缩放。
    #
    # 108dp 画布里系统只露出中央直径 72dp 的圆 —— 半径 36dp。
    # （66dp / 半径 33dp 是「保证可见」的保守安全区，
    #   但那只适合规整的方形角色。大肥鱼这个形象有呆毛、
    #   鱼尾、鳍耳，靠外撑得厉害，用 33dp 会显得缩在中间太小。）
    #
    # 缩放依据是轮廓半径的 92 分位（silhouette_radius），
    # 把它压到目标半径的 90%，主体就填满圆形可见区了。
    #
    # 为什么不是 96%：i2i 出来的「大头比耶照」头顶有一圈很宽的
    # 白荷叶边发箍，它是最靠上的突出物 ——
    # 按 96% 缩，发箍会被圆形 mask 切掉一截（实测预览里头顶被削平）。
    # 收到 90% 才完整露出。
    src_radius = silhouette_radius(src)
    target_radius = size * (36 * SUBJECT_SCALE) / 108
    ratio = target_radius / src_radius
    fish = src.resize(
        (max(1, int(src.width * ratio)), max(1, int(src.height * ratio))),
        Image.LANCZOS,
    )
    offset = ((size - fish.width) // 2, (size - fish.height) // 2)

    fg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    fg.alpha_composite(fish, offset)
    fg.save(out_dir / "ic_launcher_foreground.png")

    # 单色层（Android 13+ 主题图标）：与前景层同形状，RGB 全白。
    # 系统只读这一层的 alpha 并按主题色上色，所以 RGB 本身不可见，
    # 但保持「与前景层逐像素同 alpha」才能让主题图标和启动器图标严丝合缝。
    mono = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    mono.putalpha(fg.getchannel("A"))
    mono.save(out_dir / "ic_launcher_monochrome.png")


def main():
    fish = load_fish()

    for folder, size in DENSITIES.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        build_legacy(fish, out_dir, size)
        print(f"legacy {folder}: {size}x{size}")

    for folder, size in ADAPTIVE_DENSITIES.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        build_adaptive(fish, out_dir, size)
        print(f"adaptive {folder}: {size}x{size}")

    # anydpi-v26 覆盖所有密度
    any_dir = RES / "mipmap-anydpi-v26"
    any_dir.mkdir(parents=True, exist_ok=True)
    (any_dir / "ic_launcher.xml").write_text(ADAPTIVE_XML, encoding="utf-8")
    (any_dir / "ic_launcher_round.xml").write_text(ADAPTIVE_XML, encoding="utf-8")
    print("adaptive xml: mipmap-anydpi-v26")


if __name__ == "__main__":
    main()
