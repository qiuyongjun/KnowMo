# -*- coding: utf-8 -*-
"""round5 五分区梳理候选（daily / food / appliance / transit / phone）。

单一数据源：本文件既是候选清单，也是校验脚本与 .kt 片段的生成器。
改这里 → 重跑 → 报告与片段同步更新（沿用 build_scene_noodle_extra.py 的范式）。

判据（.trellis/spec/frontend/wordbank-guidelines.md §3）：
  这个词，老人生活环境里是否会以文字形式出现
  （招牌/价签/菜单/告示/屏幕/包装/电器面板/铭牌/贴纸/说明书）。

用法：python build_round5_candidates.py
产出：round5_candidates.md（审查稿）/ round5_candidates.fragment.kt（占位片段）
"""
import os
import re
import csv
import collections
from pypinyin import pinyin, Style

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = r"D:\QYJ\MyProject\Mando\android-app\app\src\main\java\com\knowmo\app\data"
WB = os.path.join(SRC, "WordBank.kt")
CHARLIST = os.path.join(HERE, "charlist_3500.csv")
OUT_MD = os.path.join(HERE, "round5_candidates.md")
OUT_KT = os.path.join(HERE, "round5_candidates.fragment.kt")

TERM = re.compile(
    r'term\(\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*\)'
)

SCENE_CN = {
    "daily": "常用词", "transit": "公交地铁", "food": "吃饭",
    "phone": "手机微信", "appliance": "家电",
}

