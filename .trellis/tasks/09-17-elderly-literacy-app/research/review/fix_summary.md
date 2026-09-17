# 本轮整改小结（2026-09-17）

> 起因：QYJ 让 GLM 5.3 补了词库（368 词），委托我做审查。审查产出见 `report.md`（词库）、`report_v3.md`（v3 代码）、`check_v3_graduation.md`（独立复核）。
> 本文件 = QYJ 拍板 4 项决策后的实际改动清单。

## 决策与执行

| # | QYJ 决策 | 执行 |
|---|---|---|
| 1 | 修编译错误 | ✅ `DoneCard.kt` 补回 `import androidx.compose.foundation.layout.Box` |
| 2 | 修拼音 | ✅ 只改了 **2 处**（见下） |
| 3 | 「驿」保留 | ✅ 未动 |
| 4 | 分区毕业 | ✅ 已实现，并修掉复核发现的 P1 缺陷（见下） |

## 一、编译错误（P0）

`ui/DoneCard.kt` 第 43 行用 `Box(...)`，本次改动把对应的导入一起删了 → `unresolved reference: Box`。
已补回。同批删除的其它导入（`Arrangement`/`size`/`AppSurface`/`GreenBg`/`GreenKnown`/`OrangeBg`、
`Common.kt` 的 8 个、`Theme.kt` 的 3 个符号）**逐条核过，确实都已无引用，只有 `Box` 漏网**。

## 二、拼音：真错只有 2 处，不是我上轮说的 11 处

我上轮说「11 处差异，建议全按 pypinyin 改」——**这个建议是错的**，逐条判定后推翻：

词库自带约定是 **无调号 = 轻声**（自身可证：`豆腐 → dòu fu`、`便宜 → pián yi`、`太贵了 → tài guì le`）。

| 词条 | 字 | GLM | pypinyin | 判定 |
|---|---|---|---|---|
| 豆腐 | 腐 | fu | fǔ | **GLM 对**（现汉 dòufu，轻声） |
| 司机师傅 | 傅 | fu | fù | **GLM 对**（shīfu，轻声） |
| 消息 | 息 | xi | xī | **GLM 对**（xiāoxi，轻声） |
| 发照片 | 片 | piàn | piān | **GLM 对**（zhàopiàn；pypinyin 误读） |
| 音量调大 / 字体调大 | 调 | tiáo | diào | **GLM 对**（「调」= 调整 读 tiáo；pypinyin 误读） |
| 流血了 | 血 | xuè | xiě | 两者皆可（文读 xuè / 口读 xiě），保留 GLM |
| 不要乱动 | 不 | bù | bú | 两者皆可（变调 bú，注音习惯写 bù），保留 GLM |
| 晚上 | 上 | shang | shàng | **GLM 对**（wǎnshang，轻声） |
| **扫码出库** | **码** | **má** | mǎ | **GLM 错** → 已改 `mǎ` |
| **早晨** | **晨** | **chen** | chén | **GLM 错**（漏调号）→ 已改 `chén` |

另扫全库 **19 处无声调拼音**，其中 **18 处都是正确的轻声**（了/子/头/匙/宜/息/傅/腐/上…），
只有「早晨·晨」漏了调号。

**结论：368 词 × 约 3 字 ≈ 1100 个注音里，真错 2 个（0.18%）。** 词库拼音质量比我上轮判断的高得多。
核查脚本可复跑：`pinyin_check.py` → `pinyin_check.md`。

## 三、分区毕业机制（新功能）

规则（QYJ 定）：**没有词级/每日毕业**，只做**分区级** —— 某分区**每个词都连续 3 次「认识」** → 该分区毕业。

**实现取巧点**：连续 3 次认识恰好把 `days` 推到阶梯封顶 15（1→3→7→15），忘了则打回 1，
所以 `days == 15` **天然等价**于「连续三次认识」，不需要新增计数器字段。
上限用 `GRADUATED_DAYS = INTERVALS.last()` 派生 —— 将来改阶梯，毕业阈值自动跟随。

| 文件 | 改动 |
|---|---|
| `StudyRepository.kt` | +`isSceneGraduated()` / `graduatedScenes()` / `GRADUATED_DAYS`；`scopeIds("rec")` 排除已毕业分区；+`freePoolScopeIds()` 供自由刷池（**不排除**毕业区） |
| `Common.kt` | `ChannelBar(current, graduated, onSelect)` 加 🎓 徽章、按需转绿 |
| `AppRoot.kt` | +`graduated` 状态，`LaunchedEffect(channel, session)` 刷新，传给 ChannelBar |

