# -*- coding: utf-8 -*-
"""面馆补充词候选 · v2（按「口说 / 文字」渠道重新分桶）

v1 把 43 条平铺列出；QYJ 指出「忌口和口味、调味品名词比较重复，学会调味品就差不多了」，
并提出关键的筛选问题：**哪些是口头说的、哪些是她要认的字**。

v2 据此改判：
  · 口径 = 这个词**是否以文字形式出现在她眼前**（要她认/读）。
  · **文字桶（T）** → 建议收录；**口语桶（S）** → 不建议收录（她只说，不需要认字）。
  · 唯一例外：**外卖备注**——「不要香菜」这类在订单小票上是打印文字。留作待裁定项。

产出（三份，单一数据源）：
  scene_noodle_extra.md            —— 审查稿（按渠道分桶）
  scene_noodle_extra.fragment.kt   —— 待审 Kotlin 片段（占位编号）
  _scene_noodle_extra_check.md     —— 机器校验
用法：python build_scene_noodle_extra.py
"""
import re
import os
import csv
from collections import OrderedDict
from pypinyin import pinyin, Style

HERE = os.path.dirname(os.path.abspath(__file__))
WB = r"D:\QYJ\MyProject\Mando\android-app\app\src\main\java\com\knowmo\app\data\WordBank.kt"
SD = r"D:\QYJ\MyProject\Mando\android-app\app\src\main\java\com\knowmo\app\data\StudyData.kt"
CHARLIST = os.path.join(HERE, "charlist_3500.csv")
OUT_DOC = os.path.join(HERE, "scene_noodle_extra.md")
OUT_KT = os.path.join(HERE, "scene_noodle_extra.fragment.kt")
OUT_CHK = os.path.join(HERE, "_scene_noodle_extra_check.md")

TERM = re.compile(
    r'term\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*\)'
)
TONE = re.compile(r"[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]")

# ===================================================================
# 候选数据：(text, pinyin, tip, pri, why)
#   pri: 1 = ★推荐, 2 = 可选；最小名单由 MIN_SET 单独维护
#   why: 判定依据（为什么这个词会以文字出现在她眼前 / 为什么只是口说）
# ===================================================================
GROUPS = OrderedDict()


def g(key, name, chan, blurb, items):
    GROUPS[key] = {"name": name, "chan": chan, "blurb": blurb, "items": items}


# ---------------- 文字桶 ----------------
g("T1", "后厨调料与干货（罐子、包装上印的字）", "T",
  "后厨最常低头看的字。**你说「学会调味品就差不多了」，这一组就是那句话的落点。**", [
      ("盐", "yán", "盐罐上的字，白白的细颗粒", 1, "调料罐"),
      ("糖", "táng", "糖罐上的字，甜的", 1, "调料罐"),
      ("醋", "cù", "醋瓶上的字，酸的那瓶", 1, "调料瓶"),
      ("味精", "wèi jīng", "提鲜用的白色小颗粒", 1, "调料罐"),
      ("鸡精", "jī jīng", "提鲜用的黄色小颗粒", 1, "调料罐"),
      ("花椒", "huā jiāo", "麻嘴的小圆粒，川菜都放", 1, "调料罐"),
      ("胡椒", "hú jiāo", "胡椒面，撒汤里的", 2, "调料罐"),
      ("酱油", "jiàng yóu", "黑黑的咸汁，也叫生抽", 1, "调料瓶"),
      ("香油", "xiāng yóu", "点面时滴几滴，香", 1, "调料瓶"),
      ("料酒", "liào jiǔ", "炒臊子去腥用的", 2, "调料瓶"),
      ("淀粉", "diàn fěn", "勾芡、码肉用的白粉", 1, "调料袋"),
      ("豆瓣酱", "dòu bàn jiàng", "郫县豆瓣，川菜的灵魂", 2, "调料罐"),
  ])

g("T2", "食材与浇头（价目表、加料栏、外卖单）", "T",
  "挂在墙上或写在单子上的配料名。", [
      ("香菜", "xiāng cài", "又叫芫荽，一小撮绿叶子", 1, "菜单/加料栏"),
      ("葱花", "cōng huā", "切碎的葱，撒在面上", 1, "菜单/加料栏"),
      ("泡菜", "pào cài", "免费的小碟泡萝卜、泡菜", 1, "小菜牌"),
      ("酸菜", "suān cài", "腌过的青菜，酸酸的", 1, "浇头名"),
      ("煎蛋", "jiān dàn", "油锅里煎的鸡蛋，跟卤蛋不一样", 2, "加料栏"),
      ("冰粉", "bīng fěn", "成都的甜凉粉，夏天吃", 2, "甜品牌"),
      ("凉糕", "liáng gāo", "凉凉的米糕，淋红糖水", 2, "甜品牌"),
  ])

