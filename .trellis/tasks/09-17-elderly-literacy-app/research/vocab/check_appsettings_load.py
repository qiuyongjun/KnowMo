# -*- coding: utf-8 -*-
"""AppSettings「存储后新增的分区默认隐藏」语义模拟 + 代码存在性断言（v12，2026-09-20）。

本机无 JDK / Android SDK，编译不了，跑不起来 —— 这是这条口径**唯一**的自动化防线。

两件事一起做，缺一不可：

  A. **静态断言**：`AppSettings.kt` 里确实有设计要求的那个判定。
     防的是最阴的一种漂移 —— design.md/prd.md 写了口径、代码没写（或后来被人删了），
     而两边单看都没毛病。

  B. **语义模拟**：把 `load()` 抄成 Python，用**从 `StudyData.kt` 解析出的真实 SCENES**
     跑 8 种存储场景，并且对每个场景同时算「旧口径」与「新口径」的可见分区 —— 把差异打出来。
     只算新口径的话，万一这改动根本没起作用（比如判定条件写错成恒 false），
     报告一样全绿。有旧口径对照才叫证据。

用法：python check_appsettings_load.py
产出：_appsettings_load_check.md（可复跑、可 diff）
"""
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = r"D:\QYJ\MyProject\Mando\android-app\app\src\main\java\com\knowmo\app\data"
APP = os.path.join(SRC, "AppSettings.kt")
SD = os.path.join(SRC, "StudyData.kt")
OUT = os.path.join(HERE, "_appsettings_load_check.md")

REC, FAV, DAILY = "rec", "fav", "daily"

# ---------------------------------------------------------------- A. 静态断言
src = open(APP, encoding="utf-8").read()
STATIC = [
    ("load() 记住了 order 数组的存在性",
     r"val\s+storedOrder\s*=\s*obj\.optJSONArray\(\"order\"\)"),
    ("新增分区归入 hidden 的判定（且带 order 存在性守卫）",
     r"if\s*\(\s*storedOrder\s*!=\s*null\s*\)\s*hidden\.addAll\(\s*manageableIds\(\)\.filter\s*\{\s*it\s*!in\s*known\s*\}\s*\)"),
    ("未知 id 过滤仍在（known = stored ∩ manageableIds）",
     r"val\s+known\s*=\s*stored\.filter\s*\{\s*it\s+in\s+manageableIds\(\)\s*\}"),
    ("order 仍是「已知项 + SCENES 顺序补尾」",
     r"order\s*=\s*known\s*\+\s*manageableIds\(\)\.filter\s*\{\s*it\s+!in\s+known\s*\}"),
    ("persist() 仍写完整 order",
     r"\.put\(\"order\",\s*JSONArray\(order\)\)"),
    # 顺序断言：判定必须排在 hidden.clear() **之后**。若哪天有人把它上移到 clear 之前，
    # 结果会被 clear 抹掉、修复静默失效 —— 而上面那 5 条形状断言与 B 段（独立模型）全都照样绿。
    ("判定位于 hidden.clear() 之后（上移即静默失效）",
     r"hidden\.clear\(\)[\s\S]{0,1200}?if\s*\(\s*storedOrder\s*!=\s*null\s*\)\s*hidden\.addAll\("),
    # 2026-09-20 口径细化（daily 不可显隐）：manageableIds 必须排除 CHANNEL_COMMON，
    # 否则 daily 会重新出现在设置页/频道栏—— B 段「旧存储 daily 被丢弃」的断言随之失守。
    ("manageableIds 排除 CHANNEL_COMMON（daily 不进显隐管理）",
     r"private\s+fun\s+manageableIds\(\)[\s\S]{0,500}?CHANNEL_COMMON"),
    ("setSceneVisible 防御守卫含 CHANNEL_COMMON",
     r"fun\s+setSceneVisible[\s\S]{0,300}?CHANNEL_COMMON"),
]
static_fails = [name for name, pat in STATIC if not re.search(pat, src)]

# ------------------------------------------------- SCENES / manageableIds 对照
sd = open(SD, encoding="utf-8").read()
scene_ids = re.findall(r'Scene\(\s*"([^"]*)"', sd)
if not scene_ids:
    raise SystemExit("StudyData.kt 里没解析到 SCENES —— 先修本脚本")
