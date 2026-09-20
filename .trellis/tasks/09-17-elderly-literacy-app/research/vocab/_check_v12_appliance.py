# -*- coding: utf-8 -*-
"""v12 家电分区独立质检（只读，不改任何源文件）。

检查：
 1. appliance id 连续性 1..63（无跳号、无重复）
 2. appliance 每条 scene 字段 == "appliance"
 3. 跨分区重词 4 组的对照 tip（确认两边释义不同）
 4. SCENES 颜色/图标/名称唯一性（appliance 新增色 0xFFF0F4C3 是否被复用）
 5. SCENES 顺序（appliance 是否插在 property 与 emergency 之间）
 6. 图标码点（🔌 是否在 API 26 可渲染范围）
"""
import os
import re
import collections
from pypinyin import pinyin, Style

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = r"D:\QYJ\MyProject\Mando\android-app\app\src\main\java\com\knowmo\app\data"
WB = os.path.join(SRC, "WordBank.kt")
SD = os.path.join(SRC, "StudyData.kt")
OUT = os.path.join(HERE, "_check_v12_appliance.txt")

TERM = re.compile(
    r'term\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*\)'
)
SCENE = re.compile(r'Scene\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*Color\(([^)]*)\)')

src = open(WB, encoding="utf-8").read()
sd = open(SD, encoding="utf-8").read()
rows = TERM.findall(src)
scenes = SCENE.findall(sd)

L = []
w = lambda s: L.append(s)

# 1/2 ----
appl = [r for r in rows if r[1] == "appliance"]
nums = sorted(int(r[0].split("-")[1]) for r in appl)
w("== 1. appliance id 连续性 ==")
w("count=%d  min=%d  max=%d" % (len(nums), nums[0], nums[-1]))
w("连续性 1..63 无跳号: %s" % (nums == list(range(1, 64))))
w("id 重复: %s" % [k for k, v in collections.Counter(nums).items() if v > 1])
w("scene 字段非 appliance 的条目: %s" % [r[0] for r in appl if r[1] != "appliance"])
w("id 前缀 != scene: %s" % [r[0] for r in appl if r[0].rsplit("-", 1)[0] != r[1]])
w("")

# 3 ----
w("== 3. 跨分区重词逐条对比（tip 是否真的不同） ==")
by_text = collections.defaultdict(list)
for r in rows:
    by_text[r[2]].append(r)
for t, v in sorted(by_text.items()):
    if len({x[1] for x in v}) > 1:
        w("* %s" % t)
        for r in v:
            w("    %-14s scene=%-10s pinyin=%-20s tip=%s" % (r[0], r[1], r[3], r[4]))
        tips = {r[4] for r in v}
        w("    tip 全同? %s" % (len(tips) == 1))
w("")

# 4/5/6 ----
w("== 4/5/6. SCENES 结构 ==")
w("SCENES 项数=%d（含 rec） -> 场景分区=%d" % (len(scenes), len(scenes) - 1))
ids = [s[0] for s in scenes]
w("顺序: %s" % " | ".join(ids))
w("appliance 下标=%d（property=%d emergency=%d）" % (
    ids.index("appliance"), ids.index("property"), ids.index("emergency")))
w("顺序断言（property < appliance < emergency）: %s"
  % (ids.index("property") < ids.index("appliance") < ids.index("emergency")))
for label, idx, dup in (("id", 0, None), ("name", 1, None), ("icon", 2, None), ("color", 3, None)):
    c = collections.Counter(s[idx] for s in scenes)
    w("%s 重复: %s" % (label, {k: v for k, v in c.items() if v > 1}))
icon = [s[2] for s in scenes if s[0] == "appliance"][0]
w("appliance icon=%s codepoints=%s" % (icon, ["U+%04X" % ord(ch) for ch in icon]))
color = [s[3] for s in scenes if s[0] == "appliance"][0]
w("appliance color=%s" % color)
w("")

# pypinyin 只看 appliance ----
w("== 7. appliance pypinyin 逐字对照（应为 0 处） ==")
d = []
for tid, scene, text, py, _ in appl:
    glp = py.split(" ")
    p = [x[0] for x in pinyin(text, style=Style.TONE)]
    if len(glp) != len(text) or len(p) != len(text):
        d.append((tid, "长度不符", text, py, " ".join(p)))
        continue
    for i in range(len(text)):
        if glp[i] != p[i]:
            d.append((tid, text, text[i], glp[i], p[i]))
w("差异 %d 处: %s" % (len(d), d))
w("")

# 8 无调号（轻声）逐条 ----
w("== 8. appliance 无声调音节（=轻声称谓）逐条 ==")
TONE = re.compile(r"[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]")
silent = []
for tid, scene, text, py, _ in appl:
    for i, syl in enumerate(py.split(" ")):
        if not TONE.search(syl):
            silent.append((tid, text, text[i], syl))
w("无声调 %d 处: %s" % (len(silent), silent))

open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(L))
print("OK ->", OUT)
