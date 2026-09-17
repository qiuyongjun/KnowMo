# 技术设计 — 老年人识字辅助应用（原型阶段 v2 · 抖音式 feed）

> v2 变更：交互范式从"底部 Tab + 分页导航"改为"抖音式垂直 feed"；学习单元从单字改为生活词组；移除手写/拍照；复习内嵌 feed。v1 的多屏导航方案废弃。

## 1. 原型形态

单文件 HTML/CSS/JS 交互原型（`prototype/index.html`），手机框（412×915）居中，浏览器直接运行，无外部依赖。

**语音**：Web Speech API（`speechSynthesis`，zh-CN，默认语速 0.85）。工程阶段替换为 Android `TextToSpeech`，交互契约不变。

## 2. 设计系统（Design Tokens）

| Token | 值 | 适老化依据 |
|---|---|---|
| `--font-term` | 56–64px（更大字体档 72px+） | 词条主字 |
| `--font-pinyin` | 26px | 逐字拼音 |
| `--font-body` | 22px | 用途说明等正文 |
| `--font-button` | 24px 700 | 反馈按钮 |
| `--color-primary` | #1565C0 | 深蓝，白字对比 8.6:1 |
| `--color-accent` | #E65100 | 深橙，"忘了"以外的主动作 |
| `--color-known` | #1B5E20 / `--color-forgot` #B71C1C | 复习反馈双按钮（绿/深红） |
| `--color-bg` | #FFFFFF / `--surface` #F5F7FA | 浅底深字 |
| 触控目标 | ≥64px；反馈按钮 ≥88px | 防误触 |
| 翻页 | scroll-snap y mandatory | 每滑一次正好一张，无半张卡 |

## 3. 信息架构（1 屏 + 2 弹层）

```
┌─────────────────────────────┐
│ 频道Tab（横滑）：推荐 买菜 公交 医院 银行 政务 餐厅 │ ← 常驻顶部，点按切换+播报
├─────────────────────────────┤
│                             │
│   Feed（垂直滑动，snap 吸附）  │ ← 每屏一张学习卡
│   · 新学卡：图标+词组+逐字拼音  │    进入视口自动朗读
│     +用途说明，点任意处重听     │
│   · 复习卡：🔁角标（距上次N天） │    先出词组不出释义
│     → 认识/忘了 大按钮         │    → 展开完整内容
│   · 字卡弹层：点单字看详情      │    拼音/组词/例句
│   · 完成卡：🎉 表扬+重看一遍    │
│                             │
│                  ⚙️ 设置(右上) │ ← 字体/语速/提醒/记忆曲线说明
└─────────────────────────────┘
```

## 4. Feed 队列与记忆曲线（演示逻辑）

- **推荐频道**队列 = 到期复习项 + 新学项交替穿插（演示：复习3 + 新学3，F-N-F-N-F-N 交错）+ 结束卡。
- **场景频道**队列 = 该场景词条（含 1–2 个标"复习"），同样以结束卡收尾。
- 词条携带 `ReviewState{box, lastSeen}`：认识 → box+1（间隔 1→3→7→15 天）；忘了 → box 归 1（明天再来）。原型中反馈后角标即时更新为"3 天后再见"/"明天再来"，让评审直观看到调度效果。
- "忘了"的词条自动进入**生词本**；feed 顶部"我的生词本"入口（红色数字角标）打开简单列表，点读。

## 5. 数据模型（演示数据，对应工程实体）

```js
TermChar { c, p }                       // 单字 + 拼音
Term     { id, text, chars: TermChar[], tip, sceneId, icon, kind: 'new'|'review', interval } // 学习单元=词组
Scene    { id, name, icon, color }
ReviewState { termId, box, dueInDays }  // 工程阶段入库；原型内存演示
```

演示数据：6 场景 × 6 词条（组合如"地铁站""今日特价""挂号处""输入密码""请签字""加一双筷子"），每条 2–5 字 + 逐字拼音 + 一句生活用途。

## 6. 关键交互契约

1. **自动朗读**：卡片进入视口（IntersectionObserver ≥60%）→ 朗读词条 + 拼音首字提示；同一张卡只自动播一次，重复进入不重复播。
2. **点按重听**：卡片任意空白处点按 → 重听。单字区点按 → 字卡弹层（弹层内自动发音）。
3. **复习卡流程**：初始态出词组+两按钮 → 点"认识/忘了" → 展开完整内容（拼音/用途/朗读）+ 间隔反馈文案 → 上滑继续；展开后按钮消失防误触。
4. **频道切换**：点频道 chip → feed 整体替换、回到该频道第一张 + 播报频道名。
5. **完成卡**：语音表扬 + "从头再看一遍"大按钮（重置队列）。

## 7. 工程映射（v2 已实施 → `android-app/`）

**已实现（2026-09-17，Kotlin + Compose，无第三方依赖）：**

- Feed：`VerticalPager`（foundation pager，天然 snap 吸附）；页变化 `LaunchedEffect(pagerState.currentPage, pages)` 触发朗读，`spokenKeys` 去重（频道+词条 id）。
- TTS：`tts/TTSSpeaker.kt` 封装 `TextToSpeech`（zh-CN，语速 0.85，异步初始化 pending 补播欢迎语）。
- 数据：`data/StudyData.kt` 36 词条（7 频道，词组+逐字拼音+用途），`REC_QUEUE` 交错混排；`data/StudyRepository.kt` 生词本 + 设置持久化（SharedPreferences/JSON）+ `nextInterval`（1→3→7→15 天）。
- UI：`ui/` — AppRoot（编排）、TermCard（新学/复习双态）、DoneCard、Sheets（字卡/设置/生词本 ModalBottomSheet）、Common（顶栏/频道 chips/进度）、theme（设计 token）。
- 字体放大档：`CompositionLocalProvider(LocalDensity provides Density(density, fontScale * 1.15))` 全局乘系统缩放。
- 构建：AGP 8.5.2 / Kotlin 2.0.20 / Compose BOM 2024.09.02，minSdk 26 / target 34。本机（Windows）无 JDK/SDK，构建走 Android Studio；见 `android-app/README.md`。

**待下一迭代：** Room + WorkManager 调度入库、AlarmManager 提醒、拍照识字（CameraX + ML Kit）。

## 8. 取舍与风险

- **滑动 vs 点击翻页**：选择滑动（已被短视频市场教育的习惯，单指大面积操作）；snap + 大卡片降低误操作代价。风险：部分高龄用户首次需引导——完成卡/空态有语音引导文案。
- **词组学习 vs 单字学习**：词组贴近真实识别场景（看牌子认整体），但泛化能力弱于单字。字卡弹层（点单字看组词例句）作为补充路径保留。
- **语音依赖**：浏览器无 zh voice 时降级为视觉反馈；Android TTS 内置中文，工程阶段风险消除。
