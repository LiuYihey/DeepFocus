# -*- coding: utf-8 -*-
"""校验白名单与素材尺寸的对应关系

**隔离判据是「归一化后的宽度 > 768」**，不是「短边小」——
短边只是素材摆放的副产品，跟能不能上台无关。

早期版本这里报的是「短边 min/max」，看着像「短边小就被隔离」，
其实隔离的是**横向摊得太开**的动作（躺着、挥手）——
归一化后宽度超过 768px，放不进 360dp 宽的屏，上台会显得小一号。

所以这里改成直接报**归一化宽度**，跟 gen_stage_catalog.py 用的
判据（MAX_WIDTH = 768）对得上。
"""
import json
import re
from pathlib import Path

ROOT = Path(r".")
D = ROOT / "app" / "src" / "main" / "assets" / "pet" / "anim" / "all"
BBOX = ROOT / "tools" / "media" / "anim_bbox.json"
CAT = ROOT / "app" / "src" / "main" / "java" / "com" / "deepfocus" / "app" / "pet" / "StageCatalog.kt"
PET = ROOT / "app" / "src" / "main" / "java" / "com" / "deepfocus" / "app" / "pet" / "PetAction.kt"

# 归一化基准：角色高度
CHAR_HEIGHT = 512
# 宽度上限：超过就放不进 360dp 宽的屏
MAX_WIDTH = 768

def parse_ids(src: str, name: str) -> set:
    """从 StageCatalog.kt 里抠出某个集合的 id"""
    m = re.search(rf"val {name}: Set<String> = setOf\((.*?)\n    \)", src, re.S)
    if not m:
        return set()
    return set(re.findall(r'"([^"]+)"', m.group(1)))


cat_src = CAT.read_text(encoding="utf-8")
stage = parse_ids(cat_src, "STAGE_ACTIONS")
shipped = parse_ids(cat_src, "SHIPPED_ACTIONS")

# 动作 id → 序号
idx_of = {}
pet_src = PET.read_text(encoding="utf-8")
for m in re.finditer(
        r'PetAction\(\s*"([^"]+)"\s*,\s*(\d+)\s*,\s*"[^"]*"\s*,\s*setOf\(([^)]*)\)', pet_src):
    idx_of[int(m.group(2))] = m.group(1)

# 序号 → 归一化宽度
norm_width = {}
for item in json.loads(BBOX.read_text(encoding="utf-8")):
    idx = int(re.match(r"^(\d+)_", item["name"]).group(1))
    gid = idx_of.get(idx)
    if not gid:
        continue
    x0, y0, x1, y1 = item["bbox"]
    cw, ch = x1 - x0, y1 - y0
    norm_width[gid] = round(CHAR_HEIGHT * cw / ch)

rows = []
for p in sorted(D.glob("*.webp")):
    if p.stem.endswith("_still"):
        continue
    gid = p.stem[4:]
    rows.append((gid, norm_width.get(gid), gid in stage, gid in shipped))

ons = [r for r in rows if r[2]]
offs = [r for r in rows if not r[2]]
print(f"舞台白名单 {len(stage)} 个；APK 素材 {len(rows)} 个（允许集合 {len(shipped)} 个）")
if ons:
    print(f"上台 {len(ons)}：归一化宽度 {min(r[1] for r in ons if r[1])} ~ "
          f"{max(r[1] for r in ons if r[1])}（上限 {MAX_WIDTH}）")
if offs:
    print(f"隔离但保留在 APK 里 {len(offs)}："
          f"归一化宽度 {min(r[1] for r in offs if r[1])} ~ "
          f"{max(r[1] for r in offs if r[1])}")

# 素材与白名单必须一一对上
files = {r[0] for r in rows}
missing_file = sorted(shipped - files)
if missing_file:
    print(f"\n❌ SHIPPED_ACTIONS 里有 {len(missing_file)} 个没有素材文件：{missing_file}")
extra_file = sorted(files - shipped)
if extra_file:
    print(f"\n❌ 素材目录里有 {len(extra_file)} 个不在 SHIPPED_ACTIONS：{extra_file}")
if stage - shipped:
    print(f"\n❌ STAGE_ACTIONS 有 {len(stage - shipped)} 个没进 APK：{sorted(stage - shipped)}")

# 白名单里不该有超过上限的
bad = [r for r in ons if r[1] and r[1] > MAX_WIDTH]
if bad:
    print(f"\n❌ 白名单里有 {len(bad)} 个超宽动作：{[r[0] for r in bad]}")
else:
    print(f"\n✅ 白名单里没有超宽动作（全部 ≤ {MAX_WIDTH}）")

print("\n隔离但保留在 APK 里的（归一化宽度从大到小）：")
for gid, w, _, _ in sorted(offs, key=lambda r: -(r[1] or 0))[:12]:
    print(f"  {gid[:42]:42} 归一化宽 {w}")
print("\n上台里最宽的（最容易撑爆屏的）：")
for gid, w, _, _ in sorted(ons, key=lambda r: -(r[1] or 0))[:6]:
    print(f"  {gid[:42]:42} 归一化宽 {w}")
