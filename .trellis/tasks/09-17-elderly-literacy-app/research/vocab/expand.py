# -*- coding: utf-8 -*-
"""任务驱动词库：把「生活任务链」展开的数据做批处理 —— 自动注音、算字集、算覆盖率、算边际衰减。

这一步是"如何丰富词库"的机制原型。输入只有人写的五列：
    生活域 / 任务 / 步骤 / 词条 / 提示语
拼音、字集、字频名次、新增字、覆盖率、跨任务边际衰减全部由脚本产出。
人不写拼音、不数数、不判重。

输出：
  demo_terms.json   展开后的完整词条（含逐字拼音、字频名次、所属任务步骤）
  demo_report.md    UTF-8 报告

用法：把新的任务链写成 demo_task_<名字>.tsv 丢进同目录，重跑即可。
"""
import csv
import glob
import json
import os

from pypinyin import Style, pinyin

BASE = os.path.dirname(os.path.abspath(__file__))
SEED = os.path.join(BASE, "terms_seed.json")
CHARLIST = os.path.join(BASE, "charlist_3500.csv")
TSVS = sorted(glob.glob(os.path.join(BASE, "demo_task_*.tsv")))

# ---------- 权威字表 ----------
freq_rank, top2500, top3500 = {}, set(), set()
with open(CHARLIST, encoding="utf-8") as f:
    for r in csv.DictReader(f):
        freq_rank[r["char"]] = int(r["freq_rank"])
        top3500.add(r["char"])
        if int(r["rank"]) <= 2500:
            top2500.add(r["char"])

# ---------- 现有词库已覆盖的字 ----------
seed = json.load(open(SEED, encoding="utf-8"))
existing = {ch["c"] for t in seed["terms"] for ch in t["chars"]} & top3500


def annotate(word):
    """逐字注音 —— 按词组上下文切分，多音字靠 pypinyin 取音"""
    py = pinyin(word, style=Style.TONE)
    return [{"c": ch, "p": py[i][0] if i < len(py) else "", "rank": freq_rank.get(ch, 0)}
            for i, ch in enumerate(word)]


# ---------- 逐任务展开 ----------
terms, task_stats = [], []
covered = set(existing)          # 累计已覆盖字，随任务推进增长
seen_tasks = []
for tsv in TSVS:
    rows = list(csv.DictReader(open(tsv, encoding="utf-8-sig"), delimiter="\t"))
    task = rows[0]["任务"]
    seen_tasks.append((rows[0]["生活域"], task))
    steps = []
    for r in rows:
        if r["步骤"] not in steps:
            steps.append(r["步骤"])

    before = set(covered)
    for r in rows:
        terms.append({
            "id": "demo-%03d" % (len(terms) + 1),
            "domain": r["生活域"], "task": r["任务"], "step": r["步骤"],
            "text": r["词条"], "chars": annotate(r["词条"]), "tip": r["提示语"],
        })
    task_chars = {r["词条"][i] for r in rows for i in range(len(r["词条"]))}
    covered |= task_chars
    task_stats.append({
        "tsv": os.path.basename(tsv), "domain": rows[0]["生活域"], "task": task,
        "steps": steps, "n_terms": len(rows), "n_uniq": len(task_chars),
        "new": len(task_chars - before), "cum": len(covered),
        "decline": (len(task_chars - before) / len(task_chars)) if task_chars else 0,
    })

all_chars = [ch["c"] for t in terms for ch in t["chars"]]
uniq = set(all_chars)
new_chars = uniq - existing
outside = uniq - top3500
new_ranked = sorted(new_chars, key=lambda c: freq_rank.get(c, 99999))

buckets = [("前 500 名", 1, 500), ("501-1000", 501, 1000), ("1001-1500", 1001, 1500),
           ("1501-2000", 1501, 2000), ("2001-2500", 2001, 2500), ("2501-3500", 2501, 3500)]
dist = [(name, sum(1 for c in new_chars if lo <= freq_rank.get(c, 99999) <= hi))
        for name, lo, hi in buckets]

# ---------- 边际衰减：用实测的 2 个任务外推 ----------
seq = [s for s in task_stats]
first_rate = seq[0]["decline"] if seq else 0
last_rate = seq[-1]["decline"] if len(seq) > 1 else first_rate
avg_new_last = seq[-1]["new"] if seq else 0
uniq_avg = sum(s["n_uniq"] for s in seq) / len(seq) if seq else 0

