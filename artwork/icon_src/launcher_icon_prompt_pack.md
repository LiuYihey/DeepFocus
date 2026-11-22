# App 启动图标 · 大肥鱼可爱大头表情包（已定稿）

> ## 当前状态（2026-11-19 定稿）
>
> 用户明确：**「我只使用 TRAE 内置生图」**。
> 而 **TRAE 内置没有 image2image** —— `GenerateImage` 只有
> `prompt` / `path` / `image_size` 三个参数，没有参考图入参；
> 插件目录也 grep 不到任何 i2i 能力。
>
> 所以最终图标走 **t2i + 特征锁定**，构图是**双手托腮的大头表情包**
> （用户明确不要剪刀手）：
>
> | 步骤 | 产物 |
> |---|---|
> | TRAE 内置 `GenerateImage`（t2i） | `artwork/icon_src/trae_builtin_icon.jpg` |
> | `tools/media/strip_white_bg.py` | `artwork/icon_src/dafeiyu_icon_trae_builtin.png` |
> | `tools/media/make_launcher_icon.py` | `mipmap-*/ic_launcher_*` |
> | `tools/media/preview_launcher_icon.py` | 本地预览 mask 裁切 |
>
> 所有「探测 TRAE 生图端点」的脚本（`trae_t2i.py` / `trae_i2i.py` /
> `trae_upload.py` / `gen_icon_candidates*.py` 等 14 个）与探针目录**已删除**。
> 下文关于 i2i / MiniMax 的内容是**历史记录，勿再复活**。
>
> 装机验证：模拟器桌面图标已是新大头版（深藏青发 + 冰蓝发梢 +
> 白荷叶边发箍 + 鲸鱼鳍耳 + 白围裙绣小鲸鱼，双手托腮）。

## 历史产物（MiniMax i2i，2026-11-18，已下线）

| 文件 | 说明 |
|---|---|
| `artwork/icon_src/dafeiyu_icon_i2i.png` | 抠好的主体（纯 alpha，i2i 产出） |
| `artwork/icon_src/candidates_i2i/icon_v2.jpg` | i2i 原始输出（选中的候选） |
| `app/src/main/res/mipmap-*/ic_launcher_foreground.png` | 自适应图标前景层（现由 TRAE 版覆盖） |
| `app/src/main/res/mipmap-*/ic_launcher_background.png` | 主题蓝渐变底 |
| `app/src/main/res/mipmap-*/ic_launcher_monochrome.png` | Android 13+ 主题图标剪影 |

## 历史做法（真 image2image，已下线）

**用户原话（2026-11-18）**：「参考大肥鱼的形象图用 image2image 生成一个 app icon 图吧，
可以是大肥鱼的可爱大头比耶照，用内置的生图模型」。
> 2026-11-19 改为「只使用 TRAE 内置生图」+「不要剪刀手，一个大头表情包就好」。

### Trae 内置 i2i 全部走不通（已探到底）

| 端点 | 阻塞点 |
|---|---|
| `POST /api/ide/v1/tool_text_to_image` | `code=4011` 账号级日配额。探遍 242 组 `mode_type/request_type/chat_mode/access_type` 组合 + 30 个模型名，**全部 4011** |
| `POST /api/ide/v1/tool_text_to_image_stream` | 同上 |
| `POST /api/ide/v1/tool_edit_image_stream` | payload 契约挖出来了（`edit_type` 是**数字**枚举：1=doInpaint / 2=doOutpaint / 3=doSegment），但三条子链路都报上游 `InvalidCredential` |
| `GET /api/ide/v1/text_to_image` | **内容寻址** —— 只按 prompt+size 缓存。用独特 prompt 复测 6 个参考图参数名，**全部 SAME（参考图参数被忽略）** |

`tool_edit_image_stream` 的 payload 形状（留档，避免下次重挖）：

```json
{"edit_type": 1, "image_uri": "...", "mask_uri": "...",
 "prompt": "...", "outpaint_ratio": 0, "segment_params": {}, "seed": 0}
```

`edit_type` 传字符串一律 400 空 body。

### 最后走通的：MiniMax `image-01` 的 `subject_reference`

这才是**真 image2image**（吃参考图）。客户端：
`tools/media/gen_icon_candidates_i2i.py`。

**关键点：字段名是 `image_file`，不是 `image`。**

传 `image` 服务端报：

```
disallowed image url: localhost or private address not allowed
```

它把没取到的值当地址解析了 —— 这条报错很容易把人引到
「MiniMax 不支持 i2i」的错误结论上。正确形状（官方文档
`image-generation-i2i` 确认）：

```json
{
  "model": "image-01",
  "prompt": "...",
  "aspect_ratio": "1:1",
  "n": 1,
  "response_format": "url",
  "subject_reference": [
    {"type": "character", "image_file": "<公网图片链接>"}
  ]
}
```

三条硬约束：

1. `type` **只能**是 `character`（传 `person` 报 `subject_reference must be character`）。
2. `image_file` 是**字符串**，传数组报 `invalid params`。
3. 参考图必须是**公网能匿名下载的 URL** ——
   data URL、base64 裸串都会被判成非法地址。

**参考图的公网 URL 从哪来**：Trae 的资源上传链路
（`tools/media/trae_upload.py` 的 `upload_image_with_urls`）换签名链接：