# scene -> [(text, pinyin, tip, 级别)]  级别 R=推荐 O=可选
CAND = collections.OrderedDict([
    ("daily", [
        ("推", "tuī", "门上的字，往外推才开", "R"),
        ("拉", "lā", "门上的字，往里拉才开", "R"),
        ("男", "nán", "男厕所门上的字", "R"),
        ("女", "nǚ", "女厕所门上的字", "R"),
        ("开", "kāi", "老式插座、电器面板上标的「开」那一档", "R"),
        ("关", "guān", "老式插座、电器面板上标的「关」那一档", "R"),
        ("左", "zuǒ", "门上、旋钮上标的方向，跟「右」相反", "O"),
        ("右", "yòu", "门上、旋钮上标的方向，跟「左」相反", "O"),
        ("免费", "miǎn fèi", "不要钱的，停车、量血压都见过", "R"),
        ("收费", "shōu fèi", "要交钱的，公厕、停车场门口挂着", "R"),
    ]),
    ("transit", [
        ("上行", "shàng xíng", "扶梯旁边写着，往上走的那边", "R"),
        ("下行", "xià xíng", "扶梯旁边写着，往下走的那边", "R"),
        ("扶梯", "fú tī", "会自己动的楼梯，商场地铁里都有", "R"),
        ("充值", "chōng zhí", "公交卡钱不够，来这里加钱", "R"),
        ("时刻表", "shí kè biǎo", "站牌上写着首班末班几点，照着它等车", "R"),
        ("爱心专座", "ài xīn zhuān zuò", "车厢里那排黄座位，专门让给老人", "R"),
        ("勿越黄线", "wù yuè huáng xiàn", "站台上黄线外的字，等车别踩过去", "R"),
        ("自动售票", "zì dòng shòu piào", "机器上写着，自己投钱拿票", "O"),
        ("单程票", "dān chéng piào", "只坐一趟的票，机器上能买", "O"),
        ("紧急出口", "jǐn jí chū kǒu", "出事时从这儿出去，绿牌子", "R"),
    ]),
    ("food", [
        ("凉菜", "liáng cài", "菜单上分栏，不热的那几样", "R"),
        ("热菜", "rè cài", "菜单上分栏，现炒现做的", "R"),
        ("主食", "zhǔ shí", "菜单上分栏，饭和面都归这里", "R"),
        ("小吃", "xiǎo chī", "店招牌上常见，粉面抄手这类", "R"),
        ("快餐", "kuài cān", "招牌上写着，坐下就吃不用等", "R"),
        ("家常菜", "jiā cháng cài", "招牌上写着，平常家里吃的那几样", "R"),
        ("川菜", "chuān cài", "招牌上写着，四川口味的馆子", "O"),
        ("红烧", "hóng shāo", "菜名前两个字，酱油烧的，比如红烧肉", "R"),
        ("清蒸", "qīng zhēng", "菜名前两个字，蒸出来的，不辣", "R"),
        ("凉拌", "liáng bàn", "菜名前两个字，拌好就上，不加热", "O"),
        ("扫码点餐", "sǎo mǎ diǎn cān", "桌上贴的，用手机扫一下自己点", "R"),
        ("外卖", "wài mài", "门口贴着，让骑手取餐的地方", "R"),
    ]),
    ("phone", [
        ("扫一扫", "sǎo yī sǎo", "微信里那个方框，对着码扫", "R"),
        ("转账", "zhuǎn zhàng", "把钱转给别人，认准了再按", "R"),
        ("验证码", "yàn zhèng mǎ", "短信里那串数字，谁要都别给", "R"),
        ("微信支付", "wēi xìn zhī fù", "结账时选这个，从微信里扣钱", "R"),
        ("零钱", "líng qián", "微信里的钱袋子，收的红包在这", "R"),
        ("余额", "yú é", "还剩多少钱，数字在这儿写着", "R"),
        ("发送", "fā sòng", "打完字按它，消息才发得出去", "R"),
        ("截图", "jié tú", "把屏幕照下来，存成一张图", "O"),
        ("设置", "shè zhì", "手机里调东西的地方，齿轮图标", "R"),
        ("拒接", "jù jiē", "不想接，按红色键挂掉", "O"),
        ("垃圾短信", "lā jī duǎn xìn", "广告和骗子发来的，直接删", "R"),
    ]),
    ("appliance", [
        ("待机", "dài jī", "机器歇着没干活，按一下就能用", "R"),
        ("开始", "kāi shǐ", "按这个键，机器就动起来", "R"),
        ("取消", "qǔ xiāo", "按错了就按它，重新来", "R"),
        ("清洁", "qīng jié", "洗衣机、油烟机上的档位，专门洗机器", "R"),
        ("速冻", "sù dòng", "冰箱上那个键，让东西冻得快", "R"),
        ("标准洗", "biāo zhǔn xǐ", "洗衣机最常用的那档，平常衣服都用它", "R"),
        ("强力", "qiáng lì", "洗得狠的那一档，脏衣服用", "O"),
        ("轻柔", "qīng róu", "洗得轻的那一档，毛衣用", "O"),
        ("睡眠", "shuì mián", "空调上那个键，风小了、不吵人", "R"),
        ("摆风", "bǎi fēng", "空调叶片来回摆，风不冲着人吹", "O"),
        ("温度", "wēn dù", "冷热看这个数，按加号调高", "R"),
        ("额定电压", "é dìng diàn yā", "铭牌上那行，220 伏就是它", "O"),
        ("生产日期", "shēng chǎn rì qī", "铭牌上写着，哪天造的", "R"),
        ("客服电话", "kè fú diàn huà", "坏了好打这个号，说明书上印着", "R"),
    ]),
])

# 建议删除：(id, text, 理由)
DROP = {
    "daily": [
        ("daily-1", "人", "环境里不独立出现——只在「人行道」「禁止行人」里当部件"),
        ("daily-2", "大", "只在「大白菜」「大号」里当部件；单字无独立标识载体"),
        ("daily-3", "小", "只在「小心地滑」「小卖部」里当部件"),
        ("daily-10", "好", "环境里几乎不独立出现；「好评」「好再来」都是词"),
        ("daily-9", "钱", "价签和收款码上是「￥」符号，不是「钱」字"),
    ],
}

