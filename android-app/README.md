# 慢慢懂 Mando（安卓版）

> Learn life, one word at a time. —— 学生活，从认一个字开始。

面向识字较少老年人的安卓识字辅助应用。**抖音式垂直滑动学习**：生活词组为学习单元、顶部场景频道切换、新学与复习按记忆曲线混排、全程语音引导。

技术栈：Kotlin + Jetpack Compose（`VerticalPager` 实现 feed），无第三方依赖（仅 AndroidX + Compose），minSdk 26。

## 构建

### 方式一：Android Studio（推荐）

1. 安装 [Android Studio](https://developer.android.com/studio)（Koala 及以上，自带 JDK 17 与 Android SDK）。
2. `File → Open` 选择本目录（`android-app/`）。
3. 首次打开若提示 "Gradle wrapper not found"，点提示条中的修复链接（自动生成 wrapper 并下载 Gradle 8.7），或菜单 `File → Sync Project with Gradle Files`。
4. 等待 Sync 完成 → 连接手机（开启 USB 调试）→ 点 ▶️ Run。

### 方式二：命令行（需本机已有 JDK 17 + Android SDK）

```bash
# 首次需要生成 wrapper（本机装过 gradle 的话）
cd android-app && gradle wrapper --gradle-version 8.7

# Debug APK
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 目录结构

```
app/src/main/java/com/qyj/shibang/
├── MainActivity.kt            # 入口：装配 TTS 与仓库
├── data/
│   ├── StudyData.kt           # 数据模型 + 场景频道定义（推荐聚合 + 12 个场景）
│   ├── WordBank.kt            # 词库：12 个场景共 368 条生活词组（含逐字拼音）
│   └── StudyRepository.kt     # 记忆状态（TermState + lapses）/ 每日队列持久化 + 双层调度（连击 3 次 + 间隔阶梯 1→3→7→15→30；毕业判定固定 15，与封顶解耦；due ≥ 20 进清债模式）
├── tts/TTSSpeaker.kt          # Android TTS 封装（中文、语速 0.85、就绪前排队）
└── ui/
    ├── AppRoot.kt             # 根界面：VerticalPager feed + 频道切换 + 朗读编排
    ├── TermCard.kt            # 新学卡 / 复习卡 / 温故卡（认识/忘了反馈）
    ├── DoneCard.kt            # 完成卡（v5：整个队列真处理完才出现；战果为当日持久计数）
    ├── Sheets.kt              # 字卡弹层（ModalBottomSheet）
    ├── Common.kt              # 顶栏、频道 Tab（含 🎓 毕业徽章）等共享组件
    └── theme/Theme.kt         # 适老化设计 token（高对比配色）
```

## 交互契约（与已评审网页原型一致）

1. **上下滑学习**：每屏一张学习卡，snap 吸附；卡片进入视口自动朗读（每卡一次）。
2. **生活词组**：如"地铁站"整体学习，配逐字拼音与生活用途说明；点单字开字卡弹层（组词联想）。
3. **频道切换**：顶部 Tab（推荐/买菜/公交地铁/医院/银行/办事/吃饭/手机微信/药品说明/快递驿站/物业水电/紧急求助/天气日历），点按替换 feed 并语音播报。
4. **复习混排**：推荐频道中复习卡与新学卡交错；复习卡先考回忆 → 「√ 认识 / × 忘了」→ 展开答案与间隔反馈；忘了的词隔天再考，并在当天队列尾部再出现一次。
5. **防泄题与求助通道（v5）**：复习/温故卡未作答时，点卡片只播「再想一想，想起来了吗？」，不念词、不念提示；新增「👀 想看答案」按钮，展开拼音与提示但**不写任何学习状态**（连击、间隔、队列均不动），作答按钮保留，看完仍可作答。
6. **完成度与前向拦截（v5）**：完成卡**只在整个队列真的处理完时才出现**（新词都教读过、复习词都满 3 连击移出），没有「滑到底即完成」这回事；战果为当日持久计数，重启不归零。**不允许越过未处理的卡**——向前滑过未作答/未教读的卡会被退回该卡并听到一句解释（「先回答这张卡，再往上滑」），列表顺序与任何学习状态都不受影响；**下滑回看、以及完成卡之后的自由刷不受限制**。本版没有「跳过」这个动作，出口是 √ / ×（可先「👀 想看答案」再作答）。
7. **无设置页、无生词本**：语速固定 0.85x（适老偏慢），字号跟随系统缩放，不提供字体/语速/提醒入口；拍照识字为后续扩展占位。

## 适老化硬指标

词组主字 ≥48sp 等效、拼音与说明 ≥22sp；主色与白底对比度 ≥7:1（WCAG AAA）；反馈按钮 92dp 高；字号跟随系统缩放（app 不叠加自有倍率）；全程无多级菜单、无手势依赖；语音+图标双通道引导。

## 已知边界（下一迭代）

- 记忆状态（TermState）与当日队列已持久化（SharedPreferences + JSON），完整调度入库（Room + WorkManager）待做。
- 提醒功能未实现（本轮取消设置页），AlarmManager / Notification 待做。
- 拍照识字（CameraX + ML Kit）按规划为后续版本。
