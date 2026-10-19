package com.nakisuzu.suzumori

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nakisuzu.suzumori.platform.SystemPages
import com.nakisuzu.suzumori.ui.about.AboutScreen
import com.nakisuzu.suzumori.ui.components.SzIcons
import com.nakisuzu.suzumori.ui.components.glass.LiquidGlassNavigationBar
import com.nakisuzu.suzumori.ui.main.BusyAction
import com.nakisuzu.suzumori.ui.main.EventLogScreen
import com.nakisuzu.suzumori.ui.main.EventLogViewModel
import com.nakisuzu.suzumori.ui.main.HomeScreen
import com.nakisuzu.suzumori.ui.main.LogDialogs
import com.nakisuzu.suzumori.ui.main.MainDialogs
import com.nakisuzu.suzumori.ui.main.MainViewModel
import com.nakisuzu.suzumori.ui.settings.SettingsScreen
import com.nakisuzu.suzumori.ui.settings.SettingsViewModel
import com.nakisuzu.suzumori.ui.theme.SuzumoriTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }
    private val logViewModel: EventLogViewModel by viewModels { EventLogViewModel.Factory }
    private val settingsViewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk 22 不會自動套用 edge-to-edge，要自己開啟並在 Compose 端處理 insets。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 開啟系統設定頁，失敗時顯示原因。
        fun openPage(page: (Context) -> String?) {
            page(this)?.let { viewModel.showMessage("無法開啟頁面", it) }
        }

        setContent {
            SuzumoriTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val logState by logViewModel.state.collectAsStateWithLifecycle()
                val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
                var introShown by rememberSaveable { mutableStateOf(false) }
                val pagerState = rememberPagerState { PAGES.size }
                val scope = rememberCoroutineScope()
                val background = SuzumoriTheme.colors.background
                val pageBackdrop = rememberLayerBackdrop {
                    drawRect(background)
                    drawContent()
                }

                // 從授權頁或省電設定返回時重新讀取。
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

                // 不在首頁時，返回先回到首頁。
                BackHandler(enabled = pagerState.currentPage != 0) {
                    scope.launch { pagerState.animateScrollToPage(0) }
                }

                // miuix 的對話框（OverlayDialog）要靠 Scaffold 提供的圖層才顯示得出來，所以最外層一定要包 Scaffold。
                // 內距由各頁自己處理，這裡不使用 Scaffold 給的 padding。
                Scaffold { _ ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(background),
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .layerBackdrop(pageBackdrop),
                            // 四頁都保留，切換時捲動位置與狀態不會重置。
                            beyondViewportPageCount = PAGES.size - 1,
                        ) { page ->
                            when (page) {
                                0 -> HomeScreen(
                                    state = state,
                                    viewModel = viewModel,
                                    playIntro = !introShown,
                                    onIntroShown = { introShown = true },
                                    openPage = ::openPage,
                                    onOpenLog = { scope.launch { pagerState.animateScrollToPage(1) } },
                                )
                                1 -> EventLogScreen(logState, logViewModel)
                                2 -> SettingsScreen(
                                    state = settingsState,
                                    viewModel = settingsViewModel,
                                    cleaning = state.busyAction == BusyAction.CLEANUP,
                                    canCleanup = !state.busy,
                                    onCleanup = viewModel::requestCleanup,
                                )
                                else -> AboutScreen(state, openPage = ::openPage)
                            }
                        }

                        LiquidGlassNavigationBar(
                            items = PAGES,
                            selectedIndex = pagerState.targetPage,
                            onItemClick = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                            backdrop = pageBackdrop,
                            isBlurActive = remember { isRuntimeShaderSupported() },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )

                        MainDialogs(state, viewModel, onUninstall = { openPage(SystemPages::requestUninstall) })
                        LogDialogs(logState, logViewModel)
                    }
                }
            }
        }
    }

    // 深淺色切換不重建 Activity（manifest 宣告 configChanges="uiMode"），由 Compose 漸變配色；
    // 狀態列圖示的深淺要在這裡重新套用。
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enableEdgeToEdge()
    }

    private companion object {
        val PAGES = listOf(
            NavigationItem("首頁", SzIcons.Home),
            NavigationItem("事件紀錄", SzIcons.List),
            NavigationItem("設定", SzIcons.Sliders),
            NavigationItem("關於", SzIcons.Info),
        )
    }
}
