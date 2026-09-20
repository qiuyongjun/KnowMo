package com.knowmo.app.ui

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
import com.knowmo.app.ui.theme.AppText2
import com.knowmo.app.ui.theme.BluePrimary

/**
 * 完成卡（v9 重定义，prd v9 第 1 条）：**不是「滑到底」，是「真做完」**——整个当日队列
 * （v8：每词 3 次「认识」连击满移出、全部卡作答 → 待处理数 == 0）处理完时，
 * 由 AppRoot 的 `syncDonePage()` 条件插入 feed 尾部；未做完时它根本不在 `pages` 里，
 * 队尾就是最后一张待处理卡。任务阶段锁滑，完成卡出现后唯一的前进方式是**上滑**。
 *
 * v9：**去掉 v7 的「确认」按钮**——用户**到达完成卡即自动转入浏览模式**（AppRoot.enterBrowse：
 * `repo.markConfirmed()` 持久化 + 移除全部任务卡），完成卡保留在 feed 首位：
 * 上滑进入推荐浏览、回滑仍能看到本卡战果，但任务卡已被移除、**回不去**（prd v9 第 1 条）。
 *
 * 内容：🎉 / 今日任务完成！/ 「今天学完了 N 个词 · 认识了 x 次，忘了 y 次」/ 上滑引导。
 * 战果数字是**当日持久口径**（§5.3）：`DayState` 的当日「认识/忘了」次数，重启不清零，隔天整体作废。
 *
 * @param known 当日「认识」作答次数（持久口径）
 * @param forgot 当日「忘了」作答次数（持久口径）
 * @param words 当日任务区去重词数（N = 今天学完了 N 个词）
 * @param inBrowse 是否已处于浏览模式（true = 当日重启 / 已转浏览后的完成卡，引导文案改「随便看看吧」，
 *   不再重复「上滑进入推荐模式」）
 */
@Composable
fun DoneCard(known: Int, forgot: Int, words: Int, inBrowse: Boolean) {
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
        Spacer(Modifier.height(16.dp))
        // v9（prd 第 1 条）：上滑引导取代 v7 确认按钮——到达本卡即转浏览模式，
        // 上滑进入推荐浏览；浏览模式中不再重复「进入推荐模式」
        Text(
            if (inBrowse) "随便看看吧" else "上滑进入推荐模式",
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = BluePrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        SwipeHint()
    }
}
