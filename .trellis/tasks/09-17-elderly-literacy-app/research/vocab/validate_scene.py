# -*- coding: utf-8 -*-
"""面馆词条「创作原稿」校验：字数/拼音配对、id 唯一性、跨库重词、pypinyin 对照、常用字表覆盖。

⚠️ 校验对象是**原稿** `scene_noodle.fragment.kt`（独立分区 `noodle` 方案，**已撤销**）。
   原稿沿用 368 词基线，所以本脚本报的「合计 402 条」「待插入 SCENES」都**不是现行状态**。
   现行落位 = 33 条并入 `food`（`food-33`…`food-65`），见 scene_noodle.md §6；
   现役检查脚本 = `check_wordbank_invariants.py`。

用法：python validate_scene.py
产出：_scene_noodle_check.md（同目录，原稿快照）
"""
import re
import os
import csv
from pypinyin import pinyin, Style

HERE = os.path.dirname(os.path.abspath(__file__))
FRAG = os.path.join(HERE, "scene_noodle.fragment.kt")
WB = r"D:\QYJ\MyProject\Mando\android-app\app\src\main\java\com\knowmo\app\data\WordBank.kt"
CHARLIST = os.path.join(HERE, "charlist_3500.csv")
OUT = os.path.join(HERE, "_scene_noodle_check.md")

PAT = re.compile(
    r'term\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*\)'
)


def parse(path):
    src = open(path, encoding="utf-8").read()
    rows = []
    for m in PAT.finditer(src):
        tid, scene, text, py, tip = m.groups()
        rows.append(dict(id=tid, scene=scene, text=text, glp=py.split(" "), tip=tip))
    return rows


new = parse(FRAG)
old = parse(WB)

L = []
w = L.append
w("# 面馆词条原稿校验\n")
w("> 自动生成，脚本 `validate_scene.py`（可复跑）。")
w("> ⚠️ **原稿快照**：校验对象是 `scene_noodle.fragment.kt`（独立分区方案，**已撤销**）。")
w("> 下面的「现有词库」读的是**现行** `WordBank.kt`，**其中已包含本原稿并入的 33 条**，")
w("> 所以第 4 节的重词表是**预期的自比**（证明并入成功），不是待修缺陷。")
w("> 现行落位见 `scene_noodle.md` §6；现役检查脚本 = `check_wordbank_invariants.py`。\n")
w("原稿 %d 条；**现行**词库 %d 条（已含并入的 33 条）。原稿不完整并入——「微辣」被剔除。\n"
  % (len(new), len(old)))

fail = []

# ---------- 1. 字数 / 拼音配对（启动期 require 的同一条件） ----------
w("## 1. 字数与拼音音节数配对（对应 `term()` 里的 `require`）\n")
bad = [r for r in new if len(r["text"]) != len(r["glp"])]
if bad:
    for r in bad:
        fail.append("配对不符 %s %s" % (r["id"], r["text"]))
    w("| id | 词条 | 字数 | 音节数 |\n|---|---|---|---|")
    for r in bad:
        w("| %s | %s | %d | %d |" % (r["id"], r["text"], len(r["text"]), len(r["glp"])))
else:
    w("**%d 条全部通过** —— 逐字与拼音一一对应，启动不会抛 `词条 xxx 拼音与字数不符`。\n" % len(new))

# ---------- 2. id 唯一性 ----------
w("## 2. id 唯一性\n")
ids = [r["id"] for r in new]
dup_in = sorted({i for i in ids if ids.count(i) > 1})
old_ids = {r["id"] for r in old}
clash = sorted(set(ids) & old_ids)
if dup_in or clash:
    fail.append("id 冲突 %s %s" % (dup_in, clash))
    w("- 组内重复：`%s`\n- 与现有库冲突：`%s`\n" % (dup_in, clash))
else:
    w("**通过** —— 34 个 id 组内无重复，与现有 %d 个 id 无冲突。\n" % len(old_ids))

# ---------- 3. 场景字段一致性 ----------
w("## 3. scene 字段一致性\n")
scenes = {r["scene"] for r in new}
if scenes == {"noodle"}:
    w("**通过** —— 34 条 scene 全为 `noodle`（原方案的分区 id；**该分区已撤销**，现落位为 `food`）。\n")
else:
    fail.append("scene 字段异常 %s" % scenes)
    w("异常：%s\n" % sorted(scenes))

