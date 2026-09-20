# -*- coding: utf-8 -*-
"""Pre-commit sweep: HEAD -> worktree (v6..v13 + rename).
Read-only. Outputs findings to _precmt_out.txt next to this script.
"""
import os, re, subprocess, sys

ROOT = r"D:\QYJ\MyProject\Mando"
SRC = os.path.join(ROOT, "android-app", "app", "src", "main", "java", "com", "knowmo", "app")
OUT = []
def P(*a):
    OUT.append(" ".join(str(x) for x in a))

def git(a):
    r = subprocess.run(["git"] + a, cwd=ROOT, capture_output=True)
    return r.stdout.decode("utf-8", "replace")

def rd(p):
    with open(p, "r", encoding="utf-8-sig", errors="replace") as f:
        return f.read()

# ---------- A. modified tracked files ----------
mods = [l[3:] for l in git(["status", "--porcelain", "-uall"]).splitlines() if l.startswith(" M")]
P("== A. modified tracked files ==")
for m in mods:
    P("  M", m)

# ---------- B. counting sync points (ground truth = 509, scenes=13) ----------
P("")
P("== B. counting sync points ==")
wb = rd(os.path.join(SRC, "data", "WordBank.kt"))
P("WordBank KDoc first line:", wb.splitlines()[0].strip())
sr = rd(os.path.join(SRC, "data", "StudyRepository.kt"))
for i, l in enumerate(sr.splitlines(), 1):
    if re.search(r"n\s*[≤<]=?\s*\d+", l):
        P("StudyRepository L%d: %s" % (i, l.strip()))
rme = rd(os.path.join(ROOT, "android-app", "README.md"))
for i, l in enumerate(rme.splitlines(), 1):
    if re.search(r"\b(417|480|509|401|368|431)\b", l):
        P("README L%d: %s" % (i, l.strip()))
cg = rd(os.path.join(ROOT, ".trellis", "spec", "frontend", "component-guidelines.md"))
for i, l in enumerate(cg.splitlines(), 1):
    if re.search(r"\b(417|480|509|401|368|431|n\s*≤\s*\d+)", l):
        P("component-guidelines L%d: %s" % (i, l.strip()))

# ---------- C. stale references ----------
P("")
P("== C. stale references in live code+docs ==")
targets = {
    "old package": r"com\.qyj\.shibang",
    "old theme": r"Shibang",
    "old proj name in android/readme": r"Mando",
    "Sheets.kt ref": r"Sheets\.kt",
    "12 个场景": r"12\s*个场景",
    "stale 368": r"\b368\b",
    "stale 417": r"\b417\b",
    "stale 480": r"\b480\b",
    "stale 431": r"\b431\b",
    "stale 401": r"\b401\b",
}
scan_dirs = [SRC, os.path.join(ROOT, "android-app")]
def iter_kt_and_md():
    for d in scan_dirs:
        for r, _, fs in os.walk(d):
            for f in fs:
                if f.endswith((".kt", ".md", ".xml", ".kts", ".properties")):
                    yield os.path.join(r, f)
    yield os.path.join(ROOT, "readme.md")
    yield os.path.join(ROOT, "AGENTS.md")

hits = {k: [] for k in targets}
for p in iter_kt_and_md():
    try:
        txt = rd(p)
    except Exception:
        continue
    rel = os.path.relpath(p, ROOT)
    for k, pat in targets.items():
        for i, l in enumerate(txt.splitlines(), 1):
            if re.search(pat, l):
                hits[k].append("%s:%d: %s" % (rel, i, l.strip()[:120]))
for k, v in hits.items():
    P("[%s] %d hits" % (k, len(v)))
    for h in v[:12]:
        P("   ", h)

# ---------- D. brace balance + import usage per kt ----------
P("")
P("== D. brace balance / import usage (new tree) ==")
def strip_cs(t):
    out, i, n = [], 0, len(t)
    while i < n:
        c = t[i]
        if c == "/" and i + 1 < n and t[i+1] == "/":
            j = t.find("\n", i)
            i = n if j < 0 else j
        elif c == "/" and i + 1 < n and t[i+1] == "*":
            j = t.find("*/", i + 2)
            i = n if j < 0 else j + 2
        elif c == '"':
            i += 1
            while i < n:
                if t[i] == "\\":
                    i += 2
                elif t[i] == '"':
                    i += 1
                    break
                else:
                    i += 1
        elif c == "'":
            i += 1
            while i < n:
                if t[i] == "\\":
                    i += 2
                elif t[i] == "'":
                    i += 1
                    break
                else:
                    i += 1
        else:
            out.append(c)
            i += 1
    return "".join(out)

kt_files = []
for r, _, fs in os.walk(SRC):
    for f in fs:
        if f.endswith(".kt"):
            kt_files.append(os.path.join(r, f))
P("kt files:", len(kt_files))
for p in sorted(kt_files):
    rel = os.path.relpath(p, ROOT)
    txt = rd(p)
    code = strip_cs(txt)
    bal = code.count("{") - code.count("}")
    par = code.count("(") - code.count(")")
    issues = []
    if bal != 0:
        issues.append("brace=%+d" % bal)
    if par != 0:
        issues.append("paren=%+d" % par)
    # import usage
    imports = re.findall(r"^import\s+([\w.]+)", txt, re.M)
    body = re.sub(r"^import\s+[\w.]+\s*$", "", txt, flags=re.M)
    for imp in imports:
        name = imp.rsplit(".", 1)[-1]
        if name in ("Value", "SetValue"):
            continue  # by-delegation operators
        if not re.search(r"\b" + re.escape(name) + r"\b", body):
            issues.append("unused import: " + name)
    P("  %-64s %s" % (rel, "OK" if not issues else "; ".join(issues)))

# ---------- E. cross-check: old tree files still referenced? gradle config ----------
P("")
P("== E. gradle/appid ==")
for f in ["android-app/app/build.gradle.kts", "android-app/settings.gradle.kts", "android-app/build.gradle.kts"]:
    p = os.path.join(ROOT, f)
    if os.path.exists(p):
        for i, l in enumerate(rd(p).splitlines(), 1):
            if re.search(r"namespace|applicationId|rootProject\.name", l):
                P("%s:%d: %s" % (f, i, l.strip()))

with open(os.path.join(os.path.dirname(__file__), "_precmt_out.txt"), "w", encoding="utf-8") as f:
    f.write("\n".join(OUT))
print("done, lines:", len(OUT))
