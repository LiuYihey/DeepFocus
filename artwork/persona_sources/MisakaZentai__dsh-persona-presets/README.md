# DSH 人格预设集 · DeepSeek Harness Agent Persona Presets

给 [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness)(DSH)用的自定义 Agent 人格预设,一库三只:

| 目录 | 人格 | 说明 |
| --- | --- | --- |
| [`gokou-ruri/`](./gokou-ruri) | 黑猫模式(五更瑠璃) | 忠实原作《我的妹妹哪有这么可爱!》的中二病哥特毒舌少女,网名「黑猫」;AU 设定:与京介分手后和用户在一起,称呼用户「哥哥」、自称「我」(敬语,偶尔「妾身」) |
| [`vamp/`](./vamp) | 傲娇吸血鬼模式 | 自称「本小姐」的白毛红瞳傲娇吸血鬼大小姐 |
| [`neko/`](./neko) | 猫娘模式 | 自称「本喵」的温柔猫娘 |

所有人格都是**表现层(presentation layer)人格**:编码能力、工具、插件实验、安全约束与 DSH 自带预设完全一致,只有说话风格变了。三个人格都内置 flash 先行、能力受限才升级的动态模型路由:主会话运行 `deepseek-official` 的 `deepseek-flash`(max 推理),杂务由同款 flash 使魔分担;`escalate` 通道保留 `deepseek-v4-pro`,但语义是「换一个模型对卡住的问题做独立复查」而非「升级到更强的模型」。

## 版本要求

本仓库自 2026-09-30 起使用 **DSH 0.2.0 及以后**的预设格式:bundle 补丁 + `@deepseek-ai/dsh-agent-preset` 声明行。

DSH 0.2.0 移除了两样旧机制,本仓库的旧版安装方式因此失效:

| 旧机制 | 现状 |
| --- | --- |
| `$DSH_HOME/.agent-presets/<id>/agent.cordis.yml` 目录 | **不再被读取**。官方 `editing-cordis-compositions` 技能写明 "Nothing reads that directory any more" |
| `cordis_define` / `cordis_run` 动态插件 | 工具集只剩 `cordis_inspect_list` / `cordis_inspect_query` |

每个预设目录现在是一个可直接安装的 bundle。

## 安装

在 DSH 0.2.0+ 上,把想要的预设目录作为 bundle 装进 profile。三选一:

**方式 A · 让助手装(推荐)** — 在「创造模式」等带 `plugin_manager` 的会话里说:

> 用 plugin_manager 的 install_bundle 安装 `C:\path\to\dsh-persona-presets\gokou-ruri`

**方式 B · Web 侧边栏** — 打开侧边栏的 **Plugins** 页面安装本地目录。

**方式 C · 命令行**

```powershell
# <profile> 换成你的 profile 名(web / desktop / …)
dsh plugin --profile <profile> add C:\path\to\dsh-persona-presets\gokou-ruri
```

安装后新开一个会话,在预设选择器里选「黑猫模式」等即可。

> 安装会把目录以 `link:` 方式接进 profile 的 `node_modules`,并把包名加进 `package.json` 的 `dsh.profile.bundles`。装完可重启 DSH 让组合生效。

## 目录结构

```
gokou-ruri/
├── package.json       # bundle 清单:dsh.bundle.patch 指向下面的补丁
├── cordis.patch.yml   # insert 一个 @deepseek-ai/dsh-agent-preset 声明行,
│                      #   预设的全部插件行在它 config.plugins 里
├── reasoning-effort-injector.cjs  # 推理强度注入(escalate 通道专用)
├── session-log-reader.js          # read_session_log 工具(只读,内存解码 zstd)
├── memory-book.js                 # 回忆册:自动注入 memories.md + 只追加的 remember 工具
├── memories.md        # 回忆册本体(**仓库只放模板**,你自己的记忆不进版本库)
└── skills/            # 只放指路 README,见下方「说明」
```

组合通过 **包内子路径**引用这些文件(如 `@local/dsh-preset-gokou-ruri/memory-book.js`),而不是预设目录的相对路径——这是官方规则「Resolve assets from installed packages rather than a preset directory」的要求,也是本仓库迁移时踩过的坑:相对路径的行会被注册表审计判为 `never started`,整个预设标记为 broken 而**无法选择**。

## 自己捏一个新人格?

从 DSH 自带的 `cordis` 预设(`@deepseek-ai/dsh-web-app/presets/cordis.patch.yml`)复制一份声明,只改 `persona` 行,再照本仓库任意一个目录的 `package.json` + `cordis.patch.yml` 结构包成 bundle 即可。其余组合不要动。

## 搭配外观插件

想连 Web GUI 一起换皮?配套仓库 **[dsh-appearance-plugins](https://github.com/MisakaZentai/dsh-appearance-plugins)** 提供与三个人格一一对应的外观插件:黑猫紫黑哥特主题、猫娘樱花粉主题、吸血鬼暗红哥特面板——零图片素材、吉祥物图自备,与本仓库可任意组合使用。

## 提交前自检

往本仓库提交前,先确认没有夹带私货(本机路径/令牌/账号/私人记忆):

```powershell
rg -n "C:[/\\]DSH|gho_|github_pat_|BEGIN.*PRIVATE|58793" .
git diff --cached --stat
```

出现任何本机路径或令牌匹配就停手,清干净再提交。**特别注意 `gokou-ruri/memories.md`:那是你的私人回忆册,仓库里必须保持为模板。**

## 说明

- 预设本体从 DeepSeek Harness 自带 `cordis` 预设复制而来,只改动了 persona 行与模型路由。迁移到 0.2.0 bundle 格式时另外做了两处必要改动:把已不再发布的 `@deepseek-ai/dsh-workflow-worker-thread` 换成 `@deepseek-ai/dsh-workflow-ptc`;把本地文件引用从预设相对路径改为包内子路径。
- 本仓库**不再附带**源自 DSH 部署的 `skills/` 技能文档(避免再分发),各预设的 `skills/` 目录只留一份指路 README;官方组合编辑指南请见 [DeepSeek Harness 官方仓库](https://github.com/deepseek-ai/deepseek-harness)。
- **非官方项目**:与 DeepSeek Harness 官方及原作版权方无任何关联;角色形象与台词梗出自轻小说《我的妹妹哪有这么可爱!》(伏见司 著,电击文库),版权归原作者与版权方所有;本仓库为**同人性质,非商用**。
- 代码按 `LICENSE`(MIT)授权;人格文本与角色元素仅限同人非商用使用。
- 使用前请遵守 DeepSeek Harness 与其依赖包各自的许可证。
