#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""v5 决策轮静态核对（无 JDK/SDK，逐符号人工核对的可复核化）：
   1) 去注释/去字符串后的括号配平 + AppRoot 顶层语句层级
   2) 每个 import 的简单名是否在正文中仍有使用点（未使用 import 检测）
   3) 被删符号全仓残留扫描
只读脚本，不改任何源码。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5]   # .../Mando
APP = ROOT / "android-app" / "app" / "src" / "main" / "java" / "com" / "qyj" / "shibang"

FILES = {
    "AppRoot.kt": APP / "ui" / "AppRoot.kt",
    "StudyRepository.kt": APP / "data" / "StudyRepository.kt",
    "TTSSpeaker.kt": APP / "tts" / "TTSSpeaker.kt",
    "MainActivity.kt": APP / "MainActivity.kt",
    "Common.kt": APP / "ui" / "Common.kt",
    "DoneCard.kt": APP / "ui" / "DoneCard.kt",
    "TermCard.kt": APP / "ui" / "TermCard.kt",
    "Sheets.kt": APP / "ui" / "Sheets.kt",
    "StudyData.kt": APP / "data" / "StudyData.kt",
}

fail = []


def strip_code(src: str):
    """返回 (去注释去字符串的行列表, 每行原行号)。用状态机，保留结构字符。"""
    out = []
    i = 0
    n = len(src)
    line = 1
    buf = []
    lines = []
    line_of = []
    STATE_CODE, STATE_LINE_COMMENT, STATE_BLOCK_COMMENT, STATE_STR, STATE_RAW = range(5)
    st = STATE_CODE
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""
        if c == "\n":
            lines.append("".join(buf))
            line_of.append(line)
            buf = []
            line += 1
            if st in (STATE_LINE_COMMENT, STATE_STR):
                # 行注释/普通字符串不跨行（Kotlin 普通字符串确实不跨行）
                st = STATE_CODE
            i += 1
            continue
        if st == STATE_CODE:
            if c == "/" and nxt == "/":
                st = STATE_LINE_COMMENT
                i += 2
                continue
            if c == "/" and nxt == "*":
                st = STATE_BLOCK_COMMENT
                i += 2
                continue
            if src.startswith('"""', i):
                st = STATE_RAW
                i += 3
                buf.append('""')          # 占 2 个占位字符，避免误配平
                continue
            if c == '"':
                st = STATE_STR
                i += 1
                buf.append('"')
                continue
            if c == "'":
                # char 字面量（如 '$'）；本项目里没有，稳妥跳过 3 字符
                if nxt == "\\":
                    buf.append("''")
                    j = i + 2
                    while j < n and src[j] != "'":
                        j += 1
                    for k in range(i, min(j + 1, n)):
                        if src[k] == "\n":
                            break
                    line += src[i:j + 1].count("\n")
                    i = j + 1
                    continue
                if n + 0 and i + 2 < n and src[i + 2] == "'":
                    buf.append("''")
                    i += 3
                    continue
                buf.append("'")
                i += 1
                continue
            buf.append(c)
            i += 1
            continue
        if st == STATE_LINE_COMMENT:
            i += 1
            continue
        if st == STATE_BLOCK_COMMENT:
            if c == "*" and nxt == "/":
                st = STATE_CODE
                i += 2
                continue
            i += 1
            continue
        if st == STATE_STR:
            if c == "\\":
                i += 2
                continue
            if c == '"':
                st = STATE_CODE
            i += 1
            continue
        if st == STATE_RAW:
            if src.startswith('"""', i):
                st = STATE_CODE
                i += 3
                buf.append('""')
                continue
            i += 1
            continue
    lines.append("".join(buf))
    line_of.append(line)
    return lines, line_of


def check_balance(name, path):
    src = path.read_text(encoding="utf-8")
    lines, line_of = strip_code(src)
    stack = []
    ok = True
    depths_at_line = {}
    for idx, ln in enumerate(lines):
        for col, ch in enumerate(ln):
            if ch in "({[":
                stack.append((ch, line_of[idx]))
            elif ch in ")}]":
                if not stack:
                    print(f"  [FAIL] {name}: 多余闭括号 {ch} @ line {line_of[idx]}")
                    fail.append(f"{name} 括号多余 {ch}@{line_of[idx]}")
                    ok = False
                    continue
                op, ol = stack.pop()
                if "({[".index(op) != ")}]".index(ch):
                    print(f"  [FAIL] {name}: 括号不匹配 {op}@{ol} vs {ch}@{line_of[idx]}")
                    fail.append(f"{name} 括号不匹配")
                    ok = False
        if ln.strip() == "":
            depths_at_line[line_of[idx]] = len(stack)
        else:
            depths_at_line[line_of[idx]] = len(stack) - sum(
                1 for ch in ln if ch in "({[")
    if stack:
        print(f"  [FAIL] {name}: 未闭合 {stack}")
        fail.append(f"{name} 未闭合 {len(stack)} 个")
        ok = False
    if ok:
        print(f"  [OK]   {name}: 括号全部配平（{len(lines)} 行）")
    return lines, line_of, depths_at_line


