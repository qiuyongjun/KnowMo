"""v4 审查：调度器复刻模拟 + 词库/对比度静态核对。

算法逐行照抄 android-app/.../data/StudyRepository.kt（截至 2026-09-17 16:46 快照）：
buildQueue / isDue / markKnown / markSeen / markForgot / ensureQueue / appendQueue /
saveQueuePosition / freePoolIds / isSceneGraduated / graduatedScenes / nextInterval。

结论标注：本文件全部为**推算**（无 JDK/SDK，无法真机运行 Kotlin）。
"""
import re
import random
from datetime import date, timedelta

DAY = timedelta(days=1)
INTERVALS = [1, 3, 7, 15]
GRADUATED_DAYS = INTERVALS[-1]
MODE_NEW, MODE_REVIEW = "NEW", "REVIEW"


def next_interval(cur):
    if cur not in INTERVALS:
        return INTERVALS[1]
    return INTERVALS[min(INTERVALS.index(cur) + 1, len(INTERVALS) - 1)]


class Repo:
    """复刻 StudyRepository"""

    def __init__(self, terms, scenes, today):
        # terms: list of (id, scene)
        self.terms = terms
        self.scenes = scenes          # list of scene id，含 "rec"
        self.today = today
        self.states = {}              # id -> [days, lastSeen(date)]
        self.queues = {}              # channel -> dict(date, queue[], modes[], position)
        self.forgot = {}

    # ---------- 记忆状态 ----------
    def is_due(self, st, today):
        days, last = st
        return last + timedelta(days=days) <= today

    def mark_known(self, tid):
        cur = self.states[tid][0] if tid in self.states else 1
        self.states[tid] = [next_interval(cur), self.today]

    def mark_seen(self, tid):
        if tid in self.states:
            return
        self.states[tid] = [1, self.today]

    def mark_forgot(self, tid):
        self.states[tid] = [1, self.today]
        self.forgot[tid] = self.forgot.get(tid, 0) + 1

    # ---------- 分区毕业 ----------
    def is_scene_graduated(self, scene):
        ids = [t for t, s in self.terms if s == scene]
        if not ids:
            return False
        return all(self.states.get(i, [None])[0] == GRADUATED_DAYS for i in ids)

    def graduated_scenes(self):
        return {s for s in self.scenes if s != "rec" and self.is_scene_graduated(s)}

    # ---------- 调度器 ----------
    def getattr_scene(self, tid):
        return dict(self.terms)[tid]

    def scope_ids(self, ch):
        if ch == "rec":
            g = self.graduated_scenes()
            return [i for i, s in self.terms if s not in g]
        return [i for i, s in self.terms if s == ch]

    def free_pool_scope_ids(self, ch):
        if ch == "rec":
            return [i for i, _ in self.terms]
        return [i for i, s in self.terms if s == ch]

    def build_queue(self, ch, rnd=None, news_order="shuffle"):
        today = self.today
        scope = self.scope_ids(ch)
        due = [(i, st) for i, st in ((i, self.states.get(i)) for i in scope) if st and self.is_due(st, today)]
        due.sort(key=lambda kv: kv[1][1] + timedelta(days=kv[1][0]))
        due = [i for i, _ in due]
        news = [i for i in scope if i not in self.states]
        if news_order == "shuffle" and rnd is not None:
            news = news[:]
            rnd.shuffle(news)

        q, modes = [], []
        di, since = 0, 0
        for n in news:
            q.append(n)
            modes.append(MODE_NEW)
            since += 1
            if since >= 2 and di < len(due):
                q.append(due[di])
                modes.append(MODE_REVIEW)
                di += 1
                since = 0
        while di < len(due):
            q.append(due[di])
            modes.append(MODE_REVIEW)
            di += 1
        return {"date": today, "channel": ch, "queue": q, "modes": modes, "position": 0}

    def ensure_queue(self, ch):
        ex = self.queues.get(ch)
        if ex and ex["date"] == self.today:
            return ex
        fresh = self.build_queue(ch)
        self.queues[ch] = fresh
        return fresh

    def append_queue(self, ch, tid):
        q = self.queues.get(ch)
        if q is None:
            return False
        if q["date"] != self.today or q["queue"].count(tid) != 1:
            return False
        q["queue"].append(tid)
        q["modes"].append(MODE_REVIEW)
        return True

    def save_queue_position(self, ch, pos):
        q = self.queues.get(ch)
        if q is None:
            return
        p = max(0, min(pos, len(q["queue"])))
        q["position"] = p

    def free_pool_ids(self, ch):
        return [i for i in self.free_pool_scope_ids(ch) if i in self.states]


