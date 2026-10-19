package com.nakisuzu.suzumori.ui.main

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nakisuzu.suzumori.R
import com.nakisuzu.suzumori.core.log.EventEntry
import com.nakisuzu.suzumori.core.log.EventLevel
import com.nakisuzu.suzumori.platform.Labels
import com.nakisuzu.suzumori.platform.LogExporter
import com.nakisuzu.suzumori.platform.LogTexts
import com.nakisuzu.suzumori.ui.components.Entrance
import com.nakisuzu.suzumori.ui.components.Footnote
import com.nakisuzu.suzumori.ui.components.IconBadge
import com.nakisuzu.suzumori.ui.components.Motion
import com.nakisuzu.suzumori.ui.components.PageTitle
import com.nakisuzu.suzumori.ui.components.Panel
import com.nakisuzu.suzumori.ui.components.SquareIconButton
import com.nakisuzu.suzumori.ui.components.SuzumoriDialog
import com.nakisuzu.suzumori.ui.components.SuzumoriPage
import com.nakisuzu.suzumori.ui.components.SzIcons
import com.nakisuzu.suzumori.ui.theme.SuzumoriTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import java.time.LocalDate

@Composable
fun EventLogScreen(state: EventLogUiState, viewModel: EventLogViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = SuzumoriTheme.colors
    val rows = remember(state.events, context) { buildRows(context, state.events) }
    // 進頁時前幾筆依序浮現；之後捲進畫面或新增的紀錄直接顯示。
    var introDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(900)
        introDone = true
    }

    SuzumoriPage(verticalArrangement = Arrangement.Top) {
        item(key = "title") {
            val exportFailed = stringResource(R.string.log_export_failed)
            PageTitle(stringResource(R.string.tab_log)) {
                SquareIconButton(
                    icon = SzIcons.Share,
                    contentDescription = stringResource(R.string.log_export),
                    onClick = {
                        scope.launch {
                            val uri = LogExporter.write(context.applicationContext, viewModel.exportText(context))
                            LogExporter.share(context, uri)?.let { viewModel.showMessage(exportFailed, it) }
                        }
                    },
                )
                SquareIconButton(
                    icon = SzIcons.Trash,
                    contentDescription = stringResource(R.string.log_clear),
                    onClick = viewModel::requestClear,
                )
            }
        }
        item(key = "count") {
            Text(
                text = pluralStringResource(R.plurals.log_count, state.events.size, state.events.size),
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                fontSize = 13.sp,
                color = colors.onSurfaceMuted,
            )
        }
        item(key = "hint") {
            Footnote(
                text = stringResource(R.string.log_hint),
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
        }
        if (state.loaded && state.events.isEmpty()) {
            item(key = "empty") {
                Panel(
                    modifier = Modifier
                        .animateItem()
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconBadge(
                        icon = SzIcons.List,
                        tint = colors.onSurfaceMuted,
                        size = 48.dp,
                        iconSize = 22.dp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Text(
                        text = stringResource(R.string.log_empty),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurfaceMuted,
                    )
                }
            }
        }
        items(rows, key = { it.key }, contentType = { it::class }) { row ->
            val play = !introDone && row.position < 10
            Entrance(index = row.position, play = play, modifier = Modifier.animateItem()) {
                when (row) {
                    is DayRow -> DayHeader(row)
                    is EntryRow -> EventItem(row.entry, isLast = row.last)
                }
            }
        }
    }
}

private sealed interface LogRow {
    val key: String

    /** 在清單中的位置，用來錯開進場時間。 */
    val position: Int
}

private data class DayRow(override val key: String, override val position: Int, val label: String, val detail: String) : LogRow

private data class EntryRow(override val key: String, override val position: Int, val entry: EventEntry, val last: Boolean) : LogRow

/** [events] 新到舊，依日期分組並插入日期標題。 */
private fun buildRows(context: Context, events: List<EventEntry>): List<LogRow> {
    val rows = mutableListOf<LogRow>()
    var currentDay: LocalDate? = null
    fun markLast() {
        val last = rows.lastOrNull()
        if (last is EntryRow) rows[rows.lastIndex] = last.copy(last = true)
    }
    events.forEachIndexed { index, entry ->
        val day = TimeText.date(entry.timeMillis)
        if (day != currentDay) {
            markLast()
            currentDay = day
            val (label, detail) = TimeText.dayHeader(context, day)
            rows += DayRow("day-$day", rows.size, label, detail)
        }
        // 紀錄只會在結尾附加，從結尾算的序號在新增紀錄時保持不變，可以當作穩定的 key。
        rows += EntryRow("${entry.timeMillis}-${events.size - index}", rows.size, entry, last = false)
    }
    markLast()
    return rows
}

@Composable
private fun DayHeader(row: DayRow) {
    val colors = SuzumoriTheme.colors
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text = row.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        Text(text = row.detail, fontSize = 12.sp, color = colors.onSurfaceMuted)
    }
}

