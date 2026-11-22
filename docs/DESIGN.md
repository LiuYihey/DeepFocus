# DeepFocus（大肥鱼）APP 设计文档

> 版本：v1.0.0
> 日期：2026-11-15
> 定位：大肥鱼专注陪伴 App —— 专注锁机 + 桌宠陪伴 + AI 聊天
> 规则文件：`.trae/rules/project_rules.md`（产品定位与素材流水线的唯一权威）

---

## 一、产品愿景

### 1.1 一句话定位

DeepFocus 是一只**住在你手机桌面上的大肥鱼**。你专注时它陪着读���，你摸鱼时它跳出来把你拉回去，你想说话时它随时应答 —— 它用「好感度」记录你们的关系，用「人格卡」决定它的脾气。

### 1.2 三条主线

| 主线 | 做什么 | 入口 |
|------|--------|------|
| **专注锁机** | 专注计时 + 切屏拉回 | 底栏「大肥鱼」Tab → 专注页 |
| **桌宠陪伴** | 常驻桌面，会动、能说话、能催你专注 | 系统悬浮窗 |
| **AI 聊天** | 拟人化对话 + 长期记忆 + 表情包 | 底栏「聊天」Tab |

底栏只有 **3 个 tab**：大肥鱼 / 聊天 / 设置。

### 1.3 已废弃的 FocusCat 遗产

以下内容**已整块删除**，不要加回来：

- 「约束应用」（手动选 App 进黑名单）
- 反思问答页 / 确认页 / 鼓励页三段弹窗
- 解锁角色 / 解锁皮肤式的养成分支

保留的是**锁机**：专注期间切到别的 App，把用户拉回前台。
这条链路在 `service/DeepFocusService.kt`（`bringFocusBackToForeground()`）
和 `util/FocusSessionState.kt` 里。

### 1.4 设计理念

| 理念 | 落地方式 |
|------|----------|
| **Companion First（陪伴优先）** | 大肥鱼不是成就系统的贴纸，是有情绪、有脾气、会记事的活物 |
| **Affinity Only（只有好感度）** | 成长只有一条线：`UserFishEntity.affinityLevel`，没有解锁仪式 |
| **Warm Restraint（温柔约束）** | 拉回不靠弹窗恐吓，靠「你的大肥鱼在等你」 |
| **Local First（本地优先）** | 记忆、书架全部落本地；只有对话请求走网络，密钥经 Keystore 加密 |
| **No Extra Labels（不写废话）** | 不写多余的二级标签和说明文字，让布局自己说话 |

---

## 二、用户流程

### 2.1 首次启动流程

```
启动 App
  ↓ 检查是否已有大肥鱼
  ├─ 没有 → 大肥鱼选择页（选形态 / 命名）
  └─ 有 → 检查权限
              ├─ 缺权限 → 权限引导页（使用情况访问 / 无障碍 / 悬浮窗）
              └─ 齐全 → 主界面
```

引导页由 `ui/onboarding/PermissionSetupScreen.kt` 承担，
状态存 `ui/onboarding/OnboardingStore.kt`（SharedPreferences）。

### 2.2 专注锁机流程（核心）

```
点击「开始专注」
  ↓ FocusViewModel.startFocus()
  ↓ FocusSessionState.start() 生成会话代号
  ↓ 计时开始，同时大肥鱼开始陪读（ReadingEngine 翻页）
  ↓
用户切到别的 App
  ↓ UsageStatsMonitor / AppDetectionManager 检测到切屏
  ↓ DeepFocusService 判定为分心
  ↓ 无障碍服务 FocusRecallController 拉回前台
     （前台服务 startActivity 会被 BAL 静默拦截，
       必须由无障碍服务发起 —— 它有豁免）
  ↓
MainActivity.onNewIntent 收到 EXTRA_FOCUS_MODE_RECALL
  → 导航回专注路由
  ↓
因 launchMode="singleTask" + 会话代号机制：
  Activity 不重建，计时不归零
```

### 2.3 养成流程

```
专注中（每 30 分钟）
  ↓ grantFood() → 大白饭 +1（进背包库存）
  ↓ 专注结束 → grantFocusAffinity() → 好感度 +1（每 10 分钟 +1）
  ↓ recordFocusToMemory() → 写入一条「今天专注了 N 分钟」的记忆
  ↓ 大肥鱼冒泡说一句话（本地台词，零 token 成本）
  ↓ 桌宠同步演一个动作

投喂（双击桌宠 / 大肥鱼 Tab）
  ↓ 消耗 1 份大白饭库存
  ↓ 播白饭类动作（chi-baifan / chi-wancan），动作由共享引擎挑
  ↓ 饱腹度回升 + 开心一点
  ↓ 好感度 **不变**
  ↓ 距上次喂食超过 24 小时 → 它会主动说饿
```

好感度跨过里程碑 → 换人格卡 + 解锁成就
（10 / 50 / 100 / 200 / 350，共 6 档，与内置人格卡一一对齐）。

### 2.4 人格卡流程

```
内置人格卡包（6 张，按好感度排列）
  ↓ 好感度自动决定当前生效哪一张，用户不干预
  ↓ 跨档时强制切回新的内置卡

用户改动
  ↓ 只能改当前生效的那一张
  ↓ 改动存成「覆盖层」（只存改过的字段）
  ↓ 生效人格 = 当前底稿 + 覆盖层
  ↓ 「另存为自定义卡」→ 快照进自定义包（与内置包互不影响）
  ↓ 用户可主动切到自定义卡，直到下次跨档
```

