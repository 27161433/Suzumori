package com.nakisuzu.suzumori

import android.app.Application

class SuzumoriApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
