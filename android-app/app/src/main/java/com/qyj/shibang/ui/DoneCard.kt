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
 * 完成卡（v5 R7 重定义）：**不是「滑到底」，是「真做完」**。
 *
 * 本卡只在**整个当日队列真的处理完**（新词都教读过、复习卡都已作答——v5 R11 首答定调度，答完即当日移除
 * → 待处理数 == 0）时，
 * 由 AppRoot 的 `syncDonePage()` 条件插入 feed 尾部；未做完时它根本不在 `pages` 里，
 * 队尾就是最后一张待处理卡（design.md §5.1）。所以本卡的语义是**事件**，不是位置——
 * 上一版「与队尾绑定 + 按未作答张数分流文案」的做法已整体删除（条件出现后「还有 N 张没作答」不可达）。
 *
 * 内容为单一形态：🎉 / 今日任务完成！/ 「今天学完了 N 个词 · 认识了 x 次，忘了 y 次」/ 继续上滑，随便看看 / SwipeHint。
 * 战果数字是**当日持久口径**（§5.3）：`DayState` 的当日「认识/忘了」次数，重启不清零，隔天整体作废。
 * 本卡不是尽头：继续上滑进入**温故流**（v5 R10：池型 + 纯浏览卡——词 + 逐字拼音 + 用途全展开、
 * **无 √/×**、不作答、不写任何学习状态（`TermState` / `DayState` 零写入）；无限流随时可停）。
 *
 * @param known 当日「认识」作答次数（持久口径）
 * @param forgot 当日「忘了」作答次数（持久口径）
 * @param words 当日任务区去重词数（N = 今天学完了 N 个词）
 */
@Composable
fun DoneCard(known: Int, forgot: Int, words: Int) {
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
            "今天学完了 $words 个词 · 认识了 $known 次，忘了 $forgot 次",
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