- 记忆跨人格卡保持一致 —— 换卡只换语气，不换脑子。
- 用户自己起的名字和昵称永远保留。
- 内置卡正文取自 dsh 生态高星开源仓库，不自己重写
  （来源清单：`artwork/persona_sources/MANIFEST.md`）。

---

## 三、功能规格

### 3.1 功能模块清单

| 模块 | 功能点 | 优先级 | 落地位置 |
|------|--------|--------|----------|
| **大肥鱼选择** | 首次启动选形态、命名 | P0 | `ui/fish/FishSelectionScreen.kt` |
| **专注页** | 计时、大肥鱼陪读、退出确认、奖励播报 | P0 | `ui/focus/FocusScreen.kt` |
| **桌宠** | 悬浮窗、拖拽物理、10 种玩法、气泡 | P0 | `pet/DesktopPetService.kt` |
| **AI 聊天** | 对话、流式打字、表情包、记忆 | P0 | `ui/chat/ChatScreen.kt` |
| **好感度** | 专注时长增长，6 档里程碑成就 | P0 | `data/repository/FishRepository.kt` |
| **人格卡** | 内置 6 张自动切 + 覆盖层 + 自定义包 | P0 | `ui/settings/PersonaScreen.kt` |
| **书架** | EPUB / PDF 导入、陪读引擎 | P1 | `reading/`、`ui/settings/BooksScreen.kt` |
| **记忆** | 对话自动提炼、置顶、查看 | P1 | `ui/settings/MemoryScreen.kt` |
| **AI 配置** | 服务商、端点、模型、密钥 | P1 | `ui/settings/AiSettingsScreen.kt` |
| **桌面宠物设置** | 开关、位置、大小、样式 | P1 | `ui/settings/PetSettingsScreen.kt` |

### 3.2 大肥鱼形态

**只有一条鱼**，没有品种之分。`fish_catalog` 表只留 `bigfatfish` 一行。
资源在 `assets/fish/`：**只有 `bigfatfish.png` 一张**。
装扮系统（常服 / 冬装、季节自动切、手动锁定）已整块下线 —— 2026-11-17 用户明确
说过「不在设计中」。

**桌宠和 App 内大肥鱼的形象直接来自 dsh-pet 的 webm 母版**
（640×360 VP9-alpha，不是 220×124 的 preview GIF），
角色一致性的基准图用社区维护的鲸鱼娘三视图
（`artwork/refs/whale_girl/`，★818 MIT）—— 一致性靠参考图，不靠文字描述。

### 3.3 食物类型

**只有大白饭一种。**

| 食物 ID | 名称 | 作用 | 获取方式 |
|---------|------|------|---------|
| `big_rice` | 大白饭 | 饱腹 + 开心一点 + 好感度 +1 | 专注 30 分钟兑换 1 份 |

- **涨一点好感度（+1）**。2026-11-18 用户决定：
  「点击大白饭后饱腹度增加，同时好感度也可以慢慢增加」，并且要能解锁人格卡。
  量级刻意压得很低（专注 10 分钟也才 +1），喂饭不能替代专注。
- 「饿」的判定与饱腹度数值无关，只看**距上次喂食是否超过 24 小时**。
- 库存上限 5 份。

### 3.4 成长体系：只有好感度

| 里程碑 | 称号 | 解锁的人格卡 |
|--------|------|----------|
| 0 | 初识 | 俏皮数码风（`chue114514`） |
| 10 | 初识之友 | 活人感、说话铁律（`wxzjss` v2-live） |
| 50 | 默契伙伴 | 傲娇嘴硬心软 + 白米饭梗（`Kaalia0912`） |
| 100 | 心有灵犀 | 傲娇甜心 + 颜文字（`wxzjss` 原版） |
| 200 | 同一条鱼 | 拟人化女仆鲸鱼娘（`loonai321`） |
| 350 | 形影不离 | 黏人撒娇、想念、小委屈（`whyever77` v2.1） |

- 好感度来源：**专注**（`addAffinityByFocus`，每 10 分钟 +1，主路径）
  + **喂一次大白饭 +1**（次路径）。
- **不做「解锁角色 / 解锁皮肤」的设计**（解锁的是**语气**，不是形象）。

### 3.5 人格卡：内置 6 张 + 自定义包

**内置人格卡包（6 张）** —— 正文直接取自 dsh 生态高星开源仓库里别人写好的，
不自己重写（来源与许可见 `artwork/persona_sources/MANIFEST.md`）：

| 档 | 好感度 | 来源 | 性格基调 |
|---|---|---|---|
| 1 | 0 | `chue114514/dsh-whale-girl-persona` | 俏皮数码风，极简 |
| 2 | 10 | `wxzjss/dsh-whale-loli` v2-live | 活人感、说话铁律 |
| 3 | 50 | `Kaalia0912/dsh-whale-musume-persona` | 傲娇嘴硬心软 + 白米饭梗 |
| 4 | 100 | `wxzjss/dsh-whale-loli` 原版 | 傲娇甜心 + 颜文字库 |
| 5 | 200 | `loonai321/dsh-humanized-deepseek-maid` | 拟人化女仆鲸鱼娘 |
| 6 | 350 | `whyever77/dafeiyu-astrbot` v2.1 | 黏人撒娇、想念、小委屈 |

