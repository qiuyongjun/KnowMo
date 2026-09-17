# -*- coding: utf-8 -*-
"""静态一致性核查（本机无 JDK，用符号级检查代替编译）。

检查项：
1. DoneCard.kt 的 Box 导入是否补回
2. ChannelBar 定义签名 vs AppRoot 调用处
3. AppRoot 里每个 repo.X(...) 调用，StudyRepository 是否都有定义
4. 毕业相关符号：定义齐备 + 均被引用
5. 全仓库不得出现已删除符号（REC_QUEUE / Term.Kind / ProgressRow / RedBg / BadgeDot / ProgressTrack / addForgot 调用）
6. 括号/花括号配平（粗检语法）
"""
import os, re, io

ROOT = r"D:\QYJ\MyProject\Mando\android-app\app\src\main\java\com\qyj\shibang"
def rd(p):
    with io.open(p, encoding="utf-8-sig") as f:
        return f.read()

files = {}
for r, ds, fs in os.walk(ROOT):
    for f in fs:
        if f.endswith(".kt"):
            p = os.path.join(r, f)
            files[p] = rd(p)

def find(name):
    return [p for p in files if os.path.basename(p) == name]

issues, oks = [], []
def chk(cond, msg):
    (oks if cond else issues).append(msg)

# 1 DoneCard Box 导入
dc = rd(find("DoneCard.kt")[0])
chk("import androidx.compose.foundation.layout.Box" in dc, "DoneCard.kt 已导入 Box")
chk(len(re.findall(r"\bBox\s*\(", dc)) > 0, "DoneCard.kt 确实用到 Box")

# 2 ChannelBar 签名 vs 调用
cm = rd(find("Common.kt")[0])
sig = re.search(r"fun ChannelBar\(([^)]*)\)", cm)
chk(sig is not None, "ChannelBar 定义存在")
if sig:
    params = [p.strip().split(":")[0].strip() for p in sig.group(1).split(",")]
    chk("graduated" in params, "ChannelBar 签名含 graduated 参数: %s" % params)
ar = rd(find("AppRoot.kt")[0])
call = re.search(r"ChannelBar\((.*?)\)", ar)
chk(call is not None, "AppRoot 调用了 ChannelBar")
if call and "graduated" in params if sig else False:
    chk("graduated" in call.group(1), "AppRoot 调用传了 graduated: %s" % call.group(1))

# 3 repo.X 调用 vs 定义
sr = rd(find("StudyRepository.kt")[0])
defined = set(re.findall(r"\bfun\s+(\w+)\s*\(", sr)) | set(re.findall(r"\bval\s+(\w+)\s*[:=]", sr))
called = set(re.findall(r"\brepo\.(\w+)", ar))
missing = sorted(c for c in called if c not in defined)
chk(not missing, "AppRoot 调用的 repo.X 全部有定义 (缺: %s)" % missing)

# 4 毕业符号
for sym in ["isSceneGraduated", "graduatedScenes", "freePoolScopeIds", "GRADUATED_DAYS"]:
    chk(("fun %s" % sym) in sr or ("val %s" % sym) in sr or ("GRADUATED_DAYS" in sr and sym == "GRADUATED_DAYS"),
        "%s 已定义" % sym)
chk("graduatedScenes()" in ar, "AppRoot 调用 graduatedScenes()")
chk("freePoolScopeIds(channel)" in sr, "freePoolIds 改用 freePoolScopeIds")
chk("scopeIds(channel)" in sr and "freePoolScopeIds(channel)" in sr, "两套 scope 并存")

# 5 已删除符号
dead = ["REC_QUEUE", "Term.Kind", "ProgressRow", "RedBg", "BadgeDot", "ProgressTrack"]
for d in dead:
    hits = [os.path.basename(p) for p, s in files.items() if d in s]
    chk(not hits, "无残留 %s (命中: %s)" % (d, hits))
chk("repo.addForgot" not in ar, "AppRoot 未调用 addForgot")

# 6 粗检括号配平
for p, s in files.items():
    code = re.sub(r'"(?:[^"\\]|\\.)*"', '""', s)
    code = re.sub(r"/\*.*?\*/", "", code, flags=re.S)
    code = re.sub(r"//[^\n]*", "", code)
    chk(code.count("{") == code.count("}"), "%s 花括号配平" % os.path.basename(p))

print("=== OK (%d) ===" % len(oks))
for o in oks:
    print("  +", o)
print("=== ISSUES (%d) ===" % len(issues))
for i in issues:
    print("  !", i)

# 附：毕业规则数值自检（把 Kotlin 阶梯搬到 Python 验一遍）
INTERVALS = [1, 3, 7, 15]
def next_interval(cur):
    i = INTERVALS.index(cur) if cur in INTERVALS else 0
    return INTERVALS[min(i + 1, len(INTERVALS) - 1)]
print("\n=== 阶梯自检 ===")
cur, steps = 1, 0
for _ in range(5):
    nxt = next_interval(cur)
    if nxt != cur:
        steps += 1
    print("  %2d -> %2d" % (cur, nxt))
    cur = nxt
print("  从 1 升到封顶需要连续认识次数 =", steps, "（应为 3）")
print("  封顶后再答认识仍为", next_interval(15), "（不退出池）")
