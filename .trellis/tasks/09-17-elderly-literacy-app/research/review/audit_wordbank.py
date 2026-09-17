# -*- coding: utf-8 -*-
"""审查 GLM 5.3 补充的词库（WordBank.kt）。

量化检查项：
  1. require() 守卫：text.length == pinyin 音节数 —— 不满足则 app 启动即崩
  2. id 唯一性 / id 前缀与 scene 是否一致
  3. 跨场景重复词条
  4. 拼音与 pypinyin 逐字比对（多音字 / 轻声 / 变调）
  5. 字集对 3500 常用字表 —— 超出表外的字
  6. 旧 36 词 id 保留率（决定是否丢学习记录）
  7. SCENES 定义 vs 词库实际使用的 scene
  8. 词条长度 / 提示语异常
输出：review/report.md（UTF-8）
"""
import csv
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pypinyin import Style, pinyin as pp

BASE = r"D:\QYJ\MyProject\Mando"
VB = os.path.join(BASE, "android-app", "app", "src", "main", "java", "com", "qyj", "shibang", "data", "WordBank.kt")
SD = os.path.join(BASE, "android-app", "app", "src", "main", "java", "com", "qyj", "shibang", "data", "StudyData.kt")
OLD = os.path.join(BASE, ".trellis", "tasks", "09-17-elderly-literacy-app", "research", "review", "old_StudyData.kt")
CHARLIST = os.path.join(BASE, ".trellis", "tasks", "09-17-elderly-literacy-app", "research", "vocab", "charlist_3500.csv")
OUT = os.path.join(BASE, ".trellis", "tasks", "09-17-elderly-literacy-app", "research", "review", "report.md")

freq_rank, top3500 = {}, {}
with open(CHARLIST, encoding="utf-8") as f:
    for r in csv.DictReader(f):
        freq_rank[r["char"]] = int(r["freq_rank"])
        top3500[r["char"]] = int(r["rank"])

vb = open(VB, encoding="utf-8").read()
sd = open(SD, encoding="utf-8").read()
old_src = open(OLD, encoding="utf-8").read()

TERM_RE = re.compile(r'term\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*\)')
terms = [dict(zip(("id", "scene", "text", "py", "tip"), m.groups())) for m in TERM_RE.finditer(vb)]

# SCENES 定义
scenes_def = re.findall(r'Scene\(\s*"([^"]+)"\s*,\s*"([^"]+)"', sd)
scene_ids = [s[0] for s in scenes_def]
scene_names = dict(scenes_def)

L = []
w = L.append
w("# WordBank.kt 审查报告\n")
w("> 对象：`android-app/.../data/WordBank.kt`（HEAD = ad32d06）")
w("> 方法：正则解析 368 条 `term(...)` + pypinyin 逐字比对 + 3500 字表核验\n")

w("## 0. 总览\n")
w("| 项 | 值 |")
w("|---|---|")
w("| 解析到词条数 | **%d** |" % len(terms))
w("| 场景数（词库中实际出现） | %d |" % len(set(t["scene"] for t in terms)))
w("| SCENES 中定义场景数 | %d |" % len(scene_ids))
w("| 去重汉字数 | %d |" % len({c for t in terms for c in t["text"]}))
w("")

# ---------- 1. require 守卫 ----------
w("## 1. require() 守卫（不满足 = 启动崩溃）\n")
bad_guard = []
for t in terms:
    n_py = len(t["py"].split(" "))
    if len(t["text"]) != n_py:
        bad_guard.append(t)
if bad_guard:
    w("**❌ 发现 %d 条不满足 `text.length == py.size`，会在类初始化时抛 IllegalArgumentException，app 启动即崩：**\n" % len(bad_guard))
    w("| id | 词条 | 字数 | 拼音 | 音节数 |")
    w("|---|---|---|---|---|")
    for t in bad_guard:
        w("| %s | %s | %d | %s | %d |" % (t["id"], t["text"], len(t["text"]), t["py"], len(t["py"].split(" "))))
else:
    w("✅ 全部 %d 条满足，不会触发启动崩溃。\n" % len(terms))

# ---------- 2. id ----------
w("## 2. id 唯一性与前缀\n")
ids = [t["id"] for t in terms]
dup_id = {i for i in ids if ids.count(i) > 1}
w("- id 总数 %d，唯一 id %d" % (len(ids), len(set(ids))))
if dup_id:
    w("- ❌ **重复 id：%s**（后出现的会覆盖前者，学习记录错乱）" % "、".join(sorted(dup_id)))
else:
    w("- ✅ 无重复 id")
pref_bad = [t for t in terms if not t["id"].startswith(t["scene"] + "-")]
if pref_bad:
    w("- ❌ id 前缀与 scene 不一致：%s" % "、".join("%s(%s)" % (t["id"], t["scene"]) for t in pref_bad))
else:
    w("- ✅ id 前缀与 scene 全部一致")
w("")