**效果**：①毕业区词不再进推荐队列（推荐随进度变轻）；②频道栏 🎓、仍可点进去；
③场景频道队列与自由刷池不受影响（否则全部毕业后推荐频道空转）；④**可逆** —— 在场景频道答「忘了」→ 退出毕业，次日回归推荐。

## 四、复核发现并修掉的 P1 缺陷（本轮最关键的一处）

独立复核（`check_v3_graduation.md`）发现：`replay()`（完成卡 →「从头再看一遍」）会清空 `revealed`，
同一批复习卡当天可被**反复作答**，而 `markKnown` 原本不看日期、每次都升级 →
**第 2 天在同一个词上点 3 次「认识」就能把 days 顶到 15，整个分区当天集体毕业**，
完全绕过 1/3/7/15 的间隔阶梯。那不是「连续三次认识」，是「一天点三下」。

**修法**（design.md §8.1 / prd.md v3 第 3 条已同步）：

```kotlin
fun markKnown(id: String): TermState {
    val cur = termStates[id]
    val days = if (cur != null && cur.lastSeen == today()) cur.days   // 今天已升过级 → 不再升
               else nextInterval(cur?.days ?: 1)
    ...
    return st   // 返回实际状态，UI 播报真实天数（不再自行预算）
}
```

**验证**（`sim_graduation.py`，照抄修复后的逻辑）：

| 每词每天作答次数 | 1 | 3 | 10 | 100 |
|---|---|---|---|---|
| 单字达到封顶的日期 | 第 12 天 | 第 12 天 | 第 12 天 | 第 12 天 |
| 30 词分区毕业日 | 第 12 天 | 第 12 天 | 第 12 天 | 第 12 天 |

→ **刷不动了**。单字阶梯：day1 学过 → day2 认识(3) → day5 认识(7) → day12 认识(15) → 下次到期 day27。
**分区最短毕业周期 = 12 天**（且要求该区 30–32 个词全部走完各自阶梯）。

## 五、文档同步

- `design.md`：编号整理为连续的 1–9（v3 节 = §8，8.1–8.6；原重复的 `## 9` → `## 9. 取舍与风险`）；
  新增 §8.6 分区毕业；§8.1 补「每日最多升一级」；§8.2 补推荐范围/自由刷池差异；§8.4 补 `modes` 字段。
- `prd.md`：v3 新增第 7 条「分区毕业」+ 第 6 条补「跨频道共享学习状态」+ 第 3 条补「每日最多升一级」；验收标准新增毕业一条。
- `implement.md`：步骤 1–10 标完成，新增步骤 7/8/9/10，加「已知未决」小节。
- `AppRoot.kt` 注释里的 §8 / §8.3 / §8.6 引用同步修正。

## 六、本轮未做（需 QYJ 定）

| 项 | 说明 |
|---|---|
| **推荐频道首日 368 张卡** | prd v3「先不限配额」是 36 词时代的决定。模拟（`quota_sim.md`）建议 8–10 词/天配额。**未改，等决策** |
| **稳态 ~38 张/天** | 词级不毕业决定下的必然底噪，与配额无关。已写进 design §8.6 与 prd 第 7 条说明为**有意设计** |
| 忘了词当天重现卡**不可再答** | 复核 P1-2：`revealed` 以 termId 为键，答过即 true，重现卡恒展开 →「当天再考一次」退化成复读。建议改按 `channel:index` 为键。**未改，会动 UI 语义，等决策** |
| 完成后场景频道可能只剩 0–2 张 | 复核 P2：建议空队列回落自由刷温故页 |
| 完成卡战果只算本次会话 | 复核 + 我上轮都标了。未改 |
| `addForgot`/`forgotIds`/`forgotCount`/`forgot` 死代码 | 未删 |
| 网页原型词库仍是 36 词 | 漂移未修，待转单一数据源 |
| `.trellis/spec/` 仍是模板样板 | 与 Kotlin/Compose 项目不匹配（`frontend/` 是 JS 向的 `hook-guidelines`/`state-management`），建议单独安排一次 spec bootstrap |

## 七、验证状态

- 本机**无 JDK / Android SDK，无法编译**。已做：逐符号核对导入/签名/调用（`verify_static.py`，31 项通过）、
  全仓残留符号 0 命中、花括号配平、`repo.X` 调用与定义对齐、阶梯数值自检、毕业模拟。
- **仍需 QYJ 在 Android Studio 构建 + 实机走查**（implement.md 步骤 11 = 强制评审门）。未 commit 即因该门。
