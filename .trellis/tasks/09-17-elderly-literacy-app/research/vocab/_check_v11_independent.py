# -*- coding: utf-8 -*-
"""v11 独立复核：38 条新词逐字比对 + id 纪律 + 计数口径。"""
import re, os, collections

WB = r"D:\QYJ\MyProject\Mando\android-app\app\src\main\java\com\knowmo\app\data\WordBank.kt"
FRAG = r"D:\QYJ\MyProject\Mando\.trellis\tasks\09-17-elderly-literacy-app\research\vocab\scene_noodle_extra.fragment.kt"
SD = r"D:\QYJ\MyProject\Mando\android-app\app\src\main\java\com\knowmo\app\data\StudyData.kt"
TERM = re.compile(r'term\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*\)')

src = open(WB, encoding="utf-8").read().split("\n")
frag = open(FRAG, encoding="utf-8").read().split("\n")

def terms(lines):
    out = []
    for i, l in enumerate(lines, 1):
        m = TERM.search(l)
        if m:
            out.append((i, m.groups()))
    return out

allw = terms(src)
fragt = terms(frag)

new_w = [t for t in allw if 66 <= int(t[1][0].split("-")[1]) <= 103 and t[1][0].startswith("food-")]
print("WordBank 新词条数:", len(new_w), " fragment 行数:", len(fragt))

ok = True
for (wl, w), (fl, f) in zip(new_w, fragt):
    if w != f:
        ok = False
        print("!! 不一致 WB L%d %s  vs  FRAG L%d %s" % (wl, w, fl, f))
print("逐字 identical:", ok)

# id 连续性
ids = [int(t[1][0].split("-")[1]) for t in new_w]
print("新 id 严格连续 66..103:", ids == list(range(66, 104)))

food_ids = sorted(int(t[1][0].split("-")[1]) for t in allw if t[1][0].startswith("food-"))
print("food id 数:", len(food_ids), " min/max:", food_ids[0], food_ids[-1])
gaps = [n for n in range(1, max(food_ids) + 1) if n not in food_ids]
print("food 空号:", gaps, "== [3,7,9,17,25,31,32,50,55,59]:", gaps == [3,7,9,17,25,31,32,50,55,59])

# 全库 id 唯一
ids_all = [t[1][0] for t in allw]
print("全库 id 总数/唯一:", len(ids_all), len(set(ids_all)))

# 各分区计数
per = collections.Counter(t[1][1] for t in allw)
print("总数:", len(allw), "分区:", len(per), dict(per))

# 配对（UTF-16 code unit 模拟：BMP 汉字均为 1 个 code unit，等价 len()）
bad = [t[1][0] for t in allw if len(t[1][2]) != len(t[1][3].split(" "))]
print("配对不符:", bad)
# 非 BMP 字符（会用 2 个 UTF-16 unit）排查
nonsbmp = [(t[1][0], c) for t in allw for c in t[1][2] if ord(c) > 0xFFFF]
print("非BMP字符(会影响 length):", nonsbmp)

# scene ⊂ SCENES
sd = open(SD, encoding="utf-8").read()
scene_ids = re.findall(r'Scene\(\s*"([^"]*)"', sd)
print("孤儿 scene:", sorted(set(per) - set(scene_ids)))

# 单字词条
one = [(t[1][0], t[1][2], t[1][3]) for t in allw if len(t[1][2]) == 1]
print("单字词条:", one)
print("最长词条(字数):", max(len(t[1][2]) for t in allw), [t[1][0] for t in allw if len(t[1][2]) == max(len(x[1][2]) for x in allw)])

# 新旧文本重复（food 分区内）
food_texts = collections.Counter(t[1][2] for t in allw if t[1][1] == "food")
print("food 内重词:", [k for k, v in food_texts.items() if v > 1])

# 新词 vs 全库其它分区重词
newtexts = {t[1][2] for t in new_w}
others = collections.defaultdict(list)
for t in allw:
    if t[1][2] in newtexts:
        others[t[1][2]].append(t[1][0])
print("新词文本与全库重合:", {k: v for k, v in others.items() if len(v) > 1})

# 拼音逐字（新词）打印
print("\n--- 38 条逐条 ---")
for t in new_w:
    print(t[1][0], t[1][2], "|", t[1][3], "|", t[1][4])
