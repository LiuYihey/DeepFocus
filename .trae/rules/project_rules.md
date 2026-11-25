# DeepFocus 项目规则

## 〇、产品定位（本轮定稿）

DeepFocus 是**大肥鱼专注陪伴 App**。只有三条主线：

1. **专注锁机** —— 专注计时 + 切屏拉回
2. **桌宠陪伴** —— 常驻桌面，会动、能说话、能催你专注
3. **AI 聊天** —— 拟人化对话 + 记忆

### 0.1 已废弃：约束应用 + 拦截弹窗

- 「约束应用」（手动选 App 进黑名单）功能**已整块删除**，不要再加回来。
- 反思问答页 / 确认页 / 鼓励页三段弹窗**已整块删除**。
- 保留的是**锁机**：专注期间切到别的 App，把用户拉回前台。
  这条链路在 `service/DeepFocusService.kt`（`bringFocusBackToForeground()`）
  和 `util/FocusSessionState.kt` 里，**改的时候别碰坏**。
- 底栏只有 3 个 tab：**大肥鱼 / 聊天 / 设置**。

### 0.2 文案原则

- **不写多余的二级标签和说明文字**，让布局自己说话。
  页首解释句、开关下方补充说明、状态旁白、重复的小标题 —— 全删。
- 只保留必要信息：空态引导、权限说明、合规提示、密钥安全说明。
- **大肥鱼一律用「它」指代，不用「她」**（2026-11-24 定稿）。
  它是鱼，不是人。UI 文案、AI 提示词、种子数据、宣传物料全部统一。
  已全量改完：成就描述 / 设置 / 记忆 / 游历本 / 专注页 / 聊天 / 生图设置 /
  人格卡选鱼 / 旅行工作流提示词 / 投喂 / 成就弹窗。
  写新文案时注意别把「她」写回去。
- **成就弹窗要一行放得下**（2026-11-24 定稿）。
  用 `usePlatformDefaultWidth = false` 铺满屏宽，
  「成就解锁 / 称号 / 描述」各占一行，描述不折行、`maxLines = 2` 兜底。
  不要把奖励写进描述里再靠 `\n` 断行 —— 那样在窄屏上会碎成三四行。

### 0.3 UI 高定规范（严格执行，逐条可查）

| # | 规范 | 落地位置 |
|---|---|---|
| 1 | **禁黑框与 #000**，靠 1-3% 微明度 4 层背景分层 | `theme/Color.kt` 四层背景；全项目无 `Color.Black` |
| 2 | **输入框聚焦禁描边**，加深双环阴影 | `components/SoftTextField.kt` |
| 3 | **暗色字重全降 40** | `theme/Weight.kt` 的 `themedWeight()`，禁硬编码字重 |
| 4 | **流式打字 blur(6px) 渐显**，120ms 且字间错开 30ms | `components/TypewriterText.kt` |
| 5 | **图标下移 0.125em 对齐** | `components/IconAlignment.kt` |
| 6 | **数字锁 tabular-nums 防抖** | `theme/Type.kt` 的 `tabularNumbers()` |
| 7 | **折叠用 Grid 0fr→1fr** | `components/SoftTextField.kt` 的 `CollapsibleSection` |
| 8 | **正文开 text-wrap: pretty** | `theme/Type.kt` 的 `LineBreak.Paragraph` |
| 9 | **拖拽弹簧 1200/80 零回弹，点赞 380/18 微弹** | `theme/Motion.kt`；桌宠 `pet/PetPhysics.kt` |
| 10 | **复制对勾 2.5s 自隐** | `components/CopyIconButton.kt` |

- 弹簧换算：`dampingRatio = damping / (2 × √stiffness)`。
  1200/80 → ratio 1.155（零回弹）；380/18 → ratio 0.462（微弹）。
- 视觉体系：**深海蓝 + 奶白 + 蜜桃点缀**。蜜桃 `#E8A87C` 是整屏唯一暖色。

### 0.4 界面改造规范（2026-11-17，替换 FocusCat 视觉）

旧版 UI 是 FocusCat（猫咪专注产品）的翻版。这轮整体重做，落成五条硬规则：

| # | 规范 | 落地位置 |
|---|---|---|
| 1 | **舞台式布局**：身份（名字/称号/状态）在上，角色居中，信息在下 | `ui/fish/FishScreen.kt` |
| 2 | **氛围光晕**：角色背后要有柔光，让它从背景"浮"出来 | `ui/components/Stage.kt` 的 `AmbientHalo` |
| 3 | **状态说人话**：不暴露内部数值 | `ui/fish/FishViewModel.kt` 的 `statusPillOf()` |
| 4 | **CTA 紧跟台词**：主行动放在角色/台词下方，首屏可见 | `FishScreen.kt` 的 `PrimaryButton` |
| 5 | **图标语义归位**：爪印是猫的，鲸鱼标志才是鱼的 | `ui/icons/AppIcons.kt` 的 `AppIcons.DeepSeek` |

**「人物放中心」的正确做法（2026-11-17 用户明确纠正过）**：

> 用户原话：「我说把人物放中心，你就上面全留空，上面那么多空是给用户画画的吗？」

- ❌ **不能**用「顶部塞一大块留白把角色推到首屏中线」来假装居中 ——
  那样上面空一大片，下面全堆着，看着是「上面空、下面挤」。
- ❌ **不能**用 `Arrangement.Center` 把整块内容居中 ——
  内容总高度一变（台词长度不同），角色就跟着上下漂，看着像在飘。
- ✅ **正确**：舞台区是**固定高度**的（占首屏 46%），
  角色在这个固定区域里垂直居中 → 位置绝对稳定。
- ✅ **正确**：舞台区紧贴页面顶部，不要在它上面加留白 ——
  角色就是这一页的第一眼，下面才是名字/台词/CTA/档案。

**内部数值一律不出现在界面上** —— 饱腹度、精力值只在引擎层判断，
UI 层翻译成人话（「吃得很饱」/「饿了」/「睡得很香」）。
用户明确说过「饱腹感数值也不需要在 UI 中显示出来」。

**不要复活 FocusCat 的视觉残留**：猫爪图标、状态数值卡、顶部 CTA、
平铺成就卡、四层数值堆叠。改 UI 时对照这条。

**轮播不要重复同一个动作**（2026-11-17 用户反馈「重复的很没趣味」）：
登场短剧必须是「播完一段换下一段」，
且记住最近 3 段避免短期内重复 —— 见 `ui/components/FishImage.kt`
的 `recentScenes` 逻辑。`loop = true` 会让 `onCompletion` 永远不触发，
是错的。

**图标规范（2026-11-17 用户明确要求）**：

> 用户原话：「大肥鱼 app 的 icon 换一下，换成大肥鱼本身的图，
> 而不是生成出来的好了，以及现在水波代表大肥鱼的设计也换掉，
> 用 deepseek 的标志代替都好一些吧」

- **启动图标就是大肥鱼本人** —— 用 `assets/fish/bigfatfish.png`，
  **不要重新生成角色**（生图会漂，破坏一致性）。
  生成脚本：`tools/media/make_launcher_icon.py`。

  > **2026-11-19 用户定了最终路线**：「我只使用 TRAE 内置生图」。
  > **TRAE 内置没有 image2image** —— `GenerateImage` 工具的参数只有
  > `prompt` / `path` / `image_size`，**没有参考图入参**；
  > 插件目录里也 grep 不到任何 i2i / reference_image 能力。
  > 所以启动图标走 **t2i + 特征锁定**：
  > `artwork/icon_src/trae_builtin_icon.jpg`（TRAE 内置产出）
  > → `tools/media/strip_white_bg.py` → `artwork/icon_src/dafeiyu_icon_trae_builtin.png`
  > → `tools/media/make_launcher_icon.py`。
  > 构图是**双手托腮的大头表情包**（用户明确不要剪刀手）。
  > 所有「探测 TRAE 生图端点」的脚本已全部删除，**不要再写回来** ——
  > 见 §3.1。
  > **App 内的大肥鱼形象仍然不用生成图**。
- **必须出自适应图标**（`mipmap-anydpi-v26/ic_launcher.xml`）：
  背景层（渐变底）+ 前景层（角色）+ 单色层（Android 13+ 主题图标）。
  只给 legacy PNG 的话，系统 mask 会再切一圈，角色被压小。
- **前景层按 alpha 轮廓的「面积分位半径」缩放**，不是按外接矩形对角线，
  也不是按最大轮廓半径 ——
  - 按外接矩形对角线：Q 版角色对角线远大于轮廓圆，角色只占图标 62%（实测）。
  - 按**最大**轮廓半径：呆毛 / 鱼尾 / 鲸鱼鳍耳这些细长突出物会独占一大截半径，
    把角色压小（实测最大半径 583px 但 90% 面积半径只有 385px，差一截半）。
  - 正确做法：**累加 alpha 面积，覆盖 90% 为止的那个半径**（`silhouette_radius()`）。
    主体填满可见圆，呆毛尖露一点在圆外反而更灵动。
  - 目标半径用 **36dp**（72dp 可见圆的真实半径），不是保守的 33dp。
    33dp 那套安全区是给规整方形角色的，大肥鱼这种形状撑得厉害，用 33dp 会缩在中间太小。
  - 再乘 **0.90**：图标头顶有一圈很宽的白荷叶边发箍，
    按 0.96 缩会被圆形 mask 削掉一截（实测预览里头顶被切平）。
- **抠图要按「几何 + 厚度 + 漫水填充」三条判据，不能按颜色删**：
  - 框内白底 → 从框内四边**漫水填充**白色（角色被黑描边包着，填不到）。
  - 黑色粗框 → 按**厚度**判：连续暗色 ≥ 25px 是框，角色描边只有 1~3px。
    ⚠️ 不能取「最大的暗色连通块」——深藏青头发在阈值下也算暗色，面积比框还大。
  - 框外 → **几何**：沿四边扫描定位框的内边界矩形。
  - 角色身上的**白色荷叶边 / 围裙**跟框内白底同色，
    任何「按颜色删」的做法都会把围裙吃掉。