g("T3", "规格与价目（价目表规格栏、结账单）", "T",
  "点单规格写在价目表上，是要她照着认的字。", [
      ("小碗", "xiǎo wǎn", "分量小的那种碗，饭量小的点这个", 1, "价目表规格"),
      ("半份", "bàn fèn", "只要一半的量", 2, "价目表规格"),
      ("加面", "jiā miàn", "要加钱的一项，价目表上单列", 1, "价目表加收项"),
      ("清汤", "qīng tāng", "不辣的汤，清的", 1, "价目表规格"),
      ("红汤", "hóng tāng", "辣的红油汤", 1, "价目表规格"),
      ("原汤", "yuán tāng", "煮面的本汤，不兑水", 2, "价目表规格"),
  ])

g("T4", "店内告示与证照（墙上挂的、贴的）", "T",
  "墙上的字。库里已有「营业时间 / 健康证 / 留样 / 生熟分开 / 请勿吸烟」5 条，这批是同一路的补充。", [
      ("招牌", "zhāo pái", "店里最有名的那道菜", 1, "招牌/菜单标题"),
      ("价目表", "jià mù biǎo", "墙上或柜台上写的价钱单", 1, "墙面告示"),
      ("营业中", "yíng yè zhōng", "灯牌亮着这几个字，就是还在卖", 1, "门口灯牌"),
      ("自助调料", "zì zhù tiáo liào", "调料台，自己舀，不要钱", 2, "调料台牌"),
      ("明厨亮灶", "míng chú liàng zào", "厨房敞开、贴着这种牌子", 2, "墙面告示"),
      ("卫生许可证", "wèi shēng xǔ kě zhèng", "墙上挂的证，上头有店名", 2, "墙面证照"),
  ])

g("T5", "后厨与桌前物件（包装、标签上的字）", "T",
  "拿在手上的东西，包装上都印着字。", [
      ("围裙", "wéi qún", "系在腰前的布，防油污", 1, "工装"),
      ("抹布", "mā bù", "擦桌子用的布", 2, "清洁用品"),
      ("洗洁精", "xǐ jié jīng", "洗碗用的洗涤剂", 2, "洗涤用品瓶"),
      ("保鲜膜", "bǎo xiān mó", "盖碗、封盒子用的", 2, "厨房耗材"),
      ("一次性筷子", "yī cì xìng kuài zi", "用一回就扔的筷子，打包时给", 2, "耗材包装"),
      ("牙签", "yá qiān", "剔牙用的小棍", 2, "桌上小盒"),
      ("纸巾", "zhǐ jīn", "擦嘴的纸，桌上摆着", 1, "桌上/包装"),
  ])

# ---------------- 口语桶 ----------------
g("S1", "忌口与口味（**只是嘴上说的**）", "S",
  "客人说、她也说，**全程不用认字**——只有一种情况会变成文字：外卖备注。所以不建议收。", [
      ("不要香菜", "bù yào xiāng cài", "碗里别放香菜", 2, "口头点单"),
      ("不要葱花", "bù yào cōng huā", "别放葱花", 2, "口头点单"),
      ("少放辣椒", "shǎo fàng là jiāo", "辣椒少来点", 2, "口头点单"),
      ("少放盐", "shǎo fàng yán", "盐少放，吃淡一点", 2, "口头点单"),
      ("不要味精", "bù yào wèi jīng", "味精别放", 2, "口头点单"),
      ("不要鸡精", "bù yào jī jīng", "鸡精别放", 2, "口头点单"),
      ("多放醋", "duō fàng cù", "醋多来点", 2, "口头点单"),
      ("不要蒜", "bù yào suàn", "蒜泥别放", 2, "口头点单"),
      ("不要折耳根", "bù yào zhé ěr gēn", "折耳根别放", 2, "口头点单"),
      ("少麻", "shǎo má", "花椒少放，麻味轻点", 2, "口头点单"),
      ("清淡点", "qīng dàn diǎn", "油和盐都少放些", 2, "口头点单"),
  ])

