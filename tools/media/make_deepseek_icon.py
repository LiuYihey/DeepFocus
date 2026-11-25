# -*- coding: utf-8 -*-
"""把 deepseek.jpg 做成可用的图标资源。

用户要求：「目前这个 DeepSeek 鲸鱼的图标也是太丑了，
请使用 deepseek.jpg 作为图标吧，注意去掉白色背景吧」

产出
----
`res/drawable-{m,h,xh,xxh,xxxh}dpi/ic_deepseek_whale.png`
    彩色版（保留原蓝紫粉渐变），按密度分桶。

背景抠除
--------
JPEG 里每个像素是「颜色 C 以 alpha a 叠在白纸上」的结果：
    result = a*C + (1-a)*255
所以「离白有多远」正比于 a：
    a ≈ (255 - min(r,g,b)) / (255 - min(C))
DeepSeek 的几个主色 min 通道都在 40~60 之间，
取 (255-205) 做归一化常数就够准。

只按亮度抠会把鲸鱼**肚子里那块浅紫渐变**也吃掉（它接近白），
所以这里用「离白多远」而不是「是否纯白」——
肚子里的渐变离白足够远，能保住。
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
# 源图放在 artwork/originals/ —— 素材源文件统一在那里，不散在仓库根目录
SRC = ROOT / "artwork" / "originals" / "deepseek.jpg"
RES = ROOT / "app" / "src" / "main" / "res"

# DeepSeek 主色的 min 通道 ≈ 42（蓝）/ 60（粉），取中值 205 做常数
ALPHA_SPAN = 205

# 基准边长（px，按宽度算）。取 56dp @4x = 224px ——
# 启动 Loading 画面用 56dp，底栏只用 22dp，一份资源够用。
BASE_WIDTH = 224

DENSITIES = {
    "drawable-mdpi": 1,
    "drawable-hdpi": 1.5,
    "drawable-xhdpi": 2,
    "drawable-xxhdpi": 3,
    "drawable-xxxhdpi": 4,
}


def alpha_matte(img: Image.Image) -> Image.Image:
    """由「离白多远」反推 alpha 遮罩"""
    img = img.convert("RGB")
    w, h = img.size
    src = img.load()
    matte = Image.new("L", (w, h), 0)
    mp = matte.load()
    for y in range(h):
        for x in range(w):
            r, g, b = src[x, y]
            dist = 255 - min(r, g, b)
            a = int(max(0, min(255, dist * 255 // ALPHA_SPAN)))
            mp[x, y] = a
    return matte


def trim(img: Image.Image) -> Image.Image:
    bbox = img.getchannel("A").getbbox()
    return img.crop(bbox) if bbox else img


def main():
    src = Image.open(SRC).convert("RGB")
    print("原图:", src.size)

    color = src.convert("RGBA")
    color.putalpha(alpha_matte(src))
    color = trim(color)
    print("抠白后:", color.size)

    for folder, scale in DENSITIES.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        target_w = max(1, int(BASE_WIDTH * scale / 4))
        target_h = max(1, int(color.height * target_w / color.width))
        resized = color.resize((target_w, target_h), Image.LANCZOS)
        path = out_dir / "ic_deepseek_whale.png"
        resized.save(path, optimize=True)
        print(f"{folder}: {target_w}x{target_h}  ({path.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