# ---------- 3. 重复词条 ----------
w("## 3. 跨场景重复词条\n")
bytext = {}
for t in terms:
    bytext.setdefault(t["text"], []).append(t)
dups = {k: v for k, v in bytext.items() if len(v) > 1}
if dups:
    w("**%d 个词条在多个场景重复出现。** 字本位下这是「主动利用重复」的资产（同字跨语境复现 = 间隔复习），" % len(dups))
    w("但同一份数据里重复的 `text` 会产生多张卡 —— 需确认是**有意设计**还是**疏漏**：\n")
    w("| 词条 | 出现位置 |")
    w("|---|---|")
    for k, v in sorted(dups.items(), key=lambda x: -len(x[1])):
        w("| %s | %s |" % (k, " + ".join("%s/%s" % (t["scene"], t["id"]) for t in v)))
else:
    w("✅ 无重复词条。")
w("")

# ---------- 4. 拼音比对 ----------
w("## 4. 拼音与 pypinyin 比对\n")
mismatch = []
for t in terms:
    mine = t["py"].split(" ")
    ref = [x[0] for x in pp(t["text"], style=Style.TONE)]
    if len(mine) != len(ref):
        continue
    for i, (a, b) in enumerate(zip(mine, ref)):
        if a != b:
            mismatch.append({"id": t["id"], "text": t["text"], "ch": t["text"][i],
                             "glm": a, "ref": b, "pos": i + 1})
if mismatch:
    w("**逐字比对发现 %d 处与 pypinyin 不一致**（pypinyin 按词组上下文取音，多音字可信度高；" % len(mismatch))
    w("差异需人工判定是「GLM 错」还是「轻声标记习惯不同」）：\n")
    w("| id | 词条 | 第几字 | 该字 | GLM 标注 | pypinyin | 字频名次 |")
    w("|---|---|---|---|---|---|---|")
    for m in mismatch:
        w("| %s | %s | %d | %s | %s | **%s** | %d |" % (
            m["id"], m["text"], m["pos"], m["ch"], m["glm"], m["ref"], freq_rank.get(m["ch"], 0)))
else:
    w("✅ 全部与 pypinyin 一致。")
w("")

# ---------- 5. 字集 vs 3500 ----------
w("## 5. 字集对 3500 常用字表\n")
chars = {c for t in terms for c in t["text"]}
outside = sorted(chars - set(top3500))
outside.sort(key=lambda c: freq_rank.get(c, 0))
w("- 去重汉字 %d 个" % len(chars))
w("- 落在 3500 常用字表内 %d 个（%.1f%%）" % (len(chars & set(top3500)), len(chars & set(top3500)) / len(chars) * 100))
w("- **超出字表 %d 个**" % len(outside))
if outside:
    w("")
    w("```")
    for i in range(0, len(outside), 20):
        w("  ".join(outside[i:i + 20]))
    w("```")
w("")

# 高频盲区（对照上轮 36 词）
w("### 5.1 相对旧 36 词的增量\n")
old_ids = re.findall(r'Term\(\s*"([^"]+)"', old_src)
old_terms = re.findall(r'Term\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"', old_src)
old_chars = {c for t in old_terms for c in t[3]}
w("| 项 | 值 |")
w("|---|---|")
w("| 旧词条数 | %d |" % len(old_ids))
w("| 旧去重字 | %d |" % len(old_chars))
w("| 新词条数 | %d |" % len(terms))
w("| 新增字 | **%d** |" % len(chars - old_chars))
w("")
w("### 5.2 字频前 200 名盲区（虚词缺口）\n")
ranked = sorted(top3500, key=lambda c: freq_rank.get(c, 99999))
top200 = [c for c in ranked if freq_rank.get(c, 99999) <= 200]
miss200 = [c for c in top200 if c not in chars]
w("- 前 200 名覆盖 %d / 200（%.1f%%），**盲区 %d 个**" % (
    len(top200) - len(miss200), (len(top200) - len(miss200)) / 2, len(miss200)))
w("")
w("```")
for i in range(0, min(120, len(miss200)), 20):
    w("  ".join(miss200[i:i + 20]))
w("```")
w("")

# ---------- 6. 旧 id 保留 ----------
w("## 6. 旧 36 词 id 保留率（决定是否丢学习记录）\n")
kept = [i for i in old_ids if i in set(ids)]
lost = [i for i in old_ids if i not in set(ids)]
w("- 旧 id 总数 %d，**保留 %d（%.0f%%）**，丢失 %d" % (len(old_ids), len(kept), len(kept) / len(old_ids) * 100 if old_ids else 0, len(lost)))
if lost:
    w("")
    w("- ❌ **丢失的旧 id（对应老人的已学记录会归零）**：")
    w("")
    w("```")
    w("  ".join(lost))
    w("```")
else:
    w("")
    w("- ✅ 全部保留。")
w("")