g("S2", "分量与添东西（**只是嘴上说的**）", "S",
  "同样是口头交流；只有「加面」因为在价目表上单列收了钱，才挪去 T3。", [
      ("少面", "shǎo miàn", "面少一点，汤多留些", 2, "口头点单"),
      ("加汤", "jiā tāng", "汤不够，再舀一勺", 2, "口头点单"),
  ])

MIN_SET = {"盐", "糖", "醋", "味精", "鸡精", "花椒", "酱油", "香油", "淀粉",
           "香菜", "葱花", "泡菜", "酸菜", "小碗", "清汤", "红汤",
           "招牌", "价目表", "营业中", "围裙"}


def norm(text):
    """表格行之间不留空行——Markdown 严格渲染会把空行断成两个表。"""
    prev = None
    while prev != text:
        prev = text
        text = re.sub(r"(\|[^\n]*)\n\n(?=\|)", r"\1\n", text)
    return text

# ===================================================================
src = open(WB, encoding="utf-8").read()
existing = TERM.findall(src)
by_text = {r[2]: (r[0], r[1]) for r in existing}
food_count = sum(1 for r in existing if r[1] == "food")
sd = open(SD, encoding="utf-8").read()
scene_ids = re.findall(r'Scene\(\s*"([^"]*)"', sd)
daily_count = sum(1 for r in existing if r[1] == "daily")

cand = [it for grp in GROUPS.values() for it in grp["items"]]
T_items = [it for grp in GROUPS.values() if grp["chan"] == "T" for it in grp["items"]]
S_items = [it for grp in GROUPS.values() if grp["chan"] == "S" for it in grp["items"]]
texts = [c[0] for c in cand]

pair_bad = [c for c in cand if len(c[0]) != len(c[1].split(" "))]
dup_in = sorted({t for t in texts if texts.count(t) > 1})
already = {c[0]: by_text[c[0]] for c in cand if c[0] in by_text}

common = set()
if os.path.exists(CHARLIST):
    with open(CHARLIST, encoding="utf-8-sig", newline="") as f:
        for row in csv.reader(f):
            ch = row[1].strip() if len(row) > 1 else ""
            if len(ch) == 1 and "\u4e00" <= ch <= "\u9fff":
                common.add(ch)
chars = {c for t in texts for c in t if "\u4e00" <= c <= "\u9fff"}
outside = sorted(chars - common) if common else []

diffs, toneless = [], []
for t, py, _tip, _p, _w in cand:
    glp = py.split(" ")
    if len(glp) != len(t):
        continue
    p = [x[0] for x in pinyin(t, style=Style.TONE)]
    if len(p) != len(t):
        continue
    for i in range(len(t)):
        if glp[i] != p[i]:
            diffs.append((t, t[i], glp[i], p[i]))
        if glp[i] and not TONE.search(glp[i]):
            toneless.append((t, t[i], glp[i]))

# ---------- 审查稿 ----------
D, w = [], lambda s: D.append(s)
w("# 面馆补充词 · 候选集 v2（按「口说 / 文字」重新分桶，**仍未接入**）\n")
w("> 2026-09-20。v1 把 43 条平铺列出；你指出**忌口/口味与调味品名词重复，学会调味品就差不多了**，"
  "并问「哪些通常是口说的、哪些通常接触到文字」——v2 就按这个口径重做。")
w("> 源码仍未改动。定稿后按最终名单统一编号（`food-66` 起）接入。\n")
w("---\n")
w("## 结果（2026-09-20 定稿，已接入）\n")
w("QYJ 裁定三条：")
w("1. **按「推荐」方案收** —— 文字桶 38 条全收。")
w("2. **店里有外卖，但不单收备注整句** —— 外卖备注都是围绕食物/调料的说法，**名词已在库中，重复无益**"
  "（收了「香菜」就不必再收「不要香菜」）。")
w("3. **不拆独立分区** —— 「不管吃面、还是在馆子上班，学习这些词都是没问题的」，仍并入 `food`。")
w("")
w("**执行结果**：38 条作为 `food-66` … `food-103` 并入 `food` 列表末尾（`food` 55 → **93 条**），"
  "全库 379 → **417 条**，分区数不变（12 个场景 + 常用词）。")
