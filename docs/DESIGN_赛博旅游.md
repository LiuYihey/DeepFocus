# 赛博旅游 · 设计文档

> 状态：**已实施（2026-11-22）**，DB v18
> 取代：原「陪读」设定（专注期间大肥鱼陪你看书）—— 陪读模块整体下线
> 关联：`CONTEXT.md`（领域词汇）、`.trae/rules/project_rules.md`（项目规则）
>
> 实施备注（与设计的差异）：
> - 装扮不进 Room：两套固定形象放代码常量（`FishOutfits`），当前选择存
>   `SharedPreferences`（`FishOutfitStore`），全局用 `LocalFishOutfit` 注入到各 `FishCanvas`。
>   比加表加列更轻，也避免 `fish_catalog` 多一行假品种。
> - 旅行的执行收在 `TravelService`：专注自动触发与游历本的调试触发共用同一套流程。
> - 同一段专注只跑一趟：session 起始时间写入 prefs，Activity 重建后不会重复烧 token。
> - 存相册在 API 26–28 走「应用外部目录 + MediaScanner」，**全程零运行时权限**。
> - 聊天会话另立 `chat_conversations` 表（DB v18），见第九节；
>   `travel_journals.photoIdea` 也在 v18 补上（她设计的「这一秒拍什么」）。

---

## 一、为什么是赛博旅游

**陪读的问题**：每 3 分钟读一页，每页都要把正文喂给模型 —— token 消耗随阅读时长线性上涨，
而「书」这个载体对陪伴感的贡献远不如「它自己去了某个地方、遇见了什么」。

**赛博旅游的收益**：同样一笔 token，产出的是**有地点、有事件、有情绪的完整故事**，
而且每次都不一样（agent 自己编，不是模板）。

**核心设计原则**：
> **目的地和故事必须由 agent 主观生成，不能是本地模板库。**
> 本地事件库会让每次旅行长得一样 —— 趣味性和多样性直接归零。

---

## 二、一次旅行的生命周期

### 2.1 时序

```
用户开始专注
    │
    ├─ 0–15 min  ──►  不触发任何旅行（省 token）
    │
    ├─ 15 min 到达 ──► 触发 TravelWorkflow（一次性跑完）
    │                    │
    │                    ├─ ① 目的地选定（Agent 主决策）
    │                    ├─ ② 故事构造（Event Designer LLM）
    │                    ├─ ③ 角色反应（Agent 读故事后反应）
    │                    └─ ④ 成文入库（旅游日记）
    │
    ├─ 专注继续 ──►  桌宠闲聊 / 状态更新，不额外烧 token
    │
    └─ 专注结束 ──►  向用户呈递「旅行日记卡片」
```

**关键决策**：
- **15 分钟是硬门槛**。0–15 分钟不触发 —— 短专注不值得烧 3 次 LLM。
- **整个专注只跑一趟**。60 分钟专注 = 1 个故事，不是 3 个。token 可控。
- **15 分钟触发，不是专注结束时总结**。旅程在专注中途就完成了，
  用户在专注页就能看到「她去了冰岛」的状态，结束时才拿到完整故事。

### 2.2 为什么是三个角色（三个 LLM 调用）

单次 LLM 写完「去哪 + 发生什么 + 她怎么反应」质量会塌 —— 一个 prompt 扛三个任务，
容易变成套路化的流水账。

拆成三个角色，各自职责单一：

| 角色 | 职责 | 产出 | 关键约束 |
|---|---|---|---|
| **① 目的地 Agent** | 决定这次去哪 | 地点 + 一句动机 | 依据自己的偏好 + **过往旅游经验**（去过哪、喜欢什么）|
| **② 故事构造器** | 把地点变成事件 | 故事提纲 | 结合当地风土人情、历史背景、趣味传说、景点 |
| **③ 角色 Agent** | 读故事并反应 | 反应 + 日记正文 | 以大肥鱼第一人称、有情绪、会玩梗 |

**token 控制**：3 次调用全是「短 prompt + 短输出」，不传长上下文。
目的地 Agent 只拿「过去去过哪」的摘要列表（几十字），不带完整历史。

### 2.3 目的地选取规则

**由 agent 自己决定**，但受两个客观约束牵引：

