#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""doc 编号 / §引用 / 文档-代码一致性 静态检查（只读）"""
import os
import re

# 根目录自推导（向上找 android-app），Mando→KnowMo 改名前后均可用。
# 注：SRC 的 com/qyj/shibang 子路径为 v3 时点冻结产物，不随改名更新。
ROOT = os.path.dirname(os.path.abspath(__file__))
while not os.path.isdir(os.path.join(ROOT, "android-app")):
    ROOT = os.path.dirname(ROOT)
TASK = os.path.join(ROOT, ".trellis", "tasks", "09-17-elderly-literacy-app")
SRC = os.path.join(ROOT, "android-app", "app", "src", "main", "java", "com", "qyj", "shibang")


def read(p):
    return open(p, encoding="utf-8").read()


print("== design.md 标题编号 ==")
d = read(os.path.join(TASK, "design.md"))
nums = []
for i, line in enumerate(d.splitlines(), 1):
    m = re.match(r"^(#{2,3})\s+(\d+(?:\.\d+)?)", line)
    if m:
        nums.append((i, m.group(1), m.group(2), line.strip()[:60]))
for n in nums:
    print("  L%-4d %-3s %-5s %s" % n)
h2 = [n[2] for n in nums if n[1] == "##"]
print("  ## 序号: %s" % h2)
print("  连续无重号: %s" % (h2 == [str(i) for i in range(1, len(h2) + 1)]))
sub = [n[2] for n in nums if n[1] == "###"]
print("  ### 序号: %s" % sub)

print()
print("== prd.md 段落 ==")
p = read(os.path.join(TASK, "prd.md"))
for i, line in enumerate(p.splitlines(), 1):
    if line.startswith("###"):
        print("  L%-4d %s" % (i, line.strip()))

print()
print("== implement.md 段落 ==")
im = read(os.path.join(TASK, "implement.md"))
for i, line in enumerate(im.splitlines(), 1):
    if line.startswith("##"):
        print("  L%-4d %s" % (i, line.strip()))
steps = re.findall(r"^(\d+)\.\s+\[( |x)\]", im, re.M)
print("  步骤勾选: %s" % [(n, "done" if c == "x" else "TODO") for n, c in steps])

print()
print("== 源码/文档中的 § 引用是否都有对应标题 ==")
sections = set(n[2] for n in nums)
for dirpath, _, files in os.walk(SRC):
    for f in files:
        if not f.endswith(".kt"):
            continue
        fp = os.path.join(dirpath, f)
        for i, line in enumerate(read(fp).splitlines(), 1):
            for ref in re.findall(r"§\s*(\d+(?:\.\d+)?)", line):
                ok = ref in sections
                print("  %s:%d  §%s  ->  %s" % (f, i, ref, "存在" if ok else "!! 无此节"))
for name in ("design.md", "implement.md", "prd.md"):
    for i, line in enumerate(read(os.path.join(TASK, name)).splitlines(), 1):
        for ref in re.findall(r"§\s*(\d+(?:\.\d+)?)", line):
            ok = ref in sections
            print("  %s:%d  §%s  ->  %s" % (name, i, ref, "存在" if ok else "!! 无此节"))

print()
print("== 文档-代码关键词一致性 ==")
checks = [
    ("GRADUATED_DAYS", os.path.join(SRC, "data", "StudyRepository.kt")),
    ("isSceneGraduated", os.path.join(SRC, "data", "StudyRepository.kt")),
    ("graduatedScenes", os.path.join(SRC, "data", "StudyRepository.kt")),
    ("freePoolScopeIds", os.path.join(SRC, "data", "StudyRepository.kt")),
    ("freePoolIds", os.path.join(SRC, "data", "StudyRepository.kt")),
    ("INTERVALS.last()", os.path.join(SRC, "data", "StudyRepository.kt")),
    ("graduatedScenes()", os.path.join(SRC, "ui", "AppRoot.kt")),
    ("graduated: Set<String>", os.path.join(SRC, "ui", "Common.kt")),
]
for k, fp in checks:
    txt = read(fp)
    locs = [i for i, l in enumerate(txt.splitlines(), 1) if k in l]
    docs = []
    for name in ("design.md", "prd.md", "implement.md"):
        dt = read(os.path.join(TASK, name))
        if k.split("(")[0].split(":")[0].split(".")[0] in dt:
            docs.append(name)
    print("  %-22s 代码 %s  文档提及 %s" % (k, locs, docs or "无"))

print()
print("== design.md §8.6 效果表 vs 代码 逐条 ==")
tbl = [
    ("推荐频道队列不再排入", "StudyRepository.kt", "STUDY_TERMS.filter { it.scene !in graduated }"),
    ("频道栏 🎓 + 绿色", "Common.kt", 'if (isGraduated) "${s.icon} ${s.name} 🎓"'),
    ("场景频道不受影响", "StudyRepository.kt", 'STUDY_TERMS.filter { it.scene == channel }'),
    ("自由刷池含已毕业分区", "StudyRepository.kt", 'if (channel == "rec") STUDY_TERMS.map { it.id }'),
    ("场景频道不排除毕业", "StudyRepository.kt", "freePoolScopeIds(channel).filter { termStates[it] != null }"),
    ("生效时机=次日", "StudyRepository.kt", "if (existing != null && existing.date == today()) return existing"),
    ("可逆（忘了->1）", "StudyRepository.kt", "termStates[id] = TermState(1, today())"),
    ("ChannelBar 传参", "AppRoot.kt", "ChannelBar(current = channel, graduated = graduated"),
]
repo = read(os.path.join(SRC, "data", "StudyRepository.kt"))
common = read(os.path.join(SRC, "ui", "Common.kt"))
approot = read(os.path.join(SRC, "ui", "AppRoot.kt"))
for label, where, snippet in tbl:
    hay = {"StudyRepository.kt": repo, "Common.kt": common, "AppRoot.kt": approot}[where]
    print("  %-16s %-20s %s" % (label, where, "命中" if snippet in hay else "!! 未命中"))