w("> 下文 §0–§4 是该决策形成**之前**的审查过程，保留备查（§4 的「待裁定」项已由上面三条答复覆盖）。\n")
w("---\n")
w("## 0. 先答你的问题：哪些是口说，哪些是文字\n")
w("筛选口径只有一条 —— **这个词会不会以文字的形式出现在她眼前、需要她认出来。**\n")
w("| 渠道 | 判断 | 典型词 | 收录价值 |\n|---|---|---|---|")
w("| **口说（S）** | 只在人嘴里来回传：客人点单说、她应一声。**不需要认字** | 不要香菜、少放辣椒、多放醋、清淡点、少面、加汤 | **低** —— 认字帮不上她，这些字她不看也能干活 |\n")
w("| **文字（T）** | 印在东西上、挂在墙上、打在单子上，**要她低头看、认出来** | 面名、调料罐、价目表规格、告示证照、包装标签 | **高** —— 这正是「识字」要解决的问题 |\n")
w("**结论：你的判断成立 —— 砍掉忌口整句，收调味品名词。** v1 的 A 组 11 条整句已整组降为「不建议收录」；"
  "v1 的 B 组（分量说法）也按同一口径重分：「小碗 / 半份 / 加面」在价目表上是字，留下；「少面 / 加汤」是口说，降级。\n")
w("**只有一个例外要你判**：**外卖备注**。如果店里接外卖，客人在 App 里写的「不要香菜」「少放辣椒」"
  "会**打印在小票上**，那时它就是文字了。见待裁定第 1 条。\n")
w("> 顺带一个印证：库里现有的面馆词（招牌面名 9 条、分量 3 条、后厨食安 4 条、店内流程 4 条）"
  "**本来就全落在文字桶**——上一轮我无意中走对了，这次把口径讲清楚。\n")
w("---\n")
w("## 1. 文字桶候选（**建议收录**，共 %d 条）\n" % len(T_items))
for key, grp in GROUPS.items():
    if grp["chan"] != "T":
        continue
    n_min = sum(1 for i in grp["items"] if i[0] in MIN_SET)
    w("### %s %s（%d 条，最小名单 %d 条）\n" % (key, grp["name"], len(grp["items"]), n_min))
    w("%s\n" % grp["blurb"])
    w("| # | 词条 | 拼音 | 说明（App 内朗读用） | 为什么会看到这个字 | 建议 |\n|---|---|---|---|---|---|")
    for i, (t, py, tip, pr, why) in enumerate(grp["items"], 1):
        flag = "已有" if t in already else ("**★最小**" if t in MIN_SET else ("★推荐" if pr == 1 else "可选"))
        w("| %d | %s | %s | %s | %s | %s |\n" % (i, t, py, tip, why, flag))
    w("")
w("## 2. 口语桶（**不建议收录**，共 %d 条）\n" % len(S_items))
w("> 列出来是为了让你看清「砍掉了什么」——不是候选，是**已判定不收**的清单。"
  "唯一变数见待裁定第 1 条（外卖备注）。\n")
for key, grp in GROUPS.items():
    if grp["chan"] != "S":
        continue
    w("### %s %s（%d 条）\n" % (key, grp["name"], len(grp["items"])))
    w("%s\n" % grp["blurb"])
    w("| # | 词条 | 拼音 | 说明 | 不收的理由 |\n|---|---|---|---|---|")
    for i, (t, py, tip, pr, why) in enumerate(grp["items"], 1):
        w("| %d | %s | %s | %s | %s |\n" % (i, t, py, tip, why))
    w("")
w("---\n")
w("## 3. 三个可选规模（你挑一个）\n")
w("| 方案 | 条数 | 内容 | 补完后 `food` |\n|---|---|---|---|")
w("| **最小** | %d | 调料罐 + 最核心的规格/告示/物件 | %d 条 |\n"
  % (len(MIN_SET), food_count + len(MIN_SET)))
w("| **推荐** | %d | 文字桶全部（T1–T5） | %d 条 |\n" % (len(T_items), food_count + len(T_items)))
w("| **全套** | %d | 文字桶 + 外卖备注那几条口语 | %d 条 |\n"
  % (len(T_items) + 5, food_count + len(T_items) + 5))