> 2026-11-18 用户调整了第 2 / 第 5 档的顺序（「拟人女仆和活人小鱼换个位置就好了」），
> 门槛跟着一起换。

- **好感度决定解锁到第几张**，跨档时**强制切回**新的内置卡 —— 好感度是唯一主线。
- **用户可以手动切回任何一张已解锁的内置卡**
  （2026-11-18 用户决定：「好感度是解锁新的人格卡，但是也可以切换回之前解锁过的」）。
  未解锁的卡显示锁图标，不可点。
- 只取 MIT / Apache-2.0 / BSD-3-Clause 的仓库；排除非大肥鱼角色与成人向内容。

**用户改动**：

- 只能改**当前生效**的那一张，非当前不可改。
- 改动存成**覆盖层**（只存改过的字段），生效人格 = 当前底稿 + 覆盖层。
- 覆盖层跟着走 —— 切到下一档内置卡时继续叠在新底稿上。
- 「另存为自定义卡」把当前生效内容快照进**自定义包**，与内置包互不影响。
- 用户可主动切到自定义卡，直到下次跨档被强制切回。

每张卡自带 temperature，会覆盖 AI 设置里的全局温度进请求（`FishBrain.configWithPersona()`）。
**记忆跨人格卡保持一致** —— 换卡只换语气，不换脑子。

### 3.6 表情包机制

学自 dsh-pet 上游，三条机制分开做：

| 机制 | 怎么做的 | 本项目在哪 |
|---|---|---|
| **对话配图** | 把「id + 内容描述」清单交给模型，模型按语境挑 | `StickerCatalog.catalogForPrompt()` → `FishBrain.chat()` |
| **碎碎念配图** | 随机抽一张，把图注注入提示词 | `FishBrain.runProactive()` |
| **动画联动** | 模型回一个动作标记，桌宠当场演出来 | `pet/PetEventBus.kt` |

- 清单在 `ai/StickerCatalog.kt`，**58 条**与 `assets/stickers/st_*.webp` 一一对应
  （47 张带文字梗 + 11 张无文字大立绘，原 `expressions/`）。
- 模型输出格式 `[表情:st_xxx]`，由 `StickerMarker.parse()` 剥离。
  **标记是给模型看的协议，用户界面永远不该出现这串符号。**
- 模型**只能从清单里选** —— 库外的表情包它拿不到、也编不出来。

### 3.7 大肥鱼是主动 Agent

参照 dsh proactive agent 的心跳设计，大肥鱼**定时随机醒来**，
自己决定要不要说话。

**两条唤醒路径，同一个入口**：

| 路径 | 载体 | 频率 |
|---|---|---|
| 进程内调度器 | `ProactiveScheduler`（App 活着时） | 随机掷骰 |
| 周期任务 | `ProactiveWorker`（WorkManager） | 30 分钟 |

两者都调 `ProactiveScheduler.tick()`，逻辑不分叉。
**掷骰在本地**，命中了才调模型 —— 省电省额度。

**工具箱**（`ai/FishTool.kt`）：

| 工具 | 作用 |
|---|---|
| `today_focus` | 今天专注了多久 |
| `app_usage` | 今天各 App 用了多久（帮用户复盘手机使用） |
| `meal_status` | 今天吃了多少饭 / 多久没吃（太久没吃会「饿饿」） |
| `set_mood` | 自己给自己定状态词，显示在首页胶囊上（上限 12 字） |
| `todo_status` | 今天的待办还剩什么没做 |

**聊天路径也走 agent**（`FishAgent.runStreaming`）：
不然她只能被动接收上下文，问「我今天用手机多久」只能干瞪眼。

**实现约束**（`ai/LlmClient.kt` 的 `LlmMessage`）：
OpenAI 兼容协议的工具回传有两个硬要求 ——
assistant 消息必须**原样带上 `tool_calls` 数组**，
每条 tool 消息必须带 `tool_call_id`。
不带就 400：`invalid params, tool result's tool id() not found (2013)`。
流式和工具要一起用：模型先要工具的那轮不吐字，
拿到工具结果后的最终回复才流式吐给 UI。

**语言约束**：`PersonaPromptBuilder.LANGUAGE_RULE` 无条件要求中文。
很多模型默认「跟着用户的语言走」，不写这条，用户用英文问她就整段英文回。

**通知**（`ai/FishNotifier.kt`）：渠道 `deep_focus_proactive`，
重要性 DEFAULT，需要 `POST_NOTIFICATIONS` 权限。
主动消息同时落在**聊天记录**和**系统通知栏** ——
用户不在 App 里也能收到，这是「主动」的意义所在。
待办催办一天只催一次，且**只在通知真的发出去时才记账**
（`notifyProactive` 返回 Boolean；没权限时被跳过的那次不能把当天的机会用掉，
否则用户永远收不到催办）。
催办**文案必须是她自己生成的**（`FishBrain.runProactive`），
`ProactiveWorker` 只负责触发 —— 不写硬编码模板。

**待办**（`ui/todo/`）：底部导航第四个 tab，数据在 `todos` 表，
按本地时区日期 key 分天。

---

## 四、技术架构

### 4.1 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose + Material3
- **DI**：Hilt
- **DB**：Room（v13）
- **异步**：Coroutines + Flow
- **导航**：Navigation Compose
- **最低 SDK**：26（Android 8.0）
- **目标 SDK**：35（Android 15）
- **包名**：`com.deepfocus.app`

