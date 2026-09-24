package com.knowmo.app.data

/**
 * 词库 v23 起零内置词条：官方词库以 CSV 随仓库分发（仓库 wordbanks 目录，内容单一真源，
 * 校验脚本 `check_wordbank_csv.py`），经设置页导入成为自定义分区（导入与校验见 `CustomBank.kt`）。
 * 「拼音按空格逐字配对，字数不符即拒」的契约由 `CustomBank` 导入校验执行。
 *
 * 历史沿革（v9~v22 内置词库的历次增删与分区退役）见 git 历史。v22 面馆分区退役时的
 * 私有导出通道已随 v23.1 关闭（导入只收 CSV）——⚠️ **面馆词并未并入官方词库**（私有内容
 * 不进开源仓库）：以私有 CSV（`.workbuddy/private/面馆.csv`，git 忽略）继续分发给家属设备，
 * 经设置页导入恢复；官方 5 库与面馆词零交集（2026-09-24 实测核对）。两套 id 体系不互通，
 * 进度重计（v23 拍板接受）。
 */

/** 内置词库（v23 起恒为空；自定义词库由 [STUDY_TERMS] 动态并入） */
val BUILTIN_TERMS: List<Term> = emptyList()

/** 全库词条 = 内置（恒空）+ 自定义词库（动态并入）。消费方无需感知「内置 / 自定义」区别。 */
val STUDY_TERMS: List<Term> get() = BUILTIN_TERMS + CustomBanks.terms
