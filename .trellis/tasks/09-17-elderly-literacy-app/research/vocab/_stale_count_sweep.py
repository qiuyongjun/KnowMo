# -*- coding: utf-8 -*-
"""旧计数残留全仓扫描（只读）。

扫描面：仓库全部文本文件（跳过 .git / 二进制 / 3500 字表 CSV / 原始 HTML），
按「关键词 → 命中行」列出。.trellis/tasks/** 下的历史记录按用户口径只报不改。
"""
import os
import re

ROOT = r"D:\QYJ\MyProject\Mando"
OUT = r"D:\QYJ\MyProject\Mando\.trellis\tasks\09-17-elderly-literacy-app\research\vocab\_stale_count_sweep.txt"

SKIP_DIRS = {".git", ".gradle", "build", ".idea", ".workbuddy", "node_modules"}
SKIP_EXT = {".png", ".jpg", ".jpeg", ".webp", ".apk", ".jar", ".zip", ".ttf", ".so", ".aab"}
SKIP_FILES = {"charlist_3500.csv", "_raw_charlist.html", "_stale_count_sweep.txt"}

# 关键词 = 旧值 / 计数口径
KEYS = ["417", "402", "401", "379", "368", "480", "464", "12 个场景", "13 个场景", "14 个场景",
        "12 个分区", "13 个分区", "14 个分区", "n ≤ ", "每区 ≥", "词条数", "个场景分区"]

hits = {k: [] for k in KEYS}
nfiles = 0
for dirpath, dirnames, filenames in os.walk(ROOT):
    dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
    for fn in filenames:
        if os.path.splitext(fn)[1].lower() in SKIP_EXT or fn in SKIP_FILES:
            continue
        p = os.path.join(dirpath, fn)
        try:
            txt = open(p, encoding="utf-8", errors="ignore").read()
        except Exception:
            continue
        if "\x00" in txt[:2000]:
            continue
        nfiles += 1
        rel = os.path.relpath(p, ROOT)
        for i, line in enumerate(txt.splitlines(), 1):
            for k in KEYS:
                if k in line:
                    hits[k].append((rel, i, line.strip()[:200]))

L = ["== 旧计数残留全仓扫描 ==", "扫描文本文件 %d 个" % nfiles, ""]
for k in KEYS:
    v = hits[k]
    L.append("### 关键词 `%s` —— %d 处" % (k, len(v)))
    for rel, i, line in v:
        L.append("  %s:%d  %s" % (rel, i, line))
    if not v:
        L.append("  （无）")
    L.append("")

open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(L))
print("OK files=%d ->" % nfiles, OUT)
