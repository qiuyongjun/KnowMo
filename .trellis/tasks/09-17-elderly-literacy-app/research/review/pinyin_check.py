# -*- coding: utf-8 -*-
"""逐条判定拼音差异 + 扫全库无声调拼音（数据约定：无调号 = 轻声）。"""
import re, os
from pypinyin import pinyin, Style

WB = r"D:\QYJ\MyProject\Mando\android-app\app\src\main\java\com\qyj\shibang\data\WordBank.kt"
OUT = r"D:\QYJ\MyProject\Mando\.trellis\tasks\09-17-elderly-literacy-app\research\review\pinyin_check.md"

src = open(WB, encoding="utf-8").read()
PAT = re.compile(
    r'term\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*\)'
)
rows = []
for m in PAT.finditer(src):
    tid, scene, text, py, tip = m.groups()
    rows.append({"id": tid, "scene": scene, "text": text, "glp": py.split(" "), "tip": tip})
print("parsed terms:", len(rows))

TONE_RE = re.compile(r"[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]")

def pp(text, **kw):
    r = pinyin(text, style=Style.TONE, **kw)
    return [x[0] for x in r]

L = []
w = L.append
w("# 拼音逐条判定\n")
w("> 数据约定（已从词库自身证实）：`豆腐 → dòu fu`、`便宜 → pián yi`、`太贵了 → tài guì le`")
w("> —— **无调号即表示轻声**。因此凡是无声调却不该读轻声的字，就是漏标调号。\n")

# ---------- 1. 与 pypinyin 的差异 ----------
w("## 1. 与 pypinyin 的逐字差异（共 11 处）\n")
w("| id | 词条 | 字 | GLM | pypinyin | 谁是错的 | 依据 |")
w("|---|---|---|---|---|---|---|")
KEY = {
    "豆腐": "现汉：dòufu（腐轻声）→ **GLM 对**",
    "司机师傅": "现汉：shīfu（傅轻声）→ **GLM 对**",
    "消息": "现汉：xiāoxi（息轻声）→ **GLM 对**",
    "发照片": "现汉：zhàopiàn。pypinyin 把「照片」误读 piān → **GLM 对**",
    "音量调大": "「调」表「调整」读 tiáo。pypinyin 误读 diào → **GLM 对**",
    "字体调大": "同上 → **GLM 对**",
    "扫码出库": "码 只有 mǎ 一个读音 → **GLM 错**（má 不成立）",
    "流血了": "现汉：流血 liúxuè（文读）；口语 liúxiě 亦可 → 两者皆可，保留 GLM",
    "不要乱动": "「不」在四声前变调 bú。注音教学通常仍写 bù → 两者皆可，保留 GLM",
    "晚上": "现汉：wǎnshang（上轻声）→ **GLM 对**",
    "早晨": "现汉：zǎochén（晨 chén，非轻声）→ **GLM 错**（漏调号）",
}

diffs = []
for r in rows:
    text, glp = r["text"], r["glp"]
    if len(glp) != len(text):
        diffs.append((r["id"], text, "—", "字数/拼音数不符 " + str(len(glp)) + "/" + str(len(text)), "GLM 错", ""))
        continue
    p = pp(text)
    if len(p) != len(text):
        continue
    for i, (ch, g) in enumerate(zip(text, glp)):
        if g != p[i]:
            diffs.append((r["id"], text, ch, "%s | %s" % (g, p[i]), "", KEY.get(text, "")))

for d in diffs:
    w("| %s | %s | %s | %s | | %s |" % (d[0], d[1], d[2], d[3].split(" | ")[0], d[4]))
w("")

# ---------- 2. 无声调拼音清单 ----------
w("## 2. 全库「无声调」拼音清单（约定应为轻声，逐条确认）\n")
w("| id | 词条 | 字 | 标音 | 是否真为轻声 |")
w("|---|---|---|---|---|")
toneless = []
for r in rows:
    for ch, g in zip(r["text"], r["glp"]):
        if g and not TONE_RE.search(g):
            toneless.append((r["id"], r["text"], ch, g))
            w("| %s | %s | %s | %s | |" % (r["id"], r["text"], ch, g))
w("")
w("> 共 %d 处。除「早晨·晨」外，其余均需人工过一眼。\n" % len(toneless))
print("diff:", len(diffs), "toneless:", len(toneless))

# ---------- 3. 待修清单 ----------
w("## 3. 待修清单\n")
w("| id | 词条 | 字 | 现值 | 改为 |")
w("|---|---|---|---|---|")
FIX = []
for r in rows:
    text, glp = r["text"], r["glp"]
    for i, (ch, g) in enumerate(zip(text, glp)):
        if (text == "扫码出库" and ch == "码") or (text == "早晨" and ch == "晨"):
            FIX.append((r["id"], text, ch, g))
for f in FIX:
    new = {"码": "mǎ", "晨": "chén"}[f[2]]
    w("| %s | %s | %s | %s | **%s** |" % (f[0], f[1], f[2], f[3], new))
w("")
open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(L))

for r in rows:
    if r["text"] in ("扫码出库", "早晨", "豆腐", "司机师傅", "消息", "发照片", "音量调大", "流血了", "不要乱动", "晚上"):
        print("%-8s GLM=%-22s PP=%s" % (r["text"], " ".join(r["glp"]), " ".join(pp(r["text"]))))
