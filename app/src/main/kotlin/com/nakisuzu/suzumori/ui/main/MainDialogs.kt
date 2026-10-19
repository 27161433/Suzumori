package com.nakisuzu.suzumori.ui.main

import androidx.compose.runtime.Composable
import com.nakisuzu.suzumori.ui.components.SuzumoriDialog

/** 首頁操作與解除安裝清理的對話框，放在最上層，蓋過導覽列。 */
@Composable
fun MainDialogs(state: MainUiState, viewModel: MainViewModel, onUninstall: () -> Unit) {
    when (val dialog = state.dialog) {
        null -> Unit
        is MainDialog.Message -> SuzumoriDialog(
            visible = state.dialogVisible,
            title = dialog.title,
            text = dialog.text,
            confirmText = "確定",
            onConfirm = viewModel::dismissDialog,
            onDismiss = viewModel::dismissDialog,
            onDismissFinished = viewModel::clearDialog,
        )
        MainDialog.ConfirmCleanup -> SuzumoriDialog(
            visible = state.dialogVisible,
            title = "解除安裝前清理",
            text = "會停止自動守護與夜間守護，並從遊戲白名單移除本 App 當初加入的項目（其他程式的項目會保留）。" +
                "MILLET 名單不需要清理，PowerKeeper 下次重建時會自然移除 GMS。",
            confirmText = "清理",
            dismissText = "取消",
            onConfirm = viewModel::confirmCleanup,
            onDismiss = viewModel::dismissDialog,
            onDismissFinished = viewModel::clearDialog,
        )
        is MainDialog.CleanupFinished -> SuzumoriDialog(
            visible = state.dialogVisible,
            title = "清理完成",
            text = dialog.text,
            confirmText = if (dialog.canUninstall) "前往解除安裝" else "確定",
            dismissText = if (dialog.canUninstall) "稍後" else null,
            onConfirm = {
                viewModel.dismissDialog()
                if (dialog.canUninstall) onUninstall()
            },
            onDismiss = viewModel::dismissDialog,
            onDismissFinished = viewModel::clearDialog,
        )
    }
}
