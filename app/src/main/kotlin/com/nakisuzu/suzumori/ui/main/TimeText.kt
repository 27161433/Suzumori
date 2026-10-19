package com.nakisuzu.suzumori.ui.main

import android.content.Context
import com.nakisuzu.suzumori.R
import com.nakisuzu.suzumori.platform.Labels
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** 畫面上顯示的時間文字。日期與時間長度依目前的 App 語言顯示。 */
internal object TimeText {
    private val clock = DateTimeFormatter.ofPattern("HH:mm")
    private val clockWithSeconds = DateTimeFormatter.ofPattern("HH:mm:ss")

    private fun zoned(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())

    private fun locale(context: Context) = context.resources.configuration.locales[0]

    fun clock(millis: Long): String = clock.format(zoned(millis))

    fun clockWithSeconds(millis: Long): String = clockWithSeconds.format(zoned(millis))

    fun date(millis: Long): LocalDate = zoned(millis).toLocalDate()

    /** 「今天」「昨天」或「10/16」。 */
    fun shortDay(context: Context, millis: Long): String {
        val day = date(millis)
        val today = LocalDate.now()
        return when (day) {
            today -> context.getString(R.string.today)
            today.minusDays(1) -> context.getString(R.string.yesterday)
            else -> DateTimeFormatter.ofPattern(context.getString(R.string.date_short_pattern), locale(context)).format(day)
        }
    }

    /** 事件紀錄的日期分組標題：左邊是「今天」「昨天」或日期，右邊是補充。 */
    fun dayHeader(context: Context, day: LocalDate): Pair<String, String> {
        val today = LocalDate.now()
        val locale = locale(context)
        val date = DateTimeFormatter.ofPattern(context.getString(R.string.date_month_day_pattern), locale).format(day)
        return when (day) {
            today -> context.getString(R.string.today) to date
            today.minusDays(1) -> context.getString(R.string.yesterday) to date
            else -> date to day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        }
    }

    /** 從 [from] 到下一次 [to] 還有多久，例如「1 小時 20 分」。 */
    fun until(context: Context, from: LocalTime, to: LocalTime): String {
        var minutes = Duration.between(from, to).toMinutes()
        if (minutes <= 0) minutes += 24 * 60
        return Labels.hoursMinutes(context, minutes)
    }
}
