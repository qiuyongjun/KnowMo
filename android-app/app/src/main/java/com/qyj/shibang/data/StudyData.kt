package com.qyj.shibang.data

import androidx.compose.ui.graphics.Color

/** 单字 + 拼音 */
data class TermChar(val c: String, val p: String)

/**
 * 学习单元 = 生活词组（不是单字）。
 * kind/days 不在此存储：新学/复习/温故由 StudyRepository 调度器按
 * TermState（认识计数 + 上次学习日期，计数满 3 = 已移除）在运行时计算。
 * 词条内容见 WordBank.kt。
 */
data class Term(
    val id: String,
    val scene: String,
    val icon: String,
    val text: String,
    val chars: List<TermChar>,
    val tip: String,
)

data class Scene(val id: String, val name: String, val icon: String, val color: Color)

val SCENES = listOf(
    Scene("rec", "推荐", "⭐", Color(0xFFEDE7F6)),
    Scene("market", "买菜", "🛒", Color(0xFFE8F5E9)),
    Scene("transit", "公交地铁", "🚌", Color(0xFFE3F2FD)),
    Scene("hospital", "医院", "🏥", Color(0xFFFFEBEE)),
    Scene("bank", "银行", "🏦", Color(0xFFFFF8E1)),
    Scene("gov", "办事", "🏛️", Color(0xFFF3E5F5)),
    Scene("food", "吃饭", "🍜", Color(0xFFFFF3E0)),
    Scene("phone", "手机微信", "📱", Color(0xFFE8EAF6)),
    Scene("medicine", "药品说明", "💊", Color(0xFFE0F2F1)),
    Scene("express", "快递驿站", "📦", Color(0xFFEFEBE9)),
    Scene("property", "物业水电", "🏠", Color(0xFFECEFF1)),
    Scene("emergency", "紧急求助", "📞", Color(0xFFFBE9E7)),
    Scene("weather", "天气日历", "📅", Color(0xFFE1F5FE)),
)

fun sceneColor(id: String): Color = SCENES.firstOrNull { it.id == id }?.color ?: Color(0xFFF5F7FA)
fun sceneName(id: String): String = SCENES.firstOrNull { it.id == id }?.name ?: ""
