package com.nakisuzu.suzumori.ui.about

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nakisuzu.suzumori.R
import com.nakisuzu.suzumori.platform.SystemPages
import com.nakisuzu.suzumori.ui.components.Chevron
import com.nakisuzu.suzumori.ui.components.Entrance
import com.nakisuzu.suzumori.ui.components.Footnote
import com.nakisuzu.suzumori.ui.components.InfoRow
import com.nakisuzu.suzumori.ui.components.PageTitle
import com.nakisuzu.suzumori.ui.components.Panel
import com.nakisuzu.suzumori.ui.components.SuzumoriPage
import com.nakisuzu.suzumori.ui.components.SzIcons
import com.nakisuzu.suzumori.ui.components.ValueText
import com.nakisuzu.suzumori.ui.main.MainUiState
import com.nakisuzu.suzumori.ui.theme.SuzumoriTheme
import top.yukonga.miuix.kmp.basic.Text

/**
 * 關於：本 App 的權限、省電策略、自啟動與系統版本。
 *
 * 權限類的項目都可以點擊，直接前往對應的系統頁面；沒開啟時多一行提示。
 */
@Composable
fun AboutScreen(state: MainUiState, openPage: ((Context) -> String?) -> Unit) {
    val colors = SuzumoriTheme.colors
    SuzumoriPage {
        item(key = "title") { PageTitle("關於") }
        item(key = "identity") { Entrance(index = 1, play = true) { AppIdentity() } }
        item(key = "app") {
            Entrance(index = 2, play = true) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Panel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                        InfoRow(
                            icon = SzIcons.Key,
                            iconTint = colors.onSurfaceMuted,
                            title = "寫入權限",
                            summary = if (state.loaded && !state.canWrite) "點此前往開啟" else null,
                            onClick = { openPage(SystemPages::openWriteSettings) },
                        ) {
                            NavigableValue {
                                when {
                                    !state.loaded -> ValueText("—", colors.onSurfaceMuted)
                                    state.canWrite -> ValueText("已開啟", colors.good)
                                    else -> ValueText("未開啟", colors.warn)
                                }
                            }
                        }
                        InfoRow(
                            icon = SzIcons.Battery,
                            iconTint = colors.onSurfaceMuted,
                            title = "省電策略",
                            summary = if (state.loaded && !state.selfNoRestrict) "點此前往設為「無限制」" else null,
                            onClick = { openPage(SystemPages::openPowerPolicy) },
                        ) {
                            NavigableValue {
                                when {
                                    !state.loaded -> ValueText("—", colors.onSurfaceMuted)
                                    state.selfNoRestrict -> ValueText("無限制", colors.good)
                                    else -> ValueText("未設為無限制", colors.warn)
                                }
                            }
                        }
                        InfoRow(
                            icon = SzIcons.Power,
                            iconTint = colors.onSurfaceMuted,
                            title = "自啟動",
                            summary = if (state.selfAutoStart == false) "點此前往應用管理開啟" else null,
                            onClick = { openPage(SystemPages::openAppDetails) },
                        ) {
                            NavigableValue {
                                when {
                                    !state.loaded -> ValueText("—", colors.onSurfaceMuted)
                                    state.selfAutoStart == true -> ValueText("已開啟", colors.good)
                                    state.selfAutoStart == false -> ValueText("未開啟", colors.warn)
                                    else -> ValueText("無法讀取", colors.onSurfaceMuted)
                                }
                            }
                        }
                        InfoRow(icon = SzIcons.Phone, iconTint = colors.onSurfaceMuted, title = "系統版本", showDivider = false) {
                            Text(
                                text = state.systemVersion ?: "未知",
                                modifier = Modifier.widthIn(max = 180.dp),
                                fontSize = 13.sp,
                                textAlign = TextAlign.End,
                                color = colors.onSurfaceMuted,
                            )
                        }
                    }
                    Footnote("Suzumori 必須一直在背景才能即時補回名單。沒有「自啟動」時，小米不會在開機時通知本 App。")
                }
            }
        }
    }
}

/** 值後面加上箭頭，表示這一列可以點進系統頁面。 */
@Composable
private fun NavigableValue(value: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        value()
        Chevron()
    }
}

/** 插畫、App 名稱與版本號。插畫背後有一圈和首頁圓環同色的光暈。 */
@Composable
private fun AppIdentity() {
    val colors = SuzumoriTheme.colors
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "版本 ${info.versionName}（${info.longVersionCode}）"
        }.getOrDefault("版本未知")
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.about_artwork),
            contentDescription = null,
            modifier = Modifier
                .size(176.dp)
                .drawBehind {
                    val radius = size.minDimension * 0.62f
                    drawCircle(Brush.radialGradient(listOf(colors.glow, Color.Transparent), center, radius), radius, center)
                },
        )
        Spacer(Modifier.height(14.dp))
        Text(text = "Suzumori", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(text = "鈴守・守護 FCM 推播", fontSize = 13.sp, color = colors.onSurfaceMuted)
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(text = version, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceMuted)
        }
    }
}