# 起草中自我否掉的候选 —— 与上面删除项用的是同一条理由（只在词里当部件、无独立标识载体）。
# 列出来是为了让「同一把尺子」可被检查，而不是我一边删一边加。
REJECTED = [
    ("daily", "停", "只在「急停」「暂停」里出现，无独立标识载体"),
    ("daily", "小心", "只在「小心地滑」「小心台阶」里当部件，不独立成标识"),
    ("daily", "注意", "只在「注意安全」里当部件"),
    ("transit", "候车", "牌子实际写「候车室」，单独两字少见 → 换成 `时刻表`"),
    ("phone", "群", "微信界面无独立「群」字（显示的是群名或「群聊」）→ 换成 `发送`"),
]


# ---------------- 读取现库 ----------------
src = open(WB, encoding="utf-8").read()
rows = TERM.findall(src)                      # (id, scene, text, pinyin, tip)
bank_by_scene = collections.defaultdict(list)
for r in rows:
    bank_by_scene[r[1]].append(r)
bank_texts = set(r[2] for r in rows)
bank_ids = set(r[0] for r in rows)

# 每分区当前最大 id 序号
maxseq = {}
for r in rows:
    m = re.match(r'^(.*)-(\d+)$', r[0])
    if m:
        s, n = m.group(1), int(m.group(2))
        maxseq[s] = max(maxseq.get(s, 0), n)


def next_ids(scene, k):
    start = maxseq.get(scene, 0)
    return ["%s-%d" % (scene, start + 1 + i) for i in range(k)]


# ---------------- 校验 ----------------
fail, warn, info = [], [], []

# 1. 字数 ↔ 音节配对
pairs_bad = []
for scene, items in CAND.items():
    for text, py, tip, lv in items:
        if len(text) != len(py.split(" ")):
            pairs_bad.append((scene, text, len(text), len(py.split(" "))))
if pairs_bad:
    fail.append("字数/音节不配对 %d 条: %s" % (len(pairs_bad), pairs_bad))

# 2. 与现库重词
dup_in = []      # 同一分区内重词（硬缺陷）
dup_cross = []   # 跨分区重词（允许，提示）
for scene, items in CAND.items():
    have = set(r[2] for r in bank_by_scene.get(scene, []))
    for text, py, tip, lv in items:
        if text in have:
            dup_in.append((scene, text))
        elif text in bank_texts:
            hit = [r[0] for r in rows if r[2] == text]
            dup_cross.append((scene, text, hit))
if dup_in:
    fail.append("与同分区既有词重复 %d 条（学习单元重复，硬缺陷）: %s" % (len(dup_in), dup_in))
if dup_cross:
    warn.append("与其它分区已有词重复 %d 条（跨分区允许，但需确认）: %s" % (len(dup_cross), dup_cross))

# 3. 候选内部重词
seen = collections.Counter()
for scene, items in CAND.items():
    for text, py, tip, lv in items:
        seen[text] += 1
inner = [t for t, n in seen.items() if n > 1]
if inner:
    fail.append("候选内部重词: %s" % inner)

# 4. 3500 常用字超纲
common = set()
if os.path.exists(CHARLIST):
    with open(CHARLIST, encoding="utf-8-sig", newline="") as f:
        for row in csv.reader(f):
            c = row[1].strip() if len(row) > 1 else ""
            if len(c) == 1 and "\u4e00" <= c <= "\u9fff":
                common.add(c)
outside = sorted({c for items in CAND.values() for t, _, _, _ in items
                  for c in t if "\u4e00" <= c <= "\u9fff"} - common)
if outside:
    info.append("超出《现代汉语常用字表》3500 的字: %s" % " ".join(outside))

# 5. pypinyin 对照
diffs = []
for scene, items in CAND.items():
    for text, py, tip, lv in items:
        glp = py.split(" ")
        if len(glp) != len(text):
            continue
        p = [x[0] for x in pinyin(text, style=Style.TONE)]
        if len(p) != len(text):
            continue
        for i in range(len(text)):
            if glp[i] != p[i]:
                diffs.append((scene, text, text[i], glp[i], p[i]))

