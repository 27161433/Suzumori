package com.nakisuzu.suzumori.platform

import android.content.ContentResolver
import android.provider.Settings
import com.nakisuzu.suzumori.core.guard.BootCounter

class AndroidBootCounter(private val resolver: ContentResolver) : BootCounter {
    override suspend fun current(): Int? =
        runCatching { Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1) }.getOrNull()?.takeIf { it >= 0 }
}