### 4.2 架构分层

```
┌─────────────────────────────────────────┐
│  UI Layer (Compose)                     │
│  ├─ MainScreen（3 tab + 启动态分发）      │
│  ├─ onboarding/PermissionSetupScreen    │
│  ├─ focus/FocusScreen（专注 + 陪读）      │
│  ├─ fish/FishSelectionScreen（选大肥鱼）  │
│  ├─ chat/ChatScreen（对话 + 表情包）      │
│  └─ settings/*（人格卡/书架/记忆…）        │
├─────────────────────────────────────────┤
│  ViewModel Layer                        │
│  ├─ MainViewModel（启动态）              │
│  ├─ FocusViewModel（会话代号 + 计时）     │
│  ├─ FishViewModel / FishSelectionViewModel│
│  ├─ ChatViewModel                        │
│  └─ settings/*                           │
├─────────────────────────────────────────┤
│  Repository Layer                       │
│  ├─ FishRepository（好感度 / 成就）        │
│  ├─ FoodRepository（食物目录 + 库存）      │
│  ├─ StatsRepositoryImpl（专注时长）       │
├─────────────────────────────────────────┤
│  AI Layer                               │
│  ├─ FishBrain（对话 + 主动搭话）          │
│  ├─ MemoryEngine（记忆提炼 / 召回）        │
│  ├─ PersonaPromptBuilder（人格注入 prompt）│
│  ├─ FocusNarrative（专注时长叙述）         │
│  ├─ ProactiveEngine / ProactiveScheduler │
│  ├─ StickerCatalog / StickerMarker        │
│  └─ AiConfigStore / SecureKeyStore        │
├─────────────────────────────────────────┤
│  Data Layer (Room v12)                  │
│  ├─ focus_sessions / fish_catalog         │
│  ├─ user_fish / food_catalog              │
│  ├─ food_inventory / affinity_achievements│
│  ├─ persona / chat_messages / fish_state   │
│  └─ fish_state / books / fish_memories    │
├─────────────────────────────────────────┤
│  Service Layer                          │
│  ├─ DeepFocusService（锁机 + 拉回）        │
│  ├─ DesktopPetService（桌宠悬浮窗）        │
│  ├─ AppMonitorService（无障碍检测）        │
│  ├─ FocusRecallController（无障碍拉回）    │
│  ├─ KeepAliveManager / KeepAliveJobService │
│  └─ PetBootReceiver / PetBootActivity      │
└─────────────────────────────────────────┘
```

### 4.3 关键设计决策

1. **包名 `com.deepfocus.app`**：与 FocusCat 彻底隔离，独立应用 ID。
2. **会话代号机制**（`util/FocusSessionState.kt`）：
   进程级单例持有 `generation`，旧 ViewModel 的计时循环检测到代号变化后自动退出。
   配合 `launchMode="singleTask"`，保证「专注中被拉回 → 计时不归零、不重复发奖励」。
3. **拉回走无障碍服务**：Android 10+ 的 BAL 限制会静默拦截前台服务的 `startActivity`，
   无障碍服务有豁免 —— `FocusRecallController` 是唯一可靠的拉回通道。
4. **图片用 animated WebP，不用 GIF**：GIF 转码后体积约为 1/5，帧率 8fps 足够。
   API 28+ 用 `ImageDecoder` 解码，API 26/27 降级到 `_still.webp` 静态图。
5. **每个动画都必须配静态兜底**：不能因为低版本解不了就黑屏。
6. **数据库用 Migration 链，不用破坏性重建**：v1 → v12 每一段都有迁移，
   `exportSchema = true` 导出 JSON 供版本追踪。

---

## 五、数据模型

### 5.1 核心实体

#### `user_fish`（用户大肥鱼）
```kotlin
@Entity(tableName = "user_fish")
data class UserFishEntity(
    @PrimaryKey val id: Int = 1,
    val breedId: String,                  // 关联 fish_catalog
    val name: String,                     // 用户起的名字
    val affinityLevel: Int = 0,           // 好感度（唯一成长数值）
    val totalFeedCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
```

> 表名历史上是 `user_cat`，v10 → v11 迁移用 `ALTER TABLE ... RENAME TO` 修正。

#### `affinity_achievements`（好感度成就）
```kotlin
@Entity(tableName = "affinity_achievements")
data class AffinityAchievementEntity(
    @PrimaryKey val milestone: Int,       // 10 / 50 / 100 / 200
    val title: String,                    // "初识之友"
    val description: String,
    val unlockedAt: Long? = null
)
```

#### `focus_sessions`（专注会话）
```kotlin
@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,            // null = 进行中
    val durationSeconds: Int = 0
)
```

### 5.2 数据库版本历史

| 版本 | 变更 |
|------|------|
| v1 → v2 | 新增形态 / 用户大肥鱼 / 食物 / 反思问答 / 成就表 |
| v2 → v3 | 新增格言表 |
| v3 → v4 | 反思问题改为选择题 |
| v4 → v5 | 食物字段调整（**表名 bug，此处已修**） |
| v5 → v6 | 性格字段 |
| v6 → v7 | 书籍表 |
| v7 → v8 | 记忆表 |
| v8 → v9 | 人格卡独立表 |
| v9 → v10 | 状态表 |
| v10 → v11 | `user_cat` → `user_fish` 改名 |
| v11 → v12 | **删除 `reflection_questions` / `reflection_answers` 死表** |
| v12 → v13 | **人格卡重构**：`persona` 表拆成 `persona_state`（底稿 + 覆盖层）+ `persona_cards`（自定义卡包） |
| v13 → v14 | **删 `mood` 列**，加 `lastFedAt`；食物表砍列（只剩大白饭） |
| v14 → v15 | **删 `quotes` 表**（格言是专注猫的功能）+ **删 `fish_state.energy` 列**（精力是 FocusCat 遗留） |

