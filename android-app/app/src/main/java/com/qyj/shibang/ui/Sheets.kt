package com.qyj.shibang.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qyj.shibang.data.Term
import com.qyj.shibang.data.TermChar
import com.qyj.shibang.ui.theme.AppLine
import com.qyj.shibang.ui.theme.AppText
import com.qyj.shibang.ui.theme.AppText2
import com.qyj.shibang.ui.theme.BlueBg
import com.qyj.shibang.ui.theme.BlueDark
import com.qyj.shibang.ui.theme.BluePrimary

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