# daily（常用词）不进显隐管理（2026-09-20 口径细化）：manageableIds 排除 rec/fav/daily 三者
manageable = [i for i in scene_ids if i not in (REC, FAV, DAILY)]
if REC in manageable:
    raise SystemExit("manageableIds 不该含 rec")
if DAILY in manageable:
    raise SystemExit("manageableIds 不该含 daily（不可显隐的内容源特例）")
if FAV in scene_ids:
    raise SystemExit("fav 不该出现在 SCENES 里（会被当成可调度场景）")

# ----------------------------------------------------------- B. load() 语义移植
def load(settings, new_rule):
    """把 AppSettings.load() 抄成 Python。settings=None 表示 SharedPreferences 里没有 settings 键。

    与 Kotlin 的对应关系：
      prefs.getString("settings", null) ?: return   -> settings is None 时保留字段初值（全隐藏）
      obj.optJSONArray("order")                      -> None 表示键缺失**或类型不对**（Kotlin 同义）
      hidden 初值 = 全部 manageableIds               -> 字段声明处 addAll(manageableIds())
    """
    order = list(manageable)
    hidden = set(manageable)                       # 字段缺省：全部分区隐藏
    if settings is None:
        return order, hidden

    raw_order = settings.get("order")
    if not isinstance(raw_order, list):            # Kotlin optJSONArray 遇非数组返回 null
        raw_order = None
    stored = [x for x in (raw_order or []) if isinstance(x, str)]
    known = [i for i in stored if i in manageable]
    order = known + [i for i in manageable if i not in known]

    hidden = set()
    raw_hidden = settings.get("hidden")
    if isinstance(raw_hidden, list):
        for i in raw_hidden:
            if isinstance(i, str) and i in manageable:
                hidden.add(i)

    # ← 本轮的**唯一**行为改动
    if new_rule and raw_order is not None:
        hidden |= {i for i in manageable if i not in known}
    return order, hidden


ALL = list(manageable)
# 各时代的真实存储快照（用 manageable 里实际存在的 id 构造，避免硬编码错 id）。
# 历史（口径细化前）：daily 曾进 manageableIds，v9–v13 的存储 order/hidden 里都可能有 "daily"。
PRE_V9 = [i for i in ALL if i != "appliance"]                        # v6–v8 写入：尚无 appliance
V9_V13 = [i for i in ALL if i != "appliance"]                        # v9–v13 写入：尚无 appliance
# 口径细化前某台机器真实会写出的 order（含 daily——当时它在 manageable 里）
OLD_WITH_DAILY = ["daily"] + [i for i in ALL if i != "appliance"]

CASES = [
    ("S1 新装机：无 settings 键",
     None, [],
     "频道栏只有推荐 + 收藏"),

    ("S2 升级自 v6–v8（存储里既无 daily 也无 appliance）",
     {"order": PRE_V9, "hidden": list(PRE_V9), "quota": 10, "quotaNew": 5},
     [],
     "daily 与 appliance 都该隐藏 —— daily 这条是新口径顺带修的旧缺陷"),

    ("S3 升级自 v9–v13（用户开了 market；hidden 含已不管理的 daily）",
     {"order": OLD_WITH_DAILY, "hidden": [i for i in OLD_WITH_DAILY if i != "market"], "quota": 10, "quotaNew": 5},
     ["market"],
     "用户的 market 选择要保住；存储里的 daily 被 manageableIds 过滤自然丢弃（不可显隐口径），频道栏不出现常用词"),

    ("S4 已装机且用户显式开启了 appliance",
     {"order": ALL, "hidden": [i for i in ALL if i != "appliance"], "quota": 10, "quotaNew": 5},
     ["appliance"],
     "用户显式选择优先，新口径不得把它打回隐藏"),

    ("S5 用户把 14 个分区全开",
     {"order": ALL, "hidden": [], "quota": 10, "quotaNew": 5},
     list(ALL),
     "没人被误隐藏"),

    ("S6 order 键缺失但 hidden 存在（畸形 JSON，防御分支）",
     {"hidden": ["market"]},
     [i for i in ALL if i != "market"],
     "order 缺失 → 不做推断，保持原行为（新旧口径必须完全一致）"),

    ("S7 存储里混入未知 id（noodle 已撤销 / bogus 从没存在）",
     {"order": ["noodle", "market"], "hidden": ["bogus"], "quota": 10, "quotaNew": 5},
     ["market"],
     "未知 id 一律忽略，不崩、不影响判定"),

    ("S8 order 类型不对（字符串而非数组）",
     {"order": "market,transit", "hidden": [], "quota": 10, "quotaNew": 5},
     list(ALL),
     "Kotlin optJSONArray 对非数组返回 null → 同 S6 走防御分支；此时 hidden 保持存储值（空）"
     "⇒ 全部分区可见。**这是改动前就有的既有行为，本轮未改**；且 persist() 必写数组，实际不可达"),

    ("S9 老用户曾显式开启 daily（order 与 hidden 都含它，当时可见）",
     {"order": OLD_WITH_DAILY, "hidden": [i for i in OLD_WITH_DAILY if i not in ("daily", "market")], "quota": 10, "quotaNew": 5},
     ["market"],
     "2026-09-20 口径细化的关键升级路径：daily 不再被视作可管理 id——旧存储里它可见与否都无所谓，"
     "known/hidden 双重过滤自然丢弃，无需迁移。频道栏不再出现常用词，其词仍在推荐范围（CHANNEL_COMMON 特例）"),
]