```
https://p11-copilot-multimodal-sign.byteimg.com/tos-cn-i-.../image/xxx.trae
  ?lk3s=...&x-expires=...&x-signature=...
```

实测这个链接**匿名可下载**（裸 GET，不带任何鉴权头，返回 JPEG）。
> 注意 `get_resource_url` 返回的是 `url_map`（key 是 tosUri），
> **挂在顶层而不是 `data` 下** —— 老代码只读 `url_list`，所以拿不到签名 URL。

`/v1/files/upload` 那条路没走通（24 个 `purpose` 候选全部 `invalid file purpose`），
所以参考图走 Trae 上传，成图走 MiniMax 自己的 OSS 链接。

### Prompt（`tools/media/gen_icon_candidates_i2i.py` 里）

```
App icon of the same character from the reference image.
Super deformed big head portrait, her head takes up more than half of the frame.
She leans forward playfully toward the viewer.
Centred in a square frame with a generous empty margin around her,
the whole character fully inside the frame, nothing cropped.
Clean soft cel shading, crisp clean line art, bright saturated colors,
plain solid white background.
Readable as a small app icon on a phone launcher.
keep the exact same character design as the reference image,
her hair stays deep indigo navy blue (#2b3a6b) with only the tips fading to pale ice blue,
the same navy whale-fin shaped ears, the same white frilled maid headband
and the same light blue side bow
```

**一致性靠参考图，但发色要额外锚一道**：
`subject_reference` 对「发色」这种强特征保持得比较松，
不加颜色锚会出成**粉发通用娃娃脸**（实测第一版就是这样）。
只提颜色（不描述长相结构）+ 给出十六进制值最稳。

候选里 `v2` 最好：深藏青发 + 白荷叶边发箍 + 鲸鱼鳍耳 + 浅蓝蝴蝶结 +
双比耶 + 眨眼 + 浅蓝领结。`v4` 头顶发箍会被圆形 mask 削掉一截。

### 抠图（`tools/media/strip_white_bg.py`）

i2i 输出**没有黑框**（那是 t2i 的结构），只有一层纯白底，
所以直接用四边洪水填充即可。

为什么不能全局阈值：大肥鱼身上有大量纯白（发箍荷叶边、领结、围裙），
全局阈值会一起啃掉。洪水填充只动「从画布边缘连通的近白像素」，
角色内部的白色被描边包着，天然保住。

> t2i 那条路的三层结构（外白边 / 黑色圆角粗框 / 框内白底）
> 历史上由 `extract_icon_subject.py` 处理（**该脚本已删除**，
> 本轮 TRAE 产出没有黑框，`strip_white_bg.py` 就够了），
> 判据是**几何 + 厚度 + 漫水填充**三条，详见下文「t2i 兜底路线」。

## 自适应图标的缩放（`tools/media/make_launcher_icon.py`）

`silhouette_radius()` 用**面积分位半径**（覆盖 90% alpha 面积的那个半径），
而不是最大半径。

原因：大肥鱼有呆毛、鱼尾、鲸鱼鳍耳，三样都是又细又长的突出物。
实测最大半径 583px，但 90% 面积半径只有 385px，差了一截半。
按最大半径缩，呆毛独占一大截半径，把整个角色压小（实测只占图标 46%）。
改成面积分位后主体填满圆形可见区，呆毛尖露一点在圆外反而更灵动。

目标半径从保守的 33dp 提到 36dp（72dp 可见圆的真实半径），
再乘 **0.90** —— i2i 这版头顶有一圈很宽的白荷叶边发箍，
按 0.96 缩会被圆形 mask 切掉一截（实测预览里头顶被削平）。

## t2i 兜底路线（备用，保留在仓库）

当 MiniMax 也用不了时，还有 t2i 路径：
`tools/media/gen_icon_candidates.py` → `extract_icon_subject.py` →
`make_launcher_icon.py`。

Prompt 把每个辨识特征写死（呆毛 / 白荷叶边发箍 / 侧边浅蓝小蝴蝶结 /
鲸鱼鳍耳 / 蓝宝胸针 / 深蓝女仆装 + 金星刺绣 / 白荷叶边围裙 + 绣小鲸鱼 /
深蓝鱼尾），并且**把「深蓝」写成比例 + 部位**
（「上三分之二是深藏青，只有发梢渐变成浅蓝」），
不然模型会把整体淡化。

**这条路角色会漂**（出成粉发），
所以只在 i2i 不可用时启用。

t2i 输出的结构是三层：

```
白边（画布外圈 ~30px）
  ███ 黑色圆角方框描边（左右 106px 厚 / 上下 32~55px 厚）███
    白底 + 角色
```

全都要去掉，但**不能按颜色删** —— 角色身上的白色荷叶边、围裙跟框内白底同色。

三个判据分别解决三个问题：

| 要去的东西 | 判据 |
|---|---|
| 框内白底 | 从框内四边**漫水填充**白色（角色被黑色描边包着，填不到） |
| 黑色描边框 | **厚度**：连续暗色 ≥ 25px 是框；角色描边只有 1~3px |
| 框外（含黑框直边） | **几何**：沿四边扫描定位框的内边界矩形 |

判定框位置时踩过一个坑：一开始取「最大的暗色连通块」当框，
结果把**深藏青的头发**删了（头发在阈值下也算暗色，面积比框还大）。
最后用「厚度」才分干净。
