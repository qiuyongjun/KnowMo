package com.knowmo.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
// 页面底色用暖米色而非冷灰：白卡浮在暖底上更柔和，对老年用户更亲和（卡片靠投影分层，不用描边）
val AppSurface = Color(0xFFF5F1E9)
val AppLine = Color(0xFFE6E0D4)
val AppText = Color(0xFF1A1A1A)
val AppText2 = Color(0xFF424244)
val StarGold = Color(0xFFF9A825)   // v6 收藏星按钮（藏 = 金色实心 ★；design.md §11.2 指定 #F9A825 系）

/* ---------- v21 卡片外形 token（统一三卡的圆角/投影语言） ---------- */

/** 卡片圆角：feed 主卡 = 28dp；设置分组卡 = 24dp（次级一档） */
val CardShapeLarge: Shape = RoundedCornerShape(28.dp)
val CardShapeMedium: Shape = RoundedCornerShape(24.dp)

/**
 * 卡片投影：单层大 blur 阴影在浅色底上是"脏色块"，这里拆成**环境层（大而淡）+ 接触层（小而深）**
 * 两层叠加，接近真实纸张浮起的光感。spotColor 用暖深灰而非纯黑——页面底是暖米色，
 * 纯黑阴影叠上去发蓝发脏，暖色投影与底色同温、更通透。
 */
private val ShadowSpot = Color(0xFF4A4136)

fun Modifier.cardShadow(shape: Shape, elevated: Boolean = false): Modifier {
    val ambient = if (elevated) 8.dp else 14.dp
    val contact: Dp = if (elevated) 3.dp else 5.dp
    return this
        .shadow(ambient, shape, spotColor = ShadowSpot, ambientColor = ShadowSpot)
        .shadow(contact, shape, spotColor = ShadowSpot, ambientColor = ShadowSpot)
}

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