1. **装扮倾向**（用户可切换）——
   - 冬装（`winter.jpg`）→ 倾向**寒冷**目的地（冰岛 / 雪国 / 北欧 / 高山）
   - 常服（`bigfatfish.png`）→ 倾向**温暖**目的地（东南亚 / 地中海 / 岛屿）
   - 这是「性格化」的体现：穿冬装去热带会很出戏。
2. **不重复偏好** —— 已经去过的地点降低权重（可以再去，但要给出理由，比如「重返」）。

**过往旅游经验从哪来**：游历本里已有的旅行日记。目的地 Agent 的输入包含
「你去过：冰岛、京都…」这样的短摘要，避免每趟都去同一个地方。

### 2.4 与装扮的关系

- **装扮影响生图参考图**：当前装扮决定参考图用 `assets/fish/bigfatfish.png` 还是 `assets/fish/winter.jpg`。
- **装扮影响目的地倾向**（见上）。
- **游历本是共用的** —— 常服和冬装的旅行日记进同一本游历本。

---

## 三、生图：手动触发，不自动

### 3.1 交互流程

```
用户读旅行日记
    │
    └─ 点「画一张旅行相片」
         │
         ├─ ① Agent 根据这段经历选一个生图 prompt
         │     （形式自由：自拍 / 他拍 / 事件漫画 / 风景空镜）
         │
         ├─ ② 调生图 API（带参考图，保住角色一致性）
         │
         ├─ ③ 等待中：ChatGPT 同款「生成中」动效
         │
         └─ ④ 出图 → 存入该条日记 → 用户可「保存到相册」
```

**为什么手动而不是自动**：
- 每张图真金白银，自动生成 = 每趟旅行都烧一次钱。
- 手动触发 = 用户自己决定哪趟旅行值得留影。
- 也保留了「形式自由」的空间 —— agent 看到日记内容才选得出合适的 prompt。

### 3.2 生图 API 配置

**完全独立于聊天 API**（设置里另开一块「生图 API」）：base_url + api_key + 模型名。

**协议**：OpenAI 兼容。MiniMax 也兼容 OpenAI SDK 形态，但 **MiniMax 的
`/v1/images/edits` 走 JSON + base64 data URI 参考图**，与 OpenAI 原生
`images.edit` 的 multipart 不同 —— 需要在客户端层区分。

| 环节 | MiniMax | OpenAI 原生 |
|---|---|---|
| 端点 | `POST /v1/images/edits` | `POST /v1/images/edits` |
| 请求体 | JSON：`images[].image_url` = base64 data URI | multipart/form-data |
| 参考图 | `data:image/jpeg;base64,...` | 文件直传 |
| 模型 | `MiniMax-H3-Image` | `dall-e-3` / `gpt-image-1` |

**参考图硬约束（MiniMax）**：
- 格式 JPG / PNG / WEBP / HEIC / HEIF
- 宽高 ∈ [128, 8192] px
- 长宽比（长边/短边）≤ 4:1
- 单文件 ≤ 64 MiB（base64 后约 4/3）
- 请求体总大小 ≤ 64 MiB

**当前资产**：
- `assets/fish/bigfatfish.png`（779,654 B，PNG）
- `assets/fish/winter.jpg`（968,960 B，JPG）

两者都在约束内，但 **PNG 需转 JPEG 再 base64**（减小体积 + 规避格式差异）。

### 3.3 出图尺寸与风格

- 尺寸：`aspect_ratio` + `area`，或直接 `size: "宽x高"`（互斥，同时传会 400）。
- **日记配图用 3:4 竖版**（`area: "2K"` → 1760×2368）—— 卡片式展示，竖图更合适。
- 形式由 agent 在 prompt 里决定，MiniMax-H3-Image 支持文生图与图片编辑，
  **图片编辑始终开启 prompt 增强**，可拿到 `revised_prompt` 供调试。

### 3.4 画风：人物跟随参考图，其余自由（2026-11-22 定稿）

用户报过「冰岛那篇的配图很恐怖，二次元和真人风格融合」。
追下来根因不是「画风不够好」，而是**prompt 越权替模型决定了画风**：
prompt 里写死了「日系二次元插画 + 禁止写实」，模型就在参考图之外
**重新挑了一个画风**，出来的人物跟参考图对不上 —— 半拼半凑就是恐怖谷。