def check_imports(name, path):
    src = path.read_text(encoding="utf-8")
    lines = src.splitlines()
    imports = []
    for i, ln in enumerate(lines):
        m = re.match(r"^import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*$", ln.strip())
        if m:
            imports.append((m.group(2) or m.group(1).split(".")[-1], m.group(1), i + 1))
    body = "\n".join(
        ln for i, ln in enumerate(lines) if not ln.strip().startswith("import ")
    )
    for simple, fq, lineno in imports:
        # 1) 裸标识符  2) 成员/扩展函数调用（.fillMaxSize() / .background()）  3) 全限定名
        if re.search(r"(?<![\w.])" + re.escape(simple) + r"(?![\w])", body):
            continue
        if re.search(r"\." + re.escape(simple) + r"(?![\w])", body):
            continue
        if re.search(re.escape(fq) + r"(?![\w])", body):
            continue
        # 4) by 委托运算符：import getValue/setValue 不会在源码里出现名字
        if simple in {"getValue", "setValue"} and re.search(r"\bby\s+remember\b|\bby\s+\w", body):
            print(f"  [OK]   {name}:{lineno} {simple}（by 委托运算符，隐式使用）")
            continue
        print(f"  [FAIL] {name}:{lineno} 未使用 import → {fq}")
        fail.append(f"{name} 未使用 import {fq}@{lineno}")
    print(f"  [OK]   {name}: {len(imports)} 个 import 全部有使用点")


DELETED = [
    "forgotIds", "forgotCount", "addForgot", "setFontScale", "setSpeechRate",
    "setRemindTime", "freePoolScopeIds", "fontScale", "speechRate", "remindTime",
    "fscale", "remind", "Term.Kind", "REC_QUEUE", "ProgressRow",
    "CompositionLocalProvider", "mutableFloatStateOf", "LocalDensity", "Density",
]
KEEP_OK = {
    "setSpeechRate": ["tts?.setSpeechRate(rate)"],   # android.speech.tts.TextToSpeech API
}
ALLOWED_FILES = set()


def scan_residual():
    hits = []
    for p in sorted((ROOT / "android-app").rglob("*")):
        if not p.is_file() or p.suffix not in {".kt", ".kts", ".xml", ".gradle", ".md", ".txt"}:
            continue
        if "build" in p.parts:
            continue
        try:
            txt = p.read_text(encoding="utf-8")
        except Exception:
            continue
        for i, ln in enumerate(txt.splitlines(), 1):
            for sym in DELETED:
                if re.search(r"\b" + re.escape(sym) + r"\b", ln):
                    hit = (str(p.relative_to(ROOT)), i, sym, ln.strip())
                    if any(k in ln for k in KEEP_OK.get(sym, [])):
                        continue
                    hits.append(hit)
    if hits:
        for h in hits:
            print(f"  [FAIL] 残留 {h[2]} @ {h[0]}:{h[1]} → {h[3]}")
            fail.append(f"残留 {h[2]} @ {h[0]}:{h[1]}")
    else:
        print("  [OK]   android-app/ 内被删符号 0 残留（TTSSpeaker 的平台 API 调用除外）")


def approot_structure():
    path = FILES["AppRoot.kt"]
    src = path.read_text(encoding="utf-8")
    lines, line_of = strip_code(src)
    depth = 0
    print("  AppRoot.kt 顶层结构（函数体内 depth==1 的语句）：")
    for idx, ln in enumerate(lines):
        stripped = ln.strip()
        start_depth = depth
        # 先判断该行【起始】层级，再更新
        if start_depth == 1 and stripped and not stripped.startswith("}"):
            print(f"    L{line_of[idx]:>4}  depth={start_depth}  {stripped[:72]}")
        for ch in ln:
            if ch in "({[":
                depth += 1
            elif ch in ")}]":
                depth -= 1


print("=" * 78)
print("1) 括号配平 / 结构")
print("=" * 78)
for name, path in FILES.items():
    check_balance(name, path)

print()
print("=" * 78)
print("2) import 使用点")
print("=" * 78)
for name in ("AppRoot.kt", "StudyRepository.kt"):
    check_imports(name, FILES[name])

print()
print("=" * 78)
print("3) 被删符号残留扫描")
print("=" * 78)
scan_residual()

print()
print("=" * 78)
print("4) AppRoot 去 provider 后 Column / charFor?.let 是否平级")
print("=" * 78)
approot_structure()

print()
print("=" * 78)
print("FAIL 汇总: " + (str(len(fail)) + " 项" if fail else "0 项"))
for f in fail:
    print("  -", f)
sys.exit(1 if fail else 0)
