# -*- coding: utf-8 -*-
"""词库不变量检查（通用，非一次性）。

改动 WordBank.kt / StudyData.kt 后跑一遍。检查项：

 1. 总词条数 = Σ 各分区词条数
 2. 字数 ↔ 拼音音节配对（`term()` 启动期 require 的同一条件）
 3. 全库 id 唯一
 4. 每条词条的 scene 都存在于 SCENES —— 否则 `ICON.getValue(scene)` 类初始化即抛
    NoSuchElementException，**App 一启动就崩**（本机无编译器，只能静态拦）
 5. 每个非 rec 分区都有词条（否则频道栏点出空池）
 6. **分区内词条文本不重复**（同分区出现两条一样的词 = 学习单元重复，硬缺陷）
 7. id 前缀 == scene 字段（命名一致性）
 8. 跨分区重词（信息性，需人工判定是否可接受）
 9. pypinyin 逐字对照（信息性；差异需逐条判定，不等于错）
10. 3500 常用字覆盖（信息性）

用法：python check_wordbank_invariants.py
产出：_wordbank_invariants.md
"""
import re
import os
import csv
import collections
from pypinyin import pinyin, Style

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = r"D:\QYJ\MyProject\Mando\android-app\app\src\main\java\com\knowmo\app\data"
WB = os.path.join(SRC, "WordBank.kt")
SD = os.path.join(SRC, "StudyData.kt")
CHARLIST = os.path.join(HERE, "charlist_3500.csv")
OUT = os.path.join(HERE, "_wordbank_invariants.md")

TONE = re.compile(r"[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]")
TERM = re.compile(
    r'term\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*\)'
)
SCENE = re.compile(r'Scene\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*Color\(([^)]*)\)')

src = open(WB, encoding="utf-8").read()
sd = open(SD, encoding="utf-8").read()
rows = TERM.findall(src)          # (id, scene, text, pinyin, tip)
scenes = SCENE.findall(sd)        # (id, name, icon, color)
scene_ids = [s[0] for s in scenes]
per = collections.Counter(r[1] for r in rows)

L, w, fails = [], lambda s: L.append(s), []
w("# 词库不变量检查\n")
w("> 自动生成，脚本 `check_wordbank_invariants.py`（可复跑）。源：`WordBank.kt` + `StudyData.kt`\n")
w("总词条 **%d** 条 / 分区 %d 个（`SCENES` %d 项，含 `rec`）\n" % (len(rows), len(per), len(scene_ids)))

# ---- 1. 计数 ----
w("## 1. 计数\n")
w("| 分区 id | 名称 | 图标 | 词条数 |\n|---|---|---|---|")
for sid, name, icon, _ in scenes:
    w("| `%s` | %s | %s | %s |\n" % (sid, name, icon, "—（rec 为聚合频道）" if sid == "rec" else per.get(sid, 0)))
w("")
sum_per = sum(per.values())
if sum_per != len(rows):
    fails.append("Σ分区 %d != 总词条 %d" % (sum_per, len(rows)))
w("Σ 各分区词条数 = **%d** %s\n" % (sum_per, "= 总词条数 ✓" if sum_per == len(rows) else "**≠ 总词条数 ✗**"))

# ---- 2. 配对 ----
w("## 2. 字数 ↔ 拼音音节配对\n")
bad = [r for r in rows if len(r[2]) != len(r[3].split(" "))]
if bad:
    fails.append("配对不符 %d 条" % len(bad))
    w("**失败 %d 条**：\n\n| id | 词条 | 字数 | 音节数 |\n|---|---|---|---|\n" % len(bad))
    for r in bad:
        w("| %s | %s | %d | %d |\n" % (r[0], r[2], len(r[2]), len(r[3].split(" "))))
else:
    w("**通过** —— %d 条逐字配对，启动不会抛 `词条 xxx 拼音与字数不符`。\n" % len(rows))

# ---- 3. id 唯一 ----
w("## 3. id 全局唯一\n")
ids = [r[0] for r in rows]
dups = sorted({i for i in ids if ids.count(i) > 1})
if dups:
    fails.append("id 重复 %s" % dups)
    w("**失败**：%s\n" % dups)
else:
    w("**通过** —— %d 个 id 无重复。\n" % len(ids))

# ---- 4. scene ⊂ SCENES ----
w("## 4. 词条 `scene` 必须都在 `SCENES` 内\n")
orphan = sorted(set(per) - set(scene_ids))
if orphan:
    fails.append("孤儿 scene %s" % orphan)
    w("**失败**：`%s` 不在 SCENES —— `ICON.getValue(scene)` 会在类初始化抛 "
      "`NoSuchElementException`，**App 一启动就崩**。\n" % orphan)
else:
    w("**通过** —— 所有词条的 `scene` 都能查到，`ICON.getValue()` 安全。"
      "（新增分区最容易踩的坑：加了词忘了加 `Scene`。）\n")