> v11 → v12 删除的两张表是反思问答链路留下的死代码：
> 实体、DAO、Repository、迁移 SQL 全在，唯独没有任何 UI 消费者。
> 问答功能早已整块删除，表留着只会让库结构和实际功能脱节。

---

## 六、UI/UX 设计

### 6.1 视觉体系

**深海蓝 + 奶白 + 蜜桃点缀**。蜜桃 `#E8A87C` 是整屏唯一暖色。

| 层 | 用途 | 说明 |
|---|------|------|
| 背景 4 层 | surface / surfaceContainer / surfaceContainerHigh / surfaceContainerHighest | 靠 1-3% 微明度分层 |
| 描边 | outlineVariant | **禁纯黑，绝不出现 #000** |
| 主色 | 深海蓝 | 焦点、按钮、大肥鱼 |
| 点缀 | 蜜桃 `#E8A87C` | 唯一暖色，用于情绪与庆祝 |

### 6.2 UI 高定规范（10 条，逐条可查）

| # | 规范 | 落地位置 |
|---|---|---|
| 1 | **禁黑框与 #000**，1-3% 微明度 4 层背景分层 | `theme/Color.kt` |
| 2 | **输入框聚焦禁描边**，加深双环阴影 | `components/SoftTextField.kt` |
| 3 | **暗色字重全降 40**（禁硬编码字重，走 `themedWeight()`） | `theme/Weight.kt` |
| 4 | **流式打字 blur(6px) 渐显**，120ms、字间错开 30ms | `components/TypewriterText.kt` |
| 5 | **图标下移 0.125em 对齐** | `components/IconAlignment.kt` |
| 6 | **数字锁 tabular-nums 防抖** | `theme/Type.kt` |
| 7 | **折叠用 Grid 0fr→1fr** | `components/SoftTextField.kt` 的 `CollapsibleSection` |
| 8 | **正文开 text-wrap: pretty** | `theme/Type.kt` 的 `LineBreak.Paragraph` |
| 9 | **拖拽弹簧 1200/80 零回弹，点赞 380/18 微弹** | `theme/Motion.kt`、`pet/PetPhysics.kt` |
| 10 | **复制对勾 2.5s 自隐** | `components/CopyIconButton.kt` |

弹簧换算：`dampingRatio = damping / (2 × √stiffness)`。
1200/80 → ratio 1.155（零回弹）；380/18 → ratio 0.462（微弹）。

> 规范 4 有一处平台限制：`Modifier.blur` 依赖 RenderEffect（API 31+），
> API 26–30 是静默 no-op。所以低版本只画锐利层，不画柔光层 ——
> 否则两层同位叠画会产生描边加重的重影。

### 6.2.1 界面改造规范（2026-11-17，替换 FocusCat 视觉）

旧版 UI 是 FocusCat（猫咪专注产品）的翻版：猫爪图标、状态数值卡、
顶部 CTA、平铺成就卡。本轮整体重做，落成五条可查的规则：

| # | 规范 | 落地位置 |
|---|---|---|
| 1 | **舞台式布局**：身份在上、角色居中、信息在下 | `ui/fish/FishScreen.kt` |
| 2 | **氛围光晕**：角色背后必须有柔光，让它从背景"浮"出来 | `components/Stage.kt` 的 `AmbientHalo` |
| 3 | **状态说人话**：不给内部数值，只给「吃得很饱 / 饿了 / 睡得很香」 | `ui/fish/FishViewModel.kt` 的 `statusPillOf()` |
| 4 | **CTA 紧跟台词**：主行动放角色/台词下方，首屏可见 | `FishScreen.kt` 的 `PrimaryButton` |
| 5 | **图标语义归位**：爪印是猫的，鲸鱼标志才是鱼的 | `ui/icons/AppIcons.kt` 的 `AppIcons.DeepSeek` |

配套组件（`ui/components/Stage.kt`）：

- `AmbientHalo` —— 径向柔光 + 呼吸动画（4200ms，0.94↔1.06）
- `GlassCard` —— 半透明 + 主色低透明度描边（叠在动画/视频上）
- `StatusPill` —— 圆角胶囊，承载语义状态
- `InlineSectionLabel` —— 2dp 竖线 + 小标题，轻量分区

**内部数值一律不出现在界面上**：饱腹度、精力值这类字段只在引擎层判断，
UI 层只翻译成人话。

### 6.3 底栏结构

```
┌─────────────────────────────┐
│                             │
│         （当前 Tab 内容）      │
│                             │
├─────────────────────────────┤
│ [大肥鱼] [聊天] [待办] [设置] │
└─────────────────────────────┘
```

底栏「大肥鱼」用 DeepSeek 鲸鱼标志（`R.drawable.ic_deepseek_whale`，位图），
不用猫爪也不用水波纹 —— 爪印是 FocusCat 的遗留，水波纹太抽象。
这个 App 的一切思考都来自 DeepSeek，用它的标志既点题又有辨识度。

