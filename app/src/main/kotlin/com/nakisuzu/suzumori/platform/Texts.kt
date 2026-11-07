package com.nakisuzu.suzumori.platform

import android.content.Context
import androidx.annotation.StringRes
import com.nakisuzu.suzumori.R
import com.nakisuzu.suzumori.core.guard.Trigger
import com.nakisuzu.suzumori.core.log.LogKey
import com.nakisuzu.suzumori.core.log.LogText
import com.nakisuzu.suzumori.core.log.TRUNCATED_SUFFIX
import java.util.IllegalFormatException

/** 把事件紀錄中與語言無關的 [LogText] 組成目前 App 語言的句子。 */
object LogTexts {
    fun render(context: Context, text: LogText): String = when (text) {
        is LogText.Raw -> text.text
        is LogText.Message -> when (text.key) {
            LogKey.LIST -> text.args.joinToString(context.getString(R.string.log_list_separator)) { render(context, it) }
            LogKey.CLAUSES -> text.args.joinToString(context.getString(R.string.log_clause_separator)) { render(context, it) }
            else -> {
                val args = text.args.map { render(context, it) }
                // 紀錄會跨版本保留；參數數量和字串對不上時（例如降版）顯示代碼，不讓畫面閃退。
                try {
                    context.getString(string(text.key), *args.toTypedArray())
                } catch (_: IllegalFormatException) {
                    if (args.isEmpty()) text.key.name else "${text.key.name}(${args.joinToString()})"
                }
            }
        }
    }

    /** 修改前後的值：截斷標記「…(512)」換成對應語言的說明。 */
    fun value(context: Context, value: String): String {
        val match = TRUNCATED_SUFFIX.find(value) ?: return value
        return value.substring(0, match.range.first) + context.getString(R.string.log_truncated, match.groupValues[1])
    }