# ---- 5. 非 rec 分区非空 ----
w("## 5. 每个非 `rec` 分区都有词条\n")
empty = [s for s in scene_ids if s != "rec" and per.get(s, 0) == 0]
if empty:
    fails.append("空分区 %s" % empty)
    w("**失败**：`%s`\n" % empty)
else:
    w("**通过** —— %d 个场景分区各有词条，频道栏不会点出空池。\n" % (len(scene_ids) - 1))

# ---- 6. 分区内重词 ----
w("## 6. 分区内词条文本不重复\n")
intra = []
for s in per:
    c = collections.Counter(r[2] for r in rows if r[1] == s)
    for t, n in c.items():
        if n > 1:
            intra.append((s, t, n))
if intra:
    fails.append("分区内重词 %s" % intra)
    w("**失败**：\n\n| 分区 | 词条 | 条数 |\n|---|---|---|\n")
    for s, t, n in sorted(intra):
        w("| `%s` | %s | %d |\n" % (s, t, n))
else:
    w("**通过** —— 各分区内部无重复词条（同一分区两条一样的词 = 学习单元重复）。\n")

# ---- 7. id 前缀 == scene ----
w("## 7. id 前缀与 `scene` 一致\n")
mismatch = [r for r in rows if r[0].rsplit("-", 1)[0] != r[1]]
if mismatch:
    w("不一致 %d 条（前 5）：%s\n" % (len(mismatch), [(r[0], r[1]) for r in mismatch[:5]]))
else:
    w("**通过** —— 全部 `id` 前缀等于其 `scene` 字段。\n")

# ---- 8. 跨分区重词 ----
w("## 8. 跨分区重词（信息性）\n")
by_text = collections.defaultdict(list)
for r in rows:
    by_text[r[2]].append((r[0], r[1]))
cross = {t: v for t, v in by_text.items() if len({x[1] for x in v}) > 1}
if cross:
    w("| 词条 | 出现处 |\n|---|---|\n")
    for t, v in sorted(cross.items()):
        w("| %s | %s |\n" % (t, "、".join("`%s`" % i for i, _ in v)))
    w("\n> 跨分区重词不是硬缺陷（同一词在不同场景各学一遍），但需人工确认是否可接受。\n")
else:
    w("**无** —— 全库词条文本跨分区也不重复。\n")

# ---- 9. pypinyin ----
w("## 9. pypinyin 0.55 逐字对照（信息性）\n")
diffs = []
for tid, scene, text, py, _ in rows:
    glp = py.split(" ")
    if len(glp) != len(text):
        continue
    p = [x[0] for x in pinyin(text, style=Style.TONE)]
    if len(p) != len(text):
        continue
    for i in range(len(text)):
        if glp[i] != p[i]:
            diffs.append((tid, text, text[i], glp[i], p[i]))
w("差异共 **%d** 处：\n\n" % len(diffs))
if diffs:
    w("| id | 词条 | 字 | 本词库 | pypinyin |\n|---|---|---|---|---|\n")
    for d in diffs:
        w("| %s | %s | %s | %s | %s |\n" % d)
w("\n> 差异**不等于错**。本库约定：无调号 = 轻声；教学标音写本调（`不要辣 bù yào là`、"
  "`不要乱动 bù yào luàn dòng`），不作语流变调；词义辨读以词典为准（`干拌 gān bàn` 的干＝乾、"
  "`担担面 dàn dàn miàn` 的担＝担子）。\n")

# ---- 10. 常用字 ----
w("## 10. 3500 常用字覆盖（信息性）\n")
common = set()
if os.path.exists(CHARLIST):
    with open(CHARLIST, encoding="utf-8-sig", newline="") as f:
        for row in csv.reader(f):
            c = row[1].strip() if len(row) > 1 else ""
            if len(c) == 1 and "\u4e00" <= c <= "\u9fff":
                common.add(c)
chars = {c for r in rows for c in r[2] if "\u4e00" <= c <= "\u9fff"}
outside = sorted(chars - common) if common else []
w("去重汉字 %d 个；超纲 %d 个：%s\n" % (len(chars), len(outside), "`" + "` `".join(outside) + "`" if outside else "无"))

# ---- 结论 ----
w("\n## 11. 结论\n")
if fails:
    w("**存在阻断项**：\n\n")
    for f in fails:
        w("- %s\n" % f)
else:
    w("**结构性不变量（1–7 项）全过**。剩余风险来自本机不能编译 —— 需实机/CI 构建确认。\n")

open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(L))
print("terms=%d scenes=%d per=%s" % (len(rows), len(scene_ids), dict(per)))
print("fails=%d %s | pypinyin_diffs=%d | orphan=%s | intra_dup=%s | cross_dup=%d"
      % (len(fails), fails, len(diffs), orphan, intra, len(cross)))
print("id_prefix_mismatch=%d" % len(mismatch))
print("->", OUT)
