# -*- coding: utf-8 -*-
"""查指定汉字在《现代汉语常用字表》中的字频名次，用于"场景价值 x 字频"排序讨论。"""
import csv
import os

BASE = os.path.dirname(os.path.abspath(__file__))

GROUPS = {
    "具象生活字": "药钱车票站买菜饭油米肉茶馆",
    "抽象高频虚词": "的是不了在而其为与矣夫",
    "场景专用字": "挂诊缴凭签折储缴卡票据",
    "低频冷字": "乃焉衷褒翱隘彬霓",
}

rows = {}
with open(os.path.join(BASE, "charlist_3500.csv"), encoding="utf-8") as f:
    for r in csv.DictReader(f):
        rows[r["char"]] = int(r["freq_rank"])

out = []
for name, chars in GROUPS.items():
    out.append("## %s" % name)
    for ch in chars:
        rank = rows.get(ch)
        if rank is None:
            out.append("  %s  (不在常用字表)" % ch)
        else:
            out.append("  %s  字频名次 %d / 3499" % (ch, rank))
    out.append("")

with open(os.path.join(BASE, "rank_probe.txt"), "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(out))

print("ok, groups=%d" % len(GROUPS))
