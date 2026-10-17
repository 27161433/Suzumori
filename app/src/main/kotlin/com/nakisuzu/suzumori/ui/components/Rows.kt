package com.nakisuzu.suzumori.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 數值的語氣：正常、需要注意，或只是資訊。 */
enum class Tone { Good, Bad, Neutral }

@Composable
fun Tone.color(): Color = when (this) {
    Tone.Good -> MiuixTheme.colorScheme.primary
    Tone.Bad -> MiuixTheme.colorScheme.error
    Tone.Neutral -> MiuixTheme.colorScheme.onSurfaceVariantSummary
}

/** 卡片上方的小標題。 */
@Composable
fun SectionTitle(text: String) {
    SmallTitle(text = text, modifier = Modifier.padding(top = 8.dp))
}

/** HyperOS 設定頁的圓角卡片，左右留 12dp。 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        colors = CardDefaults.defaultColors(color = color),
        content = content,
    )
}

/** 卡片下方的補充說明。 */
@Composable
fun SectionFooter(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onBackgroundVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, bottom = 12.dp),
    )
}

/** 左邊標題（與說明），右邊顯示一個值。 */
@Composable
fun ValueRow(title: String, value: String, tone: Tone = Tone.Neutral, summary: String? = null) {
    BasicComponent(
        title = title,
        summary = summary,
        endActions = {
            Text(
                text = value,
                style = MiuixTheme.textStyles.body2,
                color = tone.color(),
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        },
    )
}

/** 點一下就執行的動作列，標題用主色表示可點擊。 */
@Composable
fun ActionRow(title: String, summary: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    BasicComponent(
        title = title,
        titleColor = BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.primary),
        summary = summary,
        enabled = enabled,
        onClick = onClick,
    )
}
