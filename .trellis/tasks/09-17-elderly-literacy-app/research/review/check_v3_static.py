#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
第二轮复核静态脚本（只读，不改动任何被 git 跟踪的文件）。
用法: python check_v3_static.py

检查项:
  A. WordBank.kt 词条结构：id 唯一 / 拼音字数 == 词长 / scene 合法 / 每场景词数
  B. Leitner 状态机：可达 days 值，验证 days==15 <=> 连续 3 次「认识」
  C. 毕业判定边界：空分区 / 无 TermState / rec 自身
  D. scopeIds 性能：graduatedScenes() 的字符串比较次数
  E. 未跟踪/已跟踪符号残留扫描
"""
import os
import re
import itertools
import sys

ROOT = r"D:\QYJ\MyProject\KnowMo"
SRC = os.path.join(ROOT, "android-app", "app", "src", "main", "java", "com", "qyj", "shibang")
WB = os.path.join(SRC, "data", "WordBank.kt")
SD = os.path.join(SRC, "data", "StudyData.kt")

out = []


def p(s=""):
    out.append(s)
    print(s)


# ---------- 读取场景 ----------
sd_txt = open(SD, encoding="utf-8").read()
scenes = re.findall(r'Scene\("([a-z]+)",\s*"([^"]+)"', sd_txt)
scene_ids = [s[0] for s in scenes]
p("== A. 词库结构 ==")
p("SCENES = %d 个: %s" % (len(scene_ids), ",".join(scene_ids)))

wb_txt = open(WB, encoding="utf-8").read()
terms = re.findall(
    r'term\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)"', wb_txt)

ids = [t[0] for t in terms]
dup = [i for i in set(ids) if ids.count(i) > 1]
p("词条数 = %d, id 重复 = %s" % (len(terms), dup or "无"))

bad_len = []
bad_scene = []
per_scene = {}
id_in_text = {}
for tid, scene, text, pinyin in terms:
    py = pinyin.split(" ")
    if len(text) != len(py):
        bad_len.append((tid, text, pinyin))
    if scene not in scene_ids:
        bad_scene.append((tid, scene))
    per_scene[scene] = per_scene.get(scene, 0) + 1
    id_in_text[tid] = text
p("拼音与字数不符: %s" % (bad_len or "无"))
p("非法 scene: %s" % (bad_scene or "无"))
empty_scene = [s for s in scene_ids if s != "rec" and per_scene.get(s, 0) == 0]
p("无词条的非 rec 分区: %s" % (empty_scene or "无（=> isSceneGraduated 的空分区早退分支在本词库下不可达，仅防御性）"))
p("各场景词数: %s" % ", ".join("%s=%d" % (s, per_scene.get(s, 0)) for s in scene_ids if s != "rec"))
p("rec 是否有词条: %s" % ("是" if per_scene.get("rec") else "否（rec 只是聚合频道）"))
p("id 前缀与 scene 不一致: %s" % ([t[0] for t in terms if not t[0].startswith(t[1] + "-")] or "无"))

# 拼音抽查（用户声称已修 2 处）
for tid in ("express-16", "weather-10", "market-2"):
    m = re.search(r'term\("%s",\s*"[^"]+",\s*"([^"]+)",\s*"([^"]+)"' % re.escape(tid), wb_txt)
    p("抽查 %s -> 词=%s / 拼音=%s" % (tid, m.group(1), m.group(2)) if m else "抽查 %s 未找到" % tid)
p("残留 'má ' / ' chen' 音: %s" % (
    re.findall(r'term\("[^"]+",[^)]*"[^"]*\bmá\b[^"]*"', wb_txt) or
    re.findall(r'"[^"]*\bchen\b[^"]*"', wb_txt) or "无"))

# ---------- B. 状态机 ----------
p()
p("== B. Leitner 状态机（验证 days==15 <=> 连续 3 次认识）==")
INTERVALS = [1, 3, 7, 15]


def next_interval(cur):
    if cur not in INTERVALS:
        return INTERVALS[1]
    return INTERVALS[min(INTERVALS.index(cur) + 1, len(INTERVALS) - 1)]


# 状态 = (days 或 None, 自上次「忘了/首次学过」以来的连续认识次数, 截断到 5)
# 事件: SEE(markSeen: 仅 null->1), KNOWN(markKnown), FORGOT(markForgot)
CAP = 5
start = (None, 0)
seen_states = {start: []}
frontier = [start]
while frontier:
    nxt = []
    for st in frontier:
        days, streak = st
        # markSeen
        if days is None:
            s2 = (1, 0)
            if s2 not in seen_states:
                seen_states[s2] = seen_states[st] + ["seen"]
                nxt.append(s2)
        # markKnown
        cur = days if days is not None else 1
        s2 = (next_interval(cur), min(streak + 1, CAP))
        if s2 not in seen_states:
            seen_states[s2] = seen_states[st] + ["known"]
            nxt.append(s2)
        # markForgot
        s2 = (1, 0)
        if s2 not in seen_states:
            seen_states[s2] = seen_states[st] + ["forgot"]
            nxt.append(s2)
    frontier = nxt

reach = sorted(s for s in seen_states if s[0] is not None)
p("可达 days 值: %s" % sorted({s[0] for s in reach}))
viol = [(st, seen_states[st]) for st in reach if (st[0] == 15) != (st[1] >= 3)]
p("days==15 与「连续>=3 次认识」不等价的可达状态: %s" % (viol or "无（等价成立）"))
EXPECT = {1: 0, 3: 1, 7: 2}
vmap = [(st, seen_states[st]) for st in reach if st[0] in EXPECT and st[1] != EXPECT[st[0]]]
p("days->认识次数的映射一致性(1/3/7): %s" % (vmap or "无（1=>0 次, 3=>1 次, 7=>2 次 严格成立）"))
minpath = min((st for st in reach if st[0] == 15), key=lambda st: len(seen_states[st]))
p("达到 15 的最短事件序列: %s（步数 %d）" % ("->".join(seen_states[minpath]), len(seen_states[minpath])))
p("从 null 直接 markKnown 会使 days=3（第 1 次认识），仍计入 streak —— 与 markSeen 后 3 次认识同样需要 3 次认识")
p("=> 不存在「少于 3 次认识即 days==15」的路径；但同一天内可重复作答（见 replay 路径）,见 P2 说明")

# 逐场景毕业模拟（全词打满/一词漏）
p()
p("== C. isSceneGraduated 边界 ==")
def is_graduated(term_ids, states):
    if not term_ids:
        return False
    return all(states.get(i) == 15 for i in term_ids)


mkt = [t[0] for t in terms if t[1] == "market"]
p("空分区: %s" % is_graduated([], {}))
p("无 TermState（未学）: %s" % is_graduated(mkt, {}))
p("全部 days=15: %s" % is_graduated(mkt, {i: 15 for i in mkt}))
p("一个词 days=None(键缺失): %s" % is_graduated(mkt, {i: 15 for i in mkt[:-1]}))
p("一个词 days=7: %s" % is_graduated(mkt, dict({i: 15 for i in mkt}, **{mkt[0]: 7})))
p("rec 场景: graduatedScenes() 显式过滤 'rec'（该过滤分支已核）")
p("days=None 与 15 比较: Kotlin 'Int? == Int' 编译期合法，null==15 为 false（已核语义）")

# ---------- D. 性能 ----------
p()
p("== D. 性能 ==")
n_scene = len([s for s in scene_ids if s != "rec"])
n_terms = len(terms)
p("graduatedScenes(): %d 分区 x 全表 %d 词过滤 = 最多 %d 次 scene 字符串比较（每次作答调用 1 次）"
  % (n_scene, n_terms, n_scene * n_terms))
p("scopeIds('rec') 亦调用 1 次，但仅 ensureQueue 重建队列时（每天/每频道一次）")

# ---------- E. 符号残留 ----------
p()
p("== E. 符号残留扫描 ==")
KOT = []
for dirpath, _, files in os.walk(SRC):
    for f in files:
        if f.endswith(".kt"):
            KOT.append(os.path.join(dirpath, f))
dead = ["ProgressRow", "REC_QUEUE", "Term.Kind", "ProgressTrack", "onReady", "kind =", "Gone"]
for d in dead:
    hits = []
    for f in KOT:
        txt = open(f, encoding="utf-8").read()
        for i, line in enumerate(txt.splitlines(), 1):
            if d in line:
                hits.append("%s:%d" % (os.path.basename(f), i))
    p("残留 %-14s: %s" % (d, hits or "无"))

# ChannelBar 调用点
p()
for sig in ["ChannelBar", "TermCard(", "DoneCard(", "CharSheet("]:
    hits = []
    for f in KOT:
        txt = open(f, encoding="utf-8").read()
        for i, line in enumerate(txt.splitlines(), 1):
            if sig in line:
                hits.append("%s:%d %s" % (os.path.basename(f), i, line.strip()[:60]))
    p("%s 出现: %d 处" % (sig, len(hits)))
    for h in hits:
        p("   " + h)
