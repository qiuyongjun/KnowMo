# -*- coding: utf-8 -*-
"""用 v3 调度器的真实算法，模拟「每日队列」在不同新词配额下的负荷。

算法照抄 StudyRepository.buildQueue / isDue / markKnown / markForgot：
  - 到期复习词（lastSeen + days <= today）→ REVIEW
  - 无 TermState 的词 → NEW（当前实现：全部加入，无配额）
  - 「认识」间隔 1→3→7→15；「忘了」重置 1（明天到期）
  - 场景频道只取本场景词；推荐频道取全部词
"""
import os

BASE = r"D:\QYJ\MyProject\Mando\.trellis\tasks\09-17-elderly-literacy-app\research\review"
OUT = os.path.join(BASE, "quota_sim.md")
INTERVALS = [1, 3, 7, 15]

REC = 368          # 推荐频道词条数（= 全库）
SCENE = [30, 30, 32, 30, 30, 32, 32, 30, 30, 30, 32]  # 场景频道规模


def simulate(total, new_per_day, known_rate, days=180):
    """第一天 = day 1。返回 (每日卡片数列表, 学完全部新词的天数)"""
    state = {}          # id -> [days, last_seen_day]
    learned = 0
    load = []
    cover_day = None
    for d in range(1, days + 1):
        due, news = [], []
        for i in range(total):
            st = state.get(i)
            if st is None:
                news.append(i)
            elif st[1] + st[0] <= d:
                due.append(i)
        due.sort(key=lambda i: state[i][1] + state[i][0])
        new_today = news[:new_per_day] if new_per_day else []
        for i in new_today:
            state[i] = [1, d]
            learned += 1
        for i in due:
            k = __import__("random").Random(i * 31 + d).random() < known_rate
            days_now = state[i][0]
            if k:
                idx = INTERVALS.index(days_now) if days_now in INTERVALS else 0
                state[i] = [INTERVALS[min(idx + 1, len(INTERVALS) - 1)], d]
            else:
                state[i] = [1, d]
        load.append(len(new_today) + len(due))
        if cover_day is None and learned >= total:
            cover_day = d
    return load, cover_day


L = []
w = L.append
w("# 每日队列负荷模拟（v3 调度器，新词配额 = 不限）\n")
w("> 算法照抄 `StudyRepository.buildQueue/isDue/markKnown/markForgot`。")
w("> 当前实现的首日队列 = 全部未学新词，**没有配额**。\n")

w("## 1. 当前实现在 36 词 vs 368 词下的首日队列\n")
w("| 频道 | 词条数 | 首日队列（当前实现） |")
w("|---|---|---|")
w("| 推荐（rec） | 368 | **368 张** |")
w("| 单个场景频道 | 30–32 | 30–32 张 |")
w("")
w("> prd v3 写的是「全部未学新词（先不限配额）」—— 这个决定是在词库只有 **36 词**时做的。")
w("> 词库扩到 368 词后该前提失效：老人在推荐频道首日面对**368 张卡**，")
w("> 抖音式 feed 没有进度条、没有配额，意味着「今天要刷 368 张才算学完」。\n")

w("## 2. 若加配额：不同新词数下的每日负荷\n")
w("模拟假设：老人每次「认识」按 1→3→7→15 升级，「忘了」重置为 1（明天再见）；")
w("学习正确率 80%。模拟 180 天。\n")
w("| 每日新词配额 | 学完全部 368 词 | 前 7 天卡片 | 第 8–30 天日均 | **稳态日均**（后 30 天） | 峰值 |")
w("|---|---|---|---|---|---|")
for n in (5, 8, 10, 15, 20, 30, 368):
    load, cover = simulate(REC, n, 0.8)
    tail = sum(load[-30:]) / 30
    mid = sum(load[7:30]) / 23
    first7 = sum(load[:7])
    w("| %d | %s | %d | %.0f | **%.0f** | %d |" % (
        n, "%d 天" % cover if cover else ">180 天", first7, mid, tail, max(load)))
