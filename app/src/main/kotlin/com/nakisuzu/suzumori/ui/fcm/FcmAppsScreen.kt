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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nakisuzu.suzumori.R
import com.nakisuzu.suzumori.core.fcm.FcmAppSort
import com.nakisuzu.suzumori.core.fcm.FcmApps
import com.nakisuzu.suzumori.core.fcm.PermissionState
import com.nakisuzu.suzumori.platform.FcmAppScanner
import com.nakisuzu.suzumori.platform.FcmAppStatus
import com.nakisuzu.suzumori.platform.SystemPages
import com.nakisuzu.suzumori.ui.components.BusyIcon
import com.nakisuzu.suzumori.ui.components.Chevron
import com.nakisuzu.suzumori.ui.components.Footnote
import com.nakisuzu.suzumori.ui.components.IconBadge
import com.nakisuzu.suzumori.ui.components.PageTitle
import com.nakisuzu.suzumori.ui.components.Panel
import com.nakisuzu.suzumori.ui.components.SegmentedSelector
import com.nakisuzu.suzumori.ui.components.SquareIconButton
import com.nakisuzu.suzumori.ui.components.StatusChip
import com.nakisuzu.suzumori.ui.components.SuzumoriPage
import com.nakisuzu.suzumori.ui.components.SzIcons
import com.nakisuzu.suzumori.ui.theme.SuzumoriTheme
import java.text.Collator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults

/**
 * FCM App 檢查分頁：列出使用 FCM 的 App，以及它們的自啟動與省電策略。全部唯讀。
 *
 * 分頁一直組成著，只在停在這一頁（[active]）時讀取：切到這一頁、按重新整理，或從系統設定頁返回（ON_RESUME）。
 * 不在背景執行，也不寫進事件紀錄。可以搜尋名稱或套件名稱，並依名稱、自啟動或省電策略排序。
 * 點 App 開啟它的應用詳情頁，點「省電策略」直接開啟它的省電策略頁。
 * 上方的說明在 [showIntro] 為 true 時顯示，按右上角的關閉鈕呼叫 [onDismissIntro]。
 */
