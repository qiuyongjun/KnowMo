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
 *
 * v5 R7：本卡不再等价于「队列已清空」——滑动全程开放，用户可能一路滑底而多张复习卡一张未答
 * （原注释「任务池为空或全部移除后才到达本卡」的前提在当前实现下不成立，design.md §5.1）。
 * 故按队列完成度分流：
 * - `pending == 0`（复习卡全部作答）：维持 v4 文案，逐字不回归；
 * - `pending > 0`：只报未作答张数与已答张数，引导**往下滑**（未作答卡在本卡之前/上方，回看是下滑方向）
 *   回去作答；该分支不出现 🎉 与「今日任务完成」，也不显示 `SwipeHint`（其固定文案「上滑看下一个 ↑」
 *   方向相反，会误导）。
 */
@Composable
fun DoneCard(known: Int, forgot: Int, pending: Int, answered: Int) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        if (pending > 0) {
            // 仍有未作答复习卡：不宣告完成（v5 R7）
            Text("还有 $pending 张没作答", fontSize = 34.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "今天答了 $answered 张",
                fontSize = 22.sp, color = AppText2, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            // 方向词必须是「往下滑」：回看前面的卡 = 手指下滑（上滑是 index+1）
            Text("往下滑，回去把它们答完", fontSize = 24.sp, fontWeight = FontWeight.Black, color = BluePrimary)
        } else {
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
        }
        Spacer(Modifier.weight(1f))
    }
}
