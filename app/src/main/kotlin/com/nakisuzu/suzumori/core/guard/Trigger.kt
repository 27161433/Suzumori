package com.nakisuzu.suzumori.core.guard

/** 觸發檢查的原因，會記在事件紀錄與狀態頁。顯示的文字由平台層依語言提供。 */
enum class Trigger {
    APP_OPEN,
    MANUAL_CHECK,
    MANUAL_UNFREEZE,
    TILE,
    MILLET_CHANGED,
    GAME_ALLOWLIST_CHANGED,
    SCREEN_ON,
    SCREEN_OFF,
    LOCKED_BOOT,
    BOOT,
    PACKAGE_REPLACED,
    PERIODIC,
    SERVICE_START,
    SETTINGS_CHANGED,
    UNINSTALL_CLEANUP,
    SELF_MONITOR,
    NETWORK_CHANGED,
    KEEPALIVE,
    SLEEP_KEEPALIVE,
    ;

    companion object {
        fun fromName(name: String?): Trigger? = entries.firstOrNull { it.name == name }
    }
}
