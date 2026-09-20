# -*- coding: utf-8 -*-
"""模拟当前学习机制（09-17-scheduling-v5 口径），用于回答只能靠算的问题：
每天多少张卡 / 学完全部要多久 / 真实遗忘率下债务会不会失控 / 哪个杠杆能恢复平衡。

严格镜像 StudyRepository.kt：
  双层状态机：连击层 counts（满 COMBO 移出当日队列）+ 间隔层 days ∈ INTERVALS
  升级闸门：满连击 且 lastSeen != 今天 才升级（每日最多升一级）；lapses 升级时减半
  忘了：days=1、lastSeen=今天、lapses+1、连击清零（不封顶）
  buildQueue：learned 按 (lapses 降序, dueTime 升序) → due=到期 → news=未学洗牌
    rec: due >= 20 ? due[:15] : (due + news)[:10]
  卡数展开：新词 = 1 教读 + 3 考核；复习词 = 每轮 1 张（未满连击追加队尾 = 当天再见）

遗忘模型：单一参数 f30 = 「间隔 30 天时的回忆失败率」，
  其余天数按幂律衰减 p(d) = 1 - (1-f30)^(d/30)（d=0 刚教读 → 靠工作记忆，取 0）。
"""

import collections
import io
import os
import random
import re
import statistics

HERE = os.path.dirname(os.path.abspath(__file__))
# 根目录自推导（向上找 android-app），Mando→KnowMo 改名前后均可用
ROOT = HERE
while not os.path.isdir(os.path.join(ROOT, "android-app")):
    ROOT = os.path.dirname(ROOT)
SRC = os.path.join(ROOT, "android-app", "app", "src", "main", "java", "com", "knowmo", "app", "data", "WordBank.kt")
OUT = os.path.join(HERE, "mechanism-sim-report.txt")

raw = io.open(SRC, encoding="utf-8").read()
WORD_SCENE = {i: s for i, s in re.findall(r'term\("([^"]+)",\s*"([^"]+)"', raw)}
ALL_WORDS = list(WORD_SCENE)
SCENE_COUNT = collections.Counter(WORD_SCENE.values())

INTERVALS = [1, 3, 7, 15, 30]
QUOTA = 10
DEBT_THRESHOLD = 20
DEBT_QUOTA = 15


def next_interval(d, intervals):
    for v in intervals:
        if v > d:
            return v
    return intervals[-1]


def pct(xs, p):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(len(xs) * p))] if xs else 0


