package com.nakisuzu.suzumori.platform

import android.content.Context
import androidx.annotation.StringRes
import com.nakisuzu.suzumori.R
import com.nakisuzu.suzumori.core.guard.GuardText
import com.nakisuzu.suzumori.core.guard.Trigger
import com.nakisuzu.suzumori.core.log.ExitDescription
import com.nakisuzu.suzumori.core.log.MiuiClean

/**
 * 守護核心的文字，依目前的 App 語言從字串資源讀取。
 *
 * 每次都重新讀取，切換語言後新寫入的紀錄就會使用新的語言。
 */
class AndroidGuardText(private val context: Context) : GuardText {
    private fun s(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    override val selfApp get() = s(R.string.log_self_app)

    override fun list(items: Collection<String>) = items.joinToString(s(R.string.log_list_separator))

    override fun clauses(items: List<String>) = items.joinToString(s(R.string.log_clause_separator))

    override val skipPatch get() = s(R.string.log_skip_patch)
    override val pausedByRateLimit get() = s(R.string.log_paused_by_rate_limit)

    override fun append(names: String) = s(R.string.log_append, names)

    override fun remove(names: String) = s(R.string.log_remove, names)

    override val toggleSeparator get() = s(R.string.log_toggle_separator)

    override fun milletRestored(names: String) = s(R.string.log_millet_restored, names)

    override val gameAllowlistReloaded get() = s(R.string.log_game_allowlist_reloaded)

    override fun gameAllowlistRestored(names: String) = s(R.string.log_game_allowlist_restored, names)

    override val cleanupGameAllowlist get() = s(R.string.log_cleanup_game_allowlist)
    override val noBaseline get() = s(R.string.log_no_baseline)
    override val nothingOwned get() = s(R.string.log_nothing_owned)
    override val noWritePermission get() = s(R.string.log_no_write_permission)

    override fun retry(action: String, attempt: Int) = s(R.string.log_retry, action, attempt)

    override fun rateLimited(maxPerMinute: Int, pauseMinutes: Long) = s(R.string.log_rate_limited, maxPerMinute, pauseMinutes.toInt())

    override fun exception(error: String) = s(R.string.log_exception, error)

    override val success get() = s(R.string.log_success)
    override val verifyFailed get() = s(R.string.log_verify_failed)

    override fun externalChange(diff: String) = s(R.string.log_external_change, diff)

    override val membersUnchanged get() = s(R.string.log_members_unchanged)

    override fun removedMembers(items: String) = s(R.string.log_removed_members, items)

    override fun addedMembers(items: String) = s(R.string.log_added_members, items)

    override fun gmsInList(present: Boolean) = s(if (present) R.string.log_gms_in_list else R.string.log_gms_not_in_list)

    override val sendUnfreeze get() = s(R.string.log_send_unfreeze)
    override val sendReconnect get() = s(R.string.log_send_reconnect)
    override val unfreezeSummary get() = s(R.string.log_unfreeze_summary)

    override fun sendResult(label: String, error: String?) =
        if (error == null) s(R.string.log_sent, label) else s(R.string.log_send_exception, label, error)

    override fun truncated(total: Int) = s(R.string.log_truncated, total)
}

/** 觸發原因、程序結束原因與時間長度的顯示文字。 */
object Labels {
    @StringRes
    fun trigger(trigger: Trigger): Int = when (trigger) {
        Trigger.APP_OPEN -> R.string.trigger_app_open
        Trigger.MANUAL_CHECK -> R.string.trigger_manual_check
        Trigger.MANUAL_UNFREEZE -> R.string.trigger_manual_unfreeze
        Trigger.TILE -> R.string.trigger_tile
        Trigger.MILLET_CHANGED -> R.string.trigger_millet_changed
        Trigger.GAME_ALLOWLIST_CHANGED -> R.string.trigger_game_allowlist_changed
        Trigger.SCREEN_ON -> R.string.trigger_screen_on
        Trigger.SCREEN_OFF -> R.string.trigger_screen_off
        Trigger.LOCKED_BOOT -> R.string.trigger_locked_boot
        Trigger.BOOT -> R.string.trigger_boot
        Trigger.PACKAGE_REPLACED -> R.string.trigger_package_replaced
        Trigger.PERIODIC -> R.string.trigger_periodic
        Trigger.SERVICE_START -> R.string.trigger_service_start
        Trigger.SETTINGS_CHANGED -> R.string.trigger_settings_changed
        Trigger.UNINSTALL_CLEANUP -> R.string.trigger_uninstall_cleanup
        Trigger.SELF_MONITOR -> R.string.trigger_self_monitor
    }

