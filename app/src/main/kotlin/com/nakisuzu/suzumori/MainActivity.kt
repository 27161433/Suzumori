package com.nakisuzu.suzumori

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nakisuzu.suzumori.ui.main.EventLogScreen
import com.nakisuzu.suzumori.ui.main.MainScreen
import com.nakisuzu.suzumori.ui.main.MainViewModel
import com.nakisuzu.suzumori.ui.theme.SuzumoriTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk 22 不會自動套用 edge-to-edge，要自己開啟並在 Compose 端處理 insets。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SuzumoriTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                var showLog by rememberSaveable { mutableStateOf(false) }

                // 從授權頁或省電設定返回時重新讀取。
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
                BackHandler(enabled = showLog) { showLog = false }

                AnimatedContent(
                    targetState = showLog,
                    transitionSpec = {
                        if (targetState) {
                            slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 4 }
                        } else {
                            slideInHorizontally { -it / 4 } togetherWith slideOutHorizontally { it }
                        }
                    },
                    label = "page",
                ) { logPage ->
                    if (logPage) {
                        EventLogScreen(state, viewModel, onBack = { showLog = false })
                    } else {
                        MainScreen(state, viewModel, onOpenLog = { showLog = true })
                    }
                }
            }
        }
    }
}
