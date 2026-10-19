package com.nakisuzu.suzumori.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nakisuzu.suzumori.R
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
            confirmText = stringResource(R.string.ok),
            onConfirm = viewModel::dismissDialog,
            onDismiss = viewModel::dismissDialog,
            onDismissFinished = viewModel::clearDialog,
        )
        MainDialog.ConfirmCleanup -> SuzumoriDialog(
            visible = state.dialogVisible,
            title = stringResource(R.string.cleanup_title),
            text = stringResource(R.string.cleanup_confirm_text),
            confirmText = stringResource(R.string.cleanup_confirm),
            dismissText = stringResource(R.string.cancel),
            onConfirm = viewModel::confirmCleanup,
            onDismiss = viewModel::dismissDialog,
            onDismissFinished = viewModel::clearDialog,
        )
        is MainDialog.CleanupFinished -> SuzumoriDialog(
            visible = state.dialogVisible,
            title = stringResource(R.string.cleanup_done),
            text = dialog.text,
            confirmText = stringResource(if (dialog.canUninstall) R.string.cleanup_go_uninstall else R.string.ok),
            dismissText = if (dialog.canUninstall) stringResource(R.string.later) else null,
            onConfirm = {
                viewModel.dismissDialog()
                if (dialog.canUninstall) onUninstall()
            },
            onDismiss = viewModel::dismissDialog,
            onDismissFinished = viewModel::clearDialog,
        )
    }
}