@Composable
private fun EventItem(entry: EventEntry, isLast: Boolean) {
    val colors = SuzumoriTheme.colors
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val hasValues = entry.before != null || entry.after != null
    val tone = levelColor(entry.level)
    // 紀錄存的是與語言無關的代碼，顯示時才組成目前語言的句子。
    val action = remember(entry, context) { LogTexts.render(context, entry.action) }
    val result = remember(entry, context) { LogTexts.render(context, entry.result) }
    val trigger = remember(entry.trigger, context) { Labels.trigger(context, entry.trigger) }
    val rail = colors.divider
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, tween(350, easing = Motion.Emphasized), label = "chevron")

    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (!isLast) {
                    val x = 7.dp.toPx()
                    drawLine(rail, Offset(x, 38.dp.toPx()), Offset(x, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
            },
    ) {
        Box(
            Modifier
                .padding(top = 21.dp)
                .size(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(tone),
            )
        }
        Spacer(Modifier.width(12.dp))
        Panel(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            onClick = if (hasValues) ({ expanded = !expanded }) else null,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = TimeText.clockWithSeconds(entry.timeMillis),
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = colors.onSurfaceMuted,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
                Box(
                    modifier = Modifier
                        .height(22.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceVariant)
                        .padding(horizontal = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = trigger, fontSize = 11.sp, color = colors.onSurfaceMuted)
                }
            }
            Text(text = action, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
            Text(text = result, fontSize = 13.sp, lineHeight = 20.sp, color = tone)
            entry.key?.let {
                Text(text = it, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = colors.onSurfaceMuted)
            }
            if (hasValues) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(tween(450, easing = Motion.Emphasized)) + fadeIn(tween(300, delayMillis = 100)),
                    exit = shrinkVertically(tween(350, easing = Motion.Emphasized)) + fadeOut(tween(150)),
                ) {
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceVariant)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            entry.before?.let { ValueBlock(stringResource(R.string.log_before), LogTexts.value(context, it)) }
                            entry.after?.let { ValueBlock(stringResource(R.string.log_after), LogTexts.value(context, it)) }
                        }
                    }
                }
                Row(
                    modifier = Modifier.heightIn(min = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(if (expanded) R.string.log_collapse else R.string.log_show_values),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.primary,
                    )
                    Icon(
                        imageVector = SzIcons.ChevronDown,
                        contentDescription = null,
                        modifier = Modifier
                            .size(14.dp)
                            .rotate(chevron),
                        tint = colors.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ValueBlock(label: String, value: String) {
    val colors = SuzumoriTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, fontSize = 11.sp, color = colors.onSurfaceMuted)
        Text(text = "\"$value\"", fontSize = 11.sp, lineHeight = 17.sp, fontFamily = FontFamily.Monospace, color = colors.onSurface)
    }
}

/** 事件紀錄頁的對話框，放在最上層，蓋過導覽列。 */
@Composable
fun LogDialogs(state: EventLogUiState, viewModel: EventLogViewModel) {
    when (val dialog = state.dialog) {
        null -> Unit
        is LogDialog.Message -> SuzumoriDialog(
            visible = state.dialogVisible,
            title = dialog.title,
            text = dialog.text,
            confirmText = stringResource(R.string.ok),
            onConfirm = viewModel::dismissDialog,
            onDismiss = viewModel::dismissDialog,
            onDismissFinished = viewModel::clearDialog,
        )
        LogDialog.ConfirmClear -> SuzumoriDialog(
            visible = state.dialogVisible,
            title = stringResource(R.string.log_clear_title),
            text = stringResource(R.string.log_clear_text),
            confirmText = stringResource(R.string.log_clear),
            dismissText = stringResource(R.string.cancel),
            onConfirm = viewModel::confirmClear,
            onDismiss = viewModel::dismissDialog,
            onDismissFinished = viewModel::clearDialog,
        )
    }
}

@Composable
private fun levelColor(level: EventLevel): Color {
    val colors = SuzumoriTheme.colors
    return when (level) {
        EventLevel.WARNING -> colors.warn
        EventLevel.SUCCESS -> colors.good
        EventLevel.INFO -> colors.onSurfaceMuted
    }
}
