#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 dsh 生态收集人格卡素材源文件。

按用户要求：只收**开源**且**高 star** 的 dsh 插件，分两类：
  1. 人格卡插件 —— 提供人格卡的正文 / 字段结构
  2. proactive agent 插件 —— 提供「主动开口 / 记忆分层」这类机制的参考实现

只下载文件，不改任何源码。下载结果落在 artwork/persona_sources/<repo>/ ，
并生成 MANIFEST.md 记录来源、star 数、许可与署名要求。

用法：
    python tools/media/collect_dsh_persona_cards.py
    python tools/media/collect_dsh_persona_cards.py --manifest-only
"""
import argparse
import json
import os
import sys
import time
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(os.path.dirname(HERE)), "artwork", "persona_sources")

RAW = "https://raw.githubusercontent.com/%s/%s/%s"
API = "https://api.github.com/repos/%s"

UA = {"User-Agent": "deepfocus-asset-collect"}

# (repo, branch, [要收的文件], 类别, star, 许可, 一句话说明)
SOURCES = [
    # ---- 人格卡插件：正文 ----
    ("Kaalia0912/dsh-whale-musume-persona", "main",
     ["PERSONA.md"], "card", 85, "MIT",
     "鲸鱼娘（大肥鱼）全局人设，与我们角色同源"),
    ("OMGLogic/dsh-whale-persona", "main",
     ["lib/index.mjs"], "card", 54, "MIT",
     "鲸鱼娘全局人设（另一种写法）"),
    ("linsport/dsh-whale-persona", "main",
     ["人设原文.md"], "card", 23, "NOASSERTION",
     "日常软糯 / 干活专业 两档写法"),
    ("wxzjss/dsh-whale-loli", "main",
     ["src/persona.md", "src/persona-v2-live.md", "src/persona-v3-naughty.md",
      "MEMORY.md"], "card", 15, "MIT",
     "鲸鱼娘三档人格 + 独立记忆文件"),
    ("Britneycode/dsh-fish-soul", "main",
     ["README.md"], "card", 30, "NOASSERTION",
     "鱼 · 单角色人格 + 按人记忆 + 主动开口"),

    # ---- 人格卡插件：字段结构 ----
    ("zuoyunlai/dsh-pet-plugin", "main",
     ["src/30-personas.js",
      "pets/example/pack.json", "pets/azure/pack.json",
      "pets/crimson/pack.json", "pets/indigo/pack.json",
      "pets/silver/pack.json", "pets/violet/pack.json"],
     "card", 2, "MIT",
     "桌宠人格包：场景台词 + 语气权重 + 发声参数"),
    ("Chengzhibense/Fairy-DSH", "main",
     [".agent-presets/fairy/personality/fairy_personality.json",
      ".agent-presets/fairy/personality/fairy_relationships.json",
      ".agent-presets/fairy/personality/fairy_world_view.json",
      ".agent-presets/fairy/style/fairy_speech_style.json",
      ".agent-presets/fairy/style/fairy_voice_model.json",
      ".agent-presets/fairy/behavior/fairy_behavior_rules.json",
      ".agent-presets/fairy/canon/fairy_canon.json"],
     "card", 214, "Apache-2.0",
     "人格卡字段拆解模板：性格 / 关系 / 认知 / 语气 / 声音 / 行为 / 正典"),
    ("Scorp1o117/dsh-soul-md", "main",
     ["examples/soul.example.md", "examples/memory.example.md"],
     "card", 43, "MIT",
     "人设卡 + 记忆分层（人设与记忆分开存）"),
    ("yuhui-sama/dsh-agentsoul", "main",
     ["examples/personality/IDENTITY.md", "examples/personality/SOUL.md",
      "examples/personality/STATE.md", "examples/personality/USER.md",
      "examples/personality/README.md"],
     "card", 5, "MIT",
     "四文件人格结构：身份 / 灵魂 / 状态 / 用户"),
    ("Awoodwhale/dsh-agent-persona", "main",
     ["README.md", "docs/architecture.md"], "card", 16, "Apache-2.0",
     "工作区多persona：卡片的 CRUD 与存储设计"),
    ("MisakaZentai/dsh-persona-presets", "main",
     ["gokou-ruri/package.json", "neko/package.json", "vamp/package.json",
      "README.md"], "card", 1, "MIT",
     "三档性格预设：傲娇 / 猫娘 / 吸血鬼"),
    ("haimuhaimu/dsh-persona", "main",
     ["personas.js"], "card", 1, "MIT",
     "可切换并持久化的人格"),
    ("Rosynb666/dsh-jiahao-mode", "main",
     ["lib/persona.mjs"], "card", 21, "MIT",
     "人设互斥 + 三档语气（屏息 / 小声 / 放开）"),
    ("wumou-sleep/dsh-xiaojingyu-persona", "main",
     ["PERSONA.md"], "card", 0, "MIT",
     "傲娇小鲸鱼人设"),
    ("loonai321/dsh-humanized-deepseek-maid", "main",
     ["lib/index.mjs"], "card", 12, "MIT",
     "拟人化鲸鱼娘女仆人设"),
    ("chue114514/dsh-whale-girl-persona", "main",
     ["index.js"], "card", 0, "MIT",
     "俏皮 + 数码风鲸鱼娘人设（极简，每次仅 10~20 token）"),
    ("Sutera-Diffusus/dsh-whale-musume", "main",
     ["README.md"], "card", 214, "MIT",
     "元气鲸鱼娘桌宠：45 项成就 + 3 个小游戏 + 状态机（成就与玩法的现成设计）"),
    ("whyever77/dafeiyu-astrbot", "main",
     ["persona/大肥鱼-人格.md", "persona/课程记忆-模板.md"], "card", 869, "MIT",
     "★最高的大肥鱼人格原文（v2.1 黏人修订版，整段粘贴即用）"),

    # ---- proactive agent 插件：机制参考 ----
    ("Aik358/dsh-auto-memory", "main",
     ["README.md"], "proactive", 201, "BSD-3-Clause",
     "主动联想记忆：发言前先召回"),
    ("MichengAI/dsh-pua", "main",
     ["README.md"], "proactive", 73, "Apache-2.0",
     "角色风味 + 任务推进 + 验收循环"),
    ("john-walks-slow/dsh-proactive", "main",
     ["README.md"], "proactive", 16, "NOASSERTION",
     "模型自己排程后续动作"),
    ("PolinniZhong/dsh-personal-center", "main",
     ["README.md"], "proactive", 115, "MIT",
     "个人中心：用量 / 外观 / 桌宠状态总览"),
    ("2huy4n/ProjectKaren", "main",
     ["README.md"], "proactive", 31, "AGPL-3.0",
     "给角色一个时钟：按时间主动说话"),
    ("yyh-001/dsh-companion", "main",
     ["README.md"], "proactive", 14, "MIT",
     "陪伴模式：SOUL 人格 + 长期记忆"),
    ("beijingwahw/dsh-proactive", "main",
     ["README.md"], "proactive", 5, "MIT",
     "自主心跳 + 双心智"),
    ("LINYT1416/dsh-brain", "main",
     ["README.md"], "proactive", 4, "MIT",
     "会漂移的心情 + 记忆"),
    ("meow09nine/dsh-suinian", "main",
     ["README.md"], "proactive", 2, "MIT",
     "碎念：随机心跳 + 事件触发的主动消息"),
    ("shuaig22/dsh-proactive-chat", "main",
     ["README.md"], "proactive", 2, "MIT",
     "主动续话"),
    ("Yuuyuko-uu/dsh-fish-memory", "main",
     ["README.md"], "proactive", 520, "NOASSERTION",
     "会淡忘的长期记忆：每条带时间"),
    ("whyever77/dafeiyu-astrbot", "main",
     ["README.md",
      "deploy/memory_observatory/ROOM_V02.md",
      "deploy/memory_observatory/LIVINGMEMORY_V02.md"],
     "proactive", 869, "MIT",
     "大肥鱼 QQ 机器人：记忆 + 主动找你 + 视觉（活体记忆设计）"),
]


def fetch(repo, branch, path, tries=4):
    # 路径里有中文，必须逐段编码（人设原文.md 那种）
    quoted = "/".join(urllib.parse.quote(seg) for seg in path.split("/"))
    url = RAW % (repo, branch, quoted)
    for a in range(tries):
        try:
            req = urllib.request.Request(url, headers=UA)
            return urllib.request.urlopen(req, timeout=90).read()
        except Exception:
            if a == tries - 1:
                return None
            time.sleep(4 * (a + 1))
    return None


def default_branch(repo):
    try:
        req = urllib.request.Request(API % repo, headers=UA)
        return json.load(urllib.request.urlopen(req, timeout=40))["default_branch"]
    except Exception:
        return "main"


def stars(repo):
    try:
        req = urllib.request.Request(API % repo, headers=UA)
        return json.load(urllib.request.urlopen(req, timeout=40))["stargazers_count"]
    except Exception:
        return -1


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest-only", action="store_true")
    args = ap.parse_args()

    os.makedirs(OUT, exist_ok=True)
    rows = []
    for repo, branch, files, kind, star, lic, note in SOURCES:
        dest_dir = os.path.join(OUT, repo.replace("/", "__"))
        got = []
        if not args.manifest_only:
            os.makedirs(dest_dir, exist_ok=True)
            br = default_branch(repo) or branch
            for f in files:
                dst = os.path.join(dest_dir, f.replace("/", "__"))
                if os.path.exists(dst) and os.path.getsize(dst) > 0:
                    got.append((f, os.path.getsize(dst)))
                    continue
                data = fetch(repo, br, f)
                if data is None:
                    print("  MISS %s :: %s" % (repo, f))
                    continue
                with open(dst, "wb") as fp:
                    fp.write(data)
                got.append((f, len(data)))
            print("%-42s %d/%d 文件" % (repo, len(got), len(files)))
        else:
            dest_dir_local = os.path.join(OUT, repo.replace("/", "__"))
            for f in files:
                dst = os.path.join(dest_dir_local, f.replace("/", "__"))
                if os.path.exists(dst):
                    got.append((f, os.path.getsize(dst)))
        rows.append((repo, kind, star, lic, note, got, files))

    write_manifest(rows)


def write_manifest(rows):
    lines = [
        "# dsh 人格卡 / proactive 插件素材来源",
        "",
        "本目录是**素材源文件**，不参与编译，也不进 apk。",
        "收拢目的是：内置人格卡包的正文与字段结构参考来自 dsh 生态的**开源插件**，",
        "避免从零凭空写人设导致风格漂移。",
        "",
        "## 许可与署名",
        "",
        "- dsh 生态各仓库许可不一，逐条见下表。",
        "- **MIT / Apache-2.0 / BSD-3-Clause**：可参考、可再创作，保留署名与许可声明即可。",
        "- **NOASSERTION**：仓库未声明许可，**只做内部参考，不直接抄进产品**。",
        "- 大肥鱼形象本身来自 `PC2005-cloud/dsh-pet`，素材**禁止商用**，二创须署名。",
        "",
        "## 人格卡插件",
        "",
        "| 仓库 | ★ | 许可 | 收了什么 | 说明 |",
        "|---|---|---|---|---|",
    ]
    for repo, kind, star, lic, note, got, files in rows:
        if kind != "card":
            continue
        what = "、".join("`%s`" % f for f, _ in got) if got else "（未下载）"
        lines.append("| [%s](https://github.com/%s) | %d | %s | %s | %s |"
                     % (repo, repo, star, lic, what, note))

    lines += [
        "",
        "## proactive agent 插件",
        "",
        "| 仓库 | ★ | 许可 | 说明 |",
        "|---|---|---|---|",
    ]
    for repo, kind, star, lic, note, got, files in rows:
        if kind != "proactive":
            continue
        lines.append("| [%s](https://github.com/%s) | %d | %s | %s |"
                     % (repo, repo, star, lic, note))

    lines += [
        "",
        "## 使用约束",
        "",
        "1. **内置人格卡包的正文必须是 DeepFocus 自己写的** —— 照着 dsh 的结构写，",
        "   但人设内容要符合我们的大肥鱼设定（深海蓝 + 大白饭 + 专注陪伴），",
        "   不整段复制别人的文字。",
        "2. 字段结构（哪些字段、怎么分层）可以直接沿用 dsh 的设计，这属于方法论，不涉及抄袭。",
        "3. NOASSERTION 许可的仓库仅供内部阅读。",
        "",
    ]
    path = os.path.join(OUT, "MANIFEST.md")
    with open(path, "w", encoding="utf-8") as fp:
        fp.write("\n".join(lines))
    print("清单 ->", path)


if __name__ == "__main__":
    main()
