package com.nakisuzu.suzumori

import android.app.Application
import com.nakisuzu.suzumori.platform.Diag

class SuzumoriApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        Diag.i("process start")
    }
}
