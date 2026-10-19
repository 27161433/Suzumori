package com.nakisuzu.suzumori.core.log

/** 小米 ProcessManager 的清理原因，會原樣出現在程序結束的說明中。 */
enum class MiuiClean(val key: String) {
    SWIPE_UP("SwipeUpClean"),
    ONE_KEY("OneKeyClean"),
    FORCE("ForceClean"),
    LOCK_SCREEN("LockScreenClean"),
    GARBAGE("GarbageClean"),
    OPTIMIZATION("OptimizationClean"),
    GAME("GameClean"),
    AUTO_POWER("AutoPowerKill"),
    AUTO_THERMAL("AutoThermalKill"),
    AUTO_IDLE("AutoIdleKill"),
    AUTO_SLEEP("AutoSleepClean"),
    AUTO_LOCK_OFF("AutoLockOffClean"),
    AUTO_SYSTEM_ABNORMAL("AutoSystemAbnormalClean"),
}

/** 系統對程序結束的說明中，認得的項目。顯示的文字由平台層依語言提供。 */
sealed interface ExitDescription {
    data class Clean(val kind: MiuiClean) : ExitDescription

    data object PackageUpdate : ExitDescription

    /** 被 [by]（套件名稱）強制停止。 */
    data class ForceStopped(val by: String) : ExitDescription
}

/** 解析系統對程序結束的說明（`ApplicationExitInfo.getDescription()`，多為英文）。 */
object ExitReasons {
    /** 認得的說明回傳對應的項目，不認得時回傳 null。 */
    fun parse(description: String?): ExitDescription? {
        val text = description?.trim().orEmpty()
        if (text.isEmpty()) return null
        MiuiClean.entries.firstOrNull { it.key.equals(text, ignoreCase = true) }?.let { return ExitDescription.Clean(it) }
        return when {
            text.contains("killDueToPackageUpdate") || text.contains("installPackageLI") -> ExitDescription.PackageUpdate
            text == "remove task" -> ExitDescription.Clean(MiuiClean.SWIPE_UP)
            text.contains("from process:") -> ExitDescription.ForceStopped(text.substringAfter("from process:").trim())
            else -> null
        }
    }

    /**
     * 不認得的說明記成「原因：系統說明」，例如「系統其他原因：SwipeUpClean」（早期版本認得的項目較少）。
     * 冒號後面的說明現在認得時回傳對應的項目，否則回傳 null。
     */
    fun parseResult(result: String): ExitDescription? {
        val separator = listOf("：", ": ").firstOrNull { it in result } ?: return null
        return parse(result.substringAfter(separator))
    }
}
