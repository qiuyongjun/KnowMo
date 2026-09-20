# -*- coding: utf-8 -*-
"""场景基线字覆盖分析 —— 用「老年生活场景语料」当基线评估词库的认读能力。

与 coverage.py 的区别（后者已随 v6 结构变更失效：引用旧包名 com.qyj.shibang
与已删除的 prototype/index.html）：

  coverage.py 用《现代汉语常用字表》2500 当基线 —— 该表服务「阅读连贯文章」；
  本脚本用项目自己的场景任务语料当基线 —— 服务「认读环境里的文字」，
  与收录判据（词是否以文字形式出现在老人生活环境）口径一致。

核心对照：场景语料的「词条列」= 真实出现在环境里的文字（学习对象）；
          「提示语列」= 编者写的解释句（非学习对象，只在 App 里当注解）。
          若两列的覆盖率差异巨大，说明用通用字表当基线是把注解当成了教材。

用法：python char_coverage.py
产出：char_coverage_report.md
"""
import os
import re
import csv
import collections

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = r"D:\QYJ\MyProject\Mando\android-app\app\src\main\java\com\knowmo\app\data"
WB = os.path.join(SRC, "WordBank.kt")
CHARLIST = os.path.join(HERE, "charlist_3500.csv")
TASKS = [
    os.path.join(HERE, "demo_task_hospital.tsv"),
    os.path.join(HERE, "demo_task_market.tsv"),
]
OUT = os.path.join(HERE, "char_coverage_report.md")

TERM = re.compile(
    r'term\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*\)'
)


def han(s):
    return [c for c in s if "\u4e00" <= c <= "\u9fff"]


# ---------- 1. 词库 ----------
src = open(WB, encoding="utf-8").read()
rows = TERM.findall(src)                      # (id, scene, text, pinyin, tip)
bank_texts = [r[2] for r in rows]
bank_set = set(bank_texts)
bank_chars = set(c for t in bank_texts for c in han(t))

# ---------- 2. 场景语料 ----------
items, tips = [], []
for p in TASKS:
    with open(p, encoding="utf-8-sig", newline="") as f:
        rd = csv.reader(f, delimiter="\t")
        next(rd, None)
        for row in rd:
            if len(row) >= 5:
                items.append(row[3].strip())
                tips.append(row[4].strip())
items_u = list(dict.fromkeys(i for i in items if i))
tips_u = list(dict.fromkeys(t for t in tips if t))

item_cnt = collections.Counter(c for t in items_u for c in han(t))
tip_cnt = collections.Counter(c for t in tips_u for c in han(t))
item_set, tip_set = set(item_cnt), set(tip_cnt)

# ---------- 3. 基线 A：常用字 2500（旧报告基线） ----------
top2500 = []
with open(CHARLIST, encoding="utf-8-sig", newline="") as f:
    for row in csv.DictReader(f):
        if int(row.get("rank", "0") or 0) <= 2500:
            top2500.append(row["char"])
top2500_set = set(top2500)


def cov(pool):
    if not pool:
        return 0, 0, 0.0
    hit = sum(1 for c in pool if c in bank_chars)
    return hit, len(pool), hit / len(pool) * 100


a_hit, a_all, a_pct = cov(top2500)
i_hit, i_all, i_pct = cov(item_set)
t_hit, t_all, t_pct = cov(tip_set)

# ---------- 4. 词级命中 ----------
hit_terms = [t for t in items_u if t in bank_set]
miss_terms = [t for t in items_u if t not in bank_set]
extra_terms = [t for t in bank_texts if t not in set(items_u)]

# ---------- 5. 真正的缺口字（场景语料中出现，但词库未覆盖） ----------
gap = [(c, n) for c, n in item_cnt.items() if c not in bank_chars]
gap.sort(key=lambda x: (-x[1], x[0]))

# 未被覆盖的场景词条中，「一次能带出几个缺口字」排名
carriers = []
for t in miss_terms:
    new = [c for c in set(han(t)) if c not in bank_chars]
    carriers.append((len(new), t, "".join(sorted(new))))
carriers.sort(key=lambda x: (-x[0], x[1]))

# ---------- 6. 输出 ----------
L, w = [], lambda s: L.append(s)
w("# 场景基线字覆盖报告\n")
w("> 自动生成，脚本 `char_coverage.py`（可复跑）。源：`WordBank.kt` + 场景任务语料。")
w("> 基线取自项目自己的 demo 任务（就医 / 买菜），字频按场景出现次数统计。\n")

w("## 1. 参与计算的数据\n")
w("| 数据 | 条数 | 去重汉字 |")
w("|---|---|---|")
w("| 词库 `WordBank.kt` | %d | %d |" % (len(rows), len(bank_chars)))
w("| 场景语料 · 词条列（环境真实文字） | %d | %d |" % (len(items_u), len(item_set)))
w("| 场景语料 · 提示语列（编者解释句） | %d | %d |" % (len(tips_u), len(tip_set)))
w("")

w("## 2. 同一词库、三条基线的覆盖率对照\n")
w("| 基线 | 池大小 | 词库已覆盖 | 覆盖率 |")
w("|---|---|---|---|")
w("| A《现代汉语常用字表》前 2500（旧报告采用） | %d | %d | %.1f%% |" % (a_all, a_hit, a_pct))
w("| B 场景词条列 · 环境真实文字（**应采用**） | %d | %d | **%.1f%%** |" % (i_all, i_hit, i_pct))
w("| C 场景提示语列 · 编者解释句（非学习对象） | %d | %d | %.1f%% |" % (t_all, t_hit, t_pct))
w("")
w("> 基底 A 覆盖率高不了，是因为它按「读文章」的字频排序，前 20 位是"
  "的/是/不/了/在/有/我/他/这/个 —— 这些字在招牌、价签、按钮上几乎不出现。")