正确口径（用户原话：「不是生图prompt锁死二次元画风，
而是人物画风不变，场景故事等可以自由发挥，
人物参考图是什么画风就是什么画风就可以了」）：

| 交给谁 | 管什么 |
|---|---|
| **参考图** | 人物的画风、长相、服饰、配色 |
| **prompt** | 场景、故事、镜头、光线、氛围、这一秒拍什么 |
| **prompt 只做一件事** | 要求整张图**不要混搭**（人物是 A 画风、背景却是 B 画风 → 恐怖谷）|

所以 `buildPhotoPrompt()` 里：
- **一个字都不描述角色长相**（项目规则 §3.2：一致性靠参考图，不靠文字）。
- **不写死画风**（参考图是赛璐璐就赛璐璐，将来换画风 prompt 也不用改）。
- 只留一条「不要混搭」的约束 —— 整张图要是同一种画风。
- 形式（自拍 / 他拍 / 漫画分镜）她自己挑，**不设偏好**。

另外 `travel_journals` 加了 `photoIdea` 列 ——
她在旅行 workflow 里自己设计的「这一秒拍什么」，之前算了却没落库，
配图时只能用通用描述。现在落库并进 prompt（形式与画风仍归参考图管）。

---

## 四、聊天发图（多模态）

### 4.1 选图方式

**系统照片选择器（Photo Picker）** —— **零运行时权限**：
- `ActivityResultContracts.PickVisualMedia()`，用户只能选单张，选不到相册全部内容。
- 不需要 `READ_MEDIA_IMAGES`，也不需要 `READ_EXTERNAL_STORAGE`。
- 项目当前**完全没有运行时权限申请机制**（只有跳系统设置页的 helper），
  Photo Picker 恰好绕开了这个缺口。

### 4.2 要发给模型 → 必须改造网络层

**现状**：`LlmClient.buildRequestBody()` 里 `messages[].content` **永远是 String**，
`LlmMessage` 也只有 `content: String`，没有 base64 / image_url / multipart 处理。

**要做的**：
- `LlmMessage.content` 升级为「String 或 content-parts 数组」。
- content-parts 形态（OpenAI 兼容）：
  ```json
  {"role":"user","content":[
    {"type":"text","text":"这张图好看吗"},
    {"type":"image_url","image_url":{"url":"data:image/jpeg;base64,..."}}
  ]}
  ```
- 图片先压缩再编码（避免请求体过大）。
- 模型不支持视觉时**自动降级**为「仅展示不给模型看」，UI 上告知用户。

### 4.3 图片存哪

新增 `chat_messages` 列（`imageUri` / `imageMime`），图片文件落
`filesDir/chat_images/`，`ChatMessageEntity` 加字段 + Room 迁移。

---

## 五、装扮（常服 / 冬装）

### 5.1 与既有「已废弃概念」的关系

`CONTEXT.md` 里「装扮」被标为**已废弃概念**（2026-11-17 全线下线）。
本次是**有意识的复活**，但语义收窄 —— 不是「解锁奖励 / 皮肤系统」，
而是**两套固定形象 + 一个开关**。

需要修订 `CONTEXT.md`：
- 「装扮」条目从「已废弃概念」改为**当前有效概念**，并写明只有常服 / 冬装两套、无解锁、无自动切换。
- 「品种」保持废弃（只有一只大肥鱼，不引入第二个角色）。

### 5.2 入口

| 位置 | 行为 |
|---|---|
| **bootstrap 选鱼页**（`FishSelectionScreen`）| 两张卡：常服 / 冬装。选完写入 `FishOutfitStore` |
| **设置页** | 随时切换装扮（影响生图参考图 + 目的地倾向）|

**数据模型**（以实施为准，早期方案已作废）：
- 装扮**不进 Room** —— 只有两套固定形象 + 一个开关，存在
  `SharedPreferences deep_focus_outfit`（`data/FishOutfitStore.kt`）。
- `fish_catalog` 仍然只有 `bigfatfish` 一行；`user_fish.breedId` 固定
  `bigfatfish`，不拿它存装扮。