# ---------- 写出 ----------
json.dump({"sources": [os.path.basename(p) for p in TSVS], "terms": terms},
          open(os.path.join(BASE, "demo_terms.json"), "w", encoding="utf-8", newline="\n"),
          ensure_ascii=False, indent=1)

L = []
w = L.append
w("# 任务驱动词库展开实测报告\n")
w("> 输入只有人写的五列：生活域 / 任务 / 步骤 / 词条 / 提示语。")
w("> 拼音、字集、字频名次、新增字、覆盖率、边际衰减全部由脚本产出。\n")
w("已展开任务链：%s\n" % ("；".join("%s · %s" % (d, t) for d, t in seen_tasks)))

w("## 1. 逐任务实测\n")
w("| 生活域 | 任务 | 步骤数 | 词条数 | 去重字 | **净新增字** | 新字率 | 累计覆盖字 |")
w("|---|---|---|---|---|---|---|---|")
for s in task_stats:
    w("| %s | %s | %d | %d | %d | **%d** | %.0f%% | %d |" % (
        s["domain"], s["task"], len(s["steps"]), s["n_terms"], s["n_uniq"],
        s["new"], s["decline"] * 100, s["cum"]))
w("")

w("## 2. 边际衰减（整个推算的命门）\n")
w("- 首个任务（%s）新字率：**%.0f%%** → 净新增 %d 字" % (seq[0]["task"], first_rate * 100, seq[0]["new"]))
if len(seq) > 1:
    w("- 第二个任务（%s，**跨生活域**）新字率：**%.0f%%** → 净新增 %d 字" % (
        seq[-1]["task"], last_rate * 100, seq[-1]["new"]))
    w("- 衰减幅度：%.0f%% → %.0f%%，即每换一个任务，新字率乘以 **%.2f**" % (
        first_rate * 100, last_rate * 100, last_rate / first_rate if first_rate else 0))
w("")
w("> 跨域任务之间的复用率，决定了「要铺多少个任务才能覆盖常用字」。")
w("> 上表实测值替代了此前 55% 的拍脑袋假设。\n")

w("## 3. 累计产出\n")
w("| 指标 | 数值 |")
w("|---|---|")
w("| 累计词条 | %d |" % len(terms))
w("| 汉字出现总次数 | %d |" % len(all_chars))
w("| 累计去重汉字 | **%d** |" % len(uniq))
w("| 现有词库已覆盖字 | %d |" % len(existing))
w("| **本批新增字** | **%d** |" % len(new_chars))
w("| 其中落在 2500 常用字内 | %d（%.1f%%） |" % (
    len(new_chars & top2500), len(new_chars & top2500) / len(new_chars) * 100))
w("| **超出 3500 常用字表** | **%d** |" % len(outside))
w("| 2500 常用字覆盖率 | %.2f%% |" % (len(uniq & top2500) / 2500 * 100))
w("| 3500 常用字覆盖率 | %.2f%% |" % (len(uniq & top3500) / 3500 * 100))
w("")
if outside:
    w("- ⚠️ 超出常用字表的字：%s" % " ".join(sorted(outside)))
    w("")

w("## 4. 新增字的字频名次分布\n")
w("| 字频名次区间 | 新增字数 |")
w("|---|---|")
for name, n in dist:
    w("| %s | %d |" % (name, n))
w("")
w("新增字按字频排前 45 个（括号内为字频名次）：\n")
w("```")
for i in range(0, min(45, len(new_ranked)), 15):
    w("  ".join("%s(%d)" % (c, freq_rank.get(c, 0)) for c in new_ranked[i:i + 15]))
w("```")
w("")

w("## 5. 高频字盲区（决定「要不要第二轨」）\n")
w("任务链产出的字，能覆盖多少**真正高频的字**？这决定了纯任务链够不够用。\n")
ranked_all = sorted(top3500, key=lambda c: freq_rank.get(c, 99999))
w("| 高频区间 | 该区间字数 | 词库已覆盖 | 覆盖率 | **盲区** |")
w("|---|---|---|---|---|")
for lo, hi in [(1, 200), (201, 500), (501, 1000), (1001, 1500), (1501, 2500)]:
    seg = [c for c in ranked_all if lo <= freq_rank.get(c, 99999) <= hi]
    hit = [c for c in seg if c in uniq]
    w("| 前 %d-%d 名 | %d | %d | %.1f%% | %d |" % (
        lo, hi, len(seg), len(hit), len(hit) / len(seg) * 100, len(seg) - len(hit)))