w("")
w("> 「稳态日均」= 全部词学完后，每天仍然要刷的卡片数。**它不为零** —— 见第 3 节。\n")

w("## 3. 关键发现：15 天是封顶，不是毕业\n")
w("`nextInterval(15)` = 15（`INTERVALS[(i+1).coerceAtMost(lastIndex)]`）。")
w("也就是词学完之后**每 15 天必然回到队列，永不停止**。\n")
w("稳态复习负荷 = 已学词数 ÷ 15：\n")
w("| 已学词数 | 每天固定复习量 |")
w("|---|---|")
for n in (36, 100, 200, 368):
    w("| %d | **%.1f 张/天** |" % (n, n / 15))
w("")
w("含义：\n")
w("- 36 词时代 = 每天 2.4 张复习，几乎无感；**368 词 = 每天固定 24.5 张**，这是永久的底噪。")
w("- 老人的「今日学完」永远在 ~25 张之后才到，没有真正的终点。")
w("- 若这是有意设计（长期记忆维持），需要在 prd 里写明；若是疏漏，应加**毕业机制**")
w("  （例如连对 4 次后毕业后间隔拉长到 30/60 天，或移出当日队列只留自由刷）。\n")

w("## 4. 正确率敏感性（稳态负荷的真正驱动因素）\n")
w("固定每日新词 10 张，改变「认识」成功率：\n")
w("| 「认识」成功率 | 学完全部词 | 稳态日均（后 30 天） | 峰值 |")
w("|---|---|---|---|")
for kr in (0.6, 0.7, 0.8, 0.9, 0.95):
    load, cover = simulate(REC, 10, kr)
    w("| %.0f%% | %s | **%.0f** | %d |" % (
        kr * 100, "%d 天" % cover if cover else ">180 天",
        sum(load[-30:]) / 30, max(load)))
w("")
w("> 正确率越低，复习债越重 —— 「忘了」把间隔打回 1 天，等于让这个词天天来。")
w("> 若真实老人的成功率在 60–70%，稳态负荷会显著高于 38 张/天。\n")

w("## 5. 场景频道与推荐频道重复计数\n")
w("`ensureQueue` 按频道各存一条 `DailyQueue`，`scopeIds(\"rec\")` = 全部 368 词，")
w("`scopeIds(scene)` = 该场景 30 词。\n")
w("→ **同一个到期词会同时出现在推荐队列和它所属的场景队列里**，两个频道各算一次。")
w("老人在推荐刷完「挂号处」，切到「医院」频道还会再见到它。")
w("（PRD 写的是「场景频道走同一调度器，体验一致」，但没说明这是有意重复还是应去重。）\n")

w("## 6. 结论与建议\n")
w("| 结论 | 依据 |")
w("|---|---|")
w("| **推荐频道必须加每日新词配额** | 当前实现首日 368 张；prd v3「不限配额」的前提在 36 词时代成立，368 词时失效 |")
w("| 建议配额 **10 词/天** | 学完全部约 37 天；前 7 天日均 24 张，第 8–30 天日均 49 张 |")
w("| 场景频道**不需要**配额 | 单场景 30–32 词，一天可完成 |")
w("| **稳态负荷 ~38 张/天是真正瓶颈** | 与配额无关，由 368 词 × 间隔阶梯封顶 15 天决定；正确率越低越重 |")
w("| 需明确 15 天封顶是否符合预期 | 决定是否有毕业机制、稳态负荷能否下降 |")
w("")
w("> 以上均为模拟推算，不是实测。真机验证请以 QYJ 或真实老人的实际完成量为准。\n")

open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(L))

for n in (5, 8, 10, 15, 20, 30, 368):
    load, cover = simulate(REC, n, 0.8)
    print("new/day=%3d cover=%-7s first7=%4d mid=%5.1f steady=%5.1f peak=%3d" % (
        n, cover, sum(load[:7]), sum(load[7:30]) / 23, sum(load[-30:]) / 30, max(load)))
