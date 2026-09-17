# -*- coding: utf-8 -*-
"""下载权威常用字表（含字频），供词库覆盖率分析使用。

数据源：《现代汉语常用字表》（1988 国家语委 + 国家教委，常用字 2500 + 次常用字 1000）
        字频取自 Jun Da 现代汉语语料库统计（Middle Tennessee State University）。
本脚本只做"构建期素材获取"，不参与运行时。
"""
import os
import re
import sys
import urllib.request

SRC = "https://lingua.mtsu.edu/chinese-computing/statistics/char/listchangyong.php"
OUT_DIR = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(OUT_DIR, "_raw_charlist.html")
CSV = os.path.join(OUT_DIR, "charlist_3500.csv")

os.makedirs(OUT_DIR, exist_ok=True)

if not os.path.exists(RAW):
    req = urllib.request.Request(SRC, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        data = resp.read()
    with open(RAW, "wb") as f:
        f.write(data)
    print("downloaded %d bytes" % len(data))
else:
    print("reuse cached raw html")

raw_bytes = open(RAW, "rb").read()
# 站点声明 charset=gb2312，实际按 gb18030 解码更容错
html = raw_bytes.decode("gb18030", errors="replace")

# 结构形如： 1 一 3050722（数字与汉字之间可能夹 <br> / 标签）
plain = re.sub(r"<[^>]+>", " ", html)
plain = plain.replace("&nbsp;", " ")
rows = re.findall(r"(\d+)\s+([\u4e00-\u9fff])\s+([\d,]+)", plain)

# 去重保序（同一字可能因分页重复出现）
seen = set()
clean = []
for rank, ch, freq in rows:
    if ch in seen:
        continue
    seen.add(ch)
    clean.append((int(rank), ch, int(freq.replace(",", ""))))

# 按字频降序重排后的名次，用于"教学顺序"
by_freq = sorted(clean, key=lambda r: -r[2])
freq_rank = {ch: i + 1 for i, (_, ch, _) in enumerate(by_freq)}

with open(CSV, "w", encoding="utf-8", newline="\n") as f:
    f.write("rank,char,freq,freq_rank\n")
    for rank, ch, freq in clean:
        f.write("%d,%s,%d,%d\n" % (rank, ch, freq, freq_rank[ch]))

print("parsed chars: %d" % len(clean))
print("write -> %s" % CSV)
print("top20 by freq: %s" % " ".join(ch for _, ch, _ in by_freq[:20]))
