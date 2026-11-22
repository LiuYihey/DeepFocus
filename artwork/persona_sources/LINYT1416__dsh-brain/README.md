# DSH 三器官：心情 · 记忆 · 人格

三个 [DSH](https://github.com/deepseek-ai) Cordis 插件，各自独立，又能互相喂数据：

| 目录 | 包名 | 是什么 |
| --- | --- | --- |
| `dsh-affect/` | `dsh-affect` | **心情**：五器官（杏仁核 / 下丘脑 / 海马体 / 前额叶皮层）+ 三层（评估 / 注意 / 社会）+ 四补丁（脑干 / 默认模式 / 神经内分泌 / 病感）+ 时间轴与 IO，合成一个会自己起伏的心情 |
| `dsh-memory/` | `dsh-all` | **记忆**：海马体 + 大脑皮层 + 前额叶 + 杏仁核，单入口启动；原始经历 → 事件 → 长期事实 |
| `dsh-persona/` | `dsh-persona` | **人格**：六个结构（纹状体 / 前扣带 / 眶额 / 内侧前额叶 / 后扣带 / 颞顶联合）同拍协作，白纸起步、可养成人格 |

三者的关系是单向依赖，没有循环写：心情产出「此刻什么感觉」，记忆产出「以前发生过什么」，人格读这两样，决定「这句话怎么说」。

---

## 环境要求

- 已经装好 DSH（DeepSeek Harness），能正常启动
- Windows / macOS / Linux 都行，只需要 Node 18+

**不需要 npm**，也不从 npm 安装：这三个插件只以源码目录形式加载。

---

## 安装（Windows，一步步照做）

### 1. 找到你的 profile 目录

DSH 的插件都挂在 profile 的 `node_modules` 下。默认位置：

```
C:\Users\<你的用户名>\.dsh\profiles\desktop\node_modules
```

如果 `profiles` 下不是 `desktop`，就用你实际看到的那个目录名。

### 2. 把三个插件放到不会被删的地方

比如放到：

```
D:\dsh-plugins\dsh-affect
D:\dsh-plugins\dsh-memory
D:\dsh-plugins\dsh-persona
```

> 不要直接放在 `.dsh\profiles\desktop\node_modules` 里面当普通文件夹。DSH 升级或重装 profile 时可能被清掉。

### 3. 建目录联接（junction）

以**管理员身份**打开 PowerShell，逐条执行（路径换成你自己的）：

```powershell
cmd /c mklink /J "C:\Users\<你的用户名>\.dsh\profiles\desktop\node_modules\dsh-affect"  "D:\dsh-plugins\dsh-affect"
cmd /c mklink /J "C:\Users\<你的用户名>\.dsh\profiles\desktop\node_modules\dsh-all"     "D:\dsh-plugins\dsh-memory"
cmd /c mklink /J "C:\Users\<你的用户名>\.dsh\profiles\desktop\node_modules\dsh-persona" "D:\dsh-plugins\dsh-persona"
```

注意第二条的链接名是 `dsh-all`（记忆插件的包名是 `dsh-all`，不是 `dsh-memory`），目录名可以叫 `dsh-memory`，两者不影响。

成功时每条会回一句 `已创建联接` 之类的提示。

### 4. 在 DSH 里启用

启动 DSH → 打开设置 → **插件** → 找到这三个 → 打开开关：

- `dsh-affect`（心情）
- `dsh-all`（记忆）
- `dsh-persona`（人格）

启用的先后顺序建议：**先记忆，再心情，最后人格**。心情启动时会去问记忆在不在，反过来的话第一拍只能拿到空。

### 5. 重启 DSH

插件是启动时加载的，改完必须重启一次。

---

## 验证装好了没有

### 看面板

设置里应该多出这几页：

- **她的心情** —— 有「此刻」卡片、十个维度条、情绪列表
- **她的记忆** —— 海马体的「最近记住的」、皮层的事实、冲突
- **她的人格** —— 六个器官的读数

页面空着不等于没装好：记忆要真实对话写进去才有内容，人格默认是白纸。

### 跑自测（可选，但要装 Node）

在插件目录里：

```powershell
cd D:\dsh-plugins\dsh-affect
node tests\selftest.mjs

cd D:\dsh-plugins\dsh-persona
node tests\selftest.mjs

cd D:\dsh-plugins\dsh-memory
node tests\smoke.mjs
```

每条最后一行会打印 `PASS` 或 `FAIL n`，全 PASS 说明代码本身是好的，问题只可能在加载或配置。

---

## 目录里有什么

```
dsh-affect/
  core/         引擎、融合、注入、面板、存储
  organs/       五器官
  layers/       三层
  supplements/  四补丁
  time/ io/     时间轴、输出参数
  tests/        自测（node tests\selftest.mjs）
  cordis.patch.yml

dsh-memory/
  organs/hippocampus/   海马体：原始经历 → 事件
  organs/cortex/        皮层：事件 → 事实 / 概念 / 冲突
  organs/prefrontal/    前额叶：判该不该记、谁先谁后
  organs/amygdala/      杏仁核：显著性
  tests/

dsh-persona/
  organs/       六个结构（str/acc/ofc/mpfc/pcc/tpj）
  core/         引擎、签名、淬火、特质统计
  tests/
```

---

## 会往哪里写数据

默认都在 `C:\Users\<你的用户名>\.dsh\` 下：

- 记忆：`hippocampus\`（episodes / index / raw）
- 心情：`affect\`
- 人格：`persona\`

想换位置，用环境变量 `HIPPOCAMPUS_DIR`（记忆）等，具体看各插件 `core/consts.js`。

这些是我们的真实对话数据，**开源仓库里一条都没有**，`.gitignore` 已经把 `data/` 排掉了。

---

## 卸载

1. DSH 设置里关掉三个插件
2. 删掉 `node_modules` 下那三个联接（删联接不会删源目录）
3. 想连数据一起清，再删 `.dsh\hippocampus`、`.dsh\affect`、`.dsh\persona`

---

## 已知限制（先说清楚，不粉饰）

- **语气不靠参数**：器官算出来的是句长、温度、延迟、发不发图这些参数，改不了措辞的品味。想让她说话像某个人，得在人格里放**几句真实例句**，形容词没用。
- **记忆有门槛**：不是所有话都记。寒暄和口令式的短句会被丢掉，一轮对话合成一条，我的回复只留一句摘要。
- **皮层需要时间**：事件关段后才蒸，浅巩固十分钟一轮，深巩固凌晨三点。「永久 / 精华」要跨天反复出现才会升。
- **跨插件依赖宿主事件**：不同 DSH 版本的事件名不一致时，记忆可能一条都收不到（表现为页面一直不更新）。遇到这种情况看 `hippocampus\raw\` 当天文件有没有新行。

---

## 许可

MIT，见 [LICENSE](LICENSE)。Copyright (c) 2026 LINYT1416
