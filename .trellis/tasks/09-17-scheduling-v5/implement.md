# implement.md — 执行计划

## 执行顺序（含验证命令）

1. **StudyRepository.kt 调度层**
   - INTERVALS 增 30 档；GRADUATED_DAYS 改 `const val = 15`
   - TermState 增 `lapses: Int = 0`；markForgot `lapses+1`；markKnown 升级分支 `lapses/2`
   - isSceneGraduated：`days == GRADUATED_DAYS` → `days >= GRADUATED_DAYS`
   - buildQueue：due 排序 compareByDescending(lapses).thenBy(dueTime)；rec 频道清债模式（due.size >= 20 → due.take(15)）
   - persist 写 `"lapses"`；load `optInt("lapses", 0)`
   - 同步类/KDoc 注释（1→3→7→15→30 封顶、毕业判定 15）
2. **TermCard.kt + AppRoot.kt 防泄题 + peek**
   - TermCard 增 `onPeekHint: () -> Unit`（考试态未作答点卡回调）与 `peeked: Boolean` 参数
   - 主区域 clickable 按形态分流；底部提示文案条件化；考试态未作答区加「👀 想看答案」小按钮
   - AppRoot：`peeked` map（按 seq）；peek 回调只置 map 不写 repo；TermCard 调用点接线
3. **prototype/index.html 契约同步**
   - 考试卡点按不播读音（播「再想一想」）；加「想看答案」按钮（展开不写状态——原型本就无持久层，只改交互表现）
4. **验证**
   - `cd android-app && ./gradlew assembleDebug`（Windows: `gradlew.bat assembleDebug`）
   - 手动场景走查（下）

## 手动场景走查清单

| # | 场景 | 预期 |
|---|---|---|
| 1 | 旧 JSON（无 lapses）加载 | 不崩，due 正常，lapses 视为 0 |
| 2 | 毕业词（days=15）满 3 连击 | 播报「30 天后再来复习」，分区 🎓 不消失 |
| 3 | 「忘了」次日重建队列 | 该词排 due 池前部（lapse=1 优先） |
| 4 | due ≥ 20 重建推荐队列 | 15 张全复习、无新词 |
| 5 | 复习卡未作答点卡片 | TTS 只说「再想一想，想起来了吗？」 |
| 6 | peek 后作答 | 走正常连击（peek 未写任何状态） |
| 7 | 新学卡/已作答卡点按 | 重听行为不变 |

## 回滚点

- 每步一个独立可编译状态；任意步出问题 revert 该步即可，无跨文件半成品依赖（StudyRepository 改动向后兼容 UI 旧调用；TermCard/AppRoot 改动向后兼容 repo 新字段——lapses 有默认值）。
