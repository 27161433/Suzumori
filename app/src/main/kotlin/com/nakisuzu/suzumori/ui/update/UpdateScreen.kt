package com.nakisuzu.suzumori.ui.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nakisuzu.suzumori.R
import com.nakisuzu.suzumori.platform.UpdateState
import com.nakisuzu.suzumori.ui.components.BusyIcon
import com.nakisuzu.suzumori.ui.components.Footnote
import com.nakisuzu.suzumori.ui.components.IconBadge
import com.nakisuzu.suzumori.ui.components.Motion
import com.nakisuzu.suzumori.ui.components.Panel
import com.nakisuzu.suzumori.ui.components.SectionTitle
import com.nakisuzu.suzumori.ui.components.SquareIconButton
import com.nakisuzu.suzumori.ui.components.SuzumoriPage
import com.nakisuzu.suzumori.ui.components.SzIcons
import com.nakisuzu.suzumori.ui.main.TimeText
import com.nakisuzu.suzumori.ui.theme.SuzumoriTheme
import top.yukonga.miuix.kmp.basic.Text
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 更新頁：目前版本、GitHub 上的最新版本與更新內容，以及下載按鈕。
 *
 * 下載交給瀏覽器或系統下載器處理，App 不需要安裝 App 的權限。
 * 打開這一頁時，距離上次檢查超過一分鐘就重新檢查一次。
 */
@Composable
fun UpdateScreen(
    state: UpdateState,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val colors = SuzumoriTheme.colors
    LaunchedEffect(Unit) {
        val last = state.lastCheckedMillis
        if (!state.checking && (last == null || System.currentTimeMillis() - last > RECHECK_AFTER_MILLIS)) onCheck()
    }
    val latest = state.latest

    SuzumoriPage(verticalArrangement = Arrangement.spacedBy(20.dp)) {
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
                    text = stringResource(R.string.update_title),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = colors.onSurface,
                )
            }
        }
        item(key = "status") { StatusPanel(state) }
        if (latest != null && state.available) {
            item(key = "download") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton(
                        text = stringResource(R.string.update_download, latest.version.toString()),
                        filled = true,
                        onClick = { onOpenUrl(latest.apkUrl ?: latest.pageUrl) },
                    )
                    ActionButton(text = stringResource(R.string.update_open_github), filled = false, onClick = { onOpenUrl(latest.pageUrl) })
                    Footnote(stringResource(R.string.update_install_hint))
                    latest.apkSha256?.let { sha ->
                        SelectionContainer {
                            Text(
                                text = "SHA-256  $sha",
                                modifier = Modifier.padding(horizontal = 4.dp),
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.onSurfaceMuted,
                            )
                        }
                    }
                }
            }
            if (latest.notes.isNotBlank()) {
                item(key = "notes") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle(stringResource(R.string.update_notes))
                        Panel(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                            ReleaseNotes(latest.notes)
                        }
                    }
                }
            }
        } else {
            item(key = "check") {
                ActionButton(
                    text = stringResource(R.string.update_check_now),
                    filled = false,
                    enabled = !state.checking,
                    onClick = onCheck,
                )
            }
        }
    }
}

@Composable
private fun StatusPanel(state: UpdateState) {
    val colors = SuzumoriTheme.colors
    val context = LocalContext.current
    val locales = LocalConfiguration.current.locales
    val latest = state.latest
    val (title, tint) = when {
        state.available && latest != null -> stringResource(R.string.update_available, latest.version.toString()) to colors.primary
        state.checking -> stringResource(R.string.update_checking) to colors.onSurfaceMuted
        state.error != null && latest == null -> stringResource(R.string.update_failed) to colors.warn
        latest != null -> stringResource(R.string.update_up_to_date) to colors.good
        else -> stringResource(R.string.update_checking) to colors.onSurfaceMuted
    }
    val lastChecked = remember(state.lastCheckedMillis, locales) {
        state.lastCheckedMillis?.let { "${TimeText.shortDay(context, it)} ${TimeText.clock(it)}" }
    }
    Panel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(icon = SzIcons.Download, tint = tint, iconSize = 22.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                state.current?.let {
                    Text(text = stringResource(R.string.update_current_version, it.toString()), fontSize = 13.sp, color = colors.onSurfaceMuted)
                }
                if (latest != null) {
                    val date = latest.publishedAtMillis?.let(::formatDate)
                    Text(
                        text = if (date != null) {
                            stringResource(R.string.update_latest_version, latest.version.toString(), date)
                        } else {
                            stringResource(R.string.update_latest_version_no_date, latest.version.toString())
                        },
                        fontSize = 13.sp,
                        color = colors.onSurfaceMuted,
                    )
                }
            }
            BusyIcon(icon = SzIcons.Refresh, busy = state.checking, tint = if (state.checking) colors.primary else Color.Transparent, size = 18.dp)
        }
        AnimatedVisibility(
            visible = state.error != null,
            enter = expandVertically(tween(400, easing = Motion.Emphasized)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(300, easing = Motion.Emphasized)) + fadeOut(tween(150)),
        ) {
            Text(text = state.error.orEmpty(), fontSize = 12.sp, lineHeight = 17.sp, color = colors.warn)
        }
        lastChecked?.let {
            Text(text = stringResource(R.string.update_last_checked, it), fontSize = 12.sp, color = colors.onSurfaceMuted)
        }
    }
}

@Composable
private fun ActionButton(text: String, filled: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = SuzumoriTheme.colors
    Panel(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        color = if (filled) colors.primaryContainer else colors.surface,
        shape = RoundedCornerShape(18.dp),
        verticalArrangement = Arrangement.Center,
        enabled = enabled,
        onClick = onClick,
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (filled) colors.onPrimaryContainer else colors.primary,
        )
    }
}

/**
 * Release 說明是 Markdown。這裡只做簡單的轉換：標題加粗、清單換成圓點、程式碼區塊照原文顯示，
 * 其他語法（粗體、連結）保留原文。
 */
@Composable
private fun ReleaseNotes(markdown: String) {
    val colors = SuzumoriTheme.colors
    val lines = remember(markdown) { markdown.lines() }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            var inCode = false
            for (raw in lines) {
                val line = raw.trimEnd()
                when {
                    line.trimStart().startsWith("```") -> inCode = !inCode
                    inCode -> Text(text = line, fontSize = 11.sp, lineHeight = 16.sp, color = colors.onSurfaceMuted)
                    line.isBlank() -> Spacer(Modifier.height(6.dp))
                    line.startsWith("#") -> Text(
                        text = line.trimStart('#').trim(),
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                    )
                    line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> Text(
                        text = "•  " + line.trimStart().drop(2).replace("**", ""),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = colors.onSurface,
                    )
                    else -> Text(text = line.replace("**", ""), fontSize = 13.sp, lineHeight = 20.sp, color = colors.onSurface)
                }
            }
        }
    }
}

private fun formatDate(millis: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))

private const val RECHECK_AFTER_MILLIS = 60_000L
