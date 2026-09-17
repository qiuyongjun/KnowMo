package com.qyj.shibang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.qyj.shibang.ui.theme.AppSurface
import com.qyj.shibang.ui.theme.AppText2
import com.qyj.shibang.ui.theme.BluePrimary
import com.qyj.shibang.ui.theme.GreenBg
import com.qyj.shibang.ui.theme.GreenKnown
import com.qyj.shibang.ui.theme.OrangeBg

/** 队列末尾的完成卡：统计 + 重看一遍 */
@Composable
fun DoneCard(index: Int, total: Int, known: Int, forgot: Int, onReplay: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProgressRow(index, total)
        Spacer(Modifier.weight(1f))
        Text("🎉", fontSize = 96.sp)
        Spacer(Modifier.height(10.dp))
        Text("今天学完啦！", fontSize = 34.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "认识了 $known 个，忘了 $forgot 个（明天再来）",
            fontSize = 22.sp, color = AppText2, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(BluePrimary)
                .clickable(onClick = onReplay),
            contentAlignment = Alignment.Center,
        ) {
            Text("🔄 从头再看一遍", fontSize = 24.sp, fontWeight = FontWeight.Black, color = androidx.compose.ui.graphics.Color.White)
        }
        Spacer(Modifier.height(14.dp))
        Text("明天记得再来学一会儿", fontSize = 19.sp, color = AppText2)
        Spacer(Modifier.weight(1f))
    }
}
