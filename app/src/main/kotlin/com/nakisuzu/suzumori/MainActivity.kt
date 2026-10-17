package com.nakisuzu.suzumori

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.nakisuzu.suzumori.ui.diagnostic.DiagnosticScreen
import com.nakisuzu.suzumori.ui.diagnostic.DiagnosticViewModel
import com.nakisuzu.suzumori.ui.theme.SuzumoriTheme

class MainActivity : ComponentActivity() {
    private val viewModel: DiagnosticViewModel by viewModels { DiagnosticViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk 22 不會自動套用 edge-to-edge，要自己開啟並在 Compose 端處理 insets。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SuzumoriTheme {
                DiagnosticScreen(viewModel)
            }
        }
    }
}
