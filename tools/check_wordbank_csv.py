# -*- coding: utf-8 -*-
"""官方词库 CSV 校验（CI 门禁）：扫 <repo>/wordbanks/*.csv，规则与 App 导入校验同源。

App 侧规则在 `android-app/.../data/CustomBank.kt`（parseCsv + checkAndBuildTerm）。
本脚本在 CI（.github/workflows/android-build.yml）与本地提交前把同一套规则跑一遍，
别把坏 CSV 发给用户：

 1. 文件名（去扩展名）1~8 字（= 库名）
 2. 每行恰好 3 列：词 / 拼音 / 用途；首行表头（词,拼音,用途）官方包强制带
 3. 词文本 1~8 字；拼音音节数 = 字数（与 App 导入校验同一条件）；用途 1~30 字
 4. 库内词文本不重复；**跨文件也不重复**（「一个词只留一份」契约，v17 口径）
 5. pypinyin 逐字对照（信息性；环境未装则跳过，差异需逐条判定，不等于错）

用法：python3 tools/check_wordbank_csv.py
退出码：0 = 全部通过；1 = 存在阻断项（CI 据此拦截）。
报告：tools/_wordbank_csv.md（自动生成，不入库）。
"""
import csv
import io
import os
import re
import sys
import collections

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, ".."))
WB_DIR = os.path.join(REPO, "wordbanks")
OUT = os.path.join(HERE, "_wordbank_csv.md")

HEADER = ["词", "拼音", "用途"]

L, w, fails = [], lambda s: L.append(s), []
w("# 官方词库 CSV 检查\n")
w("> 自动生成，脚本 `tools/check_wordbank_csv.py`（可复跑）。源：`wordbanks/*.csv`；规则与 App 导入校验同源。\n")

files = sorted(f for f in os.listdir(WB_DIR) if f.endswith(".csv"))
if not files:
    fails.append("wordbanks/ 下没有任何 CSV")
    w("**失败**：`wordbanks/` 目录为空——官方词库包缺失。\n")

all_texts = {}
total = 0

for fname in files:
    base = fname[:-4]
    path = os.path.join(WB_DIR, fname)
    # ⚠️ 必须 utf-8-sig + newline=""：官方 CSV 是 BOM + CRLF；文本模式通用换行会把 CRLF
    # 折成 LF，按行尾做统计的规则会静默失效（v27 实证过总数算成 0 的事故）
    raw = io.open(path, encoding="utf-8-sig", newline="").read()
    rows = list(csv.reader(io.StringIO(raw)))
    w("## %s\n" % fname)

    if not (1 <= len(base) <= 8):
        fails.append("%s 文件名 %d 字（须 1~8，即库名）" % (fname, len(base)))

    # 表头存在性（v25 补：此前只认「若有则跳过」，缺表头不报——会让写回脚本丢首行的
    # 事故逃过校验，且下方计数无条件减 1 会掩盖条数差）
    has_header = bool(rows) and rows[0][:3] == HEADER
    if not has_header:
        fails.append("%s 首行不是表头「词,拼音,用途」——官方包强制带表头" % fname)
    rows = rows[1:] if has_header else rows
    w("库名（文件名）**%s**（%d 字）· 表头 %s · 数据行 **%d** 行\n" % (
        base, len(base), "有" if has_header else "**缺失**", len(rows)))

    seen = set()
    file_texts = []
    for i, row in enumerate(rows, start=2 if has_header else 1):
        if not any(c.strip() for c in row):
            continue
        if len(row) != 3:
            fails.append("%s 第 %d 行列数 %d ≠ 3" % (fname, i, len(row)))
            continue
        text, pinyin, tip = (c.strip() for c in row)
        label = "%s 第 %d 行「%s」" % (fname, i, text)
        if not (1 <= len(text) <= 8):
            fails.append("%s 词文本须 1~8 字" % label)
        syllables = pinyin.split(" ")
        if pinyin and len(syllables) == len(text):
            pass
        else:
            fails.append("%s 拼音配对不符（%d 音节 vs %d 字）" % (label, len(syllables) if pinyin else 0, len(text)))
        if not (1 <= len(tip) <= 30):
            fails.append("%s 用途须 1~30 字" % label)
        if text in seen:
            fails.append("%s 库内重词" % label)
        seen.add(text)
        if text in all_texts:
            fails.append("%s 与 %s 重词「%s」——一个词只留一份" % (label, all_texts[text], text))
        all_texts[text] = fname
        file_texts.append((text, pinyin, tip))

    total += len(file_texts)

    # pypinyin 对照（信息性；CI/本地无该包则自动跳过，不阻断）
    try:
        from pypinyin import pinyin as pinyin_fn, Style
        diffs = []
        for text, py, _ in file_texts:
            glp = py.split(" ")
            p = [x[0] for x in pinyin_fn(text, style=Style.TONE)]
            if len(p) != len(text) or len(glp) != len(text):
                continue
            for k in range(len(text)):
                if glp[k] != p[k]:
                    diffs.append((text, text[k], glp[k], p[k]))
        w("pypinyin 差异 **%d** 处%s\n\n" % (
            len(diffs),
            ("：" + "、".join("`%s·%s：%s≠%s`" % d for d in diffs[:8]) + ("…" if len(diffs) > 8 else "")) if diffs else "（全对）"))
    except ImportError:
        w("pypinyin 未安装，跳过对照。\n\n")

w("## 汇总\n")
w("- 文件 %d 个、词条 **%d** 条\n" % (len(files), total))
w("\n## 结论\n")
if fails:
    w("**存在阻断项**（%d 条）：\n\n" % len(fails))
    for f in fails:
        w("- %s\n" % f)
else:
    w("**全部通过**——官方 CSV 与 App 导入校验同构，可分发。\n")

io.open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(L) + "\n")
for f in fails:
    print("FAIL: %s" % f, file=sys.stderr)
print("files=%d terms=%d fails=%d" % (len(files), total, len(fails)))
sys.exit(1 if fails else 0)
