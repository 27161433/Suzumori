package com.nakisuzu.suzumori.core.guard

/** 觸發檢查的原因，會記在事件紀錄與狀態頁。 */
enum class Trigger(val label: String) {
    APP_OPEN("開啟 App"),
    MANUAL_CHECK("手動檢查"),
    MANUAL_UNFREEZE("手動解凍"),
    TILE("快速設定磚"),
    MILLET_CHANGED("MILLET 名單變更"),
    GAME_ALLOWLIST_CHANGED("遊戲白名單變更"),
    SCREEN_ON("螢幕開啟"),
    SCREEN_OFF("螢幕關閉"),
    LOCKED_BOOT("開機（解鎖前）"),
    BOOT("開機"),
    PACKAGE_REPLACED("App 更新"),
    PERIODIC("定期檢查"),
    SERVICE_START("服務啟動"),
    SETTINGS_CHANGED("設定變更"),
    UNINSTALL_CLEANUP("解除安裝前清理"),
    ;

    companion object {
        fun fromName(name: String?): Trigger? = entries.firstOrNull { it.name == name }
    }
}
