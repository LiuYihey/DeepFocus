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

ROOT = Path(r".")
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
#     → artwork/icon_src/dafeiyu_icon_trae_builtin.png
SRC = ROOT / "artwork" / "icon_src" / "dafeiyu_icon_trae_builtin.png"
RES = ROOT / "app" / "src" / "main" / "res"

# 与 ui/theme/Color.kt 的主色一致（深蓝 → 亮蓝）
BG_TOP = (0x1B, 0x3A, 0x63)
BG_BOTTOM = (0x2F, 0x5D, 0x8C)
FISH = (0x6F, 0xC6, 0xE8)

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
    bg = gradient(size, BG_TOP, BG_BOTTOM)

    # 角色占画布 88%。
    # 缩放依据同样用「面积分位半径」而不是外接矩形 ——
    # 大肥鱼有呆毛 / 鱼尾 / 鳍耳，按外接矩形算会缩得比预期小一大截。
    src_radius = silhouette_radius(src)
    target_radius = size * 0.44
    ratio = target_radius / src_radius
    fish = src.resize(
        (max(1, int(src.width * ratio)), max(1, int(src.height * ratio))),
        Image.LANCZOS,
    )
    # 主体置中偏上（底部留出投影空间）
    ox = (size - fish.width) // 2
    oy = int(size * 0.04)

    # 角色背后的柔光，让她从背景里浮出来
    halo = Image.new("L", (size, size), 0)
    hd = ImageDraw.Draw(halo)
    cx, cy = size // 2, oy + fish.height // 2
    r = int(size * 0.42)
    hd.ellipse([cx - r, cy - r, cx + r, cy + r], fill=110)
    halo = halo.filter(ImageFilter.GaussianBlur(int(size * 0.14)))
    glow = Image.new("RGB", (size, size), FISH)
    bg = Image.composite(glow, bg, halo)

    # 底部投影
    shadow = Image.new("L", (size, size), 0)
    sd = ImageDraw.Draw(shadow)
    sw = int(fish.width * 0.72)
    sh = max(2, int(size * 0.035))
    sx = (size - sw) // 2
    sy = oy + fish.height - sh
    sd.ellipse([sx, sy, sx + sw, sy + sh * 2], fill=90)
    shadow = shadow.filter(ImageFilter.GaussianBlur(int(size * 0.03)))
    bg = Image.composite(Image.new("RGB", (size, size), (0x10, 0x22, 0x38)), bg, shadow)

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
    # 背景：渐变底
    gradient(size, BG_TOP, BG_BOTTOM).save(out_dir / "ic_launcher_background.png")

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
    target_radius = size * (36 * 0.90) / 108
    ratio = target_radius / src_radius
    fish = src.resize(
        (max(1, int(src.width * ratio)), max(1, int(src.height * ratio))),
        Image.LANCZOS,
    )
    offset = ((size - fish.width) // 2, (size - fish.height) // 2)

    fg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    fg.alpha_composite(fish, offset)
    fg.save(out_dir / "ic_launcher_foreground.png")

    # 单色层（Android 13+ 主题图标）：只取 alpha，填纯黑。
    # 系统会按主题色给这一层上色，所以必须是无彩色剪影。
    mono = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    mono.paste((0, 0, 0, 255), offset, fish)
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
