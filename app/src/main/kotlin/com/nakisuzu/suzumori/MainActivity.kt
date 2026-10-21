package com.nakisuzu.suzumori

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nakisuzu.suzumori.platform.SystemPages
import com.nakisuzu.suzumori.ui.about.AboutScreen
import com.nakisuzu.suzumori.ui.components.SzIcons
import com.nakisuzu.suzumori.ui.components.glass.LiquidGlassNavigationBar
import com.nakisuzu.suzumori.ui.fcm.FcmAppsScreen
import com.nakisuzu.suzumori.ui.main.BusyAction
import com.nakisuzu.suzumori.ui.main.EventLogScreen
import com.nakisuzu.suzumori.ui.main.EventLogViewModel
import com.nakisuzu.suzumori.ui.main.HomeScreen
import com.nakisuzu.suzumori.ui.main.LogDialogs
import com.nakisuzu.suzumori.ui.main.MainDialogs
import com.nakisuzu.suzumori.ui.main.MainViewModel
import com.nakisuzu.suzumori.ui.settings.SettingsScreen
import com.nakisuzu.suzumori.ui.settings.SettingsViewModel
import com.nakisuzu.suzumori.ui.components.Motion
import com.nakisuzu.suzumori.ui.theme.SuzumoriTheme
import com.nakisuzu.suzumori.ui.update.UpdateScreen
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
    private val container get() = (application as SuzumoriApp).container

    /** 更新頁是否開著。從更新通知開啟 App 時直接打開。 */
    private var showUpdate by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk 22 不會自動套用 edge-to-edge，要自己開啟並在 Compose 端處理 insets。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (consumeOpenUpdate(intent)) showUpdate = true

        // 開啟系統設定頁，失敗時顯示原因。
        fun openPage(page: (Context) -> String?) {
            page(this)?.let { viewModel.showMessage(getString(R.string.cannot_open_page), it) }
        }

        setContent {
            SuzumoriTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val logState by logViewModel.state.collectAsStateWithLifecycle()
                val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
                val updateState by container.updates.state.collectAsStateWithLifecycle()
                // 讀到之前先不顯示，避免已經關掉的說明閃一下。
                val showFcmAppsIntro by container.hints.showFcmAppsIntro.collectAsStateWithLifecycle(initialValue = false)
                var introShown by rememberSaveable { mutableStateOf(false) }
                val pagerState = rememberPagerState { PAGE_COUNT }
                val pages = listOf(
                    NavigationItem(stringResource(R.string.tab_home), SzIcons.Home),
                    NavigationItem(stringResource(R.string.tab_log), SzIcons.List),
                    NavigationItem(stringResource(R.string.tab_apps), SzIcons.Apps),
                    NavigationItem(stringResource(R.string.tab_settings), SzIcons.Sliders),
                    NavigationItem(stringResource(R.string.tab_about), SzIcons.Info),
                )
                val scope = rememberCoroutineScope()
                val background = SuzumoriTheme.colors.background
                val pageBackdrop = rememberLayerBackdrop {
                    drawRect(background)
                    drawContent()
                }

                // 從授權頁、省電設定或系統的 App 語言設定返回時重新讀取。
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    viewModel.refresh()
                    settingsViewModel.refreshLanguage()
                }

                // 不在首頁時，返回先回到首頁。
                BackHandler(enabled = pagerState.currentPage != 0) {
                    scope.launch { pagerState.animateScrollToPage(0) }
                }
                // 更新頁開著時，返回先關閉更新頁（後宣告的優先）。
                BackHandler(enabled = showUpdate) { showUpdate = false }

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
                            // 每一頁都保留，切換時捲動位置與狀態不會重置。
                            beyondViewportPageCount = PAGE_COUNT - 1,
                        ) { page ->
                            when (page) {
                                0 -> HomeScreen(
                                    state = state,
                                    viewModel = viewModel,
                                    playIntro = !introShown,
                                    onIntroShown = { introShown = true },
                                    openPage = ::openPage,
                                    onOpenLog = { scope.launch { pagerState.animateScrollToPage(1) } },
                                    update = updateState,
                                    onOpenUpdate = { showUpdate = true },
                                )
                                1 -> EventLogScreen(logState, logViewModel)
                                // 只有停在這一頁時才讀取 App 清單；滑動經過不算。
                                2 -> FcmAppsScreen(
                                    active = pagerState.settledPage == 2,
                                    showIntro = showFcmAppsIntro,
                                    onDismissIntro = { container.appScope.launch { container.hints.dismissFcmAppsIntro() } },
                                    openPage = ::openPage,
                                )
                                3 -> SettingsScreen(
                                    state = settingsState,
                                    viewModel = settingsViewModel,
                                    cleaning = state.busyAction == BusyAction.CLEANUP,
                                    canCleanup = !state.busy,
                                    onCleanup = viewModel::requestCleanup,
                                )
                                else -> AboutScreen(state, update = updateState, openPage = ::openPage, onOpenUpdate = { showUpdate = true })
                            }
                        }

                        LiquidGlassNavigationBar(
                            items = pages,
                            selectedIndex = pagerState.targetPage,
                            onItemClick = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                            backdrop = pageBackdrop,
                            isBlurActive = remember { isRuntimeShaderSupported() },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )

                        // 更新頁從右側滑入，蓋過分頁與導覽列。
                        AnimatedVisibility(
                            visible = showUpdate,
                            enter = slideInHorizontally(tween(450, easing = Motion.Emphasized)) { it / 3 } + fadeIn(tween(300)),
                            exit = slideOutHorizontally(tween(350, easing = Motion.Emphasized)) { it / 3 } + fadeOut(tween(200)),
                        ) {
                            UpdateScreen(
                                state = updateState,
                                onBack = { showUpdate = false },
                                onCheck = { container.appScope.launch { container.updates.check() } },
                                onOpenUrl = { url -> openPage { SystemPages.openUrl(it, url) } },
                            )
                        }

                        MainDialogs(state, viewModel, onUninstall = { openPage(SystemPages::requestUninstall) })
                        LogDialogs(logState, logViewModel)
                    }
                }
            }
        }
        absorbAutoFocus()
    }

    /**
     * targetSdk 低於 28 時，App 開啟或焦點被清掉（例如按鍵盤的搜尋鍵）後，系統一定會把焦點交給某個 View。
     * 交給 Compose 的話，Compose 會再自動交給第一個可聚焦的元件，例如別的分頁上的搜尋欄或可選取的文字，
     * 結果分頁被捲過去、鍵盤跳出來。讓最外層的容器自己接下這個焦點，Compose 只在使用者點了某個元件時才取得焦點。
     */
    private fun absorbAutoFocus() {
        findViewById<ViewGroup>(android.R.id.content).apply {
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
            defaultFocusHighlightEnabled = false
        }
    }

    // 深淺色與語言切換不重建 Activity（見 manifest 的 configChanges），由 Compose 依新的設定重新組成；
    // 狀態列圖示的深淺要在這裡重新套用。
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enableEdgeToEdge()
    }

    /** App 已經開著時點更新通知，交給現有的畫面打開更新頁。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (consumeOpenUpdate(intent)) showUpdate = true
    }

    /** 讀取並移除「打開更新頁」的要求，避免畫面重建時又打開一次。 */
    private fun consumeOpenUpdate(intent: Intent?): Boolean {
        if (intent?.getBooleanExtra(EXTRA_OPEN_UPDATE, false) != true) return false
        intent.removeExtra(EXTRA_OPEN_UPDATE)
        return true
    }

    companion object {
        /** 更新通知帶的 extra：開啟後直接打開更新頁。 */
        const val EXTRA_OPEN_UPDATE = "com.nakisuzu.suzumori.extra.OPEN_UPDATE"

        /** 首頁、事件紀錄、App 檢查、設定、關於。 */
        private const val PAGE_COUNT = 5
    }
}