# ==================== 词库 / 原型 数据 ====================
def parse_wordbank(path):
    src = open(path, encoding="utf-8").read()
    pat = re.compile(r'term\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)"\)')
    out = []
    for m in pat.finditer(src):
        tid, scene, text, py, tip = m.groups()
        out.append(dict(id=tid, scene=scene, text=text, py=py.split(" "), tip=tip))
    return out


def parse_prototype(path):
    src = open(path, encoding="utf-8").read()
    seg = src.split("const TERMS = [")[1].split("\n];")[0]
    pat = re.compile(r'\{id:"([^"]+)", scene:"([^"]+)", icon:"[^"]*", text:"([^"]+)", chars:\[(.*?)\], tip:"([^"]*)", kind:"([^"]+)"')
    out = []
    for m in pat.finditer(seg):
        tid, scene, text, chars, tip, kind = m.groups()
        out.append(dict(id=tid, scene=scene, text=text, chars=chars, tip=tip, kind=kind))
    return out


def parse_proto_scenes(path):
    src = open(path, encoding="utf-8").read()
    seg = src.split("const SCENES = [")[1].split("\n];")[0]
    return re.findall(r'\{id:"([^"]+)"', seg)


# ==================== WCAG 对比度 ====================
def lum(hexcolor):
    h = hexcolor.lstrip("#")
    ch = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    lin = [c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4 for c in ch]
    return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]


def contrast(a, b):
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