rows, fails = [], []
for title, settings, expect, why in CASES:
    _, hid_old = load(settings, new_rule=False)
    order, hid_new = load(settings, new_rule=True)
    vis_old = [i for i in order if i not in hid_old]
    vis_new = [i for i in order if i not in hid_new]
    # 差异只有一个方向：新口径只会**多藏**，所以 vis_new ⊆ vis_old 恒成立。
    # 因此差异必须按这个方向算 —— 写成 new - old 的话每行都是空（见 EFFECT_CASES 的断言）。
    d = [i for i in vis_old if i not in vis_new]
    if vis_new != expect:
        fails.append("%s：期望可见 %s，实得 %s" % (title, expect, vis_new))
    rows.append(dict(title=title, why=why, old=vis_old, new=vis_new, diff=d,
                     expect=expect, ok=(vis_new == expect)))
    # vis_new ⊆ vis_old 是模型自洽的硬约束：新口径只往 hidden 加，不可能凭空多出可见项
    if not set(vis_new) <= set(vis_old):
        fails.append("%s：新口径出现了旧口径没有的可见项 —— 差异方向的前提被破坏" % title)
    # S6/S8 是「order 缺失」防御分支 → 新旧必须完全一致（证明没越界推断）
    if title.startswith(("S6", "S8")) and vis_old != vis_new:
        fails.append("%s：order 缺失时新旧口径不该有差异" % title)

# 全局不变量
for title, settings, expect, why in CASES:
    order, hidden = load(settings, new_rule=True)
    if set(order) != set(manageable):
        fails.append("%s：order 未覆盖全部分区（设置页会漏行）" % title)
    bad = hidden - set(manageable)
    if bad:
        fails.append("%s：hidden 含非可管理 id %s" % (title, bad))

# 「本改动确实生效」的硬断言：S2/S3/S7/S9 必须出现**非空**差异。
#
# 这里断言的是 diff 本身非空，不是「old != new」—— 这个区别是实测出来的：
# 首版断言写的是 old != new，而差异列被误写成 new - old 时，old != new 仍为真、
# 差异列却每行都是空，报告全绿、读者完全看不出改动到底有没有生效。
# 即：断言必须与**呈现出来的那个量**绑定，否则拦不住它自称要拦的回归。
EFFECT_CASES = ("S2", "S3", "S7", "S9")
for r in rows:
    if r["title"].startswith(EFFECT_CASES) and not r["diff"]:
        fails.append("%s：新旧差异为空 —— 改动未生效，或差异列计算方向反了" % r["title"])

# ---------------------------------------------------------------- 报告
L = []
w = L.append
w("# AppSettings `load()` 口径校验（v12 + 2026-09-20 daily 不可显隐细化）\n\n")
w("> 自动生成，脚本 `check_appsettings_load.py`（可复跑）。源：`AppSettings.kt` + `StudyData.kt`\n")
w("> 本机无 JDK / Android SDK，**未编译**；本文件与实机走查共同构成验证链。\n\n")
w("## A. 静态断言（代码里真有这条规则）\n\n")
w("| # | 断言 | 结果 |\n|---|---|---|\n")
for i, (name, _) in enumerate(STATIC, 1):
    w("| %d | %s | %s |\n" % (i, name, "❌ 未命中" if name in static_fails else "✅"))
