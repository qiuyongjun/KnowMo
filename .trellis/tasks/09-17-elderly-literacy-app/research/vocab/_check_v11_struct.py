# -*- coding: utf-8 -*-
"""WordBank.kt 结构平衡 + 列表边界检查（v11 独立复核）。"""
WB = r"D:\QYJ\MyProject\Mando\android-app\app\src\main\java\com\knowmo\app\data\WordBank.kt"
src = open(WB, encoding="utf-8").read()
lines = src.split("\n")

# 去字符串字面量与注释后统计括号
out, i, n = [], 0, len(src)
state = None  # None / '"' / '//' / '/*'
while i < n:
    c = src[i]
    if state is None:
        if src.startswith("//", i):
            state = "//"; i += 2; continue
        if src.startswith("/*", i):
            state = "/*"; i += 2; continue
        if c == '"':
            state = '"'; out.append(" "); i += 1; continue
        out.append(c); i += 1; continue
    if state == "//":
        if c == "\n":
            state = None; out.append(c)
        else:
            out.append(" ")
        i += 1; continue
    if state == "/*":
        if src.startswith("*/", i):
            state = None; out.append("  "); i += 2; continue
        out.append("\n" if c == "\n" else " "); i += 1; continue
    if state == '"':
        if c == "\\":
            out.append("  "); i += 2; continue
        if c == '"':
            state = None; out.append(" "); i += 1; continue
        out.append("\n" if c == "\n" else " "); i += 1; continue

code = "".join(out)
for op, cl in (("{", "}"), ("(", ")"), ("[", "]")):
    print("%s%s  count = %d / %d  平衡: %s" % (op, cl, code.count(op), code.count(cl), code.count(op) == code.count(cl)))
# 括号配对顺序
stack, bad = [], []
for idx, ch in enumerate(code):
    if ch in "({[":
        stack.append(ch)
    elif ch in ")}]":
        if not stack:
            bad.append((idx, "多余闭括号 " + ch)); break
        o = stack.pop()
        if "({[".index(o) != ")}]".index(ch):
            bad.append((idx, "不匹配 %s vs %s" % (o, ch))); break
if stack:
    bad.append((0, "未闭合 %s" % stack))
print("配对:", "OK" if not bad else bad)
print("字符串状态收尾:", state)

# FOOD 列表边界
start = next(k for k, l in enumerate(lines, 1) if l.strip().startswith("private val FOOD = listOf("))
end = next(k for k in range(start, len(lines) + 1) if lines[k - 1].strip() == ")")
print("FOOD 起止行:", start, end, " 行数:", end - start + 1)
print("STUDY_TERMS 处 FOOD 出现次数:", src.count("+ FOOD +"))
print("顶层 val 列表:", [l.strip().split(" ")[2] for l in lines if l.strip().startswith("private val ") and "= listOf(" in l])
print("total lines:", len(lines), "末尾空行:", repr(lines[-1]), repr(lines[-2]))