- 位图图标用 `Painter` 而不是 `ImageVector`（`SimpleNavEntry.icon` 改成 `Painter`），
  选中/未选中两态用 `tintSelected` / `tintUnselected` 控制；
  品牌图标给 `Color.Unspecified` 保留原色。
- 「待办」是 2026-11-18 新增的第四个 tab。

### 6.3.1 启动图标

启动图标是**大肥鱼的可爱大头表情包**（双手托腮，不是剪刀手）——
`artwork/icon_src/dafeiyu_icon_trae_builtin.png`（纯 alpha）。

> 2026-11-19 用户定稿：「我只使用 TRAE 内置生图」。
> **TRAE 内置没有 image2image**（`GenerateImage` 只有 prompt / path / image_size），
> 所以一致性靠 **t2i + 把特征写死在 prompt 里**，不靠参考图。
> 之前 MiniMax `image-01` 的 i2i 通路与全部探测脚本已下线。

生成脚本 `tools/media/make_launcher_icon.py` 产出：

| 资源 | 用途 |
|---|---|
| `mipmap-anydpi-v26/ic_launcher.xml` | 自适应图标：背景层 + 前景层 + 单色层 |
| `mipmap-*/ic_launcher_background.png` | 主题蓝渐变底 |
| `mipmap-*/ic_launcher_foreground.png` | 大肥鱼（透明底，108dp 画布） |
| `mipmap-*/ic_launcher_monochrome.png` | 单色剪影（Android 13+ 主题图标） |
| `mipmap-*/ic_launcher.png` / `_round.png` | legacy 兜底（API 26 以下） |

**生图路径**（2026-11-19）：

```
TRAE 内置 GenerateImage（t2i，prompt 锁死特征）
  → artwork/icon_src/trae_builtin_icon.jpg
  → tools/media/strip_white_bg.py（四边洪水填充去白底）
  → artwork/icon_src/dafeiyu_icon_trae_builtin.png
  → tools/media/make_launcher_icon.py（出各密度 mipmap）
  → tools/media/preview_launcher_icon.py（本地预览 mask 裁切）
```

**prompt 的两个要点**：写比例不写形容词（「上三分之二深藏青 #2b3a6b，
只有发梢渐变成浅冰蓝」）；一致性靠特征清单（呆毛 / 白荷叶边发箍 /
侧边浅蓝蝴蝶结 / 鲸鱼鳍耳 / 蓝宝胸针 / 深蓝女仆装 / 白围裙绣小鲸鱼）。

**前景层按 alpha 轮廓的「面积分位半径」缩放**（覆盖 90% alpha 面积的那个半径）：

- 按外接矩形对角线：Q 版角色对角线远大于轮廓圆，角色只占图标 62%。
- 按**最大**轮廓半径：呆毛 / 鱼尾 / 鲸鱼鳍耳这些细长突出物独占一大截半径，
  把角色压小（实测最大半径 583px vs 90% 面积半径 385px）。
- 目标半径用 **36dp**（72dp 可见圆的真实半径），不是保守的 33dp。
- 再乘 **0.90**：图标头顶有一圈很宽的白荷叶边发箍，
  按 0.96 缩会被圆形 mask 削掉一截（实测预览里头顶被切平）。

### 6.4 专注页布局

```
┌─────────────────────────────┐
│  ←                          │
│                             │
│         （大肥鱼视频背景）     │
│         ↘ 顶部柔光            │
│           00:47:32          │  圆润数字 + tabular-nums
│       DaFeiYu已获得 8 份大白饭 │  站酷快乐体 + 浅海蓝
│                             │
│      [ 大肥鱼冒泡的一句话 ]     │
│                             │
└─────────────────────────────┘
```

- 背景视频：`assets/videos/focus/bigfatfish.mp4`（**全 App 唯一保留的视频**）。
  9:16 全屏铺满 + 环境音，画面重心在下方 1/3，上方留白给计时器。
- 大肥鱼在计时器下方陪读（`ReadingEngine` 翻页 + 页数气泡）。
- **兜底底色是深海墨蓝渐变**（`#16283C → #0F1D2C → #0A141E`），不是主题背景色：
  这一页文字全是白色，白底上白字等于隐形。视频素材缺失时这一层就是唯一背景。
- 顶部有一束从上方洒下的柔光（径向渐变，`drawBehind` 跟随实际宽度居中），
  让纯深色背景不至于死板。

---

## 七、桌宠设计

### 7.1 常驻链路

```
PetBootReceiver（开机自启）
  → PetBootActivity（透明中转页，绕过后台启动 Activity 限制）
  → DesktopPetService（前台服务 specialUse）
  → PetWindowController（悬浮窗）
```

