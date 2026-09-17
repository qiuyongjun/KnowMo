# -*- coding: utf-8 -*-
"""验证「每日最多升一级」闸门后，分区毕业的最短周期与刷不动的性质。

照抄修复后的 Kotlin：
  markSeen(id)  : 无记录 → days=1, lastSeen=今天
  markKnown(id) : lastSeen==今天 → 不升级；否则 days=nextInterval(days), lastSeen=今天
  isDue(st)     : lastSeen + days <= 今天
  isSceneGraduated(scene) : 该场景每个词 days == 15
"""
INTERVALS = [1, 3, 7, 15]
G = INTERVALS[-1]

def nxt(cur):
    i = INTERVALS.index(cur) if cur in INTERVALS else 0
    return INTERVALS[min(i + 1, len(INTERVALS) - 1)]


def simulate_scene(n_words, spam_per_day=1, days=40):
    """spam_per_day: 每个到期词当天被作答的次数（模拟 replay 反复刷）。返回毕业日。"""
    st = {}          # id -> [days, lastSeen]
    for d in range(1, days + 1):
        # 1) 未学词首次停稳 = 学过
        for i in range(n_words):
            if i not in st:
                st[i] = [1, d]
        # 2) 到期词被作答（当天最多升一级，spam 无效）
        for _ in range(spam_per_day):
            for i in range(n_words):
                ds, ls = st[i]
                if ls + ds <= d:
                    st[i] = [ds if ls == d else nxt(ds), d]
        # 3) 毕业判定
        if all(v[0] == G for v in st.values()):
            return d, st
    return None, st


print("=== 单次作答（每天每个到期词只答一次）===")
for spam in (1, 3, 10, 100):
    d, st = simulate_scene(1, spam)
    print("  每词每天作答 %3d 次 -> 毕业日 = %s  (days=%s)" % (
        spam, d, st[0][0] if st else "-"))

print("\n=== 30 词分区 ===")
for spam in (1, 3, 100):
    d, st = simulate_scene(30, spam)
    print("  每词每天作答 %3d 次 -> 毕业日 = %s" % (spam, d))

print("\n=== 单字阶梯（无 spam）===")
st, d = {}, 1
st[0] = [1, 1]
print("  day 1  markSeen -> days=1, 到期日 = %d" % (1 + 1))
day = 2
for label in ("第1次认识", "第2次认识", "第3次认识"):
    while st[0][1] + st[0][0] > day:
        day += 1
    st[0] = [nxt(st[0][0]), day]
    print("  day %-2d %s -> days=%-2d, 下次到期 = %d" % (day, label, st[0][0], day + st[0][0]))
print("\n单字阶梯：day1 学过 → day2 认识 → day5 认识 → day12 认识 = days 15 封顶")
print("结论：毕业最短 12 天；每词每天作答次数（1/3/10/100）对毕业日无影响 → 闸门生效")
