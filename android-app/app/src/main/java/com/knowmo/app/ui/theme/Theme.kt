package com.knowmo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 适老化设计 token（与原型 design.md 一致：浅底深字、高对比 ≥7:1）
val BluePrimary = Color(0xFF1565C0)
val BlueDark = Color(0xFF0D47A1)
val BlueBg = Color(0xFFE3F2FD)
val OrangeAccent = Color(0xFFE65100)
val OrangeDark = Color(0xFFBF360C)
val OrangeBg = Color(0xFFFFF3E0)
val GreenKnown = Color(0xFF1B5E20)
val GreenBg = Color(0xFFE8F5E9)
val RedForgot = Color(0xFFB71C1C)
val AppSurface = Color(0xFFF5F7FA)
val AppLine = Color(0xFFDDE2EA)
val AppText = Color(0xFF1A1A1A)
val AppText2 = Color(0xFF424244)
val StarGold = Color(0xFFF9A825)   // v6 收藏星按钮（藏 = 金色实心 ★；design.md §11.2 指定 #F9A825 系）

@Composable
fun KnowMoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BluePrimary,
            onPrimary = Color.White,
            secondary = OrangeAccent,
            background = Color.White,
            surface = Color.White,
            onBackground = AppText,
            onSurface = AppText,
        ),
    ) {
        content()
    }
}