# ---------- 4. 词条文本重复 ----------
w("## 4. 词条文本重复检查\n")
new_texts = [r["text"] for r in new]
dup_t = sorted({t for t in new_texts if new_texts.count(t) > 1})
old_texts = {r["text"]: r["id"] for r in old}
cross = sorted(set(new_texts) & set(old_texts))
if dup_t:
    fail.append("组内重词 %s" % dup_t)
    w("- 组内重复：%s\n" % dup_t)
else:
    w("- 组内重复：无\n")
if cross:
    w("- 与**现行**库重词 —— 原稿已并入 `food`，故这里的每一行都是「并入成功」的自比证据，不是缺陷：\n")
    w("| 词条 | 现有 id |\n|---|---|")
    for t in cross:
        w("| %s | %s |" % (t, old_texts[t]))
    w("")
else:
    w("- 与现有库重词：无（本分区 34 词均为新面孔）\n")

# ---------- 5. pypinyin 对照 ----------
w("## 5. 与 pypinyin 0.55 的逐字对照\n")
TONE = re.compile(r"[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]")
diffs = []
for r in new:
    if len(r["text"]) != len(r["glp"]):
        continue
    p = [x[0] for x in pinyin(r["text"], style=Style.TONE)]
    if len(p) != len(r["text"]):
        continue
    for i, ch in enumerate(r["text"]):
        if r["glp"][i] != p[i]:
            diffs.append((r["id"], r["text"], ch, r["glp"][i], p[i]))
if diffs:
    w("| id | 词条 | 字 | 本词库 | pypinyin |\n|---|---|---|---|---|")
    for d in diffs:
        w("| %s | %s | %s | %s | %s |" % d)
    w("\n> 差异**不等于错**：本库约定「无调号 = 轻声」，且 `炸/青/担` 等字有口语专读，逐条人工判定（见 `scene_noodle.md` §5）。\n")
else:
    w("**逐字全等**，无需人工判定。\n")

# ---------- 6. 无声调（轻声候选）清单 ----------
w("## 6. 无声调拼音清单（按约定即轻声，须确认真为轻声）\n")
toneless = [(r["id"], r["text"], r["text"][i], r["glp"][i])
            for r in new for i in range(min(len(r["text"]), len(r["glp"])))
            if r["glp"][i] and not TONE.search(r["glp"][i])]
if toneless:
    w("| id | 词条 | 字 | 标音 |\n|---|---|---|---|")
    for t in toneless:
        w("| %s | %s | %s | %s |" % t)
    w("")
else:
    w("**无** —— 34 条 103 字全部带调号，无「漏标调号」风险（历史缺陷正是漏调号，如「早晨 zǎo chen」）。\n")

# ---------- 7. 常用字覆盖 ----------
w("## 7. 常用字表覆盖（3500 常用字）\n")
common = set()
if os.path.exists(CHARLIST):
    with open(CHARLIST, encoding="utf-8-sig", newline="") as f:
        for row in csv.reader(f):
            for cell in row[1:]:
                cell = cell.strip()
                if len(cell) == 1 and "\u4e00" <= cell <= "\u9fff":
                    common.add(cell)
chars = sorted({c for r in new for c in r["text"] if "\u4e00" <= c <= "\u9fff"})
if common:
    outside = [c for c in chars if c not in common]
    w("本分区去重汉字 %d 个；其中不在 3500 常用字表内的：%s\n"
      % (len(chars), ("`" + "` `".join(outside) + "`") if outside else "无"))
    if outside:
        w("> 超纲字需逐个人工判断是否保留（生僻字对识字少的老人是负担）。\n")
else:
    w("（未找到 `charlist_3500.csv`，跳过）\n")

# ---------- 8. 结论 ----------
w("## 8. 结论\n")
if fail:
    w("**存在阻断项**：\n")
    for f in fail:
        w("- %s" % f)
    w("")
else:
    w("结构性校验（配对 / id / scene / 组内重词）**全部通过** —— 原稿本身没有结构性缺陷。\n")
    w("> 注意：这不等于「可以按原稿接入」。原稿的独立分区方案已撤销，见 `scene_noodle.md` §6。\n")

open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(L))
print("new=%d old=%d fail=%d diffs=%d toneless=%d"
      % (len(new), len(old), len(fail), len(diffs), len(toneless)))
print("->", OUT)
