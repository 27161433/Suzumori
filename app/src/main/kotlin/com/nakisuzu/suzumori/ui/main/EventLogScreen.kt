package com.nakisuzu.suzumori.ui.main

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nakisuzu.suzumori.core.log.EventEntry
import com.nakisuzu.suzumori.core.log.EventLogText
import com.nakisuzu.suzumori.platform.LogExporter
import com.nakisuzu.suzumori.platform.SystemPages
import com.nakisuzu.suzumori.ui.components.SectionCard
import com.nakisuzu.suzumori.ui.components.SectionFooter
import com.nakisuzu.suzumori.ui.components.SuzumoriPage
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun EventLogScreen(state: MainUiState, viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    SuzumoriPage(
        title = "事件紀錄",
        subtitle = "最近 ${state.events.size} 筆・最多保留 2000 筆",
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 16.dp)) {
                Icon(imageVector = MiuixIcons.Back, contentDescription = "返回", tint = MiuixTheme.colorScheme.onBackground)
            }
        },
        actions = {
            IconButton(
                onClick = {
                    scope.launch {
                        val uri = LogExporter.write(context.applicationContext, viewModel.exportLogText())
                        LogExporter.share(context, uri)?.let { viewModel.showMessage("無法匯出", it) }
                    }
                },
            ) {
                Icon(imageVector = MiuixIcons.Share, contentDescription = "匯出", tint = MiuixTheme.colorScheme.onBackground)
            }
            IconButton(onClick = viewModel::requestClearLog, modifier = Modifier.padding(end = 16.dp)) {
                Icon(imageVector = MiuixIcons.Delete, contentDescription = "清除", tint = MiuixTheme.colorScheme.onBackground)
            }
        },
        overlay = { MainDialogs(state, viewModel, onUninstall = { SystemPages.requestUninstall(context) }) },
    ) {
        item(key = "hint") {
            SectionFooter("記錄每次偵測到的名單改寫、修補與廣播，用來觀察 PowerKeeper 與 Joyose 改寫名單的頻率和時間點。新的在上。")
        }
        if (state.events.isEmpty()) {
            item(key = "empty") { SectionCard { BasicComponent(title = "尚無紀錄") } }
        }
        // 紀錄只會在結尾附加，從結尾算的序號在新增紀錄時保持不變，可以當作穩定的 key。
        itemsIndexed(state.events, key = { index, entry -> "${entry.timeMillis}-${state.events.size - index}" }) { _, entry ->
            EventItem(entry)
        }
    }
}

@Composable
private fun EventItem(entry: EventEntry) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val hasValues = entry.before != null || entry.after != null
    SectionCard {
        BasicComponent(onClick = if (hasValues) ({ expanded = !expanded }) else null) {
            Text(
                text = "${EventLogText.formatTime(entry.timeMillis)}・${entry.trigger}",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = entry.action, style = MiuixTheme.textStyles.headline1, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.result,
                style = MiuixTheme.textStyles.body2,
                color = resultColor(entry.result),
            )
            entry.key?.let {
                Spacer(Modifier.height(2.dp))
                Text(text = it, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            if (hasValues) {
                Spacer(Modifier.height(6.dp))
                if (expanded) {
                    SelectionContainer {
                        Text(
                            text = buildString {
                                entry.before?.let { append("修改前：\"").append(it).append("\"\n") }
                                entry.after?.let { append("修改後：\"").append(it).append("\"") }
                            }.trimEnd(),
                            style = MiuixTheme.textStyles.footnote1,
                            fontFamily = FontFamily.Monospace,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    Text(text = "點一下顯示修改前後的值", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun resultColor(result: String) = when {
    result.startsWith("例外") || result.startsWith("驗證失敗") || result.contains("暫停") || result.startsWith("沒有「修改系統設定」") ->
        MiuixTheme.colorScheme.error
    result == "成功" -> MiuixTheme.colorScheme.primary
    else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
}
