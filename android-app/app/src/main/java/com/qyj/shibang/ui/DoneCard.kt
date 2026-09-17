package com.qyj.shibang.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qyj.shibang.ui.theme.AppText2
import com.qyj.shibang.ui.theme.BluePrimary

/**
 * 队列末尾的完成卡（v4）：战果 + "今日任务完成"播报；无"从头再看一遍"按钮（replay 已删），
 * 引导继续上滑进入自由刷（温故，可作答、连击/降级照算且受每日升一级闸门约束，无限流随时可停）。
 * 任务池为空或全部移除后才到达本卡（design.md §9.3）。
 */
@Composable
fun DoneCard(known: Int, forgot: Int) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text("🎉", fontSize = 96.sp)
        Spacer(Modifier.height(10.dp))
        Text("今日任务完成！", fontSize = 34.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "认识了 $known 个，忘了 $forgot 个",
            fontSize = 22.sp, color = AppText2, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        // 完成卡不是尽头：继续上滑进入自由刷（池空自动重洗，随时可停）
        Text("继续上滑，随便看看", fontSize = 24.sp, fontWeight = FontWeight.Black, color = BluePrimary)
        Spacer(Modifier.height(6.dp))
        SwipeHint()
        Spacer(Modifier.weight(1f))
    }
}
