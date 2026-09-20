#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Kotlin 逐符号导入核查（启发式，只读）。
做法: 去注释/字符串 -> 收集每个文件里"大写开头的标识符" -> 判定是否可由
  (a) 本文件 import  (b) 本文件顶层/嵌套声明  (c) 同包其它文件的声明
  (d) Kotlin 内置类型白名单  (e) 前导 '.' 的成员访问（跳过）  (f) 注解白名单
解释。剩下未解析的即为可疑的"漏网导入"。
"""
import os
import re

# 根目录自推导（向上找 android-app），Mando→KnowMo 改名前后均可用
ROOT = os.path.dirname(os.path.abspath(__file__))
while not os.path.isdir(os.path.join(ROOT, "android-app")):
    ROOT = os.path.dirname(ROOT)
SRC = os.path.join(ROOT, "android-app", "app", "src", "main", "java", "com", "knowmo", "app")

BUILTIN = set("""Int Long Short Byte Float Double Boolean Char String Unit Nothing Any
List MutableList Set MutableSet Map MutableMap Collection Iterable Sequence Triple Pair Array
ArrayList ArrayDeque LinkedHashMap LinkedHashSet HashMap HashSet StringBuilder Regex Result Lazy
Comparable Number Exception RuntimeException Throwable IndexOutOfBoundsException NumberFormatException
Class Function Unit Void Nullable Nothing""".split())
ANNOT = set("OptIn Suppress Deprecated Volatile JvmStatic JvmField JvmOverloads Override"
            "Composable Preview SuppressLint SdkSuppress".split())


def strip_code(txt):
    out = []
    i = 0
    n = len(txt)
    while i < n:
        c = txt[i]
        if c == '/' and i + 1 < n and txt[i + 1] == '/':
            j = txt.find('\n', i)
            i = n if j < 0 else j
        elif c == '/' and i + 1 < n and txt[i + 1] == '*':
            j = txt.find('*/', i)
            i = n if j < 0 else j + 2
        elif c == '"' and txt[i:i + 3] == '"""':
            j = txt.find('"""', i + 3)
            out.append(' ')   # 多行字符串整段丢弃
            i = n if j < 0 else j + 3
        elif c == '"':
            i += 1
            while i < n:
                if txt[i] == '\\':
                    i += 2
                    continue
                if txt[i] == '"':
                    i += 1
                    break
                if txt[i] == '\n':
                    break
                i += 1
            out.append(' ')
        elif c == "'":
            i += 1
            while i < n and txt[i] != "'":
                i += 2 if txt[i] == '\\' else 1
            i += 1
            out.append(' ')
        else:
            out.append(c)
            i += 1
    return ''.join(out)


files = {}
for dp, _, fs in os.walk(SRC):
    for f in fs:
        if f.endswith(".kt"):
            fp = os.path.join(dp, f)
            files[fp] = open(fp, encoding="utf-8").read()

pkg_decls = {}
for fp, txt in files.items():
    pkg = re.search(r"^package\s+([\w.]+)", txt, re.M).group(1)
    code = strip_code(txt)
    decls = set(re.findall(r"\b(?:fun|class|interface|object|enum class|data class|data object|val|var|typealias)\s+([A-Z]\w*)", code))
    decls |= set(re.findall(r"\benum class\s+(\w+)", code))
    pkg_decls.setdefault(pkg, set()).update(decls)

print("== 包内顶层声明（大写开头） ==")
for pkg, ds in sorted(pkg_decls.items()):
    print("  %-28s %s" % (pkg, sorted(ds)))

print()
print("== 逐文件符号解析 ==")
total_susp = 0
for fp, txt in sorted(files.items()):
    pkg = re.search(r"^package\s+([\w.]+)", txt, re.M).group(1)
    code = strip_code(txt)
    imports = set()
    for m in re.findall(r"^import\s+([\w.]+)(?:\s+as\s+(\w+))?", code, re.M):
        imports.add(m[1] or m[0].split(".")[-1])
    local = set(re.findall(r"\b(?:fun|class|interface|object|enum class|data class|data object|val|var|typealias)\s+([A-Z]\w*)", code))
    local |= set(re.findall(r"\b(?:enum class|data class|data object)\s+(\w+)", code))
    # 局部 fun / lambda 参数 / 形参 里的大写名（保守加入）
    local |= set(re.findall(r"\b([A-Z]\w*)\s*[:=]\s*remember\b", code))
    local |= set(re.findall(r"\bfun\s+([a-z]\w*)", code))  # 小写 fun 名不算
    tokens = {}
    for m in re.finditer(r"(?<![\w.@])([A-Z]\w*)", code):   # 前导 '.' 或 '@' 的不看（成员访问/注解另判）
        name = m.group(1)
        tokens.setdefault(name, code[:m.start()].count("\n") + 1)
    for m in re.finditer(r"@([A-Z]\w*)", code):
        if m.group(1) not in ANNOT:
            tokens.setdefault(m.group(1), code[:m.start()].count("\n") + 1)
    suspicious = []
    for name, ln in sorted(tokens.items(), key=lambda kv: kv[1]):
        if name in imports or name in local or name in BUILTIN or name in ANNOT:
            continue
        if name in pkg_decls.get(pkg, set()):
            continue
        suspicious.append((ln, name))
    rel = os.path.relpath(fp, SRC)
    print("  --- %s (imports=%d)" % (rel, len(imports)))
    if suspicious:
        for ln, name in suspicious:
            print("      L%-4d %s   <- 未在本文件 import / 本包声明 / 内置白名单中" % (ln, name))
        total_susp += len(suspicious)
    else:
        print("      全部符号可解析")

print()
print("可疑符号总数: %d（需逐条人工判定，可能是泛型参数/局部变量/枚举项）" % total_susp)
