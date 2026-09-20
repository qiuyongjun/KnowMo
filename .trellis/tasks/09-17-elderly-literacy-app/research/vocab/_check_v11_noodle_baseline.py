# -*- coding: utf-8 -*-
"""food-33..65 与 scene_noodle.fragment.kt 基线逐条比对（v11 独立复核）。"""
import re
WB = r"D:\QYJ\MyProject\Mando\android-app\app\src\main\java\com\knowmo\app\data\WordBank.kt"
FR = r"D:\QYJ\MyProject\Mando\.trellis\tasks\09-17-elderly-literacy-app\research\vocab\scene_noodle.fragment.kt"
T = re.compile(r'term\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*\)')

def rows(p):
    return [(i, T.search(l).groups()) for i, l in enumerate(open(p, encoding="utf-8").read().split("\n"), 1) if T.search(l)]

wb = {int(r[1][0].split("-")[1]): (r[0], r[1]) for r in rows(WB) if r[1][0].startswith("food-")}
fr = {int(r[1][0].split("-")[1]): (r[0], r[1]) for r in rows(FR)}

# noodle -> food 映射：adopted = noodle 去掉 16（微辣，未采用）；v10 删掉的 19/24/28 仍占 id（空号）
adopted = [n for n in range(1, 35) if n != 16]
deleted = {19, 24, 28}
fid = 33
bad = 0
for nid in adopted:
    src = fr[nid][1]
    tgt = wb.get(fid)
    if nid in deleted:
        if tgt is not None:
            print("!! food-%d 应为空号(v10 删)却存在: %s" % (fid, tgt[1][2])); bad += 1
    elif tgt is None:
        print("!! food-%d 缺失（对应 noodle-%d %s）" % (fid, nid, src[2])); bad += 1
    else:
        # 比对 text/pinyin/tip（id 与 scene 必然不同）
        if src[2:] != tgt[1][2:]:
            print("!! 内容漂移 food-%d vs noodle-%d: %s | %s" % (fid, nid, src[2:], tgt[1][2:])); bad += 1
    fid += 1
print("noodle 基线比对: %d 条，漂移 %d 条" % (len(adopted), bad))
print("未被采用的 noodle-16:", fr[16][1][2])
print("v10 删除对应: food-50<=noodle-19", fr[19][1][2], "| food-55<=noodle-24", fr[24][1][2], "| food-59<=noodle-28", fr[28][1][2])
print("上述 3 个 id 在现库存在？", [k for k in (50, 55, 59) if k in wb])
