package com.knowmo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knowmo.app.ui.theme.AppText2
import com.knowmo.app.ui.theme.BluePrimary
import com.knowmo.app.ui.theme.OrangeDark

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
 * 内容：🎉 / 今日任务完成！/ 「今天学完了 N 个词 / 忘了 y 次」/ 上滑引导。
 * 战果数字是**当日持久口径**（§5.3）：重启不清零，隔天整体作废。
 * 视觉与词条卡同语言：白卡 + 投影浮在暖米色页底上。
 *
 * ⚠️ **完成卡上不显示「认识了 x 个词」**（v16 定稿）：连击机制下它**恒等于 N** ——
 * 每词要连对 3 次才移出队列、任意一次「忘了」就清零重来，而完成卡只在**全部词都过关**时出现，
 * 所以到达完成卡时每个词的连击数都是 3，「认识了几个词」必然等于「队列里有几个词」。
 * 两行数字永远一样，纯重复。（设置页的「今日认识 x 个词」**保留**：那里可能在学到一半时打开，
 * x < N 是有信息的。）
 *
 * @param words 当日任务区去重词数（N = 今天学完了 N 个词）。**持久口径**——取自当日队列
 *   （`repo.todayTaskWordCount()`），不由 pages 派生（到达完成卡时任务卡已被 enterBrowse 移除）
 * @param forgot 当日「忘了」作答**次数**（持久口径；忘了是可重复发生的事件，不是词的属性）
 * @param inBrowse 是否已处于浏览模式（true = 当日重启 / 已转浏览后的完成卡，引导文案改「随便看看吧」，
 *   不再重复「上滑进入推荐模式」）
 */
@Composable
fun DoneCard(words: Int, forgot: Int, inBrowse: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("🎉", fontSize = 84.sp)
            Spacer(Modifier.height(14.dp))
            Text(
                "今日任务完成！",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            // 战果两行呈现 + 数字放大着色：老年用户先扫到大数字、再读文字（成就反馈更直接）
            Text(
                buildAnnotatedString {
                    append("今天学完了 ")
                    withStyle(SpanStyle(color = BluePrimary, fontSize = 30.sp, fontWeight = FontWeight.Black)) {
                        append("$words")
                    }
                    append(" 个词\n忘了 ")
                    withStyle(SpanStyle(color = OrangeDark, fontSize = 30.sp, fontWeight = FontWeight.Black)) {
                        append("$forgot")
                    }
                    append(" 次")
                },
                fontSize = 22.sp,
                lineHeight = 40.sp,
                color = AppText2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            // v9（prd 第 1 条）：上滑引导取代 v7 确认按钮——到达本卡即转浏览模式，
            // 上滑进入推荐浏览；浏览模式中不再重复「进入推荐模式」
            Text(
                if (inBrowse) "随便看看吧" else "上滑进入推荐模式",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = BluePrimary,
                textAlign = TextAlign.Center,
            )
        }
        SwipeHint()
    }
}