w("> 基底 C 的句法接近书面语，因此也更接近 A 的形状；但它不是老人要认读的对象。")
w("> **两个基线的落差，就是「覆盖难」这个印象的来源。**\n")

w("## 3. 场景词条覆盖（词级）\n")
w("| 指标 | 数值 |")
w("|---|---|")
w("| 场景词条总数（去重） | %d |" % (len(items_u)))
w("| 已被词库原样收录 | %d（%.1f%%） |" % (len(hit_terms), len(hit_terms) / len(items_u) * 100 if items_u else 0))
w("| 尚未收录 | %d |" % len(miss_terms))
w("| 词库中场景语料之外的自有词条 | %d |" % len(extra_terms))
w("")
if hit_terms:
    w("已收录：%s\n" % " ".join(hit_terms))
if miss_terms:
    w("未收录：%s\n" % " ".join(miss_terms))

w("## 4. 真正的缺口字（场景出现、词库未覆盖，按场景频次排序）\n")
if gap:
    w("共 **%d** 个：\n" % len(gap))
    w("```")
    line = []
    for c, n in gap:
        line.append("%s(%d)" % (c, n))
        if len(line) == 20:
            w("  ".join(line)); line = []
    if line:
        w("  ".join(line))
    w("```")
    w("")
    w("> 括号内为该字在场景语料中的出现次数 —— 这才是补词的优先级依据，")
    w("> 而不是《常用字表》的字频排名。\n")
else:
    w("**无缺口** —— 场景语料中的全部汉字都已被词库覆盖。\n")

w("## 5. 补词收益排序（未收录的场景词条，按带出的新字数）\n")
if carriers:
    w("| 带出新字 | 词条 | 新字 |")
    w("|---|---|---|")
    for n, t, nc in carriers[:25]:
        w("| %d | %s | %s |" % (n, t, nc if nc else "—"))
    w("")
    w("> 带出 0 个新字的词条说明其全部用字已被覆盖 —— 这类词的教学价值是")
    w("> 「功能覆盖」（场景要用），不是「字覆盖」。两类价值需要分开评估。\n")
else:
    w("无未收录词条。\n")

w("## 6. 最小补词集：补齐场景缺口字需要几条词\n")
need = set(item_set) - bank_chars
pool = [t for t in miss_terms if set(han(t)) & need]
chosen = []
while need and pool:
    best = max(pool, key=lambda t: (len(set(han(t)) & need), -len(t)))
    gain = set(han(best)) & need
    if not gain:
        break
    chosen.append((best, "".join(sorted(gain))))
    need -= gain
    pool.remove(best)
gap_total = len(set(item_set) - bank_chars)
if chosen:
    w("| # | 补入词条 | 带出的缺口字 |")
    w("|---|---|---|")
    for i, (t, g) in enumerate(chosen, 1):
        w("| %d | %s | %s |" % (i, t, g))
    w("")
    w("**补入 %d 条词即可覆盖 %d / %d 个缺口字**；剩余未覆盖 %d 个。\n"
      % (len(chosen), gap_total - len(need), gap_total, len(need)))
    if need:
        w("仍需覆盖：%s\n" % " ".join(sorted(need)))
else:
    w("无需补词 —— 场景用字已被词库覆盖。\n")

w("## 7. 覆盖一个完整场景的最少词条数（贪心下界）\n")
need2 = set(item_set)
pool2 = list(items_u)
steps = []
while need2 and pool2:
    best = max(pool2, key=lambda t: len(set(han(t)) & need2))
    gain = set(han(best)) & need2
    if not gain:
        break
    steps.append((best, len(gain)))
    need2 -= gain
    pool2.remove(best)
w("| 步 | 词条 | 新字 |")
w("|---|---|---|")
for i, (t, n) in enumerate(steps, 1):
    w("| %d | %s | %d |" % (i, t, n))
w("")
w("**覆盖场景全部 %d 个汉字，最少需 %d 条场景词**（贪心近似，非最优解）。\n"
  % (len(item_set), len(steps)))

w("## 8. 结论\n")
w("- 基线 B 下的覆盖率 **%.1f%%**；基线 A 下为 **%.1f%%**。" % (i_pct, a_pct))
w("- 场景词条已收录 %.1f%%。" % (len(hit_terms) / len(items_u) * 100 if items_u else 0))
w("- 缺口字 %d 个，补词收益最高的是：%s"
  % (len(gap), "、".join("%s(%d)" % (c, n) for c, n in gap[:8]) if gap else "无"))

open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(L) + "\n")
print("bank_terms=%d bank_chars=%d" % (len(rows), len(bank_chars)))
print("items=%d item_chars=%d tips=%d tip_chars=%d" % (len(items_u), len(item_set), len(tips_u), len(tip_set)))
print("cov_A=%.1f%% cov_B=%.1f%% cov_C=%.1f%%" % (a_pct, i_pct, t_pct))
print("term_hit=%d/%d miss=%d gap_chars=%d" % (len(hit_terms), len(items_u), len(miss_terms), len(gap)))
print("->", OUT)
