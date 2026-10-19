package com.nakisuzu.suzumori.core.log

/** 小米 ProcessManager 的清理原因，會原樣出現在程序結束的說明中。 */
enum class MiuiClean(val key: String, val logKey: LogKey) {
    SWIPE_UP("SwipeUpClean", LogKey.CLEAN_SWIPE_UP),
    ONE_KEY("OneKeyClean", LogKey.CLEAN_ONE_KEY),
    FORCE("ForceClean", LogKey.CLEAN_FORCE),
    LOCK_SCREEN("LockScreenClean", LogKey.CLEAN_LOCK_SCREEN),
    GARBAGE("GarbageClean", LogKey.CLEAN_GARBAGE),
    OPTIMIZATION("OptimizationClean", LogKey.CLEAN_OPTIMIZATION),
    GAME("GameClean", LogKey.CLEAN_GAME),
    AUTO_POWER("AutoPowerKill", LogKey.CLEAN_AUTO_POWER),
    AUTO_THERMAL("AutoThermalKill", LogKey.CLEAN_AUTO_THERMAL),
    AUTO_IDLE("AutoIdleKill", LogKey.CLEAN_AUTO_IDLE),
    AUTO_SLEEP("AutoSleepClean", LogKey.CLEAN_AUTO_SLEEP),
    AUTO_LOCK_OFF("AutoLockOffClean", LogKey.CLEAN_AUTO_LOCK_OFF),
    AUTO_SYSTEM_ABNORMAL("AutoSystemAbnormalClean", LogKey.CLEAN_AUTO_SYSTEM_ABNORMAL),
}

/** 系統對程序結束的說明中，認得的項目。 */
sealed interface ExitDescription {
    data class Clean(val kind: MiuiClean) : ExitDescription

    data object PackageUpdate : ExitDescription

    /** 被 [by]（套件名稱）強制停止。 */
    data class ForceStopped(val by: String) : ExitDescription
}

/** 解析系統對程序結束的說明（`ApplicationExitInfo.getDescription()`，多為英文）。 */
object ExitReasons {
    /** 有名稱可以顯示的套件。 */
    private val PACKAGE_NAMES = mapOf(
        "com.miui.securitycenter" to LogKey.PACKAGE_SECURITY_CENTER,
        "com.android.settings" to LogKey.PACKAGE_SETTINGS,
    )

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
     * 早期版本認得的說明較少，不認得的記成「原因：系統說明」，例如「系統其他原因：SwipeUpClean」。
     * 冒號後面的說明現在認得時回傳對應的項目，否則回傳 null。
     */
    fun parseResult(result: String): ExitDescription? {
        val separator = listOf("：", ": ").firstOrNull { it in result } ?: return null
        return parse(result.substringAfter(separator))
    }

    fun toLogText(description: ExitDescription): LogText = when (description) {
        is ExitDescription.Clean -> msg(description.kind.logKey)
        ExitDescription.PackageUpdate -> msg(LogKey.EXIT_PACKAGE_UPDATED)
        is ExitDescription.ForceStopped -> msg(LogKey.EXIT_FORCE_STOPPED_BY, packageName(description.by))
    }

    fun packageName(pkg: String): LogText = PACKAGE_NAMES[pkg]?.let { msg(it) } ?: raw(pkg)
}