w("")
top200_miss = [c for c in ranked_all if freq_rank.get(c, 99999) <= 200 and c not in uniq]
w("字频前 200 名里**没被任何任务链产出**的字（共 %d 个）:\n" % len(top200_miss))
w("```")
for i in range(0, min(80, len(top200_miss)), 20):
    w("  ".join(top200_miss[i:i + 20]))
w("```")
w("")
w("> 这一批就是「任务链永远产不出来」的字 —— 语法虚词、代词、抽象动词。")
w("> 它们不出现任何标牌上，但占书面语与短信/通知文本的极大比重。")
w("> **纯任务链路线到这里就到顶了，必须由第二轨（高频字表）补齐。**\n")

w("## 6. 量级推算\n")
DOMAINS, TASKS_PER_DOMAIN = 9, 4
proj_tasks = DOMAINS * TASKS_PER_DOMAIN
proj_terms = proj_tasks * (len(terms) / len(TSVS))
r1 = first_rate
FLOOR = 0.30          # 话题空间饱和后的重叠下限
d = ((last_rate - FLOOR) / (r1 - FLOOR)) if len(seq) > 1 and r1 > FLOOR else 0.75
proj_new = uniq_avg * sum(FLOOR + (r1 - FLOOR) * (d ** (n - 1)) for n in range(1, proj_tasks + 1))
proj_cum = len(existing) + proj_new
w("实测新字率 %.0f%%（第 1 个任务）→ %.0f%%（第 2 个任务，**跨生活域**），**跨域几乎不衰减**。" % (
    r1 * 100, last_rate * 100))
w("真正会衰减的是**同一生活域内的第 2、3、4 个任务**（反复出现同一批字）。\n")
w("模型：新字率从实测 %.0f%% 起步等比趋近饱和下限 %.0f%%（按第 2 个实测点反推衰减因子 %.2f），"
  "每任务平均产出 %.0f 个去重字。\n" % (r1 * 100, FLOOR * 100, d, uniq_avg))
w("按 **%d 个生活域 × 每域 %d 个任务 = %d 个任务** 推算：\n" % (DOMAINS, TASKS_PER_DOMAIN, proj_tasks))
w("| 推算项 | 数值 |")
w("|---|---|")
w("| 总词条数 | 约 %.0f |" % proj_terms)
w("| 累计覆盖不重复字 | 约 %.0f |" % proj_cum)
w("| 对应 2500 常用字覆盖 | 约 %.0f%% |" % min(100, proj_cum / 2500 * 100))
w("")
w("敏感性（饱和下限取值）：\n")
w("| 饱和下限 | 累计覆盖字 | 2500 覆盖 |")
w("|---|---|---|")
for fl in (0.25, 0.30, 0.35, 0.45):
    dd = ((last_rate - fl) / (r1 - fl)) if r1 > fl else 0.75
    c = len(existing) + uniq_avg * sum(fl + (r1 - fl) * (dd ** (n - 1)) for n in range(1, proj_tasks + 1))
    w("| %.0f%% | 约 %.0f | 约 %.0f%% |" % (fl * 100, c, min(100, c / 2500 * 100)))
w("")
w("> 停止条件：某任务新字率 < 20% 且无新步骤类型时，该任务边际价值已低。")
w("> 推算只用于判断量级，不作为承诺；真实数字靠逐任务展开后实测。\n")

open(os.path.join(BASE, "demo_report.md"), "w", encoding="utf-8", newline="\n").write("\n".join(L))

print("tasks=%d terms=%d uniq=%d new=%d outside=%d" % (len(TSVS), len(terms), len(uniq), len(new_chars), len(outside)))
for s in task_stats:
    print("  %-10s terms=%2d uniq=%2d new=%2d rate=%.0f%% cum=%d" % (
        s["task"], s["n_terms"], s["n_uniq"], s["new"], s["decline"] * 100, s["cum"]))
print("proj_tasks=%d proj_terms=%.0f proj_cum=%.0f" % (proj_tasks, proj_terms, proj_cum))
