package com.nakisuzu.suzumori.ui.main

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** 畫面上顯示的時間文字。 */
internal object TimeText {
    private val clock = DateTimeFormatter.ofPattern("HH:mm")
    private val clockWithSeconds = DateTimeFormatter.ofPattern("HH:mm:ss")

    private fun zoned(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())

    fun clock(millis: Long): String = clock.format(zoned(millis))

    fun clockWithSeconds(millis: Long): String = clockWithSeconds.format(zoned(millis))

    fun date(millis: Long): LocalDate = zoned(millis).toLocalDate()

    /** 「今天」「昨天」或「10/16」。 */
    fun shortDay(millis: Long): String {
        val day = date(millis)
        val today = LocalDate.now()
        return when (day) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> "${day.monthValue}/${day.dayOfMonth}"
        }
    }

    /** 事件紀錄的日期分組標題：左邊是「今天」「昨天」或日期，右邊是補充。 */
    fun dayHeader(day: LocalDate): Pair<String, String> {
        val today = LocalDate.now()
        val date = "${day.monthValue} 月 ${day.dayOfMonth} 日"
        return when (day) {
            today -> "今天" to date
            today.minusDays(1) -> "昨天" to date
            else -> date to day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.TRADITIONAL_CHINESE)
        }
    }

    /** 從 [from] 到下一次 [to] 還有多久，例如「1 小時 20 分」。 */
    fun until(from: LocalTime, to: LocalTime): String {
        var minutes = Duration.between(from, to).toMinutes()
        if (minutes <= 0) minutes += 24 * 60
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0L -> "$rest 分"
            rest == 0L -> "$hours 小時"
            else -> "$hours 小時 $rest 分"
        }
    }
}