- 「品种 / 解锁皮肤」保持废弃 —— 装扮不是养成奖励，是外观开关。

**资源**：
- 常服：`assets/fish/bigfatfish.png`（透明 PNG，带桌宠动画）
- 冬装：`assets/fish/winter.jpg`（968 KB 正脸立绘，白底）
  - 界面立绘用 `assets/fish/winter_cutout.png`（洪水填充去白底后的透明 PNG，
    否则浅色首页上会出现一块白方）
  - 生图参考图仍用原图 `winter.jpg`（`FishOutfit.referenceAssetPath`）

### 5.3 渲染影响

`FishCanvas` / `FishImage` 通过 `LocalFishOutfit` 拿到当前装扮的立绘资源；
桌宠动作（`assets/pet/anim/all/`）是 dsh-pet 的透明 WebP，**没有冬装版本**。

**装扮的作用范围（2026-11-22 用户决定）**：

| 位置 | 跟不跟装扮换 |
|---|---|
| 聊天头像（顶栏 / 气泡内）| ✅ 换 |
| 新对话页的大图 | ✅ 换 |
| 生图参考图 | ✅ 换（`referenceAssetPath`）|
| **首页大肥鱼的 GIF** | ❌ **不换** |

首页动作库只有常服一套，冬装没有配套动画 ——
所以首页**固定用常服**（`FishCanvas(outfit = FishOutfits.NORMAL)`），
选冬装时首页照旧播常服动作。用户原话：「选择冬装装扮之后，
大肥鱼首页的GIF展示是不用变的，因为没有冬装的GIF，
改变的是新chat页面的展示的图片，同时也更换chat agent的头像。」

---

## 六、游历本（旅游日记本）

### 6.1 入口

**设置里的子页**，与「记忆」平级。底部 tab 不动（保持 4 个）。

### 6.2 数据模型

新表 `travel_journals`：

| 字段 | 说明 |
|---|---|
| `id` | 主键 |
| `title` | 日记标题（agent 生成，如「冰岛的第七个下午」）|
| `destination` | 目的地 |
| `content` | 故事正文（agent 第一人称）|
| `mood` | 她此刻的心情（开心 / 疲惫 / 惊喜…）|
| `outfit` | 生成这趟旅行时的装扮 |
| `imagePath` | 配图本地路径（可空，手动生图后回填）|
| `imagePrompt` | 实际用的生图 prompt（便于重生成）|
| `createdAt` | 时间戳 |

配套 DAO：`TravelJournalDao`（按时间倒序、插入、update imagePath）。

**归属**：`user_fish` 单条大肥鱼 → 日记直接挂在 App 下，不需要外键。

### 6.3 卡片 UI

「适合收藏的旅游故事卡片」—— 不是普通列表项。设计要点：
- 大图优先（有图则卡片顶部是图，无图则用目的地氛围色块 + 文字排版）。
- 标题 + 目的地 + 日期 + 正文节选（可展开全文）。
- 「画一张旅行相片」按钮（未生图时）/「保存到相册」（已生图时）。
- 可收藏（用户标星）——「适合收藏」意味着有「特别收藏」的状态。

### 6.4 保存到相册

**现状**：项目**完全没有** MediaStore 相关代码。

**要做的**：
- API 29+：`MediaStore.Images` + `RELATIVE_PATH = Pictures/DeepFocus`
- API 26–28：`WRITE_EXTERNAL_STORAGE` + `MediaScannerConnection.scanFile`
  （minSdk 26，必须做兼容分支）
- 图片先写到 `cacheDir/exports/`，再通过已有的 `FileProvider`
  （`file_paths.xml` 已暴露 `cache-path name="exports"`）分享。

---

## 七、陪读模块下线

**删除清单**：

| 对象 | 路径 |
|---|---|
| Room 表 | `BookEntity` / `books` 表 + `BookDao` |
| 引擎 | `reading/ReadingEngine.kt`、`BookImporter.kt`、`EpubTextExtractor.kt`、`PdfTextExtractor.kt` |
| UI | `ui/settings/BooksScreen.kt`、`BooksViewModel.kt`、路由 `books` |
| 专注页 | `readingBookTitle` / `readingPage` 字段 + 进度文案 |
| 主动动作 | `ProactiveAction.READING` |
| Agent 上下文 | `FishBrain.currentReadingBook()` |

