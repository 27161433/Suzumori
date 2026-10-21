package com.nakisuzu.suzumori.ui.fcm

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nakisuzu.suzumori.R
import com.nakisuzu.suzumori.core.fcm.PermissionState
import com.nakisuzu.suzumori.platform.FcmAppScanner
import com.nakisuzu.suzumori.platform.FcmAppStatus
import com.nakisuzu.suzumori.platform.SystemPages
import com.nakisuzu.suzumori.ui.components.BusyIcon
import com.nakisuzu.suzumori.ui.components.Chevron
import com.nakisuzu.suzumori.ui.components.Footnote
import com.nakisuzu.suzumori.ui.components.IconBadge
import com.nakisuzu.suzumori.ui.components.Panel
import com.nakisuzu.suzumori.ui.components.SquareIconButton
import com.nakisuzu.suzumori.ui.components.StatusChip
import com.nakisuzu.suzumori.ui.components.SuzumoriPage
import com.nakisuzu.suzumori.ui.components.SzIcons
import com.nakisuzu.suzumori.ui.theme.SuzumoriTheme
import top.yukonga.miuix.kmp.basic.Text

/**
 * FCM App 檢查：列出使用 FCM 的 App，以及它們的自啟動與省電策略。全部唯讀。
 *
 * 只在打開這一頁、按重新整理，或從系統設定頁返回（ON_RESUME）時讀取；不在背景執行，也不寫進事件紀錄。
 * 點 App 開啟它的應用詳情頁，點「省電策略」直接開啟它的省電策略頁。
 */
@Composable
fun FcmAppsScreen(onBack: () -> Unit, openPage: ((Context) -> String?) -> Unit) {
    val colors = SuzumoriTheme.colors
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<FcmAppStatus>?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    // 畫面已經在前景時加入觀察者，也會馬上收到 ON_RESUME，所以第一次讀取也由這裡觸發。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    LaunchedEffect(refresh) {
        if (refresh == 0) return@LaunchedEffect
        scanning = true
        try {
            apps = FcmAppScanner.scan(context)
        } finally {
            scanning = false
        }
    }

    SuzumoriPage(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "title") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SquareIconButton(icon = SzIcons.Back, contentDescription = stringResource(R.string.back), onClick = onBack)
                Text(
                    text = stringResource(R.string.fcm_apps_title),
                    modifier = Modifier.weight(1f),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SquareIconButton(
                    icon = SzIcons.Refresh,
                    contentDescription = stringResource(R.string.fcm_apps_refresh),
                    onClick = { if (!scanning) refresh++ },
                )
            }
        }
        item(key = "intro") {
            Panel(
                modifier = Modifier.fillMaxWidth(),
                color = colors.primaryContainer,
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.fcm_apps_intro),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onPrimaryContainer,
                )
                Text(text = stringResource(R.string.fcm_apps_dnd_hint), fontSize = 13.sp, lineHeight = 20.sp, color = colors.onPrimaryContainer)
            }
        }
        item(key = "summary") {
            Row(
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BusyIcon(icon = SzIcons.List, busy = scanning, tint = colors.onSurfaceMuted, size = 16.dp)
                Text(text = summaryText(apps, scanning), fontSize = 13.sp, color = colors.onSurfaceMuted)
            }
        }
        val list = apps.orEmpty()
        items(list.size, key = { list[it].packageName }) { index ->
            val app = list[index]
            AppRow(
                app = app,
                modifier = Modifier.animateItem(),
                onOpenDetails = { openPage { SystemPages.openAppDetails(it, app.packageName, app.uid) } },
                onOpenPowerPolicy = { openPage { SystemPages.openPowerPolicy(it, app.packageName, app.uid) } },
            )
        }
        item(key = "privacy") { Footnote(stringResource(R.string.fcm_apps_privacy), Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun summaryText(apps: List<FcmAppStatus>?, scanning: Boolean): String = when {
    apps == null -> stringResource(R.string.fcm_apps_scanning)
    apps.isEmpty() -> stringResource(if (scanning) R.string.fcm_apps_scanning else R.string.fcm_apps_empty)
    else -> {
        val found = pluralStringResource(R.plurals.fcm_apps_found, apps.size, apps.size)
        val attention = apps.count { it.needsAttention }
        if (attention == 0) found else "$found・" + pluralStringResource(R.plurals.fcm_apps_attention, attention, attention)
    }
}

@Composable
private fun AppRow(app: FcmAppStatus, modifier: Modifier, onOpenDetails: () -> Unit, onOpenPowerPolicy: () -> Unit) {
    val colors = SuzumoriTheme.colors
    val icon = remember(app.icon) { app.icon?.asImageBitmap() }
    Panel(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 12.dp, bottom = 14.dp),
        onClick = onOpenDetails,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
            } else {
                IconBadge(icon = SzIcons.Bell, tint = colors.onSurfaceMuted)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = app.label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = app.packageName,
                        fontSize = 11.sp,
                        color = colors.onSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StateChip(
                        label = stringResource(R.string.about_autostart),
                        value = stateText(app.autoStart, R.string.status_on, R.string.status_off),
                        state = app.autoStart,
                        onClick = onOpenDetails,
                    )
                    StateChip(
                        label = stringResource(R.string.about_power_policy),
                        value = stateText(app.noRestrictions, R.string.power_no_restrictions, R.string.power_restricted),
                        state = app.noRestrictions,
                        onClick = onOpenPowerPolicy,
                    )
                }
            }
            Box(Modifier.padding(start = 4.dp)) { Chevron() }
        }
    }
}

@Composable
private fun stateText(state: PermissionState, allowed: Int, denied: Int): String = stringResource(
    when (state) {
        PermissionState.ALLOWED -> allowed
        PermissionState.DENIED -> denied
        PermissionState.UNKNOWN -> R.string.status_undetermined
    },
)

/** 「自啟動 已開啟」這類狀態標籤，可以點擊開啟對應的系統頁面。讀不到時用中性的灰色，不當成關閉。 */
@Composable
private fun StateChip(label: String, value: String, state: PermissionState, onClick: () -> Unit) {
    val colors = SuzumoriTheme.colors
    val (fg, bg) = when (state) {
        PermissionState.ALLOWED -> colors.good to colors.goodContainer
        PermissionState.DENIED -> colors.warn to colors.warnContainer
        PermissionState.UNKNOWN -> colors.onSurfaceMuted to colors.surfaceVariant
    }
    StatusChip(
        text = "$label $value",
        color = fg,
        container = bg,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
    )
}

/** 首頁上的「FCM App 檢查」入口。 */
@Composable
fun FcmAppsEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SuzumoriTheme.colors
    Panel(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 18.dp, top = 16.dp, end = 14.dp, bottom = 16.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(icon = SzIcons.Bell, tint = colors.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = stringResource(R.string.fcm_apps_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                Text(
                    text = stringResource(R.string.fcm_apps_entry_summary),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = colors.onSurfaceMuted,
                )
            }
            Chevron()
        }
    }
}

