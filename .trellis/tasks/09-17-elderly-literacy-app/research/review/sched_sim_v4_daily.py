"""v4 审查补充：真实使用策略下的每日队列负荷推算（含分区毕业触发点）。

用户策略假设（推算，非实测）：
- 每次打开推荐频道，把当日队列从头滑到尾；
- 新学卡：停稳 → markSeen（days=1，明天到期）；
- 复习卡：按答对率 p 随机 认识/忘了（认识 → days 升档；忘了 → days=1 且尾部重现一次）。
"""
import random
from datetime import date, timedelta
from sched_sim_v4 import Repo, parse_wordbank, parse_proto_scenes, MODE_NEW, MODE_REVIEW, next_interval

DAY = timedelta(days=1)


def simulate(terms, scenes, days=90, acc=1.0, seed=1, verbose=True):
    rnd = random.Random(seed)
    r = Repo(terms, scenes, date(2026, 9, 17))
    d0 = r.date if False else date(2026, 9, 17)
    rows = []
    for k in range(days):
        r.today = d0 + timedelta(days=k)
        q = r.ensure_queue("rec")
        n_new = q["modes"].count(MODE_NEW)
        n_rev = q["modes"].count(MODE_REVIEW)
        emptied = len(q["queue"]) == 0
        grads = sorted(r.graduated_scenes())
        rows.append((k + 1, len(q["queue"]), n_new, n_rev, len(grads)))
        # 作答
        for i in list(q["queue"]):
            if r.states.get(i) is None:
                r.mark_seen(i)          # 新学卡：停稳即已学
            else:
                if rnd.random() < acc:
                    r.mark_known(i)
                else:
                    r.mark_forgot(i)
    if verbose:
        print(f"--- 答对率 {acc:.0%}，{days} 天（day, 队列长, 新词, 复习, 已毕业分区数）---")
        for row in rows[:40]:
            print("   ", row)
    content_days = [r[0] for r in rows if r[1] > 0]
    empty_days = [r[0] for r in rows if r[1] == 0]
    print(f"  有内容天数 {len(content_days)}/{days} → {content_days[:20]}{'...' if len(content_days) > 20 else ''}")
    print(f"  空队列天数 {len(empty_days)}/{days}（前 20 个：{empty_days[:20]}）")
    print(f"  队列峰值 {max(r[1] for r in rows)}；首日 {rows[0][1]}")
    return rows


if __name__ == "__main__":
    root = "d:/QYJ/MyProject/Mando/"
    wb = parse_wordbank(root + "android-app/app/src/main/java/com/qyj/shibang/data/WordBank.kt")
    terms = [(t["id"], t["scene"]) for t in wb]
    scenes = parse_proto_scenes(root + "prototype/index.html")  # 仅取 id 列表用
    scenes = ["rec", "market", "transit", "hospital", "bank", "gov", "food",
              "phone", "medicine", "express", "property", "emergency", "weather"]

    print("=========== 368 词，全部答对（理想用户） ===========")
    simulate(terms, scenes, days=60, acc=1.0)

    print()
    print("=========== 368 词，答对率 80% ===========")
    simulate(terms, scenes, days=60, acc=0.8, seed=3)

    print()
    print("=========== 单场景频道（market 30 词），全部答对 ===========")
    mterms = [(t["id"], t["scene"]) for t in wb if t["scene"] == "market"]
    r = Repo(mterms, ["rec", "market"], date(2026, 9, 17))
    d0 = date(2026, 9, 17)
    for k in range(20):
        r.today = d0 + timedelta(days=k)
        q = r.ensure_queue("market")
        print(f"   day{k+1}: 队列 {len(q['queue'])} (新 {q['modes'].count(MODE_NEW)}/复习 {q['modes'].count(MODE_REVIEW)})"
              f" 毕业={sorted(r.graduated_scenes())}")
        for i in list(q["queue"]):
            if r.states.get(i) is None:
                r.mark_seen(i)
            else:
                r.mark_known(i)
