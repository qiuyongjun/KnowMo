# -*- coding: utf-8 -*-
"""v8 静态自检：剔除注释与字符串字面量后核验 .kt 的 {} / () / [] 配平。"""
import glob, io, sys

def strip_code(src: str) -> str:
    out = []
    i, n = 0, len(src)
    state = None  # None | 'line' | 'block' | 'str'
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ''
        if state is None:
            if c == '/' and nxt == '/':
                state = 'line'; i += 2; continue
            if c == '/' and nxt == '*':
                state = 'block'; i += 2; continue
            if c == '"':
                state = 'str'; i += 1; continue
            if c == "'":
                state = 'char'; i += 1; continue
            out.append(c); i += 1
        elif state == 'line':
            if c == '\n':
                state = None; out.append(c)
            i += 1
        elif state == 'block':
            if c == '*' and nxt == '/':
                state = None; i += 2
            else:
                i += 1
        elif state == 'str':
            if c == '\\':
                i += 2
            elif c == '"':
                state = None; i += 1
            else:
                i += 1
        elif state == 'char':
            if c == '\\':
                i += 2
            elif c == "'":
                state = None; i += 1
            else:
                i += 1
    return ''.join(out)

files = sorted(glob.glob(r'D:\QYJ\MyProject\Mando\android-app\app\src\**\*.kt', recursive=True))
report = []
ok = True
for f in files:
    src = io.open(f, encoding='utf-8').read()
    code = strip_code(src)
    pairs = {'{': '}', '(': ')', '[': ']'}
    for a, b in pairs.items():
        ca, cb = code.count(a), code.count(b)
        if ca != cb:
            ok = False
            report.append('MISMATCH %s %s:%d vs %s:%d' % (f, a, ca, b, cb))
report.append('files=%d, all balanced=%s' % (len(files), ok))
io.open(r'D:\QYJ\MyProject\Mando\.trellis\tasks\09-17-elderly-literacy-app\research\brace_check_v8.txt', 'w', encoding='utf-8').write('\n'.join(report))
print('\n'.join(report))