w("\n最小名单（%d 条）：%s\n" % (len(MIN_SET), "、".join(sorted(MIN_SET))))
w("---\n")
w("## 4. 裁定记录（原「待你裁定」）\n")
w("| # | 事项 | 裁定 |\n|---|---|---|")
w("| 1 | 店里接不接外卖 / 备注要不要单收 | **接外卖，但不单收整句**——备注围绕食物与调料，名词已收，重复无益 |\n")
w("| 2 | 收哪一档规模 | **推荐（文字桶 38 条）** |\n")
w("| 3 | 要不要拆回 `noodle` 独立分区 | **不拆**——「不管吃面还是在馆子上班，学这些词都没问题」 |\n")
w("| 4 | `food-26`「大碗」说明改成份量义 | **仍未定**（trellis-check 升为 P1：与新增 `food-85 小碗` 的份量义打架） |\n")
w("| 5 | 单字词条（盐/糖/醋）可否 | **随「推荐」方案一并认可**（已接入） |\n")
w("\n以下是裁定前的**原始问题原文**（编号与上表一一对应），保留备查。\n")
w("**1. 店里接不接外卖？** 接的话，**外卖备注是打印出来的文字**——「不要香菜」「少放辣椒」这类"
  "会以文字形式出现，值得收 4–5 条最常见的（顾客写什么、小票就印什么）。不接就整组不收。\n")
w("**2. 收哪些规模？** 见 §3（最小 %d / 推荐 %d / 全套 %d）。\n" % (len(MIN_SET), len(T_items), len(T_items) + 5))
w("**3. 要不要借这次把面馆重新拆成独立分区？** 上轮你让我并进「吃饭」，现在 `food` 已 65 条；"
  "若按「推荐」方案补完会到 **%d 条**，是其他分区（30–32）的 %.1f 倍。"
  "拆出来内容不用重写，id 前缀换回 `noodle-N` 即可。\n"
  % (food_count + len(T_items), (food_count + len(T_items)) / 31.0))
w("**4. `food-26`「大碗」的说明**现在是「装面装汤的家伙」——讲容器不像讲分量。"
  "既然要新收「小碗」，建议一并把它改成「分量大的那种碗」好配对，需你点头（改既有词条文案）。\n")
w("**5. 单字词条可以吗？** `盐` / `糖` / `醋` 我按罐子上印的字给的是单字。"
  "你的 `daily` 常用词里已有 10 个单字（人/大/小/上/下/水/火/门/钱/好），技术上没问题，"
  "但这是**词组应用里第一次出现单字食材**，口径要你确认。\n")
w("---\n")
w("## 5. 机器校验（明细见 `_scene_noodle_extra_check.md`）\n")
w("| 检查项 | 结果 |\n|---|---|")
w("| 候选条数 | %d（文字桶 %d + 口语桶 %d） |\n" % (len(cand), len(T_items), len(S_items)))
w("| 字数 ↔ 拼音音节配对（启动期 `require`） | %s |\n"
  % ("**%d 条不符**" % len(pair_bad) if pair_bad else "全部通过"))
w("| 候选组内文本重复 | %s |\n" % (dup_in if dup_in else "无"))
w("| 与现库（%d 条）精确重词 | %s |\n"
  % (len(existing), "、".join("`%s`（%s）" % (k, v[0]) for k, v in already.items()) if already else "无"))
w("| 无声调拼音（约定 = 轻声） | %s |\n"
  % ("、".join("`%s` 的「%s」= `%s`" % x for x in toneless) if toneless else "无 —— 全部带调号"))
w("| 3500 常用字覆盖 | 去重汉字 %d 个，超纲 %s |\n"
  % (len(chars), ("`" + "` `".join(outside) + "`") if outside else "**无**"))
w("| pypinyin 逐字差异 | %d 处，全部判定为有意保留（见下） |\n" % len(diffs))
if diffs:
    w("\n| 词条 | 字 | 本稿 | pypinyin | 判定 |\n|---|---|---|---|---|")
    for t, ch, mine, pp in diffs:
        if ch == "不":
            j = "**保留 bù** —— 注音教学写本调，与库内「不要乱动 bù yào luàn dòng」同惯例"
        elif ch == "一":
            j = "**保留 yī** —— 与库内「一次一片 yī cì yī piàn」一致"
        elif ch == "抹":
            j = "**保留 mā** —— 「抹布」读 mābù，pypinyin 默认给 mǒ"
        else:
            j = "待判定"
        w("| %s | %s | %s | %s | %s |" % (t, ch, mine, pp, j))
    w("")
open(OUT_DOC, "w", encoding="utf-8", newline="\n").write(norm("\n".join(D)))

