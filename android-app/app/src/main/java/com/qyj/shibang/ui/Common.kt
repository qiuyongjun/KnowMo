package com.qyj.shibang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qyj.shibang.data.Term
import com.qyj.shibang.ui.theme.AppLine
import com.qyj.shibang.ui.theme.AppSurface
import com.qyj.shibang.ui.theme.AppText
import com.qyj.shibang.ui.theme.AppText2
import com.qyj.shibang.ui.theme.BadgeDot
import com.qyj.shibang.ui.theme.BlueBg
import com.qyj.shibang.ui.theme.BlueDark
import com.qyj.shibang.ui.theme.BluePrimary
import com.qyj.shibang.ui.theme.OrangeBg
import com.qyj.shibang.ui.theme.OrangeDark
import com.qyj.shibang.ui.theme.ProgressTrack

/* ---------- 顶栏 ---------- */

@Composable
fun TopBar(forgotCount: Int, onWordbook: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("📖 识字好帮手", fontSize = 24.sp, fontWeight = FontWeight.Black, color = BlueDark)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TopIconButton("📕 生词本", badge = forgotCount, onClick = onWordbook)
            TopIconButton("⚙️ 设置", onClick = onSettings)
        }
    }
}

@Composable
private fun TopIconButton(label: String, badge: Int = 0, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .height(56.dp)
            .clip(shape)
            .background(AppSurface)
            .border(3.dp, AppLine, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppText)
        if (badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-10).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(BadgeDot),
                contentAlignment = Alignment.Center,
            ) {
                Text(badge.toString(), fontSize = 13.sp, fontWeight = FontWeight.Black, color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}

/* ---------- 频道 Tab（参考抖音顶部 tab） ---------- */

@Composable
fun ChannelBar(current: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        com.qyj.shibang.data.SCENES.forEach { s ->
            val active = s.id == current
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier
                    .clip(shape)
                    .background(if (active) BlueBg else AppSurface)
                    .clickable { onSelect(s.id) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Text(
                    "${s.icon} ${s.name}",
                    fontSize = 21.sp,
                    fontWeight = if (active) FontWeight.Black else FontWeight.Bold,
                    color = if (active) BlueDark else AppText2,
                )
            }
        }
    }
}

/* ---------- 共享小组件 ---------- */

@Composable
fun ProgressRow(index: Int, total: Int) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("第 $index / $total 张", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = AppText2)
        Spacer(Modifier.width(14.dp))
        Box(
            Modifier
                .weight(1f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ProgressTrack),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(index.toFloat() / total.toFloat())
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(BluePrimary),
            )
        }
    }
}

@Composable
fun TermBadge(term: Term) {
    val isReview = term.kind == Term.Kind.REVIEW
    androidx.compose.material3.Surface(
        color = if (isReview) OrangeBg else BlueBg,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            if (isReview) "🔁 复习 · 距上次 ${term.days} 天" else "✨ 新学",
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = if (isReview) OrangeDark else BlueDark,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun SwipeHint() {
    Text(
        "上滑看下一个 ↑",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = AppText2,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        textAlign = TextAlign.Center,
    )
}