    /** 紀錄中保存的觸發原因：認得的名稱翻成目前的語言，舊版無法對應的原文照樣顯示。 */
    fun trigger(context: Context, stored: String): String =
        Trigger.fromName(stored)?.let { context.getString(trigger(it)) } ?: stored

    fun exit(context: Context, description: ExitDescription): String = when (description) {
        is ExitDescription.Clean -> context.getString(clean(description.kind))
        ExitDescription.PackageUpdate -> context.getString(R.string.exit_package_updated)
        is ExitDescription.ForceStopped -> context.getString(R.string.exit_force_stopped_by, packageName(context, description.by))
    }

    @StringRes
    private fun clean(kind: MiuiClean): Int = when (kind) {
        MiuiClean.SWIPE_UP -> R.string.clean_swipe_up
        MiuiClean.ONE_KEY -> R.string.clean_one_key
        MiuiClean.FORCE -> R.string.clean_force
        MiuiClean.LOCK_SCREEN -> R.string.clean_lock_screen
        MiuiClean.GARBAGE -> R.string.clean_garbage
        MiuiClean.OPTIMIZATION -> R.string.clean_optimization
        MiuiClean.GAME -> R.string.clean_game
        MiuiClean.AUTO_POWER -> R.string.clean_auto_power
        MiuiClean.AUTO_THERMAL -> R.string.clean_auto_thermal
        MiuiClean.AUTO_IDLE -> R.string.clean_auto_idle
        MiuiClean.AUTO_SLEEP -> R.string.clean_auto_sleep
        MiuiClean.AUTO_LOCK_OFF -> R.string.clean_auto_lock_off
        MiuiClean.AUTO_SYSTEM_ABNORMAL -> R.string.clean_auto_system_abnormal
    }

    private fun packageName(context: Context, pkg: String): String = when (pkg) {
        "com.miui.securitycenter" -> context.getString(R.string.package_security_center)
        "com.android.settings" -> context.getString(R.string.package_settings)
        else -> pkg
    }

    /** 「3 秒」「12 分」「2 小時 5 分」「3 天」。 */
    fun duration(context: Context, millis: Long): String {
        val seconds = (millis / 1000).coerceAtLeast(0)
        return when {
            seconds < 60 -> context.getString(R.string.duration_seconds, seconds.toInt())
            seconds < 3_600 -> context.getString(R.string.duration_minutes, (seconds / 60).toInt())
            seconds < 86_400 -> context.getString(R.string.duration_hours_minutes, (seconds / 3_600).toInt(), (seconds % 3_600 / 60).toInt())
            else -> context.getString(R.string.duration_days, (seconds / 86_400).toInt())
        }
    }

    /** 以小時與分鐘表示的時間長度：「20 分」「1 小時」「1 小時 20 分」。 */
    fun hoursMinutes(context: Context, totalMinutes: Long): String {
        val hours = (totalMinutes / 60).toInt()
        val rest = (totalMinutes % 60).toInt()
        return when {
            hours == 0 -> context.getString(R.string.duration_minutes, rest)
            rest == 0 -> context.getString(R.string.duration_hours, hours)
            else -> context.getString(R.string.duration_hours_minutes, hours, rest)
        }
    }
}