**Room 迁移**：新版本迁移里 `DROP TABLE books`（配 `MIGRATION_16_17`）。
`check_migration_chain.py` 的基线 schema 目录同步加 `17.json`。

**Agent 上下文**：`buildWorldContext()` 里的「正在读《X》第 N 页」换成
「昨天去了冰岛，遇到了一个卖热汤的老奶奶」这类旅行记忆。

---

## 八、Agent 记忆：去过哪、开不开心、遇到谁

**三件事分别落在哪**：

| 要记的 | 存哪 | 怎么记 |
|---|---|---|
| 去过哪 | `travel_journals.destination` | 日记入库时自然带上；另在 `FishState` 加 `visitedPlaces` 摘要串供目的地 Agent 用 |
| 开不开心 | `travel_journals.mood` | agent 自评；同时影响桌宠表情 |
| 遇到谁 / 什么事 | `travel_journals.content` + `FishMemory` | 故事正文里已含；**值得记的人/事**抽成记忆候选，走 `MemoryEngine` 三态流程（CANDIDATE → 用户确认 → CONFIRMED）|

**记忆提炼的复用**：`MemoryEngine.extractCandidates()` 现成可用 ——
旅行故事生成完后喂一次，抽取「遇到的人/地方」作为候选记忆。
这与现有聊天记忆提炼是同一条通路。

---

## 九、聊天会话与记忆连贯（2026-11-22 追加）

用户需求：「chat agent 注意可以开启新的对话吧，但是大肥鱼记忆是连贯的，
只是开启新对话可以让用户比较好的归档而已，注意避免大肥鱼上下文的过载，
模仿 deepseek harness 的方式去做好 agent 记忆，比如睡觉的时候总结上下文。」

### 9.1 参考的开源方案

