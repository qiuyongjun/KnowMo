# 识字好帮手（安卓版）

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
│   ├── StudyData.kt           # 演示数据：7 场景频道 × 6 生活词组（含逐字拼音）
│   └── StudyRepository.kt     # 生词本/设置持久化 + 简化 Leitner 间隔（1→3→7→15 天）
├── tts/TTSSpeaker.kt          # Android TTS 封装（中文、语速 0.85、就绪前排队）
└── ui/
    ├── AppRoot.kt             # 根界面：VerticalPager feed + 频道切换 + 朗读编排
    ├── TermCard.kt            # 新学卡 / 复习卡（认识/忘了反馈）
    ├── DoneCard.kt            # 完成卡（统计 + 重看一遍）
    ├── Sheets.kt              # 字卡弹层 / 设置 / 生词本（ModalBottomSheet）
    ├── Common.kt              # 顶栏、频道 Tab、进度条等共享组件
    └── theme/Theme.kt         # 适老化设计 token（高对比配色）
```

## 交互契约（与已评审网页原型一致）

1. **上下滑学习**：每屏一张学习卡，snap 吸附；卡片进入视口自动朗读（每卡一次）。
2. **生活词组**：如"地铁站"整体学习，配逐字拼音与生活用途说明；点单字开字卡弹层（组词联想）。
3. **频道切换**：顶部 Tab（推荐/买菜/公交地铁/医院/银行/办事/吃饭），点按替换 feed 并语音播报。
4. **复习混排**：推荐频道中复习卡与新学卡交错；复习卡先考回忆 → 「😀 认识 / 😕 忘了」→ 展开答案与间隔反馈；忘了的词自动进生词本。
5. **设置**：字体大小（正常/更大，全局即时生效）、朗读速度、每日提醒、记忆曲线说明；拍照识字为后续扩展占位。

## 适老化硬指标

词组主字 42–64sp、拼音 26sp、正文 ≥18sp；主色与白底对比度 ≥7:1（WCAG AAA）；反馈按钮 92dp 高；全程无多级菜单、无手势依赖；语音+图标双通道引导。

## 已知边界（下一迭代）

- 复习状态仅内存 + 生词本持久化，完整调度入库（Room + WorkManager）待做。
- 提醒时间为设置展示，尚未接 AlarmManager/Notification。
- 拍照识字（CameraX + ML Kit）按规划为后续版本。
