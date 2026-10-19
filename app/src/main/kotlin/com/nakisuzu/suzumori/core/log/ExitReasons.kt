package com.nakisuzu.suzumori.core.log

/** 把系統對程序結束的說明（`ApplicationExitInfo.getDescription()`，多為英文）中已知的項目轉成中文。 */
object ExitReasons {
    /** 小米 ProcessManager 的清理原因，會原樣出現在說明中。 */
    private val MIUI_CLEAN = mapOf(
        "SwipeUpClean" to "從多工畫面滑掉",
        "OneKeyClean" to "在多工畫面一鍵清理",
        "ForceClean" to "被系統強制清理",
        "LockScreenClean" to "鎖屏後被清理",
        "GarbageClean" to "被垃圾清理結束",
        "OptimizationClean" to "被手機管家最佳化清理",
        "GameClean" to "遊戲加速時被清理",
        "AutoPowerKill" to "被省電機制結束",
        "AutoThermalKill" to "過熱保護時被結束",
        "AutoIdleKill" to "閒置時被系統結束",
        "AutoSleepClean" to "睡眠時被自動清理",
        "AutoLockOffClean" to "鎖屏後被自動清理",
        "AutoSystemAbnormalClean" to "系統異常時被自動清理",
    )

    private val PACKAGE_NAMES = mapOf(
        "com.miui.securitycenter" to "手機管家",
        "com.android.settings" to "系統設定",
    )

    /** 已知的說明回傳中文，不認得時回傳 null。 */
    fun describe(description: String?): String? {
        val text = description?.trim().orEmpty()
        if (text.isEmpty()) return null
        MIUI_CLEAN.entries.firstOrNull { it.key.equals(text, ignoreCase = true) }?.let { return it.value }
        return when {
            text.contains("killDueToPackageUpdate") || text.contains("installPackageLI") -> "App 更新"
            text == "remove task" -> "從多工畫面滑掉"
            text.contains("from process:") -> {
                val pkg = text.substringAfter("from process:").trim()
                "被${PACKAGE_NAMES[pkg] ?: pkg}強制停止"
            }
            else -> null
        }
    }

    /**
     * 舊版記下的結果是「原因：系統說明」，例如「系統其他原因：SwipeUpClean」。
     * 說明是已知項目時換成中文，其他內容原樣回傳。
     */
    fun localizeResult(result: String): String {
        val description = result.substringAfter("：", missingDelimiterValue = "")
        return describe(description) ?: result
    }
}