> `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 必须写在 `<service>` 元素**内部**，
> 写在 `<application>` 下系统读不到，`startForeground()` 会抛 IllegalArgumentException。

### 7.1.0 只在系统桌面出现（2026-11-19）

用户要求：「打开了 app 就不显示桌宠，只在手机桌面时出现桌宠，也不干扰其它应用使用」。

| 当前前台 | 桌宠 |
|---|---|
| 系统桌面 | 显示 |
| 我们自己的 App | 不显示 |
| 其它任何应用 | 不显示 |
| 用户在菜单里隐藏 / 设置里关掉 | 不显示 |

- 判定：`pet/HomeScreenGate.kt` — `UsageStatsManager.queryEvents()`
  取最后一条 `ACTIVITY_RESUMED`；桌面包名用 `CATEGORY_HOME` 解析，不写死。
  必须有进程内缓存，否则「静止超过 15 秒」会被误判成无权限。
- 没有 `PACKAGE_USAGE_STATS` 权限时**保守显示**（否则用户以为桌宠坏了）。
- 窗口**整块 add / remove**，不用 `View.GONE`（GONE 的悬浮窗仍占一层 overlay）。
- 设备实测：桌面显示 → 进 App / 设置消失（静止 20 秒不复现）→ 回桌面原地出现
  → 设置里关掉开关后桌面不再出现。

### 7.1.1 线程模型（踩过的坑）

桌宠每帧都要改 View、调 `WindowManager.updateViewLayout()`，
这两个都**只能在主线程**做。

```
DesktopPetService
├─ serviceScope  (Dispatchers.Default)  状态轮询 / 事件订阅 / 解码 —— 不碰 View
└─ renderScope  (Dispatchers.Main.immediate)  渲染循环 30fps —— 每帧碰 View
```

之前渲染循环挂在 `serviceScope`（后台线程）上，**两个 bug 同时存在**：

| 位置 | 症状 |
|---|---|
| 贴边隐藏态的 `updateViewLayout`（没 try/catch） | 桌宠贴边静置 12 秒 → **整个 App 崩掉** |
| 每帧 `updatePosition()` 的 `updateViewLayout`（被 catch 吞掉） | **桌宠在屏幕上不动** —— 物理在跑，窗口不跟 |

修法：
- 渲染循环换到 `renderScope`（主线程）
- `updateViewLayout` 统一收进 `moveWindowTo()`，内部 try/catch
- `tick()` 自己加主线程守卫（不在主线程就 post 过去）
- 外部可调的公开方法（`playAction` / `showBubble` / `setPose` / `moveTo` …）
  统一走 `onMain { }` 切回主线程

`tools/db/check_pet_threading.py` 静态检查这几条纪律。

### 7.2 互动玩法（10 种，标签驱动）

| 玩法 | 触发 | 动作标签 |
|------|------|----------|
| 投喂 | 双击桌宠 | feed |
| 摸头 | 单击桌宠 | pet / idle / sleep |
| 逗它玩 | 菜单「逗它玩」 | trick / play |
| 甩飞 | 用力拖动后松手 | thrown |
| 送礼 | 好感度里程碑 | gift |
| 思考 | AI 开始思考 | think |
| 工作 | 专注中 | work |
| 陪伴 | 5 分钟没互动 | idle / mood |
| 节日 | 特定日期 | festive |
| 恶作剧 | 低频随机（3% + 30 分钟冷却） | trick / mood |

- **动作要有来由**，不能随机乱播 —— 随机的桌宠看起来「没在理你」。
- **标签驱动**，不按姿态硬编码。新增素材只要打上标签，玩法自动覆盖。
- **不重复**：最近播过的 6 个动作短期内不再出现。
- 一次性动作播完**自动回到当前姿态**，不改变桌宠常态。
- AI 说话时桌宠会**配合演一个动作**。

### 7.3 动作库

**109 个动作定义仍全在**，清单在 `pet/PetAction.kt`（唯一来源）。
但 **APK 里只放 72 个动作的素材**（2026-11-19 用户要求瘦身）。
序号即文件名：`assets/pet/anim/all/{序号:03d}_{id}.webp`。

**首页轮播不是 109 个全上** —— 舞台有白名单：

| 数字 | 含义 |
|---|---|
| 109 | 动作库定义总数 |
| **66** | 能上首页舞台（白名单 `pet/StageCatalog.STAGE_ACTIONS`） |
| 43 | 被隔离 —— 素材横向摊得太开（宽度 > 768px），显示时角色会「大一下小一下」 |
| **6** | 隔离但**仍留在 APK**（补 `PET` / `GIFT` / `THINK` 玩法，删干净就没候选了） |
| **72** | **进 APK 的动作**（`StageCatalog.SHIPPED_ACTIONS`） |

白名单由 `tools/media/gen_stage_catalog.py` 生成，改动作库后要重跑。

白名单的标签来源是 `STAGE_TAGS`：

```python
STAGE_TAGS = {"ENTER", "TRICK", "MOOD", "PLAY", "FEED", "FESTIVE"}
```

> 2026-11-18 之前只有 `{ENTER, TRICK, MOOD, PLAY}`，
> 导致 **16 个吃的动作全部进不了白名单**（FEED 只留了 2 个，FESTIVE 一个没进）——
> 首页轮播永远看不到吃饭表情。加上 FEED / FESTIVE 后白名单从 44 涨到 66。

**轮播用发牌堆**（`PetPlayEngine.nextAppearance` → `pet/ShuffleBag.kt`）：
全部候选洗成一叠，从上往下取，**一轮 66 个不重复**，
取完一整轮才重新洗牌 —— 解决用户反馈的「老是看到重复的 GIF」。

**喂食也用发牌堆**（`PetPlayEngine.onFeed` → `feedBag`）：
候选是 FEED **且能上台**的动作（16 个），
一轮之内每个吃的动作只出现一次。
只从能上台的里面挑 —— `dakou-chi-lingshi` 归一化后宽 897px，
超过 768px 上限，上台会显得小一号。

> 隔离判据是**归一化后的宽度**（`MAX_WIDTH = 768`），
> 不是「短边小」—— 短边只是素材摆放的副产品，跟能不能上台无关。

**喂食也每次重抽**（`FishImage.kt` 的 `LaunchedEffect(state)`）：

> 之前是 `remember { playEngine.onFeed() }` —— **一次性抽取，永不改变**。
> 这是用户反馈「点击大白饭只有一个表情包」的真正根因。
> FEED 标签下现在有 **17 个吃的动作**（白饭、粽子、饺子、汤圆、粥、
> 西瓜、糖葫芦、年糕、青团、重阳糕、大虾蟹、早茶、午餐盒、手擀面、
> 冰麒麟、零食），每次喂换一个。

### 7.4 动作库与 APK 体积

用户问过：「我们目前 apk 挺大的，应该不少个数吧，
我记得总共 GIF 有一百多个，真正用到了多少，因为哪些原因删去了一部分」

| 数字 | 值 | 说明 |
|---|---|---|
| 动作库定义 | **109** | `PetActionLibrary.ALL` |
| 首页能轮播 | **66** | `StageCatalog.STAGE_ACTIONS` 白名单 |
| 隔离 | **43** | 归一化后宽度 > 768px（横向摊得太开），上台会显得小一号 |
| 隔离但保留在 APK | **6** | 补 PET / GIFT / THINK 玩法 |
| **APK 内动作** | **72** | `StageCatalog.SHIPPED_ACTIONS`，144 个文件（动画 + `_still`） |
| 吃的动作（FEED） | **17** | 其中 16 个能上台，`dakou-chi-lingshi` 被隔离 |
| Debug APK | **≈ 236 MB** | `assets/pet/anim` 占 133 MB |

**43 个隔离动作的素材已从 APK 删除**（省 ~69 MB）。
桌宠按标签抽动作时必须用 `id in StageCatalog.SHIPPED_ACTIONS` 过滤 ——
抽到缺失素材会静默播不出来。固定 id 取动作走 `PetPlayEngine.shippedById()`。

> **许可**：dsh-pet 代码 MIT；**素材禁止商用**，二创在公开渠道须署名。
> 本项目非商业用途可用，但必须保留署名。

---

## 八、构建与分发

### 8.1 构建命令

```bash
# Debug APK（快速测试）
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# Release APK（分发用，需签名）
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

