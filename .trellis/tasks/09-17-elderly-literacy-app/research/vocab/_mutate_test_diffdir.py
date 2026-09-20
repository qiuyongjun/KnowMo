# -*- coding: utf-8 -*-
"""变异测试：证明 check_appsettings_load.py 的「差异列方向」断言真能拦住回归。

为什么需要这个文件（质检 agent 实测出来的）：
    脚本首版把差异列算成了 `new - old`（方向反），于是每行都显示「无」，
    而当时的断言写的是 `old != new` —— 这个条件**照样为真**，脚本全绿。
    也就是说：断言没有绑定到「呈现出来的那个量」，拦不住它自称要拦的回归。
    本机无编译器，这类脚本是唯一防线，防线自己假绿是最坏的情况。

做法：
    读 `check_appsettings_load.py`，把差异列那一行改回反向，跑一遍，
    **期望它失败**。若变异后仍全绿，说明加固无效。

用法：python _mutate_test_diffdir.py
产出：_mutate_test_diffdir.txt；退出码 0 = 加固有效
"""
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "check_appsettings_load.py")
MUT = os.path.join(HERE, "_mutant_diffdir.py")
OUT = os.path.join(HERE, "_mutate_test_diffdir.txt")

orig = open(SRC, encoding="utf-8").read()

GOOD = 'd = [i for i in vis_old if i not in vis_new]'
BAD = 'd = [i for i in vis_new if i not in vis_old]'   # ← 反向（首版就是这么写的）

lines = []
if GOOD not in orig:
    lines.append("✗ 参照行未找到，本测试自身失效：%r" % GOOD)
    ok = False
else:
    mutant = orig.replace(GOOD, BAD, 1)
    open(MUT, "w", encoding="utf-8", newline="\n").write(mutant)
    try:
        p = subprocess.run([sys.executable, MUT], cwd=HERE,
                           capture_output=True, text=True, encoding="utf-8", errors="replace")
        out = (p.stdout or "") + (p.stderr or "")
        m = re.search(r"case_fails=(\d+)", out)
        got = int(m.group(1)) if m else None
        lines.append("变异：%s  →  %s" % (BAD, GOOD))
        lines.append("变异体实跑输出：%s" % out.strip().replace("\n", " | "))
        if got is None:
            lines.append("✗ 变异体没跑起来或输出不含 case_fails —— 测试本身有问题")
            ok = False
        elif got > 0:
            lines.append("✓ 变异体 case_fails=%d（>0）—— 断言**拦住了**差异列反向的回归" % got)
            ok = True
        else:
            lines.append("✗ 变异体 case_fails=0 —— 断言**没拦住**，加固无效")
            ok = False
    finally:
        if os.path.exists(MUT):
            os.remove(MUT)

# 顺带确认正常版本仍绿（否则就是加固把真身搞坏了）
p2 = subprocess.run([sys.executable, SRC], cwd=HERE,
                    capture_output=True, text=True, encoding="utf-8", errors="replace")
out2 = (p2.stdout or "") + (p2.stderr or "")
m2 = re.search(r"static_fails=(\d+) case_fails=(\d+)", out2)
lines.append("")
lines.append("未变异版本：%s" % (out2.strip().replace("\n", " | ") or "（无输出）"))
if m2 and m2.group(1) == "0" and m2.group(2) == "0":
    lines.append("✓ 未变异版本仍全绿（加固没有误伤真身）")
else:
    lines.append("✗ 未变异版本不绿 —— 先修 check_appsettings_load.py 本身")
    ok = False

lines.append("")
lines.append("**结论：%s**" % ("变异测试通过 —— 该断言确实生效" if ok else "变异测试失败 —— 断言未生效，必须重做"))

open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(lines) + "\n")
print("\n".join(lines))
sys.exit(0 if ok else 1)
