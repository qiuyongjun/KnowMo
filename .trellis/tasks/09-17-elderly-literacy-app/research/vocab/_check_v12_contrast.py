# -*- coding: utf-8 -*-
"""对比度核对（只读）：SCENES 各分区背景色 vs AppText / AppText2（适老化硬指标 ≥ 7:1）。"""
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = r"D:\QYJ\MyProject\Mando\android-app\app\src\main\java\com\knowmo\app"
SD = os.path.join(SRC, "data", "StudyData.kt")
TH = os.path.join(SRC, "ui", "theme", "Theme.kt")
OUT = os.path.join(HERE, "_check_v12_contrast.txt")

SCENE = re.compile(r'Scene\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*Color\((0x[0-9A-Fa-f]+)\)')
TONES = {"AppText": "0xFF1A1A1A", "AppText2": "0xFF424244"}


def lum(hexcolor):
    v = int(hexcolor, 16) & 0xFFFFFF
    out = []
    for shift in (16, 8, 0):
        c = ((v >> shift) & 0xFF) / 255.0
        out.append(c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4)
    r, g, b = out
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def ratio(a, b):
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


scenes = SCENE.findall(open(SD, encoding="utf-8").read())
th = open(TH, encoding="utf-8").read()
for name, hexv in TONES.items():
    m = re.search(r'val %s = Color\((0x[0-9A-Fa-f]+)\)' % name, th)
    if m:
        TONES[name] = m.group(1)

L = []
L.append("== SCENES 背景色 vs 正文色 对比度（硬指标 >= 7:1） ==")
L.append("正文色: " + ", ".join("%s=%s" % (k, v) for k, v in TONES.items()))
L.append("%-12s %-12s %-8s %-8s %s" % ("scene", "color", "AppText", "AppText2", "判定"))
worst = (99.0, "")
for sid, name, icon, color in scenes:
    r1 = ratio(color, TONES["AppText"])
    r2 = ratio(color, TONES["AppText2"])
    ok = "OK" if min(r1, r2) >= 7.0 else "**< 7:1**"
    worst = min(worst, (min(r1, r2), sid))
    L.append("%-12s %-12s %-8.2f %-8.2f %s" % (sid, color, r1, r2, ok))
L.append("")
L.append("最差项: %s %.2f:1" % (worst[1], worst[0]))
open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(L))
print("OK ->", OUT)
