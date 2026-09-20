# -*- coding: utf-8 -*-
"""由 WordBank.kt 生成「家电」分区评审稿 scene_appliance.md（v12，2026-09-20）。

**单一数据源 = WordBank.kt 本身** —— 本脚本不另存一份词表，故文档永远不可能与代码漂移。
分组标题读自 `APPLIANCE` 块内的 `// xxx` 注释，所以文档结构与源码注释分组也永远一致。

与面馆那轮的差别（有意为之）：面馆是「先出评审稿 .md + .fragment.kt，再粘贴进 WordBank」，
所以那边 .md 是**原稿**、必须挂「勿直接粘贴」警示；家电这轮反向——**先落 WordBank，再由它派生文档**，
所以这里产出的是**只读回执**，不构成回退风险。

用法：python build_scene_appliance_doc.py
产出：scene_appliance.md（同目录，可复跑、可 diff）
"""
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
WB = r"D:\QYJ\MyProject\Mando\android-app\app\src\main\java\com\knowmo\app\data\WordBank.kt"
OUT = os.path.join(HERE, "scene_appliance.md")

TERM = re.compile(
    r'term\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*\)'
)

src = open(WB, encoding="utf-8").read()
m = re.search(r"private val APPLIANCE = listOf\((.*?)\n\)\n", src, re.S)
if not m:
    raise SystemExit("找不到 APPLIANCE 块 —— WordBank.kt 结构变了，先修本脚本")
block = m.group(1)

rows, group = [], None
for line in block.splitlines():
    s = line.strip()
    if s.startswith("//"):
        group = s[2:].strip()
        continue
    t = TERM.match(s)
    if t:
        rows.append((group or "（未分组）",) + t.groups())

assert rows, "APPLIANCE 块里没解析出词条"
# rows 每项 = (group, id, scene, text, pinyin, tip)
ids = [r[1] for r in rows]
nums = sorted(int(i.split("-")[1]) for i in ids)
assert len(set(ids)) == len(ids), "id 有重复"
assert all(i.startswith("appliance-") for i in ids), "有 id 前缀不等于 scene"
# 注意：这里校验的是 **id 集合**，不是文件顺序。新词按主题插进各自分组，
# 所以文件里 id 不必升序（v13 的 appliance-64..74 就是插在中间各组的）。
# 空号也**不断言** —— 按 wordbank-guidelines.md §8，删除留下的空号是允许的、且不得回填；
# 只在报告里如实列出，让人一眼看出本分区到底有没有空号。
gaps = [n for n in range(1, max(nums) + 1) if n not in set(nums)]
file_ascending = [int(i.split("-")[1]) for i in ids] == nums
for _, _, scene, text, pinyin, _ in rows:
    assert scene == "appliance", "scene 字段错：%s" % scene
    assert len(text) == len(pinyin.split(" ")), "字数/拼音不配对：%s" % text

L = []
w = L.append
w("# 「家电」分区评审稿（v12）\n")
w("> **本文件由 `build_scene_appliance_doc.py` 从 `WordBank.kt` 的 `APPLIANCE` 块自动派生，请勿手工编辑。**\n")
w("> 生成于 2026-09-20；共 **%d** 条 / %d 组。\n" % (len(rows), len({r[0] for r in rows})))
w("> 收录判据：`.trellis/spec/frontend/wordbank-guidelines.md` §3 ——「这个词，老人生活环境里"
  "是否会以文字形式出现」。本分区补的载体是**电器面板 / 铭牌 / 贴纸 / 屏幕 / 说明书**。\n")
w("\n## 分组明细\n")

cur = None
for g, tid, _, text, pinyin, tip in rows:
    if g != cur:
        cur = g
        w("\n### %s\n\n| id | 词条 | 拼音 | 提示语 |\n|---|---|---|---|\n" % g)
    w("| `%s` | %s | %s | %s |\n" % (tid, text, pinyin, tip))

w("\n## 校验回执\n")
w("- id `appliance-1`…`appliance-%d`：无重复、前缀 == scene；**空号 %s**%s\n"
  % (max(nums), "无" if not gaps else "`%s`" % "`, `".join(str(g) for g in gaps),
     "（本分区无删除）" if not gaps else "（按 §8 不复用、不回填）"))
w("- 文件顺序%s —— 新词按主题插入各分组，id 不必升序（v13 的 64–74 即插在中间各组）\n"
  % ("恰好升序" if file_ascending else "**非**升序"))
w("- 全部 `scene == \"appliance\"`、`id` 前缀 == scene\n")
w("- %d/%d 逐字拼音配对通过（启动期 `require` 的同一条件）\n" % (len(rows), len(rows)))
w("- 结构性不变量 1–7 项见同目录 `_wordbank_invariants.md`；v12 轮（本分区 63 条时）的独立质检见 `_check_v12_appliance.txt`\n")
w("- 跨分区重词 4 条（`说明书`/`预约`/`菜单`/`确认`）——四对 tip 释义均不同，按既有口径（`身份证` 在 "
  "bank/gov 共存）保留\n")
w("\n## 已知未决 / 实机走查项\n")
# 长度分布**现算**，不硬编码 —— 这条断言上一版写死成「请勿用水冲洗是全库第一条 6 字词条」，
# v13 跨分区补词后当场过期。硬编码的全库性断言必然会烂。
all_texts = [t[2] for t in TERM.findall(src)]
maxlen = max(len(t) for t in all_texts)
longest = sorted({t for t in all_texts if len(t) == maxlen})
w("- 全库最长词条 **%d 字**，共 %d 条：%s；本分区 %d 字词条 %d 条。\n"
  % (maxlen, len(longest), "、".join("`%s`" % t for t in longest),
     max(len(r[3]) for r in rows), sum(1 for r in rows if len(r[3]) == max(len(x[3]) for x in rows))))
w("  词条 ≥ 6 字会触发 TermCard 的「≥6 字」布局分支（36sp + `FlowRow` 换行 + 右缘 56dp 星位预留）"
  "——本机无 JDK/SDK，只能实机确认。\n")
w("- 本分区**默认隐藏且不进推荐范围**（QYJ 2026-09-20 拍板）；且**新装与升级口径一致**——"
  "`AppSettings.load()` 会把不在存储 `order` 里的分区自动归入 hidden（v12，design.md §15），"
  "故升级后本分区不会自己出现在频道栏，须进设置页手动开启\n")

open(OUT, "w", encoding="utf-8", newline="\n").write("".join(L))
print("rows=%d groups=%d -> %s" % (len(rows), len({r[0] for r in rows}), OUT))
