package com.javargasp.tiominutermvi.domain.traininglog

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Pure arithmetic on local calendar dates ("yyyy-MM-dd"), without java.time.
 * A date string has no zone, so the math runs in UTC, where every day has 24 hours.
 * Weeks start on Monday.
 */
object CalendarDates {

    /** [date] moved by [days] calendar days (negative to go back). */
    fun plusDays(date: String, days: Int): String {
        val calendar = calendarOf(date)
        calendar.add(Calendar.DAY_OF_MONTH, days)
        return format(calendar)
    }

    /** The Monday of the week that contains [date]. */
    fun mondayOf(date: String): String {
        val calendar = calendarOf(date)
        // Calendar: SUNDAY = 1 ... SATURDAY = 7; days since Monday: Mon 0 ... Sun 6
        val daysSinceMonday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        return plusDays(date, -daysSinceMonday)
    }

    /** The 7 dates (Monday to Sunday) of the week that contains [date]. */
    fun weekDates(date: String): List<String> {
        val monday = mondayOf(date)
        return (0 until DAYS_PER_WEEK).map { plusDays(monday, it) }
    }

    /** The Monday [weeks] weeks after (or before) the week of [date]. */
    fun plusWeeks(date: String, weeks: Int): String = plusDays(mondayOf(date), weeks * DAYS_PER_WEEK)

    private fun calendarOf(date: String): Calendar {
        val parsed = try {
            formatter().parse(date)
        } catch (e: ParseException) {
            null
        } ?: throw IllegalArgumentException("Not a yyyy-MM-dd date: $date")
        return Calendar.getInstance(UTC, Locale.ROOT).apply { time = parsed }
    }

    private fun format(calendar: Calendar): String = formatter().format(calendar.time)

    private fun formatter() = SimpleDateFormat(LocalDateProvider.DATE_PATTERN, Locale.ROOT).apply {
        timeZone = UTC
        isLenient = false
    }

    const val DAYS_PER_WEEK = 7
    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
}
