package com.knowmo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.knowmo.app.data.AppSettings
import com.knowmo.app.data.StudyRepository
import com.knowmo.app.tts.TTSSpeaker
import com.knowmo.app.ui.AppRoot
import com.knowmo.app.ui.theme.KnowMoTheme

class MainActivity : ComponentActivity() {

    private val tts by lazy { TTSSpeaker(this) }
    // v6 设置仓库（design.md §11.1）：独立 SharedPreferences（app_settings），AppRoot 直接读
    private val settings by lazy { AppSettings(this) }
    // v6 配额注入：buildQueue 只在**重建当日队列**时读 quotaProvider()/newQuotaProvider()——当日队列冻结不变、次日生效
    // （v6 R14：新词配额独立注入，新词速率恒定、不被到期复习挤占）
    // v9 推荐范围注入：buildQueue/poolIds 只抽**可见分区**的词（隐藏分区排除，prd v9 第 3 条）；
    // 常用词分区由 StudyRepository 内部特例（CHANNEL_COMMON）恒入推荐范围（不可显隐的内容源，prd v9 第 4 条）
    private val repo by lazy {
        StudyRepository(
            this,
            { settings.quota() },
            { settings.quotaNew() },
            { settings.visibleScenes().map { it.id }.toSet() },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            KnowMoTheme {
                AppRoot(repo = repo, tts = tts, settings = settings)
            }
        }
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}