class Machine:
    def __init__(self, f30=0.0, seed=1, intervals=None, combo_of=None,
                 quota=QUOTA, debt_threshold=DEBT_THRESHOLD, debt_quota=DEBT_QUOTA,
                 new_reserve=0, scope=None):
        self.rnd = random.Random(seed)
        self.f30 = f30
        self.intervals = list(intervals or INTERVALS)
        self.combo_of = combo_of or (lambda d: 3)
        self.quota = quota
        self.debt_threshold = debt_threshold
        self.debt_quota = debt_quota
        self.new_reserve = new_reserve
        self.scope = list(scope if scope is not None else ALL_WORDS)
        self.reset()

    def reset(self):
        self.state = {}       # id -> [days, lastSeen, lapses]
        self.today = 0
        self.counts = {}
        self.seen = set()
        self.known_answers = self.forgot_answers = 0

    # ---------------- 用户 ----------------
    def fails(self, days):
        if days <= 0:
            return False
        return self.rnd.random() < (1.0 - (1.0 - self.f30) ** (days / 30.0))

    # ---------------- 间隔层 ----------------
    def due_time(self, i):
        d, ls, _ = self.state[i]
        return ls + d

    def learned(self):
        return [i for i in self.scope if i in self.state]

    def unlearned(self):
        return [i for i in self.scope if i not in self.state]

    def build_queue(self, daily=True):
        learned = sorted(self.learned(), key=lambda i: (-self.state[i][2], self.due_time(i)))
        due = [i for i in learned if self.due_time(i) <= self.today]
        news = self.unlearned()
        self.rnd.shuffle(news)
        if not daily:
            return learned + news, len(due), False
        debt = len(due) >= self.debt_threshold
        due_cap = self.debt_quota if debt else self.quota
        if self.new_reserve:
            # 保底通道：复习照旧（含清债扩容），另留固定名额给新词，新词入口永不关闭
            pool = due[:due_cap] + news[: self.new_reserve]
        else:
            pool = due[:due_cap] if debt else (due + news)[: self.quota]
        return pool, len(due), debt

    # ---------------- 连击层 ----------------
    def combo_target(self, i):
        days = self.state[i][0] if i in self.state else 0
        return self.combo_of(days)

    def removed(self, i):
        return self.counts.get(i, 0) >= self.combo_target(i)

    def mark_known(self, i):
        tgt = self.combo_target(i)
        c = min(self.counts.get(i, 0) + 1, tgt)
        self.counts[i] = c
        self.known_answers += 1
        if c >= tgt:
            if i not in self.state:
                self.state[i] = [1, self.today, 0]
            elif self.state[i][1] != self.today:
                cur = self.state[i]
                self.state[i] = [next_interval(cur[0], self.intervals), self.today, cur[2] // 2]

    def mark_forgot(self, i):
        cur = self.state.get(i)
        self.state[i] = [1, self.today, (cur[2] if cur else 0) + 1]
        self.counts[i] = 0
        self.forgot_answers += 1

    def run_day(self, daily=True):
        self.today += 1
        self.counts = {}
        self.seen = set()
        self.known_answers = self.forgot_answers = 0
        pool, due_size, debt = self.build_queue(daily)

        queue = [(i, "NEW" if i not in self.state else "REVIEW") for i in pool]
        cards = 0
        k = 0
        while k < len(queue):
            i, mode = queue[k]
            k += 1
            cards += 1
            if mode == "NEW":
                if i not in self.seen:
                    self.seen.add(i)
                    if not self.removed(i):
                        queue.append((i, "REVIEW"))
                continue
            if self.fails(0 if i not in self.state else self.state[i][0]):
                self.mark_forgot(i)
            else:
                self.mark_known(i)
            if not self.removed(i):
                queue.append((i, "REVIEW"))
        return dict(cards=cards, due=due_size, debt=debt, pool=len(pool),
                    learned=len(self.learned()))

    def hist(self):
        h = collections.Counter()
        for i, (d, _, _) in self.state.items():
            h[d] += 1
        return dict(sorted(h.items()))


def run(m, days, daily=True):
    return [m.run_day(daily) for _ in range(days)]


def steady(m, days):
    rows = run(m, days)
    cards = [r["cards"] for r in rows]
    due = [r["due"] for r in rows]
    return dict(
        cards_mean=statistics.mean(cards), cards_med=statistics.median(cards),
        cards_p90=pct(cards, 0.9), cards_max=max(cards),
        due_mean=statistics.mean(due), due_last=due[-1],
        debt_pct=100 * sum(1 for r in rows if r["debt"]) / len(rows),
        learned=rows[-1]["learned"], hist=m.hist(), cards=cards, due=due,
    )


L = []
def w(s=""):
    L.append(s)


w("=" * 80)
w("学习机制模拟（镜像 09-17-scheduling-v5）—— 本机无法编译，故以模拟器取数")
w("=" * 80)
w(f"词库 {len(ALL_WORDS)} 词 / {len(SCENE_COUNT)} 分区："
  + "  ".join(f"{s}:{c}" for s, c in SCENE_COUNT.most_common()))
w(f"常量 INTERVALS={INTERVALS} QUOTA={QUOTA} DEBT={DEBT_THRESHOLD}->{DEBT_QUOTA}")
w("遗忘模型 p(d)=1-(1-f30)^(d/30)，f30 = 间隔 30 天时的回忆失败率")
w("")

# ============================================================ 1 结构性乘法
w("-" * 80)
w("【1】卡数 = 词数 × 连击目标：这是全部体量的来源")
w("-" * 80)
w("  新词 1 个  = 1 教读卡 + 3 考核卡                      = 4 张")
w("  复习词 1 个 = 3 考核卡（每次未满 3 就追加回队尾）        = 3 张")
w(f"  → 配额 {QUOTA} 个全到期词 = {QUOTA*3} 张；清债日 {DEBT_QUOTA} 个 = {DEBT_QUOTA*3} 张（比平常更重）")
w("")

# ==================================================== 2 理想用户：学得完吗
w("-" * 80)
w("【2】理想用户（f30=0，从不答错）：能不能学完 368 词？")
w("-" * 80)
m = Machine(f30=0.0, seed=0)
marks = {}
for d in range(1, 3701):
    r = m.run_day()
    marks[d] = (r["learned"], r["due"], r["cards"], r["debt"])
for d in (1, 2, 3, 30, 120, 365, 730, 1460, 2190, 3650):
    lrn, due, cards, debt = marks[d]
    w(f"  第 {d:>4} 天：已学 {lrn:>3} 词 / 368   当日到期 {due:>2}   卡片 {cards:>2} 张"
      f"{'   清债模式' if debt else ''}")
first = next((d for d in marks if marks[d][0] == len(ALL_WORDS)), None)
w(f"  → 首次学满 368 词的日期：{'第 ' + str(first) + ' 天' if first else '3650 天内未出现'}")
w(f"  → 第 3650 天：已学 {marks[3650][0]} 词，间隔分布 {m.hist()}")
w("")
w("  结论：新词入口 =「due 与 news 抢同一个配额」，一旦每日到期量吃满配额，新词为 0。")
w("        已学词全部停在封顶间隔时，每日到期 = 已学数 / 封顶，令其等于配额：")
w(f"          学到的词数上限 = 配额 × 封顶 = {QUOTA} × {INTERVALS[-1]} = {QUOTA*INTERVALS[-1]} 词")
w(f"        词库有 {len(ALL_WORDS)} 词 → 剩下的 {len(ALL_WORDS)-QUOTA*INTERVALS[-1]} 词永远不会被递给用户。")
w("")
mm = Machine(f30=0.0, seed=0, intervals=[1, 3, 7, 15, 30, 60])
for _ in range(800):
    mm.run_day()
done = "公式成立" if len(mm.learned()) == len(ALL_WORDS) else "与公式不符"
w(f"  验证公式：理想用户 + 封顶改 60 → 上限应为 10×60=600 > 368，实测 800 天后已学 "
  f"{len(mm.learned())} 词 → {done}")
w("")

# ============================================== 3 真实遗忘率：债务稳定性
w("-" * 80)
w("【3】真实遗忘率下的收支平衡（最关键）：容量 = 配额，需求 = 到期 + 重做")
w("-" * 80)
w(f"{'f30':>6} {'日均需求':>9} {'容量':>5} {'日均卡数':>9} {'P90':>5} {'清债日':>7} "
  f"{'第60天到期':>11} {'第365天到期':>12} {'已学':>5}")
for f30 in (0.0, 0.10, 0.15, 0.20, 0.30, 0.40):
    mm = Machine(f30=f30, seed=5)
    rows = run(mm, 365)
    seg = rows[60:]
    w(f"{f30:>6.2f} {statistics.mean([r['due'] for r in seg]):>9.1f} "
      f"{QUOTA:>5} {statistics.mean([r['cards'] for r in seg]):>9.1f} "
      f"{pct([r['cards'] for r in seg], 0.9):>5} "
      f"{100*sum(1 for r in seg if r['debt'])/len(seg):>6.0f}% "
      f"{rows[59]['due']:>11} {rows[364]['due']:>12} {rows[364]['learned']:>5}")
w("  当日到期 = 每天一开门就有多少词在等着被服务（含当天要服务的）。它一旦 ≥ 配额，")
w("  新词就一个也挤不进来 → 已学词数被冻结；而冻结点的位置取决于重做（忘了）的量。")
w(f"  稳定条件：配额 ≥ 已学词数/封顶 × 1/(1-f30)。已学 368、封顶 30：")
for f30 in (0.10, 0.20, 0.30):
    need = 368 / 30 / (1 - f30)
    w(f"    f30={f30:.0%} → 需要每日 {need:.1f} 词（{need*3:.0f} 张卡），"
      f"现状配额 {QUOTA} 词（{QUOTA*3} 张）{'✔' if QUOTA >= need else '✘ 不够'}")
w("")

# ======================================================= 4 各杠杆的对比
w("-" * 80)
w("【4】候选杠杆对比（都跑 365 天，f30=0.30 的现实用户）")
w("-" * 80)
levers = [
    ("现状", dict()),
    ("连击分档 days>=15 考 1 次", dict(combo_of=lambda d: 1 if d >= 15 else 3)),
    ("连击分档 days>=15 考 2 次", dict(combo_of=lambda d: 2 if d >= 15 else 3)),
    ("配额 10 → 20", dict(quota=20, debt_quota=25, debt_threshold=30)),
    ("新词保底 3 个 + 复习 10", dict(new_reserve=3)),
    ("封顶 30 → 60 天", dict(intervals=[1, 3, 7, 15, 30, 60])),
    ("封顶 60 + 配额 10/15", dict(intervals=[1, 3, 7, 15, 30, 60], quota=10,
                                  debt_quota=15, debt_threshold=20)),
]
w(f"{'方案':<26} {'日均卡数':>8} {'P90':>5} {'第365天到期':>11} {'到期量趋势':>11} {'已学':>5}  间隔分布")
for tag, kw in levers:
    mm = Machine(f30=0.30, seed=5, **kw)
    rows = run(mm, 365)
    seg = rows[60:]
    d60, d180, d365 = rows[59]["due"], rows[179]["due"], rows[364]["due"]
    trend = "↗ 变多" if d365 > d180 > d60 else ("→ 平稳" if abs(d365 - d180) <= 3 else "↘ 变少")
    w(f"{tag:<26} {statistics.mean([r['cards'] for r in seg]):>8.1f} "
      f"{pct([r['cards'] for r in seg], 0.9):>5} {d365:>11} {trend:>10} "
      f"{rows[364]['learned']:>5}  {mm.hist()}")
w("")

# ================================================ 5 「忘了」不封顶的真实代价
w("-" * 80)
w("【5】「忘了不封顶」：一个词当天要几张卡才过？")
w("-" * 80)
w("（连对 3 次才通过，任何一次忘了清零重来 → 期望卡数 = 1/s + 1/s² + 1/s³，s = 单词成功率）")
for f30 in (0.0, 0.10, 0.20, 0.30, 0.40, 0.50, 0.60):
    s = 1 - f30
    exp3 = sum(1 / (s ** k) for k in range(1, 4)) if s > 0 else float("inf")
    exp1 = 1 / s if s > 0 else float("inf")
    w(f"  该词成功率 {s:>4.0%}（f30={f30:.2f}）→ 3 连击期望 {exp3:6.2f} 张 / 目标改 1 次则 {exp1:5.2f} 张")
w("  尾部：成功率越低，当天越可能被单个词拖死（不封顶 = 无上界）。")
w("")

# ============================================ 6 分区（R9）体量 + 次日债务
w("-" * 80)
w("【6】分区 = 该区全部词（R9）：一次刷完一个分区的体量")
w("-" * 80)
scene = "hospital"
ids = [i for i in ALL_WORDS if WORD_SCENE[i] == scene]
mm = Machine(f30=0.30, seed=9, scope=ALL_WORDS)
pool, due, debt = mm.build_queue(daily=False)
mm.today += 1
w(f"「{scene}」{len(ids)} 词，模拟用户只刷这个分区（scope = 该区）：")
m3 = Machine(f30=0.30, seed=9, scope=ids)
r = m3.run_day(daily=False)
w(f"  首次进入（全部新词）：入队 {r['pool']} 词 → 当天 {r['cards']} 张卡（理论 {len(ids)}×4={len(ids)*4} 张）")
r2 = m3.run_day(daily=True)   # 次日走 rec 频道：这 32 词全部到期
w(f"  次日在每日任务频道：当日到期 {r2['due']} 词 → 清债模式={r2['debt']}，"
  f"入队 {r2['pool']} 词，{r2['cards']} 张卡")
w(f"  → 分区刷一批新词 = 给明天埋一批到期债（每个新词都会在次日到期）")
w("")

# ============================================== 7 「每词 3 张」是不是必要
w("-" * 80)
w("【7】把连击改 1 次，等价于什么？")
w("-" * 80)
w("  现状：1 个词当天要连续答对 3 次才算过——第 1 次才是真正的「跨天回忆测试」，")
w("        第 2/3 次发生在同一分钟内，测的是工作记忆。")
w("  改 1 次后：容量同为 10 词时可服务 10 个不同词（词覆盖面 ×3），")
w("        代价是同日内没有「误点 √ 的纠错机会」。")
w("")

w("=" * 80)
w("口径说明：f30 是假设参数（本机无真机数据）。结构性结论（卡数乘法、配额竞争、")
w("          上限 = 配额 × 封顶）不依赖 f30 具体取值；只有「多快失控」依赖它。")
w("=" * 80)

# ======================================================= 8 组合杠杆
w("")
w("-" * 80)
w("【8】组合杠杆（f30=0.30，跑 365 天）：哪一套能把「卡数」和「学到词数」同时拉正？")
w("-" * 80)
COMBO_A = lambda d: 1 if d >= 15 else 3
COMBO_B = lambda d: 3 if d == 0 else 1     # 只有「首次学习日」考 3 次，复习一律 1 次
COMBO_B2 = lambda d: 3 if d == 0 else 2    # 复习一律 2 次
COMBOS = [
    ("现状", dict()),
    ("A 复习≥15 天考 1 次", dict(combo_of=COMBO_A)),
    ("B 复习一律考 1 次", dict(combo_of=COMBO_B)),
    ("B' 复习一律考 2 次", dict(combo_of=COMBO_B2)),
    ("B + 新词保底 2", dict(combo_of=COMBO_B, new_reserve=2)),
    ("B + 保底 2 + 配额 12", dict(combo_of=COMBO_B, new_reserve=2, quota=12,
                                 debt_quota=16, debt_threshold=24)),
    ("B + 保底 2 + 封顶 60", dict(combo_of=COMBO_B, new_reserve=2,
                                 intervals=[1, 3, 7, 15, 30, 60])),
    ("A + 保底 2 + 封顶 60", dict(combo_of=COMBO_A, new_reserve=2,
                                 intervals=[1, 3, 7, 15, 30, 60])),
]
w(f"{'方案':<24} {'日均卡数':>8} {'P90':>5} {'最大':>5} {'第365天到期':>11} {'已学/368':>9}  间隔分布")
for tag, kw in COMBOS:
    mm = Machine(f30=0.30, seed=5, **kw)
    rows = run(mm, 365)
    seg = rows[60:]
    cs = [r["cards"] for r in seg]
    w(f"{tag:<24} {statistics.mean(cs):>8.1f} {pct(cs, 0.9):>5} {max(cs):>5} "
      f"{rows[364]['due']:>11} {rows[364]['learned']:>9}  {mm.hist()}")
w("")
w("读法：")
w("  - 「第365天到期」= 那天一开门有多少词在等。它应 ≤ 当日容量，否则复习永远迟到。")
w("  - 配额是「当日容量」，提配额能多学词，但卡数几乎线性上涨（10→20 时卡数翻倍）。")
w("  - 连击分档把每个词的汇率从 3 降到 1–3，是唯一能同时降卡数、升词数的杠杆。")
w("  - B（复习一律 1 次）比 A 更彻底：真正吃掉容量的不是长间隔，而是")
w("    「忘了 → 打回 1 天 → 明天又来且还要连对 3 次」的 churn。")
w("  - 新词保底保证词库走得完，但要配合连击分档，否则只是把债从新词挪到复习。")
w("  - 封顶拉长降低「词的到期频率」，等价于提高容量，但会让长间隔成功率下降（f30 上升）。")

io.open(OUT, "w", encoding="utf-8").write("\n".join(L))
print("ok")
