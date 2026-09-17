#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""import 解析 + 重复声明扫描（只读）"""
import os
import re
from collections import defaultdict

ROOT = r"D:\QYJ\MyProject\Mando"
SRC = os.path.join(ROOT, "android-app", "app", "src", "main", "java", "com", "qyj", "shibang")

files = {}
for dp, _, fs in os.walk(SRC):
    for f in fs:
        if f.endswith(".kt"):
            fp = os.path.join(dp, f)
            files[fp] = open(fp, encoding="utf-8").read()


def strip_code(txt):
    txt = re.sub(r"/\*.*?\*/", " ", txt, flags=re.S)
    txt = re.sub(r"//[^\n]*", " ", txt)
    txt = re.sub(r'"""(?:.|\n)*?"""', " ", txt)
    txt = re.sub(r'"(?:\\.|[^"\\\n])*"', " ", txt)
    return txt


pkg_of = {}
decls_by_pkg = defaultdict(set)
dup = []
for fp, txt in files.items():
    code = strip_code(txt)
    pkg = re.search(r"^package\s+([\w.]+)", code, re.M).group(1)
    pkg_of[fp] = pkg
    seen = defaultdict(list)
    for m in re.finditer(r"^\s*(?:(?:public|internal|private|abstract|open|sealed|data|enum|annotation|value|inline|suspend|override|operator|infix|external|const|lateinit|companion|@\w+(?:\([^)]*\))?)\s+)*(fun|class|interface|object|val|var|typealias)\s+(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?(\w+)", code, re.M):
        kw, name = m.group(1), m.group(2)
        seen[name].append(kw)
        decls_by_pkg[pkg].add(name)
    # 顶层 object/枚举常量等不追
    for name, kws in seen.items():
        if len(kws) > 1:
            dup.append((os.path.relpath(fp, SRC), name, kws))

print("== 项目内 import 解析（com.qyj.shibang.*） ==")
bad = 0
for fp, txt in sorted(files.items()):
    for m in re.findall(r"^import\s+(com\.qyj\.shibang\.[\w.]+)(?:\s+as\s+(\w+))?", txt, re.M):
        fq, alias = m
        pkg, _, name = fq.rpartition(".")
        if name not in decls_by_pkg.get(pkg, set()):
            print("  !! %s -> %s  (包 %s 中未找到声明 %s)" % (os.path.relpath(fp, SRC), fq, pkg, name))
            bad += 1
print("  未解析的项目内 import: %d" % bad)

print()
print("== 每文件导入的项目内符号 ==")
for fp, txt in sorted(files.items()):
    imps = [m[0] for m in re.findall(r"^import\s+(com\.qyj\.shibang\.[\w.]+)(?:\s+as\s+(\w+))?", txt, re.M)]
    print("  %-28s %s" % (os.path.relpath(fp, SRC), [i.split(".")[-1] for i in imps]))

print()
print("== 同名声明多次出现（同文件内，可能是合法重载/二次赋值，需人工确认） ==")
byfile = defaultdict(lambda: defaultdict(int))
for fp, txt in files.items():
    code = strip_code(txt)
    for m in re.finditer(r"^\s*(?:(?:public|internal|private|abstract|open|sealed|data|enum|annotation|value|inline|suspend|override|operator|infix|external|const|lateinit|companion|@\w+(?:\([^)]*\))?)\s+)*(fun|class|interface|object|val|var|typealias)\s+(?:<[^>]*>\s*)?(\w+)", code, re.M):
        byfile[fp][(m.group(1), m.group(2))] += 1
for fp, d in sorted(byfile.items()):
    dups = [(k, v) for k, v in d.items() if v > 1]
    if dups:
        print("  %s -> %s" % (os.path.relpath(fp, SRC), dups))
print("（同文件中同名 val/var 重复赋值会被此脚本误报，Kotlin 中 val 二次赋值是编译错误，var 赋值 OK）")

print()
print("== 逐文件统计 ==")
for fp in sorted(files):
    print("  %-28s %d 行" % (os.path.relpath(fp, SRC), len(files[fp].splitlines())))
