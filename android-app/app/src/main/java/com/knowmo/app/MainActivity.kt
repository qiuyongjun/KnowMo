package com.knowmo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.knowmo.app.data.AppSettings
import com.knowmo.app.data.CustomBanks
import com.knowmo.app.data.StudyRepository
import com.knowmo.app.tts.TTSSpeaker
import com.knowmo.app.ui.AppRoot
import com.knowmo.app.ui.theme.KnowMoTheme

class MainActivity : ComponentActivity() {

    private val tts by lazy { TTSSpeaker(this) }
    // v6 设置仓库（design.md §11.1）：独立 SharedPreferences（app_settings），AppRoot 直接读
    private val settings by lazy { AppSettings(this) }
    // v6 配额注入：buildQueue 只在**重建当日队列**时读 quotaProvider()/newQuotaProvider()——当日队列冻结不变、次日生效
    // （v6 R14：新词配额独立注入；v28 起复习优先占预算——复习积压时新词自动降速到保底 1 个）
    // v9 推荐范围注入：buildQueue/poolIds 只抽**可见分区**的词（prd v9 第 3 条；
    // v9 的 CHANNEL_COMMON 恒入特例随 2026-09-28 死代码清理删除——v23 起内置词退役，特例恒空）
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
        // v22：自定义词库注册表必须先于 settings/repo 的首次访问装载（两者虽是 lazy，但都在
        // 组合期间首次访问）——allScenes()/STUDY_TERMS 才能包含自定义分区，见 CustomBanks KDoc。
        CustomBanks.load(this)
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