| 项目 | 做法 | 本项目对应 |
|---|---|---|
| [dsh-memory](https://github.com/haitang1/dsh-memory)（MIT）| 每段 session 蒸馏一份 rollout summary，下一段注入 prompt；全局长期记忆另有一本账 | **交接笔记** + `fish_memories` |
| [dsh-auto-memory](https://github.com/Aik358/dsh-auto-memory) | 四段式 handoff note（任务状态 / 目标 / 尝试过什么 / 进展与下一步）| 交接笔记的四个小节 |
| [dsh-brain](https://github.com/LINYT1416/dsh-brain) | 「浅巩固 / 深巩固」—— 睡前把当天的事蒸成记忆 | **睡觉时总结上下文**（`ProactiveScheduler` 的睡眠分支调 `summarize()`）|

### 9.2 数据模型（DB v18）

新表 `chat_conversations`：

| 字段 | 说明 |
|---|---|
| `id` | 主键 |
| `title` | 会话标题（取用户第一句话前 16 字）|
| `summary` | **交接笔记** —— 这段会话压成的几百字 |
| `compactedUpToId` | 最后一条被写进摘要的消息 id；上下文窗口只取 id 更大的 |
| `createdAt` / `updatedAt` | 时间戳 |
| `archived` | 是否已归档 |

另加两列：
- `chat_messages.conversationId` —— 消息归属会话（可空 = 未归入）
- `fish_memories.conversationId` —— 记忆归属会话，**删会话时连带删记忆**

迁移 `MIGRATION_17_18`：建表 + 补两列 + `travel_journals.photoIdea`。
老消息回填到 id=1 的「最早的那段对话」（否则升级后历史记录会「消失」）。

### 9.3 三条机制

| 机制 | 触发 | 做什么 |
|---|---|---|
| **交接笔记** | 开新对话（归档旧会话）时 | 旧会话压成 ≤240 字摘要（四个小节），存旧会话的 `summary` |
| **记忆连贯** | 新会话第一轮 | 把最近一个别的会话的 `summary` 注入 system prompt（`【上一段对话…】`）|
| **上下文压缩** | 当前会话消息数 > 40 | 早期消息压进摘要，`compactedUpToId` 前移，窗口只留最近 20 条原文 |

外加 **睡觉时总结**：`ProactiveScheduler` 检测到她要睡时，先 `summarize()` 一次再睡。

**为什么这套能同时满足「记忆连贯」和「上下文不过载」**：
旧对话的原文不再进上下文窗口（省 token），但信息压成了几百字还在（不失忆）。

### 9.4 删除

- 删**会话** → 连删该会话的消息 + 该会话抽出来的记忆。
  不删记忆的话，她会一边说「不记得了」一边又提起被删掉的事。
- 删**旅行日记** → 连删磁盘上的配图文件（`filesDir/travel_images/`）。
- 入口：聊天页顶栏「对话记录」抽屉（可切换 / 可删，带二次确认）；
  游历本卡片上的删除按钮。

### 9.5 表情包：她自己决定发不发

用户反馈：「目前的表情包发送是每次都必发送的吗，感觉是随机固定发送的，
agent 没有阅读这个表情包本身是什么内容，就随便发一个糊弄的感觉。」

改造：
- **去掉随机兜底**（碎碎念原来必配一张随机图）—— 现在碎碎念也把清单给模型，
  她自己挑。
- **去掉关键词猜测**（`StickerMarker.resolve` 原来会按情绪词捞一张）。
- prompt 里明确：**不是每次都要配图**，挑不出来就只回文字。

### 9.6 验收

- [x] 顶栏有「开启新对话」和「对话记录」
- [x] 开新对话后，旧对话被归档并生成交接笔记
- [x] 新对话里她能说出上一段对话的内容（交接笔记注入生效）
- [x] 删会话连带删消息与记忆
- [x] 表情包由模型自选，可不发
- [x] 睡眠时触发上下文总结

---

## 十、实施顺序

| 阶段 | 内容 | 依赖 |
|---|---|---|
| **P0** | 装扮：选鱼页两张卡 + 设置切换 + `FishCanvas` 按 breedId 取图 | 无 |
| **P1** | 陪读下线 + Room 迁移 v17 | 无 |
| **P2** | 多模态改造：`LlmMessage` content-parts + Photo Picker 选图 + 图片入库 | 无 |
| **P3** | 生图客户端：OpenAI 兼容（JSON base64 + multipart 两种）+ 独立配置 + 手动触发 + 保存到相册 | P2 的图片编码工具 |
| **P4** | 赛博旅游：`TravelWorkflow` 三角色 + `travel_journals` 表 + 15 分钟触发 + 游历本子页 + 卡片 UI | P3 |

**P0 与 P1 可并行**（改不同文件）。P3 依赖 P2 的 base64 编码工具函数。
P4 依赖 P3（生图在故事生成之后手动触发）。

---

## 十、验收标准

- [ ] 选鱼页有常服 / 冬装两张卡，选完设置里能随时换
- [ ] 换冬装后，聊天初始界面 / 首页立绘变成冬装
- [ ] 专注 < 15 分钟不触发任何旅行
- [ ] 专注满 15 分钟自动跑一趟旅行，专注页能看到「她去了哪」的状态
- [ ] 专注结束弹出旅行日记卡片
- [ ] 同一 App 连续两次专注，目的地不同（agent 自己选）
- [ ] 冬装偏向寒冷目的地，常服偏向温暖目的地
- [ ] 日记正文是第一人称、有具体地点和事件，不是套话
- [ ] 点「画一张旅行相片」→ 等待动效 → 出图，**人物画风与参考图一致、不混搭**
- [ ] 图能存进相册（API 26 和 API 33+ 都要验证）
- [ ] 游历本能看到所有历史旅行日记，卡片 UI 美观
- [ ] 游历本能删掉某篇日记，磁盘上的配图文件也一起消失
- [ ] 聊天能发图，模型能看懂并回复
- [ ] 选图不弹任何权限申请
- [ ] `books` 表已删除，迁移链 v16→v17 通过
- [ ] 陪读相关代码全部删除，无残留引用
- [ ] 开新对话后记忆仍连贯（交接笔记生效），旧对话可切回可删
- [ ] 表情包由模型自己挑，她可以不发表情包
- [ ] 选冬装后首页 GIF 不变，聊天头像 / 新对话页大图换成冬装
- [ ] 设置页底部不再有 App 图标区块