w("\n可管理分区 %d 个（SCENES %d 项，去掉 `rec` 与 `daily`；`fav` 不在 SCENES）：`%s`\n\n"
  % (len(manageable), len(scene_ids), "`, `".join(manageable)))
w("`daily`（常用词）不在可管理集合 —— 不可开关、设置页无此行、频道栏永不出现，其词恒入推荐（CHANNEL_COMMON 特例）。\n\n")

w("## B. 语义模拟（新旧口径对照）\n\n")
w("「旧口径」= 改动前：`hidden` 只按存储数组重填，新分区因此**可见**。\n")
w("「新口径」= 本轮：不在存储 `order` 里的分区额外归入 `hidden`；且 `daily` 被移出可管理集合，"
  "旧存储里的 `daily` 在 known/hidden 双重过滤时自然丢弃。\n\n")
w("| 场景 | 期望可见 | 旧口径可见 | 新口径可见 | 差异 | 判定 |\n|---|---|---|---|---|---|\n")
for r in rows:
    d = r["diff"]
    dd = "无" if not d else "**新增隐藏：%s**" % "、".join(d)
    w("| %s | %s | %s | %s | %s | %s |\n"
      % (r["title"], "、".join(r["expect"]) or "（空）",
         "、".join(r["old"]) or "（空）", "、".join(r["new"]) or "（空）",
         dd, "✅" if r["ok"] else "❌"))
w("\n### 每个场景要证明什么\n\n")
for r in rows:
    w("- **%s** —— %s\n" % (r["title"], r["why"]))

w("\n## C. 结论\n\n")
if static_fails or fails:
    w("**存在阻断项**：\n\n")
    for f in static_fails:
        w("- 静态断言未命中：%s\n" % f)
    for f in fails:
        w("- %s\n" % f)
else:
    w("**A 段 %d 条静态断言全过；B 段 %d 个场景全部符合期望**。\n\n" % (len(STATIC), len(CASES)))
    w("- S2/S3/S7/S9 的「差异」列非空 ⇒ 本改动**确实起了作用**（旧口径会把新分区放进频道栏；S9 是 daily 丢弃路径）；\n")
    w("- S4/S5 新旧一致 ⇒ 用户显式选择不被覆盖；\n")
    w("- S6/S8 新旧一致 ⇒ `order` 缺失时的防御分支没被越界推断；\n")
    w("- S9：老用户曾显式开启过 `daily` → 升级后频道栏不再出现常用词频道（其词仍在推荐范围内），无需迁移代码。\n\n")
    w("### 关于 `if (storedOrder != null)` 这个守卫\n\n")
    w("它是**纯防御**，生产路径不可达 —— `persist()` 必定写 `order` 且为数组，\n")
    w("而全仓只有 `persist()` 一处写 `settings` 键（`StudyRepository` 写的是另一个 prefs 文件\n")
    w("`study_state`）。所以「`order` 为 null 但 `hidden` 有值」的数据只能来自 adb 手改 / 备份还原 /\n")
    w("外部写坏。保留它是有意的：宁可对畸形数据退化成旧行为，也不要拿用户已有的显隐选择去做推断。\n\n")
    w("### 本脚本的已知局限\n\n")
    w("B 段的 `expect` 是人工常量，对**模型**是硬断言；对 **Kotlin 真身**的桥接只有 A 段正则。\n")
    w("若有人同时改模型与 `expect`，脚本仍会绿 —— 这是「规格 vs 实现」双向检查的固有局限，\n")
    w("唯一的缓解是 A 段正则钉在 Kotlin 真身上。**实机验证不可省。**\n\n")
    w("剩余风险来自**未编译**：Kotlin 侧的语法/类型正确性只能由实机或 CI 构建确认。\n")

open(OUT, "w", encoding="utf-8", newline="\n").write("".join(L))
print("static_fails=%d case_fails=%d manageable=%d" % (len(static_fails), len(fails), len(manageable)))
print("-> %s" % OUT)