if __name__ == "__main__":
    root = "d:/QYJ/MyProject/Mando/"
    wb = parse_wordbank(root + "android-app/app/src/main/java/com/qyj/shibang/data/WordBank.kt")
    proto = parse_prototype(root + "prototype/index.html")
    pscenes = parse_proto_scenes(root + "prototype/index.html")

    print("========== 1. 词库静态核对（安卓 WordBank.kt） ==========")
    ids = [t["id"] for t in wb]
    dup = {i for i in ids if ids.count(i) > 1}
    print("词条总数:", len(wb), "| 唯一 id:", len(set(ids)), "| 重复 id:", dup or "无")
    bad = [t["id"] for t in wb if len(t["text"]) != len(t["py"])]
    print("拼音音节数 != 字数:", bad or "无")
    from collections import Counter, defaultdict
    per_scene = Counter(t["scene"] for t in wb)
    print("场景数:", len(per_scene), "| 每场景词数:", dict(per_scene))
    maxlen = max(len(t["text"]) for t in wb)
    cnt_len = Counter(len(t["text"]) for t in wb)
    print("字数分布:", dict(sorted(cnt_len.items())), "| 最长:", maxlen)
    print(">=5 字词条数:", sum(v for k, v in cnt_len.items() if k >= 5))
    # 安卓端 SCENES
    sd = open(root + "android-app/app/src/main/java/com/qyj/shibang/data/StudyData.kt", encoding="utf-8").read()
    ascenes = re.findall(r'Scene\("([^"]+)"', sd)
    print("StudyData SCENES:", len(ascenes), ascenes)
    print("SCENES 中无词条的分区:", [s for s in ascenes if s not in per_scene and s != "rec"] or "无")
    print("词条里出现但 SCENES 没有的 scene:", [s for s in per_scene if s not in ascenes] or "无")

    print()
    print("========== 2. 两端漂移（原型 vs 安卓） ==========")
    pids = [t["id"] for t in proto]
    print("原型 TERMS 条数:", len(proto), "| 原型 SCENES:", len(pscenes), pscenes)
    print("原型词条场景分布:", dict(Counter(t["scene"] for t in proto)))
    overlap = set(pids) & set(ids)
    print("与安卓 id 交集:", len(overlap), "/", len(pids))
    print("安卓有、原型没有的 id 数:", len(set(ids) - set(pids)))
    print("原型有、安卓没有的 id:", sorted(set(pids) - set(ids)) or "无")
    print("安卓新增场景在原型缺失:", [s for s in per_scene if s not in proto[0]["scene"] and s not in [t["scene"] for t in proto]])

    print()
    print("========== 3. 适老化落值（WCAG 推算） ==========")
    pairs = [
        ("AppText #1A1A1A / 白卡", "#1A1A1A", "#FFFFFF"),
        ("AppText2 #424244 / 白卡", "#424244", "#FFFFFF"),
        ("AppText2 #424244 / AppSurface #F5F7FA", "#424244", "#F5F7FA"),
        ("拼音 BluePrimary #1565C0 / 白卡", "#1565C0", "#FFFFFF"),
        ("新学 badge BlueDark #0D47A1 / BlueBg #E3F2FD", "#0D47A1", "#E3F2FD"),
        ("复习 badge OrangeDark #BF360C / OrangeBg #FFF3E0", "#BF360C", "#FFF3E0"),
        ("温故 badge GreenKnown #1B5E20 / GreenBg #E8F5E9", "#1B5E20", "#E8F5E9"),
        ("反馈按钮 白字 / GreenKnown #1B5E20", "#FFFFFF", "#1B5E20"),
        ("反馈按钮 白字 / RedForgot #B71C1C", "#FFFFFF", "#B71C1C"),
        ("完成卡按钮 白字 / BluePrimary #1565C0", "#FFFFFF", "#1565C0"),
        ("结果文案 GreenKnown / GreenBg", "#1B5E20", "#E8F5E9"),
        ("结果文案 OrangeDark / OrangeBg", "#BF360C", "#FFF3E0"),
        ("频道chip 激活 BlueDark / BlueBg", "#0D47A1", "#E3F2FD"),
        ("频道chip 毕业 GreenKnown / AppSurface", "#1B5E20", "#F5F7FA"),
    ]
    for name, fg, bg in pairs:
        c = contrast(fg, bg)
        print(f"  {name}: {c:.2f}:1  {'OK(>=7)' if c >= 7 else ('AA(4.5-7)' if c >= 4.5 else 'FAIL')}")

    print()
    print("========== 4. 调度器路径推算 ==========")
    # 用真实词库（368 词）模拟
    terms368 = [(t["id"], t["scene"]) for t in wb]
    scenes368 = ascenes
    d0 = date(2026, 9, 17)

    r = Repo(terms368, scenes368, d0)
    q1 = r.ensure_queue("rec")
    print(f"S1 首日 rec：队列 {len(q1['queue'])} 张（NEW {q1['modes'].count(MODE_NEW)} / REVIEW {q1['modes'].count(MODE_REVIEW)}）")
    qm = r.ensure_queue("market")
    print(f"   同日 market：队列 {len(qm['queue'])} 张")

    # 用户只学 20 张（停稳播报 = markSeen）
    seen = q1["queue"][:20]
    for i in seen:
        r.mark_seen(i)
    r.save_queue_position("rec", 20)
    # 当天重启：ensureQueue 应返回同一队列 + position
    same = r.ensure_queue("rec")
    print(f"S2 当天重启：queue 长度 {len(same['queue'])}（应与 S1 相同），position={same['position']}，"
          f"队列内容一致={same['queue'] == q1['queue']}")

    # 隔天
    r.today = d0 + DAY
    q2 = r.ensure_queue("rec")
    print(f"S3 隔天 rec：队列 {len(q2['queue'])} 张（NEW {q2['modes'].count(MODE_NEW)} / REVIEW {q2['modes'].count(MODE_REVIEW)}）"
          f"，position 重置={q2['position']}")

    # 忘了 → 尾部重现
    r2 = Repo(terms368, scenes368, d0)
    r2.ensure_queue("rec")
    victim = r2.queues["rec"]["queue"][0]
    before = len(r2.queues["rec"]["queue"])
    ok1 = r2.append_queue("rec", victim)
    r2.mark_forgot(victim)
    ok2 = r2.append_queue("rec", victim)   # 第二次：队列中已出现 2 次 → 应 False
    after = len(r2.queues["rec"]["queue"])
    print(f"S4 忘了的词：首次 appendQueue={ok1}，重复 appendQueue={ok2}（应为 False）"
          f"，队列 {before} → {after}，尾部 id 匹配={r2.queues['rec']['queue'][-1] == victim}，"
          f"出现次数={r2.queues['rec']['queue'].count(victim)}")
    print(f"   markForgot 后 state={r2.states[victim]} → 次日到期={r2.is_due(r2.states[victim], d0 + DAY)}，"
          f"当天再次到期={r2.is_due(r2.states[victim], d0)}")

    # 空队列日（小词库 6 词，全部认识两轮）
    small = [("m-1", "market"), ("m-2", "market"), ("m-3", "market"), ("m-4", "market")]
    r3 = Repo(small, ["rec", "market"], d0)
    r3.ensure_queue("rec")
    for i in [x[0] for x in small]:
        r3.mark_seen(i)
    log = []
    for day in range(0, 8):
        r3.today = d0 + timedelta(days=day)
        q = r3.ensure_queue("rec")
        log.append((str(r3.today), len(q["queue"]), q["modes"].count(MODE_REVIEW)))
        # 假设用户当天把队列全部答对
        for i in list(q["queue"]):
            r3.mark_known(i)
    print("S5 小词库全部答对 8 天（日期, 队列长度, 复习数）:", log)

    # 毕业路径
    r4 = Repo(terms368, scenes368, d0)
    market_ids = [i for i, s in terms368 if s == "market"]
    for i in market_ids:
        r4.mark_seen(i)
        for _ in range(3):
            r4.mark_known(i)          # 1→3→7→15
    print(f"S6 毕业：market 词数 {len(market_ids)}，全部 days=15 → graduated={r4.graduated_scenes()}")
    r4.today = d0 + timedelta(days=15)   # 让 market 全部到期
    q4 = r4.ensure_queue("rec")
    due_market = [i for i in market_ids if r4.is_due(r4.states[i], r4.today)]
    in_rec = [i for i in due_market if i in q4["queue"]]
    print(f"   market 到期词 {len(due_market)} 个，其中出现在 rec 队列: {len(in_rec)} 个（设计上应为 0）")
    print(f"   rec 队列长度 {len(q4['queue'])}（NEW {q4['modes'].count(MODE_NEW)}）")
    q4m = r4.ensure_queue("market")
    print(f"   market 场景队列长度 {len(q4m['queue'])}（复习 {q4m['modes'].count(MODE_REVIEW)}）")
    print(f"   自由刷池(rec) 含 market 词: {len([i for i in r4.free_pool_ids('rec') if i in market_ids])}")

    # 稳态负荷（无配额，368 词，全部答对）
    r5 = Repo(terms368, scenes368, d0)
    rnd = random.Random(7)
    loads = []
    day = d0
    for k in range(60):
        r5.today = day
        q = r5.ensure_queue("rec")
        loads.append(len(q["queue"]))
        for i in list(q["queue"]):
            if i in r5.states or True:
                if r5.states.get(i) is None:
                    r5.mark_seen(i)
                r5.mark_known(i)
        day += DAY
    print("S7 无配额 368 词全部答对，前 20 天队列长度:", loads[:20])
    print("   第 21-60 天非零天数:", len([x for x in loads[20:] if x > 0]), "/ 40；平均:", round(sum(loads) / len(loads), 1))

    # 配额对照（仅示意：每天最多 N 个新词）
    def sim_with_quota(n_quota):
        r = Repo(terms368, scenes368, d0)
        rr = random.Random(7)
        day = d0
        loads2, grads = [], None
        for k in range(90):
            r.today = day
            q = r.build_queue("rec", rr)   # 绕过 ensure_queue 缓存
            news = [i for i, m in zip(q["queue"], q["modes"]) if m == MODE_NEW]
            keep = set(q["queue"]) - set(news) | set(news[:n_quota])
            q2 = [i for i in q["queue"] if i in keep]
            loads2.append(len(q2))
            for i in q2:
                if r.states.get(i) is None:
                    r.mark_seen(i)
                r.mark_known(i)
            day += DAY
        return loads2

    for nq in (10, 20):
        l = sim_with_quota(nq)
        first = next((k + 1 for k, v in enumerate(l) if k > 30 and v == 0), None)
        print(f"   配额={nq}/天：前 7 天负荷 {l[:7]}，30 天平均 {round(sum(l[:30])/30,1)}，"
              f"峰值 {max(l)}，第 31 天后首次空队列日={first}")
