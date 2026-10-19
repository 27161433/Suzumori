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
import androidx.compose.ui.res.stringResource
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
 * 關於：原始碼與授權條款，以及本 App 的權限、省電策略、自啟動與系統版本。
 *
 * 權限類的項目都可以點擊，直接前往對應的系統頁面；沒開啟時多一行提示。
 */
@Composable
fun AboutScreen(state: MainUiState, openPage: ((Context) -> String?) -> Unit) {
    val colors = SuzumoriTheme.colors
    SuzumoriPage {
        item(key = "title") { PageTitle(stringResource(R.string.tab_about)) }
        item(key = "identity") { Entrance(index = 1, play = true) { AppIdentity() } }
        item(key = "project") {
            Entrance(index = 2, play = true) {
                Panel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                    InfoRow(
                        icon = SzIcons.Code,
                        iconTint = colors.onSurfaceMuted,
                        title = stringResource(R.string.about_source_code),
                        onClick = { openPage(SystemPages::openSourceCode) },
                    ) {
                        NavigableValue { ValueText("GitHub", colors.onSurfaceMuted) }
                    }
                    InfoRow(
                        icon = SzIcons.Document,
                        iconTint = colors.onSurfaceMuted,
                        title = stringResource(R.string.about_license),
                        showDivider = false,
                        onClick = { openPage(SystemPages::openLicense) },
                    ) {
                        NavigableValue { ValueText(SystemPages.LICENSE_NAME, colors.onSurfaceMuted) }
                    }
                }
            }
        }
        item(key = "app") {
            Entrance(index = 3, play = true) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Panel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                        InfoRow(
                            icon = SzIcons.Key,
                            iconTint = colors.onSurfaceMuted,
                            title = stringResource(R.string.about_write_permission),
                            summary = if (state.loaded && !state.canWrite) stringResource(R.string.about_write_permission_hint) else null,
                            onClick = { openPage(SystemPages::openWriteSettings) },
                        ) {
                            NavigableValue {
                                when {
                                    !state.loaded -> ValueText("—", colors.onSurfaceMuted)
                                    state.canWrite -> ValueText(stringResource(R.string.status_on), colors.good)
                                    else -> ValueText(stringResource(R.string.status_off), colors.warn)
                                }
                            }
                        }
                        InfoRow(
                            icon = SzIcons.Battery,
                            iconTint = colors.onSurfaceMuted,
                            title = stringResource(R.string.about_power_policy),
                            summary = if (state.loaded && !state.selfNoRestrict) stringResource(R.string.about_power_policy_hint) else null,
                            onClick = { openPage(SystemPages::openPowerPolicy) },
                        ) {
                            NavigableValue {
                                when {
                                    !state.loaded -> ValueText("—", colors.onSurfaceMuted)
                                    state.selfNoRestrict -> ValueText(stringResource(R.string.power_no_restrictions), colors.good)
                                    else -> ValueText(stringResource(R.string.power_restricted), colors.warn)
                                }
                            }
                        }
                        InfoRow(
                            icon = SzIcons.Power,
                            iconTint = colors.onSurfaceMuted,
                            title = stringResource(R.string.about_autostart),
                            summary = if (state.selfAutoStart == false) stringResource(R.string.about_autostart_hint) else null,
                            onClick = { openPage(SystemPages::openAppDetails) },
                        ) {
                            NavigableValue {
                                when {
                                    !state.loaded -> ValueText("—", colors.onSurfaceMuted)
                                    state.selfAutoStart == true -> ValueText(stringResource(R.string.status_on), colors.good)
                                    state.selfAutoStart == false -> ValueText(stringResource(R.string.status_off), colors.warn)
                                    else -> ValueText(stringResource(R.string.status_unreadable), colors.onSurfaceMuted)
                                }
                            }
                        }
                        InfoRow(
                            icon = SzIcons.Phone,
                            iconTint = colors.onSurfaceMuted,
                            title = stringResource(R.string.about_system_version),
                            showDivider = false,
                        ) {
                            Text(
                                text = state.systemVersion ?: stringResource(R.string.unknown),
                                modifier = Modifier.widthIn(max = 180.dp),
                                fontSize = 13.sp,
                                textAlign = TextAlign.End,
                                color = colors.onSurfaceMuted,
                            )
                        }
                    }
                    Footnote(stringResource(R.string.about_footnote))
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
    val info = remember(context) { runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() }
    val version = if (info != null) {
        stringResource(R.string.about_version, info.versionName.orEmpty())
    } else {
        stringResource(R.string.about_version_unknown)
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
        Text(text = stringResource(R.string.app_tagline), fontSize = 13.sp, color = colors.onSurfaceMuted)
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