# ---------- 7. scene 一致性 ----------
w("## 7. scene 一致性\n")
used = {t["scene"] for t in terms}
w("- 词库使用的 scene：%s" % "、".join(sorted(used)))
w("- SCENES 定义的 id：%s" % "、".join(scene_ids))
undef = used - set(scene_ids)
unused = set(scene_ids) - used
if undef:
    w("- ❌ **词库引用了 SCENES 中未定义的 scene：%s** → `ICON.getValue(scene)` 会抛 NoSuchElementException" % "、".join(sorted(undef)))
else:
    w("- ✅ 词库引用的 scene 全部已在 SCENES 定义")
if unused:
    w("- ⚠️ SCENES 中定义了但没有词条的 scene：%s" % "、".join(sorted(unused)))
w("")

# ---------- 8. 词条形态 ----------
w("## 8. 词条形态\n")
lens = {}
for t in terms:
    lens[len(t["text"])] = lens.get(len(t["text"]), 0) + 1
w("| 字数 | 条数 |")
w("|---|---|")
for k in sorted(lens):
    w("| %d 字 | %d |" % (k, lens[k]))
w("")
longest = sorted(terms, key=lambda t: -len(t["text"]))[:12]
w("最长的 12 条（大字卡片排版风险）：\n")
w("| id | 词条 | 字数 |")
w("|---|---|---|")
for t in longest:
    w("| %s | %s | %d |" % (t["id"], t["text"], len(t["text"])))
w("")
short_tip = [t for t in terms if len(t["tip"]) < 8]
long_tip = [t for t in terms if len(t["tip"]) > 26]
w("- 提示语过短（<8 字）%d 条；过长（>26 字）%d 条" % (len(short_tip), len(long_tip)))
if long_tip:
    w("- 过长示例：%s" % "；".join("「%s」%s" % (t["text"], t["tip"]) for t in long_tip[:4]))
w("")

# ---------- 9. 每场景分布 ----------
w("## 9. 每场景词条数\n")
w("| scene | 名称 | 词条数 | 去重字 | 新增字 |")
w("|---|---|---|---|---|")
for s in scene_ids:
    st = [t for t in terms if t["scene"] == s]
    if not st:
        w("| %s | %s | 0 | - | - |" % (s, scene_names.get(s, "")))
        continue
    cc = {c for t in st for c in t["text"]}
    w("| %s | %s | %d | %d | %d |" % (s, scene_names.get(s, ""), len(st), len(cc), len(cc - old_chars)))
w("")

# ---------- 10. 两端同步 ----------
PROTO = os.path.join(BASE, "prototype", "index.html")
w("## 10. 两端同步（安卓 vs 网页原型）\n")
if os.path.exists(PROTO):
    html = open(PROTO, encoding="utf-8", errors="replace").read()
    proto_terms = re.findall(r"\{\s*id:\s*'([^']+)'", html)
    if not proto_terms:
        proto_terms = re.findall(r'"id"\s*:\s*"([^"]+)"', html)
    html_terms = [t for t in terms if t["text"] in html]
    w("| 项 | 安卓 WordBank.kt | 网页 prototype/index.html |")
    w("|---|---|---|")
    w("| 词条数 | %d | %d |" % (len(terms), len(proto_terms)))
    w("| 含有的新词条文本（抽样命中） | — | %d / %d |" % (len(html_terms), len(terms)))
    w("")
    missing = [t for t in terms if t["text"] not in html][:25]
    w("安卓有、网页**缺失**的词条（前 25 个抽样）：\n")
    w("```")
    w("  ".join(t["text"] for t in missing))
    w("```")
    w("")
else:
    w("⚠️ 未找到 prototype/index.html")

# ---------- 11. 补词条效率 ----------
w("## 11. 补词条效率（词本位路线的边际收益）\n")
n_new_terms = len(terms) - len(old_ids)
n_new_chars = len(chars - old_chars)
w("| 指标 | 数值 |")
w("|---|---|")
w("| 新增词条 | %d |" % n_new_terms)
w("| 新增汉字 | %d |" % n_new_chars)
w("| **每新增 1 词的净增字** | **%.2f** |" % (n_new_chars / n_new_terms if n_new_terms else 0))
w("| 累计覆盖 2500 常用字 | %.1f%% |" % (len(chars & set(top3500)) / 2500 * 100))
w("")
w("> 对照：上轮我实测「任务链」每任务约 70 去重字 / 35 净新增字（约 1.0 净增字/词）。")
w("> 两者效率接近，但**词本位补不到虚词** —— 见 5.2 的前 200 名盲区。\n")

open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(L))

print("terms=%d scenes_used=%d uniq_chars=%d outside3500=%d" % (
    len(terms), len(used), len(chars), len(outside)))
print("guard_violations=%d dup_ids=%d dup_texts=%d pinyin_mismatch=%d" % (
    len(bad_guard), len(dup_id), len(dups), len(mismatch)))
print("old_ids=%d kept=%d lost=%d new_chars=%d" % (len(old_ids), len(kept), len(lost), len(chars - old_chars)))
print("scene_undef=%s scene_unused=%s" % (sorted(undef), sorted(unused)))