# 6. 新 id 与现库冲突
newid_map = collections.OrderedDict()
for scene, items in CAND.items():
    ids = next_ids(scene, len(items))
    for i, (text, py, tip, lv) in enumerate(items):
        newid_map[ids[i]] = (scene, text, py, tip, lv)
collide = [i for i in newid_map if i in bank_ids]
if collide:
    fail.append("新 id 与现库冲突: %s" % collide)

# ---------------- 输出报告 ----------------
total_new = sum(len(v) for v in CAND.values())
rec = sum(1 for v in CAND.values() for it in v if it[3] == "R")
opt = total_new - rec
total_drop = sum(len(v) for v in DROP.values())

L, w = [], lambda s: L.append(s)
w("# round5 五分区梳理候选（daily / food / appliance / transit / phone）\n")
w("> 自动生成，脚本 `build_round5_candidates.py`（单一数据源，可复跑）。")
w("> 判据见 `.trellis/spec/frontend/wordbank-guidelines.md` §3：**这个词，老人生活环境里是否会以文字形式出现**。\n")
w("## 0. 总览\n")
w("| 分区 | 现有 | 建议新增（推荐） | 建议新增（可选） | 建议删除 | 梳理后 |")
w("|---|---|---|---|---|---|")
for scene in CAND:
    cur = len(bank_by_scene.get(scene, []))
    r = sum(1 for it in CAND[scene] if it[3] == "R")
    o = sum(1 for it in CAND[scene] if it[3] == "O")
    w("| `%s` %s | %d | +%d | +%d | %d | %d |"
      % (scene, SCENE_CN[scene], cur, r, o, len(DROP.get(scene, [])), cur + r + o - len(DROP.get(scene, []))))
w("| **合计** | **%d** | **+%d** | **+%d** | **%d** | **%d** |"
  % (sum(len(bank_by_scene.get(s, [])) for s in CAND), rec, opt, total_drop,
     sum(len(bank_by_scene.get(s, [])) for s in CAND) + total_new - total_drop))
w("")
w("> 全库由 **%d 条 → %d 条**（含删除）。\n"
  % (len(rows), len(rows) + total_new - total_drop))

w("## 1. 建议删除（%d 条，全部在 `daily`）\n" % total_drop)
w("| id | 词条 | 理由 |")
w("|---|---|---|")
for scene, items in DROP.items():
    for i, t, reason in items:
        w("| `%s` | %s | %s |" % (i, t, reason))
w("")
w("> ⚠️ 删除**不回填 id**（`wordbank-guidelines.md` §8：id 一经发布永不复用，回填会让新词继承旧学习状态）。\n")

w("## 2. 起草中自我否掉的候选（判据一致性自检）\n")
w("> 这些词我一度想收，最后按**与上面删除项同一条理由**否掉了 —— 只在词里当部件、没有独立标识载体。")
w("> 列出来是为了让「同一把尺子」可被检查，而不是一边删一边加。\n")
w("| 分区 | 词条 | 否掉的理由 |")
w("|---|---|---|")
for scene, text, reason in REJECTED:
    w("| `%s` | %s | %s |" % (scene, text, reason))
w("")

w("## 3. 建议新增（逐分区，含 id / 拼音 / tip）\n")
for scene, items in CAND.items():
    ids = next_ids(scene, len(items))
    w("### %s `%s`（+%d）\n" % (SCENE_CN[scene], scene, len(items)))
    w("| 级别 | id | 词条 | 拼音 | 载体（在哪见）/ 说明 |")
    w("|---|---|---|---|---|")
    for i, (text, py, tip, lv) in enumerate(items):
        w("| %s | `%s` | %s | %s | %s |" % ("★推荐" if lv == "R" else "可选", ids[i], text, py, tip))
    w("")

