# -*- coding: utf-8 -*-
"""覆盖率分析：现有词条覆盖了多少权威常用字，缺口是什么，补齐需要多少词条。

输入：charlist_3500.csv（权威字表 + 字频）
      两份现行数据源（android-app StudyData.kt / prototype index.html）
输出：coverage_report.md（UTF-8，供人读）
      terms_seed.json（现有词条结构化快照，作为词库单一数据源的种子）
"""
import csv
import json
import os
import re

BASE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(BASE, "..", "..", "..", "..", ".."))  # -> 仓库根
KT = os.path.join(ROOT, "android-app", "app", "src", "main", "java", "com", "qyj", "shibang", "data", "StudyData.kt")
HTML = os.path.join(ROOT, "prototype", "index.html")

# ---------- 1. 权威字表 ----------
chars = []  # (rank, char, freq, freq_rank)
with open(os.path.join(BASE, "charlist_3500.csv"), encoding="utf-8") as f:
    for row in csv.DictReader(f):
        chars.append((int(row["rank"]), row["char"], int(row["freq"]), int(row["freq_rank"])))

CHARSET = set(c for _, c, _, _ in chars)
# 常用字 2500 = rank<=2500 为《现代汉语常用字表》的"常用字"部分，其后 1000 为次常用字
TOP2500 = [c for r, c, _, _ in chars if r <= 2500]
TOP1000_BY_FREQ = [c for _, c, _, fr in sorted(chars, key=lambda x: x[3]) if fr <= 1000]
BY_FREQ = sorted(chars, key=lambda x: x[3])  # 按真实使用频率排序


def coverage_band(covered_set, pool):
    hit = [c for c in pool if c in covered_set]
    return len(hit), len(pool), (len(hit) / len(pool) * 100 if pool else 0.0)


# ---------- 2. 解析现有数据源 ----------
def parse_kt(path):
    src = open(path, encoding="utf-8").read()
    terms = []
    # 逐条 Term(...)：listOf 内是多个嵌套的 TermChar(...)，需按结构匹配而非 [^)]*
    term_re = re.compile(
        r'Term\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*'
        r'listOf\(((?:\s*TermChar\("[^"]*",\s*"[^"]*"\)\s*,?)+)\)\s*,\s*"([^"]*)"',
        re.S)
    char_re = re.compile(r'TermChar\("([^"]+)",\s*"([^"]+)"\)')
    for m in term_re.finditer(src):
        tid, scene, icon, text, chars_blob, tip = m.groups()
        cs = char_re.findall(chars_blob)
        terms.append({"id": tid, "scene": scene, "icon": icon, "text": text,
                      "chars": [{"c": c, "p": p} for c, p in cs], "tip": tip})
    return terms


def parse_html(path):
    src = open(path, encoding="utf-8").read()
    terms = []
    for m in re.finditer(r'\{id:"([^"]+)",\s*scene:"([^"]+)",\s*icon:"([^"]*)",\s*text:"([^"]+)",\s*chars:\[([^\]]*)\]\s*,\s*tip:"([^"]*)"', src):
        tid, scene, icon, text, chars_blob, tip = m.groups()
        cs = [(c, p) for c, p in re.findall(r'\{c:"([^"]+)",p:"([^"]+)"\}', chars_blob)]
        terms.append({"id": tid, "scene": scene, "icon": icon, "text": text,
                      "chars": [{"c": c, "p": p} for c, p in cs], "tip": tip})
    return terms


kt_terms = parse_kt(KT)
html_terms = parse_html(HTML)

# ---------- 3. 覆盖率 ----------
all_chars = []
for t in kt_terms:
    all_chars.extend(ch["c"] for ch in t["chars"])
uniq = set(all_chars)
dup_ratio = 1 - len(uniq) / len(all_chars) if all_chars else 0
outside = sorted(uniq - CHARSET)

c1000 = coverage_band(uniq, TOP1000_BY_FREQ)
c2500 = coverage_band(uniq, TOP2500)
c3500 = coverage_band(uniq, CHARSET)

# 缺口：2500 常用字里没被任何词条覆盖的，按使用频率排序
gap = [c for c in TOP2500 if c not in uniq]
gap_sorted = sorted(gap, key=lambda c: next(fr for _, cc, _, fr in chars if cc == c))