- **底栏「大肥鱼」Tab 用 DeepSeek 鲸鱼标志位图**（`R.drawable.ic_deepseek_whale`），
  不用水波纹 —— 水波纹太抽象，看不出是什么。
  位图用 `Painter`（`SimpleNavEntry.icon`）而不是 `ImageVector`，
  选中/未选中两态用 `tintSelected` / `tintUnselected` 控制，
  品牌图标给 `Color.Unspecified` 保留原色。
- 改图标后要**清一次启动器缓存**（`pm clear <launcher 包名>`），
  否则看到的是旧图标。
- 本地预览 mask 裁切效果用 `tools/media/preview_launcher_icon.py`，
  不用等装机才发现角色被裁掉。

- 配套组件都在 `ui/components/Stage.kt`：`AmbientHalo` / `GlassCard` /
  `StatusPill` / `InlineSectionLabel` / `EmptyState` / `SubPageTopBar`。
  新页面直接用这几个，别自己再造。
- 专注页底色是**深海墨蓝渐变**而不是主题背景色 —— 那一页文字全是白的，
  白底白字等于隐形。
- **二级页是沉浸的**：`ai_settings` / `persona` / `books` / `memory` / `pet`
  五个子页不挂底部 Tab 栏（`MainScreen.kt` 的 `subPageRoutes`），
  它们有自己的返回栏和 `SubPageTopBar`。
- **空状态要有邀请感**：图标 + 一句说明 + 一个能立刻做的动作。
  两行灰字飘在屏幕中间是加载失败的样子，不是空状态的样子。
- **禁止暴露技术单位**：`${sizeDp} dp` 这种内部实现值不摆到界面上，
  说人话（「小/中/大」）。同理，页码、好感度阈值都用语义化说法。

---

## 一、App 设计原则（先看这条）

### 1.1 养成只有一条主线：好感度

- 好感度（`UserFishEntity.affinityLevel`）是**唯一**成长数值。
- 好感度有**两个**来源：专注时长累积（主路径）、喂一次大白饭 +1（次路径）。
  - 2026-11-18 用户决定：「点击大白饭后饱腹度增加，同时好感度也可以慢慢增加」，
    并且好感度要能**解锁人格卡**。
  - **量级必须压住**：喂一次 +1，专注 10 分钟也才 +1。
    不然「躺着点大白饭」会比「真的专注」划算，好感度就贬值了。
- **不做「解锁其它角色 / 解锁皮肤」的设计**。
- 好感度涨了就是涨了，不需要额外的解锁仪式。
- **只有一只大肥鱼** —— 没有品种切换、没有装扮切换、没有第二个伙伴。
  「切换大肥鱼」「添加第二个伙伴」整块删除，不要加回来。

### 1.1.1 已删除的维度（都是 FocusCat 的遗留，不要复活）

| 维度 | 为什么删 |
|---|---|
| **心情 mood** | 情绪由好感度档位推导，不单独存可正可负的数值 |
| **精力 energy** | 猫模型的遗留，用户从没设计过，也没有可见入口。睡觉判据改成「长时间没互动就睡」 |
| **装扮 outfit**（常服/冬装） | 季节自动切 + 手动锁定都不在设计里。`FishOutfitStore` 已删除 |
| **大肥鱼格言 quotes** | 那是「专注猫」的功能，不属于 DeepFocus。`quotes` 表已删 |

### 1.2 人格卡：内置 6 张（好感度解锁 + 可手动切回）+ 自定义包

**内置包** —— 与好感度里程碑一一对齐，好感度决定**解锁到第几张**：

| 档 | 好感度 | 来源 | 性格基调 |
|---|---|---|---|
| 1 | 0 | `chue114514/dsh-whale-girl-persona` | 俏皮数码风，极简 |
| 2 | 10 | `wxzjss/dsh-whale-loli` v2-live | 活人感、说话铁律 |
| 3 | 50 | `Kaalia0912/dsh-whale-musume-persona` | 傲娇嘴硬心软 + 白米饭梗 |
| 4 | 100 | `wxzjss/dsh-whale-loli` 原版 | 傲娇甜心 + 颜文字库 |
| 5 | 200 | `loonai321/dsh-humanized-deepseek-maid` | 拟人化女仆鲸鱼娘 |
| 6 | 350 | `whyever77/dafeiyu-astrbot` v2.1 | 黏人撒娇、想念、小委屈 |

> 2026-11-18 用户调整了第 2 / 第 5 档的顺序（「拟人女仆和活人小鱼换个位置就好了」），
> 门槛跟着一起换：活人小鱼 200→10，拟人女仆 10→200。

- **正文直接用这些仓库写好的**，不自己重写。来源与许可见
  `artwork/persona_sources/MANIFEST.md`。
- 跨档时**强制切回**新的内置卡（好感度是唯一主线）。
- **用户可以手动切回任何一张已解锁的内置卡**
  （2026-11-18 用户决定：「好感度是解锁新的人格卡，但是也可以切换回之前解锁过的」）。
  未解锁的卡显示锁图标，不可点。
  实现走 `PersonaCardStore.selectBuiltinCard(id, affinity)`，
  只改底稿，不动覆盖层。

**用户改动**：

- 只能改**当前生效**的那一张，非当前不可改。
- 改动存成**覆盖层**（只存改过的字段），生效人格 = 当前底稿 + 覆盖层。
- 覆盖层跟着走 —— 切到下一档内置卡时继续叠在新底稿上。
- 「另存为自定义卡」快照进**自定义包**，与内置包互不影响。
- 用户可主动切到自定义卡，直到下次跨档被强制切回。

- 保留用户自己起的名字和昵称，不要冲掉。
- **记忆跨人格卡保持一致** —— 换卡只换语气，不换脑子。

### 1.2.1 食物：只有大白饭

- 食物**只有大白饭一种**，作用是饱腹，**同时涨一点好感度（+1）**。
- 专注 30 分钟兑换 1 份，进背包库存；库存上限 5 份。
- 「饿」的判定**只看距上次喂食是否超过 24 小时**，与饱腹度数值无关。
  判据是 `fish_state.lastFedAt`（0 = 从没喂过 = 饿）。
- 喂食后「开心一点」，好感度 **+1**（见 §1.1）。
- **没有心情（mood）这个数值维度**，不要新增。
  情绪由好感度档位推导；`FishMoodResolver` 里没有 GLUM 档。
  > 注意区分：「心情**数值**」不存在，但大肥鱼**自选的状态词**存在 ——
  > 那是 `MoodStore` 里的一个字符串，由模型通过 `set_mood` 工具产出，
  > 显示在首页状态胶囊上，不是可正可负的数值。
- 食物目录表 `food_catalog` 只剩 `foodId` / `displayName` / `iconAsset`
  —— 没有 `rarity` / `affinityBonus` / `dropRate` 了，单一食物用不上。

### 1.3 装扮：两套固定形象 + 一个开关（2026-11-22 复活）

> 2026-11-17 曾整块下线，2026-11-22 复活为「两套固定形象」。

- **只有常服 / 冬装两套**。没有第三套、没有解锁、没有季节自动切。
- 存在 `data/FishOutfitStore.kt`（`SharedPreferences deep_focus_outfit`）——
  **不进 Room**（`FishOutfits` 是代码常量，只有当前选择要存）。
- 入口：bootstrap 选鱼页两张卡 + 设置页随时切换。
- **作用范围**：聊天头像 / 新对话页大图 / 生图参考图 ✅ 换；
  **首页大肥鱼的 GIF ❌ 不换**（动作库只有常服一套，冬装没有配套动画）。
  实现上首页写死 `FishCanvas(outfit = FishOutfits.NORMAL)`。
- 牵引行为倾向：冬装爱去冷的地方，常服爱去暖的地方。
- 资源：`assets/fish/bigfatfish.png`（常服）/ `winter_cutout.png`（冬装界面立绘）
  / `winter.jpg`（冬装生图参考图）。
- 动作库里的 `zhengti-huanzhuang-shise`（整体换装着色）作为普通动作保留，
  但不承担换装流程。

### 1.4 素材命名

- 静态图：`assets/fish/bigfatfish.png`（**只有这一张**，不要叫 `cats/`，那是 FocusCat 遗留）
- 桌宠动作：`assets/pet/anim/all/{序号:03d}_{id}.webp` + `{序号:03d}_{id}_still.webp`
  - 109 个动作全量，序号与 `pet/PetAction.kt` 的 id 一一对应。
  - **源是上游 `assets/webm/*.webm`（640×360 VP9-alpha 母版）**，
    不是 `preview/*.gif`（220×124，太糊）。见 §3.4.1。
- 大肥鱼的登场 / 喂食动画**不单独做素材**，直接用桌宠动作库
  （`assets/pet/anim/all/`）里的对应动作 —— 首页与桌宠是同一只鱼，形象必须一致。
  - 登场：从 ENTER / TRICK / MOOD / PLAY 标签的动作里随机挑
  - 喂食：从 FEED 标签的动作里挑（17 个吃的动作）
- 表情包：`assets/stickers/st_{情绪}_{01..04}.webp`（47 张带文字梗）+ `st_{情绪}_big.webp`（11 张无文字大立绘，原 `expressions/`）
- 专注页视频：`assets/videos/focus/bigfatfish.mp4`（**唯一剩下的视频**）
- 资源路径统一走 `FishAnimationCatalog`，不要在 UI 里拼字符串。

### 1.4.1 随机播放用发牌堆，不放回（2026-11-18）

用户反馈：「我看还是经常能看到重复的 GIF，
可以确保随机不放回的展示一轮后再重新开始吗」

**只记住最近 N 个是不够的** —— 短剧播完一次要好几秒，
用户盯着看的时间跨度长，实际感受就是「怎么又是这个」。

**做法**：把全部候选洗牌成一叠，从上往下取，取完一整轮才重新洗牌。
实现是 `pet/ShuffleBag.kt`（纯 Kotlin，不碰 Android，可直接跑 JVM 单测）：