w("## 4. 机器校验\n")
w("| 检查项 | 结果 |")
w("|---|---|")
w("| 字数 ↔ 拼音音节配对 | %s |" % ("**失败 %d 条**" % len(pairs_bad) if pairs_bad else "通过（%d 条全配）" % total_new))
w("| 与同分区既有词重复 | %s |" % ("**失败 %d 条**" % len(dup_in) if dup_in else "无"))
w("| 与其它分区重词 | %s |" % (("%d 条（见下）" % len(dup_cross)) if dup_cross else "无"))
w("| 候选内部重词 | %s |" % ("**失败**" if inner else "无"))
w("| 新 id 与现库冲突 | %s |" % ("**失败**" if collide else "无"))
w("| 3500 常用字超纲 | %s |" % ("、".join(outside) if outside else "零超纲"))
w("| pypinyin 对照差异 | %d 处（见下） |" % len(diffs))
w("")
if dup_cross:
    w("跨分区重词明细：\n")
    w("| 候选分区 | 词条 | 现库已有 |")
    w("|---|---|---|")
    for scene, text, hit in dup_cross:
        w("| `%s` | %s | %s |" % (scene, text, "、".join("`%s`" % h for h in hit)))
    w("")
if diffs:
    w("pypinyin 差异（**不等于错**，需逐条判定）：\n")
    w("| 分区 | 词条 | 字 | 候选标音 | pypinyin |")
    w("|---|---|---|---|---|")
    for d in diffs:
        w("| `%s` | %s | %s | %s | %s |" % d)
    w("")
    w("> 本库口径：教学标音写**本调**（不写语流变调）、无调号 = 轻声、词义辨读查词典。\n")

w("## 5. 结论\n")
if fail:
    w("**存在阻断项，不可直接落地：**\n")
    for f in fail:
        w("- %s" % f)
else:
    w("**结构性检查全过**（配对 / 分区内重词 / 候选内部重词 / id 冲突四项无阻断）。\n")
w("")
w("- 新增 %d 条（推荐 %d + 可选 %d），删除 %d 条 → 全库 %d → **%d** 条。"
  % (total_new, rec, opt, total_drop, len(rows), len(rows) + total_new - total_drop))
if warn:
    w("- 需人工确认：%s" % warn[0])

open(OUT_MD, "w", encoding="utf-8", newline="\n").write("\n".join(L) + "\n")

# ---------------- 输出 .kt 片段 ----------------
K = []
K.append("// ⚠️ 原稿留档，勿直接粘贴 —— 粘贴前必须：")
K.append("//   ① 确认 WordBank.kt 未被他人改动（重跑 check_wordbank_invariants.py 取真值）")
K.append("//   ② 确认 id 起点仍是各分区当前最大 +1")
K.append("//   ③ 落地后同步 4 处计数点（wordbank-guidelines.md §9）")
K.append("")
for scene, items in CAND.items():
    ids = next_ids(scene, len(items))
    K.append("// ---- %s（%s）新增 %d 条，建议接在 %s 列表末尾 ----"
             % (SCENE_CN[scene], scene, len(items), scene.upper()))
    for i, (text, py, tip, lv) in enumerate(items):
        K.append('    term("%s", "%s", "%s", "%s", "%s"),%s'
                 % (ids[i], scene, text, py, tip, "  // 可选" if lv == "O" else ""))
    K.append("")
open(OUT_KT, "w", encoding="utf-8", newline="\n").write("\n".join(K) + "\n")

# ---------------- 控制台（纯 ASCII） ----------------
print("round5 total_new=%d rec=%d opt=%d drop=%d" % (total_new, rec, opt, total_drop))
print("bank=%d -> after=%d" % (len(rows), len(rows) + total_new - total_drop))
print("pairs_bad=%d dup_in=%d dup_cross=%d inner=%d id_collide=%d outside=%d pypy_diffs=%d"
      % (len(pairs_bad), len(dup_in), len(dup_cross), len(inner), len(collide), len(outside), len(diffs)))
print("fail=%d" % len(fail))
for f in fail:
    print("  FAIL", f)
for x in dup_cross:
    print("  CROSS", x)
print("->", OUT_MD)
print("->", OUT_KT)