@Composable
fun FcmAppsScreen(
    active: Boolean,
    showIntro: Boolean,
    onDismissIntro: () -> Unit,
    openPage: ((Context) -> String?) -> Unit,
) {
    val colors = SuzumoriTheme.colors
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<FcmAppStatus>?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(FcmAppSort.NAME) }
    val locale = LocalConfiguration.current.locales[0]
    val shown = remember(apps, query, sort, locale) {
        apps?.let { FcmApps.arrange(it, query, sort, Collator.getInstance(locale)) }
    }

    // 剛開 App 時的 ON_RESUME 還沒讀過，交給下面切到這一頁時的讀取，避免同時讀兩次。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (active && apps != null) refresh++ }
    LaunchedEffect(active, refresh) {
        if (!active) return@LaunchedEffect
        scanning = true
        try {
            apps = FcmAppScanner.scan(context)
        } finally {
            scanning = false
        }
    }

    SuzumoriPage(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "title") {
            PageTitle(stringResource(R.string.fcm_apps_title)) {
                SquareIconButton(
                    icon = SzIcons.Refresh,
                    contentDescription = stringResource(R.string.fcm_apps_refresh),
                    onClick = { if (!scanning) refresh++ },
                )
            }
        }
        if (showIntro) {
            item(key = "intro") { Intro(onDismiss = onDismissIntro, modifier = Modifier.animateItem()) }
        }
        item(key = "search") { SearchField(query = query, onQueryChange = { query = it }, active = active) }
        item(key = "sort") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.fcm_apps_sort),
                    modifier = Modifier.padding(start = 4.dp),
                    fontSize = 13.sp,
                    color = colors.onSurfaceMuted,
                )
                SegmentedSelector(
                    options = FcmAppSort.entries,
                    selected = sort,
                    label = { stringResource(sortLabel(it)) },
                    onSelect = { sort = it },
                    modifier = Modifier.weight(1f),
                    background = colors.surface,
                )
            }
        }
        item(key = "summary") {
            Row(
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BusyIcon(icon = SzIcons.List, busy = scanning, tint = colors.onSurfaceMuted, size = 16.dp)
                Text(text = summaryText(apps, shown, query.isNotBlank(), scanning), fontSize = 13.sp, color = colors.onSurfaceMuted)
            }
        }
        val list = shown.orEmpty()
        if (!apps.isNullOrEmpty() && list.isEmpty()) {
            item(key = "noMatch") {
                Text(
                    text = stringResource(R.string.fcm_apps_no_match, query.trim()),
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    fontSize = 14.sp,
                    color = colors.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
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

/** 頁面上方的說明，右上角可以關閉。 */
@Composable
private fun Intro(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SuzumoriTheme.colors
    Panel(
        modifier = modifier.fillMaxWidth(),
        color = colors.primaryContainer,
        contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 8.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = stringResource(R.string.fcm_apps_intro),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onPrimaryContainer,
            )
            // 往上移一點，讓按鈕中心對齊第一行文字。
            Box(
                modifier = Modifier
                    .offset(y = (-8).dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SzIcons.Close,
                    contentDescription = stringResource(R.string.fcm_apps_intro_dismiss),
                    modifier = Modifier.size(18.dp),
                    tint = colors.onPrimaryContainer,
                )
            }
        }
    }
}

private fun sortLabel(sort: FcmAppSort) = when (sort) {
    FcmAppSort.NAME -> R.string.fcm_apps_sort_name
    FcmAppSort.AUTO_START -> R.string.fcm_apps_sort_autostart
    FcmAppSort.NO_RESTRICTIONS -> R.string.fcm_apps_sort_power
}

/**
 * 搜尋欄：比對 App 名稱與套件名稱，有輸入時右側出現清除按鈕。
 *
 * 按鍵盤的搜尋鍵，或 [active] 變成 false（切到別的分頁）時收起鍵盤。
 */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, active: Boolean) {
    val colors = SuzumoriTheme.colors
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }

    fun release() {
        if (focused) focusManager.clearFocus()
    }
    LaunchedEffect(active) { if (!active) release() }

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.hasFocus },
        colors = TextFieldDefaults.textFieldColors(
            backgroundColor = colors.surface,
            labelColor = colors.onSurfaceMuted,
            borderColor = colors.primary,
        ),
        label = stringResource(R.string.fcm_apps_search),
        useLabelAsPlaceholder = true,
        textStyle = TextStyle(fontSize = 15.sp, color = colors.onSurface),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { release() }),
        leadingIcon = {
            Icon(
                imageVector = SzIcons.Search,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 16.dp, end = 10.dp)
                    .size(20.dp),
                tint = colors.onSurfaceMuted,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SzIcons.Close,
                        contentDescription = stringResource(R.string.fcm_apps_clear_search),
                        modifier = Modifier.size(18.dp),
                        tint = colors.onSurfaceMuted,
                    )
                }
            }
        },
        singleLine = true,
    )
}

@Composable
private fun summaryText(apps: List<FcmAppStatus>?, shown: List<FcmAppStatus>?, searching: Boolean, scanning: Boolean): String = when {
    apps == null -> stringResource(R.string.fcm_apps_scanning)
    apps.isEmpty() -> stringResource(if (scanning) R.string.fcm_apps_scanning else R.string.fcm_apps_empty)
    else -> {
        val found = pluralStringResource(R.plurals.fcm_apps_found, apps.size, apps.size)
        val matching = shown.orEmpty().size
        val attention = apps.count { it.needsAttention }
        when {
            searching -> "$found・" + pluralStringResource(R.plurals.fcm_apps_matching, matching, matching)
            attention == 0 -> found
            else -> "$found・" + pluralStringResource(R.plurals.fcm_apps_attention, attention, attention)
        }
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