| 用途 | 堆 | 候选 |
|---|---|---|
| 首页登场短剧 | `appearanceBag` | 能上台的动作（`StageCatalog.STAGE_ACTIONS`） |
| 喂食动画 | `feedBag` | FEED **且**能上台的动作 |

**两个坑**：

- **喂食只从能上台的动作里挑**。
  `dakou-chi-lingshi` 归一化后宽 897px，超过 768px 上限，
  上了台会显得小一号 —— 破坏「舞台角色高度一致」这条视觉纪律。
  （隔离判据是**归一化宽度**，不是「短边小」。）
- **喂食动画期间再点会被挡掉**（`FishViewModel.isFeeding`）——
  这是防双击的设计，不是 bug；真机测试要等动画播完再喂下一次。

### 1.5 从 FocusCat 迁移过来的代码要改

- 这个 App 大量文件来自 FocusCat，**沿用猫咪语义的地方都要改成鱼**：
  `cats/` 目录、踩奶/伸懒腰之类的动作名、皮肤解锁逻辑、猫咪文案。
- 改的时候顺手删掉真的用不上的部分，不要留 `_unused` 兼容壳。

### 1.6 大肥鱼是主动 Agent（2026-11-18）

大肥鱼不只被动 answering。参照 dsh proactive agent 的心跳设计，
它**定时随机醒来**，自己决定要不要说话。

**两条唤醒路径，同一个入口**：

| 路径 | 载体 | 作用 |
|---|---|---|
| 进程内调度器 | `ProactiveScheduler`（App 活着时） | 频率高，随机掷骰 |
| 周期任务 | `ProactiveWorker`（WorkManager，30 分钟） | App 不在也能醒 |

两者都调 `ProactiveScheduler.tick()`，**逻辑不分叉**。

**掷骰在本地**：先本地判定要不要开口，命中了才调模型 —— 省电省额度。

**工具箱**（`ai/FishTool.kt`）：大肥鱼能自己调的工具：

| 工具 | 作用 |
|---|---|
| `today_focus` | 今天专注了多久 |
| `app_usage` | 今天各 App 用了多久（帮用户复盘手机使用） |
| `meal_status` | 今天吃了多少饭 / 多久没吃（太久没吃会「饿饿」） |
| `set_mood` | **自己给自己定状态词**，显示在首页胶囊上 |
| `todo_status` | 今天的待办还剩什么没做 |

**实现约束**：

- 聊天路径也要走 agent（`FishAgent.runStreaming`），不然她只能被动接收上下文，
  问「我今天用手机多久」只能干瞪眼。
- **OpenAI 兼容协议的工具回传有两个硬要求**（`ai/LlmClient.kt` 的 `LlmMessage`）：
  1. assistant 消息必须**原样带上 `tool_calls` 数组**（含 id / name / arguments）；
  2. 每条 tool 消息必须带 `tool_call_id`。
  不带就 400：`invalid params, tool result's tool id() not found (2013)`。
  光用「角色 + 文本」表达不了这件事，所以专门建了 `LlmMessage` 类型。
- 流式和工具要一起用：模型先要工具的那轮不吐字，
  拿到工具结果后的最终回复才流式吐给 UI（`chatStreamWithTools`）。
- **语言约束要写进 prompt**：很多模型默认「跟着用户的语言走」，
  用户用英文问，她就会整段英文回（还会夹带英文歌词）。
  `PersonaPromptBuilder.LANGUAGE_RULE` 无条件要求中文。

**通知**（`ai/FishNotifier.kt`）：

- 渠道 `deep_focus_proactive`，重要性 DEFAULT。
- 需要 `POST_NOTIFICATIONS` 权限（Android 13+ 运行时申请）。
- 主动消息同时落在**聊天记录**和**系统通知栏** ——
  用户不在 App 里也能收到，这是「主动」的意义所在。
- 待办催办一天只催一次（`wasTodoNagSentToday`），
  不然 WorkManager 每 15 分钟醒一次就重复催。
- ⚠️ **只有通知真的发出去了，才记「今天催过」**。
  `FishNotifier.notifyProactive()` 返回 `Boolean`（发出去 = true），
  `ProactiveWorker.nagTodoIfNeeded()` 只在 true 时才调 `markTodoNagSentToday()`。

  踩过的坑：之前是无条件记账的。用户还没给通知权限时，
  第一次唤醒把「今天催过」写下了，**但通知被静默跳过了** ——
  之后一整天都不会再催，用户永远收不到待办提醒。
  实测症状：`todo_nag_day` 已经是今天，但通知栏里一条 proactive 都没有。
  修法就是「没发出去就不记账」，等用户给了权限，下次唤醒还能催。

  **验证脚本**：`python tools/db/check_proactive_notification.py`
  （按 channel id 过滤，不能只看标题 —— 前台服务的标题就叫「专注大肥鱼运行中」，
  会被误命中）。

- ⚠️ **催办文案必须是她自己生成的**（2026-11-19 用户明确要求）。
  `ProactiveWorker` 只负责触发，正文走 `ProactiveScheduler.forceTodoNag()` →
  `FishBrain.runProactive(TODO_NAG)`；生成失败就不发、不记账，等下次唤醒。
  **禁止**在代码里留「你今天还有『X』没做完呢，别拖了～」这类硬编码模板 ——
  用户一眼就能看出不是她说的。
  单元测试 `ProductModelInvariantTest` 会断言 `ProactiveWorker.kt` 里没有模板字样。

**待办**（`ui/todo/`）：

- 底部导航新增「待办」tab。
- 数据在 `todos` 表，按本地时区日期 key 分天。
- 大肥鱼能查（`todo_status` 工具），唤醒时提一嘴。

---

## 二、目录结构

```
DeepFocus/
  app/                    Android 源码（唯一进 apk 的东西）
    assets/
      fish/anim/           大肥鱼动画（appear/eating/outfit，GIF 转的 animated WebP）
      pet/anim/all/        桌宠动作素材（**只放 72 个进 APK 的动作**，见 §3.4.2）
      pet/                 桌宠静态贴图
      stickers/            表情包（58 张：47 张带文字梗 + 11 张无文字大立绘）
      personas/            内置人格卡包（builtin_cards.json，6 张）
      foods/               食物图（只剩大白饭）
      fish/                大肥鱼静态图（只有 bigfatfish.png 一张）
  artwork/                素材源文件，不进 apk
    originals/            用户提供的原始角色图
    refs/                 专注页视频参考图（2 张）
      whale_girl/         社区鲸鱼娘三视图 + 固定 seed 工作流（i2i 基准）
    persona_sources/      dsh 人格卡 / proactive 插件素材源 + MANIFEST.md
    stickers/             表情包成品
    videos/focus/         专注页视频出片放这里
    pet_webm/             dsh-pet webm 母版（114 个，640×360）← 高清源
    pet_gif/              dsh-pet GIF 源（109 个，220×124）← 只作对照
    pet_sheet/            选片用的动作缩略图墙
    _archive/             隔离区：三视图卡等不再用的素材
  tools/media/            素材流水线
    _fetch_webm_zip.py    拉 dsh-pet webm 母版
    build_hd_anim_map.py  建 webm<->动作 映射（含 alpha IoU 验证）
    install_hd_actions.py 109 个动作 → 640×360 animated WebP + 静态兜底
    fetch_dsh_pet.py      拉 dsh-pet GIF（旧链路，只作对照）
    install_all_actions.py 旧链路：GIF → 260px animated WebP（已弃用）
    collect_dsh_persona_cards.py  拉 dsh 人格卡 / proactive 插件素材
    collect_whale_girl_refs.py    拉社区三视图 + 固定 seed 工作流
    install_videos.py     专注页视频：压缩 + 叠环境音 + 安装
    export_focus_prompt.py 导出 docs/专注页视频Prompt.md
    check_stickers.py     校验表情包清单与资产一致
    check_persona_cards.py 校验内置人格卡包字段与里程碑对齐
    merge_expressions_into_stickers.py  大表情 expressions/ → stickers/
    test_sticker_marker.py 验证标记解析容错
    make_*.py             生成图墙/抽帧（辅助看图）
    strip_white_bg.py     四边洪水填充去白底
    make_launcher_icon.py  出各密度 mipmap（自适应图标 + 单色层）
    preview_launcher_icon.py 本地预览系统 mask 裁切效果
  tools/db/                数据库与模型校验
    start_emulator.py            启动模拟器（脱离父进程 + 等就绪）
    check_migration_chain.py     Room 迁移链实测（schema json 当基线，5 场景）
    check_persona_state_machine.py  人格卡状态机实测（模拟器 + 设备端 sqlite3）
    check_food_and_action_model.py  食物模型 / mood / FEED 标签不变量校验
    check_food_model_on_device.py    食物模型真机实测（喂食 / 24h 饥饿判定）
    check_pet_threading.py           桌宠线程纪律静态检查（渲染循环 / updateViewLayout / onMain）
    check_proactive_notification.py  主动消息通知实测（强制 WM worker + 按 channel 过滤）
    dump_ui.py                      dump 设备端 UI 层级，打印带文字节点及 bounds
    query_db.py                     设备端 sqlite3 查询（单引号转义的坑都封装在这）
  tools/_archive/         隔离区：历史脚本、已取消的方案
    video_pipeline/       9 段视频剧本 + Prompt 包（已取消，勿复活）
    oneoff_2026_*         一次性 QA / 校验脚本（tag 覆盖、key 扫描等）
  docs/                   设计文档与 Prompt 包
  CONTEXT.md              领域词汇表（术语以它为准）
  .trae/rules/            本规则
```

- 中间产物、探测脚本、被否掉的方案，一律进 `tools/_archive/` 或 `artwork/_archive/`，
  **不要散在仓库根目录**。
- `artwork/` 和 `tools/` 都不进 apk。
- **视频流水线已归档**（`tools/_archive/video_pipeline/`）。
  不要复活 `scene_plan.py` / `export_prompt_pack.py` —— 那条路已经否掉了。

---

## 三、图像与视频生成（强制）