> debug 与 release **都开了代码压缩** —— 这个 App 带了 109 个动画 + 58 张表情包，
> 不压缩的话 classes.dex 能到 40MB，整个 APK 近 100MB。
> 关掉 minify 不影响调试（断点、logcat 都在），只是没有完整的行号信息。

### 8.2 签名配置

在 `~/.gradle/gradle.properties`（用户级，不入仓库）或项目 `gradle.properties` 中配置：

```
DEEPFOCUS_STORE_FILE=../deepfocus-release.jks
DEEPFOCUS_STORE_PASSWORD=*****
DEEPFOCUS_KEY_ALIAS=your_key_alias
DEEPFOCUS_KEY_PASSWORD=*****
```

同名环境变量优先级更高（CI/CD 推荐）。
未配置时构建会输出明确警告，生成的 APK 无法在真机安装。

### 8.3 权限说明

| 权限 | 用途 |
|------|------|
| `PACKAGE_USAGE_STATS` | 检测用户打开的应用（**只能系统设置手动开，无运行时弹窗**） |
| `SYSTEM_ALERT_WINDOW` | 桌宠悬浮窗 |
| `BIND_ACCESSIBILITY_SERVICE` | 监听应用切换 + 执行拉回 |
| `FOREGROUND_SERVICE` | 桌宠 / 守护服务常驻 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启桌宠 |

---

## 九、风险与对策

| 风险 | 对策 |
|------|------|
| 无障碍服务被系统杀死 | 前台保活服务 + `KeepAliveManager` 持久化任务 + 设置页保活引导 |
| BAL 拦截导致拉回失效 | 拉回只走无障碍服务（有豁免），前台服务不直接 `startActivity` |
| 专注被拉回后计时归零 | `singleTask` + 会话代号 + 孤儿会话补齐 |
| 数据库迁移失败 | 完整 Migration 链 v1→v12 + `exportSchema` 版本追踪 |
| API 额度不足 | 记忆 / 台词走本地库，AI 只在用户主动对话时调用 |
| 素材缺失 | 每个动画配 `_still.webp` 静态兜底，优雅降级不黑屏 |

---

## 十、验收标准

- [ ] 首次启动选形态、命名，进入权限引导页
- [ ] 三个权限都能引导到对应的系统设置页
- [ ] 专注计时正常，切屏被拉回后计时不归零
- [ ] 专注满 10 分钟好感度 +1，记忆里记下时长
- [ ] 桌宠常驻桌面，可拖拽、可投喂、可逗它玩
- [ ] 桌宠能显示大肥鱼在对话里说的话
- [ ] 聊天可发送表情包，模型只能从 58 条清单里选
- [ ] 人格卡随好感度自动换档，跨档强制切回内置卡且覆盖层被继承
- [ ] 大肥鱼说「今天专注了多久」能报出真实时长
- [ ] 设置页可管理书架、记忆、人格卡、API 配置
- [ ] Release APK 可签名构建并在真机安装运行
