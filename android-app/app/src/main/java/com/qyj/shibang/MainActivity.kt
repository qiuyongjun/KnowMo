package com.qyj.shibang

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.qyj.shibang.data.StudyRepository
import com.qyj.shibang.tts.TTSSpeaker
import com.qyj.shibang.ui.AppRoot
import com.qyj.shibang.ui.theme.ShibangTheme

class MainActivity : ComponentActivity() {

    private val tts by lazy { TTSSpeaker(this) }
    private val repo by lazy { StudyRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShibangTheme {
                AppRoot(repo = repo, tts = tts)
            }
        }
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}