    /** 每一種訊息對應的字串資源，參數一律以 `%1$s`、`%2$s` 填入。 */
    @StringRes
    private fun string(key: LogKey): Int = when (key) {
        LogKey.LIST, LogKey.CLAUSES -> error("組合類訊息不對應單一字串")
        LogKey.SELF_APP -> R.string.log_self_app
        LogKey.SKIP_PATCH -> R.string.log_skip_patch
        LogKey.PAUSED_BY_RATE_LIMIT -> R.string.log_paused_by_rate_limit
        LogKey.APPEND -> R.string.log_append
        LogKey.REMOVE -> R.string.log_remove
        LogKey.TOGGLE_SEPARATOR -> R.string.log_toggle_separator
        LogKey.MILLET_RESTORED -> R.string.log_millet_restored
        LogKey.GAME_ALLOWLIST_RELOADED -> R.string.log_game_allowlist_reloaded
        LogKey.GAME_ALLOWLIST_RESTORED -> R.string.log_game_allowlist_restored
        LogKey.CLEANUP_GAME_ALLOWLIST -> R.string.log_cleanup_game_allowlist
        LogKey.NO_BASELINE -> R.string.log_no_baseline
        LogKey.NOTHING_OWNED -> R.string.log_nothing_owned
        LogKey.NO_WRITE_PERMISSION -> R.string.log_no_write_permission
        LogKey.RETRY -> R.string.log_retry
        LogKey.RATE_LIMITED -> R.string.log_rate_limited
        LogKey.EXCEPTION -> R.string.log_exception
        LogKey.SUCCESS -> R.string.log_success
        LogKey.VERIFY_FAILED -> R.string.log_verify_failed
        LogKey.EXTERNAL_CHANGE -> R.string.log_external_change
        LogKey.MEMBERS_UNCHANGED -> R.string.log_members_unchanged
        LogKey.REMOVED_MEMBERS -> R.string.log_removed_members
        LogKey.ADDED_MEMBERS -> R.string.log_added_members
        LogKey.GMS_IN_LIST -> R.string.log_gms_in_list
        LogKey.GMS_NOT_IN_LIST -> R.string.log_gms_not_in_list
        LogKey.SEND_UNFREEZE -> R.string.log_send_unfreeze
        LogKey.SEND_RECONNECT -> R.string.log_send_reconnect
        LogKey.UNFREEZE_SUMMARY -> R.string.log_unfreeze_summary
        LogKey.SENT -> R.string.log_sent
        LogKey.SEND_EXCEPTION -> R.string.log_send_exception
        LogKey.EXIT_ACTION -> R.string.self_exit_action
        LogKey.EXIT_WITH_DESCRIPTION -> R.string.self_exit_with_description
        LogKey.KILLED_SUMMARY -> R.string.self_killed_summary
        LogKey.AUTO_RESTARTED -> R.string.self_auto_restarted
        LogKey.USER_OPENED -> R.string.self_user_opened
        LogKey.STARTED -> R.string.self_started
        LogKey.START_UNKNOWN -> R.string.self_start_unknown
        LogKey.SINCE_LAST_EXIT -> R.string.self_since_last_exit
        LogKey.PAUSED_ACTION -> R.string.self_paused_action
        LogKey.PAUSED_RESULT -> R.string.self_paused_result
        LogKey.PAUSED_SUMMARY -> R.string.self_paused_summary
        LogKey.START_SERVICE -> R.string.start_service
        LogKey.START_BOOT -> R.string.start_boot
        LogKey.START_PACKAGE_REPLACED -> R.string.start_package_replaced
        LogKey.START_BROADCAST -> R.string.start_broadcast
        LogKey.START_JOB -> R.string.start_job
        LogKey.START_ALARM -> R.string.start_alarm
        LogKey.START_PUSH -> R.string.start_push
        LogKey.START_CONTENT_PROVIDER -> R.string.start_content_provider
        LogKey.START_BACKUP -> R.string.start_backup
        LogKey.START_LAUNCHER -> R.string.start_launcher
        LogKey.START_RECENTS -> R.string.start_recents
        LogKey.START_ACTIVITY -> R.string.start_activity
        LogKey.START_OTHER -> R.string.start_other
        LogKey.EXIT_SELF -> R.string.exit_self
        LogKey.EXIT_SIGNALED -> R.string.exit_signaled
        LogKey.EXIT_LOW_MEMORY -> R.string.exit_low_memory
        LogKey.EXIT_CRASH -> R.string.exit_crash
        LogKey.EXIT_CRASH_NATIVE -> R.string.exit_crash_native
        LogKey.EXIT_ANR -> R.string.exit_anr
        LogKey.EXIT_INITIALIZATION_FAILURE -> R.string.exit_initialization_failure
        LogKey.EXIT_PERMISSION_CHANGE -> R.string.exit_permission_change
        LogKey.EXIT_EXCESSIVE_RESOURCE_USAGE -> R.string.exit_excessive_resource_usage
        LogKey.EXIT_USER_REQUESTED -> R.string.exit_user_requested
        LogKey.EXIT_USER_STOPPED -> R.string.exit_user_stopped
        LogKey.EXIT_DEPENDENCY_DIED -> R.string.exit_dependency_died
        LogKey.EXIT_OTHER -> R.string.exit_other
        LogKey.EXIT_FREEZER -> R.string.exit_freezer
        LogKey.EXIT_PACKAGE_STATE_CHANGE -> R.string.exit_package_state_change
        LogKey.EXIT_PACKAGE_UPDATED -> R.string.exit_package_updated
        LogKey.EXIT_UNKNOWN -> R.string.exit_unknown
        LogKey.EXIT_FORCE_STOPPED_BY -> R.string.exit_force_stopped_by
        LogKey.CLEAN_SWIPE_UP -> R.string.clean_swipe_up
        LogKey.CLEAN_ONE_KEY -> R.string.clean_one_key
        LogKey.CLEAN_FORCE -> R.string.clean_force
        LogKey.CLEAN_LOCK_SCREEN -> R.string.clean_lock_screen
        LogKey.CLEAN_GARBAGE -> R.string.clean_garbage
        LogKey.CLEAN_OPTIMIZATION -> R.string.clean_optimization
        LogKey.CLEAN_GAME -> R.string.clean_game
        LogKey.CLEAN_AUTO_POWER -> R.string.clean_auto_power
        LogKey.CLEAN_AUTO_THERMAL -> R.string.clean_auto_thermal
        LogKey.CLEAN_AUTO_IDLE -> R.string.clean_auto_idle
        LogKey.CLEAN_AUTO_SLEEP -> R.string.clean_auto_sleep
        LogKey.CLEAN_AUTO_LOCK_OFF -> R.string.clean_auto_lock_off
        LogKey.CLEAN_AUTO_SYSTEM_ABNORMAL -> R.string.clean_auto_system_abnormal
        LogKey.PACKAGE_SECURITY_CENTER -> R.string.package_security_center
        LogKey.PACKAGE_SETTINGS -> R.string.package_settings
        LogKey.DURATION_SECONDS -> R.string.duration_seconds
        LogKey.DURATION_MINUTES -> R.string.duration_minutes
        LogKey.DURATION_HOURS_MINUTES -> R.string.duration_hours_minutes
        LogKey.DURATION_DAYS -> R.string.duration_days
    }
}

/** 觸發原因與時間長度的顯示文字。 */
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
        Trigger.NETWORK_CHANGED -> R.string.trigger_network_changed
        Trigger.KEEPALIVE -> R.string.trigger_keepalive
        Trigger.SLEEP_KEEPALIVE -> R.string.trigger_sleep_keepalive
    }

    /** 紀錄中保存的觸發原因：認得的名稱翻成目前的語言，舊版無法對應的原文照樣顯示。 */
    fun trigger(context: Context, stored: String): String =
        Trigger.fromName(stored)?.let { context.getString(trigger(it)) } ?: stored

    /** 以小時與分鐘表示的時間長度：「20 分」「1 小時」「1 小時 20 分」。字串的參數與事件紀錄共用，一律是文字。 */
    fun hoursMinutes(context: Context, totalMinutes: Long): String {
        val hours = (totalMinutes / 60).toString()
        val rest = (totalMinutes % 60).toString()
        return when {
            totalMinutes < 60 -> context.getString(R.string.duration_minutes, rest)
            totalMinutes % 60 == 0L -> context.getString(R.string.duration_hours, hours)
            else -> context.getString(R.string.duration_hours_minutes, hours, rest)
        }
    }
}