### 3.1 只用 TRAE 内置生图，不调用任何外部 API（2026-11-19 定稿）

- **唯一允许**：TRAE IDE 内置的 `GenerateImage` 工具（文生图）。
  参数只有三个：`prompt` / `path` / `image_size`。
- **禁止**：任何外部生图 API —— 火山方舟 / 豆包控制台 / MiniMax `image-01` /
  matrix connector 一律不用。用户原话：「我只使用 TRAE 内置生图」。
- **TRAE 内置没有 image2image**：
  `GenerateImage` 没有参考图入参，插件目录也没有 i2i 能力。
  不要再去找「图生图端点」，也不要再写探测脚本
  （2026-11-19 已删除全部 14 个探针脚本 + 2 个探针目录）。
- **角色一致性只能靠 prompt 锁特征**。两个要点：
  - **写比例，不写形容词**。只写「deep navy blue」出来是淡蓝色；
    改成「上三分之二是深藏青 `#2b3a6b`，只有发梢渐变成浅冰蓝」才压住。
  - **角色一致性靠特征清单，不靠描述长相**。
    大肥鱼的特征清单：呆毛 / 白荷叶边发箍 / 侧边浅蓝小蝴蝶结 /
    鲸鱼鳍耳 / 蓝宝胸针 / 深蓝女仆装 + 金星刺绣 /
    白荷叶边围裙 + 绣小鲸鱼 / 深蓝鱼尾。
- 需要「必须吃参考图」才能保证一致性的素材 → **不做，或改用现成素材**。
  生成一张漂移的角色图比没有更糟（污染资源库）。

> 历史记录：2026-11-18 曾用 MiniMax `image-01` 的 `subject_reference`
> 跑通过真 i2i（产出 `artwork/icon_src/dafeiyu_icon_i2i.png`）。
> 2026-11-19 用户明确只允许 TRAE 内置 → 该通路与相关脚本**全部下线**，
> 图标改为 t2i + 特征锁定（见 §0.4）。

### 3.1.1 补资源前先做语义核对，别急着生图

**这一条能省掉大量无效生成。**

拿到「代码要什么」和「磁盘有什么」两张清单后，先做**语义比对**：
如果已有资源换个名字就能对上，**改名，不要重画**。

实测例子（2026-11-15）：
- `SeedData` 里 foodId 是「大白饭 / 冻干 / 酸奶 / 罐头」，
  磁盘上是「大白饭.png / 小鱼干.png / 海苔汤.png / 鲔鱼罐头.png」。
- 语义核对后：「冻干」= 冻干鱼干 ≈ 已有的小鱼干；「罐头」= 已有鲔鱼罐头。
  这两个**复制改名即可**，不用生成。
- 真正缺的只有「酸奶」——已有的 4 张里没有一杯酸奶。

重画已有资源会破坏已经统一的画风（实测：同一批食物图，
重新生成的版本跟原有版本风格明显不一致）。

> **2026-11-15 更新**：食物已缩减为**只有大白饭**，
> 上面的冻干 / 酸奶 / 罐头全部作废，相关图与种子行都要删干净。

### 3.1.2 食物图的风格基线：柔和 2D 手绘

`app/src/main/assets/foods/` 里的实测风格是：

- 细**棕**色描边（不是纯黑）
- 柔和渐变填充 + 少量高光
- 纯白底，物体居中
- 容器一律 **45° 俯视**（碗 / 盘 / 杯沿都能看到内壁）
- **不画脸**（不拟人化）

生成新食物图时 prompt 必须写清这五条，否则风格必然漂。
参考写法见 `tools/media/gen_missing_assets.py` 的 `FOOD_BASE`。

> 食物只剩大白饭一种，所以只需要保留 `foods/大白饭.png`，
> 其余（小鱼干 / 海苔汤 / 鲔鱼罐头 / 冻干 / 酸奶）全部删掉。

### 3.1.3 白底图要转透明

用 `tools/media/strip_white_bg.py` 转透明。**必须用四边洪水填充，
不能用全局阈值**：大肥鱼身上有大量纯白（围裙、荷叶边、鲸鱼帽毛边），
全局阈值会把这些一起啃掉，围裙就破了。
洪水填充只动「从画布边缘连通的近白像素」，角色内部的白色被描边包着，天然保住。

> **2026-11-17**：`bigfatfish_winter.png`（冬装）已删除 —— 装扮系统整块下线。
> `assets/fish/` 只留 `bigfatfish.png` 一张。**不要用文生图重建角色。**

### 3.2 角色一致性靠参考图，不靠文字描述

**这是硬规则。** 用文字描述重建角色必然每次漂移 —— 这条路已经验证过，不可靠。

- 参考图（`artwork/refs/`）就是唯一形象来源。
- prompt 只写**动作、情节、镜头、氛围**，一个字都不描述角色长相。
- **画风也归参考图管**（2026-11-22 定稿）：参考图是什么画风，出来就是什么画风，
  prompt **不写死风格**。
  之前生图 prompt 锁死「日系二次元插画 + 禁止写实」，模型就在参考图之外
  重新挑了一个画风，出来的人物跟参考图对不上 —— 半拼半凑就是恐怖谷。
  prompt 只留一条约束：**整张图不要混搭**（人物是 A 画风、背景却是 B 画风 → 恐怖谷）。
  场景 / 故事 / 镜头 / 光线全部放开。
- **图生图（i2i）优先，文生图（t2i）只做最后手段。**
  一致性低的图**不要生成** —— 浪费配额还污染资源库。

**社区三视图是现成的一致性基准**（2026-11-15 收集）：

| 文件 | 用途 |
|---|---|
| `artwork/refs/whale_girl/site__assets__dafeiyu-chibi-character-sheet.png` | **Q 版三视图**（正/侧/背 + 局部特写）—— i2i 首选输入 |
| `artwork/refs/whale_girl/site__assets__dafeiyu-mature-character-sheet.png` | 成熟版三视图 |
| `artwork/refs/whale_girl/site__assets__workflows__comfyui-issue-2__*.json` | 6 份**固定 seed** 工作流 |

来源：`TreapGoGo/deepseek-whale-girl`（★818, MIT），社区共同维护的鲸鱼娘资源库。
收集脚本：`tools/media/collect_whale_girl_refs.py`。

- **固定 seed + 固定工作流 = 同一角色**，这是可复现生成的前提。
- 已核对：`assets/fish/bigfatfish.png` 与社区三视图是**同一只鱼**
  （深海蓝渐变长发、呆毛、鲸鱼鳍耳、深蓝女仆裙 + 白荷叶边围裙 + 小鲸鱼刺绣、蓝宝胸针）。
  冬装已删（装扮系统整块下线），不再需要基准图。

> 三视图卡和 Claude/ChatGPT/千问/Kimi 四张来客卡
> 随视频制作环节取消，已移到 `artwork/_archive/refs_video_only/`。
> 桌宠和 App 内的大肥鱼形象**直接来自 dsh-pet 的 webm 母版**，不需要三视图卡。

### 3.3 视频制作环节已取消，只剩专注页一段

**决策（2026-11-13）**：原来的 9 段视频（登场 / AI 串门 / 换装 / 专注页）全部取消制作。

原因：
- 视频要等模型出片，成本高、体积大（15 秒视频几十 MB）
- dsh-pet 有一百多个现成的透明 GIF 动作，**登场、喂食、换装用 GIF 完全够**
- GIF/animated WebP 体积约为同动作视频的 1/20

**现在只有专注页还用视频**，因为它要 9:16 全屏铺满 + 环境音，
GIF 撑不起这种沉浸感，全屏放大会把小图拉糊。

| 原来的位 | 现在用 |
|---|---|
| `videos/appear/*.mp4` 登场短剧 | 动作库 ENTER / TRICK / MOOD / PLAY 标签随机挑 |
| `videos/eating/*.mp4` 喂食动画 | 动作库 `chi-baifan` / `chi-wancan`（只喂大白饭） |
| （换装） | 已删除 —— 装扮系统下线 |
| `videos/focus/*.mp4` 专注页 | **仍是视频**，唯一保留 |

- 动画清单在 `pet/PetAction.kt`（109 条动作），`data/FishAnimationCatalog.kt` 只剩专注页视频路径。
- 播放统一走 `ui/components/FishAnimation.kt`（Compose 播 animated WebP）。
- **每个动画都要配 `_still.webp` 静态兜底** —— API 26/27 没有 `ImageDecoder`，
  解不了动画，只能显示静态图。

### 3.4 桌宠动画：来自 dsh-pet 的 webm 母版，转码成 animated WebP