# ---------- Kotlin 片段 ----------
K = []
K.append("// ===================================================================")
K.append("// 面馆补充词 · 待审候选片段 v2（按「口说 / 文字」分桶，2026-09-20）")
K.append("//")
K.append("// ✅ **已于 v11 接入**：这 38 条已作为 food-66 … food-103 并入 `food` 列表末尾（KDoc 计数 417）。")
K.append("//    本文件保留为**接入来源记录**（接入时逐字节比对 identical=True），不要再粘贴一次。")
K.append("//    现行库 = %d 条 / %d 个场景分区（面馆 33 条已并入 `food`；v9 `daily` 常用词 %d 条）。"
         % (len(existing), len(scene_ids) - 1, daily_count))
K.append("//    收录判据（QYJ v10/v11）：该词**是否会以文字形式出现**；口说类整句不单收。")
K.append("//    判据备注：「抹布」是本批依据最弱的一条（布本身不带字，仅超市货架/包装可见），")
K.append("//    下次清理时别当口说词删掉；「加面」入桶是因为它在价目表上单列加收，而「加汤/少面」纯口说故不收。")
K.append("//    审查稿与裁定记录见 scene_noodle_extra.md。")
K.append("// ===================================================================")
K.append("")
n = 66
for key, grp in GROUPS.items():
    if grp["chan"] != "T":
        continue
    K.append("// %s %s" % (key, grp["name"]))
    for t, py, tip, pr, _why in grp["items"]:
        tag = "  // ← 库里已有，勿收" if t in already else ""
        K.append('    term("food-%d", "food", "%s", "%s", "%s"),%s' % (n, t, py, tip, tag))
        n += 1
    K.append("")
open(OUT_KT, "w", encoding="utf-8", newline="\n").write("\n".join(K) + "\n")

# ---------- 机器校验 ----------
C, w2 = [], lambda s: C.append(s)
w2("# 面馆补充词候选 v2 · 机器校验\n")
w2("> 自动生成，`build_scene_noodle_extra.py`。候选 %d 条（文字桶 %d / 口语桶 %d） vs 现库 %d 条。\n"
   % (len(cand), len(T_items), len(S_items), len(existing)))
w2("## 1. 字数 ↔ 拼音音节配对\n")
if pair_bad:
    w2("**失败 %d 条**：%s\n" % (len(pair_bad), [(x[0], len(x[0]), len(x[1].split(" "))) for x in pair_bad]))
else:
    w2("**%d 条全部通过** —— 逐字配对，接入后启动不会抛 `词条 xxx 拼音与字数不符`。\n" % len(cand))
w2("## 2. 与现库重词（精确文本比对）\n")
if already:
    w2("| 候选词 | 现库 id | 处置 |\n|---|---|---|\n")
    for k, v in already.items():
        w2("| %s | `%s` | 不重复收录 |\n" % (k, v[0]))
else:
    w2("**无** —— %d 条候选与现行 %d 条都不重复。\n" % (len(cand), len(existing)))
w2("\n## 3. 候选组内重复\n%s\n" % (dup_in if dup_in else "无\n"))
w2("## 4. 无声调拼音（约定「无调号 = 轻声」）\n")
if toneless:
    w2("| 词条 | 字 | 标音 |\n|---|---|---|\n")
    for x in toneless:
        w2("| %s | %s | %s |\n" % x)
    w2("\n「筷子」的子读轻声 `zi`，属正确标注。\n")
else:
    w2("**无** —— 全部带调号。\n")
w2("## 5. 3500 常用字覆盖\n去重汉字 %d 个；超纲 %s\n"
   % (len(chars), ("`" + "` `".join(outside) + "`") if outside else "无"))
w2("\n## 6. pypinyin 差异 %d 处\n" % len(diffs))
if diffs:
    w2("| 词条 | 字 | 本稿 | pypinyin |\n|---|---|---|---|\n")
    for t, ch, mine, pp in diffs:
        w2("| %s | %s | %s | %s |\n" % (t, ch, mine, pp))
open(OUT_CHK, "w", encoding="utf-8", newline="\n").write(norm("\n".join(C)))

print("cand=%d (T=%d S=%d) min=%d pair_bad=%d dup_in=%s already=%s"
      % (len(cand), len(T_items), len(S_items), len(MIN_SET), len(pair_bad), dup_in, list(already)))
print("food_before=%d food_T=%d uniq_chars=%d outside3500=%s toneless=%d diffs=%d"
      % (food_count, food_count + len(T_items), len(chars), outside, len(toneless), len(diffs)))
for p in (OUT_DOC, OUT_KT, OUT_CHK):
    print("->", p)
