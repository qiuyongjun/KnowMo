package com.qyj.shibang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qyj.shibang.data.StudyRepository
import com.qyj.shibang.data.Term
import com.qyj.shibang.data.TermChar
import com.qyj.shibang.ui.theme.AppLine
import com.qyj.shibang.ui.theme.AppSurface
import com.qyj.shibang.ui.theme.AppText
import com.qyj.shibang.ui.theme.AppText2
import com.qyj.shibang.ui.theme.BlueBg
import com.qyj.shibang.ui.theme.BlueDark
import com.qyj.shibang.ui.theme.BluePrimary
import com.qyj.shibang.ui.theme.OrangeBg
import com.qyj.shibang.ui.theme.OrangeDark
import com.qyj.shibang.ui.theme.RedForgot

/* ==================== 字卡弹层 ==================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharSheet(ch: TermChar, terms: List<Term>, onSpeak: (String) -> Unit, onDismiss: () -> Unit) {
    val related = terms.filter { t -> t.chars.any { it.c == ch.c } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                ch.c,
                fontSize = 92.sp,
                fontWeight = FontWeight.Black,
                color = AppText,
                modifier = Modifier.clickable { onSpeak(ch.c) },
            )
            Text(
                ch.p,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = BluePrimary,
                modifier = Modifier.clickable { onSpeak(ch.c) },
            )
            HorizontalDivider(Modifier.padding(vertical = 14.dp), thickness = 2.dp, color = AppLine)
            Text(
                "🗣️ 出现在这些词里（点一下听）",
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppText2,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                related.forEach { t ->
                    val shape = RoundedCornerShape(14.dp)
                    Box(
                        Modifier
                            .clip(shape)
                            .background(BlueBg)
                            .clickable { onSpeak(t.text) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Text(t.text, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = BlueDark)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "👆 点上面的字和词，就能听发音",
                fontSize = 18.sp, color = AppText2,
            )
        }
    }
}

/* ==================== 设置弹层 ==================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    fontScale: Float,
    speechRate: Float,
    remindTime: String,
    onFont: (Float) -> Unit,
    onRate: (Float) -> Unit,
    onRemind: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 34.dp),
        ) {
            Text("⚙️ 设置", fontSize = 24.sp, fontWeight = FontWeight.Black, color = AppText)

            SectionLabel("🔤 字体大小")
            SegRow(
                options = listOf("正常", "更大 🔎"),
                selected = if (fontScale > 1f) 1 else 0,
                onSelect = { onFont(if (it == 0) 1f else 1.15f) },
            )

            SectionLabel("🐢 朗读速度")
            SegRow(
                options = listOf("慢", "适中", "稍快"),
                selected = when (speechRate) {
                    0.7f -> 0
                    1.0f -> 2
                    else -> 1
                },
                onSelect = { onRate(listOf(0.7f, 0.85f, 1.0f)[it]) },
            )

            SectionLabel("⏰ 每天提醒我来学")
            SegRow(
                options = listOf("上午 9 点", "下午 3 点", "晚上 7 点"),
                selected = listOf("上午 9 点", "下午 3 点", "晚上 7 点").indexOf(remindTime).coerceAtLeast(0),
                onSelect = { onRemind(listOf("上午 9 点", "下午 3 点", "晚上 7 点")[it]) },
            )
            Text(
                "到时间手机会大声提醒，不会错过",
                fontSize = 18.sp, color = AppText2,
                modifier = Modifier.padding(top = 8.dp),
            )

            SectionLabel("🧠 复习是怎么安排的")
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(OrangeBg)
                    .border(2.dp, OrangeDark, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(
                    "认识的词 → 1 天 → 3 天 → 7 天 → 15 天后再见\n" +
                        "忘了的词 → 明天再来，还会进生词本\n" +
                        "越认得牢，见得越少；越生疏，见得越勤 💪",
                    fontSize = 20.sp, lineHeight = 34.sp, color = OrangeDark,
                )
            }

            SectionLabel("📷 拍照识字")
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppSurface)
                    .border(2.dp, AppLine, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("后续版本开放，敬请期待", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppText2)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "慢慢懂 Mando · 安卓版 v0.2（抖音式学习）",
                fontSize = 15.sp, color = AppText2,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 21.sp, fontWeight = FontWeight.Bold, color = AppText,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun SegRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            val shape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(shape)
                    .background(if (sel) BlueBg else AppSurface)
                    .border(3.dp, if (sel) BluePrimary else AppLine, shape)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    o,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = if (sel) BlueDark else AppText2,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/* ==================== 生词本弹层 ==================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordBookSheet(repo: StudyRepository, terms: List<Term>, onSpeak: (String) -> Unit, onDismiss: () -> Unit) {
    val ids = repo.forgotIds
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 34.dp),
        ) {
            Text("📕 我的生词本", fontSize = 24.sp, fontWeight = FontWeight.Black, color = AppText)
            Spacer(Modifier.height(14.dp))
            if (ids.isEmpty()) {
                Text(
                    "还是空的 📕\n忘了的词会自动收进来，点开就能复习",
                    fontSize = 20.sp, color = AppText2, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
                )
            } else {
                ids.forEach { id ->
                    val t = terms.firstOrNull { it.id == id } ?: return@forEach
                    val shape = RoundedCornerShape(16.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(AppSurface)
                            .border(2.dp, AppLine, shape)
                            .clickable { onSpeak(t.text) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t.text, fontSize = 28.sp, fontWeight = FontWeight.Black, color = AppText)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            t.chars.joinToString(" ") { it.p },
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BluePrimary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text("忘了 ${repo.forgotCount(id)} 次", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = RedForgot)
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}