- 桌宠的会动形象**不再自己生成**，直接用 DeepSeek 生态的
  [dsh-pet](https://github.com/PC2005-cloud/dsh-pet) 的大肥鱼素材 ——
  **109 个透明动作**，源目录 `dsh-pet/assets/webm/*.webm`
  （640×360 VP9-alpha 母版）。

### 3.4.1 高清源：必须用 webm，不要用 preview GIF

**踩过的坑（2026-11-17）**：
`dsh-pet/assets/preview/*.gif` 只有 **220×124**，被桌宠窗口一放大就糊。
用户反馈「播放的 GIF 分辨率太低，为什么会这么糊？是不是下载的时候出了问题？」——
**不是下载问题，是源文件本身就小**。同仓库里还藏着
`dsh-pet/assets/webm/*.webm`（114 个，640×360 VP9-alpha），
像素量 8.4 倍，那才是母版。

| 源 | 分辨率 | 数量 | 用途 |
|---|---|---|---|
| `assets/preview/*.gif` | 220×124 | 109 | **只作对照**，不再进 apk |
| `assets/webm/*.webm` | 640×360 | 114 | ✅ **唯一高清源** |

**映射方案**（`tools/media/build_hd_anim_map.py`）：
webm 用中文名（`三球抛接.webm`），GIF 用拼音 id（`sanqiu-paojie.gif`）。
用 `pypinyin` 把中文名转拼音再对撞，最后用 **alpha 轮廓 IoU** 逐对验证
是同一段动画（实测 110/114 命中，109 个动作全覆盖，IoU 中位数 0.978）。
映射表落在 `tools/media/webm_action_map.json`，**改动作库要重新生成**。

**编码参数**（`tools/media/install_hd_actions.py`）：
- 分辨率 **640**（母版原生，1:1 不缩不放 —— 再小就又被放大了）
- 帧率 **12fps**（母版 24fps 的一半，桌宠小窗口下已经顺）
- 质量 **q75** / **256 色**（渐变和边缘不出色带）
- ffmpeg 要显式 `-vcodec libvpx-vp9` 才解得出 VP9-alpha
- 体积：109 个动画约 145MB。**用户明确表示体积不是问题，清晰度优先。**

> 旧链路 `fetch_dsh_pet.py` + `install_all_actions.py`（GIF → 260px）已弃用，
> 但保留着做对照和回退。
- **许可**：代码 MIT；**素材禁止商用**，二创在公开渠道须署名。
  本项目非商业用途可用，但**必须保留署名**。
- **全量 109 个动作都要用上**，不要只挑一部分 ——
  这批动作里藏着完整玩法的原料：雪人、风筝、孔明灯、烟花、陀螺、
  扑克、画笔、红包、火锅、贝壳、气球…… 每个动作自带道具和场景元素。

流程：

```
python tools/media/_fetch_webm_zip.py         # 拉 webm 母版到 artwork/pet_webm/
python tools/media/build_hd_anim_map.py       # 建 webm<->动作 映射（含 IoU 验证）
python tools/media/install_hd_actions.py      # 全部转 animated WebP（109 个）
python tools/media/check_action_library.py    # 校验动作库与资源一致
```

- **动作库在 `pet/PetAction.kt`**，109 条，每条有 id、序号、描述、玩法标签。
- **序号即文件名**：`assets/pet/anim/all/{序号:03d}_{id}.webp`。
  序号错了整个动作播不出来，所以有 `check_action_library.py` 守着。
- **静态兜底图 `{序号}_{id}_still.webp`** 必须齐 —— API 26/27 没有
  `ImageDecoder`，解不了动画只能显示静态图。
- **旧的两套素材已合并**：`pet/{pose}.webp` 和 `pet/anim/{pose}.webp` 全删。
  `PetSpriteLibrary.Pose` 现在直接指向动作库 id，不再有重复文件。
- **分辨率不省**：母版 640×360 原生尺寸。桌宠窗口 160dp 在 420dpi 屏上约 420px，
  220px 的源放大约 2 倍必糊 —— 这是 2026-11-17 用户明确指出的问题。
- **体积不设上限**：用户说「体积大点都还好，GIF 必须要高清原图」。

### 3.4.2 近景素材：裁空白 + 按角色高度归一（2026-11-17）

**问题**：母版 640×360 里角色只占画布宽约 40%（bbox 中位 254×278），
直接显示角色永远只占屏幕三分之一；而运行时 `scale(1.4f)` 放大
（容器 960px / 素材 640px = 1.5×，再叠 1.4× = 等效 2.1 倍双线性插值）
会让画面**发糊**，`clipToBounds()` 又把放大的部分**裁掉** ——
用户反馈「GIF 变糊了」「显示不全」。

**解法：放大离线做，运行时只做 1:1。**
`tools/media/build_hero_assets.py` 从 webm 母版重新转码：

| 步骤 | 做法 |
|---|---|
| 裁空白 | 按全帧 alpha 并集 bbox 裁掉透明边（+4px 余量） |
| 归一化 | **按角色高度**统一到 512px，宽度按比例 |
| 锐化 | `unsharp=5:5:0.9` 补回放大损失的边缘 |
| 编码 | 8fps / q70 / 256 色，`libwebp_anim` |

**为什么按「高度」而不是「短边」归一**（踩过的坑）：
角色的感知大小由**高度**决定。按短边归一时，
站立动作角色高 640、躺姿动作角色高 337 —— 屏幕上差近 2 倍，
正是用户说的「有些 GIF 大有些 GIF 小」。
按高度归一后，所有动作的角色高度完全一致。

**运行时必须 1:1**：
- 容器高度 = `512 / density` dp（`HERO_STAGE_PX` 常量 + `LocalDensity` 反算），
  各机型物理像素都正好 512，零插值。
- **ImageDecoder 默认按屏幕密度放大解码**（512px 素材在 3x 屏上解成 1536px，
  再被 ImageView 缩回容器，细节全丢）——
  必须显式 `setTargetSampleSize(1)` + `setTargetSize(原始宽高)`。
- `FishAnimation` 用 `adjustViewBounds=true` + `wrapContentWidth`，
  宽度按素材比例自适应，高度锁死 —— 这样各动作角色高度一致。

**隔离上台白名单**（`tools/media/gen_stage_catalog.py` → `pet/StageCatalog.kt`）：
按高度归一后，横向摊得太开的动作宽度会超过 768px（放不进 360dp 宽的屏）。
这类动作已隔离，**不参与首页轮播**。
桌宠的全量 109 个动作不受影响。

**白名单的标签来源是 `STAGE_TAGS`**：

```python
STAGE_TAGS = {"ENTER", "TRICK", "MOOD", "PLAY", "FEED", "FESTIVE"}
```

> ⚠️ **2026-11-18 踩过的坑**：之前只有 `{ENTER, TRICK, MOOD, PLAY}`，
> 结果 **16 个吃的动作全部进不了白名单**（FEED 只留 2 个，FESTIVE 一个没进）——
> 首页轮播永远看不到吃饭表情，喂食也只有 2 个动作可选。
> 加了 FEED / FESTIVE 后白名单从 44 涨到 **66**。
> **改动作标签后一定要重跑 `gen_stage_catalog.py`**，否则新标签的动作进不了轮播。

**流程**：

```
python tools/media/probe_anim_bbox.py        # 探测 alpha bbox
python tools/media/build_hero_assets.py      # 全量转近景素材
python tools/media/gen_stage_catalog.py      # 生成舞台白名单
python tools/media/verify_stage_sizes.py     # 校验白名单与尺寸
```

**备份**：原 16:9 素材备份在 `artwork/_anim_backup_16x9/`。

**「每次喂都是同一个表情包」的真正根因**（2026-11-18 定位）：
`ui/components/FishImage.kt` 里 `val eatScene = remember { playEngine.onFeed() }` ——
`remember` **一次性抽取，永不改变**。
`ui/fish/FishScreen.kt` 里当时也有一份同样写法的 `eatScene`，
两个都改成 `LaunchedEffect(state)` 才真正做到「每次喂重抽」。
**以后写「每次触发都要重新抽」的逻辑，一律用 `LaunchedEffect(key)`，不要用 `remember`。**

首页轮播同理用**发牌堆**（`PetPlayEngine.nextAppearance`）：
一轮 66 个不重复，取完才重新洗牌 —— 解决用户反馈的「老是看到重复的 GIF」。

**「一共有多少个 GIF」的准确答案**（2026-11-19 更新）：

| 数字 | 含义 |
|---|---|
| **109** | 动作库定义总数（`PetActionLibrary.ALL`） |
| **66** | **首页轮播实际能抽到的**（`StageCatalog.STAGE_ACTIONS` 白名单） |
| **43** | 被隔离的（归一化宽度 > 768px，横向摊太开，上台会显得小一号） |
| **6** | 隔离但**仍留在 APK**里补玩法（`PET` / `GIFT` / `THINK` 三个标签删干净就没候选了） |
| **72** | **进 APK 的动作总数**（`StageCatalog.SHIPPED_ACTIONS`），144 个文件（动画 + `_still`） |
| **16** | 吃东西的动作里**能上台**的（FEED 标签共 17 个，`dakou-chi-lingshi` 宽 897px 被隔离） |
| **133 MB** | `assets/pet/anim` 总体积 |

**隔离动作的素材已从 APK 删除**（2026-11-19 用户要求：「隔离的不需要放进 apk 了，
利用效率不高」，省 ~69 MB）。桌宠按标签抽动作时**必须**用
`id in StageCatalog.SHIPPED_ACTIONS` 过滤 —— 否则抽到缺失素材会静默播不出来。
固定 id 取动作也要走 `PetPlayEngine` 的 `shippedById()`（`byId` 对缺失素材仍返回非空，
走不到 `?:` 兜底）。
校验脚本：`python tools/media/verify_stage_sizes.py`
（报的是**归一化宽度**，不是短边 —— 短边不是真隔离判据）。


### 3.5 互动玩法：动作按「玩家做了什么」组织

**关键原则**：动作要有来由，不能随机乱播 —— 随机的桌宠看起来「没在理你」。

玩法清单（`pet/PetPlayEngine.kt`）：

| 玩法 | 触发 | 用到的动作 |
|---|---|---|
| 投喂 | 双击桌宠 / 首页喂饭 | **所有「在吃东西」的动作**（FEED 标签，17 个） |
| 摸头 | 单击桌宠 | 撸毛、伸懒腰、抖毛、呆空如隙 |
| 逗猫棒 | 菜单「逗它玩」 | 抛接球、陀螺、水枪、小提琴… |
| 甩飞 | 用力拖动后松手 | 被拎悬空 → 重力压扁 → 弹起跑掉 |
| 送礼 | 好感度里程碑 | 收红包、拆礼物、变魔术 |
| 思考 | AI 开始思考 | 思考冒泡、深度思考 |
| 工作 | 专注中 | 写代码、归档、忙个不停 |
| 陪伴 | 5 分钟没互动 | 看书、发呆、碎碎念、哼歌 |
| 节日 | 特定日期 | 堆雪人、端午粽子、中秋月饼、圣诞树 |
| 恶作剧 | 低频随机（3% + 30 分钟冷却） | 放烟花、孔明灯、河灯、魔术、凭空生花 |

- **标签驱动**，不按姿态硬编码。新增素材只要打上标签，玩法自动覆盖。
  标签见 `PetActionTag`：feed / pet / play / gift / idle / sleep /
  festive / mood / trick / work / think / thrown / enter / talk。
- **不重复**：最近播过的 6 个动作短期内不再出现。
- **恶作剧要低频** —— 惊喜不是噪音。3% 概率 + 30 分钟冷却。
- **节日要应景**：冬天堆雪人，端午粽子，中秋月饼，圣诞树。
- 一次性动作播完**自动回到当前姿态**，不改变桌宠常态。
- AI 说话时桌宠会**配合演一个动作**（`playActionForSpeech()`）——
  它撒娇，桌宠也害羞；它思考，桌宠跟着托腮冒泡。

#### 3.5.1 首页大肥鱼与桌宠共用一套选动作逻辑

**这是硬规则。** 用户感知到的是**同一只鱼**，两处行为不一致会立刻露馅。

- 选动作**统一走 `pet/PetPlayEngine`**，首页 `FishCanvas` 不许自己
  `filter { ... }.randomOrNull()`。
- `PetPlayEngine` 是 **Hilt 单例**（`@Singleton` + `PetPlayEngineEntryPoint`）。
  桌宠是 Service、首页是 Composable，两边都拿不到构造注入，
  用 EntryPoint 取同一个实例，记忆才是真共享的。
- **「最近播过的不重复」只有一份记忆**，首页与桌宠共享
  （首页刚播过摸头，桌宠不会马上再摸一次）。
- 首页触发源与桌宠不同，但**规则一致**：
  首页看的是「你现在在干嘛」，桌宠看的是「它在桌面上被你怎么着了」。
- **喂大白饭只播白饭类动作**。`PetActionTag.FEED` 现在**只有 2 个**：
  - `chi-baifan`（抱着一大碗白饭猛吃）
  - `chi-wancan`（端着饭碗认真吃）

  原本挂在 FEED 上的 19 个动作（粽子 / 火锅 / 西瓜 / 月饼 / 年糕…）
  已改成 `festive` / 玩梗标签 —— 食物只有大白饭，喂饭却播吃粽子是错的。
  `chi-token`（举着一枚硬币端详）本来就是误标，也一并改掉了。
- 登场从 ENTER / TRICK / MOOD / PLAY 里随机挑，播完换下一段。

### 3.6 桌宠要接上业务，不能只是个挂件

- 桌宠通过 `pet/PetEventBus.kt` 接事件，**不要**让服务直接依赖 ViewModel。
- 事件：`FocusStarted` / `FocusEnded` / `FocusMilestone` / `Say`。
- AI 台词：订阅 `ChatDao.observeLatestFishMessage()`，
  大肥鱼说的话会**自动出现在桌宠气泡上**，用户不用打开 App。
- 提醒专注：桌宠自己读 `focus_sessions` 表算今日时长，
  10 分钟冷却 + 20% 随机性，避免机械刷屏。
- 常驻链路已通：`PetBootReceiver`（开机自启）→ `PetBootActivity`（中转）
  → `DesktopPetService`（前台服务）。改启动逻辑别绕过 `PetBootActivity`。

### 3.6.1 桌宠常驻的权限成本与线程纪律（2026-11-18 实测）

**权限成本：四个，都是「一次性授权」型，没有拦路虎。**

| 权限 | 用途 | 拿不到会怎样 |
|---|---|---|
| `SYSTEM_ALERT_WINDOW` | 悬浮窗 | 桌宠根本起不来 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | 前台服务保活 | 服务起不来 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启 | 重启后要手动开一次 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 电池白名单 | 不加会被系统杀，看起来「它莫名消失了」 |

- **没有一个是「不给就完全不能用」的那种**（不是通知权限、不是位置）。
  拿不到悬浮窗权限，App 其余功能全部正常。
- **结论：保留桌宠。** 权限多但都是「设置页点一下」的量级，
  而且桌面常驻本来就是这个产品的差异点。

**线程纪律（踩过的坑）**：

桌宠有两种线程约束，症状完全不一样，别混：

| 约束 | 症状 | 怎么兜的 |
|---|---|---|
| `View.animate()` 只能在 Looper 线程跑 | 抛 `IllegalStateException: Animators may only be run on Looper threads`，桌宠起来了但 AI 台词气泡不出现 | `PetWindowController` 一层的 `onMain { }` |
| `WindowManager.updateViewLayout()` 只能在主线程跑 | 抛 `CalledFromWrongThreadException` | 渲染循环挂主线程 + 每处 try/catch |

- **兜底放在 `PetWindowController` 一层**（`onMain { }`）：
  `playAction` / `showBubble` / `hideBubble` / `setPose` / `setSleeping` /
  `moveTo` / `flipFacing` / `showSafely` / `hideSafely` 全部自动切主线程。
  调用方很杂（后台协程、用户点击、状态轮询），放这一层最省事。
- **渲染循环必须跑在主线程**（`DesktopPetService.renderScope` = `Dispatchers.Main.immediate`）——
  它每帧都要改 View 属性、调 `updateViewLayout()`。
  之前挂在 `serviceScope`（`Dispatchers.Default`）上，后果是**两个 bug 同时存在**：
  1. 贴边隐藏态那处 `updateViewLayout` 没 try/catch
     → 桌宠贴边静置 12 秒后**整个 App 崩掉**
  2. 每帧 `updatePosition()` 那处同样的调用被 `catch` 吞掉
     → **桌宠在屏幕上不动**（物理在跑，窗口不跟，看着像「桌宠坏了」）
- **状态轮询、解码那些活儿留在后台**（`serviceScope`）——它们不碰 View。
- `tick()` 自己再加一道主线程守卫（不在主线程就 post 过去），
  防止以后有人把渲染循环改回后台。
- `tools/db/check_pet_threading.py` 静态检查这几条纪律（改桌宠代码后跑）。

### 3.6.2 桌宠只在系统桌面显示（2026-11-19 定稿）

用户要求：
> 「打开了 app 就不显示桌宠，只在手机桌面时出现桌宠，也不干扰其它应用使用，
>  注意测试选择关闭桌宠后桌宠是否能取消显示。」

**规则**：

| 当前前台 | 桌宠 |
|---|---|
| 系统桌面（Launcher） | 显示 |
| 我们自己的 App | **不显示** |
| 其它任何应用 | **不显示** |
| 用户手动隐藏 / 设置里关掉桌宠 | **不显示** |

**实现**（`pet/HomeScreenGate.kt` + `DesktopPetService`）：

- 前台判定用 `UsageStatsManager.queryEvents()` 取最后一条 `ACTIVITY_RESUMED`，
  桌面包名用 `queryIntentActivities(ACTION_MAIN + CATEGORY_HOME)` 解析，
  **不写死 launcher 包名**。
- ⚠️ **必须带进程内缓存**（`cachedForeground`）：
  只查最近 15 秒的话，用户在一个页面静止超过 15 秒就一条 RESUMED 都没有，
  会被误判成「无权限」→ 退回「保守显示」→ 桌宠又爬出来（实测踩过）。
- **没有 `PACKAGE_USAGE_STATS` 权限时保守显示**（`null` → 显示）——
  否则用户明明开了桌宠却什么都看不见，比偶尔盖住应用更让人困惑。
- **窗口整块 add / remove，不要 `View.GONE`** ——
  GONE 的悬浮窗仍然占一层 overlay，那才叫「干扰其它应用」。
  位置和尺寸记在 `lastKnownPosition` / prefs 里，回桌面原地出现。
- 所有 WindowManager 调用走 `showSafely` / `hideSafely`（内部 `onMain`）。
- 设置页关掉桌宠 → 写 `enabled=false` + `stopService`；
  服务 `onCreate` 也读 `enabled`，为 false 时不挂窗口。

**验证**（模拟器实测通过）：桌面显示 → 进 App 消失（静止 20 秒也不复现）
→ 进系统设置消失 → 回桌面原地出现 → 设置里关开关后桌面不再出现。

### 3.7 AI 说话要像人

- **禁止客服腔**。「好的，我来帮你」「请问还有什么可以帮您」「为你服务」
  这类开头词和元信息在 prompt 里显式拉黑，输出侧再兜一层清洗。
- 事实用**口吻**说，不要照抄：「今天专注了 47 分钟」不是「用户今日专注时长为 47 分钟」。
- 主语用**称呼**（人格卡里的 `userAlias`），不要出现「用户」「客户」。
- 「今天专注了多久」由 `ai/FocusNarrative.kt` 统一生成，区分场景
  （正在专注 / 刚专注完 / 还没开始 / 专注很久），并带上段时长和段数。
- 记忆分时效（今天 / 最近几天 / 久远），久远的**不主动提**，
  且需要 ≥2 个词命中才召回。
- 记忆从对话里**自动提炼**，不要让用户手动填。

### 3.8 表情包：模型自己选，不是关键词猜

学自 dsh-pet 的表情包机制（`dsh-pet/assets/config.jsonc` 的 `memes` 段）。
上游把**三条机制**分开，本项目照搬：

| 机制 | 怎么做的 | 本项目在哪 |
|---|---|---|
| **对话配图** | 把「id + 内容描述」清单交给模型，模型按语境挑 | `StickerCatalog.catalogForPrompt()` → `FishBrain.chat()` |
| **碎碎念配图** | 同样把清单交给模型，她自己挑（**2026-11-22 改**：原来随机抽一张） | `FishBrain.runProactive()` |
| **动画联动** | 模型回一个动作标记，桌宠当场演出来 | 桌宠侧已实现（`PetEventBus`） |

**关键规则**：

- 清单在 `ai/StickerCatalog.kt`，**58 条**与 `assets/stickers/st_*.webp` 一一对应
  （47 张带文字梗 + 11 张无文字大立绘，原 `expressions/`）。
- **表情包与大表情已统一**：一个目录、一份清单、一条加载路径。
  动作库（`assets/pet/anim/all/`）是另一回事 —— 那是桌宠和首页大肥鱼的表演素材。
- 表情包 id 存在 `ChatMessageEntity.action` 字段（**不是** ProactiveAction 名）。
- 模型输出格式：`[表情:st_xxx]`，由 `StickerMarker.parse()` 剥离。
  标记是**给模型看的协议，不是给用户看的** —— 用户界面永远不该出现这串符号。
- **流式渲染时必须剥离标记**（`StickerMarker.stripMarkers()`），
  否则用户会看着 `[表情:st_cheer_01]` 被逐字打出来。
- 表情包在**回复结束后**才显示，不要在流式过程中冒出来。
- **模型只能从清单里选**。清单外的表情包它拿不到、也编不出来 ——
  这是「宁缺毋滥」在表情包上的体现。
- **没有随机兜底、没有关键词猜测**（2026-11-22 定稿）：
  用户反馈「感觉是随机固定发送的，agent 没有阅读这个表情包本身是什么内容，
  就随便发一个糊弄的感觉」。
  所以清单连着每张图的意思一起进 prompt，她读完自己挑；
  **挑不出来就只回文字，不发表情包**。
  `StickerMarker.resolve()` 只认模型选中的 id，选不中就不配图。
  碎碎念也走同一条路 —— 不再随机抽图硬配。

**改清单的流程**：

```
python tools/media/check_stickers.py       # 校验 id 与 assets 是否一一对应
python tools/media/make_sticker_sheet.py   # 生成图墙，用来校对描述
```

> `check_stickers.py` 必须过。它抓到过 `st_bored_02` 这个
> 「清单里有、文件不存在」的错 —— 模型选到就会静默不显示。
> 描述也要对着图墙校对，描述错了模型就会选错图。

### 3.9 宁缺毋滥

- 质量不达标的素材**不要**放进 `app/src/main/assets/`。
- 宁可留空让代码降级到静态图，也不要塞质量差的视频。
- 素材缺失必须优雅降级，不能崩溃、不能黑屏。

### 3.10 产出流程与规格

```
python tools/media/_fetch_webm_zip.py         # 拉 dsh-pet webm 母版（114 个，640×360）
python tools/media/build_hd_anim_map.py       # 建 webm<->动作 映射（含 alpha IoU 验证）
python tools/media/install_hd_actions.py      # 全部转 640×360 animated WebP（109 个动作）
python tools/media/check_action_library.py    # 校验动作库与资源一致
python tools/media/export_focus_prompt.py    # 导出 docs/专注页视频Prompt.md
python tools/media/install_videos.py        # 专注页视频：压缩 + 叠环境音 + 安装
python tools/media/check_stickers.py        # 校验表情包清单与资产一致
python tools/media/check_persona_cards.py   # 校验内置人格卡包与里程碑对齐
python tools/media/test_sticker_marker.py   # 验证标记解析容错
python tools/media/make_action_sheets.py    # 生成全量动作图墙（看图用）
python tools/media/make_sheet.py            # 生成表情包图墙（校对描述用）
```

- 专注页视频规格：**20 秒 / 1080P / 9:16**。
  画面重心在下方 1/3，上方留白给计时器；动作要小，循环起来不晕。
- 生图只走 TRAE 内置 `GenerateImage`（文生图），没有 i2i 通路 —— 见 §3.1。

## 四、代码约定

- 注释与文档用中文。
- Kotlin 里不写重复的工具函数；公用逻辑抽到 `util/Extensions.kt`。
- 素材是否存在用 `context.assetExists(path)` 判断，不要用 `available()`。

### 4.0 聊天会话与记忆连贯（2026-11-22 定稿）

用户需求：「可以开启新的对话，但大肥鱼记忆是连贯的，
开启新对话只是为了归档；注意避免上下文过载；
模仿 deepseek harness 的方式做好 agent 记忆，比如睡觉时总结上下文。」

**参考的开源方案**：
- [dsh-memory](https://github.com/haitang1/dsh-memory)（MIT）——
  每段 session 蒸馏一份 rollout summary，下一段注入；长期记忆另有一本账。
- [dsh-auto-memory](https://github.com/Aik358/dsh-auto-memory) ——
  四段式 handoff note（任务状态 / 目标 / 尝试过什么 / 进展与下一步）。
- [dsh-brain](https://github.com/LINYT1416/dsh-brain) —— 睡前巩固。

**本项目的三条机制**（实现在 `ai/ConversationManager.kt`）：

| 机制 | 触发 | 做什么 |
|---|---|---|
| **交接笔记** | 开新对话时 | 旧会话压成 ≤240 字摘要（四段式），存旧会话 `summary` |
| **记忆连贯** | 新会话第一轮 | 注入最近一个别的会话的 `summary`（`【上一段对话…】`）|
| **上下文压缩** | 当前会话 > 40 条 | 早期消息压进摘要，`compactedUpToId` 前移，窗口只留最近 20 条 |

外加 **睡觉时总结**：`ProactiveScheduler` 的睡眠分支先 `summarize()` 再睡。

**硬规则**：

- **开新对话 ≠ 她失忆**。旧对话只归档，不删；摘要会带到下一段。
- **删会话要连带删记忆**（`fish_memories.conversationId` 匹配）。
  不删的话，她会一边说「不记得了」一边又提起被删掉的事。
- **删旅行日记要连带删磁盘上的配图文件**（`filesDir/travel_images/`）。
- **上下文窗口有界**：只取当前会话、`compactedUpToId` 之后的原文。
  摘要已经装下的信息不再原文喂一遍。
- 摘要生成失败**不能影响开新对话**（`runCatching` 兜住）。
- **推理模型的 `max_tokens` 别给小了**：MiniMax-M3.1-Flash 会先烧 token 思考，
  1024 时 `content` 是空串（`finish_reason=length`）—— 要 4096。
  摘要那条路已经踩过这个坑（`SUMMARY_MAX_TOKENS = 4096`）。
- 新增表 `chat_conversations` + `chat_messages.conversationId` +
  `fish_memories.conversationId` + `travel_journals.photoIdea` 都在 **DB v18**。
  迁移后老消息要回填到 id=1 的会话，否则升级后历史记录会「消失」。

### 4.1 数据库迁移的四条纪律

1. **改实体必须同时改迁移链**。`@Entity` 加一个字段，就要在迁移里把列补上 ——
   否则老用户升级后 Room 启动时 schema 校验失败，App 直接开不了。
   补列用「重建表 + 搬数据」，不要用 `ALTER TABLE ADD COLUMN`：
   ADD COLUMN 必须带 DEFAULT，而 Room 校验会比对 `dflt_value`，
   带默认值会和全新安装的表结构对不上。
   **删列同理** —— 低版本 SQLite 不支持 `DROP COLUMN`，也只能重建表。
2. **删实体要配 DROP 迁移**，别留死表。v11 → v12 删掉 `reflection_questions` /
   `reflection_answers` 就是范例：功能早已删除，表也一起清掉。
   **v14 → v15 是另一个范例**：删掉 `quotes` 表（格言）+ `fish_state.energy` 列（精力）。
   两者都是「功能不在设计里」的字段，删的时候要重建 `fish_state` 表搬数据
   （SQLite 早期版本不支持 `DROP COLUMN`）。
3. **迁移链要能跑通**。改完迁移，跑 `python tools/db/check_migration_chain.py` ——
   它用 Room 导出的 schema json 当基线，在 SQLite 里模拟跑一遍完整链路，
   逐表逐列比对结果与目标 schema 是否一致。五个场景都要过：
   最老的老库升上来、全新安装、上一个版本老库升上来（带数据 / 不带数据）、
   以及每一对相邻版本单独跑一遍。
   > 脚本直接从 `AppDatabase.kt` 里抠出迁移 SQL 来跑，
   > 测的就是 App 真正执行的那段，不是抄一份。
   > **搬数据的断言要跟着迁移一起加** —— 只比 schema 的话，
   > 「列在但数据没了」这种最贵的 bug 抓不到。
4. **改了状态机要能在真机上验**。人格卡那套「底稿 + 覆盖层 + 跨档强制切回」
   不是纯函数，光看代码看不出对错。跑 `python tools/db/check_persona_state_machine.py`
   —— 它在模拟器里用设备端 sqlite3 注入状态，再看 App 的反应。
   > 注入要走**临时文件**（push SQL 文件 + `sqlite3 db < file`），
   > 直接把 SQL 塞进 `adb shell` 会被设备端 shell 把引号吃掉，
   > `VALUES('a','b')` 会变成 `VALUES(a,b)`。
   > 别用 pull/push 数据库文件那套 —— WAL 尾部残帧会让写操作报 malformed。
   踩过的坑见 §4.2。

### 4.2 模拟器实测的八个坑

这些都花过时间才定位到，重写脚本前先看一眼：

| 坑 | 表现 | 正解 |
|---|---|---|
| **不要 `adb root`** | root 会重启 adbd，模拟器里的 adbd 一断，模拟器直接退出 | 用 `run-as com.deepfocus.app` 进沙盒 |
| **不要 `adb install`** | install 会重启 adb server，模拟器跟着没 | `push` APK 到设备再 `pm install` |
| **进程要脱离父进程** | shell 命令一结束，模拟器被连带清理 | 用 WMI `Win32_Process.Create` 启动 |
| **`-gpu off` 反而更糟** | 模拟器慢到 App 一启动设备就 offline | 用默认 GPU |
| **查询 SQL 不能塞进单引号** | `sqlite3 db '...foodId='大白饭''` → 引号提前闭合 → 报 no such column → **返回空且不报错**，断言拿着空值还以为是真的 | 走临时文件 |
| **写文件必须用 LF** | `write_text` 在 Windows 上转成 CRLF，设备端 sh 报「exit: bad number」 | `write_bytes` |
| **SharedPreferences 文件名 ≠ 包名** | 找 `com.deepfocus.app_preferences.xml` 找不到 | 实际是 `deep_focus_feed_limit.xml` |
| **写库前先 force-stop App** | App 运行时 Room 持锁，外写不进去且不报错 | 先 `am force-stop` 再写 |

还有两条「不报错但结果是错的」：

- **每日喂食上限是 5 次**，计数在 SharedPreferences 里。
  跑第二遍时点大白饭会**静默被拒**（只设了个 errorMessage），
  看起来像 App 坏了。脚本跑之前要先把 `feed_count_today_*` 清零。
- **点坐标要校验在屏幕内**。
  被遮挡的节点坐标可能落在屏幕边缘（实测 (540, 2072) 在 1080x2400 的最底部），
  tap 落空，什么都不会发生。

### 4.3 单元测试锁住纯逻辑

纯逻辑的判定（24 小时饥饿规则、状态分档优先级、FEED 标签集合）
不该靠真机点 —— 又慢又脆。写 JUnit 测试跑 JVM 上：

```
./gradlew testDebugUnitTest
```

`app/src/test/java/com/deepfocus/app/ProductModelInvariantTest.kt` 覆盖：
「饿只看 24 小时」（含「饱腹 100 也照样饿」这条）、
「饿了优先于好感度档位」、
「喂一次饭只 +1，且不超过专注 10 分钟的量」（喂饭不能替代专注）、
「状态词要短」（空串和超 12 字都拒）、
「FEED 覆盖所有吃东西的动作」（17 个 id 逐个断言）、
动作库 109 条不断层。

被测的纯函数从实现里**抽出来**再断言，不要去 mock Room：
`FishRepository.focusAffinityBonus(seconds)`、
`MoodStore.isAcceptableMood(value)`。
常量（`AFFINITY_PER_FEED` / `FOCUS_AFFINITY_PER_MINUTE` / `MAX_FEED_PER_DAY`）
用 `internal const` 暴露给同模块的测试，不要 `private`。

### 4.3 产品模型的不变量要有脚本守着

「食物只有大白饭」「没有 mood 维度」「FEED 只留白饭类」这类**产品决策**
很容易在后续改代码时被悄悄破坏（多标一个标签、加回一个字段）。

跑 `python tools/db/check_food_and_action_model.py` —— 它直接读源码
`SeedData.kt` / `FoodCatalogEntity.kt` / `FishStateEntity.kt` /
`FishMoodResolver.kt` / `UserFishDao.kt` / `PetAction.kt`，
断言这些不变量还成立。改了产品模型就要同步更新这个脚本。

### 4.4 视觉规范的落地纪律

- **规范不是文档里的表格，是代码里的每个调用点**。
  加新的输入框 → 用 `SoftTextField`，不要用 Material3 的 `TextField`。
  加新的同行图标 → 加 `iconBaselineOffset(...)`。
  规范覆盖率不到 100% 就等于没有规范。
- `SoftTextField` 的 `label` 传 `null` 可以不显示标签
  （聊天输入框这类自带占位符的场景不需要二级标签）。
- 唯一的例外：`ExposedDropdownMenuBox` 的锚点被框架强制要求用
  `OutlinedTextField`，只能把描边设成透明。

### 4.5 API Key 绝不入 Git

**线上路径**（用户自己用）：
用户在「设置 → 大肥鱼 AI」填 key，存在设备本机 SharedPreferences，
经 `SecureKeyStore`（Android Keystore AES-GCM）加密。

**开发路径**（本机调试）：
把配置写进 **`local.ai.properties`**（已被 `.gitignore` 覆盖），
再跑 `python tools/db/seed_ai_config.py` 灌进设备。

- **key 永远不进 APK、不进 Git、不进构建产物。**
- 预设厂商只放域名和模型名（`AiProvider.kt`），**不放 key**。
- `local.ai.properties` 的格式是 Java properties：
  `deepfocus.ai.providerId` / `deepfocus.ai.baseUrl` / `deepfocus.ai.model` / `deepfocus.ai.apiKey`。
- 灌配置的脚本会 `force-stop` App（App 进程里缓存着 prefs，
  跑着的时候改文件会被它下一次 `save()` 覆盖回去）。
- 写 SharedPreferences 的命令必须作为**一个**参数交给 `adb shell`，
  内部用单引号把 `sh -c` 的命令串包起来 ——
  否则 `&&` 和 `>` 会被设备端最外层 shell 拆开，
  重定向跑在外层 shell（shell 用户），文件根本写不进 App 沙盒，**且不报错**。

## 版本号与 Release

- **默认不改版本号。** 任何 APK 更新都保持 `versionCode` / `versionName` 不变，
  除非用户明确说「改版本号」。
- 更新发布时，直接覆盖 GitHub Release 上同版本号的旧 APK：
  `gh release upload v<版本> <apk> --clobber`，保留原 tag 与 release。
- Release 标题统一为 `DeepFocus v<版本> — 深度专注`，不使用其他别名。

### 版本号策略（2026-11-24 用户明确）

> 用户原话：「注意我们的项目rule里增加小幅改动不变版本号，只覆盖之前的apk，
> 只有我说更新版本号时才更新。所以一直都是1.0.0版本，
> 现在的GitHub的1.0.1版本不需要，我们在视频发出之前都保持1.0.0版本。」

- **当前版本固定为 `1.0.0`（`versionCode = 100`）。**
- **小幅改动（文案 / UI 微调 / 修 bug / 换图标 / 素材替换）一律不改版本号** ——
  重新构建后用 `--clobber` 覆盖 `v1.0.0` 上已有的 APK 即可。
- **只有用户明确说「改版本号 / 发新版本」时**才递增 `versionCode` 并新建 tag。
- **不要新建 `v1.0.1` 之类的 tag**：未发布新版本前，GitHub 上只保留 `v1.0.0`。
  如果历史上误建了更高版本的 release，在用户确认前不要动它；
  用户说「不需要」时，删掉那个多余 tag/release，把 APK 发布回 `v1.0.0`。
- 发布视频/宣发物料之前，线上 APK 的版本号必须是 `1.0.0`。

## 宣传视频制作（2026-11-25）

- **宣传片统一走 `video-production` skill**（`~/.agents/skills/video-production`）。
  它是编排层，负责：需求分诊 → 素材采集 → 时间轴编排 → 动效与镜头 → 音效 → 渲染 →
  编码 → 实测验收 → 交付与归档。
- **动效风格与音效交给专项 skill**（均在 `~/.agents/skills/`）：
  - `brand-launch-video-star`（发布片主流程）/ `pixel2motion`（PNG→动效 SVG）/
    `disney-animation-rule-skill`（动效质量判断）/`procedural-fish-render` 等
    vibe-motion 全家桶 → 来源 `https://github.com/LiuYihey/vibe-motion`
  - `sound`（Freesound 真实录音检索，**优先真实素材而非合成音**）→
    来源 `https://github.com/LiuYihey/sound-skill`
  - `sound-effects`（混音标准，**做音频前必读**）
- **工程位置**：`marketing/deepfocus-promo-v7/`（当前版本），
  旧版本归档在 `marketing/_archive/`。目录说明见该目录下的 `README.md`。
- **成片验收硬指标**（必须实测，不靠感觉）：
  - 音频整体响度 **≈ -16 LUFS**，真实峰值 **< -4 dBFS**，立体声与单声道都要测。
  - 文字不得溢出、不得压到手机模型或主体；主标题走顶部安全区，补充句走底部条带。
  - 相机为**单一关键帧轨道**，逐帧位移无跳变，进场缩放不得回弹。
  - 轨迹线不得有极小弯折（转角符号翻转次数 = 0），且轨迹必须**晚于**主体出现。
- **动效原则**：一行一句、一次只说一件事；不做重复动效；不用红色做强调；
  矢量素材不用扁平三角占位；文案按用户原话，不改写。

## 工作区目录约定（2026-11-26 整理）

- **根目录只保留**：`DeepFocus/`、`FocusCat/`、`web-demo/`（三个独立 GitHub 仓库）、
  `marketing/`（宣传片工程）、`archive/`（本地归档）、`deliverables/`（最新交付物）、
  `docs/`。其余一律归档。
- **有远端或未提交改动的东西不是归档，是项目。** 任何带 GitHub remote、
  或有未推送提交 / 未提交改动的目录，都留在工作区根目录，哪怕它看起来很旧。
  （`web-demo` 曾被误放进 `archive/`，2026-11-26 已移回根目录。）
- **归档区只读、不维护、不入库**：`archive/`、`marketing/_archive/`、
  `marketing/_shared/`、`deliverables/` 均在根 `.gitignore` 中。
  `DeepFocus/.gitignore` 已加 `/archive/` 规则，设计文档与废弃脚本不上传 GitHub。
- **可再生产物随时可删**：`app/build/`、`.gradle/`、`node_modules/`、`__pycache__/`、
  `audio.wav`（可用 `make_audio.py` 重生成）。但 `app/build/outputs/apk/release/`
  里的成 APK 要保留一份到 `deliverables/apk/`，因为用户会去那里找。
- **整理必须不改变产物。** 清理前后用「逐条目 CRC 比对」验证 APK 打包内容一致
  （**不要比字节**——APK 签名不可复现，SHA 必然不同），确认 0 changed 才算完成。
  脚本见 `~/.agents/skills/project-tidying/`。
- **素材源文件只放 `DeepFocus/artwork/`**，不散落在仓库根目录；根目录同一类型的资产
  只留一份。App 图标的唯一源是 `artwork/icon_src/launcher_maid.png`
  （根目录的 `App图标.png` / `logo.png` / `New Logo.png` 三份已归档）。
- **`.kts` / `gradle` / `manifest` / `proguard` 文件不可删。** 它们是构建必需的，
  不是「某个工具专属的临时文件」。已实测：删掉 `settings.gradle.kts` 或
  `app/build.gradle.kts` 都会直接 `BUILD FAILED`。
- **两个仓库互不引用**：不把一个项目的文件挪进另一个。DeepFocus 仅引用
  `FocusCat/app/src/main/assets/cats/` 的两个 PNG（布偶 / 橘猫）。
- **Skills 一律装全局** `~/.agents/skills/`，不放工作区 `.agents/skills/`
  或 `.trae/skills/`。新增 skill 用 `curl codeload.github.com/<owner>/<repo>/zip`，
  `git clone` 在本网络不稳定。
- **字体等共用资源收敛到 `marketing/_shared/`**，避免每个历史版本各存一份
  （字体 41MB × 3 份已合并为 1 份）。