# 补齐推算：每个词条平均贡献多少"新字"
per_term_new = len(uniq) / len(kt_terms) if kt_terms else 0
proj = []
for target in (0.80, 0.90, 0.95, 1.00):
    need_chars = int(len(TOP2500) * target)
    missing = need_chars - len([c for c in TOP2500 if c in uniq])
    if missing <= 0:
        proj.append((target, 0, 0, 0))
    else:
        # 边际递减：后续词条的重叠率按当前重叠率外推，保守取每词贡献 1.5 新字
        proj.append((target, missing,
                     (missing / per_term_new) if per_term_new else -1,
                     missing / 1.5))

# ---------- 4. 输出 ----------
lines = []
w = lines.append
w("# 词库覆盖率分析报告\n")
w("> 权威基线：《现代汉语常用字表》（1988 国家语委 + 国家教委）3500 字，")
w("> 覆盖现代汉语书面语 99.48%（其中常用字 2500 覆盖 97.97%）。字频取自 Jun Da 现代汉语语料库。\n")
w("## 1. 数据源现状\n")
w("| 数据源 | 词条数 | 解析结果 |")
w("|---|---|---|")
w("| `android-app/.../StudyData.kt` | %d | %s |" % (len(kt_terms), "OK" if kt_terms else "**解析失败**"))
w("| `prototype/index.html` | %d | %s |" % (len(html_terms), "OK" if html_terms else "**解析失败**"))
w("")
kt_ids = [t["id"] for t in kt_terms]
html_ids = [t["id"] for t in html_terms]
w("- 两端 id 一致：%s" % ("是" if kt_ids == html_ids else "**否**，差异 = %s" % (set(kt_ids) ^ set(html_ids))))
w("- 两端内容逐字段一致：%s" % ("是（无需合并）" if kt_terms == html_terms else "**否**，存在字段级漂移"))
w("")
w("## 2. 当前字覆盖\n")
w("| 指标 | 数值 |")
w("|---|---|")
w("| 词条数 | %d |" % len(kt_terms))
w("| 出现汉字总次数（含重复） | %d |" % len(all_chars))
w("| 去重汉字数 | **%d** |" % len(uniq))
w("| 字重复率（同字跨词复用） | %.1f%% |" % (dup_ratio * 100))
w("| 每词平均贡献新字 | %.2f |" % per_term_new)
w("")
w("| 对照池 | 已覆盖 / 池大小 | 覆盖率 |")
w("|---|---|---|")
w("| 高频前 1000 字 | %d / %d | %.1f%% |" % c1000)
w("| **常用字 2500** | %d / %d | **%.1f%%** |" % c2500)
w("| 常用字表 3500 | %d / %d | %.1f%% |" % c3500)
w("")
if outside:
    w("- 超出《现代汉语常用字表》的字（%d 个）：%s" % (len(outside), " ".join(outside)))
    w("")
w("## 3. 缺口清单（2500 常用字中尚未被覆盖，按使用频率排序）\n")
w("共 **%d** 个缺口字。前 120 位：\n" % len(gap_sorted))
w("```")
for i in range(0, min(120, len(gap_sorted)), 20):
    w("  ".join(gap_sorted[i:i + 20]))
w("```")
w("")
w("## 4. 补齐到目标覆盖率的工作量推算\n")
w("| 目标（覆盖 2500 常用字） | 目标字数 | 需补新字 | 按当前效率需增词条 | 按每词 1.5 新字需增词条 |")
w("|---|---|---|---|---|")
for target, missing, by_cur, by_15 in proj:
    w("| %.0f%% | %d | %d | %.0f | %.0f |" % (target * 100, int(2500 * target), missing, by_cur, by_15))
w("")
w("> 说明：后两列是推算区间。随着覆盖面变宽，新词条能带来的新字越来越少（边际递减），")
w("> 因此实际需求会偏向「每词 1.5 新字」一侧。\n")

with open(os.path.join(BASE, "coverage_report.md"), "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines))

with open(os.path.join(BASE, "terms_seed.json"), "w", encoding="utf-8", newline="\n") as f:
    json.dump({"source": "StudyData.kt", "terms": kt_terms}, f, ensure_ascii=False, indent=1)

# 控制台仅输出 ASCII，避免 PowerShell 捕获乱码
print("kt_terms=%d html_terms=%d parity_ids=%s parity_content=%s" % (
    len(kt_terms), len(html_terms), kt_ids == html_ids, kt_terms == html_terms))
print("uniq=%d outside=%d coverage_top2500=%.2f%% gap=%d" % (
    len(uniq), len(outside), c2500[2], len(gap_sorted)))
