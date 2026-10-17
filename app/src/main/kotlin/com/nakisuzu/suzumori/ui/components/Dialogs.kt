package com.nakisuzu.suzumori.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/**
 * HyperOS 風格的對話框，最多兩個按鈕。
 *
 * [visible] 控制展開與收起；收起動畫結束後才呼叫 [onDismissFinished]，
 * 呼叫端在那時才清除內容，避免關閉途中文字消失。
 */
@Composable
fun SuzumoriDialog(
    visible: Boolean,
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    dismissText: String? = null,
) {
    OverlayDialog(
        show = visible,
        title = title,
        summary = text,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        Column {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (dismissText != null) {
                    TextButton(text = dismissText, onClick = onDismiss, modifier = Modifier.weight(1f))
                }
                TextButton(
                    text = confirmText,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
